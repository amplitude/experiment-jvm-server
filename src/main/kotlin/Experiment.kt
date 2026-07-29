package com.amplitude.experiment

import com.amplitude.experiment.util.Logger
import okhttp3.OkHttpClient
import java.util.concurrent.Executors

internal const val LIBRARY_VERSION = "1.8.3"

object Experiment {

    internal val scheduler = Executors.newSingleThreadScheduledExecutor()

    private val remoteInstances = mutableMapOf<String, RemoteEvaluationClient>()
    private val localInstances = mutableMapOf<String, LocalEvaluationClient>()

    /**
     * Initializes a singleton [RemoteEvaluationClient] instance. Subsequent calls will return the
     * same instance, regardless of api key or config.
     *
     * @param apiKey  The API key. This can be found in the Experiment settings and should not be null or empty.
     * @param config see [RemoteEvaluationConfig] for configuration options
     */
    @JvmStatic
    @JvmOverloads
    fun initializeRemote(
        apiKey: String,
        config: RemoteEvaluationConfig = RemoteEvaluationConfig()
    ): RemoteEvaluationClient = getOrCreateRemote(apiKey, config) {
        RemoteEvaluationClient(
            apiKey,
            config,
        )
    }

    /**
     * Initializes a singleton [RemoteEvaluationClient] with a caller-provided [OkHttpClient].
     * Subsequent calls with the same API key return the existing instance, regardless of config or
     * HTTP client.
     *
     * The caller owns the HTTP client's lifecycle. When a custom client is provided,
     * [RemoteEvaluationConfig.httpProxy] is ignored; configure the proxy on [httpClient] instead.
     *
     * @param apiKey The API key. This can be found in the Experiment settings and should not be null or empty.
     * @param config see [RemoteEvaluationConfig] for configuration options
     * @param httpClient the HTTP client used for remote evaluation requests
     */
    @JvmStatic
    fun initializeRemote(
        apiKey: String,
        config: RemoteEvaluationConfig,
        httpClient: OkHttpClient,
    ): RemoteEvaluationClient = getOrCreateRemote(apiKey, config) {
        RemoteEvaluationClient(
            apiKey,
            config,
            httpClient,
        )
    }

    private fun getOrCreateRemote(
        apiKey: String,
        config: RemoteEvaluationConfig,
        createClient: () -> RemoteEvaluationClient,
    ): RemoteEvaluationClient = synchronized(remoteInstances) {
        return when (val instance = remoteInstances[apiKey]) {
            null -> {
                Logger.configure(config.logLevel, config.loggerProvider)
                val newInstance = createClient()
                remoteInstances[apiKey] = newInstance
                newInstance
            }
            else -> instance
        }
    }

    /**
     * Initializes a singleton [LocalEvaluationClient] instance. Subsequent calls will return the
     * same instance, regardless of api key or config.
     *
     * @param apiKey  The API key. This can be found in the Experiment settings and should not be null or empty.
     * @param config see [LocalEvaluationConfig] for configuration options
     */
    @JvmStatic
    @JvmOverloads
    fun initializeLocal(
        apiKey: String,
        config: LocalEvaluationConfig = LocalEvaluationConfig(),
    ): LocalEvaluationClient = synchronized(localInstances) {
        return when (val instance = localInstances[apiKey]) {
            null -> {
                Logger.configure(config.logLevel, config.loggerProvider)
                val newInstance = LocalEvaluationClient(
                    apiKey,
                    config,
                )
                localInstances[apiKey] = newInstance
                newInstance
            }
            else -> instance
        }
    }
}
