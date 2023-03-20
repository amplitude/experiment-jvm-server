package com.amplitude.experiment

import com.amplitude.Amplitude
import com.amplitude.experiment.assignment.AmplitudeAssignmentService
import com.amplitude.experiment.assignment.Assignment
import com.amplitude.experiment.assignment.AssignmentService
import com.amplitude.experiment.assignment.LRUAssignmentFilter
import com.amplitude.experiment.cohort.CohortApiImpl
import com.amplitude.experiment.cohort.CohortService
import com.amplitude.experiment.cohort.CohortServiceConfig
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.cohort.ExperimentalApi
import com.amplitude.experiment.cohort.InMemoryCohortStorage
import com.amplitude.experiment.cohort.PollingCohortService
import com.amplitude.experiment.cohort.getCohortIds
import com.amplitude.experiment.evaluation.EvaluationEngine
import com.amplitude.experiment.evaluation.EvaluationEngineImpl
import com.amplitude.experiment.evaluation.serialization.SerialVariant
import com.amplitude.experiment.flag.FlagConfigApiImpl
import com.amplitude.experiment.flag.FlagConfigService
import com.amplitude.experiment.flag.FlagConfigServiceConfig
import com.amplitude.experiment.flag.FlagConfigServiceImpl
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.toSerialExperimentUser
import com.amplitude.experiment.util.toVariant
import com.amplitude.experiment.util.wrapMetrics
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class LocalEvaluationClient internal constructor(
    apiKey: String,
    config: LocalEvaluationConfig = LocalEvaluationConfig(),
) {
    private val startLock = Once()
    private val cohortLock = Once()
    private val assignmentLock = Once()

    private val metricsWrapper = LocalEvaluationMetricsWrapper()

    private val httpClient = OkHttpClient()
    private val serverUrl: HttpUrl = config.serverUrl.toHttpUrl()
    private val evaluation: EvaluationEngine = EvaluationEngineImpl()
    private val flagConfigService: FlagConfigService = FlagConfigServiceImpl(
        FlagConfigServiceConfig(config.flagConfigPollerIntervalMillis),
        FlagConfigApiImpl(apiKey, serverUrl, httpClient),
        metricsWrapper
    )
    private var cohortStorage: CohortStorage? = null
    private var cohortService: CohortService? = null
    private var assignmentService: AssignmentService? = null

    fun start() {
        startLock.once {
            val cohortService = this.cohortService
            if (cohortService != null) {
                // Intercept incoming flag configs and update the cohort service's set of managed cohorts
                flagConfigService.addFlagConfigInterceptor { incoming ->
                    val cohortIds = incoming.flatMapTo(mutableSetOf()) { it.getCohortIds() }
                    if (cohortService.manage(cohortIds)) {
                        cohortService.refresh()
                    }
                }
            }
            flagConfigService.start()
            cohortService?.start()
        }
    }

    @JvmOverloads
    fun evaluate(user: ExperimentUser, flagKeys: List<String> = listOf()): Map<String, Variant> {
        val enrichedUser = if (user.userId == null) {
            user
        } else {
            user.copyToBuilder().apply {
                cohortIds(cohortService?.getCohortsForUser(user.userId))
            }.build()
        }
        val flagConfigs = flagConfigService.getFlagConfigs()
        val flagResults = wrapMetrics(
            metric = metricsWrapper::onEvaluation,
            failure = metricsWrapper::onEvaluationFailure,
        ) {
            evaluation.evaluate(flagConfigs, enrichedUser.toSerialExperimentUser().convert())
        }
        Logger.i("evaluate - user=$enrichedUser, result=$flagResults")
        assignmentService?.track(Assignment(user, flagResults))
        return flagResults.filter { entry ->
            val isVariant = !entry.value.isDefaultVariant
            val isIncluded = (flagKeys.isEmpty() || flagKeys.contains(entry.key))
            val isDeployed = entry.value.deployed
            isVariant && isIncluded && isDeployed
        }.map { entry ->
            entry.key to SerialVariant(entry.value.variant).toVariant()
        }.toMap()
    }

    @ExperimentalApi
    fun enableAssignmentTracking(
        apiKey: String,
        config: AssignmentConfiguration = AssignmentConfiguration()
    ) = assignmentLock.once {
        val amplitude = Amplitude.getInstance("experiment").apply {
            setEventUploadThreshold(config.eventUploadThreshold)
            setEventUploadPeriodMillis(config.eventUploadThreshold)
            useBatchMode(config.useBatchMode)
            init(apiKey)
        }
        assignmentService = AmplitudeAssignmentService(
            amplitude,
            LRUAssignmentFilter(config.filterCapacity),
            metricsWrapper
        )
    }

    @ExperimentalApi
    fun enableCohortSync(
        apiKey: String,
        secretKey: String,
        config: CohortConfiguration = CohortConfiguration()
    ) = cohortLock.once {
        Logger.d("enableCohortSync called $config")
        val cohortStorage = InMemoryCohortStorage()
        val cohortService = PollingCohortService(
            CohortServiceConfig(
                config.cohortMaxSize,
                config.cohortSyncIntervalSeconds,
            ),
            CohortApiImpl(
                apiKey,
                secretKey,
                config.cohortServerUrl.toHttpUrl(),
                httpClient,
            ),
            cohortStorage,
            metricsWrapper,
        )
        this.cohortService = cohortService
        this.cohortStorage = cohortStorage
    }

    @ExperimentalApi
    fun enableMetrics(metrics: LocalEvaluationMetrics) {
        metricsWrapper.metrics = metrics
    }
}

interface LocalEvaluationMetrics {
    fun onEvaluation()
    fun onEvaluationFailure(exception: Exception)
    fun onAssignment()
    fun onAssignmentFilter()
    fun onAssignmentEvent()
    fun onAssignmentEventFailure(exception: Exception)
    fun onFlagConfigFetch()
    fun onFlagConfigFetchFailure(exception: Exception)
    fun onCohortDescriptionsFetch()
    fun onCohortDescriptionsFetchFailure(exception: Exception)
    fun onCohortDownload()
    fun onCohortDownloadFailure(exception: Exception)
}
