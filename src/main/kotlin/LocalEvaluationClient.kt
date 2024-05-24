@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment

import com.amplitude.Amplitude
import com.amplitude.Options
import com.amplitude.experiment.assignment.AmplitudeAssignmentService
import com.amplitude.experiment.assignment.Assignment
import com.amplitude.experiment.assignment.AssignmentService
import com.amplitude.experiment.assignment.InMemoryAssignmentFilter
import com.amplitude.experiment.cohort.CohortLoader
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.cohort.DirectCohortDownloadApiV5
import com.amplitude.experiment.cohort.DynamicCohortDownloadApi
import com.amplitude.experiment.cohort.InMemoryCohortStorage
import com.amplitude.experiment.cohort.ProxyCohortDownloadApi
import com.amplitude.experiment.cohort.ProxyCohortMembershipApi
import com.amplitude.experiment.cohort.ProxyCohortStorage
import com.amplitude.experiment.cohort.USER_GROUP_TYPE
import com.amplitude.experiment.deployment.DeploymentRunner
import com.amplitude.experiment.evaluation.EvaluationEngine
import com.amplitude.experiment.evaluation.EvaluationEngineImpl
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.evaluation.topologicalSort
import com.amplitude.experiment.flag.FlagConfigApiV2
import com.amplitude.experiment.flag.FlagConfigStorage
import com.amplitude.experiment.flag.InMemoryFlagConfigStorage
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.filterDefaultVariants
import com.amplitude.experiment.util.getGroupedCohortIds
import com.amplitude.experiment.util.toEvaluationContext
import com.amplitude.experiment.util.toVariants
import com.amplitude.experiment.util.wrapMetrics
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class LocalEvaluationClient internal constructor(
    private val deploymentKey: String,
    private val config: LocalEvaluationConfig = LocalEvaluationConfig(),
) {

    private val metricsWrapper = LocalEvaluationMetricsWrapper(config.metrics)
    private val evaluation: EvaluationEngine = EvaluationEngineImpl(null)
    private val httpClient = OkHttpClient()
    private val assignmentService: AssignmentService? = createAssignmentService(deploymentKey)
    private val cohortStorage: CohortStorage = createCohortStorage()
    private val flagConfigStorage: FlagConfigStorage = InMemoryFlagConfigStorage()
    private val deploymentRunner = DeploymentRunner(
        config,
        FlagConfigApiV2(
            deploymentKey = deploymentKey,
            serverUrl = config.serverUrl.toHttpUrl(),
            proxyUrl = config.proxyConfiguration?.proxyUrl?.toHttpUrl(),
            httpClient = httpClient,
            metrics = metricsWrapper,
        ),
        flagConfigStorage,
        cohortStorage,
        config.cohortSyncConfiguration?.let {
            val directCohortDownloadApi = DirectCohortDownloadApiV5(
                config.cohortSyncConfiguration.apiKey,
                config.cohortSyncConfiguration.secretKey,
                httpClient
            )
            val cohortDownloadApi = if (config.proxyConfiguration != null) {
                val proxyCohortDownloadApi = ProxyCohortDownloadApi(
                    deploymentKey,
                    config.proxyConfiguration.proxyUrl,
                    httpClient
                )
                DynamicCohortDownloadApi(
                    directApi = directCohortDownloadApi,
                    proxyApi = proxyCohortDownloadApi,
                    metrics = metricsWrapper
                )
            } else {
                directCohortDownloadApi
            }
            CohortLoader(
                maxCohortSize = config.cohortSyncConfiguration.maxCohortSize,
                cohortDownloadApi = cohortDownloadApi,
                cohortStorage = cohortStorage,
                directCohortDownloadApi = if (config.proxyConfiguration == null) {
                    directCohortDownloadApi
                } else {
                    null
                },
                metrics = metricsWrapper,
            )
        },
        metricsWrapper
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

    fun stop() {
        deploymentRunner.stop()
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
            metric = metricsWrapper::onEvaluation,
            failure = metricsWrapper::onEvaluationFailure,
        ) {
            evaluation.evaluate(enrichedUser.toEvaluationContext(), sortedFlagConfigs)
        }
        Logger.d("evaluate - user=$enrichedUser, result=$evaluationResults")
        assignmentService?.track(Assignment(user, evaluationResults))
        return evaluationResults.toVariants()
    }

    private fun enrichUser(user: ExperimentUser, flagConfigs: Map<String, EvaluationFlag>): ExperimentUser {
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

    private fun createAssignmentService(deploymentKey: String): AssignmentService? {
        if (config.assignmentConfiguration == null) return null
        return AmplitudeAssignmentService(
            Amplitude.getInstance(deploymentKey).apply {
                init(config.assignmentConfiguration.apiKey)
                setEventUploadThreshold(config.assignmentConfiguration.eventUploadThreshold)
                setEventUploadPeriodMillis(config.assignmentConfiguration.eventUploadPeriodMillis)
                useBatchMode(config.assignmentConfiguration.useBatchMode)
                setOptions(Options().setMinIdLength(1))
                for (middleware in config.assignmentConfiguration.middleware) {
                    addEventMiddleware(middleware)
                }
            },
            InMemoryAssignmentFilter(config.assignmentConfiguration.filterCapacity),
            metricsWrapper
        )
    }

    private fun createCohortStorage(): CohortStorage {
        if (config.proxyConfiguration == null) return InMemoryCohortStorage()
        return ProxyCohortStorage(
            config.proxyConfiguration,
            ProxyCohortMembershipApi(deploymentKey, config.proxyConfiguration.proxyUrl.toHttpUrl(), httpClient)
        )
    }
}
