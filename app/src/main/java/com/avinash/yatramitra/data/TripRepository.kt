package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.Expense
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.TripMeta
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
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
        var code: String
        var tries = 0
        while (true) {
            code = (1..6).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
            val existing = db.collection("trips").document(code).get().await()
            if (!existing.exists() || tries > 5) break
            tries++
        }
        db.collection("trips").document(code)
            .set(
                mapOf(
                    "createdAt" to System.currentTimeMillis(),
                    "groupName" to groupName.ifBlank { "Our trip" }
                )
            )
            .await()
        return code
    }

    /** Returns true if a trip with this code exists. */
    suspend fun tripExists(code: String): Boolean {
        ensureSignedIn()
        return db.collection("trips").document(code.uppercase()).get().await().exists()
    }

    /** Adds a member with the given display name and returns their memberId. Used both for
     *  someone joining themselves with the trip code, and for an existing member adding a
     *  travel companion by name directly (e.g. someone who won't install the app). */
    suspend fun joinTrip(code: String, name: String): String {
        ensureSignedIn()
        val ref = db.collection("trips").document(code.uppercase())
            .collection("members").document()
        ref.set(mapOf("name" to name, "joinedAt" to System.currentTimeMillis())).await()
        return ref.id
    }

    fun observeMembers(code: String): Flow<List<Member>> = callbackFlow {
        val reg = db.collection("trips").document(code.uppercase())
            .collection("members")
            .orderBy("joinedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val members = snap?.documents?.map {
                    Member(id = it.id, name = it.getString("name") ?: "")
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
                    val customAmounts = (doc.get("customSplitAmounts") as? Map<*, *>)
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
                        customSplitAmounts = customAmounts,
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
                    "createdAt" to System.currentTimeMillis()
                )
            ).await()
    }

    suspend fun deleteExpense(code: String, expenseId: String) {
        ensureSignedIn()
        db.collection("trips").document(code.uppercase())
            .collection("expenses").document(expenseId)
            .delete().await()
    }
}
