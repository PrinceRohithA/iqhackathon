package com.agent.mobile.core

import android.content.Context
import androidx.room.Room
import com.agent.mobile.agent.AgentClient
import com.agent.mobile.agent.ChatStore
import com.agent.mobile.automation.AccessibilityAutomation
import com.agent.mobile.copilot.CopilotSession
import com.agent.mobile.data.AgentDatabase
import com.agent.mobile.data.ExecutionRepository
import com.agent.mobile.data.SettingsRepository
import com.agent.mobile.data.ToolRepository
import com.agent.mobile.execution.ExecutionManager
import com.agent.mobile.tools.SystemToolExecutor
import com.agent.mobile.tools.ToolRegistry
import com.agent.mobile.workflow.WorkflowEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AgentDatabase = Room.databaseBuilder(
        appContext,
        AgentDatabase::class.java,
        "agent.db",
    ).fallbackToDestructiveMigration().build()

    val settings: SettingsRepository = SettingsRepository(appContext)
    val toolRepository: ToolRepository = ToolRepository(database.toolDao())
    val executionRepository: ExecutionRepository = ExecutionRepository(database.executionDao())
    val toolRegistry: ToolRegistry = ToolRegistry(toolRepository)
    val automation: AccessibilityAutomation = AccessibilityAutomation(appContext)
    val workflowEngine: WorkflowEngine = WorkflowEngine(automation)
    val systemTools: SystemToolExecutor = SystemToolExecutor(appContext, automation)
    val chatStore: ChatStore = ChatStore()
    val agentClient: AgentClient = AgentClient(settings, scope)
    val copilotSession: CopilotSession = CopilotSession(appContext, automation, agentClient, scope)
    val executionManager: ExecutionManager = ExecutionManager(
        toolRegistry = toolRegistry,
        workflowEngine = workflowEngine,
        systemTools = systemTools,
        executionRepository = executionRepository,
        agentClient = agentClient,
        chatStore = chatStore,
        scope = scope,
    )

    fun start() {
        scope.launch {
            toolRegistry.ensureSystemTools()
        }
        copilotSession.startListening()
        executionManager.start()
        scope.launch {
            val s = settings.snapshot()
            if (s.host.isNotBlank() && s.token.isNotBlank()) {
                agentClient.connect(s.host, s.port, s.token)
            }
        }
    }
}
