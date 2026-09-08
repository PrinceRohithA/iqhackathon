package com.agent.mobile.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.mobile.ui.appViewModel
import com.agent.shared.model.ExecutionPolicy
import com.agent.shared.model.InputParameter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolEditScreen(
    toolId: String,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    viewModel: ToolEditViewModel = appViewModel(key = toolId) { ToolEditViewModel(it, toolId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Create Tool" else "Edit Tool") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(state.name, viewModel::updateName, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.description, viewModel::updateDescription, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text("Execution policy")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.policy == ExecutionPolicy.AUTOMATIC,
                    onClick = { viewModel.updatePolicy(ExecutionPolicy.AUTOMATIC) },
                    label = { Text("Automatic") },
                )
                FilterChip(
                    selected = state.policy == ExecutionPolicy.APPROVAL_REQUIRED,
                    onClick = { viewModel.updatePolicy(ExecutionPolicy.APPROVAL_REQUIRED) },
                    label = { Text("Approval required") },
                )
            }
            Text("Inputs")
            state.inputs.forEachIndexed { index, input ->
                InputRow(input, onChange = { viewModel.updateInput(index, it) }, onRemove = { viewModel.removeInput(index) })
            }
            OutlinedButton(onClick = viewModel::addInput) { Text("Add input") }
            state.errors.forEach { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    scope.launch {
                        val id = viewModel.save()
                        if (id != null) onOpenEditor(id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save and open workflow") }
            if (!state.isNew) {
                OutlinedButton(onClick = { onOpenEditor(state.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open workflow editor (${state.nodeCount} nodes)")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputRow(input: InputParameter, onChange: (InputParameter) -> Unit, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val types = listOf("string", "image", "number", "boolean")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(input.name, { onChange(input.copy(name = it)) }, label = { Text("Input name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(input.description, { onChange(input.copy(description = it)) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = input.type,
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                types.forEach { type ->
                    DropdownMenuItem(text = { Text(type) }, onClick = {
                        onChange(input.copy(type = type))
                        expanded = false
                    })
                }
            }
        }
        OutlinedButton(onClick = onRemove) { Text("Remove input") }
    }
}
