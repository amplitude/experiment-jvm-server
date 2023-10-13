@file:Suppress("INLINE_FROM_HIGHER_PLATFORM")

package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.HttpErrorResponseException
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert
import org.junit.Test

class CohortDownloadApiTest {

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
        val asyncRequestMembers = setOf("user")
        val api = spyk(DirectCohortDownloadApiV5("api","secret", OkHttpClient(), 10L))
        every { api.getCohortAsyncRequest(cohort) }.returns(asyncRequestResponse)
        every { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }.returns(asyncRequestStatusResponse)
        every { api.getCohortAsyncRequestMembers(asyncRequestResponse.requestId) }.returns(asyncRequestMembers)
        val members = api.getCohortMembers(cohort)
        Assert.assertEquals(setOf("user"), members)
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 1) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestMembers(asyncRequestResponse.requestId) }
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
        val asyncRequestMembers = setOf("user")
        val api = spyk(DirectCohortDownloadApiV5("api","secret", OkHttpClient(), 10L))
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
        every { api.getCohortAsyncRequestMembers(asyncRequestResponse.requestId) }.returns(asyncRequestMembers)
        val members = api.getCohortMembers(cohort)
        Assert.assertEquals(setOf("user"), members)
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 10) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestMembers(asyncRequestResponse.requestId) }
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
        val asyncRequestMembers = setOf("user")
        val api = spyk(DirectCohortDownloadApiV5("api","secret", OkHttpClient(), 10L))
        every { api.getCohortAsyncRequest(cohort) }.returns(asyncRequestResponse)
        every { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }.returnsMany(
            asyncRequestStatus503Response,
            asyncRequestStatus503Response,
            asyncRequestStatus200Response
        )
        every { api.getCohortAsyncRequestMembers(asyncRequestResponse.requestId) }.returns(asyncRequestMembers)
        val members = api.getCohortMembers(cohort)
        Assert.assertEquals(setOf("user"), members)
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 3) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestMembers(asyncRequestResponse.requestId) }
    }

    @Test
    fun `cohort request status throws after 3 failures`() {
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
        val asyncRequestMembers = setOf("user")
        val api = spyk(DirectCohortDownloadApiV5("api","secret", OkHttpClient(), 10L))
        every { api.getCohortAsyncRequest(cohort) }.returns(asyncRequestResponse)
        every { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }.returns(asyncRequestStatusResponse)
        every { api.getCohortAsyncRequestMembers(asyncRequestResponse.requestId) }.returns(asyncRequestMembers)
        try {
            api.getCohortMembers(cohort)
            Assert.fail("expected failure")
        } catch (e: HttpErrorResponseException) {
            // expected
        }
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 3) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 0) { api.getCohortAsyncRequestMembers(asyncRequestResponse.requestId) }
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
        val asyncRequestMembers = setOf("user")
        val api = spyk(DirectCohortDownloadApiV5("api","secret", OkHttpClient(), 10L))
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
        every { api.getCohortAsyncRequestMembers(asyncRequestResponse.requestId) }.returns(asyncRequestMembers)
        val members = api.getCohortMembers(cohort)
        Assert.assertEquals(setOf("user"), members)
        verify(exactly = 1) { api.getCohortAsyncRequest(cohort) }
        verify(exactly = 10) { api.getCohortAsyncRequestStatus(asyncRequestResponse.requestId) }
        verify(exactly = 1) { api.getCohortAsyncRequestMembers(asyncRequestResponse.requestId) }
    }

    // Util

    private fun response(code: Int): Response {
        val req = Request.Builder().url("https://cohort.lab.amplitude.com").build()
        return Response.Builder().request(req).protocol(Protocol.HTTP_2).message("OK").code(code).build()
    }
}
