package com.amplitude.experiment.flag

import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.get
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

internal interface FlagConfigApi {
    fun getFlagConfigs(): List<EvaluationFlag>
}

internal class DirectFlagConfigApi(
    private val deploymentKey: String,
    private val serverUrl: HttpUrl,
    private val httpClient: OkHttpClient,
) : FlagConfigApi {

    override fun getFlagConfigs(): List<EvaluationFlag> {
        return httpClient.get<List<EvaluationFlag>>(
            serverUrl = serverUrl,
            path = "sdk/v2/flags",
            headers = mapOf(
                "Authorization" to "Api-Key $deploymentKey",
            ),
            queries = mapOf("v" to "0")
        ).get()
    }
}
