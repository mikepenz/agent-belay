package com.mikepenz.agentbelay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.mikepenz.agentbelay.model.ThemeMode
import com.mikepenz.agentbelay.ui.components.OutlineButton
import com.mikepenz.agentbelay.ui.components.PrimaryButton
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.AgentBelayTheme
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import io.github.kdroidfilter.nucleus.window.material.MaterialDecoratedWindow
import io.github.kdroidfilter.nucleus.window.material.MaterialTitleBar

/**
 * Confirmation window shown before triggering a destructive
 * `installAndRestart` — replaces the previous bare-Material3 `AlertDialog`
 * with a properly themed [MaterialDecoratedWindow] so the surface, ink, and
 * action buttons match the rest of Agent Belay (sidebar, port-error window,
 * pop-out detail window).
 *
 * This is the *only* in-app confirmation between the user clicking
 * "Install & Restart" and the OS installer taking over — once [onConfirm]
 * fires, the process exits, so the body deliberately reads as a soft warning
 * rather than a casual prompt.
 */
@Composable
fun InstallConfirmWindow(
    version: String,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val windowState = rememberWindowState(
        size = DpSize(440.dp, 260.dp),
        position = WindowPosition(Alignment.Center),
    )
    AgentBelayTheme(themeMode = themeMode) {
        MaterialDecoratedWindow(
            onCloseRequest = onCancel,
            title = "Install Update",
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onCancel(); true
                } else false
            },
        ) {
            MaterialTitleBar {
                Text(
                    "Install Update",
                    color = AgentBelayColors.inkSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            InstallConfirmBody(version = version, onConfirm = onConfirm, onCancel = onCancel)
        }
    }
}

/**
 * Pure body so previews can render without a native window host.
 */
@Composable
fun InstallConfirmBody(
    version: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AgentBelayColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentEmerald.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "↓",
                    color = AccentEmerald,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "Install v$version?",
                color = AgentBelayColors.inkPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.1).sp,
            )
            Text(
                "Agent Belay will close, install the update, then relaunch. " +
                    "Any unsaved work will be lost.",
                color = AgentBelayColors.inkTertiary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                OutlineButton(text = "Cancel", onClick = onCancel)
                PrimaryButton(text = "Install & Restart", onClick = onConfirm)
            }
        }
    }
}

@Preview(widthDp = 440, heightDp = 260)
@Composable
private fun PreviewInstallConfirmBody() {
    PreviewScaffold {
        InstallConfirmBody(version = "2.5.0", onConfirm = {}, onCancel = {})
    }
}

@Preview(widthDp = 440, heightDp = 260)
@Composable
private fun PreviewInstallConfirmBodyLight() {
    PreviewScaffold(themeMode = ThemeMode.LIGHT) {
        InstallConfirmBody(version = "2.5.0", onConfirm = {}, onCancel = {})
    }
}

@Preview(widthDp = 480, heightDp = 280)
@Composable
private fun PreviewInstallConfirmBodyLongVersion() {
    PreviewScaffold {
        InstallConfirmBody(
            version = "2.5.0-beta.42+gabc1234",
            onConfirm = {},
            onCancel = {},
        )
    }
}
