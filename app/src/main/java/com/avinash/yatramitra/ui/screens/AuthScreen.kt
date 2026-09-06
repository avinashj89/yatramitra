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
                            } else {
                                AuthRepository.signInWithEmail(email, password)
                            }
                            onAuthenticated()
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
}
