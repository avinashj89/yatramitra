package com.avinash.yatramitra

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.MemberRole
import com.avinash.yatramitra.ui.components.InitialsAvatar
import com.avinash.yatramitra.ui.components.YatraMitraLogo
import com.avinash.yatramitra.ui.screens.ExpensesScreen
import com.avinash.yatramitra.ui.screens.ItineraryScreen
import com.avinash.yatramitra.ui.screens.JoinTripScreen
import com.avinash.yatramitra.ui.screens.TripPlannerScreen
import com.avinash.yatramitra.ui.theme.Spacing
import com.avinash.yatramitra.ui.theme.YatraMitraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

/** Every tab is trip-scoped now (Organizer/Joiner roles, live sync), not just Expenses — so the
 *  whole app gates on having a trip session before showing the bottom-nav'd tabs at all. */
@Composable
fun YatraMitraApp() {
    val context = LocalContext.current
    var session by remember { mutableStateOf<LocalStore.Session?>(null) }
    var checked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        session = LocalStore.loadSession(context)
        checked = true
    }

    if (!checked) return

    val current = session
    if (current == null) {
        JoinTripScreen(onJoined = { code, memberId, name ->
            LocalStore.saveSession(context, code, memberId, name)
            session = LocalStore.Session(code, memberId, name)
        })
    } else {
        TripScaffold(
            session = current,
            onLeaveTrip = {
                LocalStore.clearSession(context)
                session = null
            }
        )
    }
}

@Composable
private fun TripScaffold(session: LocalStore.Session, onLeaveTrip: () -> Unit) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    var groupName by remember { mutableStateOf("") }

    LaunchedEffect(session.tripCode) {
        launch { TripRepository.observeMembers(session.tripCode).collect { members = it } }
        launch { TripRepository.observeTripMeta(session.tripCode).collect { groupName = it.groupName } }
    }

    val currentRole = members.find { it.id == session.memberId }?.role ?: MemberRole.JOINER

    Scaffold(
        topBar = {
            TripTopBar(
                role = currentRole,
                memberName = session.memberName,
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
                TripPlannerScreen(session = session, members = members, currentRole = currentRole)
            }
            composable("itinerary") {
                ItineraryScreen(session = session, members = members, currentRole = currentRole)
            }
            composable("expenses") { ExpensesScreen(session = session, onLeaveTrip = onLeaveTrip) }
        }
    }
}

/** Shown above every tab once inside a trip: brand, an Invite action, the current member's
 *  avatar, their role badge, and a live-sync indicator (Firestore already pushes updates to
 *  every open app — this is just a visual confirmation, not a separate mechanism). */
@Composable
private fun TripTopBar(role: MemberRole, memberName: String, onInvite: () -> Unit) {
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
            YatraMitraLogo(markSize = 32.dp, showTagline = false)
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
                InitialsAvatar(name = memberName, size = 32.dp)
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
                label = { Text(if (role == MemberRole.ORGANIZER) "Organizer View" else "Joiner View") }
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
