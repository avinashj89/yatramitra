package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.DayHospitals
import com.avinash.yatramitra.model.Hospital
import com.avinash.yatramitra.model.RouteDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Pure rules for the hospitals-along-the-route list: reading OpenStreetMap results, choosing the
 * prominent ones, spreading them along the drive, and saving them. Covered by HospitalsTest.
 */
object HospitalRules {

    /** How far either side of the route to look. */
    const val CORRIDOR_METERS = 4000

    /** At most this many per day, so the list stays quick to scan in an emergency. */
    const val MAX_PER_DAY = 15

    /** A hospital as read from OpenStreetMap, before choosing. */
    data class Candidate(val hospital: Hospital, val score: Double, val metersFromRoute: Double)

    /** Reads an Overpass reply: named hospitals with a position; everything else is skipped. */
    fun parseOverpass(json: String): List<Hospital> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val seen = HashSet<String>()
        return (0 until elements.length()).mapNotNull { i ->
            val el = elements.optJSONObject(i) ?: return@mapNotNull null
            val tags = el.optJSONObject("tags") ?: return@mapNotNull null
            val name = tags.optString("name").trim().ifEmpty { tags.optString("name:en").trim() }
            if (name.isEmpty()) return@mapNotNull null
            val center = el.optJSONObject("center")
            val lat = if (el.has("lat")) el.optDouble("lat") else center?.optDouble("lat") ?: Double.NaN
            val lng = if (el.has("lon")) el.optDouble("lon") else center?.optDouble("lon") ?: Double.NaN
            if (lat.isNaN() || lng.isNaN()) return@mapNotNull null
            val id = "${el.optString("type")}/${el.optLong("id")}"
            if (!seen.add(id)) return@mapNotNull null
            Hospital(
                id = id,
                name = name,
                address = address(tags),
                phone = phone(tags),
                lat = lat,
                lng = lng,
                emergency = tags.optString("emergency") == "yes"
            )
        }
    }

    /** "45, 10th Main Road, Jayanagar, Bengaluru 560041" from OpenStreetMap's address tags. */
    fun address(tags: JSONObject): String {
        tags.optString("addr:full").trim().takeIf { it.isNotEmpty() }?.let { return it }
        val street = listOf(tags.optString("addr:housenumber"), tags.optString("addr:street"))
            .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
        val area = listOf("addr:suburb", "addr:district", "addr:city", "addr:place")
            .map { tags.optString(it).trim() }.filter { it.isNotEmpty() }.distinct()
        val cityLine = (area + listOf(tags.optString("addr:postcode").trim()).filter { it.isNotEmpty() }).joinToString(", ")
        return listOf(street, cityLine).filter { it.isNotEmpty() }.joinToString(", ")
    }

    /** The first listed phone number (OpenStreetMap allows several, separated by ";"). */
    fun phone(tags: JSONObject): String =
        listOf("phone", "contact:phone", "contact:mobile", "mobile", "phone:mobile")
            .map { tags.optString(it).trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.split(";", ",")?.first()?.trim()
            .orEmpty()

    /** Bigger, better-documented, emergency-ready hospitals first. */
    fun score(h: Hospital, tags: JSONObject? = null): Double {
        var s = 0.0
        if (h.emergency) s += 3.0
        if (h.phone.isNotEmpty()) s += 2.0
        val name = h.name.lowercase(Locale.ROOT)
        if (listOf("medical college", "district hospital", "general hospital", "government hospital", "aiims", "institute").any { it in name }) s += 2.0
        val beds = tags?.optString("beds")?.toIntOrNull()
        if (beds != null) s += if (beds >= 50) 2.0 else 1.0
        if (h.address.isNotEmpty()) s += 0.5
        return s
    }

    /** Where along the route a point is (km from the start) and how far off the route it lies. */
    fun positionOnRoute(lat: Double, lng: Double, route: RouteRepository.RouteInfo): Pair<Double, Double> {
        if (route.points.isEmpty()) return 0.0 to Double.MAX_VALUE
        val target = RouteRepository.RoutePoint(lat, lng)
        var cumulativeKm = 0.0
        var bestKm = 0.0
        var bestDistanceKm = Double.MAX_VALUE
        route.points.forEachIndexed { i, point ->
            if (i > 0) cumulativeKm += RouteRepository.haversineKm(route.points[i - 1], point)
            val d = RouteRepository.haversineKm(point, target)
            if (d < bestDistanceKm) {
                bestDistanceKm = d
                bestKm = cumulativeKm
            }
        }
        return bestKm to bestDistanceKm * 1000
    }

    /** Spreads the choice along the drive: the best two in every stretch, then the overall best,
     *  so a long day has help every few dozen km instead of 15 hospitals all in the start city. */
    fun choose(candidates: List<Candidate>, routeKm: Double): List<Hospital> {
        if (candidates.isEmpty()) return emptyList()
        val stretchKm = max(15.0, routeKm / 10)
        val perStretch = candidates
            .groupBy { (it.hospital.kmFromStart / stretchKm).toInt() }
            .values
            .flatMap { group -> group.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.metersFromRoute }).take(2) }
        return perStretch
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.metersFromRoute })
            .take(MAX_PER_DAY)
            .map { it.hospital }
            .sortedBy { it.kmFromStart }
    }

    /** Identifies a day's route, so a saved list is only reused for the same route. */
    fun routeKey(day: RouteDay): String =
        RoutePlans.placesInOrder(day).joinToString("|") { place ->
            val at = if (place.lat != null && place.lng != null) "@%.3f,%.3f".format(Locale.US, place.lat, place.lng) else ""
            place.name.trim().lowercase(Locale.ROOT) + at
        }

    /** Keeps an Overpass query small: up to [max] points evenly picked along the route. */
    fun thin(points: List<RouteRepository.RoutePoint>, max: Int = 80): List<RouteRepository.RoutePoint> {
        if (points.size <= max) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        return (0 until max).map { points[Math.round(it * step).toInt()] }
    }

    fun overpassQuery(points: List<RouteRepository.RoutePoint>): String {
        val line = thin(points).joinToString(",") { "%.5f,%.5f".format(Locale.US, it.lat, it.lon) }
        val around = "(around:$CORRIDOR_METERS,$line)"
        return """
            [out:json][timeout:40];
            (
              node["amenity"="hospital"]$around;
              way["amenity"="hospital"]$around;
              relation["amenity"="hospital"]$around;
            );
            out center tags 300;
        """.trimIndent()
    }

    fun toMap(day: DayHospitals): Map<String, Any?> = mapOf(
        "dayIndex" to day.dayIndex,
        "routeKey" to day.routeKey,
        "generatedAt" to day.generatedAtMillis,
        "hospitals" to day.hospitals.map {
            mapOf(
                "id" to it.id, "name" to it.name, "address" to it.address, "phone" to it.phone,
                "lat" to it.lat, "lng" to it.lng, "kmFromStart" to it.kmFromStart, "emergency" to it.emergency
            )
        }
    )

    fun fromMap(map: Map<*, *>): DayHospitals = DayHospitals(
        dayIndex = (map["dayIndex"] as? Number)?.toInt() ?: 0,
        routeKey = map["routeKey"] as? String ?: "",
        generatedAtMillis = (map["generatedAt"] as? Number)?.toLong() ?: 0L,
        hospitals = (map["hospitals"] as? List<*>)?.mapNotNull { item ->
            val h = item as? Map<*, *> ?: return@mapNotNull null
            val name = h["name"] as? String ?: return@mapNotNull null
            Hospital(
                id = h["id"] as? String ?: "",
                name = name,
                address = h["address"] as? String ?: "",
                phone = h["phone"] as? String ?: "",
                lat = (h["lat"] as? Number)?.toDouble() ?: return@mapNotNull null,
                lng = (h["lng"] as? Number)?.toDouble() ?: return@mapNotNull null,
                kmFromStart = (h["kmFromStart"] as? Number)?.toDouble() ?: 0.0,
                emergency = h["emergency"] as? Boolean ?: false
            )
        }.orEmpty()
    )
}

