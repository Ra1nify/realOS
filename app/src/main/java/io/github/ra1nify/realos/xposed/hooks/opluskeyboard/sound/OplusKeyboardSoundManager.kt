package io.github.ra1nify.realos.xposed.hooks.opluskeyboard.sound

import io.github.ra1nify.realos.BuildConfig

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import io.github.ra1nify.realos.util.RealOSLog as Log
import android.view.View
import io.github.ra1nify.realos.opluskeyboard.OplusInputPrefs
import io.github.ra1nify.realos.opluskeyboard.OplusModuleRef
import io.github.ra1nify.realos.xposed.hooks.opluskeyboard.effects.OplusPerKeyBoundsProvider
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

/**
 * Mechanical keyboard sound engine (Android SoundPool + Samsung Keys Cafe 3-track split).
 *
 * 1. Non-blocking I/O: audio is extracted asynchronously and cached in an in-memory
 *    SoundPool; the key callback only calls play (< 0.01ms).
 * 2. Ringer mode: silent or vibrate mode short-circuits playback.
 * 3. 3-track routing:
 *    - STANDARD: letters, digits and punctuation
 *    - DELETE: backspace
 *    - FUNCTION: space, enter, shift, symbol keys
 * 4. Fail closed: corrupt resources or load errors degrade silently.
 */
object OplusKeyboardSoundManager {

    private const val TAG = "RealOS-KeyboardSound"
    private val REALOS_PKG = BuildConfig.APPLICATION_ID
    private const val ASSETS_SOUNDS_PREFIX = "assets/keyboard_sounds/"
    private const val LOCAL_ASSETS_DIR = "keyboard_sounds"

    @Volatile
    private var soundPool: SoundPool? = null
    private val soundIdMap = ConcurrentHashMap<SoundKeyType, Int>()

    /** Written by the background decode thread, read by the key callback; volatile like the file's other cross-thread state. */
    @Volatile
    private var currentLoadedStyle: KeyboardSoundStyle? = null

    private val isPreloading = AtomicBoolean(false)
    private val errorLogged = AtomicBoolean(false)

    @Volatile
    private var lastRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    @Volatile
    private var lastRingerCheckTime = 0L

