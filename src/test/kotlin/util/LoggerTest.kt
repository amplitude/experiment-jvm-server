package com.amplitude.experiment.util

import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class LoggerTest {

    private lateinit var mockLogger: LoggerProvider

    @Before
    fun setUp() {
        mockLogger = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        // Reset logger to default state after each test
        Logger.configure(LogLevel.ERROR, DefaultLogger())
    }

    // ==================== LogLevel Priority Tests ====================

    @Test
    fun `test VERBOSE level logs all messages`() {
        Logger.configure(LogLevel.VERBOSE, mockLogger)

        Logger.verbose("verbose msg")
        Logger.debug("debug msg")
        Logger.info("info msg")
        Logger.warn("warn msg")
        Logger.error("error msg")

        verify(exactly = 1) { mockLogger.verbose("verbose msg") }
        verify(exactly = 1) { mockLogger.debug("debug msg") }
        verify(exactly = 1) { mockLogger.info("info msg") }
        verify(exactly = 1) { mockLogger.warn("warn msg") }
        verify(exactly = 1) { mockLogger.error("error msg") }
    }

    @Test
    fun `test DEBUG level filters verbose only`() {
        Logger.configure(LogLevel.DEBUG, mockLogger)

        Logger.verbose("verbose msg")
        Logger.debug("debug msg")
        Logger.info("info msg")
        Logger.warn("warn msg")
        Logger.error("error msg")

        verify(exactly = 0) { mockLogger.verbose(any()) }
        verify(exactly = 1) { mockLogger.debug("debug msg") }
        verify(exactly = 1) { mockLogger.info("info msg") }
        verify(exactly = 1) { mockLogger.warn("warn msg") }
        verify(exactly = 1) { mockLogger.error("error msg") }
    }

    @Test
    fun `test INFO level filters verbose and debug`() {
        Logger.configure(LogLevel.INFO, mockLogger)

        Logger.verbose("verbose msg")
        Logger.debug("debug msg")
        Logger.info("info msg")
        Logger.warn("warn msg")
        Logger.error("error msg")

        verify(exactly = 0) { mockLogger.verbose(any()) }
        verify(exactly = 0) { mockLogger.debug(any()) }
        verify(exactly = 1) { mockLogger.info("info msg") }
        verify(exactly = 1) { mockLogger.warn("warn msg") }
        verify(exactly = 1) { mockLogger.error("error msg") }
    }

    @Test
    fun `test WARN level filters verbose debug and info`() {
        Logger.configure(LogLevel.WARN, mockLogger)

        Logger.verbose("verbose msg")
        Logger.debug("debug msg")
        Logger.info("info msg")
        Logger.warn("warn msg")
        Logger.error("error msg")

        verify(exactly = 0) { mockLogger.verbose(any()) }
        verify(exactly = 0) { mockLogger.debug(any()) }
        verify(exactly = 0) { mockLogger.info(any()) }
        verify(exactly = 1) { mockLogger.warn("warn msg") }
        verify(exactly = 1) { mockLogger.error("error msg") }
    }

    @Test
    fun `test ERROR level filters all except error`() {
        Logger.configure(LogLevel.ERROR, mockLogger)

        Logger.verbose("verbose msg")
        Logger.debug("debug msg")
        Logger.info("info msg")
        Logger.warn("warn msg")
        Logger.error("error msg")

        verify(exactly = 0) { mockLogger.verbose(any()) }
        verify(exactly = 0) { mockLogger.debug(any()) }
        verify(exactly = 0) { mockLogger.info(any()) }
        verify(exactly = 0) { mockLogger.warn(any()) }
        verify(exactly = 1) { mockLogger.error("error msg") }
    }

    @Test
    fun `test DISABLE level filters all messages`() {
        Logger.configure(LogLevel.DISABLE, mockLogger)

        Logger.verbose("verbose msg")
        Logger.debug("debug msg")
        Logger.info("info msg")
        Logger.warn("warn msg")
        Logger.error("error msg")

        verify(exactly = 0) { mockLogger.verbose(any()) }
        verify(exactly = 0) { mockLogger.debug(any()) }
        verify(exactly = 0) { mockLogger.info(any()) }
        verify(exactly = 0) { mockLogger.warn(any()) }
        verify(exactly = 0) { mockLogger.error(any()) }
    }

    @Test
    fun `test message is passed correctly`() {
        Logger.configure(LogLevel.INFO, mockLogger)

        Logger.info("test info message")

        verify { mockLogger.info("test info message") }
    }

    @Test
    fun `test error with null exception`() {
        Logger.configure(LogLevel.ERROR, mockLogger)

        Logger.error("test error message")

        verify { mockLogger.error("test error message") }
    }

    @Test
    fun `test null provider does not throw exception`() {
        Logger.configure(LogLevel.INFO, null)

        // Should not throw
        Logger.info("test message")
    }
}
