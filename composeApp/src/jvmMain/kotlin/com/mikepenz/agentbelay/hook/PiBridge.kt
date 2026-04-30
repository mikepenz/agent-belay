package com.mikepenz.agentbelay.hook

/**
 * Thin interface over [PiBridgeInstaller] so settings view-model tests can
 * exercise registration flows without touching the real home directory.
 */
interface PiBridge {
    fun isRegistered(port: Int): Boolean
    fun register(port: Int)
    fun unregister(port: Int)
}

/** Production-only delegate to the [PiBridgeInstaller] singleton. */
object DefaultPiBridge : PiBridge {
    override fun isRegistered(port: Int): Boolean = PiBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = PiBridgeInstaller.register(port)
    override fun unregister(port: Int) = PiBridgeInstaller.unregister(port)
}