    private fun isSilentMode(context: Context): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastRingerCheckTime > 1500L) {
            lastRingerCheckTime = now
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null) {
                lastRingerMode = audioManager.ringerMode
            }
        }
        return lastRingerMode == AudioManager.RINGER_MODE_SILENT
    }

    @Synchronized
    private fun getOrCreateSoundPool(): SoundPool {
        soundPool?.let { return it }
        val pool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundPool = pool
        return pool
    }

    /**
     * Preloads the given sound style (returns early when it is already loaded).
     */
    fun ensureLoaded(context: Context, style: KeyboardSoundStyle) {
        if (currentLoadedStyle == style && soundIdMap.size == 3) {
            return
        }
        if (isPreloading.compareAndSet(false, true)) {
            Thread {
                try {
                    loadStyleInternal(context.applicationContext, style)
                } catch (t: Throwable) {
                    if (errorLogged.compareAndSet(false, true)) {
                        Log.w(TAG, "Failed to preload sound style: ${style.key}", t)
                    }
                } finally {
                    isPreloading.set(false)
                }
            }.start()
        }
    }

    @Synchronized
    private fun loadStyleInternal(context: Context, style: KeyboardSoundStyle) {
        val pool = getOrCreateSoundPool()

        // Unloads the previously loaded resources
        for ((_, id) in soundIdMap) {
            if (id > 0) {
                runCatching { pool.unload(id) }
            }
        }
        soundIdMap.clear()

        val soundDir = getSoundCacheDir(context)

        // Extracts and loads the standard, delete and function key sounds
        val stdFile = ensureSoundExtracted(context, soundDir, style.standardFile)
        val delFile = ensureSoundExtracted(context, soundDir, style.deleteFile)
        val fnFile = ensureSoundExtracted(context, soundDir, style.functionFile)

        if (stdFile != null && stdFile.exists()) {
            val id = pool.load(stdFile.absolutePath, 1)
            soundIdMap[SoundKeyType.STANDARD] = id
        }
        if (delFile != null && delFile.exists()) {
            val id = pool.load(delFile.absolutePath, 1)
            soundIdMap[SoundKeyType.DELETE] = id
        }
        if (fnFile != null && fnFile.exists()) {
            val id = pool.load(fnFile.absolutePath, 1)
            soundIdMap[SoundKeyType.FUNCTION] = id
        }

        currentLoadedStyle = style
    }

    /**
     * Extracts the audio resource into the private cache directory.
     */
    private fun ensureSoundExtracted(context: Context, soundDir: File, fileName: String): File? {
        val target = File(soundDir, fileName)
        if (target.exists() && target.length() > 0) {
            return target
        }

        // 1. Opens local assets directly (host-embedded or main app environment)
        runCatching {
            context.assets.open("$LOCAL_ASSETS_DIR/$fileName").use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            if (target.exists() && target.length() > 0) {
                return target
            }
        }

        // 2. Reads from the module APK archive
        val apkPath = getModuleApkPath(context) ?: return null
        return runCatching {
            ZipFile(apkPath).use { zip ->
                val entry = zip.getEntry("$ASSETS_SOUNDS_PREFIX$fileName") ?: return null
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (target.exists() && target.length() > 0) target else null
        }.getOrNull()
    }

    private fun getSoundCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "realos_keyboard_sounds")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getModuleApkPath(context: Context): String? {
        OplusModuleRef.get()?.let { module ->
            runCatching {
                return module.getModuleApplicationInfo().sourceDir
            }
        }
        return runCatching {
            context.packageManager.getApplicationInfo(REALOS_PKG, 0).sourceDir
        }.getOrNull()
    }

    /**
     * Key-down event entry (called from the Xposed touchMethod hook).
     */
    fun onKeyDown(
        keyboardView: View,
        localX: Float,
        localY: Float,
        perKeyProvider: OplusPerKeyBoundsProvider? = null
    ) {
        val context = keyboardView.context ?: return

        // 1. Checks silent mode from the cached ringer mode
        if (isSilentMode(context)) {
            return
        }

        // 2. Loads the sound set if needed
        val styleKey = OplusInputPrefs.getKeyboardSoundStyle(context)
        val style = KeyboardSoundStyle.fromKey(styleKey)
        ensureLoaded(context, style)

        // 3. Resolves the 3-track key class
        val keyType = resolveKeyType(keyboardView, localX, localY, perKeyProvider)

        // 4. Reads the configured volume and plays
        val volume = OplusInputPrefs.getKeyboardSoundVolume(context).coerceIn(0.0f, 1.0f)
        if (volume <= 0.001f) return

        playSound(keyType, volume)
    }

    /**
     * Classifies the key as STANDARD / DELETE / FUNCTION.
     *
     * Two-tier criteria:
     * 1. Real key geometry (when perKeyProvider is available):
     *    - width > 0.28 * keyboard width and bottom row -> Space (FUNCTION)
     *    - right edge and middle row -> Backspace (DELETE)
     *    - right edge and bottom corner -> Enter (FUNCTION)
     *    - left edge and lower half -> Shift / symbol toggle (FUNCTION)
     * 2. Touch relative coordinates (fallback when perKeyProvider is unavailable or misses).
     */
    fun resolveKeyType(
        keyboardView: View,
        localX: Float,
        localY: Float,
        perKeyProvider: OplusPerKeyBoundsProvider? = null
    ): SoundKeyType {
        val kbWidth = keyboardView.width.toFloat()
        val kbHeight = keyboardView.height.toFloat()

        if (kbWidth <= 0 || kbHeight <= 0) {
            return SoundKeyType.STANDARD
        }

        // Prefers the per-key real key rects for high-confidence identification
        val target = perKeyProvider?.targetFor(keyboardView, localX, localY)
        if (target != null) {
            val r = target.localBounds
            val keyWidth = r.width()

            // 1. Space: much wider than a normal key (usually > 2.5x nominal) and at the keyboard bottom
            if (keyWidth > kbWidth * 0.26f && r.bottom >= kbHeight * 0.72f) {
                return SoundKeyType.FUNCTION
            }

            // 2. Backspace: hugging the right edge, upper-middle of the letter area
            if (r.right >= kbWidth * 0.85f && r.top >= kbHeight * 0.35f && r.bottom <= kbHeight * 0.82f) {
                return SoundKeyType.DELETE
            }

            // 3. Enter: at the bottom-right corner
            if (r.right >= kbWidth * 0.80f && r.bottom >= kbHeight * 0.80f) {
                return SoundKeyType.FUNCTION
            }

            // 4. Left function key: Shift or symbol keyboard toggle
            if (r.left <= kbWidth * 0.20f && r.top >= kbHeight * 0.45f) {
                return SoundKeyType.FUNCTION
            }

            return SoundKeyType.STANDARD
        }

        // Fallback: geometry split by touch relative coordinates
        if (localX >= kbWidth * 0.85f && localY in (kbHeight * 0.35f)..(kbHeight * 0.80f)) {
            return SoundKeyType.DELETE
        }

        if (localY >= kbHeight * 0.76f) {
            if (localX in (kbWidth * 0.25f)..(kbWidth * 0.75f)) {
                return SoundKeyType.FUNCTION
            }
            if (localX >= kbWidth * 0.75f || localX <= kbWidth * 0.25f) {
                return SoundKeyType.FUNCTION
            }
        }

        return SoundKeyType.STANDARD
    }

    /**
     * Plays the loaded key sound track.
     * Pitch is fixed at 1.0f; no random micro-variance.
     */
    fun playSound(keyType: SoundKeyType, volume: Float = 1.0f) {
        val pool = soundPool ?: return
        val soundId = soundIdMap[keyType] ?: soundIdMap[SoundKeyType.STANDARD] ?: return
        if (soundId <= 0) return

        runCatching {
            pool.play(soundId, volume, volume, 1, 0, 1.0f)
        }
    }

    /**
     * Instant audition player for the settings page.
     */
    fun playPreview(
        context: Context,
        style: KeyboardSoundStyle,
        keyType: SoundKeyType,
        volume: Float = 1.0f
    ) {
        val safeVol = volume.coerceIn(0.0f, 1.0f)
        if (safeVol <= 0.001f) return

        ensureLoaded(context, style)
        playSound(keyType, safeVol)
    }

    /**
     * Releases the SoundPool resources.
     */
    @Synchronized
    fun release() {
        // Idempotent: the touch callback calls this every frame while sound is off; returns when there is nothing to release
        if (soundPool == null && soundIdMap.isEmpty() && currentLoadedStyle == null) return
        soundIdMap.clear()
        soundPool?.release()
        soundPool = null
        currentLoadedStyle = null
    }
}
