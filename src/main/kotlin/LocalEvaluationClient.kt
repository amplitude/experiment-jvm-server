package com.amplitude.experiment

import com.amplitude.experiment.evaluation.EvaluationEngine
import com.amplitude.experiment.evaluation.EvaluationEngineImpl
import com.amplitude.experiment.evaluation.FlagConfig
import com.amplitude.experiment.evaluation.serialization.SerialFlagConfig
import com.amplitude.experiment.util.toJvmSerialVariant
import com.amplitude.experiment.util.toSerialExperimentUser
import com.amplitude.experiment.util.toVariant
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val json = Json {
    explicitNulls = false
    ignoreUnknownKeys = true
}

class LocalEvaluationClient @JvmOverloads constructor(
    private val apiKey: String,
    private val config: LocalEvaluationConfig = LocalEvaluationConfig(),
) {
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val supervisor = SupervisorJob()

    private val httpClient = HttpClient(CIO) {
        expectSuccess = true
        install(Logging) {
            level = LogLevel.ALL
            logger = Logger.SIMPLE
        }
        install(HttpTimeout)
    }

    private val evaluation: EvaluationEngine = EvaluationEngineImpl()
    private val rulesMutex = Mutex()
    private var rules: Map<String, FlagConfig> = mapOf()

    fun start() {
        runBlocking {
            val newRules = doRules()
            rulesMutex.withLock {
                rules = newRules
            }
            async(supervisor + dispatcher) {
                while (true) {
                    delay(config.flagConfigPollerIntervalMillis)
                    val newRules = doRules()
                    rulesMutex.withLock {
                        rules = newRules
                    }
                }
            }
        }
    }

    @JvmOverloads
    fun evaluate(user: ExperimentUser, flagKeys: List<String> = listOf()): Map<String, Variant> {
        val flagConfigs = runBlocking {
            rulesMutex.withLock { rules }.let { flagConfigs ->
                if (flagKeys.isEmpty()) {
                    flagConfigs.values.toList()
                } else {
                    flagKeys.mapNotNull { flagKey ->
                        flagConfigs[flagKey]
                    }
                }
            }
        }

        val flagResults = evaluation.evaluate(flagConfigs, user.toSerialExperimentUser().convert())
        return flagResults.mapNotNull { entry ->
            val variant = entry.value.variant.toJvmSerialVariant()?.toVariant()
            if (variant == null) {
                null
            } else {
                entry.key to variant
            }
        }.toMap()
    }

    private suspend fun doRules(): Map<String, FlagConfig> {
        val url = URLBuilder(config.serverUrl).apply {
            encodedPath = "/sdk/rules"
        }.buildString()
        val response = httpClient.get(url) {
            headers {
                append(HttpHeaders.Authorization, "Api-Key $apiKey")
            }
            timeout {
                requestTimeoutMillis = config.flagConfigPollerRequestTimeoutMillis
            }
        }
        return parseFlagConfigsResponse(response.bodyAsText())
    }
}

internal fun parseFlagConfigsResponse(jsonString: String) =
    json.decodeFromString<List<SerialFlagConfig>>(
        jsonString
    ).associate {
        it.flagKey to it.convert()
    }
