package com.aar.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwipeDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aar.reader.R

/**
 * State-driven Material 3 Prominent Disclosure Dialog for Android Accessibility Service usage.
 *
 * Automatically manages visibility based on [ProminentDisclosureState.isDialogVisible]
 * and forwards user actions directly to [ProminentDisclosureState.onAgreeAndEnable]
 * and [ProminentDisclosureState.onDismiss].
 *
 * @param state The production state model governing disclosure presentation and consent.
 * @param modifier Optional modifier applied to the dialog container.
 * @param onAgreeAdditional Optional additional callback executed upon user agreement (e.g. analytics).
 * @param onDismissAdditional Optional additional callback executed upon dismissal.
 */
@Composable
fun ProminentDisclosureDialog(
    state: ProminentDisclosureState,
    modifier: Modifier = Modifier,
    onAgreeAdditional: (() -> Unit)? = null,
    onDismissAdditional: (() -> Unit)? = null
) {
    if (!state.isDialogVisible) return

    ProminentDisclosureDialog(
        onAgreeAndEnable = {
            state.onAgreeAndEnable()
            onAgreeAdditional?.invoke()
        },
        onDismiss = {
            state.onDismiss()
            onDismissAdditional?.invoke()
        },
        modifier = modifier
    )
}

/**
 * Material 3 Prominent Disclosure Dialog for Android Accessibility Service usage.
 *
 * Strictly complies with Google Play policy by explaining:
 * 1. Why AccessibilityService is requested (Screen Text Reading & Continuous Auto-Scroll Gestures).
 * 2. 100% Offline zero-network architecture (No INTERNET permission).
 * 3. Zero data collection and password field exclusion.
 *
 * @param onAgreeAndEnable Callback when user consents to enable the service.
 * @param onDismiss Callback when user declines or dismisses the dialog.
 */
@Composable
fun ProminentDisclosureDialog(
    onAgreeAndEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.disclosure_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.disclosure_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                DisclosureItemCard(
                    icon = Icons.Default.RecordVoiceOver,
                    title = stringResource(R.string.disclosure_item_reading_title),
                    description = stringResource(R.string.disclosure_item_reading_desc)
                )

                DisclosureItemCard(
                    icon = Icons.Default.SwipeDown,
                    title = stringResource(R.string.disclosure_item_gestures_title),
                    description = stringResource(R.string.disclosure_item_gestures_desc)
                )

                DisclosureItemCard(
                    icon = Icons.Default.CloudOff,
                    title = stringResource(R.string.disclosure_item_privacy_title),
                    description = stringResource(R.string.disclosure_item_privacy_desc)
                )

                DisclosureItemCard(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.disclosure_item_security_title),
                    description = stringResource(R.string.disclosure_item_security_desc)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAgreeAndEnable,
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.disclosure_action_agree),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.disclosure_action_deny),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}

@Composable
private fun DisclosureItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
