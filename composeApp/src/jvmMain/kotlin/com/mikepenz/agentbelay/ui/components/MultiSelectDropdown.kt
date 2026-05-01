package com.mikepenz.agentbelay.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.agentbelay.ui.icons.LucideCheck
import com.mikepenz.agentbelay.ui.icons.LucideChevronDown
import com.mikepenz.agentbelay.ui.theme.AccentEmerald
import com.mikepenz.agentbelay.ui.theme.AgentBelayColors

/**
 * Compact multi-select dropdown: button shows a summary ("All harnesses",
 * "Claude Code +1") and clicking it pops the existing Material3
 * [DropdownMenu] populated with the [options]. Each row is a checkable
 * item.
 *
 * The current selection is passed in as [selected]; `null` is "no filter
 * (everything)" so callers can distinguish "user chose nothing" from
 * "user hasn't engaged the filter".
 *
 * The button + menu are kept lean on purpose — the existing color tokens
 * and chevron icon match the rest of the toolbar's pill aesthetic.
 */
@Composable
fun <T> MultiSelectDropdown(
    options: List<Pair<T, String>>,
    selected: Set<T>?,
    onChange: (Set<T>?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "All",
    leadingDot: ((T) -> Color)? = null,
    /**
     * Forces the menu open on first composition — used by previews to show
     * the expanded state under compose-buddy. Defaults `false` for runtime.
     */
    initiallyOpen: Boolean = false,
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val effectiveSelected = selected ?: emptySet()
    val isAll = selected == null || selected.size == options.size
    val summary = when {
        options.isEmpty() -> allLabel
        isAll -> allLabel
        effectiveSelected.size == 1 -> options.firstOrNull { it.first == effectiveSelected.first() }?.second ?: allLabel
        else -> "${effectiveSelected.size} selected"
    }

    Box {
        Row(
            modifier = modifier
                .height(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(AgentBelayColors.surface)
                .border(1.dp, AgentBelayColors.line1, RoundedCornerShape(7.dp))
                .clickable(enabled = options.isNotEmpty()) { open = true }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = summary,
                color = AgentBelayColors.inkPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = LucideChevronDown,
                contentDescription = null,
                tint = AgentBelayColors.inkMuted,
                modifier = Modifier.size(11.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            // "All" toggle at the top.
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selected == null) {
                            Icon(LucideCheck, contentDescription = "selected", tint = AccentEmerald, modifier = Modifier.size(12.dp))
                        } else {
                            Box(modifier = Modifier.size(12.dp))
                        }
                        Text(allLabel, fontSize = 12.sp)
                    }
                },
                onClick = {
                    onChange(null)
                },
            )
            options.forEach { (id, label) ->
                val isChecked = id in effectiveSelected
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isChecked) {
                                Icon(LucideCheck, contentDescription = "selected", tint = AccentEmerald, modifier = Modifier.size(12.dp))
                            } else {
                                Box(modifier = Modifier.size(12.dp))
                            }
                            if (leadingDot != null) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(leadingDot(id)),
                                )
                            }
                            Text(label, fontSize = 12.sp)
                        }
                    },
                    onClick = {
                        // Toggle this entry; close on every click so the user
                        // can immediately confirm or open the menu again to
                        // tweak. Mirrors the GMail-style multi-select.
                        val next = if (isChecked) effectiveSelected - id else effectiveSelected + id
                        onChange(next.takeIf { it.isNotEmpty() })
                    },
                )
            }
        }
    }
}
