# Android AI Automation Agent
## Full System Architecture — MVP

**Version:** 1.0  
**Target:** Android APK + Local PC Backend + LM Studio  
**Primary Language:** Kotlin  
**LLM Runtime:** LM Studio on developer/user PC  
**Connectivity:** Local Wi-Fi / LAN

---

# 1. Architecture Summary

The system is a **mobile AI agent whose capabilities are represented as automation tools**.

The Android application provides:

- Agent chat
- Tool management
- Visual workflow builder
- Workflow execution
- Android UI automation
- Execution state and results

The PC provides:

- Backend server
- Agent orchestration
- LLM communication
- Tool-call processing
- Optional persistence/logging

LM Studio provides the local LLM.

```text
                         ┌───────────────────────┐
                         │       Android         │
                         │         APK           │
                         │                       │
                         │  Chat                 │
                         │  Tool Manager         │
                         │  Workflow Builder     │
                         │  Workflow Runtime     │
                         │  AccessibilitySvc     │
                         └───────────┬───────────┘
                                     │
                                  LAN/Wi-Fi
                                     │
                                     ▼
                         ┌───────────────────────┐
                         │      PC Backend       │
                         │                       │
                         │ API Server            │
                         │ Agent Runtime         │
                         │ Tool Registry         │
                         │ Session Manager       │
                         │ Workflow Manager      │
                         └───────────┬───────────┘
                                     │
                                     │ HTTP
                                     ▼
                         ┌───────────────────────┐
                         │       LM Studio      │
                         │                       │
                         │ Local LLM             │
                         │ Tool Calling          │
                         │ Structured Output     │
                         └───────────────────────┘
```

The Android app is the **device interface and execution environment**.

The PC is the **agent backend**.

LM Studio is the **reasoning engine**.

---

# 2. Core Design Principle

The system separates intelligence from capabilities.

```text
LLM
  = Intelligence / reasoning / planning

Tool
  = Capability

Workflow
  = Implementation of a capability

Android Runtime
  = Execution environment

Backend
  = Agent orchestration
```

The LLM does not directly manipulate Android.

Instead:

```text
User request
      ↓
LLM
      ↓
Tool call
      ↓
Backend
      ↓
Android
      ↓
Workflow
      ↓
Android UI
      ↓
Result
      ↓
Backend
      ↓
LLM
```

This separation is the central architectural boundary.

---

# 3. Example

User:

> Post this picture as an Instagram story.

Available tool:

```text
instagram_post_story
```

The LLM receives the tool definition and decides to call it.

```text
LLM
 │
 ├── tool = instagram_post_story
 └── image = selected image
        │
        ▼
Backend
        │
        ▼
Android Tool Runtime
        │
        ▼
Workflow
        │
        ├── Open Instagram
        ├── Find Story
        ├── Tap
        ├── Select Image
        ├── Tap Share
        └── Verify
        │
        ▼
success
        │
        ▼
LLM
        │
        ▼
"Done — the story was posted."
```

---

# 4. System Components

## 4.1 Android Application

The Android APK contains five major subsystems.

```text
Android App
├── UI
├── Agent Client
├── Tool Manager
├── Workflow Engine
└── Android Automation Layer
```

### UI

Jetpack Compose application interface.

Screens:

```text
Chat
Tools
Create Tool
Edit Tool
Workflow Editor
Execution
Settings
```

### Agent Client

Communicates with the PC backend.

Responsibilities:

- connect to backend
- send user messages
- receive assistant responses
- receive tool execution requests
- send execution results
- maintain connection state
- handle streaming events

### Tool Manager

Maintains tools installed on the device.

Responsibilities:

- create tools
- edit tools
- delete tools
- enable/disable tools
- expose tool metadata
- manage execution policies

### Workflow Engine

Executes the graph/sequence defined by a tool.

Responsibilities:

- load workflow
- resolve variables
- execute nodes
- maintain runtime state
- handle branches
- handle loops
- return results
- enforce timeouts

### Android Automation Layer

Interfaces with Android system capabilities.

Primary interface:

```text
AccessibilityService
```

Secondary interfaces:

