package com.amplitude.experiment

import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.SerializableVariant
import com.amplitude.experiment.util.toSerializableUser
import com.amplitude.experiment.util.toVariant
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.CompletableFuture
import kotlin.math.min
import kotlin.math.pow

class ExperimentClient internal constructor(
    private val apiKey: String,
    private val config: ExperimentConfig,
) {
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val supervisor = SupervisorJob()
    private val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    private val httpClient = HttpClient(CIO) {
        expectSuccess = true
        install(Logging) {
            level = LogLevel.ALL
            logger = io.ktor.client.plugins.logging.Logger.SIMPLE
        }
        install(HttpTimeout) {
            requestTimeoutMillis = config.fetchTimeoutMillis
        }
        install(HttpRequestRetry)
    }

    @Throws(Exception::class)
    fun fetch(user: ExperimentUser): Map<String, Variant> = runBlocking {
        withContext(supervisor + dispatcher) {
            doFetch(user)
        }
    }

    fun fetchAsync(user: ExperimentUser): CompletableFuture<Map<String, Variant>> = runBlocking {
        async(supervisor + dispatcher) {
            doFetch(user)
        }.asCompletableFuture()
    }

    private suspend fun doFetch(
        user: ExperimentUser
    ): Map<String, Variant> {
        if (user.userId == null && user.deviceId == null) {
            Logger.w("user id and device id are null; amplitude may not resolve identity")
        }
        Logger.d("Fetch variants for user: $user")
        val url = URLBuilder(config.serverUrl).apply {
            encodedPath = "/sdk/vardata"
        }.buildString()
        val response = httpClient.post(url) {
            headers {
                append(HttpHeaders.Authorization, "Api-Key $apiKey")
            }
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(user.toSerializableUser()))
            retry {
                maxRetries = config.fetchRetries
                retryIf { _, response ->
                    !response.status.isSuccess()
                }
                retryOnExceptionIf { _, _ ->
                    true
                }
                delayMillis { retry ->
                    min(
                        config.fetchRetryBackoffMinMillis * config.fetchRetryBackoffScalar.pow(retry).toLong(),
                        config.fetchRetryBackoffMaxMillis,
                    )
                }
            }
        }
        return json.decodeFromString<HashMap<String, SerializableVariant>>(
            response.bodyAsText()
        ).mapValues { it.value.toVariant() }
    }
}
