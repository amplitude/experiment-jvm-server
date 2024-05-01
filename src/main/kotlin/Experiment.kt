package com.amplitude.experiment

import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.SystemLogger
import java.util.concurrent.Executors

internal const val LIBRARY_VERSION = "1.2.0-proxy.9"

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
    ): RemoteEvaluationClient = synchronized(remoteInstances) {
        return when (val instance = remoteInstances[apiKey]) {
            null -> {
                Logger.implementation = SystemLogger(config.debug)
                val newInstance = RemoteEvaluationClient(
                    apiKey,
                    config,
                )
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
                Logger.implementation = SystemLogger(config.debug)
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
