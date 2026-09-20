package com.avinash.yatramitra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
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
import com.avinash.yatramitra.model.TripStatus
import com.avinash.yatramitra.model.TripSummary
import com.avinash.yatramitra.ui.components.InitialsAvatar
import com.avinash.yatramitra.ui.components.ProfileSheet
import com.avinash.yatramitra.ui.components.YatraMitraLogo
import com.avinash.yatramitra.ui.theme.Spacing
import com.avinash.yatramitra.ui.util.launchSafely
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Lands here after signing in whenever no trip is open. Lists your 5 most recent trips — the
 *  Organizer reopens an ongoing one fully editable, same as right after creating it; everyone
 *  else (or anyone once it's marked Completed) gets a read-only view — and lets you start a new
 *  one or join an existing one by code. */
@Composable
fun HomeScreen(
    onOpenTrip: (session: LocalStore.Session, isReadOnly: Boolean) -> Unit,
    onSignOut: () -> Unit,
    pendingJoinCode: String? = null,
    onJoinCodeHandled: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val uid = AuthRepository.currentUserId
    val myName = AuthRepository.currentUserName

    var trips by remember { mutableStateOf<List<TripSummary>>(emptyList()) }
    var showNewTripDialog by remember { mutableStateOf(false) }
    var newTripNameInput by remember { mutableStateOf("") }
    var duplicateNameConfirm by remember { mutableStateOf<String?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCodeInput by remember { mutableStateOf("") }
    var showProfile by remember { mutableStateOf(false) }
    var menuForTrip by remember { mutableStateOf<String?>(null) } // tripCode whose overflow menu is open
    var tripPendingDelete by remember { mutableStateOf<TripSummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        if (uid != null) {
            TripRepository.observeMyTrips(uid).collect { trips = it }
        }
    }

    // A yatramitra://join?code=XXXXXX tap (from the "Invite" share action) lands here instead of
    // asking the user to type the code in by hand.
    LaunchedEffect(pendingJoinCode) {
        if (!pendingJoinCode.isNullOrBlank()) {
            joinCodeInput = pendingJoinCode
            showJoinDialog = true
            onJoinCodeHandled()
        }
    }

    fun openExisting(summary: TripSummary) {
        scope.launch {
            if (uid == null) return@launch
            if (!TripRepository.tripExists(summary.tripCode)) {
                // Someone deleted this trip since it was last opened — drop the stale pointer
                // from just this user's own index (that's all a non-owner is allowed to touch)
                // instead of navigating into a trip that no longer exists.
                TripRepository.removeMyTripEntry(uid, summary.tripCode)
                error = "\"${summary.tripName.ifBlank { summary.tripCode }}\" was deleted and is no longer available."
                return@launch
            }
            // Re-fetch status fresh rather than trusting the (possibly stale) homepage index
            // entry: this is exactly what decides whether the Organizer gets edit access back,
            // so a status change made from another device must take effect immediately.
            val meta = TripRepository.getTripMeta(summary.tripCode)
            val readOnly = meta.status == TripStatus.COMPLETED || summary.role != MemberRole.ORGANIZER
            TripRepository.recordTripAccess(uid, summary.tripCode, summary.tripName, summary.memberId, summary.role, meta.status)
            onOpenTrip(LocalStore.Session(summary.tripCode, summary.memberId, myName), readOnly)
        }
    }

    fun createNewTrip(name: String) {
        if (uid == null) return
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
    }

    fun markCompleted(summary: TripSummary) {
        menuForTrip = null
        scope.launchSafely(
            onError = { error = it },
            errorMessage = "Couldn't update the trip — check your internet connection."
        ) {
            TripRepository.updateTripStatus(summary.tripCode, TripStatus.COMPLETED)
            if (uid != null) {
                TripRepository.recordTripAccess(uid, summary.tripCode, summary.tripName, summary.memberId, summary.role, TripStatus.COMPLETED)
            }
        }
    }

    fun deleteTrip(summary: TripSummary) {
        tripPendingDelete = null
        scope.launchSafely(
            onError = { error = it },
            errorMessage = "Couldn't delete the trip — check your internet connection."
        ) {
            TripRepository.deleteTrip(summary.tripCode, uid)
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
            InitialsAvatar(
                name = myName,
                size = 36.dp,
                modifier = Modifier.clickable { showProfile = true }
            )
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
                        .clickable { openExisting(trip) }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    when {
                                        trip.status == TripStatus.COMPLETED -> "Completed"
                                        trip.role == MemberRole.ORGANIZER -> "Organizer"
                                        else -> "Group Member"
                                    }
                                )
                            }
                        )
                        val canMarkCompleted = trip.role == MemberRole.ORGANIZER && trip.status == TripStatus.ONGOING
                        val canDelete = trip.status == TripStatus.COMPLETED ||
                            (trip.status == TripStatus.ONGOING && trip.role == MemberRole.ORGANIZER)
                        if (canMarkCompleted || canDelete) {
                            Box {
                                IconButton(onClick = { menuForTrip = trip.tripCode }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Trip options")
                                }
                                DropdownMenu(
                                    expanded = menuForTrip == trip.tripCode,
                                    onDismissRequest = { menuForTrip = null }
                                ) {
                                    if (canMarkCompleted) {
                                        DropdownMenuItem(
                                            text = { Text("Mark as completed") },
                                            onClick = { markCompleted(trip) }
                                        )
                                    }
                                    if (canDelete) {
                                        DropdownMenuItem(
                                            text = { Text("Delete trip") },
                                            onClick = {
                                                menuForTrip = null
                                                tripPendingDelete = trip
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewTripDialog) {
        AlertDialog(
            onDismissRequest = { showNewTripDialog = false; newTripNameInput = "" },
            title = { Text("Start a new trip") },
            text = {
                OutlinedTextField(
                    value = newTripNameInput,
                    onValueChange = { newTripNameInput = it },
                    label = { Text("Trip name") },
                    placeholder = { Text("e.g. Coorg long weekend") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newTripNameInput.trim()
                        if (name.isBlank() || uid == null) return@TextButton
                        showNewTripDialog = false
                        // A duplicate name can't be checked against every trip that exists (the
                        // security rules deliberately forbid listing the whole trips collection —
                        // see firestore.rules), only against this organizer's own recent trips, so
                        // this is a "did you mean to do that" nudge, not a hard uniqueness rule.
                        if (trips.any { it.tripName.equals(name, ignoreCase = true) }) {
                            duplicateNameConfirm = name
                        } else {
                            newTripNameInput = ""
                            createNewTrip(name)
                        }
                    },
                    enabled = newTripNameInput.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewTripDialog = false; newTripNameInput = "" }) { Text("Cancel") } }
        )
    }

    duplicateNameConfirm?.let { name ->
        AlertDialog(
            onDismissRequest = { duplicateNameConfirm = null },
            title = { Text("You already have a trip named this") },
            text = {
                Text("\"$name\" matches one of your recent trips. Create another one with the same name, or go back and rename it?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        duplicateNameConfirm = null
                        newTripNameInput = ""
                        createNewTrip(name)
                    }
                ) { Text("Create anyway") }
            },
            dismissButton = {
                TextButton(onClick = { duplicateNameConfirm = null; showNewTripDialog = true }) { Text("Go back") }
            }
        )
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false; joinCodeInput = "" },
            title = { Text("Join a trip") },
            text = {
                OutlinedTextField(
                    value = joinCodeInput,
                    onValueChange = { joinCodeInput = it.uppercase() },
                    label = { Text("Trip code") },
                    placeholder = { Text("e.g. 7F3K9Q") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = joinCodeInput.trim()
                        if (trimmed.isBlank() || uid == null) return@TextButton
                        showJoinDialog = false
                        joinCodeInput = ""
                        error = null
                        loading = true
                        scope.launch {
                            try {
                                if (!TripRepository.tripExists(trimmed)) {
                                    error = "No trip found with that code."
                                } else {
                                    val meta = TripRepository.getTripMeta(trimmed)
                                    val memberId = TripRepository.joinTrip(trimmed, myName, role = MemberRole.JOINER, uid = uid)
                                    TripRepository.recordTripAccess(uid, trimmed, meta.groupName, memberId, MemberRole.JOINER, meta.status)
                                    onOpenTrip(LocalStore.Session(trimmed.uppercase(), memberId, myName), false)
                                }
                            } catch (e: Exception) {
                                error = "Couldn't join — check your internet connection and try again."
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = joinCodeInput.isNotBlank()
                ) { Text("Join") }
            },
            dismissButton = { TextButton(onClick = { showJoinDialog = false; joinCodeInput = "" }) { Text("Cancel") } }
        )
    }

    if (showProfile) {
        ProfileSheet(
            onDismiss = { showProfile = false },
            onSignOut = {
                showProfile = false
                onSignOut()
            }
        )
    }

    tripPendingDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { tripPendingDelete = null },
            title = { Text("Delete this trip?") },
            text = {
                Text(
                    "This permanently deletes \"${trip.tripName.ifBlank { trip.tripCode }}\" and all its data " +
                        "(route, itinerary, expenses) for everyone on the trip — this can't be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { deleteTrip(trip) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { tripPendingDelete = null }) { Text("Cancel") } }
        )
    }
}
