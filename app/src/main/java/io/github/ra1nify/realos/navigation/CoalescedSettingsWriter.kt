package io.github.ra1nify.realos.navigation

import android.content.ContentResolver
import android.provider.Settings
import io.github.ra1nify.realos.util.RealOSLog as Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coalescing writer for `Settings.System` values that change continuously.
 *
 * A slider drag fires `onValueChange` once per frame, and `Settings.System.putX` is a synchronous
 * binder round-trip to system_server. Writing per frame therefore saturates the main thread and the
 * drag stops following the finger, while a tap still lands because it produces a single write.
 *
 * This writer keeps only the latest block per key and drains them on one background thread, so a
 * drag costs one IPC per drain instead of one per frame, and the final value always lands.
 *
 * Writes and notifications are queued separately and drained in two phases, so a notification always
 * observes the values written by the same drain.
 *
 * Discrete controls (switches, pickers) keep writing synchronously: they fire once per interaction
 * and the caller may read the value straight back.
 */
internal object CoalescedSettingsWriter {

    private const val TAG = "CoalescedSettings"

    private val lock = Any()
    private val pendingWrites = LinkedHashMap<String, () -> Unit>()
    private val pendingNotifies = LinkedHashMap<String, () -> Unit>()
    private val scheduled = AtomicBoolean(false)

    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "realos-settings-writer").apply { isDaemon = true }
        }

    /** Queues a `Settings.System` string write; the latest value per [key] wins. */
    fun putString(resolver: ContentResolver, key: String, value: String) =
        submitWrite(key) { Settings.System.putString(resolver, key, value) }

    /** Queues a `Settings.System` int write; the latest value per [key] wins. */
    fun putInt(resolver: ContentResolver, key: String, value: Int) =
        submitWrite(key) { Settings.System.putInt(resolver, key, value) }

    /** Queues a write block; the latest block per [key] wins. */
    fun submitWrite(key: String, write: () -> Unit) {
        synchronized(lock) {
            pendingWrites.remove(key)
            pendingWrites[key] = write
        }
        schedule()
    }

    /** Queues a notification block; it runs after every pending write of the same drain. */
    fun submitNotify(key: String, notify: () -> Unit) {
        synchronized(lock) {
            pendingNotifies.remove(key)
            pendingNotifies[key] = notify
        }
        schedule()
    }

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        executor.execute {
            try {
                while (true) {
                    val writes: List<() -> Unit>
                    val notifies: List<() -> Unit>
                    synchronized(lock) {
                        if (pendingWrites.isEmpty() && pendingNotifies.isEmpty()) return@execute
                        writes = ArrayList(pendingWrites.values)
                        notifies = ArrayList(pendingNotifies.values)
                        pendingWrites.clear()
                        pendingNotifies.clear()
                    }
                    for (write in writes) {
                        runCatching { write() }.onFailure {
                            Log.w(TAG, "coalesced write failed: ${it.message}")
                        }
                    }
                    for (notify in notifies) {
                        runCatching { notify() }.onFailure {
                            Log.w(TAG, "coalesced notify failed: ${it.message}")
                        }
                    }
                    if (synchronized(lock) { pendingWrites.isEmpty() && pendingNotifies.isEmpty() }) {
                        return@execute
                    }
                }
            } finally {
                scheduled.set(false)
                // Re-arm if anything arrived between the last check and the flag reset.
                if (synchronized(lock) { pendingWrites.isNotEmpty() || pendingNotifies.isNotEmpty() }) {
                    schedule()
                }
            }
        }
    }
}
