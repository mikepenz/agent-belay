package com.mikepenz.agentapprover.ui.approvals

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
fun AskUserQuestionCard(
    request: ApprovalRequest,
    onSendResponse: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var customMode by remember { mutableStateOf(false) }
    var customResponse by remember { mutableStateOf("") }

    val question = request.toolInput["question"]?.jsonPrimitive?.contentOrNull
        ?: request.toolInput.toString()
    val options = request.toolInput["options"]?.jsonArray?.mapNotNull {
        it.jsonPrimitive.contentOrNull
    } ?: emptyList()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("No timeout", fontSize = 10.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))

        // Question (markdown rendered)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
        ) {
            Box(modifier = Modifier.padding(10.dp)) {
                Markdown(content = question)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Option selection (radio buttons)
        if (options.isNotEmpty() && !customMode) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOption = option }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedOption == option,
                        onClick = { selectedOption = option },
                    )
                    Text(
                        option,
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // "or" divider + custom toggle
        if (options.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text("or", modifier = Modifier.padding(horizontal = 8.dp), fontSize = 11.sp, color = Color.Gray)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { customMode = !customMode; if (!customMode) customResponse = "" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = customMode, onCheckedChange = { customMode = it; if (!it) customResponse = "" })
                Text("Custom response", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Custom input (shown when custom mode or no options)
        if (customMode || options.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = customResponse,
                onValueChange = { customResponse = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Type your response...", fontSize = 12.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                minLines = 2,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Dismiss")
            }
            Button(
                onClick = {
                    val response = if (customMode || options.isEmpty()) customResponse else selectedOption ?: ""
                    onSendResponse(response)
                },
                modifier = Modifier.weight(1f),
                enabled = if (customMode || options.isEmpty()) customResponse.isNotBlank() else selectedOption != null,
            ) {
                Text("Send Response")
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAskUserQuestionWithOptions() {
    AgentApproverTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
                AskUserQuestionCard(
                    request = ApprovalRequest(
                        id = "preview-ask",
                        source = Source.CLAUDE_CODE,
                        toolName = "AskUserQuestion",
                        toolType = ToolType.ASK_USER_QUESTION,
                        toolInput = JsonObject(
                            mapOf(
                                "question" to JsonPrimitive("Which database backend should I use for this project?"),
                                "options" to JsonArray(
                                    listOf(
                                        JsonPrimitive("PostgreSQL"),
                                        JsonPrimitive("SQLite"),
                                        JsonPrimitive("MySQL"),
                                    )
                                ),
                            )
                        ),
                        sessionId = "sess-abc123",
                        cwd = "/home/user/project",
                        timestamp = Clock.System.now(),
                        rawRequestJson = "{}",
                    ),
                    onSendResponse = {},
                    onDismiss = {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAskUserQuestionCustomOnly() {
    AgentApproverTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.width(350.dp).padding(12.dp)) {
                AskUserQuestionCard(
                    request = ApprovalRequest(
                        id = "preview-ask-custom",
                        source = Source.CLAUDE_CODE,
                        toolName = "AskUserQuestion",
                        toolType = ToolType.ASK_USER_QUESTION,
                        toolInput = JsonObject(
                            mapOf(
                                "question" to JsonPrimitive("What name would you like for the new module?"),
                            )
                        ),
                        sessionId = "sess-def456",
                        cwd = "/home/user/project",
                        timestamp = Clock.System.now(),
                        rawRequestJson = "{}",
                    ),
                    onSendResponse = {},
                    onDismiss = {},
                )
            }
        }
    }
}
