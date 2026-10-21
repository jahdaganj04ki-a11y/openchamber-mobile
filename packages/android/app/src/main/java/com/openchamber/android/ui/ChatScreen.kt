package com.openchamber.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openchamber.android.AppUiState
import com.openchamber.android.AppViewModel
import com.openchamber.android.core.design.OpenChamberTheme
import com.openchamber.android.core.model.Message
import com.openchamber.android.core.model.Part
import com.openchamber.android.ui.parts.MessageParts
import com.openchamber.android.ui.prompt.FormCard
import com.openchamber.android.ui.prompt.PermissionCard

/**
 * The conversation.
 *
 * The timeline scrolls to the newest message, and it follows the stream, so a
 * reply that arrives while the user reads something else is still in view.
 */
@Composable
fun ChatScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
) {
    RuntimeGate(state) {
        Column(modifier.fillMaxSize()) {
            ChatTopBar(
                title = state.sessions
                    .firstOrNull { it.id == state.selectedSessionID }
                    ?.title
                    ?.ifBlank { null }
                    ?: "OpenChamber",
                onOpenDrawer = onOpenDrawer,
            )
            HorizontalDivider()

            val listState = rememberLazyListState()
            val lastMessageID = state.messages.lastOrNull()?.id
            LaunchedEffect(lastMessageID) {
                if (lastMessageID != null && state.messages.isNotEmpty()) {
                    listState.animateScrollToItem(state.messages.lastIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageRow(
                        message = message,
                        parts = state.parts[message.id].orEmpty(),
                    )
                }
                if (state.permissions.isNotEmpty()) {
                    item("permissions") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.permissions.forEach { request ->
                                PermissionCard(
                                    request = request,
                                    onReply = { decision -> viewModel.replyPermission(request.id, decision) },
                                )
                            }
                        }
                    }
                }
                if (state.forms.isNotEmpty()) {
                    item("forms") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.forms.forEach { form ->
                                FormCard(
                                    form = form,
                                    onReply = { answer -> viewModel.replyForm(form.id, answer) },
                                )
                            }
                        }
                    }
                }
            }

            Composer(
                draft = state.draft,
                onDraftChange = viewModel::setDraft,
                onSend = { viewModel.send(state.draft) },
                enabled = state.selectedSessionID != null,
            )
        }
    }
}

@Composable
private fun MessageRow(message: Message, parts: List<Part>) {
    val colors = OpenChamberTheme.colors
    when (message) {
        is com.openchamber.android.core.model.UserMessage -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    color = colors.userMessageBackground,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.widthIn(max = 480.dp),
                ) {
                    MessageParts(parts = parts, role = Role.USER)
                }
            }
        }
        is com.openchamber.android.core.model.AssistantMessage -> {
            Box(Modifier.fillMaxWidth()) {
                MessageParts(parts = parts, role = Role.ASSISTANT)
            }
        }
        else -> {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.subtle)
                    .padding(8.dp),
            ) {
                Text(
                    text = noticeText(message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun noticeText(message: Message): String = when (message) {
    is com.openchamber.android.core.model.SyntheticMessage ->
        message.description?.ifBlank { null } ?: message.text
    is com.openchamber.android.core.model.SystemMessage ->
        message.description?.ifBlank { null } ?: message.text
    is com.openchamber.android.core.model.SkillMessage -> "Skill: ${message.name}"
    is com.openchamber.android.core.model.ShellMessage -> "Shell: ${message.command}"
    is com.openchamber.android.core.model.CompactionMessage -> "Compaction: ${message.status}"
    is com.openchamber.android.core.model.AgentSwitchedMessage -> "Agent: ${message.agent}"
    is com.openchamber.android.core.model.ModelSwitchedMessage -> "Model: ${message.model.id}"
    is com.openchamber.android.core.model.LocationSwitchedMessage -> "Directory: ${message.directory}"
    is com.openchamber.android.core.model.IdleMessage -> "Idle"
    else -> ""
}
