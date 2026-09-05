package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.BreakUnit
import com.avinash.yatramitra.model.Expense
import com.avinash.yatramitra.model.ItineraryDay
import com.avinash.yatramitra.model.ItinerarySuggestion
import com.avinash.yatramitra.model.ItineraryStop
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.model.RoutePlan
import com.avinash.yatramitra.model.RoutePreference
import com.avinash.yatramitra.model.RouteSuggestion
import com.avinash.yatramitra.model.StopSource
import com.avinash.yatramitra.model.SuggestionStatus
import com.avinash.yatramitra.model.TripMeta
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Everything the app needs to talk to Firebase for the shared, live-synced expenses feature.
 *
 * Data shape in Firestore:
 *   trips/{tripCode}                          -> { createdAt, groupName }
 *   trips/{tripCode}/members/{memberId}       -> { name, joinedAt }
 *   trips/{tripCode}/expenses/{expenseId}     -> { description, amount, paidByMemberId, paidByName,
 *                                                  splitAmongMemberIds, customSplitAmounts, createdAt }
 */
object TripRepository {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    /** Signs in anonymously (once) so Firestore security rules can require request.auth != null. */
    suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    /** Creates a brand-new trip with a short, easy-to-read join code, e.g. "7F3K9Q". */
    suspend fun createTrip(groupName: String = ""): String {
        ensureSignedIn()
        val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // no 0/O/1/I to avoid mix-ups
        var code: String? = null
        for (attempt in 1..6) {
            val candidate = (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
            val existing = db.collection("trips").document(candidate).get().await()
            if (!existing.exists()) {
                code = candidate
                break
            }
        }
        // Every candidate collided with an existing trip six times in a row — vanishingly rare
        // with ~1 billion possible codes, but bail out rather than silently overwriting someone
        // else's trip.
        val finalCode = code ?: throw IllegalStateException("Couldn't generate a unique trip code — please try again.")
        db.collection("trips").document(finalCode)
            .set(
                mapOf(
                    "createdAt" to System.currentTimeMillis(),
                    "groupName" to groupName.ifBlank { "Our trip" }
                )
            )
            .await()
        return finalCode
    }

    /** Returns true if a trip with this code exists. */
    suspend fun tripExists(code: String): Boolean {
        ensureSignedIn()
        return db.collection("trips").document(code.uppercase()).get().await().exists()
    }

    /** Adds a member with the given display name and returns their memberId. Used both for
     *  someone joining themselves with the trip code, and for an existing member adding a
     *  travel companion by name directly (e.g. someone who won't install the app). The trip's
     *  creator joins as ORGANIZER; everyone else (code entry, or "Add people") joins as JOINER —
     *  this is a UI convention only, not a Firestore security boundary (see [Member.role]). */
    suspend fun joinTrip(code: String, name: String, role: MemberRole = MemberRole.JOINER): String {
        ensureSignedIn()
        val ref = db.collection("trips").document(code.uppercase())
            .collection("members").document()
        ref.set(
            mapOf(
                "name" to name,
                "joinedAt" to System.currentTimeMillis(),
                "role" to role.name
            )
        ).await()
        return ref.id
    }

    suspend fun updateMemberUpiId(code: String, memberId: String, upiId: String) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .collection("members").document(memberId)
            .update("upiId", upiId.trim())
            .await()
    }

