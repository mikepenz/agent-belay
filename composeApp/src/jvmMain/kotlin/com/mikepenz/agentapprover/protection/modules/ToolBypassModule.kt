package com.mikepenz.agentapprover.protection.modules

import com.mikepenz.agentapprover.model.HookInput
import com.mikepenz.agentapprover.model.ProtectionHit
import com.mikepenz.agentapprover.model.ProtectionMode
import com.mikepenz.agentapprover.protection.CommandParser
import com.mikepenz.agentapprover.protection.ProtectionModule
import com.mikepenz.agentapprover.protection.ProtectionRule

object ToolBypassModule : ProtectionModule {
    override val id = "tool_bypass"
    override val name = "Tool-Switching Bypass Detection"
    override val description = "Detects when Bash is used to write files, bypassing Write/Edit tool controls."
    override val corrective = false
    override val defaultMode = ProtectionMode.ASK
    override val applicableTools = setOf("Bash")

    override val rules: List<ProtectionRule> = listOf(
        SedInline,
        PerlInline,
        PythonFileWrite,
        EchoRedirect,
        TeeWrite,
        BashHeredoc,
    )

    private fun hit(ruleId: String, message: String) = ProtectionHit(
        moduleId = id,
        ruleId = ruleId,
        message = message,
        mode = defaultMode,
    )

    private object SedInline : ProtectionRule {
        override val id = "sed_inline"
        override val name = "sed -i in-place editing"
        override val description = "Detects sed -i in-place file editing. Use the Edit/Write tool instead."
        private val pattern = Regex("""\bsed\s+-i\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (pattern.containsMatchIn(cmd)) {
                return hit(id, "sed -i in-place editing detected. Use the Edit/Write tool instead.")
            }
            return null
        }
    }

    private object PerlInline : ProtectionRule {
        override val id = "perl_inline"
        override val name = "perl -i in-place editing"
        override val description = "Detects perl -i or perl -pi -e in-place file editing. Use the Edit/Write tool instead."
        private val pattern = Regex("""\bperl\s+(-[a-zA-Z]*i|-pi\s+-e)\b""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (pattern.containsMatchIn(cmd)) {
                return hit(id, "perl in-place editing detected. Use the Edit/Write tool instead.")
            }
            return null
        }
    }

    private object PythonFileWrite : ProtectionRule {
        override val id = "python_file_write"
        override val name = "python -c file write"
        override val description = "Detects python -c with open() and write(). Use the Edit/Write tool instead."
        private val pythonCPattern = Regex("""\bpython[23]?\s+-c\s""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (!pythonCPattern.containsMatchIn(cmd)) return null
            if (cmd.contains("open(") && cmd.contains("write")) {
                return hit(id, "python -c file write detected. Use the Edit/Write tool instead.")
            }
            return null
        }
    }

    private object EchoRedirect : ProtectionRule {
        override val id = "echo_redirect"
        override val name = "echo/printf redirect to file"
        override val description = "Detects echo or printf redirected to a file. Use the Edit/Write tool instead."
        private val pattern = Regex("""\b(echo|printf)\b.*>\s*(\S+)""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = pattern.find(cmd) ?: return null
            val target = match.groupValues[2]
            if (target == "/dev/null") return null
            return hit(id, "echo/printf redirect to file detected. Use the Edit/Write tool instead.")
        }
    }

    private object TeeWrite : ProtectionRule {
        override val id = "tee_write"
        override val name = "tee to file"
        override val description = "Detects tee writing to a file. Use the Edit/Write tool instead."
        private val pattern = Regex("""\btee\s+(\S+)""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            val match = pattern.find(cmd) ?: return null
            val target = match.groupValues[1]
            if (target == "/dev/null") return null
            return hit(id, "tee to file detected. Use the Edit/Write tool instead.")
        }
    }

    private object BashHeredoc : ProtectionRule {
        override val id = "bash_heredoc"
        override val name = "heredoc file write"
        override val description = "Detects cat or tee with heredoc writing to a file. Use the Edit/Write tool instead."
        private val pattern = Regex("""\b(cat|tee)\s+>\s*\S+.*<<|(\bcat|\btee)\s+\S+.*<<""")

        override fun evaluate(hookInput: HookInput): ProtectionHit? {
            val cmd = CommandParser.bashCommand(hookInput) ?: return null
            if (pattern.containsMatchIn(cmd)) {
                return hit(id, "heredoc file write detected. Use the Edit/Write tool instead.")
            }
            return null
        }
    }
}
