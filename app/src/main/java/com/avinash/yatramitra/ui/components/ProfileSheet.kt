package com.avinash.yatramitra.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.AuthRepository

/** Shown when tapping the signed-in user's own avatar — basic account info plus sign out. There's
 *  no photo upload anywhere in the app, so this shows the same initials avatar used everywhere
 *  else rather than a real profile picture. */
@Composable
fun ProfileSheet(onDismiss: () -> Unit, onSignOut: () -> Unit) {
    val name = AuthRepository.currentUserName
    val email = AuthRepository.currentUserEmail
    val phone = AuthRepository.currentUserPhone

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your profile") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                InitialsAvatar(name = name, size = 52.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    if (email.isNotBlank()) {
                        Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (phone.isNotBlank()) {
                        Text(phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSignOut) { Text("Sign out") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
