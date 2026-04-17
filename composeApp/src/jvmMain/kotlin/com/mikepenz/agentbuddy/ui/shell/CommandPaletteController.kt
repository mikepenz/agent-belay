package com.mikepenz.agentbuddy.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Stable
class CommandPaletteController {
    var isOpen by mutableStateOf(false)
        private set

    /**
     * Set to `true` when a global (OS-level) hotkey is wired for the
     * palette. The in-window Compose handler checks this to avoid
     * double-toggling when the app window happens to be focused — if the
     * global hotkey is active it already fires regardless of focus.
     */
    var globalHotkeyActive by mutableStateOf(false)

    fun open() { isOpen = true }
    fun close() { isOpen = false }
    fun toggle() { isOpen = !isOpen }
}

val LocalCommandPaletteController = compositionLocalOf<CommandPaletteController> {
    error("CommandPaletteController not provided")
}

@Composable
fun rememberCommandPaletteController(): CommandPaletteController =
    remember { CommandPaletteController() }
