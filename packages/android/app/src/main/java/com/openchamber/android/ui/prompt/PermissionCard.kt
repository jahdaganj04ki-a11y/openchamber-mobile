package com.openchamber.android.ui.prompt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openchamber.android.core.model.PermissionRequest
import com.openchamber.android.i18n.LocalCatalog

/**
 * The card that asks the user for a decision.
 *
 * The three answers are the ones the engine understands: once, always, and
 * no. "Always" is only offered for the actions the engine can remember, so
 * the user cannot grant a permission the server would not store.
 */
@Composable
fun PermissionCard(
    request: PermissionRequest,
    onReply: (decision: String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = request.action,
                style = MaterialTheme.typography.titleSmall,
            )
            if (!request.message.isNullOrBlank()) {
                val message = request.message
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (request.resources.isNotEmpty()) {
                Text(
                    text = request.resources.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onReply("once") }) {
                    Text(LocalCatalog.current.t("android.permission.allowOnce"))
                }
                OutlinedButton(onClick = { onReply("always") }) {
                    Text(LocalCatalog.current.t("android.permission.always"))
                }
                OutlinedButton(onClick = { onReply("reject") }) {
                    Text(LocalCatalog.current.t("android.permission.reject"))
                }
            }
        }
    }
}
