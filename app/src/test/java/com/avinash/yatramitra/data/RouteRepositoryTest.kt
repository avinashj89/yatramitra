package com.avinash.yatramitra.data

import com.avinash.yatramitra.data.RouteRepository.RoutePoint
import com.avinash.yatramitra.data.RouteRepository.RouteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRepositoryTest {

    @Test
    fun `haversineKm is zero for the same point`() {
        val p = RoutePoint(12.9716, 77.5946)
        assertEquals(0.0, RouteRepository.haversineKm(p, p), 0.0001)
    }

    @Test
    fun `haversineKm matches the well-known ~111 km per degree of latitude`() {
        val a = RoutePoint(0.0, 0.0)
        val b = RoutePoint(1.0, 0.0)
        assertEquals(111.19, RouteRepository.haversineKm(a, b), 0.5)
    }

    /** A straight line of points spaced ~111.19 km apart along the equator, so cumulative
     *  distance is easy to reason about: point i is roughly i * 111.19 km from the start. */
    private fun straightLineRoute(pointCount: Int): RouteInfo {
        val points = (0 until pointCount).map { RoutePoint(0.0, it.toDouble()) }
        val segmentKm = RouteRepository.haversineKm(points[0], points[1])
        val totalKm = segmentKm * (pointCount - 1)
        return RouteInfo(points, distanceMeters = totalKm * 1000, durationSeconds = 0.0)
    }

    @Test
    fun `samplePoints places one sample per interval along the route`() {
        val route = straightLineRoute(pointCount = 6) // ~555.97 km total, 5 segments
        val samples = RouteRepository.samplePoints(route, intervalKm = 100.0)
        // Expect samples near 100, 200, 300, 400, 500 km — none within 2km of the ~556km end.
        assertEquals(5, samples.size)
        val cumulativeKms = samples.map { it.second }
        assertEquals(listOf(100.0, 200.0, 300.0, 400.0, 500.0), cumulativeKms)
    }

    @Test
    fun `samplePoints drops a sample that lands within 2km of the route end`() {
        // One 10km segment; asking for a break at 9km falls inside the last-2km exclusion zone.
        val points = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 10.0 / 111.19))
        val route = RouteInfo(points, distanceMeters = 10_000.0, durationSeconds = 0.0)
        val samples = RouteRepository.samplePoints(route, intervalKm = 9.0)
        assertTrue(samples.isEmpty())
    }

    @Test
    fun `samplePoints never exceeds the requested cap`() {
        val route = straightLineRoute(pointCount = 21) // 20 segments, ~2223 km
        val samples = RouteRepository.samplePoints(route, intervalKm = 50.0, maxSamples = 3)
        assertEquals(3, samples.size)
    }

    @Test
    fun `samplePoints returns nothing for a non-positive interval`() {
        val route = straightLineRoute(pointCount = 6)
        assertTrue(RouteRepository.samplePoints(route, intervalKm = 0.0).isEmpty())
        assertTrue(RouteRepository.samplePoints(route, intervalKm = -5.0).isEmpty())
    }

    @Test
    fun `samplePoints returns nothing for a route with fewer than 2 points`() {
        val route = RouteInfo(listOf(RoutePoint(0.0, 0.0)), distanceMeters = 0.0, durationSeconds = 0.0)
        assertTrue(RouteRepository.samplePoints(route, intervalKm = 10.0).isEmpty())
    }
}
