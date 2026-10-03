package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.ItineraryDay
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRulesTest {

    // ---- Editing until completed ----

    @Test
    fun `an ongoing trip is editable and a completed one is locked`() {
        assertFalse(TripRules.isReadOnly(TripStatus.ONGOING))
        assertTrue(TripRules.isReadOnly(TripStatus.COMPLETED))
    }

    @Test
    fun `the organizer edits the plan only while the trip is ongoing`() {
        assertTrue(TripRules.canEditPlan(MemberRole.ORGANIZER, TripStatus.ONGOING))
        assertFalse(TripRules.canEditPlan(MemberRole.ORGANIZER, TripStatus.COMPLETED))
    }

    @Test
    fun `group members suggest plan changes instead of editing`() {
        assertFalse(TripRules.canEditPlan(MemberRole.JOINER, TripStatus.ONGOING))
        assertFalse(TripRules.canEditPlan(MemberRole.JOINER, TripStatus.COMPLETED))
    }

    // ---- Joining by code ----

    @Test
    fun `a plain code is accepted in any case`() {
        assertEquals("7F3K9Q", TripRules.extractTripCode("7F3K9Q"))
        assertEquals("7F3K9Q", TripRules.extractTripCode("7f3k9q"))
    }

    @Test
    fun `spaces, dashes and invisible characters inside a code are ignored`() {
        assertEquals("7F3K9Q", TripRules.extractTripCode("  7F3 K9Q "))
        assertEquals("7F3K9Q", TripRules.extractTripCode("7F3-K9Q"))
        assertEquals("7F3K9Q", TripRules.extractTripCode("7F3​K9Q"))
        assertEquals("7F3K9Q", TripRules.extractTripCode("7F3K9Q\n"))
    }

    @Test
    fun `the whole invite message from the top bar can be pasted`() {
        val message = "Join our trip on YatraMitra! If this shows up as a tappable link, use it to jump " +
            "straight in (needs the app already installed): yatramitra://join?code=ABC234\n" +
            "Otherwise, open the app and enter code: ABC234"
        assertEquals("ABC234", TripRules.extractTripCode(message))
    }

    @Test
    fun `the whole invite message from the Expenses tab can be pasted`() {
        val message = "Join our trip on YatraMitra! Tap to open it in the app: yatramitra://join?code=XY7Z23\n" +
            "Or open the app, tap \"Have a trip code?\" and enter code: XY7Z23"
        assertEquals("XY7Z23", TripRules.extractTripCode(message))
    }

    @Test
    fun `a code mentioned in a chat message is found`() {
        assertEquals("ABC234", TripRules.extractTripCode("Trip code is ABC234 see you"))
        assertEquals("ABC234", TripRules.extractTripCode("code:abc234"))
    }

    @Test
    fun `input that is not code shaped is rejected`() {
        assertNull(TripRules.extractTripCode(""))
        assertNull(TripRules.extractTripCode("   "))
        assertNull(TripRules.extractTripCode("7F3K9"))
        assertNull(TripRules.extractTripCode("7F3K9QX"))
        assertNull(TripRules.extractTripCode("hi"))
    }

    @Test
    fun `characters that never appear in generated codes are flagged as typos`() {
        assertFalse(TripRules.hasImpossibleCharacters("7F3K9Q"))
        assertTrue(TripRules.hasImpossibleCharacters("7F3K0Q"))
        assertTrue(TripRules.hasImpossibleCharacters("OOOOOO"))
        assertTrue(TripRules.hasImpossibleCharacters("1IIIII"))
    }

    @Test
    fun `the code alphabet itself never contains look-alike characters`() {
        assertEquals(32, TripRules.CODE_ALPHABET.length)
        assertFalse(TripRules.hasImpossibleCharacters(TripRules.CODE_ALPHABET))
        "0O1I".forEach { assertFalse(it in TripRules.CODE_ALPHABET) }
    }

    // ---- One member entry per account ----

    private fun member(id: String, role: MemberRole) = Member(id = id, name = id, role = role, uid = "u1")

    @Test
    fun `the organizer entry wins over a later duplicate group member entry`() {
        val picked = TripRules.pickOwnMember(
            listOf(member("dup", MemberRole.JOINER) to 100L, member("org", MemberRole.ORGANIZER) to 200L)
        )
        assertEquals("org", picked?.id)
    }

    @Test
    fun `without an organizer entry the earliest entry wins`() {
        val picked = TripRules.pickOwnMember(
            listOf(member("late", MemberRole.JOINER) to 300L, member("early", MemberRole.JOINER) to 100L)
        )
        assertEquals("early", picked?.id)
    }

    @Test
    fun `no entries means not on the trip yet`() {
        assertNull(TripRules.pickOwnMember(emptyList()))
    }

    @Test
    fun `a homepage entry is never switched from organizer to group member`() {
        assertFalse(TripRules.shouldAdoptMember(null, MemberRole.JOINER))
        assertFalse(TripRules.shouldAdoptMember(member("j", MemberRole.JOINER), MemberRole.ORGANIZER))
        assertTrue(TripRules.shouldAdoptMember(member("o", MemberRole.ORGANIZER), MemberRole.JOINER))
        assertTrue(TripRules.shouldAdoptMember(member("o", MemberRole.ORGANIZER), MemberRole.ORGANIZER))
        assertTrue(TripRules.shouldAdoptMember(member("j", MemberRole.JOINER), MemberRole.JOINER))
    }

    // ---- Itinerary days ----

    @Test
    fun `the first day gets order zero`() {
        assertEquals(0, TripRules.nextDayOrder(emptyList()))
    }

    @Test
    fun `a new day never reuses a number after a middle day was deleted`() {
        val days = listOf(ItineraryDay(id = "a", label = "Day 1", order = 0), ItineraryDay(id = "c", label = "Day 3", order = 2))
        val next = TripRules.nextDayOrder(days)
        assertEquals(3, next)
        assertFalse(days.any { it.label == "Day ${next + 1}" })
    }

    // ---- Account matching by phone ----

    @Test
    fun `every common way of typing an Indian number finds the stored form`() {
        listOf("98765 43210", "9876543210", "098765-43210", "+91 98765 43210", "919876543210", "+919876543210")
            .forEach { typed ->
                val variants = TripRules.phoneVariants(typed)
                assertTrue("$typed -> $variants", "+919876543210" in variants)
                assertTrue("$typed -> $variants", "9876543210" in variants)
            }
    }

    @Test
    fun `phone variants stay within the Firestore in-query limit`() {
        assertTrue(TripRules.phoneVariants("+91 98765 43210").size <= 10)
    }

    @Test
    fun `no digits means nothing to look up`() {
        assertTrue(TripRules.phoneVariants("").isEmpty())
        assertTrue(TripRules.phoneVariants("abc").isEmpty())
    }
}
