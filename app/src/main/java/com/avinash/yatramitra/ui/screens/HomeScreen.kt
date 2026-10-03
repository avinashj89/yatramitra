package com.avinash.yatramitra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.avinash.yatramitra.data.AuthRepository
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.TripLookup
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.data.TripRules
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.model.TripStatus
import com.avinash.yatramitra.model.TripSummary
import com.avinash.yatramitra.ui.components.InitialsAvatar
import com.avinash.yatramitra.ui.components.ProfileSheet
import com.avinash.yatramitra.ui.components.YatraMitraLogo
import com.avinash.yatramitra.ui.theme.Spacing
import com.avinash.yatramitra.ui.util.launchSafely
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Lands here after signing in whenever no trip is open. Lists all your trips, most recent first,
 *  and lets you start a new one or join an existing one by code. Whether an opened trip is
 *  editable is decided live inside the trip from its status (see TripRules), not here. */
@Composable
fun HomeScreen(
    onOpenTrip: (session: LocalStore.Session) -> Unit,
    onSignOut: () -> Unit,
    pendingJoinCode: String? = null,
    onJoinCodeHandled: () -> Unit = {},
    /** A one-off message to show on arrival, e.g. that the trip you were in was just deleted. */
    notice: String? = null,
    onNoticeShown: () -> Unit = {}
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

    LaunchedEffect(notice) {
        if (notice != null) {
            error = notice
            onNoticeShown()
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
        if (uid == null) return
        val label = summary.tripName.ifBlank { summary.tripCode }
        error = null
        loading = true
        // Every failure here is caught: before, any network error while opening a trip from this
        // list was thrown out of the coroutine and crashed the whole app.
        scope.launch {
            try {
                when (val found = TripRepository.lookupTrip(summary.tripCode)) {
                    is TripLookup.NotFound -> {
                        // Deleted since it was last opened: drop the stale pointer from this user's
                        // own list (the only one they're allowed to change).
                        runCatching { TripRepository.removeMyTripEntry(uid, summary.tripCode) }
                        error = "\"$label\" was deleted and is no longer available."
                    }
                    is TripLookup.Unreachable -> error = "Couldn't open \"$label\": ${found.reason}."
                    is TripLookup.Found -> {
                        var memberId = summary.memberId
                        var role = summary.role
                        if (!found.fromCache) {
                            // Repair entries older versions got wrong: joining your own trip by code
                            // on a second phone re-pointed this entry at a new group-member place,
                            // which made the Organizer's own trip uneditable everywhere.
                            val mine = runCatching { TripRepository.findMemberByUid(summary.tripCode, uid) }.getOrNull()
                            if (mine != null && TripRules.shouldAdoptMember(mine, summary.role)) {
                                memberId = mine.id
                                role = mine.role
                            }
                            // Refresh the entry with the trip's current name and status (this used to
                            // write the old name back every time, so renames never showed here).
                            runCatching {
                                TripRepository.recordTripAccess(
                                    uid, summary.tripCode, found.meta.groupName, memberId, role, found.meta.status
                                )
                            }
                        }
                        onOpenTrip(LocalStore.Session(summary.tripCode, memberId, myName))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Couldn't open \"$label\": ${e.message ?: "unexpected error"}."
            } finally {
                loading = false
            }
        }
    }

    fun joinByCode(rawInput: String) {
        if (uid == null) return
        val code = TripRules.extractTripCode(rawInput)
        if (code == null) {
            error = "\"${rawInput.trim()}\" isn't a trip code. Codes are 6 letters and numbers, like 7F3K9Q."
            return
        }
        error = null
        loading = true
        scope.launch {
            try {
                when (val found = TripRepository.lookupTrip(code)) {
                    is TripLookup.NotFound -> error = if (TripRules.hasImpossibleCharacters(code)) {
                        "No trip has the code $code. Trip codes never contain 0, O, 1 or I, so one of those was probably mistyped."
                    } else {
                        "No trip found with the code $code. Ask the organiser to check the code, or to send the invite again."
                    }
                    is TripLookup.Unreachable -> error = "Couldn't check the code $code: ${found.reason}."
                    is TripLookup.Found -> {
                        if (found.fromCache) {
                            error = "You're offline. Connect to the internet to join a trip."
                            return@launch
                        }
                        // Reuse this account's existing place on the trip if it has one: joining
                        // again used to add a duplicate member, and the Organizer opening their own
                        // code on a second phone was demoted to a group member there.
                        val existing = TripRepository.findMemberByUid(code, uid)
                        val memberId = existing?.id ?: TripRepository.joinTrip(code, myName, role = MemberRole.JOINER, uid = uid)
                        val role = existing?.role ?: MemberRole.JOINER
                        TripRepository.recordTripAccess(uid, code, found.meta.groupName, memberId, role, found.meta.status)
                        onOpenTrip(LocalStore.Session(code, memberId, myName))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Couldn't join: ${e.message ?: "check your internet connection and try again"}."
            } finally {
                loading = false
            }
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
                onOpenTrip(LocalStore.Session(code, memberId, myName))
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = joinCodeInput,
                        onValueChange = { joinCodeInput = it.uppercase() },
                        label = { Text("Trip code") },
                        placeholder = { Text("e.g. 7F3K9Q") },
                        singleLine = true,
                        // No autocorrect: keyboards were free to "fix" a code into a different word.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrect = false,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "You can also paste the whole invite message.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val input = joinCodeInput
                        if (input.isBlank() || uid == null) return@TextButton
                        showJoinDialog = false
                        joinCodeInput = ""
                        joinByCode(input)
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
