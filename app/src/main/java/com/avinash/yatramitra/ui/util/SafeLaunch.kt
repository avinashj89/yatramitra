package com.avinash.yatramitra.ui.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Launches a coroutine that reports failures via [onError] instead of letting an uncaught
 *  exception crash the whole app. Every Firestore-backed UI action should go through this rather
 *  than a bare `scope.launch { ... }`. Returns the [Job], same as a plain `launch`, so callers that
 *  need to cancel a previous debounced call (e.g. `saveJob?.cancel()`) still can.
 *
 *  [CancellationException] is rethrown, never reported as a failure — it fires whenever a caller
 *  cancels a superseded debounced job (e.g. every keystroke before the last one in an
 *  auto-save field), which is normal control flow, not an error. */
fun CoroutineScope.launchSafely(
    onError: (String) -> Unit,
    errorMessage: String = "Something went wrong — check your internet connection and try again.",
    block: suspend () -> Unit
): Job = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(errorMessage)
    }
}
