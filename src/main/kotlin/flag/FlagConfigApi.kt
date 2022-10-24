package com.amplitude.experiment.flag

import com.amplitude.experiment.LIBRARY_VERSION
import com.amplitude.experiment.evaluation.EvaluationMode
import com.amplitude.experiment.evaluation.FlagConfig
import com.amplitude.experiment.evaluation.serialization.SerialFlagConfig
import com.amplitude.experiment.util.request
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CompletableFuture

internal data class GetFlagConfigsRequest(
    val evaluationMode: EvaluationMode,
)

internal typealias GetFlagConfigsResponse = Map<String, FlagConfig>

internal interface FlagConfigApi {
    fun getFlagConfigs(request: GetFlagConfigsRequest): CompletableFuture<GetFlagConfigsResponse>
}

internal class FlagConfigApiImpl(
    private val deploymentKey: String,
    private val serverUrl: HttpUrl,
    private val httpClient: OkHttpClient,
) : FlagConfigApi {

    override fun getFlagConfigs(request: GetFlagConfigsRequest): CompletableFuture<GetFlagConfigsResponse> {
        val url = serverUrl.newBuilder()
            .addPathSegments("sdk/rules")
            .build()
        return httpClient.request<List<SerialFlagConfig>>(
            Request.Builder()
                .get()
                .url(url)
                .addHeader("Authorization", "Api-Key $deploymentKey")
                .addHeader("X-Amp-Exp-Library", "experiment-jvm-server/$LIBRARY_VERSION")
                .build()
        ).thenApply { result ->
            result.associate {
                it.flagKey to it.convert()
            }
        }
    }
}
