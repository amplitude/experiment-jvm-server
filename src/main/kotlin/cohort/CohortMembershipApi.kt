package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.get
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

internal interface CohortMembershipApi {
    fun getCohortsForUser(userId: String): Set<String>
}

internal class ProxyCohortMembershipApi(
    private val deploymentKey: String,
    private val serverUrl: HttpUrl,
    private val httpClient: OkHttpClient,
) : CohortMembershipApi {
    override fun getCohortsForUser(userId: String): Set<String> {
        return httpClient.get(serverUrl, "/sdk/v1/deployments/$deploymentKey/users/$userId/cohorts")
    }
}
