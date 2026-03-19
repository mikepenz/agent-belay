package com.mikepenz.agentapprover.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, DARK, LIGHT }

@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val serverPort: Int = 19532,
    val alwaysOnTop: Boolean = true,
    val defaultTimeoutSeconds: Int = 240,
    val startOnBoot: Boolean = false,
    val riskAnalysisEnabled: Boolean = true,
    val riskAnalysisModel: String = "haiku",
    val riskAnalysisCustomPrompt: String = "",
    val autoApproveRisk1: Boolean = false,
    val autoDenyRisk5: Boolean = false,
    val windowX: Int? = null,
    val windowY: Int? = null,
    val windowWidth: Int? = null,
    val windowHeight: Int? = null,
)
