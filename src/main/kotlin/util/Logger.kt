package com.amplitude.experiment.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val priority: Int) {
    VERBOSE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    DISABLE(5);
}

interface LoggerProvider {
    fun verbose(msg: String)
    fun debug(msg: String)
    fun info(msg: String)
    fun warn(msg: String)
    fun error(msg: String)
}

internal object Logger {

    private var logLevel: LogLevel = LogLevel.ERROR
    private var loggerProvider: LoggerProvider? = null

    internal fun configure(logLevel: LogLevel) {
        this.logLevel = logLevel
    }

    internal fun configure(logLevel: LogLevel, provider: LoggerProvider?) {
        this.logLevel = logLevel
        loggerProvider = provider
    }

    fun verbose(msg: String) {
        if (shouldLog(LogLevel.VERBOSE)) {
            loggerProvider?.verbose(msg)
        }
    }

    fun debug(msg: String) {
        if (shouldLog(LogLevel.DEBUG)) {
            loggerProvider?.debug(msg)
        }
    }

    fun info(msg: String) {
        if (shouldLog(LogLevel.INFO)) {
            loggerProvider?.info(msg)
        }
    }

    fun warn(msg: String, e: Throwable? = null) {
        if (shouldLog(LogLevel.WARN)) {
            if (e == null) {
                loggerProvider?.warn(msg)
            } else {
                loggerProvider?.warn("$msg\n${e.stackTraceToString()}")
            }
        }
    }

    fun error(msg: String, e: Throwable? = null) {
        if (shouldLog(LogLevel.ERROR)) {
            if (e == null) {
                loggerProvider?.error(msg)
            } else {
                loggerProvider?.error("$msg\n${e.stackTraceToString()}")
            }
        }
    }

    private fun shouldLog(logLevel: LogLevel): Boolean {
        return logLevel.priority >= this.logLevel.priority
    }
}

internal val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

internal fun timestamp(): String {
    return format.format(Date())
}

// Default Logger
internal class DefaultLogger : LoggerProvider {

    override fun verbose(msg: String) {
        println("[${timestamp()}] VERBOSE: $msg")
    }

    override fun debug(msg: String) {
        println("[${timestamp()}] DEBUG: $msg")
    }

    override fun info(msg: String) {
        println("[${timestamp()}] INFO: $msg")
    }

    override fun warn(msg: String) {
        println("[${timestamp()}] WARN: $msg")
    }

    override fun error(msg: String) {
        println("[${timestamp()}] ERROR: $msg")
    }
}
