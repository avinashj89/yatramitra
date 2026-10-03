package com.avinash.yatramitra.push

import com.avinash.yatramitra.data.TripRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/** Links this phone's push address to the signed-in account, and unlinks it on sign-out. */
object PushTokens {

    suspend fun register(uid: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        TripRepository.saveDeviceToken(uid, token)
    }

    suspend fun unregister() {
        val token = FirebaseMessaging.getInstance().token.await()
        TripRepository.removeDeviceToken(token)
    }
}
