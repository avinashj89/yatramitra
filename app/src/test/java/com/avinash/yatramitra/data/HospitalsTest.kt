package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.DayHospitals
import com.avinash.yatramitra.model.Hospital
import com.avinash.yatramitra.model.Place
import com.avinash.yatramitra.model.PlaceSource
import com.avinash.yatramitra.model.RouteDay
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class HospitalsTest {

    private val overpassReply = """
        {"elements": [
          {"type": "node", "id": 1, "lat": 12.95, "lon": 77.60,
           "tags": {"amenity": "hospital", "name": "City Hospital", "phone": "+91 80 1234 5678; +91 80 8765 4321",
                    "emergency": "yes", "addr:housenumber": "12", "addr:street": "MG Road", "addr:city": "Bengaluru", "addr:postcode": "560001"}},
          {"type": "way", "id": 2, "center": {"lat": 12.60, "lon": 77.10},
           "tags": {"amenity": "hospital", "name": "District Hospital Ramanagara", "beds": "200", "contact:phone": "080-2727-1234"}},
          {"type": "node", "id": 3, "lat": 12.70, "lon": 77.20, "tags": {"amenity": "hospital"}},
          {"type": "relation", "id": 4, "tags": {"amenity": "hospital", "name": "No position"}},
          {"type": "node", "id": 1, "lat": 12.95, "lon": 77.60, "tags": {"amenity": "hospital", "name": "City Hospital"}}
        ]}
    """.trimIndent()

    @Test
    fun `reads named hospitals with a position, phone and address, once each`() {
        val hospitals = HospitalRules.parseOverpass(overpassReply)
        assertEquals(listOf("City Hospital", "District Hospital Ramanagara"), hospitals.map { it.name })
        val city = hospitals[0]
        assertEquals("+91 80 1234 5678", city.phone)
        assertEquals("12, MG Road, Bengaluru, 560001", city.address)
        assertTrue(city.emergency)
        assertEquals(12.60, hospitals[1].lat, 0.0)
        assertEquals("080-2727-1234", hospitals[1].phone)
        assertFalse(hospitals[1].emergency)
    }

    @Test
    fun `a reply with no elements is an empty list`() {
        assertTrue(HospitalRules.parseOverpass("{}").isEmpty())
    }

    @Test
    fun `emergency-ready, reachable, big hospitals score higher`() {
        val plain = Hospital(name = "Small Clinic Hospital")
        val ready = plain.copy(emergency = true, phone = "123")
        assertTrue(HospitalRules.score(ready) > HospitalRules.score(plain))
        val college = Hospital(name = "Bangalore Medical College")
        assertTrue(HospitalRules.score(college) > HospitalRules.score(plain))
        assertTrue(HospitalRules.score(plain, JSONObject().put("beds", "300")) > HospitalRules.score(plain))
    }

    private val straightRoute = RouteRepository.RouteInfo(
        points = (0..100).map { RouteRepository.RoutePoint(12.0 + it * 0.01, 77.0) }, // about 111 km due north
        distanceMeters = 111_000.0,
        durationSeconds = 7200.0
    )

    @Test
    fun `position along the route is km from the start and metres off the road`() {
        val (km, meters) = HospitalRules.positionOnRoute(12.5, 77.01, straightRoute)
        assertEquals(55.6, km, 1.0)
        assertEquals(1085.0, meters, 50.0)
    }

    @Test
    fun `the choice is spread along the drive and capped`() {
        // 40 equally good hospitals, all in the first 2 km (a big city at the start)...
        val crowded = (0 until 40).map {
            HospitalRules.Candidate(Hospital(id = "c$it", name = "City $it", kmFromStart = it * 0.05), 1.0, 100.0)
        }
        // ...and one modest one further along.
        val far = HospitalRules.Candidate(Hospital(id = "far", name = "Highway Hospital", kmFromStart = 80.0), 0.5, 3000.0)
        val chosen = HospitalRules.choose(crowded + far, routeKm = 111.0)
        assertTrue(chosen.any { it.id == "far" })
        assertTrue(chosen.size <= HospitalRules.MAX_PER_DAY)
        assertEquals(chosen.sortedBy { it.kmFromStart }, chosen)
        assertEquals(2, chosen.count { it.kmFromStart < 15 })
    }

    @Test
    fun `nothing found means nothing chosen`() {
        assertTrue(HospitalRules.choose(emptyList(), 100.0).isEmpty())
    }

    @Test
    fun `the route key changes when the day's places change`() {
        val day = RouteDay(from = Place("Bengaluru"), toStops = listOf(Place("Mysuru")))
        val same = RouteDay(from = Place("  bengaluru "), toStops = listOf(Place("MYSURU")))
        assertEquals(HospitalRules.routeKey(day), HospitalRules.routeKey(same))
        assertNotEquals(HospitalRules.routeKey(day), HospitalRules.routeKey(day.copy(toStops = listOf(Place("Coorg")))))
        assertNotEquals(HospitalRules.routeKey(day), HospitalRules.routeKey(day.copy(roundTrip = true)))
        val pinned = day.copy(from = Place("Bengaluru", lat = 12.97, lng = 77.59, source = PlaceSource.CURRENT_LOCATION))
        assertNotEquals(HospitalRules.routeKey(day), HospitalRules.routeKey(pinned))
    }

    @Test
    fun `long routes are thinned to a small query`() {
        val points = (0 until 1000).map { RouteRepository.RoutePoint(it.toDouble(), 0.0) }
        val thin = HospitalRules.thin(points, 80)
        assertEquals(80, thin.size)
        assertEquals(points.first(), thin.first())
        assertEquals(points.last(), thin.last())
        assertEquals(5, HospitalRules.thin(points.take(5)).size)
    }

    @Test
    fun `saved lists read back the same`() {
        val day = DayHospitals(
            dayIndex = 2,
            routeKey = "a|b",
            generatedAtMillis = 99L,
            hospitals = listOf(Hospital("node/1", "City Hospital", "MG Road", "123", 12.9, 77.6, 3.5, true))
        )
        assertEquals(day, HospitalRules.fromMap(HospitalRules.toMap(day)))
        val broken = HospitalRules.fromMap(mapOf("dayIndex" to 1, "hospitals" to listOf("junk", mapOf("name" to "No position"))))
        assertEquals(1, broken.dayIndex)
        assertTrue(broken.hospitals.isEmpty())
    }

    @Test
    fun `finding asks overpass for hospitals within 4 km of the route and ranks them`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody(overpassReply))
            val route = RouteRepository.RouteInfo(
                points = listOf(RouteRepository.RoutePoint(12.95, 77.60), RouteRepository.RoutePoint(12.60, 77.10)),
                distanceMeters = 65_000.0,
                durationSeconds = 3600.0
            )
            val found = HospitalFinder.findAlong(route, server.url("/").toString().trimEnd('/'))
            assertEquals(listOf("City Hospital", "District Hospital Ramanagara"), found.map { it.name })
            assertEquals(0.0, found[0].kmFromStart, 0.5)
            assertTrue(found[1].kmFromStart > 50)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/interpreter", request.path)
            val query = URLDecoder.decode(request.body.readUtf8().removePrefix("data="), "UTF-8")
            assertTrue(query.contains("\"amenity\"=\"hospital\""))
            assertTrue(query.contains("around:4000,12.95000,77.60000,12.60000,77.10000"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a failed lookup is an error the screen can offer to retry`() = runTest {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(MockResponse().setResponseCode(429))
            val route = RouteRepository.RouteInfo(
                points = listOf(RouteRepository.RoutePoint(1.0, 1.0), RouteRepository.RoutePoint(1.1, 1.1)),
                distanceMeters = 1000.0,
                durationSeconds = 60.0
            )
            try {
                HospitalFinder.findAlong(route, server.url("/").toString().trimEnd('/'))
                org.junit.Assert.fail("expected an error")
            } catch (e: java.io.IOException) {
                assertTrue(e.message!!.contains("429"))
            }
        } finally {
            server.shutdown()
        }
    }
}
