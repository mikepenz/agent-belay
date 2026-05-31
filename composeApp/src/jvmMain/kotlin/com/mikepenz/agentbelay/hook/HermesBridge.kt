package com.mikepenz.agentbelay.hook

interface HermesBridge {
    fun isRegistered(port: Int): Boolean
    fun register(port: Int)
    fun unregister(port: Int)
    fun isCapabilityHookRegistered(port: Int): Boolean
    fun registerCapabilityHook(port: Int, userPromptSubmit: Boolean, sessionStart: Boolean)
    fun unregisterCapabilityHook(port: Int)
}

object DefaultHermesBridge : HermesBridge {
    override fun isRegistered(port: Int): Boolean = HermesBridgeInstaller.isRegistered(port)
    override fun register(port: Int) = HermesBridgeInstaller.register(port)
    override fun unregister(port: Int) = HermesBridgeInstaller.unregister(port)
    override fun isCapabilityHookRegistered(port: Int): Boolean = HermesBridgeInstaller.isCapabilityHookRegistered(port)
    override fun registerCapabilityHook(port: Int, userPromptSubmit: Boolean, sessionStart: Boolean) =
        HermesBridgeInstaller.registerCapabilityHook(port, userPromptSubmit, sessionStart)
    override fun unregisterCapabilityHook(port: Int) = HermesBridgeInstaller.unregisterCapabilityHook(port)
}
