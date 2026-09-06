package com.avinash.yatramitra.model

/** Where an itinerary row's place came from. Anything but MANUAL is auto-populated from the
 *  Route & Stops tab and shown locked (read-only place text) in the Itinerary UI — only its time
 *  and notes stay editable. */
enum class StopSource { MANUAL, ROUTE_START, ROUTE_PITSTOP, ROUTE_END }

/** One row in the Day-by-day itinerary table (matches the reference screenshot). */
data class ItineraryStop(
    val id: String = "",
    val fromTime: String = "",   // e.g. "05:00 AM"
    val tillTime: String = "",   // e.g. "07:30 AM" (blank when it's a single point-in-time stop)
    val place: String = "",      // e.g. "Paakashala Yediyur"
    val notes: String = "",      // e.g. "Breakfast"
    val order: Int = 0,
    val source: StopSource = StopSource.MANUAL
)

/** A single day of the trip, holding its ordered list of stops. */
data class ItineraryDay(
    val id: String = "",
    val label: String = "Day 1",
    val order: Int = 0,
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

/** Whether a trip member created the trip (Organizer) or joined it (Group Member). This gates which
 *  edit affordances the UI shows — same trust model as today, where anyone with the trip code
 *  can already read/write everything in Firestore; the role is a UI convention, not a hard
 *  security boundary. */
enum class MemberRole { ORGANIZER, JOINER }

/** A person on the shared trip (for expense splitting). */
data class Member(
    val id: String = "",
    val name: String = "",
    val role: MemberRole = MemberRole.JOINER,
    /** Optional UPI VPA (e.g. "name@bank") this member added themselves, so others can pay them
     *  directly via a "Pay via UPI" deep link when settling up. Blank if not set. */
    val upiId: String = ""
)

/** One shared expense. If customSplitAmounts is empty, the amount is split equally
 *  among splitAmongMemberIds; otherwise customSplitAmounts gives each member's exact share
 *  and must sum to `amount`. When the split was entered as percentages, splitPercentages holds
 *  the raw percentages (so re-editing shows clean percentages again, not derived decimals) while
 *  customSplitAmounts still holds the computed ₹ shares actually used for balance math. */
data class Expense(
    val id: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val paidByMemberId: String = "",
    val paidByName: String = "",
    val splitAmongMemberIds: List<String> = emptyList(),
    val customSplitAmounts: Map<String, Double> = emptyMap(),
    val splitPercentages: Map<String, Double> = emptyMap(),
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
    val fromMemberId: String,
    val fromName: String,
    val toMemberId: String,
    val toName: String,
    val amount: Double
)

/** Trip-level metadata that lives on the Firestore trip document itself (not a subcollection). */
data class TripMeta(
    val groupName: String = ""
)

enum class SuggestionStatus { PENDING, ACCEPTED, DISMISSED }

/** A joiner's free-text suggestion for a route/stop change. Accepting one just marks it resolved
 *  so the Organizer can go make the actual edit themselves — there's no way to auto-apply a
 *  free-text suggestion to the route. */
data class RouteSuggestion(
    val id: String = "",
    val authorMemberId: String = "",
    val authorName: String = "",
    val text: String = "",
    val status: SuggestionStatus = SuggestionStatus.PENDING,
    val createdAtMillis: Long = 0L
)

/** Same idea as [RouteSuggestion], for itinerary/schedule changes. */
data class ItinerarySuggestion(
    val id: String = "",
    val authorMemberId: String = "",
    val authorName: String = "",
    val text: String = "",
    val status: SuggestionStatus = SuggestionStatus.PENDING,
    val createdAtMillis: Long = 0L
)
