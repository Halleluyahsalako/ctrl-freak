package com.hal.ctrlfreak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hal.ctrlfreak.ui.theme.CfColor
import com.hal.ctrlfreak.ui.theme.CfRadius
import com.hal.ctrlfreak.ui.theme.CfSpace
import com.hal.ctrlfreak.ui.theme.CfType

// docs/ctrl-freak-android-ui-spec.md §2 — component states. Every
// interactive element wires default/pressed/disabled(/loading) explicitly;
// the spec calls out missing states as the prior build's core failure.

@Composable
fun CfPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = if (!enabled) CfColor.Accent.copy(alpha = 0.4f) else CfColor.Accent
    val overlay = if (pressed) Color.White.copy(alpha = 0.06f) else Color.Transparent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CfSpace.TapTarget)
            .offset(y = if (pressed) 1.dp else 0.dp)
            .shadow(12.dp, RoundedCornerShape(CfRadius.Small))
            .clip(RoundedCornerShape(CfRadius.Small))
            .background(bg)
            .background(overlay)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = CfColor.Background,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text,
                style = CfType.Button.copy(
                    color = if (enabled) CfColor.Background else CfColor.Background.copy(alpha = 0.6f),
                ),
            )
        }
    }
}

@Composable
fun CfPinToggle(pinned: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier.size(CfSpace.TapTarget)) {
        Icon(
            imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            contentDescription = if (pinned) "Unpin" else "Pin",
            tint = if (pinned) CfColor.Accent else CfColor.InkFaint,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
fun CfCategoryChip(label: String, active: Boolean, onClick: () -> Unit) {
    val borderColor = if (active) CfColor.Accent else CfColor.Rule
    val bg = if (active) CfColor.SurfaceRaised else Color.Transparent
    val textColor = if (active) CfColor.Accent else CfColor.InkMuted

    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(CfRadius.Small))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(CfRadius.Small))
            .clickable(onClick = onClick)
            .padding(horizontal = CfSpace.S9),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CfType.Chip.copy(color = textColor))
    }
}

@Composable
fun CfEmptyState(icon: ImageVector, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CfSpace.S30, horizontal = CfSpace.S24),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(CfRadius.Card))
                .background(CfColor.Surface)
                .border(1.dp, CfColor.Rule, RoundedCornerShape(CfRadius.Card)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = CfColor.Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(CfSpace.S14))
        Text(title, style = CfType.Body)
        Spacer(Modifier.height(CfSpace.S6))
        Text(body, style = CfType.BodyMuted, textAlign = TextAlign.Center)
    }
}

enum class CfSnackbarKind { Success, Error, Neutral }

@Composable
fun CfSnackbar(data: SnackbarData, kind: CfSnackbarKind = CfSnackbarKind.Success) {
    val (icon, tint) = when (kind) {
        CfSnackbarKind.Success -> Icons.Filled.Check to CfColor.Accent
        CfSnackbarKind.Error -> Icons.Filled.ErrorOutline to CfColor.Danger
        CfSnackbarKind.Neutral -> Icons.Filled.Check to CfColor.InkMuted
    }
    Row(
        modifier = Modifier
            .padding(CfSpace.S14)
            .shadow(12.dp, RoundedCornerShape(CfRadius.Small))
            .clip(RoundedCornerShape(CfRadius.Small))
            .background(CfColor.Surface)
            .border(1.dp, CfColor.Rule, RoundedCornerShape(CfRadius.Small))
            .padding(horizontal = CfSpace.S12, vertical = CfSpace.S10),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CfSpace.S8),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(data.visuals.message, style = CfType.Body)
    }
}
