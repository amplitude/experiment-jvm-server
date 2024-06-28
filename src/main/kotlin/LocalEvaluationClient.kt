package com.amplitude.experiment

import com.amplitude.Amplitude
import com.amplitude.Options
import com.amplitude.experiment.assignment.AmplitudeAssignmentService
import com.amplitude.experiment.assignment.Assignment
import com.amplitude.experiment.assignment.AssignmentService
import com.amplitude.experiment.assignment.InMemoryAssignmentFilter
import com.amplitude.experiment.cohort.DirectCohortDownloadApi
import com.amplitude.experiment.cohort.InMemoryCohortStorage
import com.amplitude.experiment.deployment.DeploymentRunner
import com.amplitude.experiment.evaluation.EvaluationEngine
import com.amplitude.experiment.evaluation.EvaluationEngineImpl
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.evaluation.topologicalSort
import com.amplitude.experiment.flag.DirectFlagConfigApi
import com.amplitude.experiment.flag.InMemoryFlagConfigStorage
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.USER_GROUP_TYPE
import com.amplitude.experiment.util.filterDefaultVariants
import com.amplitude.experiment.util.getGroupedCohortIds
import com.amplitude.experiment.util.toEvaluationContext
import com.amplitude.experiment.util.toVariants
import com.amplitude.experiment.util.wrapMetrics
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class LocalEvaluationClient internal constructor(
    private val apiKey: String,
    private val config: LocalEvaluationConfig = LocalEvaluationConfig(),
) {
    private val startLock = Once()
    private val httpClient = OkHttpClient()
    private val assignmentService: AssignmentService? = createAssignmentService(apiKey)
    private val serverUrl: HttpUrl = getServerUrl(config)
    private val cohortServerUrl: HttpUrl = getCohortServerUrl(config)
    private val evaluation: EvaluationEngine = EvaluationEngineImpl()
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper(config.metrics)
    private val flagConfigApi = DirectFlagConfigApi(apiKey, serverUrl, httpClient)
    private val flagConfigStorage = InMemoryFlagConfigStorage()
    private val cohortDownloadApi = if (config.cohortSyncConfiguration != null) {
        DirectCohortDownloadApi(
            apiKey = config.cohortSyncConfiguration.apiKey,
            secretKey = config.cohortSyncConfiguration.secretKey,
            maxCohortSize = config.cohortSyncConfiguration.maxCohortSize,
            serverUrl = cohortServerUrl,
            httpClient = httpClient,
        )
    } else {
        null
    }
    private val cohortStorage = if (config.cohortSyncConfiguration != null) {
        InMemoryCohortStorage()
    } else {
        null
    }

    private val deploymentRunner = DeploymentRunner(
        config = config,
        flagConfigApi = flagConfigApi,
        flagConfigStorage = flagConfigStorage,
        cohortDownloadApi = cohortDownloadApi,
        cohortStorage = cohortStorage,
        metrics = metrics,
    )

    fun start() {
        startLock.once {
            deploymentRunner.start()
        }
    }

    private fun createAssignmentService(deploymentKey: String): AssignmentService? {
        if (config.assignmentConfiguration == null) return null
        return AmplitudeAssignmentService(
            Amplitude.getInstance(deploymentKey).apply {
                init(config.assignmentConfiguration.apiKey)
                setEventUploadThreshold(config.assignmentConfiguration.eventUploadThreshold)
                setEventUploadPeriodMillis(config.assignmentConfiguration.eventUploadPeriodMillis)
                useBatchMode(config.assignmentConfiguration.useBatchMode)
                setOptions(Options().setMinIdLength(1))
                setServerUrl(config.assignmentConfiguration.serverUrl)
            },
            InMemoryAssignmentFilter(config.assignmentConfiguration.cacheCapacity)
        )
    }
    @JvmOverloads
    @Deprecated(
        "Use the evaluateV2 method. EvaluateV2 returns variant objects with default values (e.g. null/off) if the user is evaluated, but not assigned a variant.",
        ReplaceWith("evaluateV2(user, flagKeys)")
    )
    fun evaluate(user: ExperimentUser, flagKeys: List<String> = listOf()): Map<String, Variant> {
        return evaluateV2(user, flagKeys.toSet()).filterDefaultVariants()
    }

    @JvmOverloads
    fun evaluateV2(user: ExperimentUser, flagKeys: Set<String> = setOf()): Map<String, Variant> {
        val flagConfigs = flagConfigStorage.getFlagConfigs()
        val sortedFlagConfigs = topologicalSort(flagConfigs, flagKeys)
        if (sortedFlagConfigs.isEmpty()) {
            return mapOf()
        }
        val enrichedUser = enrichUser(user, flagConfigs)
        val evaluationResults = wrapMetrics(
            metric = metrics::onEvaluation,
            failure = metrics::onEvaluationFailure,
        ) {
            evaluation.evaluate(enrichedUser.toEvaluationContext(), sortedFlagConfigs)
        }
        Logger.d("evaluate - user=$enrichedUser, result=$evaluationResults")
        val variants = evaluationResults.toVariants()
        assignmentService?.track(Assignment(user, variants))
        return variants
    }

    private fun enrichUser(user: ExperimentUser, flagConfigs: Map<String, EvaluationFlag>): ExperimentUser {
        if (cohortStorage == null) {
            return user
        }
        val groupedCohortIds = flagConfigs.values.getGroupedCohortIds()
        return user.copyToBuilder().apply {
            val userCohortsIds = groupedCohortIds[USER_GROUP_TYPE]
            if (!userCohortsIds.isNullOrEmpty() && user.userId != null) {
                cohortIds(cohortStorage.getCohortsForUser(user.userId, userCohortsIds))
            }
            if (user.groups != null) {
                for (group in user.groups) {
                    val groupType = group.key
                    val groupName = group.value.firstOrNull() ?: continue
                    val cohortIds = groupedCohortIds[groupType]
                    if (cohortIds.isNullOrEmpty()) {
                        continue
                    }
                    groupCohortIds(
                        groupType,
                        groupName,
                        cohortStorage.getCohortsForGroup(groupType, groupName, cohortIds)
                    )
                }
            }
        }.build()
    }
}

private fun getServerUrl(config: LocalEvaluationConfig): HttpUrl {
    if (config.serverZone == LocalEvaluationConfig.Defaults.SERVER_ZONE) {
        return config.serverUrl.toHttpUrl()
    } else {
        return when (config.serverZone) {
            ServerZone.US -> US_SERVER_URL.toHttpUrl()
            ServerZone.EU -> EU_SERVER_URL.toHttpUrl()
        }
    }
}

private fun getCohortServerUrl(config: LocalEvaluationConfig): HttpUrl {
    if (config.serverZone == LocalEvaluationConfig.Defaults.SERVER_ZONE) {
        return config.cohortServerUrl.toHttpUrl()
    } else {
        return when (config.serverZone) {
            ServerZone.US -> US_SERVER_URL.toHttpUrl()
            ServerZone.EU -> EU_SERVER_URL.toHttpUrl()
        }
    }
}
