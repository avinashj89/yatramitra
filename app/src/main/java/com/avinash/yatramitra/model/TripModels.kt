package com.avinash.yatramitra.model

/** One row in the Day-by-day itinerary table (matches the reference screenshot). */
data class ItineraryStop(
    val id: String = "",
    val fromTime: String = "",   // e.g. "05:00 AM"
    val tillTime: String = "",   // e.g. "07:30 AM" (blank when it's a single point-in-time stop)
    val place: String = "",      // e.g. "Paakashala Yediyur"
    val notes: String = "",      // e.g. "Breakfast"
    val order: Int = 0,
    val isSuggested: Boolean = false // true when auto-added by the pitstop finder, so it's easy to spot/edit
)

/** A single day of the trip, holding its ordered list of stops. */
data class ItineraryDay(
    val id: String = "",
    val label: String = "Day 1",
    val stops: List<ItineraryStop> = emptyList()
)

enum class BreakUnit { KM, HOURS }
enum class RoutePreference { FASTEST, SURPRISE }

/** The From/To route-planning form. Supports up to 4 "To" stops, in order. */
data class RoutePlan(
    val tripName: String = "",
    val from: String = "",
    val toStops: List<String> = listOf(""), // 1 to 4 entries
    val roundTrip: Boolean = false,
    val breakEvery: String = "",       // free-typed number, kept as String for easy text-field binding
    val breakUnit: BreakUnit = BreakUnit.HOURS,
    val routePreference: RoutePreference = RoutePreference.FASTEST
) {
    companion object {
        const val MAX_TO_STOPS = 4
    }
}

/** A single free-text place suggestion from the (free) OpenStreetMap search. */
data class PlaceSuggestion(
    val displayName: String,
    val lat: Double,
    val lon: Double
)

/** A person on the shared trip (for expense splitting). */
data class Member(
    val id: String = "",
    val name: String = ""
)

/** One shared expense. If customSplitAmounts is empty, the amount is split equally
 *  among splitAmongMemberIds; otherwise customSplitAmounts gives each member's exact share
 *  and must sum to `amount`. */
data class Expense(
    val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val paidByMemberId: String = "",
    val paidByName: String = "",
    val splitAmongMemberIds: List<String> = emptyList(),
    val customSplitAmounts: Map<String, Double> = emptyMap(),
    val createdAtMillis: Long = 0L
)

/** Net balance for one member: positive means the group owes them, negative means they owe the group. */
data class Balance(
    val memberId: String,
    val name: String,
    val net: Double
)

/** A single "X pays Y ₹amount" settle-up suggestion. */
data class Settlement(
    val fromName: String,
    val toName: String,
    val amount: Double
)

/** Trip-level metadata that lives on the Firestore trip document itself (not a subcollection). */
data class TripMeta(
    val groupName: String = ""
)
