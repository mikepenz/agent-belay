package com.mikepenz.agentapprover.protection

import com.mikepenz.agentapprover.model.HookInput
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object CommandParser {
    fun bashCommand(hookInput: HookInput): String? {
        if (hookInput.toolName != "Bash") return null
        val cmd = hookInput.toolInput["command"]
        return (cmd as? JsonPrimitive)?.contentOrNull
    }

    fun filePath(hookInput: HookInput): String? {
        val path = hookInput.toolInput["file_path"]
        return (path as? JsonPrimitive)?.contentOrNull
    }

    fun extractPaths(command: String): List<String> {
        val pattern = Regex("""(?:^|\s)([./~][^\s;|&<>]+)""")
        return pattern.findAll(command).map { it.groupValues[1] }.toList()
    }

    fun countChainedCommands(command: String): Int {
        val separators = Regex("""[;]|&&|\|\|""")
        return separators.findAll(command).count() + 1
    }
}
