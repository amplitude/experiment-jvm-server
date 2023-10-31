package com.amplitude.experiment.flag

import com.amplitude.experiment.LIBRARY_VERSION
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.get
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

internal interface FlagConfigApi {
    fun getFlagConfigs(): List<EvaluationFlag>
}

internal class FlagConfigApiV2(
    private val deploymentKey: String,
    private val serverUrl: HttpUrl,
    private val proxyUrl: HttpUrl?,
    private val httpClient: OkHttpClient,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) : FlagConfigApi {

    override fun getFlagConfigs(): List<EvaluationFlag> {
        return if (proxyUrl != null) {
            try {
                getFlagConfigs(proxyUrl)
            } catch (e: Exception) {
                metrics.onFlagConfigFetchOriginFallback(e)
                getFlagConfigs(serverUrl)
            }
        } else {
            getFlagConfigs(serverUrl)
        }
    }

    private fun getFlagConfigs(url: HttpUrl): List<EvaluationFlag> {
        return httpClient.get<List<EvaluationFlag>>(
            url, "sdk/v2/flags",
            headers = mapOf(
                "Authorization" to "Api-Key $deploymentKey",
                "X-Amp-Exp-Library" to "experiment-jvm-server/$LIBRARY_VERSION"
            )
        )
    }
}
