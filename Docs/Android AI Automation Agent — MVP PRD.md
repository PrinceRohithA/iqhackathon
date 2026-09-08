# Android AI Automation Agent — MVP PRD

## 1. Product Overview

An Android application where an external LLM acts as the **main agent** and users provide the agent with capabilities through **automation tools**.

A tool is a user-defined visual workflow that can interact with Android applications.

The user can also use a set of built-in system tools.

Example:

> “Post this photo to my Instagram story.”

The LLM analyzes the request, selects the appropriate `instagram_post_story` tool, supplies its inputs, executes the workflow, observes the result, and reports completion.

The MVP focuses on proving this core loop.

---

## 2. Product Goal

Build the smallest working Android application that demonstrates:

```text
User request
    ↓
External LLM
    ↓
Select tool
    ↓
Execute visual automation workflow
    ↓
Return result
    ↓
LLM responds
```

The application should be designed so the automation engine can eventually support a much more capable autonomous agent.

---

## 3. MVP Scope

### Included

- Android application written in Kotlin
- Jetpack Compose UI
- External LLM API
- Agent chat interface
- Tool registry
- Built-in system tools
- User-created tools
- Visual workflow editor
- Android workflow execution
- Accessibility-based UI automation
- Workflow variables and inputs
- Tool execution policies
- Execution results
- Basic execution logs
- LLM tool calling
- LLM interaction with tool results
- Ability for the LLM to call multiple tools during a task

### Not Included

- Multi-agent architecture
- Local/on-device LLM
- Advanced visual AI perception
- Tool marketplace
- Cloud workflow synchronization
- Multi-device execution
- Complex memory system
- Automatic workflow generation
- Large-scale workflow search
- Advanced recovery/planning
- Desktop workflow editor

---

# 4. Core Concepts

## Agent

The LLM-powered component responsible for understanding the user's request and selecting/executing tools.

## Tool

A capability available to the agent.

There are two types:

### User Tool

Created by the user using the visual workflow editor.

Example:

```text
instagram_post_story
send_whatsapp_message
check_attendance
download_file
```

### System Tool

Built into the application.

Example:

```text
open_app
take_screenshot
get_device_info
read_notifications
```

---

# 5. Tool Model

Each tool contains:

```text
Tool
├── Name
├── Description
├── Input parameters
├── Output definition
├── Workflow
├── Required permissions
└── Execution policy
```

Example:

```json
{
  "name": "instagram_post_story",
  "description": "Posts an image as an Instagram story",
  "inputs": {
    "image": "image",
    "caption": "string"
  },
  "executionPolicy": "approval_required"
}
```

The LLM receives the available tool definitions and decides which tool to use.

For MVP, **all enabled tools are provided to the LLM** rather than implementing tool search/retrieval.

---

# 6. Agent Loop

The basic agent loop is:

```text
User
 ↓
LLM
 ↓
Tool selection
 ↓
Tool execution
 ↓
Execution result
 ↓
LLM
 ↓
Final response
```

The LLM must be able to call multiple tools when necessary.

Example:

```text
User:
"Check my delivery and tell Rahul whether it has arrived."

LLM
 ↓
check_delivery()
 ↓
result
 ↓
send_message()
 ↓
result
 ↓
Final response
```

---

# 7. Workflow Builder

Users manually create tools through a visual node editor.

MVP does not require workflow recording.

Example:

```text
[Launch App]
      ↓
[Find Element: Story]
      ↓
[Tap]
      ↓
[Find Element: Gallery]
      ↓
[Tap]
      ↓
[Select Input Image]
      ↓
[Tap Share]
      ↓
[Verify]
      ↓
[Return Result]
```

## MVP Nodes

### App

- Launch App

### UI

- Find Element
- Tap
- Long Press
- Swipe
- Type Text
- Read Text
- Back

### Control

- Wait
- Condition
- Loop

### Data

- Set Variable
- Input Parameter
- Return Result

### Observation

- Screenshot

---

# 8. UI Element Selection

