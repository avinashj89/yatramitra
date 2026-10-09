package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.PlaceSearchResult
import com.avinash.yatramitra.model.PlaceSource
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Google place search against a local mock server: what we send, what we read back, and that
 *  search falls back to OpenStreetMap when Google fails. */
class GooglePlacesTest {

    private lateinit var google: MockWebServer
    private lateinit var nominatim: MockWebServer

    private fun googleUrl() = google.url("/").toString().trimEnd('/')
    private fun nominatimUrl() = nominatim.url("/").toString().trimEnd('/')

    private val autocompleteReply = """
        {"suggestions": [
          {"placePrediction": {"placeId": "ChIJ-house", "text": {"text": "House of Commons, Jayanagar 5th Block, Bengaluru"},
            "structuredFormat": {"mainText": {"text": "House of Commons"}, "secondaryText": {"text": "Jayanagar 5th Block, Bengaluru, Karnataka"}}}},
          {"queryPrediction": {"text": {"text": "house of commons near me"}}},
          {"placePrediction": {"placeId": "ChIJ-plain", "text": {"text": "Lalbagh Botanical Garden"}}},
          {"placePrediction": {"text": {"text": "no id, skipped"}}}
        ]}
    """.trimIndent()

    @Before
    fun setUp() {
        google = MockWebServer().apply { start() }
        nominatim = MockWebServer().apply { start() }
        GooglePlaces.configureWith(GooglePlaces.Config("test-key", "com.avinash.yatramitra", "7B1C1BB17865B23AC744B2AF5C9F16D8FF59464C"))
    }

    @After
    fun tearDown() {
        GooglePlaces.configureWith(null)
        google.shutdown()
        nominatim.shutdown()
    }

    @Test
    fun `autocomplete sends the key, the app identity, the session and India-only`() = runTest {
        google.enqueue(MockResponse().setResponseCode(200).setBody(autocompleteReply))
        GooglePlaces.autocomplete("house of commons", "session-1", near = 12.9 to 77.5, baseUrl = googleUrl())

        val request = google.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/places:autocomplete", request.path)
        assertEquals("test-key", request.getHeader("X-Goog-Api-Key"))
        assertEquals("com.avinash.yatramitra", request.getHeader("X-Android-Package"))
        assertEquals("7B1C1BB17865B23AC744B2AF5C9F16D8FF59464C", request.getHeader("X-Android-Cert"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("house of commons", body.getString("input"))
        assertEquals("session-1", body.getString("sessionToken"))
        assertEquals("in", body.getJSONArray("includedRegionCodes").getString(0))
        assertEquals(12.9, body.getJSONObject("locationBias").getJSONObject("circle").getJSONObject("center").getDouble("latitude"), 0.0)
    }

    @Test
    fun `autocomplete reads places with a title and an address line, skipping the rest`() = runTest {
        google.enqueue(MockResponse().setResponseCode(200).setBody(autocompleteReply))
        val results = GooglePlaces.autocomplete("house", "s", baseUrl = googleUrl())
        assertEquals(
            listOf(
                PlaceSearchResult("House of Commons", "Jayanagar 5th Block, Bengaluru, Karnataka", PlaceSource.GOOGLE, placeId = "ChIJ-house"),
                PlaceSearchResult("Lalbagh Botanical Garden", "", PlaceSource.GOOGLE, placeId = "ChIJ-plain")
            ),
            results
        )
    }

    @Test
    fun `autocomplete with no suggestions is an empty list`() = runTest {
        google.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        assertTrue(GooglePlaces.autocomplete("zzzz", "s", baseUrl = googleUrl()).isEmpty())
    }

    @Test
    fun `a refused key is reported with Google's own reason`() = runTest {
        google.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error": {"code": 403, "message": "Requests from this Android client application are blocked."}}""")
        )
        try {
            GooglePlaces.autocomplete("house", "s", baseUrl = googleUrl())
            fail("expected an error")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("HTTP 403"))
            assertTrue(e.message!!.contains("Android client application are blocked"))
        }
    }

    @Test
    fun `details asks only for the cheap fields and reads the location`() = runTest {
        google.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"id": "ChIJ-house", "formattedAddress": "45, 10th Main Rd, Jayanagar 5th Block, Bengaluru 560041", "location": {"latitude": 12.925, "longitude": 77.5938}}"""
            )
        )
        val details = GooglePlaces.details("ChIJ-house", "session-1", googleUrl())
        assertEquals(GooglePlaces.Details("45, 10th Main Rd, Jayanagar 5th Block, Bengaluru 560041", 12.925, 77.5938), details)
        val request = google.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/places/ChIJ-house?sessionToken=session-1", request.path)
        assertEquals("id,formattedAddress,location", request.getHeader("X-Goog-FieldMask"))
    }

    @Test
    fun `details without a location is an error, not a place at 0,0`() = runTest {
        google.enqueue(MockResponse().setResponseCode(200).setBody("""{"id": "x", "formattedAddress": "Somewhere"}"""))
        try {
            GooglePlaces.details("x", null, googleUrl())
            fail("expected an error")
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("no location"))
        }
    }

    @Test
    fun `picking a google result saves google's id, address and location`() = runTest {
        google.enqueue(MockResponse().setResponseCode(200).setBody("""{"formattedAddress": "Full address", "location": {"latitude": 1.5, "longitude": 2.5}}"""))
        val place = PlaceSearch.select(
            PlaceSearchResult("House of Commons", "Jayanagar", PlaceSource.GOOGLE, placeId = "ChIJ-house"),
            googleUrl()
        )
        assertEquals("House of Commons", place.name)
        assertEquals("Full address", place.address)
        assertEquals(1.5, place.lat!!, 0.0)
        assertEquals("ChIJ-house", place.placeId)
        assertEquals(PlaceSource.GOOGLE, place.source)
        assertTrue(place.locatedAtMillis > 0)
    }

    @Test
    fun `search uses google when it works`() = runTest {
        google.enqueue(MockResponse().setResponseCode(200).setBody(autocompleteReply))
        val results = PlaceSearch.search("house", googleBaseUrl = googleUrl(), nominatimBaseUrl = nominatimUrl())
        assertEquals(PlaceSource.GOOGLE, results.first().source)
        assertEquals(0, nominatim.requestCount)
    }

    @Test
    fun `search falls back to openstreetmap when google fails`() = runTest {
        google.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        nominatim.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""[{"display_name": "Jayanagar 5th Block, Bengaluru, Karnataka", "lat": "12.92", "lon": "77.58"}]""")
        )
        val results = PlaceSearch.search("jayanagar", googleBaseUrl = googleUrl(), nominatimBaseUrl = nominatimUrl())
        assertEquals(listOf(PlaceSearchResult("Jayanagar 5th Block", "Bengaluru, Karnataka", PlaceSource.OPENSTREETMAP, 12.92, 77.58)), results)
    }

    @Test
    fun `without a key nothing is sent to google`() = runTest {
        GooglePlaces.configureWith(null)
        assertFalse(GooglePlaces.isEnabled)
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        PlaceSearch.search("jayanagar", googleBaseUrl = googleUrl(), nominatimBaseUrl = nominatimUrl())
        assertEquals(0, google.requestCount)
        assertEquals(1, nominatim.requestCount)
    }
}
