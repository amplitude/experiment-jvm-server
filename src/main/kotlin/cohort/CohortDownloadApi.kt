package com.amplitude.experiment.cohort

import com.amplitude.experiment.LIBRARY_VERSION
import com.amplitude.experiment.util.HttpErrorResponseException
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.get
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.Base64

internal class CohortTooLargeException(cohortId: String, maxCohortSize: Int) : Exception(
    "Cohort $cohortId exceeds the maximum cohort size defined in the SDK configuration $maxCohortSize"
)

internal class CohortNotModifiedException(cohortId: String) : Exception(
    "Cohort $cohortId has not been modified."
)

@Serializable
private data class GetCohortResponse(
    private val id: String,
    private val lastModified: Long,
    private val size: Int,
    private val memberIds: Set<String> = setOf(),
    private val groupType: String,
) {
    fun toCohort() = Cohort(id, groupType, size, lastModified, memberIds)
}

internal interface CohortDownloadApi {
    fun getCohort(cohortId: String, cohort: Cohort?): Cohort
}

internal class DirectCohortDownloadApi(
    apiKey: String,
    secretKey: String,
    private val maxCohortSize: Int,
    private val serverUrl: HttpUrl,
    private val httpClient: OkHttpClient,
) : CohortDownloadApi {
    private val bearerToken = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray())
    override fun getCohort(cohortId: String, cohort: Cohort?): Cohort {
        Logger.d("getCohortMembers($cohortId): start")
        var errors = 0
        while (true) {
            val headers = mapOf(
                "Authorization" to "Bearer $bearerToken",
                "X-Amp-Exp-Library" to "experiment-jvm-server/$LIBRARY_VERSION",
            )
            val queries = mutableMapOf(
                "maxCohortSize" to "$maxCohortSize",
            )
            if (cohort != null && cohort.lastModified > 0) {
                queries["lastModified"] = "${cohort.lastModified}"
            }
            try {
                return httpClient.get<GetCohortResponse>(
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
                }.toCohort()
            } catch (e: HttpErrorResponseException) {
                if (e.code == 429) {
                    continue
                }
                if (errors >= 3) {
                    throw e
                }
                errors++
            }
        }
    }
}
