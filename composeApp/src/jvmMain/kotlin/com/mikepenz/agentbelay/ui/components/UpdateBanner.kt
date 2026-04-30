package com.mikepenz.agentbelay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AccentEmeraldInk
import com.mikepenz.agentbelay.ui.theme.AccentEmeraldTint
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors
import com.mikepenz.agentbelay.ui.theme.PreviewScaffold
import com.mikepenz.agentbelay.update.UpdateUiState

/**
 * Top-of-app banner shown when an update is `Available`, `Downloading`, or
 * `Ready`. Other [UpdateUiState] values render nothing — the auto-check is
 * silent and the in-app surface for it is the Updates row in Settings.
 *
 * Visually anchored on the emerald accent the rest of the design system uses
 * for positive prompts (matching the Settings update row's spinner colour),
 * with a subtle accent tint behind the row so the banner reads as a
 * notification rather than chrome. The dismiss "x" lets the user clear the
 * banner without acting; the underlying [UpdateUiState] is reset to Idle by
 * the caller — re-running a check brings it back.
 */
@Composable
fun UpdateBanner(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Cache the most recent visible state so the banner keeps rendering its
    // last content during the slide-out animation. Without this, dismissing
    // while AnimatedVisibility was unwrapping would flash empty content.
    var lastVisibleState by remember { mutableStateOf<UpdateUiState?>(null) }
    val visible = state.isBannerVisible()
    if (visible) lastVisibleState = state
    val effectiveState = if (visible) state else lastVisibleState

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 240),
        ) + fadeIn(animationSpec = tween(durationMillis = 240)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 200),
        ) + fadeOut(animationSpec = tween(durationMillis = 200)),
        modifier = modifier,
    ) {
        when (effectiveState) {
            is UpdateUiState.Available -> BannerLayout(
                title = "Update available — v${effectiveState.version}",
                subtitle = "A newer Agent Belay release is ready to download.",
                progressPercent = null,
                primary = BannerAction(label = "Download", onClick = onDownload),
                onDismiss = onDismiss,
            )
            is UpdateUiState.Downloading -> BannerLayout(
                title = "Downloading update… ${effectiveState.percent}%",
                subtitle = "Agent Belay will prompt to install when the download finishes.",
                progressPercent = effectiveState.percent,
                primary = null,
                onDismiss = onDismiss,
            )
            is UpdateUiState.Ready -> BannerLayout(
                title = "v${effectiveState.version} is ready to install",
                subtitle = "Click Install to relaunch Agent Belay on the new version.",
                primary = BannerAction(label = "Install", onClick = onInstall),
                progressPercent = null,
                onDismiss = onDismiss,
            )
            else -> Unit
        }
    }
}

private fun UpdateUiState.isBannerVisible(): Boolean = when (this) {
    is UpdateUiState.Available,
    is UpdateUiState.Downloading,
    is UpdateUiState.Ready -> true
    UpdateUiState.Idle,
    UpdateUiState.Checking,
    UpdateUiState.UpToDate,
    is UpdateUiState.Failed -> false
}

@Composable
private fun BannerLayout(
    title: String,
    subtitle: String,
    progressPercent: Int?,
    primary: BannerAction?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // Top hairline only — the banner sits flush against the window
            // bottom, so a ringed border would look like a floating chip.
            .drawTopHairline(AccentEmerald.copy(alpha = 0.35f)),
        color = AccentEmeraldTint,
        contentColor = AgentBelayColors.inkPrimary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccentDot()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AgentBelayColors.inkPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AgentBelayColors.inkTertiary,
                    fontSize = 11.5.sp,
                )
                if (progressPercent != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progressPercent.coerceIn(0, 100) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentEmerald,
                        trackColor = AgentBelayColors.surface3,
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
            }
            if (primary != null) {
                PrimaryAction(text = primary.label, onClick = primary.onClick)
            }
            DismissButton(onClick = onDismiss)
        }
    }
}

@Composable
private fun AccentDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(AccentEmerald),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) AccentEmerald.copy(alpha = 0.85f) else AccentEmerald)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AccentEmeraldInk,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DismissButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) AgentBelayColors.surface3 else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Dismiss update notice" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            val tint = androidx.compose.ui.graphics.Color(0xFF8A9AA8) // matches inkTertiary in dark
            val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            drawLine(
                color = tint,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = stroke.width,
                cap = stroke.cap,
            )
            drawLine(
                color = tint,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
                strokeWidth = stroke.width,
                cap = stroke.cap,
            )
        }
    }
}

private data class BannerAction(val label: String, val onClick: () -> Unit)

private fun Modifier.drawTopHairline(color: Color) =
    this.then(
        Modifier.drawWithContent {
            drawContent()
            val strokePx = 0.5.dp.toPx()
            drawLine(
                color = color,
                start = Offset(0f, strokePx / 2f),
                end = Offset(size.width, strokePx / 2f),
                strokeWidth = strokePx,
            )
        }
    )

// ── Previews ──────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(widthDp = 720, heightDp = 80)
@Composable
private fun PreviewBannerAvailable() {
    PreviewScaffold {
        BannerLayout(
            title = "Update available — v2.4.0",
            subtitle = "A newer Agent Belay release is ready to download.",
            progressPercent = null,
            primary = BannerAction("Download") {},
            onDismiss = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 720, heightDp = 100)
@Composable
private fun PreviewBannerDownloading() {
    PreviewScaffold {
        BannerLayout(
            title = "Downloading update… 42%",
            subtitle = "Agent Belay will prompt to install when the download finishes.",
            progressPercent = 42,
            primary = null,
            onDismiss = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 720, heightDp = 80)
@Composable
private fun PreviewBannerReady() {
    PreviewScaffold {
        BannerLayout(
            title = "v2.4.0 is ready to install",
            subtitle = "Click Install to relaunch Agent Belay on the new version.",
            progressPercent = null,
            primary = BannerAction("Install") {},
            onDismiss = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 720, heightDp = 80)
@Composable
private fun PreviewBannerAvailableLight() {
    PreviewScaffold(themeMode = com.mikepenz.agentbelay.model.ThemeMode.LIGHT) {
        BannerLayout(
            title = "Update available — v2.4.0",
            subtitle = "A newer Agent Belay release is ready to download.",
            progressPercent = null,
            primary = BannerAction("Download") {},
            onDismiss = {},
        )
    }
}
