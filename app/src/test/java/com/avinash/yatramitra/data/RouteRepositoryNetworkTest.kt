package com.avinash.yatramitra.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.TimeUnit

/**
 * Exercises RouteRepository.fetchRoute and the full suggestPitstops pipeline (geocode -> route ->
 * sample -> nearby-pitstop lookup) against real local MockWebServer instances standing in for
 * Nominatim, OSRM, and Overpass -- one server per "service" so each can be scripted independently,
 * exactly mirroring the three real endpoints this code calls in production.
 */
class RouteRepositoryNetworkTest {

    private lateinit var nominatim: MockWebServer
    private lateinit var osrm: MockWebServer
    private lateinit var overpass: MockWebServer

    @Before
    fun setUp() {
        nominatim = MockWebServer().apply { start() }
        osrm = MockWebServer().apply { start() }
        overpass = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        nominatim.shutdown()
        osrm.shutdown()
        overpass.shutdown()
    }

    private fun MockWebServer.baseUrl() = url("").toString().removeSuffix("/")

    private fun geocodeHit(name: String, lat: String, lon: String) =
        """[{"display_name":"$name","lat":"$lat","lon":"$lon"}]"""

    // Bangalore -> Mysore, ~150km, matching the two geocode hits enqueued alongside it.
    private val validOsrmRoute = """
        {"code":"Ok","routes":[{"distance":150000.0,"duration":7200.0,
        "geometry":{"coordinates":[[77.5946,12.9716],[76.6394,12.2958]]},
        "legs":[{"distance":150000.0}]}]}
    """.trimIndent()

    // runTest's lambda must return Unit, so the pitstop list is captured into this var from
    // inside the block rather than returned directly from runTest itself. If suggestPitstops
    // throws, that exception propagates out of runTest (and this function) before result is ever
    // assigned, so callers expecting an exception still see it correctly via try/catch.
    private fun runPipeline(
        names: List<String> = listOf("Bangalore", "Mysore"),
        breakEveryKm: Double? = 50.0,
        breakEveryHours: Double? = null
    ): List<RouteRepository.Pitstop> {
        lateinit var result: List<RouteRepository.Pitstop>
        runTest {
            result = RouteRepository.suggestPitstops(
                names, breakEveryKm, breakEveryHours,
                nominatim.baseUrl(), osrm.baseUrl(), overpass.baseUrl()
            )
        }
        return result
    }

    // ---- fetchRoute: the safe wrapper used by the passive Route Summary card ----

    @Test
    fun `fetchRoute valid response returns parsed distance and duration`() = runTest {
        osrm.enqueue(MockResponse().setResponseCode(200).setBody(validOsrmRoute))
        val points = listOf(RouteRepository.RoutePoint(12.9716, 77.5946), RouteRepository.RoutePoint(12.2958, 76.6394))
        val result = RouteRepository.fetchRoute(points, osrm.baseUrl())
        assertEquals(150000.0, result?.distanceMeters ?: 0.0, 0.1)
        assertEquals(2, result?.points?.size)
    }

    @Test
    fun `fetchRoute HTTP error returns null rather than throwing`() = runTest {
        osrm.enqueue(MockResponse().setResponseCode(500))
        val points = listOf(RouteRepository.RoutePoint(0.0, 0.0), RouteRepository.RoutePoint(1.0, 1.0))
        assertNull(RouteRepository.fetchRoute(points, osrm.baseUrl()))
    }

    @Test
    fun `fetchRoute malformed JSON returns null rather than crashing`() = runTest {
        osrm.enqueue(MockResponse().setResponseCode(200).setBody("not json at all"))
        val points = listOf(RouteRepository.RoutePoint(0.0, 0.0), RouteRepository.RoutePoint(1.0, 1.0))
        assertNull(RouteRepository.fetchRoute(points, osrm.baseUrl()))
    }

    @Test
    fun `fetchRoute boundary - fewer than 2 points returns null without a network call`() = runTest {
        assertNull(RouteRepository.fetchRoute(listOf(RouteRepository.RoutePoint(0.0, 0.0)), osrm.baseUrl()))
        assertEquals(0, osrm.requestCount)
    }

    // ---- suggestPitstops: the full "Generate pitstops" pipeline ----

    @Test
    fun `suggestPitstops valid end-to-end input returns a non-empty pitstop list`() {
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Bengaluru", "12.9716", "77.5946")))
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Mysuru", "12.2958", "76.6394")))
        osrm.enqueue(MockResponse().setResponseCode(200).setBody(validOsrmRoute))
        // The fake route is a single ~128km segment (only 2 geometry points), so a 100km break
        // interval yields exactly one sample (at the 100km mark) -> exactly one Overpass lookup.
        overpass.enqueue(MockResponse().setResponseCode(200).setBody("""{"elements":[{"tags":{"name":"Maddur Tiffanys"}}]}"""))
        val result = runPipeline(breakEveryKm = 100.0)
        assertTrue("expected at least one pitstop", result.isNotEmpty())
        assertEquals("Maddur Tiffanys", result.first().placeName)
    }

    @Test
    fun `suggestPitstops boundary - fewer than 2 place names throws immediately, no network calls`() {
        try {
            runPipeline(names = listOf("OnlyOnePlace"))
            fail("expected PitstopUnavailableException")
        } catch (e: RouteRepository.PitstopUnavailableException) {
            assertEquals("Add a From place and at least one To place first.", e.message)
        }
        assertEquals(0, nominatim.requestCount)
    }

