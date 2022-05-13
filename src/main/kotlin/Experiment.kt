package com.amplitude.experiment

import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.SystemLogger

object Experiment {

    private const val DEFAULT_INSTANCE = "\$default_instance"
    private val remoteInstances = mutableMapOf<String, RemoteEvaluationClient>()
    private val localInstances = mutableMapOf<String, LocalEvaluationClient>()

    /**
     * Initializes a singleton [RemoteEvaluationClient] instance. Subsequent calls will return the
     * same instance, regardless of api key or config. However, It is advised to inject the client
     * inside your application rather than re-initializing
     *
     * @param apiKey  The API key. This can be found in the Experiment settings and should not be null or empty.
     * @param config see [RemoteEvaluationConfig] for configuration options
     */
    @JvmStatic
    fun initializeRemote(
        apiKey: String,
        config: RemoteEvaluationConfig
    ): RemoteEvaluationClient = synchronized(remoteInstances) {
        val instanceName = DEFAULT_INSTANCE
        return when (val instance = remoteInstances[instanceName]) {
            null -> {
                Logger.implementation = SystemLogger(config.debug)
                val newInstance = RemoteEvaluationClient(
                    apiKey,
                    config,
                )
                remoteInstances[instanceName] = newInstance
                newInstance
            }
            else -> instance
        }
    }
    /**
     * Initializes a singleton [LocalEvaluationClient] instance. Subsequent calls will return the
     * same instance, regardless of api key or config. However, It is advised to inject the client
     * inside your application rather than re-initializing
     *
     * @param apiKey  The API key. This can be found in the Experiment settings and should not be null or empty.
     * @param config see [LocalEvaluationConfig] for configuration options
     */
    @JvmStatic
    fun initializeLocal(
        apiKey: String,
        config: LocalEvaluationConfig,
    ): LocalEvaluationClient = synchronized(localInstances) {
        val instanceName = DEFAULT_INSTANCE
        return when (val instance = localInstances[instanceName]) {
            null -> {
                Logger.implementation = SystemLogger(config.debug)
                val newInstance = LocalEvaluationClient(
                    apiKey,
                    config,
                )
                localInstances[instanceName] = newInstance
                newInstance
            }
            else -> instance
        }
    }
}
