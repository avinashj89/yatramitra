package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.Member
import java.net.URLEncoder

/**
 * Builds the "the trip has started" message and its recipients. Sending is handed to the phone's
 * own SMS / email / WhatsApp apps (no server, no cost), which also reaches companions who were
 * added by phone or email and never installed YatraMitra. Pure Kotlin, covered by TripNotifyTest.
 */
object TripNotify {

    data class Recipients(
        val phones: List<String>,
        val emails: List<String>,
        /** Names of people with neither a phone number nor an email on file. */
        val unreachable: List<String>
    )

    /** Everyone on the trip except [myMemberId], with duplicate numbers and addresses removed. */
    fun recipients(members: List<Member>, myMemberId: String): Recipients {
        val others = members.filter { it.id != myMemberId }
        val phones = others.mapNotNull { cleanPhone(it.phone) }.distinct()
        val emails = others.mapNotNull { cleanEmail(it.email) }.distinct()
        val unreachable = others.filter { cleanPhone(it.phone) == null && cleanEmail(it.email) == null }.map { it.name }
        return Recipients(phones, emails, unreachable)
    }

    fun startedMessage(tripName: String, tripCode: String): String =
        "Our trip \"${tripName.ifBlank { "Our trip" }}\" has started! Open YatraMitra to see the route, " +
            "itinerary and expenses: yatramitra://join?code=$tripCode\n" +
            "Or open the app, tap \"Have a trip code?\" and enter: $tripCode"

    /** An `smsto:` target that opens one message to every number. */
    fun smsTarget(phones: List<String>): String = "smsto:" + phones.joinToString(";")

    /** A `mailto:` target with every address, the subject and the body filled in. */
    fun mailtoTarget(emails: List<String>, subject: String, body: String): String =
        "mailto:" + emails.joinToString(",") + "?subject=" + encode(subject) + "&body=" + encode(body)

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    private fun cleanPhone(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 7) return null
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    private fun cleanEmail(raw: String): String? = raw.trim().lowercase().takeIf { '@' in it && ' ' !in it }
}
