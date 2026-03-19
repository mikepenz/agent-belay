package com.mikepenz.agentapprover.ui.approvals

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentapprover.model.*
import com.mikepenz.agentapprover.ui.theme.AgentApproverTheme
import com.mikepenz.markdown.m3.Markdown
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

@Composable
fun DefaultCard(
    request: ApprovalRequest,
    onApprove: (String?) -> Unit,
    onDeny: (String) -> Unit,
) {
    var denyFeedback by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Session ID
        Text(
            text = "Session: ${request.hookInput.sessionId}",
            fontSize = 10.sp,
            color = Color.Gray,
        )

        Spacer(Modifier.height(8.dp))

        // Content area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E1E1E),
            shape = MaterialTheme.shapes.small,
        ) {
            Box(modifier = Modifier.padding(8.dp)) {
                val content = formatContent(request)
                Markdown(content = content)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Deny feedback input
        OutlinedTextField(
            value = denyFeedback,
            onValueChange = { denyFeedback = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Denial reason (optional)", fontSize = 12.sp) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
        )

        Spacer(Modifier.height(8.dp))

        // Buttons: hide Approve when denial reason is entered
        val hasDenyText = denyFeedback.isNotBlank()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (hasDenyText) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { onDeny(denyFeedback) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Deny")
                }
            } else {
                OutlinedButton(
                    onClick = { onDeny("") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Deny")
                }
                Button(
                    onClick = { onApprove(null) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Approve")
                }
            }
        }
    }
}

private fun formatContent(request: ApprovalRequest): String {
    val toolName = request.hookInput.toolName.lowercase()
    val input = request.hookInput.toolInput
    return when {
        toolName == "bash" -> {
            val command = input["command"]?.jsonPrimitive?.contentOrNull ?: input.toString()
            "```bash\n$command\n```"
        }
        toolName == "edit" -> {
            val filePath = input["file_path"]?.jsonPrimitive?.contentOrNull ?: ""
            val oldStr = input["old_string"]?.jsonPrimitive?.contentOrNull ?: ""
            val newStr = input["new_string"]?.jsonPrimitive?.contentOrNull ?: ""
            buildString {
                appendLine("**File:** `$filePath`")
                appendLine()
                appendLine("```diff")
                oldStr.lines().forEach { appendLine("- $it") }
                newStr.lines().forEach { appendLine("+ $it") }
                appendLine("```")
            }
        }
        else -> {
            val json = Json { prettyPrint = true }
            "```json\n${json.encodeToString(JsonObject.serializer(), JsonObject(input))}\n```"
        }
    }
}

@Preview
@Composable
private fun PreviewBashCard() {
    AgentApproverTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
                DefaultCard(
                    request = ApprovalRequest(
                        id = "preview-1",
                        source = Source.CLAUDE_CODE,
                        toolType = ToolType.DEFAULT,
                        hookInput = HookInput(
                            sessionId = "sess-abc123",
                            toolName = "Bash",
                            toolInput = mapOf("command" to JsonPrimitive("git status && git diff HEAD")),
                            cwd = "/home/user/project",
                        ),
                        timestamp = Clock.System.now(),
                        rawRequestJson = "{}",
                    ),
                    onApprove = {},
                    onDeny = {},
                )
            }
        }
    }
}
