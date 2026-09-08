package com.agent.mobile.ui.workflow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.mobile.AgentApplication
import com.agent.mobile.ui.appViewModel
import com.agent.shared.model.NodeType
import com.agent.shared.model.Selector
import com.agent.shared.model.WorkflowNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowEditorScreen(
    toolId: String,
    onBack: () -> Unit,
    viewModel: WorkflowEditorViewModel = appViewModel(key = toolId) { WorkflowEditorViewModel(it, toolId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    val selected = state.nodes.find { it.id == state.selectedId }
    BackHandler { viewModel.save(andThen = onBack) }
    DisposableEffect(Unit) {
        AgentApplication.instance.container.copilotSession.hideCompletely()
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.tool?.name ?: "Workflow") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.save(andThen = onBack) }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.move(-1) }) { Icon(Icons.Outlined.ArrowUpward, contentDescription = "Up") }
                    IconButton(onClick = { viewModel.move(1) }) { Icon(Icons.Outlined.ArrowDownward, contentDescription = "Down") }
                    IconButton(onClick = viewModel::deleteSelected) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
                    IconButton(onClick = { picker = true }) { Icon(Icons.Outlined.Add, contentDescription = "Add") }
                    TextButton(onClick = { viewModel.save() }) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.saved) {
                Text(
                    if (state.errors.isEmpty()) "Saved" else "Saved. Fix these before running:",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            state.errors.forEach {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (state.picking) {
                Text(
                    "You'll go Home. Open the target app and walk to the screen you need. Use Pause 5s on the bottom bar if you need more time, then tap the field.",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                TextButton(onClick = viewModel::cancelIndicate) { Text("Cancel indicate") }
            }
            state.indicateError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.nodes, key = { it.id }) { node ->
                    val selectedBorder = if (node.id == state.selectedId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .border(1.dp, selectedBorder, MaterialTheme.shapes.medium)
                            .clickable { viewModel.select(node.id) },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(node.type.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                            Text(node.label(), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            if (selected != null) {
                NodeEditor(
                    node = selected,
                    capturedValue = selected.stringParam("capturedValue").ifBlank { state.capturedValue },
                    picking = state.picking,
                    onIndicate = viewModel::indicate,
                    onChange = { params, selector -> viewModel.updateSelected(params, selector) },
                )
            }
        }
    }

    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false },
            title = { Text("Add node") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NodeType.entries.forEach { type ->
                        TextButton(onClick = {
                            viewModel.addNode(type)
                            picker = false
                        }) { Text(type.name.lowercase().replace('_', ' ')) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun NodeEditor(
    node: WorkflowNode,
    capturedValue: String?,
    picking: Boolean,
    onIndicate: () -> Unit,
    onChange: (Map<String, String>, Selector?) -> Unit,
) {
    var params by remember(node.id, node.params) { mutableStateOf(node.scalarParams()) }
    var selector by remember(node.id, node.params) { mutableStateOf(node.selector() ?: Selector()) }
    LaunchedEffect(node.id, node.params) {
        params = node.scalarParams()
        selector = node.selector() ?: Selector()
    }
    Column(
        Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Edit node", style = MaterialTheme.typography.titleSmall)
        when (node.type) {
            NodeType.LAUNCH_APP -> {
                Field("App name", params["appName"].orEmpty()) { params = params + ("appName" to it); onChange(params, null) }
                Field("Package", params["packageName"].orEmpty()) { params = params + ("packageName" to it); onChange(params, null) }
            }
            NodeType.FIND_ELEMENT, NodeType.TAP, NodeType.LONG_PRESS, NodeType.READ_TEXT, NodeType.TYPE_TEXT -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onIndicate, modifier = Modifier.weight(1f)) {
                        Text(if (picking) "Indicating…" else "Indicate")
                    }
                }
                if (!capturedValue.isNullOrBlank()) {
                    OutlinedTextField(
                        value = capturedValue,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Current value") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Field("Text", selector.text.orEmpty()) { selector = selector.copy(text = it.ifBlank { null }); onChange(params, selector) }
                Field("Resource ID", selector.resourceId.orEmpty()) { selector = selector.copy(resourceId = it.ifBlank { null }); onChange(params, selector) }
                Field("Content description", selector.contentDescription.orEmpty()) {
                    selector = selector.copy(contentDescription = it.ifBlank { null }); onChange(params, selector)
                }
                if (node.type == NodeType.TYPE_TEXT) {
                    Field("Value", params["value"].orEmpty()) { params = params + ("value" to it); onChange(params, selector) }
                }
                if (node.type == NodeType.READ_TEXT) {
                    Field("Save as variable", params["name"].orEmpty().ifBlank { "text" }) {
                        params = params + ("name" to it); onChange(params, selector)
                    }
                }
            }
            NodeType.WAIT -> Field("Duration ms", params["durationMs"].orEmpty()) { params = params + ("durationMs" to it); onChange(params, null) }
            NodeType.SWIPE -> Field("Direction", params["direction"].orEmpty()) { params = params + ("direction" to it); onChange(params, null) }
            NodeType.SET_VARIABLE, NodeType.RETURN_RESULT -> {
                Field("Name", params["name"].orEmpty()) { params = params + ("name" to it); onChange(params, null) }
                Field("Value", params["value"].orEmpty()) { params = params + ("value" to it); onChange(params, null) }
            }
            NodeType.LOOP -> Field("Times", params["times"].orEmpty()) { params = params + ("times" to it); onChange(params, null) }
            NodeType.CONDITION -> {
                Field("Variable", params["variable"].orEmpty()) { params = params + ("variable" to it); onChange(params, null) }
                Field("Operator", params["operator"].orEmpty()) { params = params + ("operator" to it); onChange(params, null) }
                Field("Value", params["value"].orEmpty()) { params = params + ("value" to it); onChange(params, null) }
            }
            else -> Button(onClick = { onChange(params, null) }) { Text("Apply") }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}
