package com.amplitude.experiment.flag

import com.amplitude.experiment.LIBRARY_VERSION
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.util.request
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.CompletableFuture

internal object GetFlagConfigsRequest

internal typealias GetFlagConfigsResponse = List<EvaluationFlag>

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
            .addPathSegments("sdk/v2/flags")
            .addQueryParameter("v", "0")
            .build()
        return httpClient.request<List<EvaluationFlag>>(
            Request.Builder()
                .get()
                .url(url)
                .addHeader("Authorization", "Api-Key $deploymentKey")
                .addHeader("X-Amp-Exp-Library", "experiment-jvm-server/$LIBRARY_VERSION")
                .build()
        )
    }
}
