@file:Suppress("INLINE_FROM_HIGHER_PLATFORM")

package com.amplitude.experiment.cohort

import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert
import org.junit.Test

class CohortDownloadApiTest {
    private val location = "https://example.com/cohorts/Cohort_asdf?asdf=asdf#asdf".toHttpUrl()

    @Test
    fun `cohort download success`() {
        val cohort = CohortDescription(
            id = "1234",
            lastComputed = 0L,
            size = 1,
        )
        val asyncRequestResponse = GetCohortAsyncResponse(
            cohortId = "1234",
            requestId = "4321",
        )

        val asyncRequestStatusResponse = response(200)
        val api = spyk(DirectCohortDownloadApiV5("api", "secret", OkHttpClient(), 10L))
        every { api.getCohortAsyncRequest(cohort) }.returns(asyncRequestResponse)
        every { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }.returns(asyncRequestStatusResponse)
        every { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }.returns(location)
        every { api.getCohortAsyncRequestMembers(cohort.id, USER_GROUP_TYPE, location) }.returns(setOf("user"))
        val members = api.getCohortMembers(cohort)
        Assert.assertEquals(setOf("user"), members)
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 1) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestMembers(cohort.id, USER_GROUP_TYPE, location) }
    }

    @Test
    fun `cohort download, many 202s, success`() {
        val cohort = CohortDescription(
            id = "1234",
            lastComputed = 0L,
            size = 1,
        )
        val asyncRequestResponse = GetCohortAsyncResponse(
            cohortId = "1234",
            requestId = "4321",
        )

        val asyncRequestStatus202Response = response(202)
        val asyncRequestStatus200Response = response(200)
        val api = spyk(DirectCohortDownloadApiV5("api", "secret", OkHttpClient(), 10L))
        every { api.getCohortAsyncRequest(cohort) }.returns(asyncRequestResponse)
        every { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }.returnsMany(
            asyncRequestStatus202Response,
            asyncRequestStatus202Response,
            asyncRequestStatus202Response,
            asyncRequestStatus202Response,
            asyncRequestStatus202Response,
            asyncRequestStatus202Response,
            asyncRequestStatus202Response,
            asyncRequestStatus202Response,
            asyncRequestStatus202Response,
            asyncRequestStatus200Response
        )
        every { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }.returns(location)
        every { api.getCohortAsyncRequestMembers(cohort.id, USER_GROUP_TYPE, location) }.returns(setOf("user"))
        val members = api.getCohortMembers(cohort)
        Assert.assertEquals(setOf("user"), members)
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 10) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestMembers(cohort.id, USER_GROUP_TYPE, location) }
    }

    @Test
    fun `cohort request status with two failures succeeds`() {
        val cohort = CohortDescription(
            id = "1234",
            lastComputed = 0L,
            size = 1,
        )
        val asyncRequestResponse = GetCohortAsyncResponse(
            cohortId = "1234",
            requestId = "4321",
        )
        val asyncRequestStatus503Response = response(503)
        val asyncRequestStatus200Response = response(200)
        val api = spyk(DirectCohortDownloadApiV5("api", "secret", OkHttpClient(), 10L))
        every { api.getCohortAsyncRequest(cohort) }.returns(asyncRequestResponse)
        every { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }.returnsMany(
            asyncRequestStatus503Response,
            asyncRequestStatus503Response,
            asyncRequestStatus200Response
        )
        every { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }.returns(location)
        every { api.getCohortAsyncRequestMembers(cohort.id, USER_GROUP_TYPE, location) }.returns(setOf("user"))
        val members = api.getCohortMembers(cohort)
        Assert.assertEquals(setOf("user"), members)
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 3) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestMembers(cohort.id, USER_GROUP_TYPE, location) }
    }

    @Test
    fun `cohort request status throws after 3 failures, cache fallback succeeds`() {
        val cohort = CohortDescription(
            id = "1234",
            lastComputed = 0L,
            size = 1,
        )
        val asyncRequestResponse = GetCohortAsyncResponse(
            cohortId = "1234",
            requestId = "4321",
        )
        val asyncRequestStatusResponse = response(503)
        val api = spyk(DirectCohortDownloadApiV5("api", "secret", OkHttpClient(), 10L))
        every { api.getCohortAsyncRequest(cohort) }.returns(asyncRequestResponse)
        every { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }.returns(asyncRequestStatusResponse)
        every { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }.returns(location)
        every { api.getCohortAsyncRequestMembers(cohort.id, USER_GROUP_TYPE, location) }.returns(setOf("user"))
        every { api.getCachedCohortMembers(cohort.id, USER_GROUP_TYPE) }.returns(setOf("user2"))
        try {
            api.getCohortMembers(cohort)
            Assert.fail("expected failure")
        } catch (e: CachedCohortDownloadException) {
            // expected
            Assert.assertEquals(setOf("user2"), e.members)
        }
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 3) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 0) { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }
        verify(exactly = 0) { api.getCohortAsyncRequestMembers(cohort.id, USER_GROUP_TYPE, location) }
        verify(exactly = 1) { api.getCachedCohortMembers(cohort.id, USER_GROUP_TYPE)}
    }

    @Test
    fun `cohort request status 429s keep retrying`() {
        val cohort = CohortDescription(
            id = "1234",
            lastComputed = 0L,
            size = 1,
        )
        val asyncRequestResponse = GetCohortAsyncResponse(
            cohortId = "1234",
            requestId = "4321",
        )
        val asyncRequestStatus429Response = response(429)
        val asyncRequestStatus200Response = response(200)
        val api = spyk(DirectCohortDownloadApiV5("api", "secret", OkHttpClient(), 10L))
        every { api.getCohortAsyncRequest(cohort) }.returns(asyncRequestResponse)
        every { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }.returnsMany(
            asyncRequestStatus429Response,
            asyncRequestStatus429Response,
            asyncRequestStatus429Response,
            asyncRequestStatus429Response,
            asyncRequestStatus429Response,
            asyncRequestStatus429Response,
            asyncRequestStatus429Response,
            asyncRequestStatus429Response,
            asyncRequestStatus429Response,
            asyncRequestStatus200Response
        )
        every { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }.returns(location)
        every { api.getCohortAsyncRequestMembers(cohort.id, USER_GROUP_TYPE, location) }.returns(setOf("user"))
        val members = api.getCohortMembers(cohort)
        Assert.assertEquals(setOf("user"), members)
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 10) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestMembers(cohort.id, USER_GROUP_TYPE, location) }
    }

    @Test
    fun `cohort async request download failure falls back on cached request`() {
        val cohort = CohortDescription(
            id = "1234",
            lastComputed = 0L,
            size = 1,
        )
        val api = spyk(DirectCohortDownloadApiV5("api", "secret", OkHttpClient(), 10L))
        every { api.getCohortAsyncRequest(cohort) }.throws(RuntimeException("fail"))
        every { api.getCachedCohortMembers(cohort.id, cohort.groupType) }.returns(setOf("user"))
        try {
            val members = api.getCohortMembers(cohort)
            Assert.fail("exception expected")
        } catch (e: CachedCohortDownloadException) {
            Assert.assertEquals(setOf("user"), e.members)
            verify(exactly = 1) { api.getCachedCohortMembers(cohort.id, cohort.groupType) }
        }
    }

    @Test
    fun `group cohort download success`() {
        val cohort = CohortDescription(
            id = "1234",
            lastComputed = 0L,
            size = 1,
            groupType = "org name"
        )
        val asyncRequestResponse = GetCohortAsyncResponse(
            cohortId = "1234",
            requestId = "4321",
        )

        val asyncRequestStatusResponse = response(200)
        val api = spyk(DirectCohortDownloadApiV5("api", "secret", OkHttpClient(), 10L))
        every { api.getCohortAsyncRequest(cohort) }.returns(asyncRequestResponse)
        every { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }.returns(asyncRequestStatusResponse)
        every { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }.returns(location)
        every { api.getCohortAsyncRequestMembers(cohort.id, "org name", location) }.returns(setOf("group"))
        val members = api.getCohortMembers(cohort)
        Assert.assertEquals(setOf("group"), members)
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 1) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestLocation(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestMembers(cohort.id, "org name", location) }
    }

    // Util

    private fun response(code: Int): Response {
        val req = Request.Builder().url("https://cohort.lab.amplitude.com").build()
        return Response.Builder().request(req).protocol(Protocol.HTTP_2).message("OK").code(code).build()
    }
}
