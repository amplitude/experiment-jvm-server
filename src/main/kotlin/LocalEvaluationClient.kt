package com.amplitude.experiment

import com.amplitude.experiment.cohort.CohortApiImpl
import com.amplitude.experiment.cohort.CohortService
import com.amplitude.experiment.cohort.CohortServiceConfig
import com.amplitude.experiment.cohort.CohortServiceImpl
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.cohort.ExperimentalCohortApi
import com.amplitude.experiment.cohort.InMemoryCohortStorage
import com.amplitude.experiment.cohort.getCohortIds
import com.amplitude.experiment.evaluation.EvaluationEngine
import com.amplitude.experiment.evaluation.EvaluationEngineImpl
import com.amplitude.experiment.evaluation.serialization.SerialVariant
import com.amplitude.experiment.flag.FlagConfigApiImpl
import com.amplitude.experiment.flag.FlagConfigService
import com.amplitude.experiment.flag.FlagConfigServiceConfig
import com.amplitude.experiment.flag.FlagConfigServiceImpl
import com.amplitude.experiment.flag.InMemoryFlagConfigStorage
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.toSerialExperimentUser
import com.amplitude.experiment.util.toVariant
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class LocalEvaluationClient internal constructor(
    apiKey: String,
    config: LocalEvaluationConfig = LocalEvaluationConfig(),
) {
    private val lock = Once()
    private val httpClient = OkHttpClient()
    private val serverUrl: HttpUrl = config.serverUrl.toHttpUrl()
    private val evaluation: EvaluationEngine = EvaluationEngineImpl()
    private val flagConfigStorage = InMemoryFlagConfigStorage()
    private val flagConfigService: FlagConfigService = FlagConfigServiceImpl(
        FlagConfigServiceConfig(config.flagConfigPollerIntervalMillis),
        FlagConfigApiImpl(apiKey, serverUrl, httpClient),
        flagConfigStorage
    )
    private var cohortStorage: CohortStorage? = null
    private var cohortService: CohortService? = null

    fun start() {
        lock.once {
            flagConfigService.start()
            startCohortSync()
        }
    }

    @JvmOverloads
    fun evaluate(user: ExperimentUser, flagKeys: List<String> = listOf()): Map<String, Variant> {
        val flagConfigs = flagConfigService.getFlagConfigs(flagKeys)
        val enrichedUser = if (user.userId == null) {
            user
        } else {
            user.copyToBuilder().apply {
                cohortIds(cohortService?.getCohorts(user.userId))
            }.build()
        }
        val flagResults = evaluation.evaluate(flagConfigs, enrichedUser.toSerialExperimentUser().convert())
        return flagResults.mapNotNull { entry ->
            if (!entry.value.isDefaultVariant) {
                entry.key to SerialVariant(entry.value.variant).toVariant()
            } else {
                null
            }
        }.toMap()
    }

    @ExperimentalCohortApi
    fun enableCohortSync(
        apiKey: String,
        secretKey: String,
        config: CohortConfiguration = CohortConfiguration()
    ) {
        val cohortStorage = InMemoryCohortStorage()
        val cohortService = CohortServiceImpl(
            CohortServiceConfig(
                config.cohortMaxSize,
                config.cohortSyncIntervalSeconds
            ),
            CohortApiImpl(
                apiKey,
                secretKey,
                config.cohortServerUrl.toHttpUrl(),
                httpClient,
            ),
            cohortStorage
        ) {
            flagConfigStorage.getAll().values.getCohortIds().apply {
                Logger.d("managing cohorts: $this")
            }
        }
        this.cohortService = cohortService
        this.cohortStorage = cohortStorage
    }

    // TODO feels too hacky. Design better interaction between cohort and flag config services
    private fun startCohortSync() {
        if (cohortService == null || cohortStorage == null) {
            return;
        }
        // Intercept incoming flag configs. If the configs are new, and
        // contain cohort ids, refresh
        flagConfigService.addFlagConfigInterceptor { incoming ->
            val newCohortIds = mutableSetOf<String>()
            val stored = flagConfigStorage.getAll()
            incoming.forEach { (incomingFlagKey, incomingFlagConfig) ->
                val storedFlagConfig = stored[incomingFlagKey]
                if (storedFlagConfig == null || storedFlagConfig != incomingFlagConfig) {
                    // This is a new or updated flag config, check for new cohort Ids.
                    newCohortIds += incomingFlagConfig.getCohortIds()
                        .toMutableSet()
                        .filter { cohortId ->
                            cohortStorage?.getCohortDescription(cohortId) == null
                        }
                }
            }
            if (newCohortIds.isNotEmpty()) {
                cohortService?.refresh(newCohortIds)
            }
        }
        cohortService?.start()
    }
}
