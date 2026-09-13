package com.avinash.yatramitra.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.AuthRepository
import com.avinash.yatramitra.ui.components.YatraMitraLogo
import kotlinx.coroutines.launch

private enum class AuthMethod { EMAIL, PHONE }
private enum class AuthMode { SIGN_IN, SIGN_UP }

/** Real account sign-up/sign-in — email+password or phone/OTP. Shown before anything else in the
 *  app; [onAuthenticated] fires once Firebase confirms a session, and the homepage takes over. */
@Composable
fun AuthScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var method by remember { mutableStateOf(AuthMethod.EMAIL) }
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var needsEmailVerification by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }
    var resetSent by remember { mutableStateOf(false) }

    if (needsEmailVerification) {
        VerifyEmailGate(
            email = AuthRepository.currentUserEmail.ifBlank { email },
            onVerified = onAuthenticated,
            onSignOutInstead = {
                AuthRepository.signOut()
                needsEmailVerification = false
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        YatraMitraLogo(markSize = 48.dp)
        Text(
            "Plan a route, build a day-by-day itinerary, and split expenses live with your travel group.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = method == AuthMethod.EMAIL,
                onClick = { method = AuthMethod.EMAIL; error = null },
                label = { Text("Email") }
            )
            FilterChip(
                selected = method == AuthMethod.PHONE,
                onClick = { method = AuthMethod.PHONE; error = null; verificationId = null },
                label = { Text("Phone") }
            )
        }

        if (method == AuthMethod.EMAIL) {
            if (mode == AuthMode.SIGN_UP) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    error = null
                    loading = true
                    scope.launch {
                        try {
                            if (mode == AuthMode.SIGN_UP) {
                                AuthRepository.signUpWithEmail(name, email, password)
                                // A brand-new account is never verified yet — always gate here.
                                needsEmailVerification = true
                            } else {
                                AuthRepository.signInWithEmail(email, password)
                                if (AuthRepository.isEmailVerified) {
                                    onAuthenticated()
                                } else {
                                    needsEmailVerification = true
                                }
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "Something went wrong. Check your details and try again."
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = !loading && email.isNotBlank() && password.isNotBlank() && (mode == AuthMode.SIGN_IN || name.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(if (mode == AuthMode.SIGN_UP) "Create account" else "Sign in") }

            TextButton(onClick = {
                mode = if (mode == AuthMode.SIGN_UP) AuthMode.SIGN_IN else AuthMode.SIGN_UP
                error = null
            }) {
                Text(if (mode == AuthMode.SIGN_UP) "Already have an account? Sign in" else "New here? Create an account")
            }

            if (mode == AuthMode.SIGN_IN) {
                TextButton(onClick = { showForgotPassword = true; resetSent = false; error = null }) {
                    Text("Forgot password?")
                }
            }
        } else {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                enabled = verificationId == null,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone number") },
                placeholder = { Text("+91XXXXXXXXXX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = verificationId == null,
                modifier = Modifier.fillMaxWidth()
            )

            if (verificationId == null) {
                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity == null) {
                            error = "Can't verify your phone right now — try again."
                        } else {
                            error = null
                            loading = true
                            AuthRepository.sendPhoneOtp(
                                phoneNumber = phone,
                                activity = activity,
                                pendingDisplayName = name,
                                onCodeSent = { id ->
                                    loading = false
                                    verificationId = id
                                },
                                onAutoVerified = {
                                    loading = false
                                    onAuthenticated()
                                },
                                onError = { message ->
                                    loading = false
                                    error = message
                                }
                            )
                        }
                    },
                    enabled = !loading && name.isNotBlank() && phone.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Send code") }
            } else {
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { otpCode = it },
                    label = { Text("Verification code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        error = null
                        loading = true
                        AuthRepository.verifyPhoneOtpCode(
                            verificationId = verificationId ?: return@Button,
                            code = otpCode,
                            pendingDisplayName = name,
                            onSuccess = {
                                loading = false
                                onAuthenticated()
                            },
                            onError = { message ->
                                loading = false
                                error = message
                            }
                        )
                    },
                    enabled = !loading && otpCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Verify") }
                TextButton(onClick = { verificationId = null; otpCode = "" }) { Text("Use a different number") }
            }
        }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }

    if (showForgotPassword) {
        var resetEmail by remember { mutableStateOf(email) }
        var resetLoading by remember { mutableStateOf(false) }
        var resetError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showForgotPassword = false },
            title = { Text("Reset your password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (resetSent) {
                        Text("Check your email for a reset link.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (resetLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        resetError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                if (resetSent) {
                    TextButton(onClick = { showForgotPassword = false }) { Text("Done") }
                } else {
                    TextButton(
                        onClick = {
                            resetError = null
                            resetLoading = true
                            scope.launch {
                                try {
                                    AuthRepository.sendPasswordReset(resetEmail)
                                    resetSent = true
                                } catch (e: Exception) {
                                    resetError = e.message ?: "Couldn't send the reset email — check the address and try again."
                                } finally {
                                    resetLoading = false
                                }
                            }
                        },
                        enabled = !resetLoading && resetEmail.isNotBlank()
                    ) { Text("Send reset link") }
                }
            },
            dismissButton = {
                if (!resetSent) TextButton(onClick = { showForgotPassword = false }) { Text("Cancel") }
            }
        )
    }
}

/** Blocks progress into the app until the just-signed-up/signed-in email account has clicked its
 *  verification link — [onVerified] fires once a reload confirms it. */
@Composable
private fun VerifyEmailGate(email: String, onVerified: () -> Unit, onSignOutInstead: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        YatraMitraLogo(markSize = 48.dp)
        Text("Verify your email", style = MaterialTheme.typography.headlineSmall)
        Text(
            "We sent a verification link to $email. Click it, then come back and continue below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                error = null
                loading = true
                scope.launch {
                    try {
                        if (AuthRepository.reloadAndCheckVerified()) {
                            onVerified()
                        } else {
                            error = "Still not verified — check your email and click the link first."
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Something went wrong — try again."
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("I've verified — Continue") }

        TextButton(
            onClick = {
                error = null
                loading = true
                scope.launch {
                    try {
                        AuthRepository.sendVerificationEmail()
                        resent = true
                    } catch (e: Exception) {
                        error = e.message ?: "Couldn't resend the email — try again."
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading
        ) { Text("Resend email") }

        TextButton(onClick = onSignOutInstead) { Text("Use a different account") }

        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (resent) {
            Text("Verification email resent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
