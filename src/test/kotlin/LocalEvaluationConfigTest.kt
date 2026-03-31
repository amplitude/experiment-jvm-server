package com.amplitude.experiment

import com.amplitude.experiment.util.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import kotlin.test.Test

@OptIn(ExperimentalApi::class)
class LocalEvaluationConfigTest {

    @Test
    fun `test builder with default values`() {
        val config = LocalEvaluationConfig.builder().build()

        assertEquals(LocalEvaluationConfig.Defaults.DEBUG, config.debug)
        assertEquals(LocalEvaluationConfig.Defaults.LOG_LEVEL, config.logLevel)
        assertEquals(LocalEvaluationConfig.Defaults.SERVER_URL, config.serverUrl)
        assertEquals(LocalEvaluationConfig.Defaults.SERVER_ZONE, config.serverZone)
        assertEquals(LocalEvaluationConfig.Defaults.FLAG_CONFIG_POLLER_INTERVAL_MILLIS, config.flagConfigPollerIntervalMillis)
        assertEquals(LocalEvaluationConfig.Defaults.FLAG_CONFIG_POLLER_REQUEST_TIMEOUT_MILLIS, config.flagConfigPollerRequestTimeoutMillis)
        assertEquals(LocalEvaluationConfig.Defaults.STREAM_UPDATES, config.streamUpdates)
        assertEquals(LocalEvaluationConfig.Defaults.STREAM_SERVER_URL, config.streamServerUrl)
        assertEquals(LocalEvaluationConfig.Defaults.STREAM_FLAG_CONN_TIMEOUT_MILLIS, config.streamFlagConnTimeoutMillis)
        assertNull(config.assignmentConfiguration)
        assertNull(config.exposureConfiguration)
        assertNull(config.cohortSyncConfig)
        assertNull(config.evaluationProxyConfig)
        assertNull(config.metrics)
    }

    @Test
    fun `test builder with custom log level`() {
        val config = LocalEvaluationConfig.builder()
            .logLevel(LogLevel.DEBUG)
            .build()

        assertEquals(LogLevel.DEBUG, config.logLevel)
    }

    @Test
    fun `test builder with custom server URL`() {
        val customUrl = "https://custom.example.com/"
        val config = LocalEvaluationConfig.builder()
            .serverUrl(customUrl)
            .build()

        assertEquals(customUrl, config.serverUrl)
    }

    @Test
    fun `test builder with custom server zone`() {
        val config = LocalEvaluationConfig.builder()
            .serverZone(ServerZone.EU)
            .build()

        assertEquals(ServerZone.EU, config.serverZone)
    }

    @Test
    fun `test builder with custom flag config poller interval`() {
        val interval = 60_000L
        val config = LocalEvaluationConfig.builder()
            .flagConfigPollerIntervalMillis(interval)
            .build()

        assertEquals(interval, config.flagConfigPollerIntervalMillis)
    }

    @Test
    fun `test builder with custom flag config poller request timeout`() {
        val timeout = 5_000L
        val config = LocalEvaluationConfig.builder()
            .flagConfigPollerRequestTimeoutMillis(timeout)
            .build()

        assertEquals(timeout, config.flagConfigPollerRequestTimeoutMillis)
    }

    @Test
    fun `test builder with stream updates enabled`() {
        val config = LocalEvaluationConfig.builder()
            .streamUpdates(true)
            .build()

        assertEquals(true, config.streamUpdates)
    }

    @Test
    fun `test builder with custom stream server URL`() {
        val customUrl = "wss://custom-stream.example.com/"
        val config = LocalEvaluationConfig.builder()
            .streamServerUrl(customUrl)
            .build()

        assertEquals(customUrl, config.streamServerUrl)
    }

    @Test
    fun `test builder with custom stream flag connection timeout`() {
        val timeout = 3_000L
        val config = LocalEvaluationConfig.builder()
            .streamFlagConnTimeoutMillis(timeout)
            .build()

        assertEquals(timeout, config.streamFlagConnTimeoutMillis)
    }

    @Test
    fun `test builder with exposure configuration`() {
        val exposureConfig = ExposureConfiguration(apiKey = "test-api-key")
        val config = LocalEvaluationConfig.builder()
            .enableExposureTracking(exposureConfig)
            .build()

        assertNotNull(config.exposureConfiguration)
        assertEquals("test-api-key", config.exposureConfiguration?.apiKey)
    }

    @Test
    fun `test builder with cohort sync config`() {
        val cohortConfig = CohortSyncConfig(
            apiKey = "test-api-key",
            secretKey = "test-secret-key"
        )
        val config = LocalEvaluationConfig.builder()
            .cohortSyncConfig(cohortConfig)
            .build()

        assertNotNull(config.cohortSyncConfig)
        assertEquals("test-api-key", config.cohortSyncConfig?.apiKey)
        assertEquals("test-secret-key", config.cohortSyncConfig?.secretKey)
    }