```text
MediaProjection
Android Intents
PackageManager
Clipboard APIs
Notification APIs
Other Android APIs as required
```

---

# 5. PC Backend

The backend runs on the user's machine.

The backend is intentionally separate from Android so that the mobile app remains lightweight.

```text
PC Backend
├── HTTP API
├── WebSocket / Streaming
├── Agent Runtime
├── LLM Client
├── Tool Registry
├── Session Manager
├── Device Manager
└── Persistence
```

## HTTP API

Provides endpoints for:

```text
POST /session
POST /chat
GET  /tools
POST /tools
PUT  /tools/{id}
DELETE /tools/{id}
POST /tools/{id}/execute
GET  /executions/{id}
```

For real-time agent communication, WebSocket is recommended.

---

# 6. Device Connection

The Android phone connects to the PC through the local network.

Example:

```text
PC:
192.168.1.100:8787

Phone:
http://192.168.1.100:8787
```

The backend should provide a local discovery mechanism later.

For MVP, the user can manually enter the PC address or scan a QR code displayed by the backend.

Example:

```text
Connect Device

PC:
192.168.1.100

Port:
8787

[Connect]
```

A future version can support:

```text
mDNS
Bonjour
QR pairing
automatic discovery
```

---

# 7. LM Studio Integration

LM Studio runs on the PC and serves the local model through its API.

LM Studio currently provides a local server that can listen on localhost or the local network, supports OpenAI-compatible endpoints, and supports tool use through its API.

For the MVP, use the OpenAI-compatible API.

Default endpoint:

```text
http://localhost:1234/v1
```

LM Studio documents `/v1/chat/completions` and `/v1/responses`, and its OpenAI-compatible interface allows standard OpenAI clients to be pointed at LM Studio by changing the base URL.

The backend therefore contains:

```text
LLMClient
```

with:

```text
baseUrl
model
temperature
maxTokens
```

Example:

```text
LLMClient
   ↓
http://localhost:1234/v1/chat/completions
```

---

# 8. Why the LLM Should Stay on the PC

The Android application should not directly depend on a model implementation.

Instead:

```text
Android
   ↓
Backend
   ↓
LLM Provider
```

For the MVP:

```text
LLM Provider = LM Studio
```

Later:

```text
LM Studio
OpenAI
Anthropic
Gemini
Local llama.cpp
Ollama
Other OpenAI-compatible servers
```

This makes the agent architecture model-independent.

---

# 9. Agent Runtime

The agent runtime is hosted on the PC backend.

Responsibilities:

1. receive user input
2. maintain conversation
3. load available tools
4. send tool definitions to the LLM
5. process tool calls
6. route tool execution to Android
7. receive results
8. send results back to LLM
9. continue the agent loop
10. return final response

Architecture:

```text
User
 ↓
Android
 ↓
Backend
 ↓
Agent Runtime
 ↓
LLM
 ↓
Tool Call
 ↓
Tool Router
 ↓
Android Device
 ↓
Workflow Engine
 ↓
Result
 ↓
Agent Runtime
 ↓
LLM
 ↓
Final Answer
```

---

# 10. Tool Registry

The Tool Registry is the bridge between user-created automation and the LLM.

Each tool contains:

```text
id
name
description
type
inputSchema
outputSchema
workflowId
executionPolicy
permissions
enabled
```

Example:

```json
{
  "id": "tool_instagram_story",
  "name": "instagram_post_story",
  "description": "Post an image as an Instagram story",
  "inputSchema": {
    "type": "object",
    "properties": {
      "image": {
        "type": "string"
      }
    },
    "required": ["image"]
  },
  "outputSchema": {
    "type": "object",
    "properties": {
      "success": {
        "type": "boolean"
      },
      "status": {
        "type": "string"
      }
    }
  },
  "workflowId": "workflow_001",
  "executionPolicy": "approval_required",
  "enabled": true
}
```

---

# 11. Tool Types

## User Tools

Created using the visual workflow editor.

Examples:

```text
instagram_post_story
send_whatsapp_message
submit_assignment
book_ticket
open_application
```

## System Tools

