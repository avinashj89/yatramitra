package com.avinash.yatramitra.model

/** One row in the Day-by-day itinerary table (matches the reference screenshot). */
data class ItineraryStop(
    val id: String = "",
    val fromTime: String = "",   // e.g. "05:00 AM"
    val tillTime: String = "",   // e.g. "07:30 AM" (blank when it's a single point-in-time stop)
    val place: String = "",      // e.g. "Paakashala Yediyur"
    val notes: String = "",      // e.g. "Breakfast"
    val order: Int = 0
)

/** A single day of the trip, holding its ordered list of stops. */
data class ItineraryDay(
    val id: String = "",
    val label: String = "Day 1",
    val stops: List<ItineraryStop> = emptyList()
)

enum class BreakUnit { KM, HOURS }
enum class RoutePreference { FASTEST, ALTERNATE }

/** The From/To route-planning form. */
data class RoutePlan(
    val from: String = "",
    val to: String = "",
    val roundTrip: Boolean = false,
    val breakEvery: String = "",       // free-typed number, kept as String for easy text-field binding
    val breakUnit: BreakUnit = BreakUnit.HOURS,
    val routePreference: RoutePreference = RoutePreference.FASTEST
)

/** A person on the shared trip (for expense splitting). */
data class Member(
    val id: String = "",
    val name: String = ""
)

/** One shared expense, split equally among a chosen subset of members. */
data class Expense(
    val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val paidByMemberId: String = "",
    val paidByName: String = "",
    val splitAmongMemberIds: List<String> = emptyList(),
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
