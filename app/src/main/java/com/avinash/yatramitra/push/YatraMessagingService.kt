package com.avinash.yatramitra.push

import com.avinash.yatramitra.data.AuthRepository
import com.avinash.yatramitra.data.TripRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the pushes sent by the server function (functions/index.js). They are data-only
 * messages, so this runs whether the app is open or not and builds the notification itself.
 */
class YatraMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Firebase replaced this phone's address; keep the signed-in account pointed at it.
        val uid = AuthRepository.currentUserId ?: return
        scope.launch { runCatching { TripRepository.saveDeviceToken(uid, token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val tripCode = data["tripCode"] ?: return
        val title = data["title"] ?: message.notification?.title ?: "YatraMitra"
        val body = data["body"] ?: message.notification?.body ?: return
        // Only for the account signed in right now (the server addresses one account per phone,
        // but a push can still be in flight while someone signs out).
        val forUid = data["uid"]
        if (forUid != null && forUid != AuthRepository.currentUserId) return
        Notifications.show(applicationContext, tripCode, data["type"].orEmpty(), title, body)
    }
}
