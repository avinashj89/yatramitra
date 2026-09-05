package com.avinash.yatramitra.data

import android.content.Context

/**
 * On-device persistence for the one thing that has to survive closing the app and can't live in
 * Firestore: which trip this phone is currently in. The route plan, itinerary, and expenses are
 * all shared/live-synced via TripRepository once you're in a trip.
 */
object LocalStore {

    private const val PREFS = "yatramitra_prefs"
    private const val KEY_TRIP_CODE = "trip_code"
    private const val KEY_MEMBER_ID = "member_id"
    private const val KEY_MEMBER_NAME = "member_name"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Trip session (which shared trip this phone is currently in) ----

    fun saveSession(context: Context, tripCode: String, memberId: String, memberName: String) {
        prefs(context).edit()
            .putString(KEY_TRIP_CODE, tripCode)
            .putString(KEY_MEMBER_ID, memberId)
            .putString(KEY_MEMBER_NAME, memberName)
            .apply()
    }

    data class Session(val tripCode: String, val memberId: String, val memberName: String)

    fun loadSession(context: Context): Session? {
        val p = prefs(context)
        val code = p.getString(KEY_TRIP_CODE, null) ?: return null
        val memberId = p.getString(KEY_MEMBER_ID, null) ?: return null
        val name = p.getString(KEY_MEMBER_NAME, null) ?: return null
        return Session(code, memberId, name)
    }

    fun clearSession(context: Context) {
        prefs(context).edit()
            .remove(KEY_TRIP_CODE)
            .remove(KEY_MEMBER_ID)
            .remove(KEY_MEMBER_NAME)
            .apply()
    }
}
