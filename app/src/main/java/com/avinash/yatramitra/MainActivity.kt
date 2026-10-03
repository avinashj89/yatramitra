package com.avinash.yatramitra

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.avinash.yatramitra.data.AuthRepository
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.ThemeMode
import com.avinash.yatramitra.data.ThemePreference
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.data.TripRules
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.model.TripStatus
import com.avinash.yatramitra.ui.components.InitialsAvatar
import com.avinash.yatramitra.ui.components.ProfileSheet
import com.avinash.yatramitra.ui.components.YatraMitraLogo
import com.avinash.yatramitra.ui.screens.AuthScreen
import com.avinash.yatramitra.ui.screens.ExpensesScreen
import com.avinash.yatramitra.ui.screens.HomeScreen
import com.avinash.yatramitra.ui.screens.ItineraryScreen
import com.avinash.yatramitra.ui.screens.TripPlannerScreen
import com.avinash.yatramitra.ui.theme.Spacing
import com.avinash.yatramitra.ui.theme.YatraMitraTheme
import com.avinash.yatramitra.ui.util.launchSafely
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    // Set from a yatramitra://join?code=XXXXXX deep link (see the second intent-filter in
    // AndroidManifest.xml) -- read once by HomeScreen to pre-fill and auto-open its "Have a trip
    // code?" dialog, then cleared via onJoinCodeHandled so it doesn't re-trigger on every
    // recomposition or when navigating back to the homepage later in the same session.
    private var pendingJoinCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemePreference.init(applicationContext)
        pendingJoinCode = joinCodeFrom(intent)
        // Some Android devices carry an outdated TLS/crypto provider that fails to negotiate a
        // handshake with certain modern servers ("Handshake failed") even though the server side
        // is fine. This patches the device's provider at runtime via Play Services -- Google's
        // documented fix for exactly that symptom. Runs on a background thread so it never delays
        // startup, and is defensive since a device without Play Services must still work.
        Thread {
            try {
                ProviderInstaller.installIfNeeded(applicationContext)
            } catch (e: GooglePlayServicesRepairableException) {
                // Play Services needs updating -- app still works, just without the patched provider.
            } catch (e: GooglePlayServicesNotAvailableException) {
                // Play Services unavailable on this device -- nothing more to do here.
            } catch (e: Exception) {
                // Never let provider-patching itself crash startup.
            }
        }.start()
        setContent {
            val darkTheme = when (ThemePreference.mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            YatraMitraTheme(darkTheme = darkTheme) {
                YatraMitraApp(
                    pendingJoinCode = pendingJoinCode,
                    onJoinCodeHandled = { pendingJoinCode = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        joinCodeFrom(intent)?.let { pendingJoinCode = it }
    }

    private fun joinCodeFrom(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.scheme != "yatramitra" || data.host != "join") return null
        return data.getQueryParameter("code")?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
    }
}

private data class TopLevelDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    TopLevelDestination("route-and-stops", "Route & Stops", Icons.Filled.Map),
    TopLevelDestination("itinerary", "Itinerary", Icons.Filled.ListAlt),
    TopLevelDestination("expenses", "Expenses", Icons.Filled.AccountBalanceWallet)
)

/** Not signed in -> [AuthScreen]. Signed in, no trip open -> [HomeScreen]. Trip open -> the
 *  3-tab [TripScaffold]. How editable an open trip is depends only on its live status and your
 *  role in it (see [TripRules]), never on how it was opened. */
@Composable
fun YatraMitraApp(pendingJoinCode: String? = null, onJoinCodeHandled: () -> Unit = {}) {
    var authChecked by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }
    var mustVerifyEmail by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (AuthRepository.isSignedIn && !AuthRepository.isEmailVerified) {
            // An email account that closed the app on the "Verify your email" screen used to walk
            // straight in on the next launch. The cached flag can be stale, so ask the server first.
            val verified = runCatching { AuthRepository.reloadAndCheckVerified() }.getOrDefault(false)
            mustVerifyEmail = !verified
            signedIn = verified
        } else {
            signedIn = AuthRepository.isSignedIn
        }
        authChecked = true
    }

    // Keeps users/{uid} current so "Add a travel companion" can match a real account by phone/
    // email (see TripRepository.findUserProfile) -- runs on every sign-in, including an already
    // signed-in cold start, so accounts created before this existed get backfilled too. Failure
    // here is silently non-fatal: it only affects account-matching, never the ability to sign in.
    LaunchedEffect(signedIn) {
        if (signedIn) {
            val uid = AuthRepository.currentUserId
            if (uid != null) {
                runCatching {
                    TripRepository.upsertUserProfile(
                        uid = uid,
                        name = AuthRepository.currentUserName,
                        email = AuthRepository.currentUserEmail,
                        phone = AuthRepository.currentUserPhone
                    )
                }
            }
        }
    }

    if (!authChecked) return

    if (!signedIn) {
        AuthScreen(
            onAuthenticated = { mustVerifyEmail = false; signedIn = true },
            startAtVerification = mustVerifyEmail
        )
    } else {
        SignedInApp(
            pendingJoinCode = pendingJoinCode,
            onJoinCodeHandled = onJoinCodeHandled,
            onSignedOut = {
                AuthRepository.signOut()
                signedIn = false
            }
        )
    }
}

