package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.BreakUnit
import com.avinash.yatramitra.model.RouteDay
import com.avinash.yatramitra.model.RoutePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlansTest {

    private val day1 = RouteDay(from = "Bengaluru", toStops = listOf("Mysuru", "Madikeri"))

    @Test
    fun `a new trip starts with exactly one empty day`() {
        assertEquals(1, RoutePlan().days.size)
        assertEquals(RouteDay(), RoutePlan().days.first())
    }

    @Test
    fun `a new day starts where the previous day ended`() {
        val plan = RoutePlans.addDay(RoutePlan(days = listOf(day1)))
        assertEquals(2, plan.days.size)
        assertEquals("Madikeri", plan.days[1].from)
        assertEquals(listOf(""), plan.days[1].toStops)
    }

    @Test
    fun `after a round trip day the next day starts back at the start`() {
        val plan = RoutePlans.addDay(RoutePlan(days = listOf(day1.copy(roundTrip = true))))
        assertEquals("Bengaluru", plan.days[1].from)
    }

    @Test
    fun `a new day after an unfinished day starts blank`() {
        val plan = RoutePlans.addDay(RoutePlan(days = listOf(RouteDay(from = "Bengaluru"))))
        assertEquals("", plan.days[1].from)
    }

    @Test
    fun `days stop at the maximum`() {
        var plan = RoutePlan()
        repeat(RoutePlan.MAX_DAYS + 3) { plan = RoutePlans.addDay(plan) }
        assertEquals(RoutePlan.MAX_DAYS, plan.days.size)
    }

    @Test
    fun `removing a middle day moves later days up`() {
        val plan = RoutePlan(days = listOf(RouteDay(from = "A"), RouteDay(from = "B"), RouteDay(from = "C")))
        assertEquals(listOf("A", "C"), RoutePlans.removeDay(plan, 1).days.map { it.from })
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
        assertEquals(plan, RoutePlans.updateDay(plan, 5, RouteDay(from = "X")))
    }

    @Test
    fun `updating one day leaves the others alone`() {
        val plan = RoutePlan(days = listOf(RouteDay(from = "A"), RouteDay(from = "B")))
        val updated = RoutePlans.updateDay(plan, 1, RouteDay(from = "Z"))
        assertEquals(listOf("A", "Z"), updated.days.map { it.from })
    }

    @Test
    fun `places are in driving order without blanks, back to the start on a round trip`() {
        val day = RouteDay(from = " Bengaluru ", toStops = listOf("Mysuru", " ", "Madikeri"))
        assertEquals(listOf("Bengaluru", "Mysuru", "Madikeri"), RoutePlans.placesInOrder(day))
        assertEquals(
            listOf("Bengaluru", "Mysuru", "Madikeri", "Bengaluru"),
            RoutePlans.placesInOrder(day.copy(roundTrip = true))
        )
    }

    @Test
    fun `a day needs a start and a destination to count as a route`() {
        assertTrue(RoutePlans.hasRoute(day1))
        assertFalse(RoutePlans.hasRoute(RouteDay(from = "Bengaluru")))
        assertFalse(RoutePlans.hasRoute(RouteDay(toStops = listOf("Mysuru"))))
    }

    @Test
    fun `saving and loading keeps every day and setting`() {
        val plan = RoutePlan(
            days = listOf(day1, RouteDay(from = "Madikeri", toStops = listOf("Bengaluru"), roundTrip = true, pitstopsEnabled = false)),
            breakEvery = "2",
            breakUnit = BreakUnit.KM,
            pitstopCategories = setOf("Scenic Viewpoints")
        )
        assertEquals(plan, RoutePlans.fromMap(RoutePlans.toMap(plan)))
    }

    @Test
    fun `pitstops can be off on one day and on for the others`() {
        val plan = RoutePlan(days = listOf(day1, RouteDay(from = "Madikeri", pitstopsEnabled = false), RouteDay(from = "Hassan")))
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
        val map = RoutePlans.toMap(RoutePlan(days = listOf(day1.copy(pitstopsEnabled = false), RouteDay(from = "X"))))
        assertEquals(false, map["pitstopsEnabled"])
    }

    @Test
    fun `a new day starts with pitstops on, whatever the day before had`() {
        val plan = RoutePlans.addDay(RoutePlan(days = listOf(day1.copy(pitstopsEnabled = false))))
        assertTrue(plan.days[1].pitstopsEnabled)
    }

    @Test
    fun `day 1 is also saved in the old single-day fields for older app versions`() {
        val map = RoutePlans.toMap(RoutePlan(days = listOf(day1, RouteDay(from = "X"))))
        assertEquals("Bengaluru", map["from"])
        assertEquals(listOf("Mysuru", "Madikeri"), map["toStops"])
    }

    @Test
    fun `a route saved before days existed loads as day 1`() {
        val old = mapOf("from" to "Pune", "toStops" to listOf("Lonavala"), "roundTrip" to true, "breakEvery" to "1")
        val plan = RoutePlans.fromMap(old)
        assertEquals(listOf(RouteDay(from = "Pune", toStops = listOf("Lonavala"), roundTrip = true)), plan.days)
        assertEquals("1", plan.breakEvery)
    }

    @Test
    fun `missing or broken data loads as an empty day instead of crashing`() {
        assertEquals(RoutePlan(), RoutePlans.fromMap(null))
        assertEquals(listOf(RouteDay()), RoutePlans.fromMap(mapOf("days" to emptyList<Any>())).days)
        assertEquals(listOf(RouteDay()), RoutePlans.fromMap(mapOf("days" to listOf("junk", 3))).days)
        assertEquals(listOf(""), RoutePlans.fromMap(mapOf("days" to listOf(mapOf("from" to "A", "toStops" to emptyList<String>())))).days[0].toStops)
    }
}
