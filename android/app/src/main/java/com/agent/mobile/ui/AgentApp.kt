package com.agent.mobile.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agent.mobile.ui.chat.ChatScreen
import com.agent.mobile.ui.execution.ExecutionScreen
import com.agent.mobile.ui.navigation.Routes
import com.agent.mobile.ui.settings.SettingsScreen
import com.agent.mobile.ui.tools.ToolEditScreen
import com.agent.mobile.ui.tools.ToolsScreen
import com.agent.mobile.ui.workflow.WorkflowEditorScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AgentApp() {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab(Routes.Chat, "Chat", Icons.Outlined.ChatBubbleOutline),
        Tab(Routes.Tools, "Tools", Icons.Outlined.Handyman),
        Tab(Routes.Execution, "Run", Icons.Outlined.PlayCircleOutline),
        Tab(Routes.Settings, "Settings", Icons.Outlined.Settings),
    )
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = current in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Chat,
            modifier = Modifier.padding(padding).imePadding(),
        ) {
            composable(Routes.Chat) { ChatScreen(onOpenExecution = { navController.navigate(Routes.Execution) }) }
            composable(Routes.Tools) {
                ToolsScreen(
                    onCreate = { navController.navigate(Routes.toolEdit("new")) },
                    onEdit = { navController.navigate(Routes.toolEdit(it)) },
                )
            }
            composable(Routes.Execution) { ExecutionScreen() }
            composable(Routes.Settings) { SettingsScreen() }
            composable(
                Routes.ToolEdit,
                arguments = listOf(navArgument("toolId") { type = NavType.StringType }),
            ) { entry ->
                val toolId = entry.arguments?.getString("toolId") ?: "new"
                ToolEditScreen(
                    toolId = toolId,
                    onBack = { navController.popBackStack() },
                    onOpenEditor = { id ->
                        navController.navigate(Routes.workflowEditor(id)) {
                            popUpTo(Routes.Tools)
                        }
                    },
                )
            }
            composable(
                Routes.WorkflowEditor,
                arguments = listOf(navArgument("toolId") { type = NavType.StringType }),
            ) { entry ->
                val toolId = entry.arguments?.getString("toolId") ?: return@composable
                WorkflowEditorScreen(
                    toolId = toolId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