/** Keeps the open trip across screen rotation and dark-mode switches, which recreate the activity
 *  and used to drop you back on the homepage. */
private val SessionSaver = listSaver<LocalStore.Session?, String>(
    save = { s -> if (s == null) emptyList() else listOf(s.tripCode, s.memberId, s.memberName) },
    restore = { list -> if (list.size == 3) LocalStore.Session(list[0], list[1], list[2]) else null }
)

@Composable
private fun SignedInApp(pendingJoinCode: String?, onJoinCodeHandled: () -> Unit, onSignedOut: () -> Unit) {
    var activeTrip by rememberSaveable(stateSaver = SessionSaver) { mutableStateOf<LocalStore.Session?>(null) }
    var homeNotice by remember { mutableStateOf<String?>(null) }

    val current = activeTrip
    if (current == null) {
        HomeScreen(
            onOpenTrip = { session -> homeNotice = null; activeTrip = session },
            onSignOut = onSignedOut,
            pendingJoinCode = pendingJoinCode,
            onJoinCodeHandled = onJoinCodeHandled,
            notice = homeNotice,
            onNoticeShown = { homeNotice = null }
        )
    } else {
        // key(): a different trip gets fresh state (tabs, listeners) instead of the previous trip's.
        key(current.tripCode) {
            TripScaffold(
                session = current,
                onHome = { notice -> homeNotice = notice; activeTrip = null },
                onSignedOut = onSignedOut
            )
        }
    }
}