    fun observeMembers(code: String): Flow<List<Member>> = callbackFlow {
        val reg = db.collection("trips").document(code.uppercase())
            .collection("members")
            .orderBy("joinedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val members = snap?.documents?.map {
                    Member(
                        id = it.id,
                        name = it.getString("name") ?: "",
                        role = runCatching { MemberRole.valueOf(it.getString("role") ?: "") }
                            .getOrDefault(MemberRole.JOINER),
                        upiId = it.getString("upiId") ?: ""
                    )
                } ?: emptyList()
                trySend(members)
            }
        awaitClose { reg.remove() }
    }

    /** Live-observes the trip's own metadata document (currently just its group name). */
    fun observeTripMeta(code: String): Flow<TripMeta> = callbackFlow {
        val reg = db.collection("trips").document(code.uppercase())
            .addSnapshotListener { snap, _ ->
                trySend(TripMeta(groupName = snap?.getString("groupName") ?: ""))
            }
        awaitClose { reg.remove() }
    }

    /** Live-observes the trip's shared route plan (Route & Stops tab) — replaces the old
     *  per-phone local-only copy so the Organizer's edits sync to every joiner. */
    fun observeRoutePlan(code: String): Flow<RoutePlan> = callbackFlow {
        val reg = db.collection("trips").document(code.uppercase())
            .addSnapshotListener { snap, _ ->
                trySend(mapToRoutePlan(snap?.get("routePlan") as? Map<*, *>))
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateRoutePlan(code: String, plan: RoutePlan) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .update("routePlan", routePlanToMap(plan))
            .await()
    }

    private fun routePlanToMap(plan: RoutePlan): Map<String, Any?> = mapOf(
        "tripName" to plan.tripName,
        "from" to plan.from,
        "toStops" to plan.toStops,
        "roundTrip" to plan.roundTrip,
        "breakEvery" to plan.breakEvery,
        "breakUnit" to plan.breakUnit.name,
        "routePreference" to plan.routePreference.name
    )

    private fun mapToRoutePlan(map: Map<*, *>?): RoutePlan {
        if (map == null) return RoutePlan()
        val toStops = (map["toStops"] as? List<*>)?.filterIsInstance<String>()?.ifEmpty { listOf("") }
        return RoutePlan(
            tripName = map["tripName"] as? String ?: "",
            from = map["from"] as? String ?: "",
            toStops = toStops ?: listOf(""),
            roundTrip = map["roundTrip"] as? Boolean ?: false,
            breakEvery = map["breakEvery"] as? String ?: "",
            breakUnit = runCatching { BreakUnit.valueOf(map["breakUnit"] as? String ?: "") }
                .getOrDefault(BreakUnit.HOURS),
            routePreference = runCatching { RoutePreference.valueOf(map["routePreference"] as? String ?: "") }
                .getOrDefault(RoutePreference.FASTEST)
        )
    }

    // ---- Itinerary (shared, replaces the old per-phone local-only copy) ----

    fun observeItineraryDays(code: String): Flow<List<ItineraryDay>> = callbackFlow {
        val reg = db.collection("trips").document(code.uppercase())
            .collection("itineraryDays")
            .orderBy("order", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.map { docToItineraryDay(it) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveItineraryDay(code: String, day: ItineraryDay) {
        ensureSignedIn()
        val id = day.id.ifBlank { UUID.randomUUID().toString() }
        db.collection("trips").document(code.uppercase())
            .collection("itineraryDays").document(id)
            .set(itineraryDayToMap(day))
            .await()
    }

    suspend fun deleteItineraryDay(code: String, dayId: String) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .collection("itineraryDays").document(dayId)
            .delete()
            .await()
    }

    /** Regenerates Day 1's auto-populated rows (route start, pitstops, route end) from the
     *  current route plan and freshly-computed pitstops, while preserving any rows the Organizer
     *  added manually themselves — called when "Generate pitstops" is used on the Route tab. */
    suspend fun regenerateDay1FromRoute(code: String, plan: RoutePlan, pitstops: List<RouteRepository.Pitstop>) {
        ensureSignedIn()
        val dayCollection = db.collection("trips").document(code.uppercase()).collection("itineraryDays")
        val existing = dayCollection.whereEqualTo("label", "Day 1").limit(1).get().await().documents.firstOrNull()
        val manualStops = existing?.let { docToItineraryDay(it) }?.stops?.filter { it.source == StopSource.MANUAL } ?: emptyList()

        val validStops = plan.toStops.map { it.trim() }.filter { it.isNotBlank() }
        val destinationName = if (plan.roundTrip) plan.from else validStops.lastOrNull().orEmpty()

        var order = 0
        val autoStops = mutableListOf<ItineraryStop>()
        if (plan.from.isNotBlank()) {
            autoStops.add(ItineraryStop(id = UUID.randomUUID().toString(), place = plan.from, order = order++, source = StopSource.ROUTE_START))
        }
        pitstops.forEach { p ->
            val hours = (p.elapsedMinutes / 60).toInt()
            val minutes = (p.elapsedMinutes % 60).roundToInt()
            val elapsed = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
            autoStops.add(
                ItineraryStop(
                    id = UUID.randomUUID().toString(),
                    place = p.placeName,
                    notes = "Suggested break • ~${p.distanceKm.roundToInt()} km / $elapsed from start",
                    order = order++,
                    source = StopSource.ROUTE_PITSTOP
                )
            )
        }
        if (destinationName.isNotBlank()) {
            autoStops.add(ItineraryStop(id = UUID.randomUUID().toString(), place = destinationName, order = order++, source = StopSource.ROUTE_END))
        }
        val finalStops = autoStops + manualStops.mapIndexed { i, s -> s.copy(order = order + i) }

        val dayId = existing?.id ?: UUID.randomUUID().toString()
        saveItineraryDay(code, ItineraryDay(id = dayId, label = "Day 1", order = 0, stops = finalStops))
    }

    private fun itineraryDayToMap(day: ItineraryDay): Map<String, Any?> = mapOf(
        "label" to day.label,
        "order" to day.order,
        "stops" to day.stops.sortedBy { it.order }.map { stopToMap(it) }
    )

    private fun stopToMap(stop: ItineraryStop): Map<String, Any?> = mapOf(
        "id" to stop.id,
        "fromTime" to stop.fromTime,
        "tillTime" to stop.tillTime,
        "place" to stop.place,
        "notes" to stop.notes,
        "order" to stop.order,
        "source" to stop.source.name
    )

    private fun docToItineraryDay(doc: DocumentSnapshot): ItineraryDay {
        val stops = (doc.get("stops") as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            ?.map { mapToStop(it) }
            ?: emptyList()
        return ItineraryDay(
            id = doc.id,
            label = doc.getString("label") ?: "",
            order = (doc.getLong("order") ?: 0L).toInt(),
            stops = stops
        )
    }

    private fun mapToStop(map: Map<*, *>): ItineraryStop = ItineraryStop(
        id = map["id"] as? String ?: "",
        fromTime = map["fromTime"] as? String ?: "",
        tillTime = map["tillTime"] as? String ?: "",
        place = map["place"] as? String ?: "",
        notes = map["notes"] as? String ?: "",
        order = (map["order"] as? Number)?.toInt() ?: 0,
        source = runCatching { StopSource.valueOf(map["source"] as? String ?: "") }.getOrDefault(StopSource.MANUAL)
    )

    suspend fun updateGroupName(code: String, groupName: String) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .update("groupName", groupName.ifBlank { "Our trip" })
            .await()
    }

    fun observeExpenses(code: String): Flow<List<Expense>> = callbackFlow {
        val reg = db.collection("trips").document(code.uppercase())
            .collection("expenses")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val expenses = snap?.documents?.map { doc ->
                    fun doubleMap(field: String) = (doc.get(field) as? Map<*, *>)
                        ?.entries
                        ?.mapNotNull { (k, v) ->
                            val key = k as? String
                            val value = (v as? Number)?.toDouble()
                            if (key != null && value != null) key to value else null
                        }
                        ?.toMap() ?: emptyMap()
                    Expense(
                        id = doc.id,
                        description = doc.getString("description") ?: "",
                        amount = doc.getDouble("amount") ?: 0.0,
                        paidByMemberId = doc.getString("paidByMemberId") ?: "",
                        paidByName = doc.getString("paidByName") ?: "",
                        splitAmongMemberIds = (doc.get("splitAmongMemberIds") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList(),
                        customSplitAmounts = doubleMap("customSplitAmounts"),
                        splitPercentages = doubleMap("splitPercentages"),
                        createdAtMillis = doc.getLong("createdAt") ?: 0L
                    )
                } ?: emptyList()
                trySend(expenses)
            }
        awaitClose { reg.remove() }
    }

    suspend fun addExpense(code: String, expense: Expense) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .collection("expenses").document()
            .set(
                mapOf(
                    "description" to expense.description,
                    "amount" to expense.amount,
                    "paidByMemberId" to expense.paidByMemberId,
                    "paidByName" to expense.paidByName,
                    "splitAmongMemberIds" to expense.splitAmongMemberIds,
                    "customSplitAmounts" to expense.customSplitAmounts,
                    "splitPercentages" to expense.splitPercentages,
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
    }

    /** Overwrites an existing expense in place (used when editing), keeping its original id
     *  and creation time so it doesn't jump position in the live-ordered list. */
    suspend fun updateExpense(code: String, expense: Expense) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .collection("expenses").document(expense.id)
            .set(
                mapOf(
                    "description" to expense.description,
                    "amount" to expense.amount,
                    "paidByMemberId" to expense.paidByMemberId,
                    "paidByName" to expense.paidByName,
                    "splitAmongMemberIds" to expense.splitAmongMemberIds,
                    "customSplitAmounts" to expense.customSplitAmounts,
                    "splitPercentages" to expense.splitPercentages,
                    "createdAt" to expense.createdAtMillis
                )
            ).await()
    }

    suspend fun deleteExpense(code: String, expenseId: String) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .collection("expenses").document(expenseId)
            .delete().await()
    }

    // ---- Route & itinerary suggestions (joiner -> organizer collaborative flow) ----

    suspend fun addRouteSuggestion(code: String, authorMemberId: String, authorName: String, text: String) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .collection("routeSuggestions").document()
            .set(suggestionFields(authorMemberId, authorName, text))
            .await()
    }

    fun observeRouteSuggestions(code: String): Flow<List<RouteSuggestion>> =
        observeSuggestions(code, "routeSuggestions") { id, authorId, authorName, text, status, createdAt ->
            RouteSuggestion(id, authorId, authorName, text, status, createdAt)
        }

    suspend fun updateRouteSuggestionStatus(code: String, suggestionId: String, status: SuggestionStatus) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .collection("routeSuggestions").document(suggestionId)
            .update("status", status.name)
            .await()
    }

    suspend fun addItinerarySuggestion(code: String, authorMemberId: String, authorName: String, text: String) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .collection("itinerarySuggestions").document()
            .set(suggestionFields(authorMemberId, authorName, text))
            .await()
    }

    fun observeItinerarySuggestions(code: String): Flow<List<ItinerarySuggestion>> =
        observeSuggestions(code, "itinerarySuggestions") { id, authorId, authorName, text, status, createdAt ->
            ItinerarySuggestion(id, authorId, authorName, text, status, createdAt)
        }

    suspend fun updateItinerarySuggestionStatus(code: String, suggestionId: String, status: SuggestionStatus) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .collection("itinerarySuggestions").document(suggestionId)
            .update("status", status.name)
            .await()
    }

    private fun suggestionFields(authorMemberId: String, authorName: String, text: String) = mapOf(
        "authorMemberId" to authorMemberId,
        "authorName" to authorName,
        "text" to text,
        "status" to SuggestionStatus.PENDING.name,
        "createdAt" to System.currentTimeMillis()
    )

    private fun <T> observeSuggestions(
        code: String,
        subcollection: String,
        build: (id: String, authorId: String, authorName: String, text: String, status: SuggestionStatus, createdAt: Long) -> T
    ): Flow<List<T>> = callbackFlow {
        val reg = db.collection("trips").document(code.uppercase())
            .collection(subcollection)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val items = snap?.documents?.map { doc ->
                    build(
                        doc.id,
                        doc.getString("authorMemberId") ?: "",
                        doc.getString("authorName") ?: "",
                        doc.getString("text") ?: "",
                        runCatching { SuggestionStatus.valueOf(doc.getString("status") ?: "") }
                            .getOrDefault(SuggestionStatus.PENDING),
                        doc.getLong("createdAt") ?: 0L
                    )
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { reg.remove() }
    }
}