Built into the platform.

Examples:

```text
open_app
take_screenshot
read_screen
get_device_info
```

The LLM receives both types.

For MVP:

```text
All enabled tools
        ↓
LLM tool definitions
```

No tool retrieval system is required.

---

# 12. Workflow Architecture

A workflow is a sequence/graph of nodes.

```text
Workflow
├── metadata
├── inputs
├── variables
├── nodes
└── outputs
```

Example:

```json
{
  "version": 1,
  "id": "workflow_001",
  "inputs": [
    {
      "name": "message",
      "type": "string"
    }
  ],
  "nodes": [
    {
      "id": "1",
      "type": "launch_app",
      "package": "..."
    },
    {
      "id": "2",
      "type": "find_element",
      "selector": {
        "text": "Messages"
      }
    },
    {
      "id": "3",
      "type": "tap"
    },
    {
      "id": "4",
      "type": "type_text",
      "value": "{{message}}"
    }
  ]
}
```

The schema must be versioned.

```text
workflow.version = 1
```

Future versions can migrate older workflows.

---

# 13. Workflow Nodes

MVP nodes:

### Application

```text
Launch App
```

### Interaction

```text
Find Element
Tap
Long Press
Swipe
Type Text
Read Text
Back
```

### Control Flow

```text
Wait
Condition
Loop
```

### Data

```text
Input Parameter
Set Variable
Return Result
```

### Observation

```text
Screenshot
```

---

# 14. Selectors

The workflow engine should prioritize semantic selectors.

Selector model:

```text
Selector
├── resourceId
├── text
├── contentDescription
├── className
└── index
```

Example:

```json
{
  "type": "find_element",
  "selector": {
    "text": "Send"
  }
}
```

Fallback:

```text
coordinate
```

Coordinates should not be the primary automation strategy.

---

# 15. Android Accessibility Layer

The AccessibilityService is the main executor.

Responsibilities:

```text
Inspect UI hierarchy
Find nodes
Click nodes
Long press nodes
Scroll
Type text
Perform gestures
Read visible text
Observe window changes
```

The workflow engine should never directly depend on implementation-specific AccessibilityService details.

Use an abstraction:

```kotlin
interface AndroidAutomation {
    suspend fun find(selector: Selector): UiElement?
    suspend fun tap(element: UiElement)
    suspend fun type(element: UiElement, text: String)
    suspend fun swipe(gesture: SwipeGesture)
    suspend fun readText(element: UiElement): String
}
```

Then:

```text
Workflow Engine
      ↓
AndroidAutomation
      ↓
AccessibilityAutomation
      ↓
AccessibilityService
```

This keeps the engine testable.

---

# 16. Workflow Execution Lifecycle

Every execution receives a unique ID.

```text
execution_id
```

Lifecycle:

```text
CREATED
   ↓
WAITING_APPROVAL
   ↓
RUNNING
   ↓
COMPLETED
```

Failure states:

```text
FAILED
CANCELLED
TIMEOUT
```

Example:

```json
{
  "executionId": "exec_1024",
  "toolId": "tool_instagram_story",
  "status": "running",
  "currentNode": "select_image"
}
```

---

# 17. Tool Approval

Every tool has an execution policy.

```text
automatic
approval_required
```

The architecture should allow additional policies later.

Example:

```text
User request
      ↓
LLM calls tool
      ↓
Policy check
      ↓
Approval required?
   /          \
 yes          no
 ↓             ↓
User          Execute
approval
 ↓
Execute
```

Approval is controlled by the tool configuration.

---

# 18. Agent Tool Execution

When the LLM asks for a tool:

```text
ToolCall
{
    toolName
    arguments
}
```

Backend performs:

```text
1. Validate tool exists
2. Validate arguments
3. Validate tool enabled
4. Check execution policy
5. Check device connection
6. Send execution request
7. Track execution
8. Receive result
9. Return result to LLM
```

The LLM must never be allowed to directly issue arbitrary workflow instructions.

---

# 19. Android ↔ Backend Protocol

Use JSON messages.

### Tool execution request

