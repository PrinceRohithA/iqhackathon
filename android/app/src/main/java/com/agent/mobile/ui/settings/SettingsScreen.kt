package com.agent.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agent.mobile.ui.appViewModel
import com.agent.shared.model.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = appViewModel { SettingsViewModel(it) }) {
    val host by viewModel.host.collectAsStateWithLifecycle()
    val port by viewModel.port.collectAsStateWithLifecycle()
    val code by viewModel.code.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(title = { Text("Settings") })
        Column(
            Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("PC backend", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = host,
                onValueChange = viewModel::updateHost,
                label = { Text("Host / IP") },
                placeholder = { Text("10.109.32.248") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = port,
                onValueChange = viewModel::updatePort,
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = code,
                onValueChange = viewModel::updateCode,
                label = { Text("Pairing code") },
                placeholder = { Text("256828") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            Text("Connection: ${connection.name.lowercase().replace('_', ' ')}")
            Button(
                onClick = viewModel::connect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                Text(
                    when {
                        busy -> "Connecting…"
                        connection == ConnectionState.CONNECTED -> "Reconnect"
                        else -> "Connect"
                    },
                )
            }
            OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Text("Disconnect")
            }
            OutlinedButton(onClick = viewModel::refreshHealth, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                Text("Check health")
            }
            health?.let {
                Text("LM Studio: ${if (it.lmStudio.reachable) it.lmStudio.model ?: "reachable" else it.lmStudio.error ?: "offline"}")
                Text("Device connected (server): ${it.deviceConnected}")
            }
            message?.let { text ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (text.startsWith("Connected")) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                ) {
                    Text(text, modifier = Modifier.padding(12.dp))
                }
            }

            Text("Automation access", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                if (viewModel.accessibilityEnabled) "Accessibility service is enabled"
                else "Automation access required. Enable Android Agent in Accessibility settings.",
            )
            Button(onClick = { context.startActivity(accessibilitySettingsIntent()) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Accessibility Settings")
            }
        }
    }
}
