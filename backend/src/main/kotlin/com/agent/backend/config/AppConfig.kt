package com.agent.backend.config

import java.io.File
import java.net.NetworkInterface

data class AppConfig(
    val host: String = env("AGENT_HOST") ?: "0.0.0.0",
    val port: Int = env("AGENT_PORT")?.toIntOrNull() ?: 8787,
    val lmStudioBaseUrl: String = env("LMSTUDIO_URL") ?: "http://localhost:1234/v1",
    val lmStudioModel: String = env("LMSTUDIO_MODEL") ?: "",
    val deepseekApiKey: String = env("DEEPSEEK_API_KEY") ?: "",
    val deepseekBaseUrl: String = env("DEEPSEEK_URL") ?: "https://api.deepseek.com",
    val deepseekModel: String = env("DEEPSEEK_MODEL") ?: "deepseek-chat",
    val geminiApiKey: String = env("GEMINI_API_KEY") ?: "",
    val geminiBaseUrl: String = env("GEMINI_URL") ?: "https://generativelanguage.googleapis.com/v1beta/openai",
    val geminiModel: String = env("GEMINI_MODEL") ?: "gemini-3.1-flash-lite",
    val dbPath: String = env("AGENT_DB") ?: "backend/data/agent.db",
    val apkPath: String = env("AGENT_APK_PATH") ?: "android/app/build/outputs/apk/debug/app-debug.apk",
) {
    val pairingCode: String = (100000..999999).random().toString()

    fun localAddresses(): List<String> = buildList {
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nic ->
                if (!nic.isUp || nic.isLoopback) return@forEach
                nic.inetAddresses.toList().forEach { addr ->
                    val host = addr.hostAddress ?: return@forEach
                    if (!addr.isLoopbackAddress && host.indexOf(':') < 0) add(host)
                }
            }
        }
        add("127.0.0.1")
    }.distinct()

    companion object {
        private val fileEnv: Map<String, String> = loadDotEnv()

        fun env(name: String): String? =
            System.getenv(name)?.ifBlank { null } ?: fileEnv[name]

        private fun loadDotEnv(): Map<String, String> {
            val files = listOf(File("backend/.env"), File(".env"))
            val file = files.firstOrNull { it.isFile } ?: return emptyMap()
            return file.readLines().mapNotNull { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
                val eq = line.indexOf('=')
                if (eq <= 0) return@mapNotNull null
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim().trim('"').trim('\'')
                key to value
            }.toMap()
        }
    }
}
