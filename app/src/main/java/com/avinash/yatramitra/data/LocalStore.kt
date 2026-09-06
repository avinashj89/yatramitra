package com.avinash.yatramitra.data

/** Which trip is currently open and who you are within it. Purely in-memory navigation state now
 *  (not persisted) — Firebase Auth already keeps you signed in across app restarts natively, and
 *  Firestore's `users/{uid}/trips` index is the durable record of which trips you're part of, so
 *  there's nothing left worth saving locally. Restarting the app returns you to the homepage,
 *  where every trip you've touched is still one tap away. */
object LocalStore {
    data class Session(val tripCode: String, val memberId: String, val memberName: String)
}
