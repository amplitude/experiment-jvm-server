package com.amplitude.experiment

import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.SystemLogger
import java.util.concurrent.Executors

internal const val LIBRARY_VERSION = "1.2.4"

object Experiment {

    internal val scheduler = Executors.newSingleThreadScheduledExecutor()

    private val remoteInstances = mutableMapOf<String, RemoteEvaluationClient>()
    private val localInstances = mutableMapOf<String, LocalEvaluationClient>()

    /**
     * Initializes a singleton [RemoteEvaluationClient] instance. Subsequent calls will return the
     * same instance, regardless of api key or config.
     *
     * @param apiKey  The Amplitude Project API key. This can be found in the Organization Settings -> Projects,
     * and should not be null or empty. If a deployment key is provided in the config, it will be used instead.
     * @param config see [RemoteEvaluationConfig] for configuration options
     */
    @JvmStatic
    @JvmOverloads
    fun initializeRemote(
        apiKey: String,
        config: RemoteEvaluationConfig = RemoteEvaluationConfig()
    ): RemoteEvaluationClient = synchronized(remoteInstances) {
        val usedKey = config.deploymentKey ?: apiKey
        return when (val instance = remoteInstances[usedKey]) {
            null -> {
                Logger.implementation = SystemLogger(config.debug)
                val newInstance = RemoteEvaluationClient(
                    usedKey,
                    config,
                )
                remoteInstances[usedKey] = newInstance
                newInstance
            }
            else -> instance
        }
    }
    /**
     * Initializes a singleton [LocalEvaluationClient] instance. Subsequent calls will return the
     * same instance, regardless of api key or config.
     *
     * @param apiKey  The Amplitude Project API key. This can be found in the Organization Settings -> Projects,
     * and should not be null or empty. If a deployment key is provided in the config, it will be used instead.
     * @param config see [LocalEvaluationConfig] for configuration options
     */
    @JvmStatic
    @JvmOverloads
    fun initializeLocal(
        apiKey: String,
        config: LocalEvaluationConfig = LocalEvaluationConfig(),
    ): LocalEvaluationClient = synchronized(localInstances) {
        val usedKey = config.deploymentKey ?: apiKey
        return when (val instance = localInstances[apiKey]) {
            null -> {
                Logger.implementation = SystemLogger(config.debug)
                val newInstance = LocalEvaluationClient(
                    usedKey,
                    config,
                )
                localInstances[usedKey] = newInstance
                newInstance
            }
            else -> instance
        }
    }
}
