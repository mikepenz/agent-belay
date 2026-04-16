package com.mikepenz.agentapprover.storage

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.AppSettings
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SettingsStorage(private val dataDir: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val file get() = File(dataDir, "settings.json")

    fun load(): AppSettings {
        return try {
            val f = file
            if (!f.exists()) return AppSettings()
            json.decodeFromString<AppSettings>(f.readText())
        } catch (e: Exception) {
            Logger.w("SettingsStorage") { "Failed to load settings, using defaults: ${e.message}" }
            AppSettings()
        }
    }

    fun save(settings: AppSettings) {
        try {
            val dir = File(dataDir)
            if (!dir.exists()) dir.mkdirs()
            val tmp = File(dataDir, "settings.json.tmp")
            tmp.writeText(json.encodeToString(AppSettings.serializer(), settings))
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Logger.e("SettingsStorage") { "Failed to save settings: ${e.message}" }
        }
    }
}
