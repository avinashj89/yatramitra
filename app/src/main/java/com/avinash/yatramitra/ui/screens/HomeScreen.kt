package com.avinash.yatramitra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.AuthRepository
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.model.TripSummary
import com.avinash.yatramitra.ui.components.YatraMitraLogo
import com.avinash.yatramitra.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Lands here after signing in whenever no trip is open. Lists your 5 most recent trips (opened
 *  read-only), and lets you start a new one or join an existing one by code. */
@Composable
fun HomeScreen(
    onOpenTrip: (session: LocalStore.Session, isReadOnly: Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uid = AuthRepository.currentUserId
    val myName = AuthRepository.currentUserName

    var trips by remember { mutableStateOf<List<TripSummary>>(emptyList()) }
    var showNewTripDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        if (uid != null) {
            TripRepository.observeMyTrips(uid).collect { trips = it }
        }
    }

    fun openExisting(summary: TripSummary, readOnly: Boolean) {
        scope.launch {
            if (uid != null) {
                TripRepository.recordTripAccess(uid, summary.tripCode, summary.tripName, summary.memberId, summary.role)
            }
            onOpenTrip(LocalStore.Session(summary.tripCode, summary.memberId, myName), readOnly)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            YatraMitraLogo(markSize = 40.dp, showTagline = false)
            TextButton(onClick = onSignOut) {
                Icon(Icons.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sign out")
            }
        }

        Text("Hi, $myName", style = MaterialTheme.typography.headlineSmall)

        Button(
            onClick = { showNewTripDialog = true },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Start a new trip", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = { showJoinDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text("Have a trip code?") }

        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider()

        Text("Recent trips", style = MaterialTheme.typography.titleMedium)
        if (trips.isEmpty()) {
            Text(
                "Trips you create or join will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
            trips.forEach { trip ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { openExisting(trip, readOnly = true) }
                        .padding(Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            trip.tripName.ifBlank { trip.tripCode },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            dateFormat.format(Date(trip.lastAccessedAtMillis)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(if (trip.role == MemberRole.ORGANIZER) "Organizer" else "Group Member") }
                    )
                }
            }
        }
    }

    if (showNewTripDialog) {
        var tripName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewTripDialog = false },
            title = { Text("Start a new trip") },
            text = {
                OutlinedTextField(
                    value = tripName,
                    onValueChange = { tripName = it },
                    label = { Text("Trip name") },
                    placeholder = { Text("e.g. Coorg long weekend") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = tripName.trim()
                        if (name.isBlank() || uid == null) return@TextButton
                        showNewTripDialog = false
                        error = null
                        loading = true
                        scope.launch {
                            try {
                                val code = TripRepository.createTrip(groupName = name)
                                val memberId = TripRepository.joinTrip(code, myName, role = MemberRole.ORGANIZER, uid = uid)
                                TripRepository.recordTripAccess(uid, code, name, memberId, MemberRole.ORGANIZER)
                                onOpenTrip(LocalStore.Session(code, memberId, myName), false)
                            } catch (e: Exception) {
                                error = "Couldn't create the trip — check your internet connection and try again."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = tripName.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewTripDialog = false }) { Text("Cancel") } }
        )
    }

    if (showJoinDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join a trip") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Trip code") },
                    placeholder = { Text("e.g. 7F3K9Q") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = code.trim()
                        if (trimmed.isBlank() || uid == null) return@TextButton
                        showJoinDialog = false
                        error = null
                        loading = true
                        scope.launch {
                            try {
                                if (!TripRepository.tripExists(trimmed)) {
                                    error = "No trip found with that code."
                                } else {
                                    val tripName = TripRepository.getGroupName(trimmed)
                                    val memberId = TripRepository.joinTrip(trimmed, myName, role = MemberRole.JOINER, uid = uid)
                                    TripRepository.recordTripAccess(uid, trimmed, tripName, memberId, MemberRole.JOINER)
                                    onOpenTrip(LocalStore.Session(trimmed.uppercase(), memberId, myName), false)
                                }
                            } catch (e: Exception) {
                                error = "Couldn't join — check your internet connection and try again."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = code.isNotBlank()
                ) { Text("Join") }
            },
            dismissButton = { TextButton(onClick = { showJoinDialog = false }) { Text("Cancel") } }
        )
    }
}
