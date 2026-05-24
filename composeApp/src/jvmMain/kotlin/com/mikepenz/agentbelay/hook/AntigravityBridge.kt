package com.mikepenz.agentbelay.hook

interface AntigravityBridge {
    fun isRegistered(port: Int): Boolean
    fun register(port: Int)
    fun unregister(port: Int)
    fun isCapabilityHookRegistered(port: Int): Boolean
    fun registerCapabilityHook(port: Int, userPromptSubmit: Boolean, sessionStart: Boolean)
    fun unregisterCapabilityHook(port: Int)
}

object DefaultAntigravityBridge : AntigravityBridge {
    override fun isRegistered(port: Int): Boolean = AntigravityBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = AntigravityBridgeInstaller.register(port)
    override fun unregister(port: Int) = AntigravityBridgeInstaller.unregister(port)
    override fun isCapabilityHookRegistered(port: Int): Boolean = false
    override fun registerCapabilityHook(port: Int, userPromptSubmit: Boolean, sessionStart: Boolean) {}
    override fun unregisterCapabilityHook(port: Int) {}
}