```json
{
  "type": "tool.execute",
  "executionId": "exec_001",
  "toolId": "instagram_post_story",
  "inputs": {
    "image": "..."
  }
}
```

### Execution status

```json
{
  "type": "execution.status",
  "executionId": "exec_001",
  "status": "running",
  "nodeId": "node_04"
}
```

### Execution result

```json
{
  "type": "execution.result",
  "executionId": "exec_001",
  "status": "success",
  "output": {
    "posted": true
  }
}
```

### Execution error

```json
{
  "type": "execution.result",
  "executionId": "exec_001",
  "status": "failed",
  "error": {
    "code": "ELEMENT_NOT_FOUND",
    "message": "Could not find Send button"
  }
}
```

---

# 20. Intermediate Agent Observation

The architecture supports intermediate execution information.

Two modes:

### Simple

```text
LLM
 ↓
Tool
 ↓
Complete result
```

### Observable

```text
LLM
 ↓
Tool
 ↓
Execution event
 ↓
Agent
 ↓
Continue
```

MVP should implement execution events but does not require the LLM to reason over every node.

Example events:

```text
workflow.started
node.started
node.completed
node.failed
workflow.completed
workflow.failed
```

This creates the foundation for future adaptive agents.

---

# 21. State Management

There are three distinct state layers.

## Conversation State

Managed by backend.

```text
session
messages
tool calls
tool results
```

## Agent State

```text
current task
active tool
pending approval
execution IDs
```

## Workflow State

Maintained locally on Android.

```text
current node
variables
loop counters
execution status
```

These should remain separate.

---

# 22. Persistence

## Android

Store:

```text
Tools
Workflows
User settings
Execution history
Device configuration
```

Recommended:

```text
Room
```

## PC

Store:

```text
Agent sessions
Conversation metadata
Tool metadata/cache
Execution records
Backend configuration
```

SQLite is sufficient for MVP.

---

# 23. Package Structure

## Android

```text
com.agent.mobile

├── ui
│   ├── chat
│   ├── tools
│   ├── workflow
│   ├── execution
│   └── settings
│
├── agent
│   ├── AgentClient
│   ├── Session
│   └── events
│
├── tools
│   ├── Tool
│   ├── ToolRegistry
│   └── SystemTools
│
├── workflow
│   ├── Workflow
│   ├── WorkflowNode
│   ├── WorkflowEngine
│   ├── WorkflowParser
│   └── RuntimeContext
│
├── automation
│   ├── AndroidAutomation
│   ├── AccessibilityAutomation
│   ├── selectors
│   └── gestures
│
├── accessibility
│   └── AgentAccessibilityService
│
├── data
│   ├── database
│   ├── repositories
│   └── models
│
└── core
    ├── networking
    ├── serialization
    └── logging
```

---

# 24. Backend Package Structure

Recommended backend language:

```text
Kotlin
```

Use:

```text
Ktor
```

for the HTTP/WebSocket server.

Structure:

```text
backend/

├── api/
│   ├── routes/
│   ├── websocket/
│   └── dto/
│
├── agent/
│   ├── AgentRuntime
│   ├── ConversationManager
│   ├── ToolCallProcessor
│   └── ExecutionCoordinator
│
├── llm/
│   ├── LLMProvider
│   └── LMStudioProvider
│
├── tools/
│   ├── ToolRegistry
│   ├── ToolSchema
│   └── SystemTools
│
├── devices/
│   ├── DeviceManager
│   └── DeviceConnection
│
├── executions/
│   ├── ExecutionManager
│   └── ExecutionState
│
├── persistence/
│   ├── Database
│   └── repositories
│
└── config/
```

---

# 25. Shared Models

Create a shared Kotlin module containing:

```text
ToolDefinition
ToolInput
ToolOutput
Workflow
WorkflowNode
WorkflowExecution
ExecutionStatus
AgentEvent
DeviceInfo
```

Architecture:

```text
Android
   │
   ├──── shared models ────┐
   │                       │
Backend ───────────────────┘
```

This prevents API-model duplication.

---

# 26. Network Architecture

