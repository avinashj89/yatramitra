package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.BreakUnit
import com.avinash.yatramitra.model.DEFAULT_PITSTOP_CATEGORIES
import com.avinash.yatramitra.model.Place
import com.avinash.yatramitra.model.RouteDay
import com.avinash.yatramitra.model.RoutePlan
import com.avinash.yatramitra.model.RoutePreference

/**
 * The multi-day route: adding and removing days, and converting to and from the map stored in
 * Firestore. Pure Kotlin, covered by RoutePlansTest.
 */
object RoutePlans {

    private fun Place.isSet() = name.isNotBlank()

    /** Where a day's drive ends: back at its start for a round trip, otherwise its last "To". */
    fun endpoint(day: RouteDay): Place =
        if (day.roundTrip) day.from.trimmed() else day.toStops.map { it.trimmed() }.lastOrNull { it.isSet() } ?: Place()

    /** True once a day has a start and at least one destination. */
    fun hasRoute(day: RouteDay): Boolean = day.from.isSet() && day.toStops.any { it.isSet() }

    /** The day's places in driving order, blanks dropped, ending back at the start on a round trip. */
    fun placesInOrder(day: RouteDay): List<Place> {
        val places = (listOf(day.from) + day.toStops).map { it.trimmed() }.filter { it.isSet() }
        return if (day.roundTrip && places.size >= 2) places + places.first() else places
    }

    /** Adds the next day, starting where the previous day ended (with its exact location, if
     *  known) so it doesn't have to be retyped. */
    fun addDay(plan: RoutePlan): RoutePlan {
        if (plan.days.size >= RoutePlan.MAX_DAYS) return plan
        return plan.copy(days = plan.days + RouteDay(from = plan.days.lastOrNull()?.let(::endpoint) ?: Place()))
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
            "days" to plan.days.map {
                mapOf(
                    // Plain names stay where older app versions (and the admin panel) read them;
                    // the full places sit next to them.
                    "from" to it.from.name,
                    "toStops" to it.toStops.map { stop -> stop.name },
                    "fromPlace" to PlaceRules.toMap(it.from),
                    "toPlaces" to it.toStops.map(PlaceRules::toMap),
                    "roundTrip" to it.roundTrip,
                    "pitstopsEnabled" to it.pitstopsEnabled
                )
            },
            // Day 1 is also written in the old single-day fields, so a phone still running an
            // older version of the app keeps showing at least Day 1 instead of an empty route.
            "from" to first.from.name,
            "toStops" to first.toStops.map { it.name },
            "roundTrip" to first.roundTrip,
            "breakEvery" to plan.breakEvery,
            "breakUnit" to plan.breakUnit.name,
            "routePreference" to plan.routePreference.name,
            // Older app versions had one trip-wide switch; they get Day 1's.
            "pitstopsEnabled" to first.pitstopsEnabled,
            "pitstopCategories" to plan.pitstopCategories.toList()
        )
    }

    fun fromMap(map: Map<*, *>?): RoutePlan {
        if (map == null) return RoutePlan()
        // Before pitstops could be switched per day there was one trip-wide switch: days saved
        // then (which have no switch of their own) take that value.
        val tripWidePitstops = map["pitstopsEnabled"] as? Boolean ?: true
        val savedDays = (map["days"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let { d -> dayFromMap(d, tripWidePitstops) } }
        // Trips saved before routes had days keep their single route as Day 1.
        val days = savedDays?.takeIf { it.isNotEmpty() } ?: listOf(dayFromMap(map, tripWidePitstops))
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
            pitstopCategories = pitstopCategories
        )
    }

    private fun dayFromMap(map: Map<*, *>, defaultPitstops: Boolean): RouteDay {
        val fromName = map["from"] as? String ?: ""
        val stopNames = (map["toStops"] as? List<*>)?.filterIsInstance<String>()?.ifEmpty { null } ?: listOf("")
        val savedStops = map["toPlaces"] as? List<*>
        return RouteDay(
            from = PlaceRules.fromMap(map["fromPlace"], fromName),
            toStops = stopNames.mapIndexed { i, name -> PlaceRules.fromMap(savedStops?.getOrNull(i), name) },
            roundTrip = map["roundTrip"] as? Boolean ?: false,
            pitstopsEnabled = map["pitstopsEnabled"] as? Boolean ?: defaultPitstops
        )
    }

    private fun Place.trimmed(): Place = if (name == name.trim()) this else copy(name = name.trim())
}
