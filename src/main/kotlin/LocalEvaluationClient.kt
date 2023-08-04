@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment

import com.amplitude.Amplitude
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
import com.amplitude.experiment.evaluation.FlagConfig
import com.amplitude.experiment.evaluation.FlagResult
import com.amplitude.experiment.evaluation.serialization.SerialVariant
import com.amplitude.experiment.flag.FlagConfigStorage
import com.amplitude.experiment.flag.HybridFlagConfigApi
import com.amplitude.experiment.flag.InMemoryFlagConfigStorage
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.getCohortIds
import com.amplitude.experiment.util.toSerialExperimentUser
import com.amplitude.experiment.util.toVariant
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
        config,
        httpClient,
        HybridFlagConfigApi(
            deploymentKey,
            config.serverUrl.toHttpUrl(),
            config.proxyConfiguration?.proxyUrl?.toHttpUrl(),
            httpClient,
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
    fun evaluate(user: ExperimentUser, flagKeys: List<String> = listOf()): Map<String, Variant> {
        val flagConfigs = flagConfigStorage.getFlagConfigs()
        val enrichedUser = enrichUser(user, flagConfigs)
        val evaluationResults = wrapMetrics(
            metric = metricsWrapper::onEvaluation,
            failure = metricsWrapper::onEvaluationFailure,
        ) {
            evaluation.evaluate(flagConfigs, enrichedUser.toSerialExperimentUser().convert())
        }
        Logger.d("evaluate - user=$enrichedUser, result=$evaluationResults")
        val assignmentResults = mutableMapOf<String, FlagResult>()
        val results = evaluationResults.filter { entry ->
            val isVariant = !entry.value.isDefaultVariant
            val isIncluded = (flagKeys.isEmpty() || flagKeys.contains(entry.key))
            val isDeployed = entry.value.deployed
            if (isIncluded || entry.value.type == "mutual-exclusion-group" || entry.value.type == "holdout-group") {
                assignmentResults[entry.key] = entry.value
            }
            isVariant && isIncluded && isDeployed
        }.map { entry ->
            entry.key to SerialVariant(entry.value.variant).toVariant()
        }.toMap()
        assignmentService?.track(Assignment(user, assignmentResults))
        return results;
    }

    private fun enrichUser(user: ExperimentUser, flagConfigs: List<FlagConfig>): ExperimentUser {
        val cohortIds = flagConfigs.getCohortIds()
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
