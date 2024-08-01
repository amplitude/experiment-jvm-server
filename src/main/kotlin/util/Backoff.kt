package com.amplitude.experiment.util

import com.amplitude.experiment.Experiment
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

internal fun <T> backoff(
    config: BackoffConfig,
    function: () -> CompletableFuture<T>,
): CompletableFuture<T> {
    return Backoff<T>(config).start(function, null)
}

internal fun <T> backoff(
    config: BackoffConfig,
    function: () -> CompletableFuture<T>,
    retry: (Throwable) -> Boolean,
): CompletableFuture<T> {
    return Backoff<T>(config).start(function, retry)
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

    fun start(
        function: () -> CompletableFuture<T>,
        retry: ((Throwable) -> Boolean)?
    ): CompletableFuture<T> {
        backoff(0, config.min, function, retry)
        return completableFuture
    }

    private fun backoff(
        attempt: Int,
        delay: Long,
        function: () -> CompletableFuture<T>,
        retry: ((Throwable) -> Boolean)? = null
    ) {
        Experiment.scheduler.schedule({
            if (completableFuture.isCancelled) {
                return@schedule
            }
            function.invoke().whenComplete { result, t ->
                if (t != null || result == null) {
                    val unwrapped = when (t) {
                        is CompletionException -> t.cause ?: t
                        else -> t
                    }
                    val shouldRetry = retry?.invoke(unwrapped) ?: true
                    val nextAttempt = attempt + 1
                    if (shouldRetry && nextAttempt < config.attempts) {
                        val nextDelay = min(delay * config.scalar, config.max.toDouble()).toLong()
                        val jitter = Random.nextLong(
                            (nextDelay - (nextDelay*0.1).toLong()) * -1,
                            nextDelay + (nextDelay+0.1).toLong()
                        )
                        backoff(nextAttempt, nextDelay + jitter, function, retry)
                    } else {
                        completableFuture.completeExceptionally(unwrapped)
                    }
                } else {
                    completableFuture.complete(result)
                }
            }
        }, delay, TimeUnit.MILLISECONDS)
    }
}
