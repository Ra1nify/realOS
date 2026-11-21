package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shared slider persistence entry for keyboard settings pages.
 *
 * Contract:
 *  1. Dragging updates local state + live preview only, no write;
 *  2. One write [debounceMs] after the last change;
 *  3. One job per key: setting A then B right away keeps A's pending value;
 *  4. The caller flushes the current state before page disposal (see each page's
 *     `DisposableEffect`); `rememberCoroutineScope` is cancelled with the composition.
 */
class SliderPrefWriter(
    private val scope: CoroutineScope,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS
) {

    private val jobs = HashMap<String, Job>()

    /** Schedules a delayed write; the previous job for the same key is cancelled (last value wins). */
    fun submit(key: String, write: () -> Unit) {
        jobs.remove(key)?.cancel()
        jobs[key] = scope.launch {
            delay(debounceMs)
            // Catches write failures; an exception would cancel the composition scope.
            runCatching { write() }
        }
    }

    /** Drops all pending writes; called before page disposal, before flushing the current state. */
    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 250L
    }
}

/** Returns a writer bound to the current composition's lifecycle, for use inside a Composable. */
@Composable
fun rememberSliderPrefWriter(): SliderPrefWriter {
    val scope = rememberCoroutineScope()
    return remember(scope) { SliderPrefWriter(scope) }
}
