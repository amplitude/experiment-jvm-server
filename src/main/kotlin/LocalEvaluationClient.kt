package com.amplitude.experiment

import com.amplitude.Amplitude
import com.amplitude.experiment.assignment.AmplitudeAssignmentService
import com.amplitude.experiment.assignment.Assignment
import com.amplitude.experiment.assignment.AssignmentService
import com.amplitude.experiment.assignment.InMemoryAssignmentFilter
import com.amplitude.experiment.evaluation.EvaluationEngine
import com.amplitude.experiment.evaluation.EvaluationEngineImpl
import com.amplitude.experiment.evaluation.FlagResult
import com.amplitude.experiment.evaluation.serialization.SerialVariant
import com.amplitude.experiment.flag.FlagConfigApiImpl
import com.amplitude.experiment.flag.FlagConfigService
import com.amplitude.experiment.flag.FlagConfigServiceConfig
import com.amplitude.experiment.flag.FlagConfigServiceImpl
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.toSerialExperimentUser
import com.amplitude.experiment.util.toVariant
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class LocalEvaluationClient internal constructor(
    private val apiKey: String,
    private val config: LocalEvaluationConfig = LocalEvaluationConfig(),
) {
    private val startLock = Once()
    private val httpClient = OkHttpClient()
    private val assignmentService: AssignmentService? = createAssignmentService()
    private val serverUrl: HttpUrl = config.serverUrl.toHttpUrl()
    private val evaluation: EvaluationEngine = EvaluationEngineImpl()
    private val flagConfigService: FlagConfigService = FlagConfigServiceImpl(
        FlagConfigServiceConfig(config.flagConfigPollerIntervalMillis),
        FlagConfigApiImpl(apiKey, serverUrl, httpClient),
    )

    fun start() {
        startLock.once {
            flagConfigService.start()
        }
    }

    private fun createAssignmentService(): AssignmentService? {
        if (config.assignmentConfiguration == null) return null
        return AmplitudeAssignmentService(
            Amplitude.getInstance().apply {
                init(config.assignmentConfiguration.apiKey)
                setEventUploadThreshold(config.assignmentConfiguration.eventUploadThreshold)
                setEventUploadPeriodMillis(config.assignmentConfiguration.eventUploadPeriodMillis)
                useBatchMode(config.assignmentConfiguration.useBatchMode)
            },
            InMemoryAssignmentFilter(config.assignmentConfiguration.cacheCapacity),
        )
    }

    @JvmOverloads
    fun evaluate(user: ExperimentUser, flagKeys: List<String> = listOf()): Map<String, Variant> {
        val flagConfigs = flagConfigService.getFlagConfigs()
        val flagResults = evaluation.evaluate(flagConfigs, user.toSerialExperimentUser().convert())
        val assignmentResults = mutableMapOf<String, FlagResult>()
        val results = flagResults.filter { entry ->
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
        return results
    }
}
