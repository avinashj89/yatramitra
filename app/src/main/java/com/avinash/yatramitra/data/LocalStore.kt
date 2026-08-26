package com.avinash.yatramitra.data

import android.content.Context
import com.avinash.yatramitra.model.BreakUnit
import com.avinash.yatramitra.model.ItineraryDay
import com.avinash.yatramitra.model.ItineraryStop
import com.avinash.yatramitra.model.RoutePlan
import com.avinash.yatramitra.model.RoutePreference
import org.json.JSONArray
import org.json.JSONObject

/**
 * Small on-device persistence layer (SharedPreferences + JSON) so:
 *  - the route plan and itinerary you build survive closing the app, and
 *  - once you've joined/created a trip's expense group, you stay in it next time you open the app.
 * No extra libraries needed, and none of this ever leaves the phone except the expenses themselves,
 * which are written to Firestore by TripRepository.
 */
object LocalStore {

    private const val PREFS = "yatramitra_prefs"
    private const val KEY_ITINERARY = "itinerary_days"
    private const val KEY_ROUTE_PLAN = "route_plan"
    private const val KEY_TRIP_CODE = "trip_code"
    private const val KEY_MEMBER_ID = "member_id"
    private const val KEY_MEMBER_NAME = "member_name"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Route plan (Planner tab) ----

    fun saveRoutePlan(context: Context, plan: RoutePlan) {
        val obj = JSONObject()
        obj.put("tripName", plan.tripName)
        obj.put("from", plan.from)
        val stopsArr = JSONArray()
        plan.toStops.forEach { stopsArr.put(it) }
        obj.put("toStops", stopsArr)
        obj.put("roundTrip", plan.roundTrip)
        obj.put("breakEvery", plan.breakEvery)
        obj.put("breakUnit", plan.breakUnit.name)
        obj.put("routePreference", plan.routePreference.name)
        prefs(context).edit().putString(KEY_ROUTE_PLAN, obj.toString()).apply()
    }

    fun loadRoutePlan(context: Context): RoutePlan {
        val raw = prefs(context).getString(KEY_ROUTE_PLAN, null) ?: return RoutePlan()
        return try {
            val obj = JSONObject(raw)
            val stopsArr = obj.optJSONArray("toStops")
            val stops = if (stopsArr != null) {
                (0 until stopsArr.length()).map { stopsArr.optString(it) }
            } else {
                listOf("")
            }.ifEmpty { listOf("") }
            RoutePlan(
                tripName = obj.optString("tripName"),
                from = obj.optString("from"),
                toStops = stops,
                roundTrip = obj.optBoolean("roundTrip", false),
                breakEvery = obj.optString("breakEvery"),
                breakUnit = runCatching { BreakUnit.valueOf(obj.optString("breakUnit")) }.getOrDefault(BreakUnit.HOURS),
                routePreference = runCatching { RoutePreference.valueOf(obj.optString("routePreference")) }.getOrDefault(RoutePreference.FASTEST)
            )
        } catch (e: Exception) {
            RoutePlan()
        }
    }

    // ---- Itinerary ----

    fun saveItinerary(context: Context, days: List<ItineraryDay>) {
        val arr = JSONArray()
        days.forEach { day ->
            val dayObj = JSONObject()
            dayObj.put("id", day.id)
            dayObj.put("label", day.label)
            val stopsArr = JSONArray()
            day.stops.forEach { stop ->
                val stopObj = JSONObject()
                stopObj.put("id", stop.id)
                stopObj.put("fromTime", stop.fromTime)
                stopObj.put("tillTime", stop.tillTime)
                stopObj.put("place", stop.place)
                stopObj.put("notes", stop.notes)
                stopObj.put("order", stop.order)
                stopObj.put("isSuggested", stop.isSuggested)
                stopsArr.put(stopObj)
            }
            dayObj.put("stops", stopsArr)
            arr.put(dayObj)
        }
        prefs(context).edit().putString(KEY_ITINERARY, arr.toString()).apply()
    }

    fun loadItinerary(context: Context): List<ItineraryDay> {
        val raw = prefs(context).getString(KEY_ITINERARY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val dayObj = arr.getJSONObject(i)
                val stopsArr = dayObj.getJSONArray("stops")
                val stops = (0 until stopsArr.length()).map { j ->
                    val s = stopsArr.getJSONObject(j)
                    ItineraryStop(
                        id = s.getString("id"),
                        fromTime = s.optString("fromTime"),
                        tillTime = s.optString("tillTime"),
                        place = s.optString("place"),
                        notes = s.optString("notes"),
                        order = s.optInt("order"),
                        isSuggested = s.optBoolean("isSuggested", false)
                    )
                }
                ItineraryDay(
                    id = dayObj.getString("id"),
                    label = dayObj.getString("label"),
                    stops = stops
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---- Trip session (which shared expense group this phone is currently in) ----

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