```text
                    Local Network

┌─────────────┐                  ┌─────────────┐
│   Android   │                  │     PC      │
│             │                  │             │
│ Agent UI    │◄──── HTTP ─────►│ Ktor API    │
│ Workflow    │◄── WebSocket ──►│ Agent       │
│ Executor    │                  │ Runtime     │
└─────────────┘                  └──────┬──────┘
                                        │
                                      HTTP
                                        │
                                        ▼
                                ┌──────────────┐
                                │  LM Studio   │
                                │ localhost    │
                                │ :1234        │
                                └──────────────┘
```

LM Studio can also serve over the local network when configured to do so, but keeping the Android app connected to **our backend** rather than directly to LM Studio gives us a cleaner security and abstraction boundary.

---

# 27. Security Model

The PC backend is reachable only on the local network.

The MVP should still implement pairing.

Recommended flow:

```text
PC starts
 ↓
Generates pairing code / QR
 ↓
Phone scans
 ↓
Phone establishes device identity
 ↓
Backend issues device token
 ↓
Authenticated communication
```

Every request should include the device token.

LM Studio can optionally be configured to require API-token authentication; its documentation notes that authentication is not required by default but can be enabled in server settings.

---

# 28. LLM Tool Schema

The backend converts application tools into the LLM provider's tool schema.

Example:

```json
{
  "type": "function",
  "function": {
    "name": "instagram_post_story",
    "description": "Post an image as an Instagram story",
    "parameters": {
      "type": "object",
      "properties": {
        "image": {
          "type": "string",
          "description": "Image to post"
        }
      },
      "required": ["image"]
    }
  }
}
```

LM Studio supports tool calling through its OpenAI-compatible endpoints.

For workflow validation and future agent operations, structured JSON responses can also be enforced through JSON schemas supported by LM Studio's compatible API.

---

# 29. Agent System Prompt

The backend provides the LLM with rules similar to:

```text
You are an Android task agent.

Use available tools to complete the user's request.

Rules:
1. Select the tool that best matches the task.
2. Provide valid arguments.
3. Do not invent tools.
4. Never claim a task succeeded without a successful tool result.
5. If a tool fails, interpret the result and decide whether another action is appropriate.
6. Respect tool approval requirements.
```

The tool list is injected dynamically.

---

# 30. Tool Result Handling

A tool result should be structured.

Example:

```json
{
  "success": true,
  "output": {
    "status": "posted"
  }
}
```

Failure:

```json
{
  "success": false,
  "error": {
    "code": "TIMEOUT",
    "message": "Workflow exceeded 30 seconds"
  }
}
```

The agent runtime feeds the result back to the LLM.

---

# 31. Workflow Runtime Rules

The runtime must guarantee:

- sequential execution
- deterministic node execution
- variable resolution
- timeout protection
- cancellation
- failure propagation
- result generation
- permission checks

Example:

```text
Node A
 ↓ success
Node B
 ↓ success
Node C
 ↓ failure
Workflow Failed
```

Conditions create branching:

```text
           Condition
          /         \
       true         false
        ↓             ↓
     Node A        Node B
```

---

# 32. Cancellation

The user must be able to stop an executing workflow.

```text
Agent execution

[Stop]
```

Cancellation propagation:

```text
Android UI
 ↓
WorkflowEngine.cancel()
 ↓
Current node cancellation
 ↓
Workflow termination
 ↓
Backend notified
 ↓
LLM notified
```

---

# 33. Timeouts

Every workflow has:

```text
workflow timeout
node timeout
```

Example:

```text
Workflow timeout = 60 seconds
Node timeout = 10 seconds
```

Timeouts prevent a stuck UI action from hanging the agent indefinitely.

---

# 34. Logging

Every execution should have an execution log.

```text
09:32:10 Workflow started
09:32:11 Launch Instagram
09:32:13 Find Story
09:32:14 Tap Story
09:32:17 Select image
09:32:20 Tap Share
09:32:23 Verification successful
09:32:23 Workflow completed
```

For debugging, store:

```text
execution ID
node ID
timestamp
status
error
optional screenshot
```

---

# 35. Observability

The MVP should provide an execution screen.

