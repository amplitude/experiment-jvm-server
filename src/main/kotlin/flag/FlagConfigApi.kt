package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.EvaluationMode
import com.amplitude.experiment.evaluation.FlagConfig

internal data class GetFlagConfigsRequest(
    val evaluationMode: EvaluationMode,
)

internal typealias GetFlagConfigsResponse = Map<String, FlagConfig>

internal interface FlagConfigApi {
    fun getFlagConfigs(request: GetFlagConfigsRequest): GetFlagConfigsResponse
}
