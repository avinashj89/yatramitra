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

/** The set of pitstop-category chips shown by default, before an Organizer has touched them — an
 *  arbitrary but reasonable starting point, not a meaningful "recommended" set. */
val DEFAULT_PITSTOP_CATEGORIES = setOf("Temples & Spiritual", "Dhabas & Highway Food", "Heritage & Forts")

/** Where a route place came from: typed by hand (name only), picked from a search, or the phone's
 *  own position. */
enum class PlaceSource { TYPED, OPENSTREETMAP, GOOGLE, CURRENT_LOCATION }

/**
 * A From/To place. Saving the exact location (not just a name) is what lets "House of Commons,
 * Jayanagar 5th Block" or "my current location" survive into distances, pitstops and the Google
 * Maps hand-off; before, only the name was kept and was searched again later, which only ever
 * found the surrounding area.
 */
data class Place(
    val name: String = "",
    /** The rest of the address, shown under the name. */
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    /** Google's ID for the place, when it was picked from a Google search. */
    val placeId: String? = null,
    val source: PlaceSource = PlaceSource.TYPED,
    /** When [lat]/[lng] were looked up (Google lets apps keep its coordinates for 30 days). */
    val locatedAtMillis: Long = 0L
)

/** One row of the From/To search dropdown. Google results have no coordinates until picked. */
data class PlaceSearchResult(
    val title: String,
    val subtitle: String,
    val source: PlaceSource,
    val lat: Double? = null,
    val lng: Double? = null,
    val placeId: String? = null
)

/** One day's drive on the Route tab: where that day starts, up to 4 "To" stops in order,
 *  whether that day ends back where it started, and whether pitstops are suggested on that day
 *  (a sightseeing day may want none while the long drive days do). */
data class RouteDay(
    val from: Place = Place(),
    val toStops: List<Place> = listOf(Place()), // 1 to 4 entries
    val roundTrip: Boolean = false,
    val pitstopsEnabled: Boolean = true
)

/** The route-planning form: one [RouteDay] per day of the trip (Day 1, Day 2, ...), plus the
 *  break settings shared by every day. The trip's display name lives only on
 *  [TripMeta.groupName]. */
data class RoutePlan(
    val days: List<RouteDay> = listOf(RouteDay()), // never empty
    val breakEvery: String = "",       // free-typed number, kept as String for easy text-field binding
    val breakUnit: BreakUnit = BreakUnit.HOURS,
    val routePreference: RoutePreference = RoutePreference.FASTEST,
    /** Which kinds of places (temples, food, forts, fuel, viewpoints...) the pitstop search should
     *  actually look for — previously collected in the UI but never sent anywhere, so changing
     *  these had no effect on the generated stops. */
    val pitstopCategories: Set<String> = DEFAULT_PITSTOP_CATEGORIES
) {
    companion object {
        const val MAX_TO_STOPS = 4
        const val MAX_DAYS = 15
    }
}

/** A single free-text place suggestion from the (free) OpenStreetMap search. */
data class PlaceSuggestion(
    val displayName: String,
    val lat: Double,
    val lon: Double
)

/** A registered YatraMitra account's public-ish contact info, stored at `users/{uid}` (written
 *  once per sign-in via [com.avinash.yatramitra.data.TripRepository.upsertUserProfile]) so that
 *  adding a travel companion by phone/email can look them up: if they already have an account,
 *  the trip is linked to them directly and shows up on their own homepage automatically, instead
 *  of only ever being a name-only contact entry they'd never otherwise learn about. */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = ""
)

/** Whether a trip member created the trip (Organizer) or joined it (Group Member). This gates which
 *  edit affordances the UI shows — same trust model as today, where anyone with the trip code
 *  can already read/write everything in Firestore; the role is a UI convention, not a hard
 *  security boundary. */
enum class MemberRole { ORGANIZER, JOINER }

/** A person on the shared trip (for expense splitting). [uid] is set only when this member is a
 *  registered YatraMitra account holder who joined themselves (so their trips show up on their own
 *  homepage); it's null for a contact-only companion someone else added by name — they never need
 *  to install the app or have an account. */
data class Member(
    val id: String = "",
    val name: String = "",
    val role: MemberRole = MemberRole.JOINER,
    val phone: String = "",
    val email: String = "",
    val uid: String? = null,
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

enum class TripStatus { ONGOING, COMPLETED }

/** Trip-level metadata that lives on the Firestore trip document itself (not a subcollection). */
data class TripMeta(
    val groupName: String = "",
    val status: TripStatus = TripStatus.ONGOING,
    /** False once the server confirms the trip document is gone (someone deleted the trip). */
    val exists: Boolean = true,
    /** When the Organizer tapped "Start trip" (0 = not started yet), and their name. */
    val startedAtMillis: Long = 0L,
    val startedBy: String = ""
)

/** One message in a trip's Group chat. Posted straight to everyone; nobody approves it. */
data class ChatMessage(
    val id: String = "",
    val authorMemberId: String = "",
    val authorName: String = "",
    val text: String = "",
    val createdAtMillis: Long = 0L
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

/** One entry in a signed-in user's own "my trips" index (stored at `users/{uid}/trips/{tripCode}`),
 *  so the homepage can list their recent trips without a broader, harder-to-secure query across
 *  every trip in the database. [memberId] is that user's own member id within that trip, so opening
 *  it from the homepage doesn't need a separate lookup. */
data class TripSummary(
    val tripCode: String = "",
    val tripName: String = "",
    val memberId: String = "",
    val role: MemberRole = MemberRole.JOINER,
    val lastAccessedAtMillis: Long = 0L,
    val status: TripStatus = TripStatus.ONGOING
)
