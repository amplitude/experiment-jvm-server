package com.amplitude.experiment.flag

import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.BackoffConfig
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.backoff
import com.amplitude.experiment.util.get
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ExecutionException

internal interface FlagConfigApi {
    fun getFlagConfigs(): List<EvaluationFlag>
}

internal class DynamicFlagConfigApi(
    private val deploymentKey: String,
    private val serverUrl: HttpUrl,
    private val proxyUrl: HttpUrl?,
    private val httpClient: OkHttpClient,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) : FlagConfigApi {

    private val backoffConfig = BackoffConfig(
        attempts = 3,
        min = 500,
        max = 2000,
        scalar = 2.0,
    )

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
        val future = backoff(backoffConfig) {
            httpClient.get<List<EvaluationFlag>>(
                serverUrl = url,
                path = "sdk/v2/flags",
                headers = mapOf(
                    "Authorization" to "Api-Key $deploymentKey",
                ),
                queries = mapOf("v" to "0")
            )
        }
        try {
            return future.get()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}
