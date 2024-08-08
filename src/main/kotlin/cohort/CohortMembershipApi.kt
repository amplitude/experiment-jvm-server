package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.get
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

internal interface CohortMembershipApi {
    fun getCohortMemberships(groupType: String, groupName: String): Set<String>
}

internal class ProxyCohortMembershipApi(
    private val deploymentKey: String,
    private val serverUrl: HttpUrl,
    private val httpClient: OkHttpClient,
) : CohortMembershipApi {

    override fun getCohortMemberships(groupType: String, groupName: String): Set<String> {
        return httpClient.get<Set<String>>(
            serverUrl,
            "sdk/v2/memberships/$groupType/$groupName",
            headers = mapOf(
                "Authorization" to "Api-Key $deploymentKey",
            )
        ).get()
    }
}
