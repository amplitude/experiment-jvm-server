package com.amplitude.experiment.flag

import com.amplitude.experiment.LIBRARY_VERSION
import com.amplitude.experiment.evaluation.FlagConfig
import com.amplitude.experiment.evaluation.serialization.SerialFlagConfig
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.get
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

internal interface FlagConfigApi {
    fun getFlagConfigs(): List<FlagConfig>
}

internal class HybridFlagConfigApi(
    private val directApi: FlagConfigApi,
    private val proxyApi: FlagConfigApi?,
) : FlagConfigApi {

    constructor(
        deploymentKey: String,
        directUrl: HttpUrl,
        proxyUrl: HttpUrl?,
        httpClient: OkHttpClient,
    ) : this(
        directApi = DirectFlagConfigApi(deploymentKey, directUrl, httpClient),
        proxyApi = proxyUrl?.let { ProxyFlagConfigApi(deploymentKey, proxyUrl, httpClient) },
    )

    override fun getFlagConfigs(): List<FlagConfig> {
        if (proxyApi != null) {
            try {
                return proxyApi.getFlagConfigs()
            } catch (e: Exception) {
                Logger.e("Failed to get flag configs from proxy api.", e)
            }
        }
        return directApi.getFlagConfigs()
    }
}

internal class DirectFlagConfigApi(
    private val deploymentKey: String,
    private val serverUrl: HttpUrl,
    private val httpClient: OkHttpClient,
) : FlagConfigApi {

    override fun getFlagConfigs(): List<FlagConfig> {
        val response = httpClient.get<List<SerialFlagConfig>>(serverUrl, "sdk/v1/flags", headers = mapOf(
            "Authorization" to "Api-Key $deploymentKey",
            "X-Amp-Exp-Library" to "experiment-jvm-server/$LIBRARY_VERSION"
        ))
        return response.map { it.convert() }
    }
}

internal class ProxyFlagConfigApi(
    private val deploymentKey: String,
    private val serverUrl: HttpUrl,
    private val httpClient: OkHttpClient,
) : FlagConfigApi {

    override fun getFlagConfigs(): List<FlagConfig> {
        val response = httpClient.get<List<SerialFlagConfig>>(serverUrl, "/sdk/v1/deployments/$deploymentKey/flags")
        return response.map { it.convert() }
    }
}
