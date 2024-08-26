@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment

import com.amplitude.Amplitude
import com.amplitude.Options
import com.amplitude.experiment.assignment.AmplitudeAssignmentService
import com.amplitude.experiment.assignment.Assignment
import com.amplitude.experiment.assignment.AssignmentService
import com.amplitude.experiment.assignment.InMemoryAssignmentFilter
import com.amplitude.experiment.cohort.CohortApi
import com.amplitude.experiment.cohort.DynamicCohortApi
import com.amplitude.experiment.cohort.InMemoryCohortStorage
import com.amplitude.experiment.cohort.ProxyCohortMembershipApi
import com.amplitude.experiment.cohort.ProxyCohortStorage
import com.amplitude.experiment.deployment.DeploymentRunner
import com.amplitude.experiment.evaluation.EvaluationEngine
import com.amplitude.experiment.evaluation.EvaluationEngineImpl
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.evaluation.topologicalSort
import com.amplitude.experiment.flag.DynamicFlagConfigApi
import com.amplitude.experiment.flag.FlagConfigPoller
import com.amplitude.experiment.flag.FlagConfigStreamApi
import com.amplitude.experiment.flag.InMemoryFlagConfigStorage
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.USER_GROUP_TYPE
import com.amplitude.experiment.util.filterDefaultVariants
import com.amplitude.experiment.util.getAllCohortIds
import com.amplitude.experiment.util.getGroupedCohortIds
import com.amplitude.experiment.util.toEvaluationContext
import com.amplitude.experiment.util.toVariants
import com.amplitude.experiment.util.wrapMetrics
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class LocalEvaluationClient internal constructor(
    apiKey: String,
    private val config: LocalEvaluationConfig = LocalEvaluationConfig(),
    private val httpClient: OkHttpClient = OkHttpClient(),
    cohortApi: CohortApi? = getCohortDownloadApi(config, httpClient)
) {
    private val assignmentService: AssignmentService? = createAssignmentService(apiKey)
    private val serverUrl: HttpUrl = getServerUrl(config)
    private val streamServerUrl: HttpUrl = getStreamServerUrl(config)
    private val evaluation: EvaluationEngine = EvaluationEngineImpl()
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper(config.metrics)
    private val flagConfigApi = DynamicFlagConfigApi(apiKey, serverUrl, null, httpClient)
    private val proxyUrl: HttpUrl? = getProxyUrl(config)
    private val flagConfigProxyApi = if (proxyUrl == null) null else DynamicFlagConfigApi(apiKey, proxyUrl, null, httpClient)
    private val flagConfigStreamApi = if (config.streamUpdates) FlagConfigStreamApi(apiKey, streamServerUrl, httpClient, config.streamFlagConnTimeoutMillis) else null
    private val flagConfigStorage = InMemoryFlagConfigStorage()
    private val cohortStorage = if (config.cohortSyncConfig == null) {
        null
    } else if (config.evaluationProxyConfig == null) {
        InMemoryCohortStorage()
    } else {
        ProxyCohortStorage(
            proxyConfig = config.evaluationProxyConfig,
            membershipApi = ProxyCohortMembershipApi(apiKey, config.evaluationProxyConfig.proxyUrl.toHttpUrl(), httpClient),
            metrics = metrics,
        )
    }

    private val deploymentRunner = DeploymentRunner(
        config = config,
        flagConfigApi = flagConfigApi,
        flagConfigProxyApi = flagConfigProxyApi,
        flagConfigStreamApi = flagConfigStreamApi,
        flagConfigStorage = flagConfigStorage,
        cohortApi = cohortApi,
        cohortStorage = cohortStorage,
        metrics = metrics,
    )

    fun start() {
        try {
            deploymentRunner.start()
        } catch (t: Throwable) {
            throw ExperimentException(
                message = "Failed to start local evaluation client.",
                cause = t
            )
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
                setServerUrl(getEventServerUrl(config, config.assignmentConfiguration))
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
        val enrichedUser = enrichUser(user, sortedFlagConfigs)
        val evaluationResults = wrapMetrics(
            metric = metrics::onEvaluation,
            failure = metrics::onEvaluationFailure,
        ) {
            evaluation.evaluate(enrichedUser.toEvaluationContext(), sortedFlagConfigs)
        }
        val variants = evaluationResults.toVariants()
        assignmentService?.track(Assignment(user, variants))
        return variants
    }

    private fun enrichUser(user: ExperimentUser, flagConfigs: List<EvaluationFlag>): ExperimentUser {
        val groupedCohortIds = flagConfigs.getGroupedCohortIds()
        if (cohortStorage == null) {
            if (groupedCohortIds.isNotEmpty()) {
                val flagKeys = flagConfigs.mapNotNull { flag ->
                    val cohortIds = flag.getAllCohortIds()
                    if (cohortIds.isEmpty()) {
                        null
                    } else {
                        flag.key
                    }
                }
                Logger.e("Local evaluation flags $flagKeys target cohorts but cohort targeting is not configured.")
            }
            return user
        }
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

private fun getCohortDownloadApi(config: LocalEvaluationConfig, httpClient: OkHttpClient): CohortApi? {
    return if (config.cohortSyncConfig != null) {
        DynamicCohortApi(
            apiKey = config.cohortSyncConfig.apiKey,
            secretKey = config.cohortSyncConfig.secretKey,
            maxCohortSize = config.cohortSyncConfig.maxCohortSize,
            serverUrl = getCohortServerUrl(config),
            proxyUrl = getProxyUrl(config),
            httpClient = httpClient,
        )
    } else {
        null
    }
}

private fun getServerUrl(config: LocalEvaluationConfig): HttpUrl {
    return if (config.serverUrl == LocalEvaluationConfig.Defaults.SERVER_URL) {
        when (config.serverZone) {
            ServerZone.US -> US_SERVER_URL.toHttpUrl()
            ServerZone.EU -> EU_SERVER_URL.toHttpUrl()
        }
    } else {
        config.serverUrl.toHttpUrl()
    }
}

private fun getStreamServerUrl(config: LocalEvaluationConfig): HttpUrl {
    return if (config.streamServerUrl == LocalEvaluationConfig.Defaults.STREAM_SERVER_URL) {
        when (config.serverZone) {
            ServerZone.US -> US_STREAM_SERVER_URL.toHttpUrl()
            ServerZone.EU -> EU_STREAM_SERVER_URL.toHttpUrl()
        }
    } else {
        config.streamServerUrl.toHttpUrl()
    }
}

private fun getProxyUrl(config: LocalEvaluationConfig): HttpUrl? {
    return config.evaluationProxyConfig?.proxyUrl?.toHttpUrl()
}

private fun getCohortServerUrl(config: LocalEvaluationConfig): HttpUrl {
    return if (config.cohortSyncConfig?.cohortServerUrl == LocalEvaluationConfig.Defaults.COHORT_SERVER_URL) {
        when (config.serverZone) {
            ServerZone.US -> US_COHORT_SERVER_URL.toHttpUrl()
            ServerZone.EU -> EU_COHORT_SERVER_URL.toHttpUrl()
        }
    } else {
        config.cohortSyncConfig?.cohortServerUrl?.toHttpUrl()
            ?: LocalEvaluationConfig.Defaults.COHORT_SERVER_URL.toHttpUrl()
    }
}

private fun getEventServerUrl(
    config: LocalEvaluationConfig,
    assignmentConfiguration: AssignmentConfiguration
): String {
    return if (assignmentConfiguration.serverUrl == LocalEvaluationConfig.Defaults.EVENT_SERVER_URL) {
        when (config.serverZone) {
            ServerZone.US -> US_EVENT_SERVER_URL
            ServerZone.EU -> EU_EVENT_SERVER_URL
        }
    } else {
        assignmentConfiguration.serverUrl
    }
}

fun main() {
    val client = LocalEvaluationClient("server-qz35UwzJ5akieoAdIgzM4m9MIiOLXLoz", LocalEvaluationConfig(streamUpdates = true))
    client.start()
    println(client.evaluateV2(ExperimentUser("1")))
}