package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.ItineraryDay
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.model.TripStatus

/**
 * Pure decisions shared by several screens, kept free of Compose and Firebase so they are covered
 * by plain JVM unit tests (see TripRulesTest).
 */
object TripRules {
    const val CODE_LENGTH = 6

    /** The alphabet trip codes are generated from: no 0/O and no 1/I, to avoid mix-ups. */
    const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /** A trip stays editable for everyone on it until the Organizer marks it completed. */
    fun isReadOnly(status: TripStatus): Boolean = status == TripStatus.COMPLETED

    /** The route and itinerary belong to the Organizer; group members suggest changes instead.
     *  Expenses, suggestions and your own UPI ID stay open to every member while the trip is ongoing. */
    fun canEditPlan(role: MemberRole, status: TripStatus): Boolean =
        role == MemberRole.ORGANIZER && !isReadOnly(status)

    /**
     * Pulls a trip code out of whatever was typed or pasted: the bare code in any case, with
     * stray spaces, dashes or invisible characters removed, or the whole invite message / link
     * ("...yatramitra://join?code=ABC234 ... enter code: ABC234"). Null if nothing code-shaped is
     * found.
     */
    fun extractTripCode(raw: String): String? {
        val upper = raw.uppercase()
        Regex("CODE[=:\\s]+([A-Z0-9]{$CODE_LENGTH})(?![A-Z0-9])").find(upper)?.let { return it.groupValues[1] }
        val compact = upper.filter { it in 'A'..'Z' || it in '0'..'9' }
        if (compact.length == CODE_LENGTH) return compact
        return Regex("(?<![A-Z0-9])([A-Z0-9]{$CODE_LENGTH})(?![A-Z0-9])").findAll(upper).lastOrNull()?.groupValues?.get(1)
    }

    /** True if [code] contains a character no generated code can contain (0, O, 1 or I), which
     *  means it was mistyped rather than that the trip is missing. */
    fun hasImpossibleCharacters(code: String): Boolean = code.any { it !in CODE_ALPHABET }

    /** Which of an account's member entries on one trip is really "them" (pairs of member and
     *  joinedAt millis). Older versions added a second entry whenever someone joined again by code,
     *  including the Organizer on another phone, which then saw the trip as a group member. */
    fun pickOwnMember(entries: List<Pair<Member, Long>>): Member? =
        entries.sortedWith(
            compareBy<Pair<Member, Long>> { if (it.first.role == MemberRole.ORGANIZER) 0 else 1 }.thenBy { it.second }
        ).firstOrNull()?.first

    /** Whether to switch a homepage entry over to the member entry [found] on the server. Never
     *  trades an Organizer entry for a group-member one (very old Organizer entries have no account
     *  id on them, so the lookup can only ever find a later duplicate). */
    fun shouldAdoptMember(found: Member?, listedRole: MemberRole): Boolean =
        found != null && (found.role == MemberRole.ORGANIZER || listedRole != MemberRole.ORGANIZER)

    /** Order for a newly added itinerary day: one past the highest existing order, so the "Day N"
     *  label never repeats after a day in the middle has been deleted. */
    fun nextDayOrder(days: List<ItineraryDay>): Int = (days.maxOfOrNull { it.order } ?: -1) + 1

    /**
     * Every form an Indian mobile number is commonly stored or typed in, so "98765 43210",
     * "098765-43210", "+91 98765 43210" and "919876543210" all find an account whose sign-in
     * number Firebase saved as "+919876543210". Empty if there are no digits at all.
     */
    fun phoneVariants(raw: String): List<String> {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return emptyList()
        val variants = linkedSetOf(trimmed, digits, "+$digits")
        val local = when {
            digits.length == 10 -> digits
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            else -> null
        }
        if (local != null) {
            variants += local
            variants += "+91$local"
            variants += "91$local"
        }
        return variants.toList()
    }
}
