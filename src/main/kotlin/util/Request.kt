package com.amplitude.experiment.util

import com.amplitude.experiment.LIBRARY_VERSION
import kotlinx.serialization.decodeFromString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.util.concurrent.CompletableFuture

internal inline fun <reified T> OkHttpClient.request(
    request: Request,
): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    val call = newCall(request)
    call.enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use {
                    if (!response.isSuccessful) {
                        throw IOException("$request - error response: $response")
                    }
                    val body = response.body?.string() ?: throw IOException("$request - null response body")
                    json.decodeFromString<T>(body)
                }
                future.complete(result)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        override fun onFailure(call: Call, e: IOException) {
            future.completeExceptionally(e)
        }
    })
    return future
}

internal open class HttpErrorResponseException(
    val code: Int,
) : IOException("Request resulted error response $code")

private fun OkHttpClient.submit(
    request: Request,
): CompletableFuture<Response> {
    val future = CompletableFuture<Response>()
    val call = newCall(request)
    call.enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            future.complete(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            future.completeExceptionally(e)
        }
    })
    return future
}

internal fun newGet(
    serverUrl: HttpUrl,
    path: String? = null,
    headers: Map<String, String>? = null,
    queries: Map<String, String>? = null,
): Request {
    val url = serverUrl.newBuilder().apply {
        if (path != null) {
            addPathSegments(path)
        }
        queries?.forEach {
            addQueryParameter(it.key, it.value)
        }
    }.build()
    val builder = Request.Builder().get().url(url)
    headers?.forEach {
        builder.addHeader(it.key, it.value)
    }
    builder.addHeader("X-Amp-Exp-Library", "experiment-jvm-server/$LIBRARY_VERSION")
    return builder.build()
}

internal inline fun <reified T> OkHttpClient.get(
    serverUrl: HttpUrl,
    path: String? = null,
    headers: Map<String, String>? = null,
    queries: Map<String, String>? = null,
    crossinline block: (Response) -> Unit,
): CompletableFuture<T> {
    val request = newGet(serverUrl, path, headers, queries)
    return submit(request).thenApply {
        it.use { response ->
            block(response)
            val body = response.body?.string() ?: throw IOException("null response body")
            json.decodeFromString<T>(body)
        }
    }
}

internal inline fun <reified T> OkHttpClient.get(
    serverUrl: HttpUrl,
    path: String? = null,
    headers: Map<String, String>? = null,
    queries: Map<String, String>? = null,
): CompletableFuture<T> {
    return this.get<T>(serverUrl, path, headers, queries) { response ->
        if (!response.isSuccessful) {
            throw HttpErrorResponseException(response.code)
        }
    }
}
