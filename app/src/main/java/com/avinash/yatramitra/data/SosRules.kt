package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.DayHospitals
import com.avinash.yatramitra.model.Hospital
import com.avinash.yatramitra.model.SosAlert
import com.avinash.yatramitra.model.SosType

/**
 * Pure rules for SOS messages: wording, what's offered after sending, the nearest hospitals,
 * and how an SOS is stored on a chat message. Covered by SosRulesTest.
 */
object SosRules {

    data class Info(val label: String, val help: String)

    fun info(type: SosType): Info = when (type) {
        SosType.ACCIDENT -> Info("Accident", "Someone is hurt or a vehicle crashed")
        SosType.MEDICAL -> Info("Medical help", "Someone is unwell and needs care")
        SosType.BREAKDOWN -> Info("Breakdown", "Vehicle stopped, flat tyre or won't start")
        SosType.POLICE -> Info("Police help", "Theft, a threat or trouble on the road")
        SosType.FUEL -> Info("Need fuel", "Running low on fuel or charge")
        SosType.ATM -> Info("Need cash", "Need an ATM or cash nearby")
    }

    /** The chat text, also shown by older app versions and in notifications. */
    fun messageText(type: SosType, authorName: String, locationName: String?): String {
        val who = authorName.ifBlank { "Someone" }
        val where = locationName?.trim()?.takeIf { it.isNotEmpty() }?.let { " Location: $it." } ?: " Location not available."
        return "SOS: ${info(type).label}. $who needs help.$where"
    }

    /** One thing to offer right after an SOS is sent. */
    sealed interface Action {
        val label: String
        data class Dial(val number: String, override val label: String) : Action
        data class SearchMaps(val query: String, override val label: String) : Action
    }

    /** India's numbers: 112 all emergencies, 108 ambulance, 100 police, 1033 national highway
     *  helpline (breakdowns and accidents on national highways). */
    fun actions(type: SosType): List<Action> = when (type) {
        SosType.ACCIDENT -> listOf(Action.Dial("108", "Call 108 Ambulance"), Action.Dial("112", "Call 112 Emergency"), Action.Dial("1033", "Call 1033 Highway helpline"))
        SosType.MEDICAL -> listOf(Action.Dial("108", "Call 108 Ambulance"), Action.Dial("112", "Call 112 Emergency"), Action.SearchMaps("pharmacy", "Find a pharmacy nearby"))
        SosType.BREAKDOWN -> listOf(Action.SearchMaps("car mechanic", "Find a mechanic nearby"), Action.Dial("1033", "Call 1033 Highway helpline"), Action.SearchMaps("towing service", "Find towing nearby"))
        SosType.POLICE -> listOf(Action.Dial("112", "Call 112 Emergency"), Action.Dial("100", "Call 100 Police"), Action.SearchMaps("police station", "Find a police station"))
        SosType.FUEL -> listOf(Action.SearchMaps("petrol pump", "Find petrol pumps nearby"), Action.SearchMaps("EV charging station", "Find EV charging nearby"))
        SosType.ATM -> listOf(Action.SearchMaps("ATM", "Find ATMs nearby"), Action.SearchMaps("bank", "Find banks nearby"))
    }

    /** Whether to show the nearest hospitals after sending. */
    fun showsHospitals(type: SosType): Boolean = type == SosType.ACCIDENT || type == SosType.MEDICAL

    /** The saved hospitals (all days) closest to where the SOS was sent, nearest first, with km. */
    fun nearestHospitals(saved: Collection<DayHospitals>, lat: Double, lng: Double, count: Int = 3): List<Pair<Hospital, Double>> {
        val here = RouteRepository.RoutePoint(lat, lng)
        return saved.flatMap { it.hospitals }
            .distinctBy { it.id.ifBlank { "${it.name}@${it.lat},${it.lng}" } }
            .map { it to RouteRepository.haversineKm(here, RouteRepository.RoutePoint(it.lat, it.lng)) }
            .sortedBy { it.second }
            .take(count)
    }

    fun toFields(alert: SosAlert): Map<String, Any?> = mapOf(
        "kind" to "sos",
        "sosType" to alert.type.name,
        "lat" to alert.lat,
        "lng" to alert.lng,
        "locationName" to alert.locationName
    )

    /** Reads an SOS off a chat message's fields; null for an ordinary message. */
    fun fromFields(fields: Map<String, Any?>): SosAlert? {
        if (fields["kind"] != "sos") return null
        val type = runCatching { SosType.valueOf(fields["sosType"] as? String ?: "") }.getOrNull() ?: return null
        return SosAlert(
            type = type,
            lat = (fields["lat"] as? Number)?.toDouble(),
            lng = (fields["lng"] as? Number)?.toDouble(),
            locationName = fields["locationName"] as? String ?: ""
        )
    }
}
