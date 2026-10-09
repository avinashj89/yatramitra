package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.Place
import com.avinash.yatramitra.model.PlaceSearchResult
import com.avinash.yatramitra.model.PlaceSource

/**
 * The From/To search box's one entry point: [search] fills the dropdown, [select] turns the
 * picked row into a saved place with its exact location. Today this uses OpenStreetMap.
 */
object PlaceSearch {

    suspend fun search(query: String): List<PlaceSearchResult> =
        PlacesRepository.searchPlaces(query).map { hit ->
            val (title, subtitle) = PlaceRules.splitDisplayName(hit.displayName)
            PlaceSearchResult(title = title, subtitle = subtitle, source = PlaceSource.OPENSTREETMAP, lat = hit.lat, lng = hit.lon)
        }

    suspend fun select(result: PlaceSearchResult): Place = PlaceRules.fromSearchResult(result, System.currentTimeMillis())
}
