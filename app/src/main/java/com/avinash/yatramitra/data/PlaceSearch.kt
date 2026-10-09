package com.avinash.yatramitra.data

import android.util.Log
import com.avinash.yatramitra.model.Place
import com.avinash.yatramitra.model.PlaceSearchResult
import com.avinash.yatramitra.model.PlaceSource
import kotlinx.coroutines.CancellationException
import java.util.UUID

/**
 * The From/To search box's one entry point: [search] fills the dropdown, [select] turns the
 * picked row into a saved place with its exact location.
 *
 * Uses Google when a key is configured (it knows apartments, cafés and landmarks), and
 * OpenStreetMap otherwise, or whenever a Google request fails, so search never stops working.
 */
object PlaceSearch {

    private const val TAG = "PlaceSearch"

    /** Groups the keystrokes of one search with the pick that ends it, so Google bills the
     *  whole search as a single lookup. A new one starts after each pick. */
    @Volatile
    private var sessionToken: String = UUID.randomUUID().toString()

    suspend fun search(
        query: String,
        near: Pair<Double, Double>? = null,
        googleBaseUrl: String = GooglePlaces.BASE_URL,
        nominatimBaseUrl: String = PlacesRepository.NOMINATIM_BASE_URL
    ): List<PlaceSearchResult> {
        if (GooglePlaces.isEnabled) {
            try {
                return GooglePlaces.autocomplete(query, sessionToken, near, googleBaseUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { Log.w(TAG, "Google search failed, using OpenStreetMap", e) }
            }
        }
        return PlacesRepository.searchPlaces(query, nominatimBaseUrl).map { hit ->
            val (title, subtitle) = PlaceRules.splitDisplayName(hit.displayName)
            PlaceSearchResult(title = title, subtitle = subtitle, source = PlaceSource.OPENSTREETMAP, lat = hit.lat, lng = hit.lon)
        }
    }

    suspend fun select(result: PlaceSearchResult, googleBaseUrl: String = GooglePlaces.BASE_URL): Place {
        val now = System.currentTimeMillis()
        if (result.source != PlaceSource.GOOGLE || result.placeId == null) return PlaceRules.fromSearchResult(result, now)
        val token = sessionToken
        sessionToken = UUID.randomUUID().toString()
        val details = GooglePlaces.details(result.placeId, token, googleBaseUrl)
        return Place(
            name = result.title,
            address = details.address.ifBlank { result.subtitle },
            lat = details.lat,
            lng = details.lng,
            placeId = result.placeId,
            source = PlaceSource.GOOGLE,
            locatedAtMillis = now
        )
    }
}
