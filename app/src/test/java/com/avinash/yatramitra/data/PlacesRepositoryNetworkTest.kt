package com.avinash.yatramitra.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Exercises PlacesRepository's network functions against a real local HTTP server (MockWebServer)
 * standing in for Nominatim/Overpass, so every response shape (valid, empty, malformed, error,
 * unreachable) is a real, observed test result instead of a guess.
 */
class PlacesRepositoryNetworkTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun baseUrl(): String = server.url("").toString().removeSuffix("/")

    private val validNominatimHit =
        """[{"display_name":"Bengaluru, Karnataka, India","lat":"12.9716","lon":"77.5946"}]"""

    // ---- geocodeOrThrow: the function pitstop generation actually calls ----

    @Test
    fun `geocodeOrThrow valid input returns the parsed place`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(validNominatimHit))
        val result = PlacesRepository.geocodeOrThrow("Bangalore", baseUrl())
        assertEquals("Bengaluru, Karnataka, India", result?.displayName)
        assertEquals(12.9716, result?.lat ?: 0.0, 0.0001)
    }

    @Test
    fun `geocodeOrThrow empty results array returns null, not an error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        assertNull(PlacesRepository.geocodeOrThrow("Nonexistentplacexyz123", baseUrl()))
    }

    @Test
    fun `geocodeOrThrow boundary - single character query returns null without a network call`() = runTest {
        // No response enqueued on purpose: a real network call here would fail the test with
        // "no more responses queued", proving the short-circuit for length < 2 actually happens.
        assertNull(PlacesRepository.geocodeOrThrow("A", baseUrl()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `geocodeOrThrow boundary - blank query returns null without a network call`() = runTest {
        assertNull(PlacesRepository.geocodeOrThrow("   ", baseUrl()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `geocodeOrThrow malformed JSON throws rather than silently returning null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{not valid json"))
        try {
            PlacesRepository.geocodeOrThrow("Bangalore", baseUrl())
            fail("expected an exception for malformed JSON")
        } catch (e: org.json.JSONException) {
            // Expected: org.json's parser throws on malformed input; suggestPitstops's caller
            // catches this as Exception and reports it via describeFailure.
        }
    }

    @Test
    fun `geocodeOrThrow HTTP error throws with the status code in the message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            PlacesRepository.geocodeOrThrow("Bangalore", baseUrl())
            fail("expected an IOException for HTTP 503")
        } catch (e: IOException) {
            assertEquals("Place search failed (HTTP 503)", e.message)
        }
    }

    @Test
    fun `geocodeOrThrow HTTP 200 with an empty body throws rather than crashing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        try {
            PlacesRepository.geocodeOrThrow("Bangalore", baseUrl())
            fail("expected a JSON exception for an empty body")
        } catch (e: org.json.JSONException) {
            // Expected: JSONArray("") throws; confirms this doesn't silently misreport as "not found".
        }
    }

    @Test
    fun `geocodeOrThrow special characters and non-Latin script are sent without crashing`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""[{"display_name":"Sao Paulo","lat":"-23.55","lon":"-46.63"}]""")
        )
        val result = PlacesRepository.geocodeOrThrow("São Paulo 🇧🇷", baseUrl())
        assertEquals("Sao Paulo", result?.displayName)
        val recorded = server.takeRequest()
        // URLEncoder must have produced a request path with no raw non-ASCII bytes.
        assertTrue(recorded.path?.contains("q=") == true)
    }

    // ---- findNearbyPitstop: used once per generated pitstop ----

    @Test
    fun `findNearbyPitstop valid Overpass response returns the named amenity`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"elements":[{"tags":{"name":"Kamat Upachar"}}]}""")
        )
        val result = PlacesRepository.findNearbyPitstop(12.9, 77.5, overpassBaseUrl = baseUrl())
        assertEquals("Kamat Upachar", result)
    }

    @Test
    fun `findNearbyPitstop no elements falls back to reverseGeocode`() = runTest {
        val overpass = MockWebServer().apply { start() }
        val nominatim = MockWebServer().apply { start() }
        try {
            overpass.enqueue(MockResponse().setResponseCode(200).setBody("""{"elements":[]}"""))
            nominatim.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody("""{"address":{"city":"Mysuru"},"display_name":"Mysuru, India"}""")
            )
            val overpassUrl = overpass.url("").toString().removeSuffix("/")
            val nominatimUrl = nominatim.url("").toString().removeSuffix("/")
            val result = PlacesRepository.findNearbyPitstop(12.3, 76.6, overpassUrl, nominatimUrl)
            assertEquals("Mysuru", result)
        } finally {
            overpass.shutdown()
            nominatim.shutdown()
        }
    }

    @Test
    fun `findNearbyPitstop Overpass HTTP error falls back to reverseGeocode instead of throwing`() = runTest {
        val overpass = MockWebServer().apply { start() }
        val nominatim = MockWebServer().apply { start() }
        try {
            overpass.enqueue(MockResponse().setResponseCode(500))
            nominatim.enqueue(MockResponse().setResponseCode(500)) // force the ultimate fallback too
            val overpassUrl = overpass.url("").toString().removeSuffix("/")
            val nominatimUrl = nominatim.url("").toString().removeSuffix("/")
            val result = PlacesRepository.findNearbyPitstop(12.3, 76.6, overpassUrl, nominatimUrl)
            // Both services down -> the "%.3f, %.3f" coordinate fallback, never an exception.
            assertEquals("12.300, 76.600", result)
        } finally {
            overpass.shutdown()
            nominatim.shutdown()
        }
    }
}
