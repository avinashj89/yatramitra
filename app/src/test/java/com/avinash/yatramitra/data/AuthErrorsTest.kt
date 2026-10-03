package com.avinash.yatramitra.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthErrorsTest {

    // The exact text Firebase returned in the reported screenshot.
    private val regionBlocked = "This operation is not allowed. This may be because the given sign-in provider is disabled " +
        "for this Firebase project. Enable it in the Firebase console, under the sign-in method tab of the Auth " +
        "section. [ SMS unable to be sent until this region enabled by the app developer. ]"

    @Test
    fun `the region error from the screenshot becomes plain language`() {
        val shown = AuthErrors.phoneMessage("ERROR_OPERATION_NOT_ALLOWED", regionBlocked, tooManyRequests = false)
        assertTrue(shown.contains("isn't switched on for this country"))
        assertFalse(shown.contains("Firebase"))
    }

    @Test
    fun `other known errors get their own plain messages`() {
        assertTrue(AuthErrors.phoneMessage("ERROR_OPERATION_NOT_ALLOWED", "This operation is not allowed.", false).contains("switched off"))
        assertTrue(AuthErrors.phoneMessage(null, "We have blocked all requests", true).contains("Too many codes"))
        assertTrue(AuthErrors.phoneMessage("ERROR_QUOTA_EXCEEDED", "quota", false).contains("today's text messages"))
        assertTrue(AuthErrors.phoneMessage("ERROR_INVALID_PHONE_NUMBER", "bad", false).contains("+91"))
        assertTrue(AuthErrors.phoneMessage("ERROR_INVALID_VERIFICATION_CODE", "bad", false).contains("code is wrong"))
    }

    @Test
    fun `unknown errors keep their own text, and empty ones get a fallback`() {
        assertEquals("Something odd", AuthErrors.phoneMessage("ERROR_X", "Something odd", false))
        assertTrue(AuthErrors.phoneMessage(null, null, false).contains("internet"))
    }

    @Test
    fun `indian numbers can be typed without plus 91`() {
        assertEquals("+918396912082", AuthErrors.normalizePhone("8396912082"))
        assertEquals("+918396912082", AuthErrors.normalizePhone("83969 12082"))
        assertEquals("+918396912082", AuthErrors.normalizePhone("08396912082"))
        assertEquals("+918396912082", AuthErrors.normalizePhone("918396912082"))
        assertEquals("+918396912082", AuthErrors.normalizePhone("+91 83969-12082"))
        assertEquals("+918396912082", AuthErrors.normalizePhone("+918396912082"))
    }

    @Test
    fun `other countries keep their own code`() {
        assertEquals("+14155550123", AuthErrors.normalizePhone("+1 415 555 0123"))
    }

    @Test
    fun `things that can't be phone numbers are rejected`() {
        assertNull(AuthErrors.normalizePhone(""))
        assertNull(AuthErrors.normalizePhone("12345"))
        assertNull(AuthErrors.normalizePhone("abc"))
    }
}
