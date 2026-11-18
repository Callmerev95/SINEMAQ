package com.nuvio.app.features.profiles

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.features.settings.CommunityConfig
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.profile_limit_upgrade_body
import nuvio.composeapp.generated.resources.profile_limit_upgrade_dismiss
import nuvio.composeapp.generated.resources.profile_limit_upgrade_title
import nuvio.composeapp.generated.resources.profile_limit_upgrade_cta
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProfileLimitUpgradeDialog(
    currentLimit: Int,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val upgradeUrl = CommunityConfig.UPGRADE_URL.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.profile_limit_upgrade_title)) },
        text = { Text(text = stringResource(Res.string.profile_limit_upgrade_body, currentLimit)) },
        confirmButton = {
            if (upgradeUrl.isNotBlank()) {
                TextButton(onClick = {
                    onDismiss()
                    runCatching { uriHandler.openUri(upgradeUrl) }
                }) {
                    Text(text = stringResource(Res.string.profile_limit_upgrade_cta))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.profile_limit_upgrade_dismiss))
            }
        },
        properties = DialogProperties(),
    )
}
