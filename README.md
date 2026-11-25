# Browser Agent — Star Browser X

An Android mobile AI browser written in Kotlin with Jetpack Compose and Material Design 3. It combines a privacy-focused browsing experience (Chromium-based WebView, tracker blocking, mobile sandbox) with an autonomous page agent and a multi-provider LLM studio that can read and act on the page you are viewing.

## Features

- **Autonomous page agent** — a task engine (`TrueAgentEngine`) that plans, calls tools, and drives the browser to complete multi-step tasks on the current page.
- **Multi-provider LLM studio** — pluggable chat backends (`MultiProviderBrain`, `LlmBrain`, `ProviderDialectClients`) supporting multiple model providers, with per-provider request dialects and streaming responses.
- **MCP connector** — `McpConnectorClient` for connecting external Model Context Protocol tool servers to the agent.
- **Tool registry** — a sandboxed set of browser tools the agent may call (`ToolRegistry`).
- **Browser core** — tabbed browsing with a DOM bridge for page access (`DomBridge`), built-in tracker/ad blocking (`TrackerBlocker`), tab switcher, bookmarks, history and downloads.
- **Mobile sandbox** — `MobileSandboxManager` isolates agent-executed operations from the host browser session.
- **Local persistence** — Room database (`StarDatabase`) for history/bookmarks/tabs and DataStore preferences (`BrowserPreferences`) for settings.
- **Voice input** — microphone permission and composer voice capture.
- **Theming** — Material 3 dynamic theming with light/dark modes and bundled Inter, Plus Jakarta Sans and JetBrains Mono fonts.

## Tech stack

| Component | Choice |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Min SDK / Target SDK | 24 / 36 |
| Java compatibility | 11 |
| Persistence | Room (KSP), DataStore Preferences |
| Networking | OkHttp |
| Secrets | Secrets Gradle Plugin (`.env` at build time) |
| Testing | JUnit, Robolectric, Android instrumented tests |

## Project structure

```
app/src/main/java/com/example/
├── MainActivity.kt          # App entry point
├── agent/                   # Agent engine, brains, MCP, tool registry
├── browser/                 # DOM bridge, tracker blocker
├── data/
│   ├── local/               # Room database
│   └── preferences/         # DataStore preferences
├── sandbox/                 # Mobile sandbox manager
└── ui/
    ├── components/          # Compose screens and components
    ├── theme/               # Colors, themes, typography
    └── viewmodel/           # View models and run coordination
```

## Configuration

Copy `.env.example` to `.env` and fill in your keys before building:

```
# GEMINI_API_KEY: Required for Gemini AI API calls.
GEMINI_API_KEY=your_key_here
```

The Secrets Gradle Plugin reads `.env` at build time; keys are **not** packaged if left unset. `.env` is git-ignored and never committed.

## Building

Open the project in Android Studio (or run `gradle :app:assembleDebug` with a local Gradle installation — the wrapper JAR is not included in this archive, only `gradle/wrapper/gradle-wrapper.properties`). Release builds expect a signing keystore configured via the `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD` environment variables.

## Running tests

```
gradle :app:testDebugUnitTest        # Unit + Robolectric tests
gradle :app:connectedDebugAndroidTest # Instrumented tests (device/emulator required)
```
