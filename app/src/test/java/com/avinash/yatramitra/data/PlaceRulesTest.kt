package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.Place
import com.avinash.yatramitra.model.PlaceSearchResult
import com.avinash.yatramitra.model.PlaceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PlaceRulesTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `a saved location is used, a typed name is not`() {
        assertEquals(12.9 to 77.5, PlaceRules.usableLocation(Place(name = "X", lat = 12.9, lng = 77.5, source = PlaceSource.OPENSTREETMAP), now))
        assertNull(PlaceRules.usableLocation(Place(name = "X"), now))
        assertNull(PlaceRules.usableLocation(Place(name = "X", lat = 12.9), now))
        assertNull(PlaceRules.usableLocation(Place(name = "X", lat = 200.0, lng = 77.5), now))
    }

    @Test
    fun `google coordinates are only trusted for 30 days, the phone's own forever`() {
        val google = Place(name = "Cafe", lat = 1.0, lng = 2.0, placeId = "abc", source = PlaceSource.GOOGLE, locatedAtMillis = now - 29 * day)
        assertEquals(1.0 to 2.0, PlaceRules.usableLocation(google, now))
        assertNull(PlaceRules.usableLocation(google.copy(locatedAtMillis = now - 31 * day), now))
        val mine = Place(name = "Me", lat = 1.0, lng = 2.0, source = PlaceSource.CURRENT_LOCATION, locatedAtMillis = now - 400 * day)
        assertEquals(1.0 to 2.0, PlaceRules.usableLocation(mine, now))
    }

    @Test
    fun `openstreetmap names are split into a short title and an address line`() {
        assertEquals(
            "Jayanagar 5th Block" to "Bengaluru, Karnataka, 560041, India",
            PlaceRules.splitDisplayName("Jayanagar 5th Block, Bengaluru, Karnataka, 560041, India")
        )
        assertEquals("Hampi" to "", PlaceRules.splitDisplayName("Hampi"))
        assertEquals("" to "", PlaceRules.splitDisplayName(""))
    }

    @Test
    fun `a picked search result keeps its location and remembers when it was found`() {
        val picked = PlaceRules.fromSearchResult(PlaceSearchResult("House of Commons", "Jayanagar", PlaceSource.OPENSTREETMAP, 12.9, 77.5), now)
        assertEquals(Place("House of Commons", "Jayanagar", 12.9, 77.5, null, PlaceSource.OPENSTREETMAP, now), picked)
        val noLocation = PlaceRules.fromSearchResult(PlaceSearchResult("Cafe", "", PlaceSource.GOOGLE, placeId = "id1"), now)
        assertEquals(0L, noLocation.locatedAtMillis)
        assertEquals("id1", noLocation.placeId)
    }

    @Test
    fun `the current location gets a readable name`() {
        assertEquals("Near 10th Main Road, Jayanagar", PlaceRules.currentLocationName("10th Main Road", "Jayanagar", "Bengaluru", 1.0, 2.0))
        assertEquals("Near Jayanagar, Bengaluru", PlaceRules.currentLocationName(null, "Jayanagar", "Bengaluru", 1.0, 2.0))
        assertEquals("Near Bengaluru", PlaceRules.currentLocationName(" ", null, "Bengaluru", 1.0, 2.0))
        assertEquals("Pinned location (12.92500, 77.59380)", PlaceRules.currentLocationName(null, null, null, 12.925, 77.5938))
    }

    @Test
    fun `numbers are written with a dot whatever the phone's language`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("Pinned location (1.50000, 2.50000)", PlaceRules.currentLocationName(null, null, null, 1.5, 2.5))
            val place = Place(name = "X", lat = 1.5, lng = 2.5, source = PlaceSource.CURRENT_LOCATION)
            assertEquals("1.500000,2.500000", PlaceRules.waypointText(place, now))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `the line under the field says whether the exact place is saved`() {
        assertNull(PlaceRules.describe(Place(), now))
        assertEquals("Pick a suggestion to save the exact place", PlaceRules.describe(Place(name = "Jayan"), now))
        assertEquals("Jayanagar", PlaceRules.describe(Place(name = "X", address = "Jayanagar", lat = 1.0, lng = 2.0, source = PlaceSource.OPENSTREETMAP), now))
        assertEquals("Exact place saved", PlaceRules.describe(Place(name = "X", lat = 1.0, lng = 2.0, source = PlaceSource.OPENSTREETMAP), now))
        assertTrue(PlaceRules.describe(Place(name = "Near X", lat = 1.0, lng = 2.0, source = PlaceSource.CURRENT_LOCATION), now)!!.startsWith("Your location"))
    }

    @Test
    fun `google maps gets the exact point, google's place, or the name`() {
        val exact = Place(name = "Me", lat = 12.5, lng = 77.25, source = PlaceSource.CURRENT_LOCATION)
        assertEquals("12.500000,77.250000" to null, PlaceRules.mapsTarget(exact, now))
        val google = Place(name = "House of Commons", address = "Jayanagar", lat = 1.0, lng = 2.0, placeId = "pid", source = PlaceSource.GOOGLE, locatedAtMillis = now)
        assertEquals("House of Commons, Jayanagar" to "pid", PlaceRules.mapsTarget(google, now))
        assertEquals("Mysuru" to null, PlaceRules.mapsTarget(Place(name = "Mysuru"), now))
        assertEquals("1.000000,2.000000", PlaceRules.waypointText(google, now))
        assertEquals("Mysuru, Karnataka", PlaceRules.waypointText(Place(name = "Mysuru", address = "Karnataka"), now))
    }

    @Test
    fun `saved places read back the same, and bad data falls back to the name`() {
        val place = Place("House of Commons", "Jayanagar", 12.9, 77.5, "pid", PlaceSource.GOOGLE, now)
        assertEquals(place, PlaceRules.fromMap(PlaceRules.toMap(place), "House of Commons"))
        assertEquals(Place(name = "Other"), PlaceRules.fromMap(PlaceRules.toMap(place), "Other"))
        assertEquals(Place(name = "X"), PlaceRules.fromMap(null, "X"))
        assertEquals(Place(name = "X"), PlaceRules.fromMap("junk", "X"))
        val oddSource = PlaceRules.fromMap(mapOf("name" to "X", "source" to "MARS", "lat" to 1, "lng" to 2L), "X")
        assertEquals(PlaceSource.TYPED, oddSource.source)
        assertEquals(1.0, oddSource.lat!!, 0.0)
    }
}
