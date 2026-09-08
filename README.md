# Android AI Automation Agent

Local Android agent: a Kotlin Compose APK executes user-defined automation **tools** (workflows) through AccessibilityService. A Ktor backend on your PC owns the agent loop. [LM Studio](https://lmstudio.ai/) provides tool-calling inference on `localhost:1234`.

```text
User → Android chat → PC backend → LM Studio
                         ↓
                   tool call
                         ↓
              Android workflow engine
                         ↓
              AccessibilityService → apps
```

## Requirements

- JDK 17
- Android SDK (compile SDK 35)
- LM Studio with a **tool-calling** model loaded
- Phone and PC on the same Wi-Fi network

## Layout

| Path | Role |
|---|---|
| `android/app` | Jetpack Compose APK (`com.agent.mobile`) |
| `backend` | Ktor server on port `8787` |
| `shared` | Shared Kotlin models (Android + JVM) |
| `workflow-schema/workflow-v1.json` | Workflow JSON schema |
| `Docs/` | PRD and architecture |

## Run the backend

```bash
# Optional: point at LM Studio
set LMSTUDIO_URL=http://localhost:1234/v1
set LMSTUDIO_MODEL=           # leave empty to use the first loaded model

.\gradlew :backend:run
```

On startup the console prints a **6-digit pairing code** and LAN addresses. Open `http://127.0.0.1:8787/pair` or `GET /health`.

Start LM Studio first: **Developer → Start server** (or `lms server start`) on port `1234`, with tool calling enabled.

## Install the Android app

1. Open this folder in Android Studio, or:

   ```bash
   .\gradlew :android:app:installDebug
   ```

2. On the phone, enable **Settings → Accessibility → Android Agent Automation**.
3. In the app **Settings** tab, enter the PC IP, port `8787`, and pairing code, then **Connect**.

Cleartext HTTP on the LAN is allowed for this MVP.

## Demo

A sample user tool is seeded: `open_app_and_navigate`.

1. Confirm it appears under **Tools** (enabled, automatic policy is fine for the first demo).
2. In **Chat**, after pairing: `Open Settings and tap Battery` (or whatever label exists on your device).
3. The LLM should call `open_app_and_navigate` with `appName` / `elementText`.
4. The **Run** tab shows node progress. **Stop** cancels the workflow.

Second demo: create `instagram_post_story` in the linear workflow editor (Launch App → Find Element → Tap → …), set **Approval required**, then ask the agent to post a story.

You can also press the play icon on a tool to run it locally without the LLM.

## HTTP API

- `GET /health` — backend + LM Studio + device
- `GET /pair` — pairing page
- `POST /session` — `{ "code", "device" }` → `{ token, sessionId }`
- `POST /chat` — `{ "message" }` (Bearer token)
- `GET/POST /tools`, `PUT/DELETE /tools/{id}`
- `POST /tools/{id}/execute`, `GET /executions/{id}`
- `WS /ws?token=...` — agent events (`tool.execute`, `execution.status`, `execution.result`, `chat.message`)

## Environment

| Variable | Default |
|---|---|
| `AGENT_HOST` | `0.0.0.0` |
| `AGENT_PORT` | `8787` |
| `LMSTUDIO_URL` | `http://localhost:1234/v1` |
| `LMSTUDIO_MODEL` | first model from `/v1/models` |
| `AGENT_DB` | `backend/data/agent.db` |

API keys / device tokens are stored in Android EncryptedSharedPreferences. The LLM can only invoke **registered** tools, never arbitrary code or a raw accessibility tree as an action surface.
