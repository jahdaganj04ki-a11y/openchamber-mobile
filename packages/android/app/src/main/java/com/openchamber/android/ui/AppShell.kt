package com.openchamber.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openchamber.android.AppViewModel
import com.openchamber.android.AppUiState
import com.openchamber.android.core.runtime.RuntimeState
import com.openchamber.android.i18n.LocalCatalog
import kotlinx.coroutines.launch

/**
 * The shell of the app.
 *
 * A phone shows one thing at a time: the session list is a drawer, the chat
 * is the page. A tablet shows both, because the space is there.
 */
@Composable
fun AppShell(state: AppUiState, viewModel: AppViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val isWide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
    var showProjects by remember { mutableStateOf(false) }

    if (showProjects) {
        ProjectPickerSheet(
            projects = state.projects,
            selectedPath = state.directory,
            onSelect = { project ->
                viewModel.selectDirectory(project.worktree)
                showProjects = false
            },
            onImportFolder = { tree ->
                viewModel.importProject(tree)
                showProjects = false
            },
            onClone = { url, name ->
                viewModel.cloneProject(url, name)
                showProjects = false
            },
            onDismiss = { showProjects = false },
        )
    }

    if (isWide) {
        Row(Modifier.fillMaxSize()) {
            SessionSidebar(
                state = state,
                onSelectSession = viewModel::selectSession,
                onNewSession = viewModel::createSession,
                onOpenSettings = { },
                onOpenProjects = { showProjects = true },
                modifier = Modifier.width(320.dp),
            )
            HorizontalDivider()
            ChatScreen(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                SessionSidebar(
                    state = state,
                    onSelectSession = { sessionID ->
                        viewModel.selectSession(sessionID)
                        scope.launch { drawerState.close() }
                    },
                    onNewSession = {
                        viewModel.createSession()
                        scope.launch { drawerState.close() }
                    },
                    onOpenSettings = { scope.launch { drawerState.close() } },
                    onOpenProjects = {
                        scope.launch { drawerState.close() }
                        showProjects = true
                    },
                )
            }
        },
    ) {
        Scaffold { padding ->
            ChatScreen(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onOpenDrawer = { scope.launch { drawerState.open() } },
            )
        }
    }
}

/**
 * What the app shows while the server or the engine is starting.
 *
 * The two stages are told apart, because the engine takes much longer than
 * the server and a user who sees one spinner for both waits longer than they
 * have to.
 */
@Composable
fun RuntimeGate(state: AppUiState, content: @Composable () -> Unit) {
    val runtime = state.runtime
    if (runtime is RuntimeState.Ready) {
        content()
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = when (runtime) {
                    is RuntimeState.Stopped -> LocalCatalog.current.t("android.runtime.stopped")
                    is RuntimeState.Starting -> runtime.stage
                    is RuntimeState.ServerReady -> LocalCatalog.current.t("android.runtime.serverReady")
                    is RuntimeState.EngineStarting -> LocalCatalog.current.t("android.runtime.engineStarting")
                    is RuntimeState.Ready -> LocalCatalog.current.t("android.runtime.ready")
                    is RuntimeState.Failed -> runtime.reason
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (runtime is RuntimeState.Failed) {
                Text(
                    text = LocalCatalog.current.t("android.runtime.retrying"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