Example:

```text
Instagram Story

● Launch Instagram
● Find Story
● Select Image
● Tap Share
✓ Verify Posted

Status: Completed
Duration: 8.4s
```

This is useful both for debugging and for user trust.

---

# 36. Workflow Editor

The editor is a node-based canvas.

Basic node representation:

```text
┌───────────────────┐
│ Launch App        │
│ Instagram         │
└─────────┬─────────┘
          ↓
┌───────────────────┐
│ Find Element      │
│ "Your Story"      │
└─────────┬─────────┘
          ↓
┌───────────────────┐
│ Tap               │
└───────────────────┘
```

Users can:

```text
Add node
Delete node
Reorder node
Edit node
Connect branches
Set parameters
Set variables
Define inputs
```

The MVP can implement a linear editor first and evolve to a full graph editor.

---

# 37. Tool Creation Flow

```text
Tools
 ↓
Create Tool
 ↓
Name
 ↓
Description
 ↓
Define Inputs
 ↓
Define Approval Policy
 ↓
Open Workflow Editor
 ↓
Build workflow
 ↓
Save
 ↓
Validate
 ↓
Register Tool
```

Validation should detect:

```text
missing inputs
invalid nodes
unconnected branches
undefined variables
invalid selectors
missing permissions
```

---

# 38. Built-in System Tools

The system should register a small number of tools automatically.

Example:

```text
open_app
take_screenshot
read_current_screen
get_device_info
```

These are represented in the same Tool Registry abstraction as user tools.

The LLM therefore doesn't need a different mechanism for system versus user capabilities.

---

# 39. Agent Conversation

The chat should show both natural language and important tool activity.

Example:

```text
User

Post this photo as my Instagram story.

Agent

I'll use Instagram Story.

[Tool: instagram_post_story]
[Running...]

✓ Completed

Done — your story was posted.
```

For failures:

```text
Agent

I couldn't complete the Instagram Story workflow.

The "Share" button was not found.
```

---

# 40. Error Recovery

MVP recovery should remain simple.

On failure:

```text
Workflow failed
       ↓
Return structured error
       ↓
LLM receives error
       ↓
LLM decides:
   retry
   use another tool
   ask user
   stop
```

Example:

```text
Tool:
instagram_post_story

Result:
ELEMENT_NOT_FOUND

LLM:
Could not verify posting.
Ask user.
```

Advanced autonomous recovery is V2.

---

# 41. Image and File Inputs

For workflows requiring files/images, the Android app should resolve local content into a transferable reference.

Example:

```text
User selects image
 ↓
Android stores/identifies image
 ↓
Tool input references image
 ↓
Workflow uses image
```

For MVP, use a local URI/reference rather than designing a complete distributed file storage system.

---

# 42. Backend File Handling

The backend should not become the permanent file store in V1.

File transfer should be explicit.

Possible flow:

```text
Android
 ↓
Temporary upload
 ↓
Backend
 ↓
Tool execution
```

Temporary files should have:

```text
TTL
execution ID
content type
size limit
```

---

# 43. Model Abstraction

Create:

```kotlin
interface LLMProvider
```

Example:

```kotlin
interface LLMProvider {
    suspend fun chat(request: ChatRequest): ChatResponse
}
```

Implementation:

```text
LMStudioProvider
```

Future:

```text
OpenAIProvider
AnthropicProvider
GeminiProvider
OllamaProvider
```

The Agent Runtime never depends directly on LM Studio.

---

# 44. LM Studio Configuration

Backend configuration:

```text
LLM Provider: LM Studio

Base URL:
http://localhost:1234/v1

Model:
<loaded LM Studio model>
```

LM Studio supports local model serving through its Developer interface or `lms server start`, and its documented default server port is `1234`.

A health-check endpoint in our backend should verify:

```text
LM Studio reachable
Model available
Tool calling supported
```

---

# 45. Backend Startup

The MVP backend should have a simple startup sequence:

```text
Start Agent Backend
        ↓
Check configuration
        ↓
Check LM Studio
        ↓
Load model information
        ↓
Initialize database
        ↓
Register tools
        ↓
Start API
        ↓
Show pairing QR
```

