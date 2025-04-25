package com.amplitude.experiment

import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.flag.DynamicFlagConfigApi
import com.amplitude.experiment.flag.FlagConfigStreamApi
import com.amplitude.experiment.util.json
import com.amplitude.experiment.util.newGet
import io.github.cdimascio.dotenv.dotenv
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert
import kotlin.random.Random
import kotlin.random.Random.Default.nextInt
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// Read env vars.
val ENVIRONMENT = System.getenv("ENVIRONMENT")
val ENV_VARS = dotenv{
    filename = if (ENVIRONMENT != null) ".env." + ENVIRONMENT else ".env"
}
val SERVER_URL = ENV_VARS["SERVER_URL"] ?: LocalEvaluationConfig.Defaults.SERVER_URL
val STREAM_SERVER_URL = ENV_VARS["STREAM_SERVER_URL"] ?: LocalEvaluationConfig.Defaults.STREAM_SERVER_URL
val MANAGEMENT_API_SERVER_URL = ENV_VARS["MANAGEMENT_API_SERVER_URL"] ?: "https://experiment.amplitude.com"
val DEPLOYMENT_KEY = ENV_VARS["DEPLOYMENT_KEY"] ?: "server-qz35UwzJ5akieoAdIgzM4m9MIiOLXLoz"
val MANAGEMENT_API_KEY = ENV_VARS["MANAGEMENT_API_KEY"]
val FLAG_KEY = "sdk-ci-stream-flag-test"

val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()

internal fun newPatch(
    serverUrl: HttpUrl,
    path: String? = null,
    headers: Map<String, String>? = null,
    queries: Map<String, String>? = null,
    body: String = "",
    mediaType: MediaType? = null,
): Request {
    val url = serverUrl.newBuilder().apply {
        if (path != null) {
            addPathSegments(path)
        }
        queries?.forEach {
            addQueryParameter(it.key, it.value)
        }
    }.build()
    val builder = Request.Builder().patch(body.toRequestBody(mediaType)).url(url)
    headers?.forEach {
        builder.addHeader(it.key, it.value)
    }
    builder.addHeader("X-Amp-Exp-Library", "experiment-jvm-server/$LIBRARY_VERSION")
    return builder.build()
}

fun retry(
    retryTimes: Int = 2,
    delay: Duration = 1.seconds,
    block: () -> Unit,
) {
    var attempts = 0
    while (attempts <= retryTimes) {
        try {
            return block()
        } catch (e: Throwable) {
            if (attempts++ >= retryTimes) throw e
            Thread.sleep(delay.inWholeMilliseconds)
        }
    }
}

class StreamTest {
    @OptIn(ExperimentalApi::class)
    @Test
    fun `test success`() {
        Assert.assertNotNull(MANAGEMENT_API_KEY)
        retry {
            // Connect stream.
            val api = FlagConfigStreamApi(
                DEPLOYMENT_KEY, STREAM_SERVER_URL.toHttpUrl(),
                connectionTimeoutMillis = 5000L,
            )
            val streamFlags: MutableList<List<EvaluationFlag>?> = mutableListOf()
            var streamError: Exception? = null
            api.connect({ flags ->
                streamFlags.add(flags)
            }, { flags ->
                streamFlags.add(flags)
            }, { error ->
                streamError = error
            })

            // Get flags from fetch api.
            val fetchFlags = DynamicFlagConfigApi(
                deploymentKey = DEPLOYMENT_KEY,
                serverUrl = SERVER_URL.toHttpUrl(),
                proxyUrl = null,
                httpClient = OkHttpClient(),
            ).getFlagConfigs()

            Thread.sleep(5000L)

            // Check stream no error.
            Assert.assertNull(streamError)

            // Check at lease one of the streamed updates is equal to the fetched flags.
            val theStreamFlags = streamFlags.filter { it?.equals(fetchFlags) == true }.first()
            Assert.assertNotNull(theStreamFlags)
            Assert.assertEquals(fetchFlags, theStreamFlags)
            val sFlag = findFlag(theStreamFlags, FLAG_KEY)
            val fFlag = findFlag(fetchFlags, FLAG_KEY)
            Assert.assertNotNull(sFlag?.metadata?.get("flagVersion"))
            Assert.assertEquals(fFlag?.metadata?.get("flagVersion"), sFlag?.metadata?.get("flagVersion"))

            // Test stream is alive after 20s. (15s keepalive)
            Thread.sleep(20000L)
            Assert.assertNull(streamError)

            // Get flag id using management api.
            val flagIdReq = newGet(
                (MANAGEMENT_API_SERVER_URL).toHttpUrl(), "api/1/flags", mapOf(
                    "Authorization" to "Bearer " + MANAGEMENT_API_KEY,
                    "Content-Type" to "application/json",
                    "Accept" to "*/*",
                ), mapOf("key" to FLAG_KEY)
            )
            val flagIdResp = OkHttpClient().newCall(flagIdReq).execute()
            Assert.assertTrue(flagIdResp.isSuccessful)
            val flagIdBody = json.decodeFromString<Map<String, JsonElement>>(flagIdResp.body?.string() ?: "")
            val flagId = (flagIdBody["flags"]?.jsonArray?.get(0)?.jsonObject?.get("id"))?.jsonPrimitive?.content
            Assert.assertNotNull(flagId)

            // Get the max flag version.
            var maxFlagVersion = sFlag?.metadata?.get("flagVersion") as Int
            for (i in 0 until streamFlags.size) {
                val flagVersion = findFlag(streamFlags[i], FLAG_KEY)?.metadata?.get("flagVersion") as Int
                if (maxFlagVersion < flagVersion) {
                    maxFlagVersion = flagVersion
                }
            }

            // Call management api to edit the flag. Then wait for stream to update.
            streamFlags.clear()
            val randNumber = nextInt(Int.MAX_VALUE).toString()
            val modifyFlagReq = newPatch(
                MANAGEMENT_API_SERVER_URL.toHttpUrl(), "api/1/flags/$flagId/variants/on", mapOf(
                    "Authorization" to "Bearer $MANAGEMENT_API_KEY",
                    "Content-Type" to "application/json",
                    "Accept" to "*/*",
                ), mapOf(), "{\"payload\":\"$randNumber\"}", JSON_MEDIA_TYPE
            )
            val modifyFlagResp = OkHttpClient().newCall(modifyFlagReq).execute()
            Assert.assertTrue(modifyFlagResp.isSuccessful)

            // Check at least one of the updates happened during this time have the random number we generated.
            // This means that the stream is working and we are getting updates.
            Thread.sleep(5000L)
            Assert.assertEquals(1, streamFlags.size)
            var gotUpdate = false;
            for (i in 0 until streamFlags.size) {
                val flag = findFlag(streamFlags[i], FLAG_KEY) ?: continue
                val payload = flag.variants["on"]?.payload ?: continue
                val version = flag.metadata?.get("flagVersion") ?: continue
                if (randNumber.equals(payload as String) && version as Int > maxFlagVersion) {
                    gotUpdate = true
                    break
                }
            }
            Assert.assertTrue(gotUpdate)

            api.close()
        }
    }

    private fun findFlag(flags: List<EvaluationFlag>?, flagKey: String): EvaluationFlag? {
        if (flags == null) {
            return null
        }
        for (flag in flags) {
            if (flagKey.equals(flag.key)) {
                return flag
            }
        }
        return null
    }
}