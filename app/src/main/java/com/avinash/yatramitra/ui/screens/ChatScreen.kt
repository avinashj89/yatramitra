package com.avinash.yatramitra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.avinash.yatramitra.data.AuthRepository
import com.avinash.yatramitra.data.GroupChat
import com.avinash.yatramitra.data.LocalStore
import com.avinash.yatramitra.data.TripRepository
import com.avinash.yatramitra.model.ChatMessage
import com.avinash.yatramitra.model.ItinerarySuggestion
import com.avinash.yatramitra.model.Member
import com.avinash.yatramitra.model.RouteSuggestion
import com.avinash.yatramitra.push.Notifications
import com.avinash.yatramitra.ui.theme.Spacing
import com.avinash.yatramitra.ui.util.launchSafely
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The trip's Group chat: everyone on the trip posts here directly and sees every message live
 * (this replaces the old "suggest a change" boxes that the Organizer had to accept or dismiss).
 * Everyone else with the trip gets each message as a phone notification.
 */
@Composable
fun ChatScreen(
    session: LocalStore.Session,
    members: List<Member>,
    isReadOnly: Boolean,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var chat by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var routeSuggestions by remember { mutableStateOf<List<RouteSuggestion>>(emptyList()) }
    var itinerarySuggestions by remember { mutableStateOf<List<ItinerarySuggestion>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var showSos by remember { mutableStateOf(false) }

    LaunchedEffect(session.tripCode) {
        launch { TripRepository.observeChat(session.tripCode).collect { chat = it } }
        launch { TripRepository.observeRouteSuggestions(session.tripCode).collect { routeSuggestions = it } }
        launch { TripRepository.observeItinerarySuggestions(session.tripCode).collect { itinerarySuggestions = it } }
    }

    // While this chat is on screen, its messages don't also pop up as notifications.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(session.tripCode, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> Notifications.visibleChatTripCode = session.tripCode
                Lifecycle.Event.ON_PAUSE -> Notifications.visibleChatTripCode = null
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            Notifications.visibleChatTripCode = session.tripCode
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            Notifications.visibleChatTripCode = null
        }
    }

    val messages = remember(chat, routeSuggestions, itinerarySuggestions) {
        GroupChat.timeline(chat, routeSuggestions, itinerarySuggestions)
    }
    val myName = members.find { it.id == session.memberId }?.name ?: session.memberName
    val listState = rememberLazyListState()

    // Keep the newest message in view as messages arrive.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send() {
        val text = GroupChat.cleanMessage(draft) ?: return
        // Cleared at once: the message appears in the list straight away, even before the
        // server confirms it, and a second tap can't post it twice.
        draft = ""
        scope.launchSafely(
            onError = { message ->
                if (draft.isBlank()) draft = text
                onError(message)
            },
            errorMessage = "Couldn't send your message — check your internet connection."
        ) {
            TripRepository.sendChatMessage(session.tripCode, session.memberId, myName, AuthRepository.currentUserId, text)
        }
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        ) {
            Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Group chat", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${members.size} on this trip · everyone gets a notification",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Always available, even on a completed trip: an emergency doesn't check trip status.
            SosButton(onClick = { showSos = true })
        }
        HorizontalDivider()

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "No messages yet. Say hello, share a plan, or suggest a stop — everyone on the trip sees it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.md)
                    )
                }
            }
            itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
                val isMine = message.authorMemberId == session.memberId
                if (message.sos != null) {
                    SosBubble(
                        message = message,
                        isMine = isMine,
                        authorPhone = members.find { it.id == message.authorMemberId }?.phone
                    )
                } else {
                    ChatBubble(
                        message = message,
                        isMine = isMine,
                        showAuthor = GroupChat.showsAuthor(messages.getOrNull(index - 1), message)
                    )
                }
            }
        }

        HorizontalDivider()
        if (isReadOnly) {
            Text(
                "This trip is completed, so the chat is closed. The Organizer can reopen the trip from the ⋮ menu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= GroupChat.MAX_LENGTH) draft = it },
                    placeholder = { Text("Message the group…") },
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = ::send, enabled = draft.isNotBlank()) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSos) {
        SosSheet(session = session, myName = myName, onDismiss = { showSos = false })
    }
}

/** A small pointer from the Route and Itinerary tabs to the Group chat tab. */
@Composable
fun GroupChatHint(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(Spacing.sm)
    ) {
        Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, isMine: Boolean, showAuthor: Boolean) {
    val time = remember(message.createdAtMillis) {
        if (message.createdAtMillis > 0) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(message.createdAtMillis))
        } else {
            ""
        }
    }
    Column(
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth().padding(top = if (showAuthor) 8.dp else 0.dp)
    ) {
        if (showAuthor && !isMine) {
            Text(
                message.authorName.ifBlank { "Someone" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            if (time.isNotBlank()) {
                Text(
                    time,
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        .copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