/** Finds hospitals near a driving route using OpenStreetMap's free Overpass service. */
object HospitalFinder {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            // Overpass's certificate chain needs this on older Android versions (see TrustedHttpClients).
            .sslSocketFactory(TrustedHttpClients.sslSocketFactory, TrustedHttpClients.trustManager)
            .build()
    }

    /** Prominent hospitals within [HospitalRules.CORRIDOR_METERS] of [route], in driving order.
     *  Throws if the lookup itself fails, so the screen can offer to try again. */
    suspend fun findAlong(
        route: RouteRepository.RouteInfo,
        overpassBaseUrl: String = PlacesRepository.OVERPASS_BASE_URL
    ): List<Hospital> = withContext(Dispatchers.IO) {
        if (route.points.size < 2) return@withContext emptyList()
        val request = Request.Builder()
            .url("$overpassBaseUrl/api/interpreter")
            .post(FormBody.Builder().add("data", HospitalRules.overpassQuery(route.points)).build())
            .header("User-Agent", "YatraMitra-PersonalTripApp/1.0")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("hospital lookup returned HTTP ${response.code}")
            response.body?.string() ?: throw java.io.IOException("hospital lookup returned nothing")
        }
        val tagsById = tagsById(body)
        val candidates = HospitalRules.parseOverpass(body).map { h ->
            val (km, meters) = HospitalRules.positionOnRoute(h.lat, h.lng, route)
            val placed = h.copy(kmFromStart = km)
            HospitalRules.Candidate(placed, HospitalRules.score(placed, tagsById[h.id]), meters)
        }
        HospitalRules.choose(candidates, route.distanceMeters / 1000.0)
    }

    private fun tagsById(json: String): Map<String, JSONObject> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyMap()
        return (0 until elements.length()).mapNotNull { i ->
            val el = elements.optJSONObject(i) ?: return@mapNotNull null
            val tags = el.optJSONObject("tags") ?: return@mapNotNull null
            "${el.optString("type")}/${el.optLong("id")}" to tags
        }.toMap()
    }
}
