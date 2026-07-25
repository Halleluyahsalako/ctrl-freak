package com.hal.ctrlfreak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hal.ctrlfreak.ui.theme.CfColor
import com.hal.ctrlfreak.ui.theme.CfRadius
import com.hal.ctrlfreak.ui.theme.CfSpace
import com.hal.ctrlfreak.ui.theme.CfType

// docs/ctrl-freak-android-ui-spec.md §3.1

@Composable
fun CfSignInScreen(
    busy: Boolean,
    error: String?,
    onSignIn: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(CfColor.Background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = CfSpace.S24),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                Text("ctrl+", style = CfType.Wordmark.copy(color = CfColor.Ink))
                Text("freak", style = CfType.Wordmark.copy(color = CfColor.Accent))
            }
            Spacer(Modifier.height(CfSpace.S16))
            Text(
                "Your clipboard and notes,\non every device.",
                style = CfType.BodyMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(CfSpace.S16))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CfSpace.TapTarget)
                    .clip(RoundedCornerShape(CfRadius.Small))
                    .background(CfColor.Surface)
                    .border(1.dp, CfColor.Rule, RoundedCornerShape(CfRadius.Small))
                    .clickable(enabled = !busy, onClick = onSignIn)
                    .padding(horizontal = CfSpace.S16),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(14.dp),
                        color = CfColor.Ink,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = CfColor.Cyan)
                    Spacer(Modifier.height(CfSpace.S8))
                    Text("  Continue with Google", style = CfType.Chip.copy(color = CfColor.Ink))
                }
            }

            if (error != null) {
                Spacer(Modifier.height(CfSpace.S8))
                Text(error, style = CfType.Meta.copy(color = CfColor.Danger))
            }
        }
    }
}
