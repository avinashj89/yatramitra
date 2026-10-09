package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.Place
import com.avinash.yatramitra.model.PlaceSearchResult
import com.avinash.yatramitra.model.PlaceSource
import java.util.Locale

/**
 * Pure rules for route places: when a saved location can be trusted, how places are stored in
 * Firestore, and how they are described and handed to Google Maps. Covered by PlaceRulesTest.
 */
object PlaceRules {

    /** Google's terms let an app keep coordinates from its place search for up to 30 days. */
    const val GOOGLE_LOCATION_MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun typed(name: String) = Place(name = name)

    /** The saved location if it can still be used; null means "look it up again". */
    fun usableLocation(place: Place, now: Long): Pair<Double, Double>? {
        val lat = place.lat ?: return null
        val lng = place.lng ?: return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        if (place.source == PlaceSource.GOOGLE && now - place.locatedAtMillis > GOOGLE_LOCATION_MAX_AGE_MILLIS) return null
        return lat to lng
    }

    fun fromSearchResult(result: PlaceSearchResult, now: Long): Place = Place(
        name = result.title,
        address = result.subtitle,
        lat = result.lat,
        lng = result.lng,
        placeId = result.placeId,
        source = result.source,
        locatedAtMillis = if (result.lat != null && result.lng != null) now else 0L
    )

    /** OpenStreetMap's "Jayanagar 5th Block, Bengaluru, Karnataka, 560041, India" as a short
     *  title ("Jayanagar 5th Block") and the rest as the address line. */
    fun splitDisplayName(displayName: String): Pair<String, String> {
        val parts = displayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return displayName.trim() to ""
        return parts.first() to parts.drop(1).joinToString(", ")
    }

    /** A readable name for the phone's own position, from whatever the reverse lookup found:
     *  "Near 10th Main Road, Jayanagar", or the coordinates if nothing was found. */
    fun currentLocationName(
        thoroughfare: String?,
        subLocality: String?,
        locality: String?,
        lat: Double,
        lng: Double
    ): String {
        val near = listOfNotNull(thoroughfare, subLocality, locality)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(2)
        return if (near.isEmpty()) "Pinned location (%.5f, %.5f)".format(Locale.US, lat, lng) else "Near " + near.joinToString(", ")
    }

    /** The line shown under a From/To field, so it's clear whether the exact place is saved. */
    fun describe(place: Place, now: Long): String? = when {
        place.name.isBlank() -> null
        place.source == PlaceSource.CURRENT_LOCATION -> "Your location when you tapped the button" +
            (place.address.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        usableLocation(place, now) != null || place.placeId != null -> place.address.ifBlank { "Exact place saved" }
        else -> "Pick a suggestion to save the exact place"
    }

    /** What to give Google Maps for this place: its exact position when known, else its name.
     *  The second value is Google's place ID, when there is one. */
    fun mapsTarget(place: Place, now: Long): Pair<String, String?> {
        val location = usableLocation(place, now)
        val text = when {
            place.placeId != null -> listOf(place.name, place.address).filter { it.isNotBlank() }.joinToString(", ")
            location != null -> "%.6f,%.6f".format(Locale.US, location.first, location.second)
            else -> listOf(place.name, place.address).filter { it.isNotBlank() }.joinToString(", ")
        }
        return text to place.placeId
    }

    /** A stop between the start and the destination: the exact point when known (Maps routes
     *  through precisely that spot), otherwise the name and address. */
    fun waypointText(place: Place, now: Long): String {
        val location = usableLocation(place, now)
        return if (location != null) {
            "%.6f,%.6f".format(Locale.US, location.first, location.second)
        } else {
            listOf(place.name, place.address).filter { it.isNotBlank() }.joinToString(", ")
        }
    }

    fun toMap(place: Place): Map<String, Any?> = mapOf(
        "name" to place.name,
        "address" to place.address,
        "lat" to place.lat,
        "lng" to place.lng,
        "placeId" to place.placeId,
        "source" to place.source.name,
        "locatedAt" to place.locatedAtMillis
    )

    /** Reads a saved place. [name] is the plain name saved next to it: if the two disagree
     *  (an older app version changed the name without knowing about places), the name wins and
     *  the stale location is dropped. */
    fun fromMap(map: Any?, name: String): Place {
        val m = map as? Map<*, *> ?: return typed(name)
        val savedName = m["name"] as? String ?: ""
        if (savedName != name) return typed(name)
        return Place(
            name = name,
            address = m["address"] as? String ?: "",
            lat = (m["lat"] as? Number)?.toDouble(),
            lng = (m["lng"] as? Number)?.toDouble(),
            placeId = (m["placeId"] as? String)?.takeIf { it.isNotBlank() },
            source = runCatching { PlaceSource.valueOf(m["source"] as? String ?: "") }.getOrDefault(PlaceSource.TYPED),
            locatedAtMillis = (m["locatedAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