@Composable
private fun TripScaffold(
    session: LocalStore.Session,
    onHome: (notice: String?) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    var membersLoaded by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var tripStatus by remember { mutableStateOf(TripStatus.ONGOING) }
    var metaLoaded by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(session.tripCode) {
        launch {
            TripRepository.observeMembers(session.tripCode).collect {
                members = it
                membersLoaded = true
            }
        }
        launch {
            TripRepository.observeTripMeta(session.tripCode).collect {
                if (!it.exists) {
                    // Someone deleted the trip while it was open here: leave instead of showing an
                    // empty trip whose every save fails.
                    onHome("\"${groupName.ifBlank { session.tripCode }}\" has been deleted.")
                    return@collect
                }
                groupName = it.groupName
                tripStatus = it.status
                metaLoaded = true
            }
        }
    }

    // The phone's Back button: first back to the Route tab, then out of the trip to the homepage.
    // Before, there was no handler at all, so Back closed the whole app from inside a trip.
    BackHandler {
        val startRoute = destinations.first().route
        val currentRoute = navController.currentDestination?.route // null while the trip is still loading
        if (currentRoute != null && currentRoute != startRoute) {
            navController.navigate(startRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            onHome(null)
        }
    }

    val currentRole = members.find { it.id == session.memberId }?.role ?: MemberRole.JOINER
    val isOrganizer = currentRole == MemberRole.ORGANIZER
    // Live, not decided once when the trip was opened: the trip stays editable until the Organizer
    // marks it completed, and locks (or unlocks again) on every phone the moment that changes.
    val isReadOnly = TripRules.isReadOnly(tripStatus)
    val canMarkCompleted = isOrganizer && tripStatus == TripStatus.ONGOING
    val canReopen = isOrganizer && tripStatus == TripStatus.COMPLETED
    val canDeleteTrip = tripStatus == TripStatus.COMPLETED || (tripStatus == TripStatus.ONGOING && isOrganizer)
    val onError: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    fun setStatus(status: TripStatus) {
        scope.launchSafely(onError, "Couldn't update the trip — check your internet connection.") {
            TripRepository.updateTripStatus(session.tripCode, status)
            // Keep this phone's homepage badge in step too (other phones refresh theirs on next open).
            AuthRepository.currentUserId?.let { uid ->
                runCatching {
                    TripRepository.recordTripAccess(uid, session.tripCode, groupName, session.memberId, currentRole, status)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TripTopBar(
                role = currentRole,
                memberName = session.memberName,
                tripStatus = tripStatus,
                canMarkCompleted = canMarkCompleted,
                canReopen = canReopen,
                canDeleteTrip = canDeleteTrip,
                onHome = { onHome(null) },
                onAvatarClick = { showProfile = true },
                onMarkCompleted = { setStatus(TripStatus.COMPLETED) },
                onReopen = { setStatus(TripStatus.ONGOING) },
                onDeleteTrip = { showDeleteConfirm = true },
                onInvite = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Join our trip on YatraMitra! If this shows up as a tappable link, use it to jump " +
                                "straight in (needs the app already installed): yatramitra://join?code=${session.tripCode}\n" +
                                "Otherwise, open the app and enter code: ${session.tripCode}"
                        )
                    }
                    context.startActivity(Intent.createChooser(send, "Share trip code"))
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination

                destinations.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (!membersLoaded || !metaLoaded) {
            // Wait for your role and the trip's status before drawing the tabs; otherwise the
            // Organizer briefly saw the Group Member (no editing) version of every screen.
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        NavHost(
            navController = navController,
            startDestination = "route-and-stops",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("route-and-stops") {
                TripPlannerScreen(
                    session = session,
                    members = members,
                    currentRole = currentRole,
                    isReadOnly = isReadOnly,
                    groupName = groupName,
                    onError = onError
                )
            }
            composable("itinerary") {
                ItineraryScreen(
                    session = session,
                    members = members,
                    currentRole = currentRole,
                    isReadOnly = isReadOnly,
                    onError = onError
                )
            }
            composable("expenses") {
                ExpensesScreen(session = session, isReadOnly = isReadOnly, onError = onError)
            }
        }
    }

    if (showProfile) {
        ProfileSheet(
            onDismiss = { showProfile = false },
            onSignOut = {
                showProfile = false
                onSignedOut()
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this trip?") },
            text = {
                Text(
                    "This permanently deletes \"${groupName.ifBlank { session.tripCode }}\" and all its data " +
                        "(route, itinerary, expenses) for everyone on the trip — this can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launchSafely(onError, "Couldn't delete the trip — check your internet connection.") {
                            TripRepository.deleteTrip(session.tripCode, AuthRepository.currentUserId)
                            onHome(null)
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

/** Shown above every tab once inside a trip: brand, a non-destructive Home action, an Invite
 *  action, the current member's avatar, their role badge (or a Completed badge once the trip is
 *  locked), and a live-sync indicator. */
@Composable
private fun TripTopBar(
    role: MemberRole,
    memberName: String,
    tripStatus: TripStatus,
    canMarkCompleted: Boolean,
    canReopen: Boolean,
    canDeleteTrip: Boolean,
    onHome: () -> Unit,
    onAvatarClick: () -> Unit,
    onMarkCompleted: () -> Unit,
    onReopen: () -> Unit,
    onDeleteTrip: () -> Unit,
    onInvite: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onHome) {
                    Icon(Icons.Filled.Home, contentDescription = "Home")
                }
                YatraMitraLogo(markSize = 32.dp, showTagline = false)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onInvite,
                    label = { Text("Invite") },
                    leadingIcon = {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimary,
                        leadingIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                Spacer(Modifier.width(Spacing.xs))
                InitialsAvatar(name = memberName, size = 32.dp, modifier = Modifier.clickable(onClick = onAvatarClick))
                if (canMarkCompleted || canReopen || canDeleteTrip) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Trip options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (canMarkCompleted) {
                                DropdownMenuItem(
                                    text = { Text("Mark as completed") },
                                    onClick = { showMenu = false; onMarkCompleted() }
                                )
                            }
                            if (canReopen) {
                                // Without this, marking a trip completed by mistake locked it forever.
                                DropdownMenuItem(
                                    text = { Text("Reopen trip for editing") },
                                    onClick = { showMenu = false; onReopen() }
                                )
                            }
                            if (canDeleteTrip) {
                                DropdownMenuItem(
                                    text = { Text("Delete trip") },
                                    onClick = { showMenu = false; onDeleteTrip() }
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs2))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        when {
                            tripStatus == TripStatus.COMPLETED -> "Completed · read-only"
                            role == MemberRole.ORGANIZER -> "Organizer View"
                            else -> "Group Member View"
                        }
                    )
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Live Sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
