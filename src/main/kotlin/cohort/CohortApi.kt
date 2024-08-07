package com.amplitude.experiment.cohort

import com.amplitude.experiment.LIBRARY_VERSION
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.util.BackoffConfig
import com.amplitude.experiment.util.HttpErrorResponseException
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.backoff
import com.amplitude.experiment.util.get
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.Base64
import java.util.concurrent.ExecutionException

internal class CohortTooLargeException(cohortId: String, maxCohortSize: Int) : RuntimeException(
    "Cohort $cohortId exceeds the maximum cohort size defined in the SDK configuration $maxCohortSize"
)

internal class CohortNotModifiedException(cohortId: String) : RuntimeException(
    "Cohort $cohortId has not been modified."
)

@Serializable
internal data class GetCohortResponse(
    private val cohortId: String,
    private val lastModified: Long,
    private val size: Int,
    private val memberIds: Set<String> = setOf(),
    private val groupType: String,
) {
    fun toCohort() = Cohort(
        id = cohortId,
        groupType = groupType,
        size = size,
        lastModified = lastModified,
        members = memberIds
    )
}

internal interface CohortApi {
    fun getCohort(cohortId: String, cohort: Cohort?): Cohort
}

internal class DynamicCohortApi(
    apiKey: String,
    secretKey: String,
    private val maxCohortSize: Int,
    private val serverUrl: HttpUrl,
    private val proxyUrl: HttpUrl?,
    private val httpClient: OkHttpClient,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) : CohortApi {

    private val token = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray())
    private val backoffConfig = BackoffConfig(
        attempts = 5,
        min = 100,
        max = 2000,
        scalar = 2.0,
    )

    override fun getCohort(cohortId: String, cohort: Cohort?): Cohort {
        return if (proxyUrl != null) {
            try {
                getCohort(proxyUrl, cohortId, cohort)
            } catch (e: CohortNotModifiedException) {
                throw e
            } catch (e: CohortTooLargeException) {
                throw e
            } catch (e: Exception) {
                metrics.onCohortDownloadOriginFallback(e)
                getCohort(serverUrl, cohortId, cohort)
            }
        } else {
            getCohort(serverUrl, cohortId, cohort)
        }
    }

    private fun getCohort(url: HttpUrl, cohortId: String, cohort: Cohort?): Cohort {
        Logger.d("getCohortMembers($cohortId): start")
        val future = backoff(backoffConfig, {
            val headers = mapOf(
                "Authorization" to "Basic $token",
                "X-Amp-Exp-Library" to "experiment-jvm-server/$LIBRARY_VERSION",
            )
            val queries = mutableMapOf(
                "maxCohortSize" to "$maxCohortSize",
            )
            if (cohort != null) {
                queries["lastModified"] = "${cohort.lastModified}"
            }
            httpClient.get<GetCohortResponse>(
                serverUrl = serverUrl,
                path = "sdk/v1/cohort/$cohortId",
                headers = headers,
                queries = queries,
            ) { response ->
                Logger.d("getCohortMembers($cohortId): status=${response.code}")
                when (response.code) {
                    200 -> return@get
                    204 -> throw CohortNotModifiedException(cohortId)
                    413 -> throw CohortTooLargeException(cohortId, maxCohortSize)
                    else -> throw HttpErrorResponseException(response.code)
                }
            }
        }, { e ->
            // Don't retry on expected responses
            when (e) {
                is CohortNotModifiedException -> false
                is CohortTooLargeException -> false
                else -> true
            }
        })
        try {
            return future.get().toCohort()
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}
