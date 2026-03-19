package com.mikepenz.agentapprover.storage

import co.touchlab.kermit.Logger
import com.mikepenz.agentapprover.model.ApprovalResult
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class HistoryStorage(
    private val dataDir: String,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 10_000L,
) {

    companion object {
        const val MAX_ENTRIES = 250
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val serializer = ListSerializer(ApprovalResult.serializer())
    private val file get() = File(dataDir, "history.json")

    private var pendingResults: List<ApprovalResult>? = null
    private var debounceJob: Job? = null

    fun load(): List<ApprovalResult> {
        return try {
            val f = file
            if (!f.exists()) return emptyList()
            json.decodeFromString(serializer, f.readText())
        } catch (e: Exception) {
            Logger.w("HistoryStorage") { "Failed to load history: ${e.message}" }
            try {
                val backup = File(dataDir, "history.json.bak")
                file.copyTo(backup, overwrite = true)
                Logger.w("HistoryStorage") { "Backed up corrupted history to ${backup.absolutePath}" }
            } catch (backupEx: Exception) {
                Logger.e("HistoryStorage") { "Failed to backup history: ${backupEx.message}" }
            }
            emptyList()
        }
    }

    @Synchronized
    fun save(results: List<ApprovalResult>) {
        pendingResults = results
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            flushInternal()
        }
    }

    @Synchronized
    fun shutdown() {
        debounceJob?.cancel()
        debounceJob = null
        val results = pendingResults ?: return
        pendingResults = null
        writeToDisk(results)
    }

    private fun flushInternal() {
        synchronized(this) {
            val results = pendingResults ?: return
            pendingResults = null
            writeToDisk(results)
        }
    }

    private fun writeToDisk(results: List<ApprovalResult>) {
        try {
            val dir = File(dataDir)
            if (!dir.exists()) dir.mkdirs()
            val capped = if (results.size > MAX_ENTRIES) results.takeLast(MAX_ENTRIES) else results
            val tmp = File(dataDir, "history.json.tmp")
            tmp.writeText(json.encodeToString(serializer, capped))
            tmp.renameTo(file)
        } catch (e: Exception) {
            Logger.e("HistoryStorage") { "Failed to save history: ${e.message}" }
        }
    }
}