---

# 46. Android Startup

```text
Launch APK
 ↓
Load local database
 ↓
Check AccessibilityService
 ↓
Check backend connection
 ↓
Pair if required
 ↓
Load tools
 ↓
Ready
```

If AccessibilityService is disabled:

```text
Automation access required

[Open Accessibility Settings]
```

---

# 47. End-to-End Request Sequence

```text
1. User enters request
   ↓
2. Android → Backend
   ↓
3. Backend loads session + tools
   ↓
4. Backend → LM Studio
   ↓
5. LLM returns tool call
   ↓
6. Backend validates call
   ↓
7. Backend checks approval policy
   ↓
8. Android receives execution request
   ↓
9. Workflow Engine executes
   ↓
10. Android sends result
   ↓
11. Backend → LM Studio
   ↓
12. LLM produces final response
   ↓
13. Backend → Android
   ↓
14. User sees result
```

---

# 48. Sequence Diagram

```text
User
 │
 │ request
 ▼
Android
 │
 │ chat message
 ▼
Backend
 │
 │ prompt + tools
 ▼
LM Studio
 │
 │ tool call
 ▼
Backend
 │
 │ execution request
 ▼
Android
 │
 │ workflow
 ▼
AccessibilityService
 │
 │ Android actions
 ▼
Target App
 │
 │ result/state
 ▼
Workflow Engine
 │
 │ tool result
 ▼
Backend
 │
 │ tool result
 ▼
LM Studio
 │
 │ final answer
 ▼
Backend
 │
 │ final answer
 ▼
Android
 │
 ▼
User
```

---

# 49. Responsibility Boundaries

## Android owns

```text
UI
Workflow creation
Workflow storage
Workflow execution
Accessibility
Android permissions
Device state
Execution events
```

## Backend owns

```text
Agent loop
Conversation
LLM communication
Tool orchestration
Device sessions
Execution coordination
Authentication
Backend persistence
```

## LM Studio owns

```text
Model inference
Reasoning
Tool selection
Response generation
```

This boundary should remain strict.

---

# 50. MVP Technology Stack

## Android

```text
Kotlin
Jetpack Compose
Coroutines
Flow
Room
Kotlin Serialization
AccessibilityService
MediaProjection where required
OkHttp / Ktor Client
```

## Backend

```text
Kotlin
Ktor
Coroutines
WebSockets
Kotlin Serialization
SQLite
```

## LLM

```text
LM Studio
OpenAI-compatible API
Tool Calling
Structured Outputs where useful
```

LM Studio supports both its native REST API and OpenAI-compatible endpoints; the latter is preferable here because it keeps our LLM adapter portable.

---

# 51. Repository Structure

A monorepo is recommended.

```text
android-agent/
│
├── android/
│   └── app/
│
├── backend/
│   └── server/
│
├── shared/
│   └── models/
│
├── workflow-schema/
│   └── workflow-v1.json
│
├── docs/
│   └── architecture.md
│
└── README.md
```

This makes it easy to develop Android and backend together.

---

# 52. MVP Development Order

## Phase 1 — Android foundation

Build:

```text
Compose app
AccessibilityService
basic navigation
database
```

## Phase 2 — Workflow engine

Implement:

```text
Workflow schema
Node model
Executor
Selectors
Basic Android actions
```

## Phase 3 — Workflow editor

Implement:

```text
Create Tool
Input definitions
Node editor
Save workflow
Validate workflow
```

## Phase 4 — Backend

Implement:

```text
Ktor server
Pairing
WebSocket
Device management
Tool synchronization
```

## Phase 5 — LM Studio

Implement:

```text
LMStudioProvider
Tool schemas
Tool calling
Agent loop
```

## Phase 6 — Integration

Complete:

```text
User
 ↓
LLM
 ↓
Tool
 ↓
Workflow
 ↓
Android
 ↓
Result
 ↓
LLM
```

## Phase 7 — Hardening

Add:

```text
Approvals
timeouts
cancellation
logging
errors
reconnection
```

---

# 53. MVP Demo

