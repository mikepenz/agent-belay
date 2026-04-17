package com.mikepenz.agentbuddy.app

import co.touchlab.kermit.Logger
import io.github.kdroidfilter.nucleus.globalhotkey.GlobalHotKeyManager
import io.github.kdroidfilter.nucleus.globalhotkey.HotKeyModifier
import io.github.kdroidfilter.nucleus.globalhotkey.plus
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * Thin wrapper around Nucleus' [GlobalHotKeyManager] that owns the app-level
 * command-palette hotkey (⌘K on macOS, Ctrl+K on Windows/Linux).
 *
 * Global hotkeys fire regardless of window focus, so the user can summon the
 * command palette even when Agent Buddy is in the background. On platforms
 * where the native subsystem isn't available (e.g. a Linux setup without X11
 * nor a registered GlobalShortcuts portal), [tryRegister] returns `false` and
 * the caller should fall back to the in-window Compose handler.
 *
 * The Nucleus callback runs on a native background thread, so we marshal the
 * user-supplied action onto the AWT EDT before touching Compose state.
 */
class GlobalHotkeyController {
    private val log = Logger.withTag("GlobalHotkeyController")

    @Volatile
    private var handle: Long = -1L

    @Volatile
    private var initialized: Boolean = false

    /**
     * Attempt to register the command-palette hotkey. Returns `true` on
     * success. When this returns `false`, callers should rely on the Compose
     * in-window fallback.
     *
     * [onTrigger] is always invoked on the AWT event dispatch thread.
     */
    fun tryRegister(onTrigger: () -> Unit): Boolean {
        if (!GlobalHotKeyManager.isAvailable) {
            log.i { "Global hotkeys not available on this platform; falling back to in-window handler" }
            return false
        }
        if (!initialized) {
            if (!GlobalHotKeyManager.initialize()) {
                log.w { "GlobalHotKeyManager.initialize() failed: ${GlobalHotKeyManager.lastError}" }
                return false
            }
            initialized = true
        }

        val isMac = System.getProperty("os.name").lowercase().contains("mac")
        // HotKeyModifier.plus(HotKeyModifier) → Int exposes the bitmask; the
        // enum's `nativeFlag` itself is internal. Int.plus(HotKeyModifier) is
        // a public extension, so `0 + META` yields the single-modifier mask.
        val modifiers: Int = if (isMac) 0 + HotKeyModifier.META else 0 + HotKeyModifier.CONTROL

        val id = GlobalHotKeyManager.register(
            keyCode = KeyEvent.VK_K,
            modifiers = modifiers,
        ) { _, _ ->
            SwingUtilities.invokeLater { onTrigger() }
        }
        if (id < 0L) {
            log.w { "Failed to register global hotkey: ${GlobalHotKeyManager.lastError}" }
            return false
        }
        handle = id
        log.i { "Registered global hotkey ${if (isMac) "Cmd+K" else "Ctrl+K"} (handle=$id)" }
        return true
    }

    /** Unregister the hotkey and shut down the native subsystem. Safe to call more than once. */
    fun shutdown() {
        if (handle >= 0L) {
            GlobalHotKeyManager.unregister(handle)
            handle = -1L
        }
        if (initialized) {
            GlobalHotKeyManager.shutdown()
            initialized = false
        }
    }

}
