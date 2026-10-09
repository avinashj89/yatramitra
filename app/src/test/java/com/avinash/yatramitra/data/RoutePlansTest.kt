package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.BreakUnit
import com.avinash.yatramitra.model.Place
import com.avinash.yatramitra.model.PlaceSource
import com.avinash.yatramitra.model.RouteDay
import com.avinash.yatramitra.model.RoutePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlansTest {

    private fun p(name: String) = Place(name = name)
    private fun day(from: String = "", vararg to: String, roundTrip: Boolean = false, pitstops: Boolean = true) =
        RouteDay(from = p(from), toStops = if (to.isEmpty()) listOf(Place()) else to.map(::p), roundTrip = roundTrip, pitstopsEnabled = pitstops)

    private val day1 = day("Bengaluru", "Mysuru", "Madikeri")

    // A place picked from a search, with its exact location.
    private val houseOfCommons = Place(
        name = "House of Commons",
        address = "Jayanagar 5th Block, Bengaluru",
        lat = 12.9250,
        lng = 77.5938,
        source = PlaceSource.OPENSTREETMAP,
        locatedAtMillis = 1_000L
    )

    @Test
    fun `a new trip starts with exactly one empty day`() {
        assertEquals(1, RoutePlan().days.size)
        assertEquals(RouteDay(), RoutePlan().days.first())
    }

    @Test
    fun `a new day starts where the previous day ended`() {
        val plan = RoutePlans.addDay(RoutePlan(days = listOf(day1)))
        assertEquals(2, plan.days.size)
        assertEquals("Madikeri", plan.days[1].from.name)
        assertEquals(listOf(Place()), plan.days[1].toStops)
    }

    @Test
    fun `a new day keeps the exact location of where the previous day ended`() {
        val plan = RoutePlans.addDay(RoutePlan(days = listOf(day1.copy(toStops = listOf(houseOfCommons)))))
        assertEquals(houseOfCommons, plan.days[1].from)
    }

    @Test
    fun `after a round trip day the next day starts back at the start`() {
        val plan = RoutePlans.addDay(RoutePlan(days = listOf(day1.copy(roundTrip = true))))
        assertEquals("Bengaluru", plan.days[1].from.name)
    }

    @Test
    fun `a new day after an unfinished day starts blank`() {
        val plan = RoutePlans.addDay(RoutePlan(days = listOf(day("Bengaluru"))))
        assertEquals("", plan.days[1].from.name)
    }

    @Test
    fun `days stop at the maximum`() {
        var plan = RoutePlan()
        repeat(RoutePlan.MAX_DAYS + 3) { plan = RoutePlans.addDay(plan) }
        assertEquals(RoutePlan.MAX_DAYS, plan.days.size)
    }

    @Test
    fun `removing a middle day moves later days up`() {
        val plan = RoutePlan(days = listOf(day("A"), day("B"), day("C")))
        assertEquals(listOf("A", "C"), RoutePlans.removeDay(plan, 1).days.map { it.from.name })
    }

    @Test
    fun `removing the only day leaves one empty day`() {
        val plan = RoutePlans.removeDay(RoutePlan(days = listOf(day1)), 0)
        assertEquals(listOf(RouteDay()), plan.days)
    }

    @Test
    fun `out of range edits change nothing`() {
        val plan = RoutePlan(days = listOf(day1))
        assertEquals(plan, RoutePlans.removeDay(plan, 5))
        assertEquals(plan, RoutePlans.updateDay(plan, 5, day("X")))
    }

    @Test
    fun `updating one day leaves the others alone`() {
        val plan = RoutePlan(days = listOf(day("A"), day("B")))
        val updated = RoutePlans.updateDay(plan, 1, day("Z"))
        assertEquals(listOf("A", "Z"), updated.days.map { it.from.name })
    }

    @Test
    fun `places are in driving order without blanks, back to the start on a round trip`() {
        val d = day(" Bengaluru ", "Mysuru", " ", "Madikeri")
        assertEquals(listOf("Bengaluru", "Mysuru", "Madikeri"), RoutePlans.placesInOrder(d).map { it.name })
        assertEquals(
            listOf("Bengaluru", "Mysuru", "Madikeri", "Bengaluru"),
            RoutePlans.placesInOrder(d.copy(roundTrip = true)).map { it.name }
        )
    }

    @Test
    fun `a day needs a start and a destination to count as a route`() {
        assertTrue(RoutePlans.hasRoute(day1))
        assertFalse(RoutePlans.hasRoute(day("Bengaluru")))
        assertFalse(RoutePlans.hasRoute(RouteDay(toStops = listOf(p("Mysuru")))))
    }

    @Test
    fun `saving and loading keeps every day and setting`() {
        val plan = RoutePlan(
            days = listOf(day1, day("Madikeri", "Bengaluru", roundTrip = true, pitstops = false)),
            breakEvery = "2",
            breakUnit = BreakUnit.KM,
            pitstopCategories = setOf("Scenic Viewpoints")
        )
        assertEquals(plan, RoutePlans.fromMap(RoutePlans.toMap(plan)))
    }

    @Test
    fun `exact places survive saving and loading`() {
        val current = Place(name = "Near 10th Main Road, Jayanagar", lat = 12.93, lng = 77.58, source = PlaceSource.CURRENT_LOCATION, locatedAtMillis = 5L)
        val plan = RoutePlan(days = listOf(RouteDay(from = current, toStops = listOf(houseOfCommons, p("Mysuru")))))
        val loaded = RoutePlans.fromMap(RoutePlans.toMap(plan))
        assertEquals(current, loaded.days[0].from)
        assertEquals(listOf(houseOfCommons, p("Mysuru")), loaded.days[0].toStops)
    }

    @Test
    fun `plain names are still saved where older app versions read them`() {
        val map = RoutePlans.toMap(RoutePlan(days = listOf(RouteDay(from = houseOfCommons, toStops = listOf(p("Mysuru"))))))
        val savedDay = (map["days"] as List<*>)[0] as Map<*, *>
        assertEquals("House of Commons", savedDay["from"])
        assertEquals(listOf("Mysuru"), savedDay["toStops"])
        assertEquals("House of Commons", map["from"])
    }

    @Test
    fun `a name changed by an older app version drops the stale location`() {
        val map = RoutePlans.toMap(RoutePlan(days = listOf(RouteDay(from = houseOfCommons, toStops = listOf(houseOfCommons)))))
        @Suppress("UNCHECKED_CAST")
        val days = (map["days"] as List<Map<String, Any?>>).map { it.toMutableMap() }
        days[0]["from"] = "Somewhere else" // edited by a version that only knew names
        val loaded = RoutePlans.fromMap(map.toMutableMap().also { it["days"] = days })
        assertEquals(p("Somewhere else"), loaded.days[0].from)
        assertEquals(houseOfCommons, loaded.days[0].toStops[0])
    }

    @Test
    fun `pitstops can be off on one day and on for the others`() {
        val plan = RoutePlan(days = listOf(day1, day("Madikeri", pitstops = false), day("Hassan")))
        val loaded = RoutePlans.fromMap(RoutePlans.toMap(plan))
        assertEquals(listOf(true, false, true), loaded.days.map { it.pitstopsEnabled })
    }

    @Test
    fun `a trip saved with the old trip-wide switch keeps it on every day`() {
        val old = mapOf(
            "days" to listOf(mapOf("from" to "A", "toStops" to listOf("B")), mapOf("from" to "B", "toStops" to listOf("C"))),
            "pitstopsEnabled" to false
        )
        assertEquals(listOf(false, false), RoutePlans.fromMap(old).days.map { it.pitstopsEnabled })
        assertEquals(listOf(true), RoutePlans.fromMap(mapOf("from" to "A")).days.map { it.pitstopsEnabled })
    }

    @Test
    fun `older app versions read day 1's pitstop switch`() {
        val map = RoutePlans.toMap(RoutePlan(days = listOf(day1.copy(pitstopsEnabled = false), day("X"))))
        assertEquals(false, map["pitstopsEnabled"])
    }

    @Test
    fun `a new day starts with pitstops on, whatever the day before had`() {
        val plan = RoutePlans.addDay(RoutePlan(days = listOf(day1.copy(pitstopsEnabled = false))))
        assertTrue(plan.days[1].pitstopsEnabled)
    }

    @Test
    fun `day 1 is also saved in the old single-day fields for older app versions`() {
        val map = RoutePlans.toMap(RoutePlan(days = listOf(day1, day("X"))))
        assertEquals("Bengaluru", map["from"])
        assertEquals(listOf("Mysuru", "Madikeri"), map["toStops"])
    }

    @Test
    fun `a route saved before days existed loads as day 1`() {
        val old = mapOf("from" to "Pune", "toStops" to listOf("Lonavala"), "roundTrip" to true, "breakEvery" to "1")
        val plan = RoutePlans.fromMap(old)
        assertEquals(listOf(day("Pune", "Lonavala", roundTrip = true)), plan.days)
        assertNull(plan.days[0].from.lat)
        assertEquals("1", plan.breakEvery)
    }

    @Test
    fun `missing or broken data loads as an empty day instead of crashing`() {
        assertEquals(RoutePlan(), RoutePlans.fromMap(null))
        assertEquals(listOf(RouteDay()), RoutePlans.fromMap(mapOf("days" to emptyList<Any>())).days)
        assertEquals(listOf(RouteDay()), RoutePlans.fromMap(mapOf("days" to listOf("junk", 3))).days)
        assertEquals(listOf(Place()), RoutePlans.fromMap(mapOf("days" to listOf(mapOf("from" to "A", "toStops" to emptyList<String>())))).days[0].toStops)
        val badPlaces = mapOf("days" to listOf(mapOf("from" to "A", "fromPlace" to "junk", "toStops" to listOf("B"), "toPlaces" to listOf(42))))
        assertEquals(day("A", "B"), RoutePlans.fromMap(badPlaces).days[0])
    }
}
