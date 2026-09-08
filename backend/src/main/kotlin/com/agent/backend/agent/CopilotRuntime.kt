package com.agent.backend.agent

import com.agent.backend.devices.DeviceManager
import com.agent.backend.devices.DeviceSession
import com.agent.backend.llm.LLMProvider
import com.agent.backend.llm.LlmChatRequest
import com.agent.shared.AgentJson
import com.agent.shared.model.ChatMessage
import com.agent.shared.model.CopilotAction
import com.agent.shared.model.ProtocolMessage
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.util.UUID

class CopilotRuntime(
    private val llm: LLMProvider,
    private val devices: DeviceManager,
) {
    private val json = AgentJson.instance
    private val log = LoggerFactory.getLogger(CopilotRuntime::class.java)

    suspend fun handleObserve(session: DeviceSession, observe: ProtocolMessage.CopilotObserve) {
        val jpegBytes = observe.imageJpegBase64?.length ?: 0
        log.info(
            "copilot.observe session={} device={} turn={} step={} goal='{}' screen={}x{} jpegChars={} treeChars={}",
            session.sessionId,
            session.device.deviceId,
            observe.turnId,
            observe.step,
            observe.goal.take(200),
            observe.screenWidth,
            observe.screenHeight,
            jpegBytes,
            observe.screenTree.length,
        )
        if (observe.imageJpegBase64.isNullOrBlank()) {
            log.warn("copilot.observe turn={} has NO screenshot from the phone", observe.turnId)
        } else {
            val kb = observe.imageJpegBase64!!.length * 3 / 4 / 1024
            log.info(
                "copilot.observe turn={} screenshot FROM PHONE ok ~{}KB (base64Chars={}). It is attached on the user message for Gemini.",
                observe.turnId,
                kb,
                jpegBytes,
            )
        }
        log.debug("copilot.observe tree preview:\n{}", observe.screenTree.take(1_200))
        val system = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "system",
            content = SYSTEM_PROMPT,
            timestamp = System.currentTimeMillis(),
        )
        val jpeg = observe.imageJpegBase64
        val user = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = userPrompt(observe),
            imageJpegBase64 = jpeg,
            timestamp = System.currentTimeMillis(),
        )
        val plan = try {
            var parsed = askModel(observe.turnId, listOf(system, user))
            if (isEmptyGreeting(parsed)) {
                log.warn("copilot.plan turn={} was empty/greeting, retrying with a stricter instruction", observe.turnId)
                val retry = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    content = RETRY_PROMPT + "\n\n" + userPrompt(observe),
                    imageJpegBase64 = jpeg,
                    timestamp = System.currentTimeMillis(),
                )
                parsed = askModel(observe.turnId, listOf(system, retry))
            }
            if (isEmptyGreeting(parsed)) {
                parsed.copy(
                    speak = "Need a real tap from the on-screen tree",
                    done = false,
                    needNextScreenshot = true,
                    error = "Model returned no actions",
                )
            } else {
                parsed
            }
        } catch (e: Exception) {
            log.error("copilot.llm turn={} failed: {}", observe.turnId, e.message, e)
            ProtocolMessage.CopilotPlan(
                turnId = observe.turnId,
                speak = "Could not plan the next step",
                done = true,
                error = e.message ?: "LLM error",
            )
        }
        log.info(
            "copilot.plan turn={} speak='{}' done={} needNextScreenshot={} actions={} error={}",
            plan.turnId,
            plan.speak.take(200),
            plan.done,
            plan.needNextScreenshot,
            plan.actions.joinToString { action ->
                "${action.type}(x=${action.x},y=${action.y},dir=${action.direction},text=${action.text})"
            }.ifBlank { "(none)" },
            plan.error,
        )
        devices.send(session, plan)
        log.debug("copilot.plan turn={} sent to device {}", observe.turnId, session.device.deviceId)
    }

    private suspend fun askModel(turnId: String, messages: List<ChatMessage>): ProtocolMessage.CopilotPlan {
        log.debug("copilot.observe turn={} calling Gemini", turnId)
        val started = System.currentTimeMillis()
        val response = llm.chat(LlmChatRequest(messages, jsonMode = true))
        log.info(
            "copilot.llm turn={} model={} elapsedMs={} rawChars={} preview={}",
            turnId,
            response.model,
            System.currentTimeMillis() - started,
            response.content?.length ?: 0,
            response.content.orEmpty().replace("\n", " ").take(400),
        )
        return parsePlan(turnId, response.content)
    }

    private fun isEmptyGreeting(plan: ProtocolMessage.CopilotPlan): Boolean {
        if (plan.actions.isNotEmpty()) return false
        val speak = plan.speak.lowercase()
        return plan.done || speak.contains("hello") || speak.isBlank()
    }

    private fun userPrompt(observe: ProtocolMessage.CopilotObserve): String = buildString {
        appendLine("Operate the phone to complete this goal. You can see a screenshot of the current screen plus the accessibility tree. Do not greet. Do not finish unless the goal is already done.")
        appendLine("User goal: ${observe.goal}")
        appendLine("Step: ${observe.step}")
        appendLine("Screen size in pixels: ${observe.screenWidth}x${observe.screenHeight}")
        appendLine("Tap coordinates are 0-1000. Center of bounds [l,t][r,b] is x=((l+r)/2)*1000/${observe.screenWidth}, y=((t+b)/2)*1000/${observe.screenHeight}.")
        appendLine("To type, first tap the field then use {\"type\":\"type\",\"x\":..,\"y\":..,\"text\":\"the value\"}.")
        appendLine("Visible labels:")
        appendLine(visibleLabels(observe.screenTree).ifBlank { "(none)" })
        appendLine("Accessibility tree:")
        append(observe.screenTree.ifBlank { "(empty)" }.take(8_000))
    }

    private fun visibleLabels(tree: String): String {
        val labels = linkedSetOf<String>()
        Regex("""text=(.*?) desc=""").findAll(tree).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank() && value != "null") labels += value
        }
        Regex("""desc=(.*?) id=""").findAll(tree).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank() && value != "null") labels += value
        }
        return labels.take(40).joinToString(" | ")
    }

    private fun parsePlan(turnId: String, raw: String?): ProtocolMessage.CopilotPlan {
        val text = raw.orEmpty()
        val jsonText = extractJson(text) ?: run {
            log.warn("copilot.parse turn={} no JSON in model output: {}", turnId, text.take(400))
            return ProtocolMessage.CopilotPlan(
                turnId = turnId,
                speak = text.ifBlank { "I could not decide the next gesture." },
                done = true,
                error = "Model did not return JSON",
            )
        }
        val obj = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrElse { err ->
            log.warn("copilot.parse turn={} invalid JSON: {} body={}", turnId, err.message, jsonText.take(400))
            return ProtocolMessage.CopilotPlan(turnId = turnId, speak = text, done = true, error = "Invalid JSON")
        }
        val actions = obj["actions"]?.jsonArray.orEmpty().mapNotNull { el ->
            val a = el.jsonObject
            val type = a["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            CopilotAction(
                type = type,
                x = a["x"]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() },
                y = a["y"]?.jsonPrimitive?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() },
                direction = a["direction"]?.jsonPrimitive?.contentOrNull,
                text = a["text"]?.jsonPrimitive?.contentOrNull,
                durationMs = a["durationMs"]?.jsonPrimitive?.longOrNull
                    ?: a["durationMs"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong(),
            )
        }
        return ProtocolMessage.CopilotPlan(
            turnId = turnId,
            speak = obj["speak"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            needNextScreenshot = obj["needNextScreenshot"]?.jsonPrimitive?.booleanOrNull ?: false,
            done = obj["done"]?.jsonPrimitive?.booleanOrNull ?: false,
            actions = actions,
        )
    }

    private fun extractJson(text: String): String? {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        if (!fenced.isNullOrBlank()) return fenced.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) return text.substring(start, end + 1)
        return null
    }

    companion object {
        const val RETRY_PROMPT = """
The previous JSON was invalid because it had no actions (for example speak Hello and done true).
Pick one visible control from the tree, convert its bounds to 0-1000, and return JSON with at least one tap/swipe/type.
done must be false unless the goal is already complete.
"""

        const val SYSTEM_PROMPT = """
You are a phone operator. You control an Android device with taps and swipes.

You receive a screenshot of the current screen and an accessibility tree. Use both. Each tree node has text/desc and pixel bounds [left,top][right,bottom].

Return ONLY JSON:
{
  "speak": "Tapping Settings",
  "needNextScreenshot": true,
  "done": false,
  "actions": [
    {"type":"tap","x":500,"y":820},
    {"type":"type","x":500,"y":400,"text":"hello world"}
  ]
}

Allowed actions: tap, swipe, type, long_press, back, home, wait.
x and y are 0-1000 of the screen (0,0 top-left). Use the center of a node's bounds.
To type into a field, include type+x+y+text so the phone taps the field then types.

Rules:
- Never greet. Never say Hello. Never return empty actions.
- One or two gestures per turn. Then set needNextScreenshot true.
- done=true only after the user goal is actually finished. Empty actions + done true is a failure.
- If you need to open an app, tap the icon/label that matches the goal.
- JSON only. No markdown.
"""
    }
}
