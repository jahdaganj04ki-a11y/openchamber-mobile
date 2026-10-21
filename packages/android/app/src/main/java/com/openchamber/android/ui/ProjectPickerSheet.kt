package com.openchamber.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openchamber.android.core.model.Project
import com.openchamber.android.i18n.LocalCatalog

/**
 * The sheet that holds the projects on the phone.
 *
 * A project is a folder in the app's storage. The user picks a folder from
 * the device, and the app copies it, or the user names a repository and the
 * app clones it. Picking a project makes it the one the server works in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPickerSheet(
    projects: List<Project>,
    selectedPath: String?,
    onSelect: (Project) -> Unit,
    onImportFolder: (android.net.Uri) -> Unit,
    onClone: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cloning by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { tree ->
        if (tree != null) {
            onImportFolder(tree)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = LocalCatalog.current.t("android.projects.title"),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { folderPicker.launch(null) }) {
                    Text(LocalCatalog.current.t("android.projects.import"))
                }
                OutlinedButton(onClick = { cloning = true }) {
                    Text(LocalCatalog.current.t("android.projects.clone"))
                }
            }
            HorizontalDivider()
            if (projects.isEmpty()) {
                Text(
                    text = LocalCatalog.current.t("android.projects.empty"),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(projects, key = { it.id }) { project ->
                        ProjectRow(
                            project = project,
                            selected = project.worktree == selectedPath,
                            onClick = { onSelect(project) },
                        )
                    }
                }
            }
        }
    }

    if (cloning) {
        CloneDialog(
            onConfirm = { url, name ->
                cloning = false
                onClone(url, name)
            },
            onDismiss = { cloning = false },
        )
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = project.name.orEmpty(),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                androidx.compose.material3.MaterialTheme.colorScheme.primary
            } else {
                androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The dialog that asks for a repository.
 *
 * The name of the project is the name of the repository, unless the user
 * wants another one.
 */
@Composable
private fun CloneDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(LocalCatalog.current.t("android.projects.clone")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(LocalCatalog.current.t("android.projects.cloneUrl")) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(LocalCatalog.current.t("android.projects.cloneName")) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val address = url.trim()
                    onConfirm(address, name.trim().ifEmpty { address.substringAfterLast('/') })
                },
                enabled = url.isNotBlank(),
            ) {
                Text(LocalCatalog.current.t("android.projects.cloneAction"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LocalCatalog.current.t("android.projects.cancel"))
            }
        },
    )
}