The executor should primarily use semantic Android UI information rather than fixed coordinates.

Preferred order:

```text
Resource ID
↓
Accessibility ID / content description
↓
Text
↓
UI element properties
↓
Coordinates
```

Coordinates may be supported as a fallback.

Advanced OCR/image recognition is outside the MVP.

---

# 9. Workflow Execution Engine

The execution engine runs workflows locally on Android.

Primary implementation:

```text
AccessibilityService
```

The engine executes workflow nodes sequentially and maintains execution state.

Example:

```text
Workflow starts
 ↓
Node 1 executes
 ↓
Node 2 executes
 ↓
Node 3 executes
 ↓
...
 ↓
Workflow returns result
```

Each node should return:

```text
Success
Failure
Data
```

Example:

```json
{
  "success": true,
  "data": {
    "status": "posted"
  }
}
```

---

# 10. Agent ↔ Tool Interface

The LLM should never directly manipulate Android UI.

Instead:

```text
LLM
 ↓
Tool Call
 ↓
Tool Runtime
 ↓
Workflow
 ↓
Android
```

Example:

```json
{
  "tool": "instagram_post_story",
  "arguments": {
    "image": "selected_image"
  }
}
```

The application validates the call and executes the corresponding workflow.

---

# 11. Execution Policies

Every tool has a configurable execution policy.

### Automatic

The tool executes immediately.

### Approval Required

The agent pauses before executing and asks the user.

Example:

```text
Instagram Story
This tool will post content to Instagram.

[Approve] [Cancel]
```

### User Configurable

Users can change the policy for each tool.

Example:

```text
instagram_post_story
Execution: Approval Required
```

---

# 12. Intermediate Execution

The architecture supports both simple and observable execution.

### Normal execution

```text
LLM
 ↓
Tool
 ↓
Final result
```

### Observable execution

```text
LLM
 ↓
Tool
 ↓
Step/result
 ↓
LLM
 ↓
Continue
```

For the MVP, workflows should primarily execute deterministically while exposing execution status/results to the agent.

This allows future versions to support recovery and adaptive execution without redesigning the architecture.

---

# 13. Built-in System Tools

The MVP should include a very small set of system tools to validate the architecture.

Suggested initial tools:

```text
open_app
take_screenshot
read_current_screen
get_device_info
```

The architecture must allow additional system tools to be added later.

---

# 14. Main Screens

## Chat

Primary application screen.

```text
┌───────────────────────────────┐
│ AI Agent                      │
├───────────────────────────────┤
│                               │
│ You: Post this photo          │
│ as an Instagram story.        │
│                               │
│ Agent: Using                  │
│ instagram_post_story          │
│                               │
│ [Executing...]                │
│                               │
├───────────────────────────────┤
│ Message...                 ➤  │
└───────────────────────────────┘
```

## Tools

List all available tools.

```text
Tools

Instagram Story
Send WhatsApp Message
Open App
Take Screenshot

+ Create Tool
```

## Create/Edit Tool

Tool metadata:

```text
Name
Description
Inputs
Execution Policy
```

Then the visual workflow editor.

## Execution

Show:

```text
Current tool
Current node
Execution status
Errors
Result
```

---

# 15. Data Storage

MVP stores workflows locally on the device.

Recommended:

```text
Room / SQLite
```

Workflow definition can use a versioned JSON representation.

Example:

```json
{
  "version": 1,
  "id": "tool_001",
  "name": "send_message",
  "inputs": [
    {
      "name": "contact",
      "type": "string"
    },
    {
      "name": "message",
      "type": "string"
    }
  ],
  "nodes": []
}
```

This keeps workflows portable for future import/export and synchronization.

---

# 16. LLM Integration

The LLM is external.

The application sends:

```text
System instructions
+
Conversation
+
Available tool definitions
```

The LLM returns either:

```text
Normal response
```

or:

```text
Tool call
```

The application executes the tool and sends the result back to the LLM.

The LLM then produces the final response or calls another tool.

