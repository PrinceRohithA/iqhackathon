package com.agent.mobile.ui.execution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.mobile.core.AppContainer
import com.agent.mobile.ui.appViewModel
import com.agent.shared.model.ExecutionStatus

class ExecutionViewModel(private val container: AppContainer) : ViewModel() {
    val current = container.executionManager.current
    val pending = container.executionManager.pending
    fun stop() = container.executionManager.cancel()
    fun approve() {
        val id = container.executionManager.pending.value?.executionId ?: return
        container.executionManager.approve(id)
    }
    fun reject() {
        val id = container.executionManager.pending.value?.executionId ?: return
        container.executionManager.reject(id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionScreen(viewModel: ExecutionViewModel = appViewModel { ExecutionViewModel(it) }) {
    val current by viewModel.current.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Execution") })
        if (current == null) {
            Text("No active execution", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
        val exec = current!!
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(exec.toolName.ifBlank { exec.toolId }, style = MaterialTheme.typography.titleLarge)
            Text("Status: ${exec.status.name.lowercase()}")
            if (exec.status == ExecutionStatus.RUNNING) LinearProgressIndicator(Modifier.fillMaxWidth())
            exec.currentNodeLabel?.let { Text("Current: $it") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pending != null) {
                    Button(onClick = viewModel::approve) { Text("Approve") }
                    OutlinedButton(onClick = viewModel::reject) { Text("Cancel") }
                }
                if (exec.status == ExecutionStatus.RUNNING || exec.status == ExecutionStatus.WAITING_APPROVAL) {
                    OutlinedButton(onClick = viewModel::stop) { Text("Stop") }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(exec.nodes, key = { it.nodeId }) { node ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(statusMark(node.status) + " " + node.label)
                    }
                }
            }
            items(exec.logs, key = { it.timestamp.toString() + it.message }) { log ->
                Text("${log.status}: ${log.message}${log.error?.let { " — $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
            }
        }
        exec.result?.error?.let {
            Text("Error: ${it.code} ${it.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        exec.result?.output?.let {
            Text("Result: $it", modifier = Modifier.padding(16.dp))
        }
    }
}

private fun statusMark(status: ExecutionStatus): String = when (status) {
    ExecutionStatus.COMPLETED -> "✓"
    ExecutionStatus.FAILED, ExecutionStatus.TIMEOUT, ExecutionStatus.CANCELLED -> "✗"
    ExecutionStatus.RUNNING -> "●"
    else -> "○"
}
