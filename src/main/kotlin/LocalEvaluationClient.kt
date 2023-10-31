@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment

import com.amplitude.Amplitude
import com.amplitude.Options
import com.amplitude.experiment.assignment.AmplitudeAssignmentService
import com.amplitude.experiment.assignment.Assignment
import com.amplitude.experiment.assignment.AssignmentService
import com.amplitude.experiment.assignment.InMemoryAssignmentFilter
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.cohort.InMemoryCohortStorage
import com.amplitude.experiment.cohort.ProxyCohortMembershipApi
import com.amplitude.experiment.cohort.ProxyCohortStorage
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
import com.amplitude.experiment.util.getCohortIds
import com.amplitude.experiment.util.toEvaluationContext
import com.amplitude.experiment.util.toVariant
import com.amplitude.experiment.util.toVariants
import com.amplitude.experiment.util.wrapMetrics
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class LocalEvaluationClient internal constructor(
    private val deploymentKey: String,
    private val config: LocalEvaluationConfig = LocalEvaluationConfig(),
) {

    private val metricsWrapper = LocalEvaluationMetricsWrapper(config.metrics)
    private val evaluation: EvaluationEngine = EvaluationEngineImpl()
    private val httpClient = OkHttpClient()
    private val assignmentService: AssignmentService? = createAssignmentService(deploymentKey)
    private val cohortStorage: CohortStorage = createCohortStorage()
    private val flagConfigStorage: FlagConfigStorage = InMemoryFlagConfigStorage()
    private val deploymentRunner = DeploymentRunner(
        deploymentKey,
        config,
        httpClient,
        FlagConfigApiV2(
            deploymentKey = deploymentKey,
            serverUrl = config.serverUrl.toHttpUrl(),
            proxyUrl = config.proxyConfiguration?.proxyUrl?.toHttpUrl(),
            httpClient = httpClient,
        ),
        flagConfigStorage,
        cohortStorage,
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
        val cohortIds = flagConfigs.values.getCohortIds()
        return if (user.userId == null || cohortIds.isEmpty()) {
            user
        } else {
            user.copyToBuilder().apply {
                cohortIds(cohortStorage.getCohortsForUser(user.userId, cohortIds))
            }.build()
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
