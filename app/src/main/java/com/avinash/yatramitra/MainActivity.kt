package com.avinash.yatramitra

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
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
import com.avinash.yatramitra.data.TripRepository
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            YatraMitraTheme {
                YatraMitraApp()
            }
        }
    }
}

private data class TopLevelDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    TopLevelDestination("route-and-stops", "Route & Stops", Icons.Filled.Map),
    TopLevelDestination("itinerary", "Itinerary", Icons.Filled.ListAlt),
    TopLevelDestination("expenses", "Expenses", Icons.Filled.AccountBalanceWallet)
)

/** Not signed in -> [AuthScreen]. Signed in, no trip open -> [HomeScreen]. Trip open -> the
 *  3-tab [TripScaffold]. A trip opened from the homepage's recent-trips list is always read-only,
 *  regardless of role — freshly creating or joining one opens it fully editable as before. */
@Composable
fun YatraMitraApp() {
    var authChecked by remember { mutableStateOf(false) }
    var signedIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        signedIn = AuthRepository.isSignedIn
        authChecked = true
    }

    if (!authChecked) return

    if (!signedIn) {
        AuthScreen(onAuthenticated = { signedIn = true })
    } else {
        SignedInApp(onSignedOut = {
            AuthRepository.signOut()
            signedIn = false
        })
    }
}

private data class ActiveTrip(val session: LocalStore.Session, val isReadOnly: Boolean)

@Composable
private fun SignedInApp(onSignedOut: () -> Unit) {
    var activeTrip by remember { mutableStateOf<ActiveTrip?>(null) }

    val current = activeTrip
    if (current == null) {
        HomeScreen(
            onOpenTrip = { session, readOnly -> activeTrip = ActiveTrip(session, readOnly) },
            onSignOut = onSignedOut
        )
    } else {
        TripScaffold(
            session = current.session,
            isReadOnly = current.isReadOnly,
            onHome = { activeTrip = null },
            onSignedOut = onSignedOut
        )
    }
}

@Composable
private fun TripScaffold(
    session: LocalStore.Session,
    isReadOnly: Boolean,
    onHome: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    var groupName by remember { mutableStateOf("") }
    var tripStatus by remember { mutableStateOf(TripStatus.ONGOING) }
    var showProfile by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(session.tripCode) {
        launch { TripRepository.observeMembers(session.tripCode).collect { members = it } }
        launch {
            TripRepository.observeTripMeta(session.tripCode).collect {
                groupName = it.groupName
                tripStatus = it.status
            }
        }
    }

    val currentRole = members.find { it.id == session.memberId }?.role ?: MemberRole.JOINER
    val isOrganizer = currentRole == MemberRole.ORGANIZER
    val canMarkCompleted = isOrganizer && tripStatus == TripStatus.ONGOING
    val canDeleteTrip = tripStatus == TripStatus.COMPLETED || (tripStatus == TripStatus.ONGOING && isOrganizer)
    val onError: (String) -> Unit = { message -> scope.launch { snackbarHostState.showSnackbar(message) } }

    Scaffold(
        topBar = {
            TripTopBar(
                role = currentRole,
                memberName = session.memberName,
                isReadOnly = isReadOnly,
                tripStatus = tripStatus,
                canMarkCompleted = canMarkCompleted,
                canDeleteTrip = canDeleteTrip,
                onHome = onHome,
                onAvatarClick = { showProfile = true },
                onMarkCompleted = {
                    scope.launchSafely(onError, "Couldn't update the trip — check your internet connection.") {
                        TripRepository.updateTripStatus(session.tripCode, TripStatus.COMPLETED)
                    }
                },
                onDeleteTrip = { showDeleteConfirm = true },
                onInvite = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Join our trip on YatraMitra! Enter this code in the app: ${session.tripCode}"
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
                            onHome()
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

/** Shown above every tab once inside a trip: brand, a non-destructive Home action, an Invite
 *  action, the current member's avatar, their role badge (or a Read-only badge when browsing a
 *  past trip from the homepage), and a live-sync indicator. */
@Composable
private fun TripTopBar(
    role: MemberRole,
    memberName: String,
    isReadOnly: Boolean,
    tripStatus: TripStatus,
    canMarkCompleted: Boolean,
    canDeleteTrip: Boolean,
    onHome: () -> Unit,
    onAvatarClick: () -> Unit,
    onMarkCompleted: () -> Unit,
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
                if (canMarkCompleted || canDeleteTrip) {
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
                            tripStatus == TripStatus.COMPLETED -> "Completed"
                            isReadOnly -> "Read-only"
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
