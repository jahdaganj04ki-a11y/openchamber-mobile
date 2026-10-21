package com.openchamber.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openchamber.android.AppUiState
import com.openchamber.android.core.design.OpenChamberTheme
import com.openchamber.android.i18n.LocalCatalog

/**
 * The list of sessions of the current directory.
 *
 * The list is the way into a conversation and the way to see what the engine
 * is doing right now: a session that is busy or waiting for the user is
 * marked.
 */
@Composable
fun SessionSidebar(
    state: AppUiState,
    onSelectSession: (String?) -> Unit,
    onNewSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProjects: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = LocalCatalog.current.t("android.app.name"),
                style = MaterialTheme.typography.titleMedium,
            )
            Row {
                IconButton(onClick = onOpenProjects) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = LocalCatalog.current.t("android.projects.title"),
                    )
                }
                IconButton(onClick = onNewSession) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = LocalCatalog.current.t("android.session.new"),
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = LocalCatalog.current.t("android.settings.title"),
                    )
                }
            }
        }
        HorizontalDivider()

        DirectoryPicker(
            state = state,
            onOpenProjects = onOpenProjects,
        )

        if (state.sessions.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = LocalCatalog.current.t("android.session.empty"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(state.sessions, key = { it.id }) { session ->
                    SessionRow(
                        title = session.title.ifBlank { LocalCatalog.current.t("android.session.untitled") },
                        subtitle = session.model?.id ?: session.agent.orEmpty(),
                        selected = session.id == state.selectedSessionID,
                        onClick = { onSelectSession(session.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryPicker(
    state: AppUiState,
    onOpenProjects: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = LocalCatalog.current.t("android.directory.title"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.directory ?: LocalCatalog.current.t("android.directory.projects"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onOpenProjects),
        )
    }
}

@Composable
private fun SessionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = OpenChamberTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.selection else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The bar at the top of the chat, with the drawer button on a phone. */
@Composable
fun ChatTopBar(
    title: String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = LocalCatalog.current.t("android.session.sessions"),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
    }
}
