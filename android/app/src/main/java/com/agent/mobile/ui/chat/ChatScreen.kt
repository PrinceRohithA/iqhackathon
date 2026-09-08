package com.agent.mobile.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.mobile.ui.appViewModel
import com.agent.shared.model.ChatMessage
import com.agent.shared.model.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onOpenExecution: () -> Unit, viewModel: ChatViewModel = appViewModel { ChatViewModel(it) }) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val error by viewModel.lastError.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("AI Agent") },
            actions = {
                TextButton(onClick = viewModel::openCopilot) { Text("Copilot") }
                Text(
                    connection.name.lowercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (connection == ConnectionState.CONNECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 16.dp),
                )
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { MessageBubble(it) }
        }
        if (pending != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Approve ${pending!!.tool.name}?", style = MaterialTheme.typography.titleMedium)
                    Text(pending!!.tool.description, style = MaterialTheme.typography.bodySmall)
                    if (pending!!.inputs.isNotEmpty()) {
                        Text(pending!!.inputs.entries.joinToString { "${it.key}=${it.value}" }, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            viewModel.approve()
                            onOpenExecution()
                        }) { Text("Approve") }
                        OutlinedButton(onClick = { viewModel.reject() }) { Text("Cancel") }
                    }
                }
            }
        }
        if (!error.isNullOrBlank()) {
            Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                singleLine = true,
            )
            IconButton(onClick = {
                viewModel.send(draft)
                draft = ""
            }) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    val container = when (message.role) {
        "user" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        "tool" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Card(colors = CardDefaults.cardColors(containerColor = container), modifier = Modifier.fillMaxWidth(0.86f)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    when (message.role) {
                        "user" -> "You"
                        "tool" -> message.toolName ?: "Tool"
                        else -> "Agent"
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(message.content)
            }
        }
    }
}
