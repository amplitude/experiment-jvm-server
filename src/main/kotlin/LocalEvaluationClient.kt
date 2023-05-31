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
    private val assignmentService: AssignmentService? = createAssignmentService()
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
        deploymentRunner.start()
    }

    fun stop() {
        deploymentRunner.stop()
    }

    @JvmOverloads
    fun evaluate(user: ExperimentUser, flagKeys: List<String> = listOf()): Map<String, Variant> {
        val flagConfigs = flagConfigStorage.getFlagConfigs()
        val enrichedUser = enrichUser(user, flagConfigs)
        val flagResults = wrapMetrics(
            metric = metricsWrapper::onEvaluation,
            failure = metricsWrapper::onEvaluationFailure,
        ) {
            evaluation.evaluate(flagConfigs, enrichedUser.toSerialExperimentUser().convert())
        }
        Logger.d("evaluate - user=$enrichedUser, result=$flagResults")
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

    private fun createAssignmentService(): AssignmentService? {
        if (config.assignmentConfiguration == null) return null
        return AmplitudeAssignmentService(
            Amplitude.getInstance("experiment").apply {
                setEventUploadThreshold(config.assignmentConfiguration.eventUploadThreshold)
                setEventUploadPeriodMillis(config.assignmentConfiguration.eventUploadPeriodMillis)
                useBatchMode(config.assignmentConfiguration.useBatchMode)
                init(config.assignmentConfiguration.apiKey)
            },
            InMemoryAssignmentFilter(config.assignmentConfiguration.filterCapacity),
            metricsWrapper
        )
    }

    private fun createCohortStorage(): CohortStorage {
        if (config.proxyConfiguration == null) return InMemoryCohortStorage()
        return ProxyCohortStorage(config.proxyConfiguration, ProxyCohortMembershipApi(deploymentKey, config.proxyConfiguration.proxyUrl.toHttpUrl(), httpClient))
    }
}
