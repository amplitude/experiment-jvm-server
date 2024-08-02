package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.HttpErrorResponseException
import com.amplitude.experiment.util.json
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.util.Base64
import kotlin.test.Test
import kotlin.test.fail

class CohortDownloadApiTest {

    private val apiKey = "api"
    private val secretKey = "secret"
    private val maxCohortSize = Int.MAX_VALUE
    private val httpClient = OkHttpClient()
    private val server = MockWebServer()
    private val url = server.url("/")
    private val proxyUrl = server.url("/")
    private val api = DynamicCohortApi(apiKey,secretKey, maxCohortSize, url, proxyUrl, httpClient)
    @Test
    fun `cohort download, success`() {
        val response = cohortResponse("a", setOf("1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(json.encodeToString(response)))
        val cohort = api.getCohort("a", null)
        assertEquals(cohort("a", setOf("1")), cohort)
    }

    @Test
    fun `cohort download, null cohort input request validation`() {
        val response = cohortResponse("a", setOf("1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(json.encodeToString(response)))
        api.getCohort("a", null)
        val request = server.takeRequest()
        val expectedAuth = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray())
        val actualAuth = request.headers["Authorization"]
        assertEquals("Basic $expectedAuth", actualAuth)
        val actualMaxCohortSize = request.requestUrl?.queryParameter("maxCohortSize")
        assertEquals("$maxCohortSize", actualMaxCohortSize)
        val actualLastModified = request.requestUrl?.queryParameter("lastModified")
        assertNull(actualLastModified)
    }

    @Test
    fun `cohort download, with cohort input request validation`() {
        val response = cohortResponse("a", setOf("1"))
        server.enqueue(MockResponse().setResponseCode(200).setBody(json.encodeToString(response)))
        val cohort = cohort("a", setOf("1"))
        api.getCohort("a", cohort)
        val request = server.takeRequest()
        val expectedAuth = Base64.getEncoder().encodeToString("$apiKey:$secretKey".toByteArray())
        val actualAuth = request.headers["Authorization"]
        assertEquals("Basic $expectedAuth", actualAuth)
        val actualMaxCohortSize = request.requestUrl?.queryParameter("maxCohortSize")
        assertEquals("$maxCohortSize", actualMaxCohortSize)
        val actualLastModified = request.requestUrl?.queryParameter("lastModified")
        assertEquals("${cohort.lastModified}", actualLastModified)
    }

    @Test
    fun `cohort download, 204 not modified, throws exception`() {
        server.enqueue(MockResponse().setResponseCode(204))
        try {
            api.getCohort("a", null)
            fail("Expected getCohort to throw CohortNotModifiedException")
        } catch (e: CohortNotModifiedException) {
            // Expected
        }
    }

    @Test
    fun `cohort download, 413 too large, throws exception`() {
        server.enqueue(MockResponse().setResponseCode(413))
        try {
            api.getCohort("a", null)
            fail("Expected getCohort to throw CohortTooLargeException")
        } catch (e: CohortTooLargeException) {
            // Expected
        }
    }

    @Test
    fun `cohort download, 503 service unavailable, retries 3 times then throws`() {
        server.enqueue(MockResponse().setResponseCode(501))
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setResponseCode(503))
        // Should not be sent in response
        server.enqueue(MockResponse().setResponseCode(204))
        try {
            api.getCohort("a", null)
            fail("Expected getCohort to throw HttpErrorResponseException")
        } catch (e: HttpErrorResponseException) {
            // Expected
            assertEquals(503, e.code)
        }
    }

    @Test
    fun `cohort download, 503 service unavailable, 2 errors, 3rd attempt success`() {
        val response = cohortResponse("a", setOf("1"))
        server.enqueue(MockResponse().setResponseCode(501))
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setResponseCode(200).setBody(json.encodeToString(response)))
        val cohort = api.getCohort("a", null)
        assertEquals(cohort("a", setOf("1")), cohort)
    }
}

private fun cohortResponse(id: String, members: Set<String>, lastModified: Long = 1234) =
    GetCohortResponse(
        cohortId = id,
        memberIds = members,
        lastModified = lastModified,
        size = members.size,
        groupType = "User",
    )

private fun cohort(id: String, members: Set<String>, lastModified: Long = 1234) =
    Cohort(
        id = id,
        members = members,
        lastModified = lastModified,
        size = members.size,
        groupType = "User",
    )