The LLM provider must be abstracted so providers can be changed later.

Example abstraction:

```text
LLMProvider
├── sendMessage()
├── getToolCalls()
└── processToolResult()
```

---

# 17. Permissions

The MVP requires the user to explicitly enable the permissions required by the automation system.

Primary:

```text
Accessibility Service
```

Potentially:

```text
MediaProjection / screen capture
```

Only request permissions when required.

Tool definitions should declare their required permissions.

---

# 18. Error Handling

Every workflow execution must be able to terminate with:

```text
SUCCESS
FAILED
CANCELLED
TIMEOUT
```

Example failure:

```text
Tool failed

Could not find:
"Share"

Reason:
Element not found

[Retry]
[Stop]
```

The tool result should be returned to the agent so the LLM can decide what to do next.

---

# 19. Security

The LLM must not receive unrestricted Android access.

The execution boundary is:

```text
LLM
 ↓
Registered Tool
 ↓
Validated Workflow
 ↓
Allowed Android Actions
```

The agent cannot dynamically execute arbitrary Kotlin code or shell commands in the MVP.

Sensitive tools use the configured approval policy.

API keys should be stored securely using Android's secure storage mechanisms.

---

# 20. Technical Architecture

```text
┌───────────────────────────────────────┐
│              Jetpack Compose          │
│                                       │
│ Chat | Tools | Workflow Editor        │
└───────────────────┬───────────────────┘
                    │
                    ▼
┌───────────────────────────────────────┐
│             Agent Runtime             │
│                                       │
│ Conversation Manager                  │
│ Tool Registry                         │
│ LLM Client                            │
│ Tool Call Handler                     │
│ Execution Manager                     │
└───────────────┬───────────────┬───────┘
                │               │
                ▼               ▼
       ┌──────────────┐  ┌──────────────┐
       │ LLM Provider │  │ Workflow DB  │
       └──────────────┘  └──────────────┘
                │
                ▼
       ┌──────────────────┐
       │ Workflow Engine  │
       └────────┬─────────┘
                │
                ▼
       ┌──────────────────┐
       │ Accessibility    │
       │ Service          │
       └────────┬─────────┘
                │
                ▼
             Android
```

---

# 21. Suggested Kotlin Modules

```text
app/
  ui/

core/
  agent/
  llm/
  tools/
  workflows/
  execution/

automation/
  accessibility/
  gestures/
  selectors/

data/
  database/
  repositories/
  models/

system/
  permissions/
```

The architecture should keep the workflow engine independent from the Compose UI.

---

# 22. MVP Success Criteria

The MVP is successful when a user can:

1. Open the Android app.
2. Configure an external LLM.
3. Create a tool manually using the visual workflow editor.
4. Define its inputs.
5. Save the tool.
6. See the tool in the agent's available tools.
7. Ask the agent to perform a task.
8. Have the LLM select the correct tool.
9. Have the workflow execute on Android.
10. Return the execution result to the LLM.
11. Receive a natural-language completion response.
12. Require approval for tools configured as approval-required.

### End-to-end demonstration

```text
User:
"Post this image as an Instagram story."

        ↓

LLM

        ↓

instagram_post_story(
    image = ...
)

        ↓

User approval
(if configured)

        ↓

Workflow Engine

        ↓

Open Instagram
Find Story
Select image
Post
Verify

        ↓

Result:
success = true

        ↓

LLM

        ↓

"Done — the story was posted."
```

---

# 23. Future Direction

The MVP architecture should leave room for:

```text
Tool recording
AI-generated workflows
OCR
Image understanding
Adaptive UI navigation
Tool discovery/search
Persistent agent memory
Cloud synchronization
Tool sharing/marketplace
Multi-device agents
On-device models
More Android system capabilities
Sensors and physical-world tools
```

The fundamental abstraction remains:

```text
LLM = Intelligence
Tools = Capabilities
Workflows = User-defined capabilities
Android Runtime = Execution
```

That separation is the foundation of the product.