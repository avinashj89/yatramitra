package com.avinash.yatramitra.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Free route calculation via the public OSRM demo server (https://project-osrm.org/), plus
 * sampling points along that route for the pitstop finder. No API key, no billing account.
 * The public demo server is rate-limited and best-effort (not for heavy/commercial use), which
 * is a fine trade-off for a personal trip-planning app.
 */
object RouteRepository {

    data class RoutePoint(val lat: Double, val lon: Double)
    data class RouteInfo(
        val points: List<RoutePoint>,   // full route geometry, in order
        val distanceMeters: Double,
        val durationSeconds: Double,
        // One entry per pair of consecutive input waypoints (OSRM "legs"), so callers can work out
        // each named stop's cumulative distance from the start without re-walking the geometry.
        val legDistancesMeters: List<Double> = emptyList()
    )

    /** A suggested pitstop: a place name plus how far into the route it falls. */
    data class Pitstop(val placeName: String, val distanceKm: Double, val elapsedMinutes: Double)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Fetches the driving route through an ordered list of coordinates (at least 2 points). */
    suspend fun fetchRoute(stops: List<RoutePoint>): RouteInfo? = withContext(Dispatchers.IO) {
        if (stops.size < 2) return@withContext null
        try {
            val coordsParam = stops.joinToString(";") { "${it.lon},${it.lat}" }
            val url = "https://router.project-osrm.org/route/v1/driving/$coordsParam?overview=full&geometries=geojson"
            val request = Request.Builder().url(url).header("User-Agent", "YatraMitra-PersonalTripApp/1.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val obj = JSONObject(body)
                if (obj.optString("code") != "Ok") return@withContext null
                val route = obj.getJSONArray("routes").getJSONObject(0)
                val distance = route.optDouble("distance", 0.0)
                val duration = route.optDouble("duration", 0.0)
                val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
                val points = (0 until coords.length()).map { i ->
                    val pair = coords.getJSONArray(i)
                    RoutePoint(lat = pair.getDouble(1), lon = pair.getDouble(0))
                }
                val legs = route.optJSONArray("legs")
                val legDistances = if (legs != null) {
                    (0 until legs.length()).map { legs.getJSONObject(it).optDouble("distance", 0.0) }
                } else {
                    emptyList()
                }
                RouteInfo(points, distance, duration, legDistances)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Great-circle distance between two points, in kilometers. */
    private fun haversineKm(a: RoutePoint, b: RoutePoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * Walks the route geometry and picks sample points spaced roughly every [intervalKm],
     * skipping the very start/end (those are already the named From/To stops). Capped at
     * [maxSamples] to be gentle on the free lookup services.
     */
    private fun samplePoints(route: RouteInfo, intervalKm: Double, maxSamples: Int = 8): List<Pair<RoutePoint, Double>> {
        if (intervalKm <= 0.0 || route.points.size < 2) return emptyList()
        val samples = mutableListOf<Pair<RoutePoint, Double>>() // point, cumulative km
        var cumulativeKm = 0.0
        var nextTarget = intervalKm
        for (i in 1 until route.points.size) {
            val segmentKm = haversineKm(route.points[i - 1], route.points[i])
            cumulativeKm += segmentKm
            while (nextTarget <= cumulativeKm && samples.size < maxSamples) {
                samples.add(route.points[i] to nextTarget)
                nextTarget += intervalKm
            }
            if (samples.size >= maxSamples) break
        }
        // Drop a sample that's essentially at the very end of the route (within 2km of total distance).
        val totalKm = route.distanceMeters / 1000.0
        return samples.filter { (_, km) -> km < totalKm - 2.0 }
    }

    /** Geocodes an ordered list of typed place names and fetches the driving route through them.
     *  Null if fewer than 2 valid names, any place can't be found, or the route lookup fails. */
    suspend fun fetchRouteSummary(orderedPlaceNames: List<String>): RouteInfo? {
        val validNames = orderedPlaceNames.map { it.trim() }.filter { it.isNotBlank() }
        if (validNames.size < 2) return null
        val geocoded = validNames.map { PlacesRepository.geocode(it) }
        if (geocoded.any { it == null }) return null
        val points = geocoded.map { RoutePoint(it!!.lat, it.lon) }
        return fetchRoute(points)
    }

    /**
     * End-to-end pitstop suggestion: geocode the named stops, fetch the route, sample points
     * according to the break preference, and look up a real nearby place name for each sample.
     * Returns an empty list (rather than throwing) if anything along the way fails, so the UI
     * can show a friendly "couldn't find pitstops right now" message.
     */
    suspend fun suggestPitstops(
        orderedPlaceNames: List<String>,
        breakEveryKm: Double? ,
        breakEveryHours: Double?
    ): List<Pitstop> {
        val route = fetchRouteSummary(orderedPlaceNames) ?: return emptyList()
        val totalKm = route.distanceMeters / 1000.0
        val totalHours = route.durationSeconds / 3600.0
        val avgSpeedKmh = if (totalHours > 0) totalKm / totalHours else 0.0

        val intervalKm = when {
            breakEveryKm != null && breakEveryKm > 0 -> breakEveryKm
            breakEveryHours != null && breakEveryHours > 0 && avgSpeedKmh > 0 -> breakEveryHours * avgSpeedKmh
            else -> return emptyList()
        }

        val samples = samplePoints(route, intervalKm)
        val results = mutableListOf<Pitstop>()
        for ((point, km) in samples) {
            val name = PlacesRepository.findNearbyPitstop(point.lat, point.lon)
            val elapsedMinutes = if (totalKm > 0) (km / totalKm) * (route.durationSeconds / 60.0) else 0.0
            results.add(Pitstop(name, km, elapsedMinutes))
            delay(300) // be gentle on the free Overpass API between lookups
        }
        return results
    }
}