The first complete demo should be a simple user-created automation.

Example:

```text
Tool:
open_app_and_navigate
```

Workflow:

```text
Launch App
↓
Find Element
↓
Tap
↓
Return Result
```

Then demonstrate:

```text
User:
"Open Instagram and go to my profile."

LLM
 ↓
open_instagram_profile()
 ↓
Workflow
 ↓
Android
 ↓
Success
 ↓
LLM
 ↓
"Done."
```

A second demo can be:

```text
instagram_post_story
```

which demonstrates inputs, approval policy, execution and result handling.

---

# 54. MVP Non-Goals

Do not build these initially:

```text
Autonomous screen exploration
OCR
Computer vision
AI-generated workflows
Workflow recording
Marketplace
Cloud accounts
Multi-user backend
Remote internet control
Long-term memory
Multi-agent planning
On-device LLM
```

The architecture should support them later, but the implementation should stay focused.

---

# 55. Future Architecture

The architecture should eventually evolve toward:

```text
                       Agent
                         │
              ┌──────────┼──────────┐
              │          │          │
           Planning    Memory     Tools
              │          │          │
              └──────────┼──────────┘
                         │
                   Tool Registry
                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
 Android Workflows    Web Tools        Native APIs
       │
       ▼
 Android
```

Possible future tool implementations:

```text
Workflow
API
MCP
Native Android capability
Browser automation
Cloud service
Other device
```

The LLM should eventually see a unified capability model regardless of implementation.

---

# 56. Key Architectural Decision

The most important decision in this project is:

> **A workflow is a tool, not merely an automation script.**

That means every workflow is designed to be consumed by an agent.

```text
Workflow
   ↓
Tool Metadata
   ↓
LLM Tool Schema
   ↓
Agent
```

This lets users effectively **teach the agent new capabilities** without changing the agent itself.

---

# 57. Final MVP Architecture

```text
┌─────────────────────────────────────────────────────────┐
│                     ANDROID APK                         │
│                                                         │
│  ┌──────────┐   ┌─────────────┐   ┌────────────────┐  │
│  │ Chat UI  │   │ Tool Manager│   │ Workflow Editor│  │
│  └────┬─────┘   └──────┬──────┘   └───────┬────────┘  │
│       │                │                  │            │
│       └────────────────┼──────────────────┘            │
│                        ▼                               │
│                ┌──────────────┐                       │
│                │ Agent Client │                       │
│                └──────┬───────┘                       │
│                       │                               │
│                ┌──────▼───────┐                       │
│                │ Workflow     │                       │
│                │ Engine       │                       │
│                └──────┬───────┘                       │
│                       │                               │
│                ┌──────▼───────┐                       │
│                │ Accessibility│                       │
│                │ Service      │                       │
│                └──────┬───────┘                       │
└───────────────────────┼─────────────────────────────────┘
                        │
                     LAN/Wi-Fi
                        │
┌───────────────────────▼─────────────────────────────────┐
│                       PC BACKEND                         │
│                                                         │
│  ┌────────────┐    ┌──────────────┐                    │
│  │ Ktor API   │───►│ Agent Runtime│                    │
│  └────────────┘    └──────┬───────┘                    │
│                            │                            │
│                     ┌──────▼──────┐                     │
│                     │ Tool Router │                     │
│                     └──────┬──────┘                     │
│                            │                            │
│                     ┌──────▼──────┐                     │
│                     │ LLM Client  │                     │
│                     └──────┬──────┘                     │
└────────────────────────────┼────────────────────────────┘
                             │
                          HTTP API
                             │
                    ┌────────▼─────────┐
                    │    LM STUDIO     │
                    │                  │
                    │ Local LLM        │
                    │ Tool Calling     │
                    └──────────────────┘
```

---

# 58. Architectural Principle

The resulting platform can be summarized in one sentence:

> **A local AI agent running on a PC uses an LLM served by LM Studio to reason over user-defined Android automation tools, while a Kotlin Android application executes those tools directly on the user's device.**

This architecture gives us a clean MVP while preserving the path toward a full **user-programmable AI agent for Android**.