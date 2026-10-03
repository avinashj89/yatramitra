package com.avinash.yatramitra.data

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.avinash.yatramitra.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Real user accounts (email+password, phone/OTP or Google), replacing the old anonymous-only sign-in.
 * Every trip screen now assumes [isSignedIn] is already true — the app gates on [AuthScreen]
 * before anything else, so [TripRepository] no longer needs to sign anyone in itself.
 */
object AuthRepository {

    private val auth by lazy { FirebaseAuth.getInstance() }

    val isSignedIn: Boolean get() = auth.currentUser != null
    val currentUserId: String? get() = auth.currentUser?.uid
    val currentUserName: String get() = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Traveler"
    val currentUserEmail: String get() = auth.currentUser?.email.orEmpty()
    val currentUserPhone: String get() = auth.currentUser?.phoneNumber.orEmpty()

    /** True once the signed-in email account has clicked its verification link. Always true for
     *  a phone account (phone sign-in is already OTP-verified) or when there's no user at all. */
    val isEmailVerified: Boolean get() = auth.currentUser?.let { it.email == null || it.isEmailVerified } ?: true

    suspend fun signUpWithEmail(name: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        result.user?.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()
        )?.await()
        result.user?.sendEmailVerification()?.await()
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    /** Re-sends the verification link to the signed-in user's email. */
    suspend fun sendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    /** Refreshes the current user's data from Firebase and returns whether their email is now
     *  verified — [FirebaseUser.isEmailVerified] is only as fresh as the last sign-in/reload, so
     *  this must be called after the user claims to have clicked the emailed link. */
    suspend fun reloadAndCheckVerified(): Boolean {
        auth.currentUser?.reload()?.await()
        return isEmailVerified
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** Thrown when Google sign-in can't start because the app has no Web client ID yet. */
    class GoogleSignInNotSetUpException : Exception(
        "Google sign-in isn't set up for this app yet. Please use Email or Phone for now."
    )

    /**
     * "Continue with Google": shows Android's Google account sheet, then signs in to Firebase with
     * the chosen account (creating the YatraMitra account on first use). Google accounts are
     * already email-verified. Throws GetCredentialCancellationException if the user backs out.
     */
    suspend fun signInWithGoogle(activity: Activity) {
        val clientId = googleWebClientId(activity) ?: throw GoogleSignInNotSetUpException()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Google didn't return an account. Please try again.")
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await()
    }

    /** The Firebase project's Web client ID: from google-services.json (as the generated
     *  default_web_client_id resource) or, failing that, from strings.xml. */
    private fun googleWebClientId(context: Context): String? {
        @SuppressLint("DiscouragedApi") // the resource only exists once google-services.json has it
        val generated = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val fromConfig = if (generated != 0) context.getString(generated) else ""
        return fromConfig.ifBlank { context.getString(R.string.google_web_client_id) }.trim().takeIf { it.isNotBlank() }
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
                onError(friendly(e))
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
            .addOnFailureListener { onError(friendly(it)) }
    }

    private fun friendly(e: Exception): String = AuthErrors.phoneMessage(
        errorCode = (e as? FirebaseAuthException)?.errorCode,
        rawMessage = e.message,
        tooManyRequests = e is FirebaseTooManyRequestsException
    )
}
