package com.avinash.yatramitra.data

import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.MemberRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripNotifyTest {

    private val me = Member(id = "me", name = "Avi", role = MemberRole.ORGANIZER, phone = "+919000000001", email = "avi@x.com")
    private val ravi = Member(id = "r", name = "Ravi", phone = "98765 43210")
    private val sita = Member(id = "s", name = "Sita", email = " Sita@Example.com ")
    private val both = Member(id = "b", name = "Bob", phone = "+91 90000 00002", email = "bob@x.com")
    private val ghost = Member(id = "g", name = "Ghost")

    @Test
    fun `the sender is never messaged`() {
        val r = TripNotify.recipients(listOf(me, ravi), "me")
        assertEquals(listOf("9876543210"), r.phones)
        assertTrue(r.emails.isEmpty())
    }

    @Test
    fun `phones and emails are cleaned and collected`() {
        val r = TripNotify.recipients(listOf(me, ravi, sita, both), "me")
        assertEquals(listOf("9876543210", "+919000000002"), r.phones)
        assertEquals(listOf("sita@example.com", "bob@x.com"), r.emails)
        assertTrue(r.unreachable.isEmpty())
    }

    @Test
    fun `people with no phone or email are listed by name`() {
        val r = TripNotify.recipients(listOf(me, ghost, Member(id = "j", name = "Junk", phone = "12", email = "nope")), "me")
        assertEquals(listOf("Ghost", "Junk"), r.unreachable)
    }

    @Test
    fun `the same number twice is messaged once`() {
        val r = TripNotify.recipients(listOf(ravi, ravi.copy(id = "r2")), "me")
        assertEquals(1, r.phones.size)
    }

    @Test
    fun `one sms goes to every number`() {
        assertEquals("smsto:9876543210;+919000000002", TripNotify.smsTarget(listOf("9876543210", "+919000000002")))
    }

    @Test
    fun `the email link carries everyone, the subject and the body safely encoded`() {
        val link = TripNotify.mailtoTarget(listOf("a@x.com", "b@y.com"), "Goa & back", "Hi all\nline 2")
        assertTrue(link.startsWith("mailto:a@x.com,b@y.com?subject="))
        assertTrue(link.contains("subject=Goa%20%26%20back"))
        assertTrue(link.contains("body=Hi%20all%0Aline%202"))
        assertFalse(link.contains(" "))
    }

    @Test
    fun `the message names the trip and carries the code and the link`() {
        val text = TripNotify.startedMessage("Coorg weekend", "ABC234")
        assertTrue(text.contains("Coorg weekend"))
        assertTrue(text.contains("yatramitra://join?code=ABC234"))
        assertEquals("ABC234", TripRules.extractTripCode(text))
    }
}
