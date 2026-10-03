package com.avinash.yatramitra.data

/**
 * Plain-language versions of Firebase's phone sign-in errors, which are written for developers
 * ("This operation is not allowed... [ SMS unable to be sent until this region enabled by the app
 * developer. ]"). Pure Kotlin, covered by AuthErrorsTest.
 */
object AuthErrors {

    /** The number in the +<country><number> form Firebase needs. Indian numbers can be typed
     *  without +91 ("98765 43210", "098765 43210", "91 98765 43210"). Null if it can't be a number. */
    fun normalizePhone(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        return when {
            trimmed.startsWith("+") && digits.length in 8..15 -> "+$digits"
            digits.length == 10 -> "+91$digits"
            digits.length == 11 && digits.startsWith("0") -> "+91${digits.drop(1)}"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> null
        }
    }

    /**
     * [errorCode] is FirebaseAuthException.errorCode when there is one, [tooManyRequests] is true
     * for FirebaseTooManyRequestsException, [rawMessage] is the exception's own message.
     */
    fun phoneMessage(errorCode: String?, rawMessage: String?, tooManyRequests: Boolean): String {
        val message = rawMessage.orEmpty()
        return when {
            message.contains("region enabled", ignoreCase = true) ||
                message.contains("region", ignoreCase = true) && message.contains("SMS", ignoreCase = true) ->
                "Sign-in by text message isn't switched on for this country yet. Please use Email for now."
            errorCode == "ERROR_OPERATION_NOT_ALLOWED" ->
                "Sign-in by text message is switched off for this app right now. Please use Email for now."
            errorCode == "ERROR_QUOTA_EXCEEDED" || message.contains("quota", ignoreCase = true) ->
                "The app has used up today's text messages. Please use Email, or try again tomorrow."
            tooManyRequests || errorCode == "ERROR_TOO_MANY_REQUESTS" ->
                "Too many codes were requested from this phone. Wait a while, or use Email."
            errorCode == "ERROR_INVALID_PHONE_NUMBER" ->
                "That phone number doesn't look right. Type it as +91 followed by the 10-digit number."
            errorCode == "ERROR_INVALID_VERIFICATION_CODE" ->
                "That code is wrong. Check the text message and try again."
            errorCode == "ERROR_SESSION_EXPIRED" ->
                "That code has expired. Tap \"Use a different number\" and send a new one."
            message.isNotBlank() -> message
            else -> "Couldn't send the verification code. Check your internet connection and try again."
        }
    }
}
