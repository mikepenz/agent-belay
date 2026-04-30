package com.mikepenz.agentbelay.testutil

import java.nio.file.Files
import java.nio.file.Path

/**
 * Sets `user.home` to a fresh temp directory for the duration of [block],
 * then restores the original. Use only in tests; concurrent use across tests
 * will race because `user.home` is JVM-global.
 *
 * The installer code under `hook/` reads `System.getProperty("user.home")`
 * lazily on every call, so swapping it for a test is enough to redirect all
 * filesystem writes ([CopilotBridgeInstaller], [HookRegistrar],
 * [OpenCodeBridgeInstaller]) into the temp dir without code changes.
 */
inline fun <T> withTempHome(block: (Path) -> T): T {
    val original = System.getProperty("user.home")
    val tmp = Files.createTempDirectory("belay-home-")
    System.setProperty("user.home", tmp.toString())
    try {
        return block(tmp)
    } finally {
        System.setProperty("user.home", original)
        tmp.toFile().deleteRecursively()
    }
}
