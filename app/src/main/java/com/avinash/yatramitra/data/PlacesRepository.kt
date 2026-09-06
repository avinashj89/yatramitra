package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.PlaceSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Free place search and reverse-geocoding, backed by OpenStreetMap's Nominatim service.
 * No API key, no billing account — just a polite User-Agent as their usage policy asks for.
 * (https://operations.osmfoundation.org/policies/nominatim/)
 */
object PlacesRepository {

    private const val USER_AGENT = "YatraMitra-PersonalTripApp/1.0 (hobby project, no support contact)"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /** Returns up to 5 place suggestions for a free-typed query. Empty on error/no results. */
    suspend fun searchPlaces(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5&addressdetails=0"
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val arr = JSONArray(body)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.getJSONObject(i)
                    val displayName = obj.optString("display_name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val lat = obj.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                    val lon = obj.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                    PlaceSuggestion(displayName, lat, lon)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Geocodes a single free-typed place name to coordinates (first/best match), or null if not found. */
    suspend fun geocode(place: String): PlaceSuggestion? {
        return searchPlaces(place).firstOrNull()
    }

    /** Like [geocode], but rethrows a network/HTTP failure instead of masking it as "not found" —
     *  [searchPlaces] deliberately swallows every failure to keep the live-typing dropdown from
     *  ever crashing, but that also makes a real place name look identical to a dead network. This
     *  is for callers (pitstop generation) that need to tell a user their internet is down instead
     *  of wrongly claiming a real place like "Bangalore" doesn't exist. */
    suspend fun geocodeOrThrow(place: String): PlaceSuggestion? = withContext(Dispatchers.IO) {
        val query = place.trim()
        if (query.length < 2) return@withContext null
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1&addressdetails=0"
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("Place search failed (HTTP ${response.code})")
            }
            val arr = JSONArray(response.body?.string().orEmpty())
            if (arr.length() == 0) return@withContext null
            val obj = arr.getJSONObject(0)
            val displayName = obj.optString("display_name").takeIf { it.isNotBlank() } ?: return@withContext null
            val lat = obj.optString("lat").toDoubleOrNull() ?: return@withContext null
            val lon = obj.optString("lon").toDoubleOrNull() ?: return@withContext null
            PlaceSuggestion(displayName, lat, lon)
        }
    }

    /** Reverse-geocodes a coordinate to a short, human-readable place name. Falls back to "lat, lon" on failure. */
    suspend fun reverseGeocode(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&zoom=14"
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext fallbackName(lat, lon)
                val body = response.body?.string() ?: return@withContext fallbackName(lat, lon)
                val obj = org.json.JSONObject(body)
                val address = obj.optJSONObject("address")
                val short = address?.let {
                    it.optString("village").takeIf { v -> v.isNotBlank() }
                        ?: it.optString("town").takeIf { v -> v.isNotBlank() }
                        ?: it.optString("city").takeIf { v -> v.isNotBlank() }
                        ?: it.optString("suburb").takeIf { v -> v.isNotBlank() }
                        ?: it.optString("county").takeIf { v -> v.isNotBlank() }
                }
                short ?: obj.optString("display_name").takeIf { it.isNotBlank() } ?: fallbackName(lat, lon)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fallbackName(lat, lon)
        }
    }

    /** Looks for a nearby named amenity (food/fuel/rest spot) within ~3km using the free Overpass API.
     *  Falls back to reverseGeocode (a nearby place name) when nothing suitable is found. */
    suspend fun findNearbyPitstop(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val query = """
                [out:json][timeout:10];
                (
                  node["amenity"~"restaurant|cafe|fast_food|fuel"](around:3000,$lat,$lon);
                );
                out center 5;
            """.trimIndent()
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://overpass-api.de/api/interpreter?data=$encoded"
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext reverseGeocode(lat, lon)
                val body = response.body?.string() ?: return@withContext reverseGeocode(lat, lon)
                val obj = org.json.JSONObject(body)
                val elements = obj.optJSONArray("elements") ?: return@withContext reverseGeocode(lat, lon)
                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val tags = el.optJSONObject("tags")
                    val name = tags?.optString("name")?.takeIf { it.isNotBlank() }
                    if (name != null) return@withContext name
                }
                reverseGeocode(lat, lon)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reverseGeocode(lat, lon)
        }
    }

    private fun fallbackName(lat: Double, lon: Double): String =
        "%.3f, %.3f".format(lat, lon)
}
