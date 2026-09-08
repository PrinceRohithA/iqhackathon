package com.agent.mobile.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.mobile.ui.appViewModel
import com.agent.shared.model.ExecutionPolicy
import com.agent.shared.model.ToolDefinition
import com.agent.shared.model.ToolType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ToolsViewModel = appViewModel { ToolsViewModel(it) },
) {
    val tools by viewModel.tools.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Tools") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Outlined.Add, contentDescription = "Create tool")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tools, key = { it.id }) { tool ->
                ToolRow(
                    tool = tool,
                    onToggle = { viewModel.toggleEnabled(tool) },
                    onPolicy = { viewModel.setPolicy(tool, it) },
                    onDelete = { viewModel.delete(tool) },
                    onRun = { viewModel.run(tool) },
                    onEdit = { onEdit(tool.id) },
                )
            }
        }
    }
}

@Composable
private fun ToolRow(
    tool: ToolDefinition,
    onToggle: () -> Unit,
    onPolicy: (ExecutionPolicy) -> Unit,
    onDelete: () -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tool.name, style = MaterialTheme.typography.titleMedium)
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (tool.type == ToolType.SYSTEM) "System" else "User",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Switch(checked = tool.enabled, onCheckedChange = { onToggle() })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tool.executionPolicy == ExecutionPolicy.AUTOMATIC,
                onClick = { onPolicy(ExecutionPolicy.AUTOMATIC) },
                label = { Text("Automatic") },
            )
            FilterChip(
                selected = tool.executionPolicy == ExecutionPolicy.APPROVAL_REQUIRED,
                onClick = { onPolicy(ExecutionPolicy.APPROVAL_REQUIRED) },
                label = { Text("Approval") },
            )
            IconButton(onClick = onRun) { Icon(Icons.Outlined.PlayArrow, contentDescription = "Run") }
            if (tool.type == ToolType.USER) {
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
            }
        }
    }
}
