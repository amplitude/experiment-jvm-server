package com.amplitude.experiment.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.min

internal fun <T> backoff(
    config: BackoffConfig,
    function: () -> CompletableFuture<T>,
): CompletableFuture<T> {
    return Backoff<T>(config).start(function)
}

internal data class BackoffConfig(
    val attempts: Int,
    val min: Long,
    val max: Long,
    val scalar: Double,
)

private class Backoff<T>(
    private val config: BackoffConfig,
) {

    private val completableFuture = CompletableFuture<T>()

    fun start(function: () -> CompletableFuture<T>): CompletableFuture<T> {
        backoff(0, config.min, function)
        return completableFuture
    }

    private fun backoff(
        attempt: Int,
        delay: Long,
        function: () -> CompletableFuture<T>
    ) {
        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute {
            if (completableFuture.isCancelled) {
                return@execute
            }
            function.invoke().whenComplete { variants, t ->
                if (t != null || variants == null) {
                    // Retry the request function
                    val nextAttempt = attempt + 1
                    if (nextAttempt < config.attempts) {
                        val nextDelay = min(delay * config.scalar, config.max.toDouble()).toLong()
                        backoff(nextAttempt, nextDelay, function)
                    } else {
                        completableFuture.completeExceptionally(t)
                    }
                } else {
                    completableFuture.complete(variants)
                }
            }
        }
    }
}
