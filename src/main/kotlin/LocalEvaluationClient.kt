package com.amplitude.experiment

import com.amplitude.experiment.evaluation.EvaluationEngine
import com.amplitude.experiment.evaluation.EvaluationEngineImpl
import com.amplitude.experiment.evaluation.FlagConfig
import com.amplitude.experiment.evaluation.serialization.SerialFlagConfig
import com.amplitude.experiment.evaluation.serialization.SerialVariant
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.toSerialExperimentUser
import com.amplitude.experiment.util.toVariant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val json = Json {
    ignoreUnknownKeys = true
}

class LocalEvaluationClient internal constructor(
    private val apiKey: String,
    private val config: LocalEvaluationConfig = LocalEvaluationConfig(),
) {

    private val startLock = Any()
    private var started = false
    private val httpClient = OkHttpClient()
    private val serverUrl: HttpUrl = config.serverUrl.toHttpUrl()
    private val poller = Executors.newSingleThreadScheduledExecutor()
    private val evaluation: EvaluationEngine = EvaluationEngineImpl()
    private val rulesLock = ReentrantReadWriteLock()
    private var rules: Map<String, FlagConfig> = mapOf()

    fun start() {
        synchronized(startLock) {
            if (started) {
                return
            } else {
                started = true
            }
        }

        // Poller
        poller.scheduleAtFixedRate(
            { updateRules() },
            config.flagConfigPollerIntervalMillis,
            config.flagConfigPollerIntervalMillis,
            TimeUnit.MILLISECONDS
        )

        // Initial rules
        updateRules().join()
    }

    @JvmOverloads
    fun evaluate(user: ExperimentUser, flagKeys: List<String> = listOf()): Map<String, Variant> {
        val flagConfigs = rulesLock.read {
            if (flagKeys.isEmpty()) {
                rules.values.toList()
            } else {
                flagKeys.mapNotNull { flagKey ->
                    rules[flagKey]
                }
            }
        }

        val flagResults = evaluation.evaluate(flagConfigs, user.toSerialExperimentUser().convert())
        return flagResults.mapNotNull { entry ->
            entry.key to SerialVariant(entry.value.variant).toVariant()
        }.toMap()
    }

    private fun updateRules(): CompletableFuture<Map<String, FlagConfig>> {
        return doRules().thenApply { newRules ->
            rulesLock.write {
                rules = newRules
            }
            newRules
        }
    }

    private fun doRules(): CompletableFuture<Map<String, FlagConfig>> {
        val url = serverUrl.newBuilder()
            .addPathSegments("sdk/rules")
            .build()
        val request = Request.Builder()
            .get()
            .url(url)
            .addHeader("Authorization", "Api-Key $apiKey")
            .build()
        val future = CompletableFuture<Map<String, FlagConfig>>()
        val call = httpClient.newCall(request)
        call.timeout().timeout(config.flagConfigPollerRequestTimeoutMillis, TimeUnit.MILLISECONDS)
        // Execute request and handle response
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                try {
                    Logger.d("Received flag configs response: $response")
                    val variants = response.use {
                        if (!response.isSuccessful) {
                            throw IOException("flag configs error response: $response")
                        }
                        parseFlagConfigsResponse(response.body?.string() ?: "")
                    }
                    future.complete(variants)
                } catch (e: IOException) {
                    onFailure(call, e)
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                future.completeExceptionally(e)
            }
        })
        return future
    }
}

internal fun parseFlagConfigsResponse(jsonString: String) =
    json.decodeFromString<List<SerialFlagConfig>>(
        jsonString
    ).associate {
        it.flagKey to it.convert()
    }