    @Test
    fun `suggestPitstops geocoding a nonexistent place reports which place, not a generic error`() {
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Bengaluru", "12.9716", "77.5946")))
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody("[]")) // second place: no match
        try {
            runPipeline(names = listOf("Bangalore", "Nonexistentplacexyz"))
            fail("expected PitstopUnavailableException")
        } catch (e: RouteRepository.PitstopUnavailableException) {
            assertEquals("Couldn't find \"Nonexistentplacexyz\" — check the spelling or try a nearby landmark.", e.message)
        }
    }

    @Test
    fun `suggestPitstops malformed input - special characters in a place name still geocode correctly`() {
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Sao Paulo", "-23.55", "-46.63")))
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Rio", "-22.9", "-43.2")))
        osrm.enqueue(MockResponse().setResponseCode(200).setBody(validOsrmRoute))
        // Same single-sample math as the happy-path test (see comment there): one Overpass call,
        // which returns no elements here to also exercise the reverseGeocode fallback.
        overpass.enqueue(MockResponse().setResponseCode(200).setBody("""{"elements":[]}"""))
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody("""{"address":{"city":"Somewhere"}}""")) // reverseGeocode fallback
        val result = runPipeline(names = listOf("São Paulo 🇧🇷", "Rio de Janeiro"), breakEveryKm = 100.0)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `suggestPitstops environment - place-lookup service unreachable reports the real exception`() = runTest {
        // A disposable server shut down immediately after starting: its port refuses connections,
        // simulating "no network"/service-down without touching the shared servers @After relies on.
        val deadServer = MockWebServer().apply { start() }
        val deadUrl = deadServer.baseUrl()
        deadServer.shutdown()
        try {
            RouteRepository.suggestPitstops(
                listOf("Bangalore", "Mysore"), 50.0, null,
                deadUrl, osrm.baseUrl(), overpass.baseUrl()
            )
            fail("expected PitstopUnavailableException")
        } catch (e: RouteRepository.PitstopUnavailableException) {
            assertTrue(
                "expected the place-lookup-service message, got: ${e.message}",
                e.message?.startsWith("Couldn't reach the place-lookup service") == true
            )
        }
    }

    @Test
    fun `suggestPitstops environment - route service HTTP error reports the real status code`() {
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Bengaluru", "12.9716", "77.5946")))
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Mysuru", "12.2958", "76.6394")))
        osrm.enqueue(MockResponse().setResponseCode(503))
        try {
            runPipeline()
            fail("expected PitstopUnavailableException")
        } catch (e: RouteRepository.PitstopUnavailableException) {
            assertEquals(
                "Couldn't calculate a route (route service returned HTTP 503) — check your internet connection and try again.",
                e.message
            )
        }
    }

    @Test
    fun `suggestPitstops environment - malformed route response is reported, not a silent generic message`() {
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Bengaluru", "12.9716", "77.5946")))
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Mysuru", "12.2958", "76.6394")))
        osrm.enqueue(MockResponse().setResponseCode(200).setBody("{completely broken"))
        try {
            runPipeline()
            fail("expected PitstopUnavailableException")
        } catch (e: RouteRepository.PitstopUnavailableException) {
            assertTrue(e.message?.startsWith("Couldn't calculate a route") == true)
        }
    }

    @Test
    fun `suggestPitstops boundary - break amount of zero is rejected with a specific message`() {
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Bengaluru", "12.9716", "77.5946")))
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Mysuru", "12.2958", "76.6394")))
        osrm.enqueue(MockResponse().setResponseCode(200).setBody(validOsrmRoute))
        try {
            runPipeline(breakEveryKm = 0.0, breakEveryHours = null)
            fail("expected PitstopUnavailableException")
        } catch (e: RouteRepository.PitstopUnavailableException) {
            assertEquals("Enter a break amount greater than zero.", e.message)
        }
    }

    @Test
    fun `suggestPitstops boundary - break interval longer than the route reports no pitstops needed`() {
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Bengaluru", "12.9716", "77.5946")))
        nominatim.enqueue(MockResponse().setResponseCode(200).setBody(geocodeHit("Mysuru", "12.2958", "76.6394")))
        osrm.enqueue(MockResponse().setResponseCode(200).setBody(validOsrmRoute)) // ~150km route
        try {
            runPipeline(breakEveryKm = 500.0) // far longer than the 150km route
            fail("expected PitstopUnavailableException")
        } catch (e: RouteRepository.PitstopUnavailableException) {
            assertTrue(e.message?.contains("shorter than your break interval") == true)
        }
    }

    @Test
    fun `suggestPitstops cancellation is rethrown, not reported as a PitstopUnavailableException`() = runBlocking {
        // A deliberately slow first geocode response, long enough to reliably still be in-flight
        // when we cancel -- this reproduces the exact class of bug fixed earlier (a bare
        // "catch (e: Exception)" swallowing CancellationException and misreporting it as a
        // generic connectivity error).
        nominatim.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(geocodeHit("Bengaluru", "12.9716", "77.5946"))
                .setBodyDelay(1500, TimeUnit.MILLISECONDS)
        )
        var thrown: Throwable? = null
        val job = launch(Dispatchers.Default) {
            try {
                RouteRepository.suggestPitstops(
                    listOf("Bangalore", "Mysore"), 50.0, null,
                    nominatim.baseUrl(), osrm.baseUrl(), overpass.baseUrl()
                )
            } catch (e: Throwable) {
                thrown = e
            }
        }
        delay(200) // let the coroutine actually reach the in-flight (slow) network call
        job.cancel()
        job.join()
        assertTrue("expected a CancellationException, got: $thrown", thrown is CancellationException)
    }
}
