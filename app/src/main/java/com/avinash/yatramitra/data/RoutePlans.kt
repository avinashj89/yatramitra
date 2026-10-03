package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.BreakUnit
import com.avinash.yatramitra.model.DEFAULT_PITSTOP_CATEGORIES
import com.avinash.yatramitra.model.RouteDay
import com.avinash.yatramitra.model.RoutePlan
import com.avinash.yatramitra.model.RoutePreference

/**
 * The multi-day route: adding and removing days, and converting to and from the map stored in
 * Firestore. Pure Kotlin, covered by RoutePlansTest.
 */
object RoutePlans {

    /** Where a day's drive ends: back at its start for a round trip, otherwise its last "To". */
    fun endpoint(day: RouteDay): String =
        if (day.roundTrip) day.from.trim() else day.toStops.map { it.trim() }.lastOrNull { it.isNotBlank() }.orEmpty()

    /** True once a day has a start and at least one destination. */
    fun hasRoute(day: RouteDay): Boolean = day.from.isNotBlank() && day.toStops.any { it.isNotBlank() }

    /** The day's places in driving order, blanks dropped, ending back at the start on a round trip. */
    fun placesInOrder(day: RouteDay): List<String> {
        val places = (listOf(day.from) + day.toStops).map { it.trim() }.filter { it.isNotBlank() }
        return if (day.roundTrip && places.size >= 2) places + places.first() else places
    }

    /** Adds the next day, starting where the previous day ended so it doesn't have to be retyped. */
    fun addDay(plan: RoutePlan): RoutePlan {
        if (plan.days.size >= RoutePlan.MAX_DAYS) return plan
        return plan.copy(days = plan.days + RouteDay(from = plan.days.lastOrNull()?.let(::endpoint).orEmpty()))
    }

    /** Removes a day; removing the only day clears it instead, so there is always a Day 1. */
    fun removeDay(plan: RoutePlan, index: Int): RoutePlan {
        if (index !in plan.days.indices) return plan
        if (plan.days.size == 1) return plan.copy(days = listOf(RouteDay()))
        return plan.copy(days = plan.days.filterIndexed { i, _ -> i != index })
    }

    fun updateDay(plan: RoutePlan, index: Int, day: RouteDay): RoutePlan {
        if (index !in plan.days.indices) return plan
        return plan.copy(days = plan.days.toMutableList().also { it[index] = day })
    }

    fun toMap(plan: RoutePlan): Map<String, Any?> {
        val first = plan.days.firstOrNull() ?: RouteDay()
        return mapOf(
            "days" to plan.days.map { mapOf("from" to it.from, "toStops" to it.toStops, "roundTrip" to it.roundTrip) },
            // Day 1 is also written in the old single-day fields, so a phone still running an
            // older version of the app keeps showing at least Day 1 instead of an empty route.
            "from" to first.from,
            "toStops" to first.toStops,
            "roundTrip" to first.roundTrip,
            "breakEvery" to plan.breakEvery,
            "breakUnit" to plan.breakUnit.name,
            "routePreference" to plan.routePreference.name,
            "pitstopsEnabled" to plan.pitstopsEnabled,
            "pitstopCategories" to plan.pitstopCategories.toList()
        )
    }

    fun fromMap(map: Map<*, *>?): RoutePlan {
        if (map == null) return RoutePlan()
        val savedDays = (map["days"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(::dayFromMap) }
        // Trips saved before routes had days keep their single route as Day 1.
        val days = savedDays?.takeIf { it.isNotEmpty() } ?: listOf(dayFromMap(map))
        val pitstopCategories = (map["pitstopCategories"] as? List<*>)
            ?.filterIsInstance<String>()?.toSet()
            ?: DEFAULT_PITSTOP_CATEGORIES
        return RoutePlan(
            days = days,
            breakEvery = map["breakEvery"] as? String ?: "",
            breakUnit = runCatching { BreakUnit.valueOf(map["breakUnit"] as? String ?: "") }
                .getOrDefault(BreakUnit.HOURS),
            routePreference = runCatching { RoutePreference.valueOf(map["routePreference"] as? String ?: "") }
                .getOrDefault(RoutePreference.FASTEST),
            pitstopsEnabled = map["pitstopsEnabled"] as? Boolean ?: true,
            pitstopCategories = pitstopCategories
        )
    }

    private fun dayFromMap(map: Map<*, *>): RouteDay = RouteDay(
        from = map["from"] as? String ?: "",
        toStops = (map["toStops"] as? List<*>)?.filterIsInstance<String>()?.ifEmpty { null } ?: listOf(""),
        roundTrip = map["roundTrip"] as? Boolean ?: false
    )
}