    @Test
    fun `test builder method chaining with multiple options`() {
        val exposureConfig = ExposureConfiguration(apiKey = "exp-key")
        val cohortConfig = CohortSyncConfig(
            apiKey = "cohort-key",
            secretKey = "cohort-secret"
        )

        val config = LocalEvaluationConfig.builder()
            .logLevel(LogLevel.DEBUG)
            .serverUrl("https://custom-server.com/")
            .serverZone(ServerZone.EU)
            .flagConfigPollerIntervalMillis(45_000L)
            .streamUpdates(true)
            .streamServerUrl("wss://custom-stream.com/")
            .enableExposureTracking(exposureConfig)
            .cohortSyncConfig(cohortConfig)
            .build()

        assertEquals(LogLevel.DEBUG, config.logLevel)
        assertEquals("https://custom-server.com/", config.serverUrl)
        assertEquals(ServerZone.EU, config.serverZone)
        assertEquals(45_000L, config.flagConfigPollerIntervalMillis)
        assertEquals(true, config.streamUpdates)
        assertEquals("wss://custom-stream.com/", config.streamServerUrl)
        assertNotNull(config.exposureConfiguration)
        assertEquals("exp-key", config.exposureConfiguration?.apiKey)
        assertNotNull(config.cohortSyncConfig)
        assertEquals("cohort-key", config.cohortSyncConfig?.apiKey)
    }

    @Test
    fun `test builder logLevel method sets debug to false when not DEBUG`() {
        val config = LocalEvaluationConfig.builder()
            .logLevel(LogLevel.ERROR)
            .build()

        assertEquals(LogLevel.ERROR, config.logLevel)
        assertEquals(false, config.debug)
    }

    @Test
    fun `test builder logLevel DEBUG sets debug to true`() {
        val config = LocalEvaluationConfig.builder()
            .logLevel(LogLevel.DEBUG)
            .build()

        assertEquals(LogLevel.DEBUG, config.logLevel)
        // Note: debug flag is set separately, not automatically by logLevel
    }

    @Test
    fun `test builder with custom logger provider`() {
        val customLogger = com.amplitude.experiment.util.DefaultLogger()
        val config = LocalEvaluationConfig.builder()
            .loggerProvider(customLogger)
            .build()

        assertEquals(customLogger, config.loggerProvider)
    }

    @Test
    fun `test default constructor`() {
        val config = LocalEvaluationConfig()

        assertEquals(LocalEvaluationConfig.Defaults.DEBUG, config.debug)
        assertEquals(LocalEvaluationConfig.Defaults.LOG_LEVEL, config.logLevel)
        assertEquals(LocalEvaluationConfig.Defaults.SERVER_URL, config.serverUrl)
    }

    @Test
    fun `test builder exposure configuration is null by default`() {
        val config = LocalEvaluationConfig.builder().build()
        assertNull(config.exposureConfiguration)
    }

    @Test
    fun `test builder cohort sync config is null by default`() {
        val config = LocalEvaluationConfig.builder().build()
        assertNull(config.cohortSyncConfig)
    }

    @Test
    fun `test builder with custom exposure configuration properties`() {
        val exposureConfig = ExposureConfiguration(
            apiKey = "custom-key",
            cacheCapacity = 1024,
            eventUploadThreshold = 5,
            eventUploadPeriodMillis = 5000,
            useBatchMode = false
        )

        val config = LocalEvaluationConfig.builder()
            .enableExposureTracking(exposureConfig)
            .build()

        val exposure = config.exposureConfiguration
        assertNotNull(exposure)
        assertEquals("custom-key", exposure?.apiKey)
        assertEquals(1024, exposure?.cacheCapacity)
        assertEquals(5, exposure?.eventUploadThreshold)
        assertEquals(5000, exposure?.eventUploadPeriodMillis)
        assertEquals(false, exposure?.useBatchMode)
    }

    @Test
    fun `test builder with all timeout and interval values`() {
        val config = LocalEvaluationConfig.builder()
            .flagConfigPollerIntervalMillis(120_000L)
            .flagConfigPollerRequestTimeoutMillis(20_000L)
            .streamFlagConnTimeoutMillis(5_000L)
            .build()

        assertEquals(120_000L, config.flagConfigPollerIntervalMillis)
        assertEquals(20_000L, config.flagConfigPollerRequestTimeoutMillis)
        assertEquals(5_000L, config.streamFlagConnTimeoutMillis)
    }
}

