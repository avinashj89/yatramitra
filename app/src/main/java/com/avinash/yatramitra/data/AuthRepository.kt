package com.avinash.yatramitra.data

import android.app.Activity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Real user accounts (email+password or phone/OTP), replacing the old anonymous-only sign-in.
 * Every trip screen now assumes [isSignedIn] is already true — the app gates on [AuthScreen]
 * before anything else, so [TripRepository] no longer needs to sign anyone in itself.
 */
object AuthRepository {

    private val auth by lazy { FirebaseAuth.getInstance() }

    val isSignedIn: Boolean get() = auth.currentUser != null
    val currentUserId: String? get() = auth.currentUser?.uid
    val currentUserName: String get() = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Traveler"

    suspend fun signUpWithEmail(name: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        result.user?.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
        )?.await()
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun signOut() {
        auth.signOut()
    }

    /** Starts phone verification. [onCodeSent] fires once an SMS is on its way (show the OTP
     *  entry field then); [onAutoVerified] fires if Android verified the device silently without
     *  the user typing anything; [onError] covers both send failures and later verification
     *  failures. [pendingDisplayName] is applied to the account's profile only on first sign-up
     *  (i.e. only if the account doesn't already have a display name). */
    fun sendPhoneOtp(
        phoneNumber: String,
        activity: Activity,
        pendingDisplayName: String,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: () -> Unit,
        onError: (String) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithPhoneCredential(credential, pendingDisplayName, onAutoVerified, onError)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                onError(e.message ?: "Couldn't send the verification code.")
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                onCodeSent(verificationId)
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber.trim())
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyPhoneOtpCode(
        verificationId: String,
        code: String,
        pendingDisplayName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code.trim())
        signInWithPhoneCredential(credential, pendingDisplayName, onSuccess, onError)
    }

    private fun signInWithPhoneCredential(
        credential: PhoneAuthCredential,
        pendingDisplayName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null && user.displayName.isNullOrBlank() && pendingDisplayName.isNotBlank()) {
                    user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(pendingDisplayName).build())
                }
                onSuccess()
            }
            .addOnFailureListener { onError(it.message ?: "That code didn't work — try again.") }
    }
}
