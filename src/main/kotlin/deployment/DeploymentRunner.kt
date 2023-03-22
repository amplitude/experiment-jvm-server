@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.deployment

import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationConfig
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.cohort.DirectCohortDownloadApi
import com.amplitude.experiment.cohort.PollingCohortSyncService
import com.amplitude.experiment.flag.FlagConfigApi
import com.amplitude.experiment.flag.FlagConfigStorage
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.Once
import com.amplitude.experiment.util.wrapMetrics
import okhttp3.OkHttpClient
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class DeploymentRunner(
    private val config: LocalEvaluationConfig,
    private val httpClient: OkHttpClient,
    private val flagConfigApi: FlagConfigApi,
    private val flagConfigStorage: FlagConfigStorage,
    private val cohortStorage: CohortStorage,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) {

    private val cohortService = config.cohortSyncConfiguration?.let {
        val cohortDownloadApi = DirectCohortDownloadApi(
            config.cohortSyncConfiguration.apiKey,
            config.cohortSyncConfiguration.secretKey,
            httpClient
        )
        PollingCohortSyncService(
            config = config.cohortSyncConfiguration,
            cohortDownloadApi = cohortDownloadApi,
            cohortStorage = cohortStorage,
            metrics = metrics
        )
    }

    private val lock = Once()
    private val poller = Executors.newSingleThreadScheduledExecutor()

    private fun refresh() {
        Logger.d("Refreshing flag configs.")
        val flagConfigs = wrapMetrics(
            metric = metrics::onFlagConfigFetch,
            failure = metrics::onFlagConfigFetchFailure,
        ) {
            flagConfigApi.getFlagConfigs()
        }

        flagConfigStorage.putFlagConfigs(flagConfigs)
        Logger.d("Refreshed ${flagConfigs.size} flag configs.")
    }

    fun start() {
        lock.once {
            refresh()
            poller.scheduleAtFixedRate(
                {
                    try {
                        refresh()
                    } catch (t: Throwable) {
                        Logger.e("Refresh flag configs failed.", t)
                    }
                },
                config.flagConfigPollerIntervalMillis,
                config.flagConfigPollerIntervalMillis,
                TimeUnit.MILLISECONDS
            )
            cohortService?.start()
        }
    }

    fun stop() {
        cohortService?.stop()
        poller.shutdown()
    }
}
