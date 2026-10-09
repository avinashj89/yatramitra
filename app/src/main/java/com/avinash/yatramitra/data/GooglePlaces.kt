package com.avinash.yatramitra.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.avinash.yatramitra.BuildConfig
import com.avinash.yatramitra.model.PlaceSearchResult
import com.avinash.yatramitra.model.PlaceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Google place search (Places API, New): finds apartments, cafés, offices and landmarks that
 * OpenStreetMap doesn't have. Only active when a key is configured (see app/build.gradle.kts);
 * otherwise [isEnabled] is false and nothing here is called.
 *
 * Costs stay low on purpose: suggestions use a session token, so a search that ends in a pick is
 * billed as one Place Details lookup, and that lookup asks only for the address and location
 * (the cheapest "Essentials" fields). The name comes from the suggestion itself.
 */
object GooglePlaces {

    internal const val BASE_URL = "https://places.googleapis.com"
    private const val TAG = "GooglePlaces"

    /** The key plus the app identity Google checks when the key is locked to this Android app. */
    internal data class Config(val apiKey: String, val packageName: String, val certSha1: String)

    @Volatile
    private var config: Config? = null

    val isEnabled: Boolean get() = config != null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Called once at startup. Does nothing (OpenStreetMap stays in use) without a key. */
    fun configure(context: Context) {
        val key = BuildConfig.MAPS_API_KEY.trim()
        if (key.isEmpty()) return
        configureWith(Config(key, context.packageName, signingCertSha1(context)))
    }

    internal fun configureWith(value: Config?) {
        config = value
        // Places picked from Google whose saved location is older than Google allows us to keep
        // are looked up again by their place ID instead of by name.
        RouteRepository.locateHook = if (value == null) null else { place ->
            val id = place.placeId
            if (id == null) null else runCatching { details(id, null) }.getOrNull()?.let { RouteRepository.RoutePoint(it.lat, it.lng) }
        }
    }

    /** Suggestions for typed text, Indian results only, optionally biased to [near]. */
    suspend fun autocomplete(
        query: String,
        sessionToken: String,
        near: Pair<Double, Double>? = null,
        baseUrl: String = BASE_URL
    ): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val cfg = config ?: return@withContext emptyList()
        val body = JSONObject()
            .put("input", query.trim())
            .put("sessionToken", sessionToken)
            .put("includedRegionCodes", JSONArray().put("in"))
            .put("languageCode", "en")
        if (near != null) {
            body.put(
                "locationBias",
                JSONObject().put(
                    "circle",
                    JSONObject()
                        .put("center", JSONObject().put("latitude", near.first).put("longitude", near.second))
                        .put("radius", 50_000.0)
                )
            )
        }
        val request = Request.Builder()
            .url("$baseUrl/v1/places:autocomplete")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .withIdentity(cfg)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw java.io.IOException("Google place search failed (HTTP ${response.code}): ${errorMessage(text)}")
            parseAutocomplete(text)
        }
    }

    /** A picked place's address and location. */
    data class Details(val address: String, val lat: Double, val lng: Double)

    suspend fun details(placeId: String, sessionToken: String?, baseUrl: String = BASE_URL): Details = withContext(Dispatchers.IO) {
        val cfg = config ?: throw IllegalStateException("Google place search isn't set up")
        val id = URLEncoder.encode(placeId, "UTF-8")
        val session = sessionToken?.let { "?sessionToken=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        val request = Request.Builder()
            .url("$baseUrl/v1/places/$id$session")
            .get()
            .header("X-Goog-FieldMask", "id,formattedAddress,location")
            .withIdentity(cfg)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw java.io.IOException("Google place lookup failed (HTTP ${response.code}): ${errorMessage(text)}")
            parseDetails(text) ?: throw java.io.IOException("Google returned no location for that place")
        }
    }

    private fun Request.Builder.withIdentity(cfg: Config): Request.Builder = this
        .header("X-Goog-Api-Key", cfg.apiKey)
        .header("X-Android-Package", cfg.packageName)
        .header("X-Android-Cert", cfg.certSha1)

    internal fun parseAutocomplete(json: String): List<PlaceSearchResult> {
        val suggestions = JSONObject(json).optJSONArray("suggestions") ?: return emptyList()
        return (0 until suggestions.length()).mapNotNull { i ->
            val prediction = suggestions.optJSONObject(i)?.optJSONObject("placePrediction") ?: return@mapNotNull null
            val placeId = prediction.optString("placeId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val structured = prediction.optJSONObject("structuredFormat")
            val full = prediction.optJSONObject("text")?.optString("text").orEmpty()
            val title = structured?.optJSONObject("mainText")?.optString("text")?.takeIf { it.isNotBlank() }
                ?: full.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val subtitle = structured?.optJSONObject("secondaryText")?.optString("text").orEmpty()
            PlaceSearchResult(title = title, subtitle = subtitle, source = PlaceSource.GOOGLE, placeId = placeId)
        }
    }

    internal fun parseDetails(json: String): Details? {
        val obj = JSONObject(json)
        val location = obj.optJSONObject("location") ?: return null
        if (!location.has("latitude") || !location.has("longitude")) return null
        return Details(obj.optString("formattedAddress"), location.getDouble("latitude"), location.getDouble("longitude"))
    }

    private fun errorMessage(body: String): String =
        runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: body.take(200)

    /** The SHA-1 of the certificate this app is signed with, as Google expects it (upper-case
     *  hex, no colons). Read at runtime so a future release key works without code changes. */
    @Suppress("DEPRECATION")
    private fun signingCertSha1(context: Context): String = try {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val cert = signatures?.firstOrNull()?.toByteArray()
        if (cert == null) "" else MessageDigest.getInstance("SHA-1").digest(cert).joinToString("") { "%02X".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "couldn't read the app signature", e)
        ""
    }
}
