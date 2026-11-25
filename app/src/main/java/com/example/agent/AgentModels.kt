package com.example.agent

import org.json.JSONArray
import org.json.JSONObject

enum class ProviderDialect(val label: String) {
    OPENAI_COMPAT("OpenAI Compatible"),
    ANTHROPIC("Anthropic Messages"),
    GEMINI_REST("Gemini REST")
}

enum class ExecutionMode(val label: String, val shortLabel: String, val description: String) {
    AUTO("Autonomous", "Auto", "Runs browser and file tools directly; asks before destructive or external calls."),
    APPROVE("Approval (HITL)", "Approve", "Human-in-the-Loop confirmation before every mutating or external action."),
    ASK("Ask Only", "Ask", "Answers using current page context without mutating actions.")
}

enum class ToolRiskLevel(val label: String) {
    READ_ONLY("Read"),
    NAVIGATION("Navigate"),
    MUTATING("Action"),
    DESTRUCTIVE_OR_EXTERNAL("Sensitive")
}

enum class ApprovalDecision {
    APPROVE_ONCE,
    ALLOW_FOR_THREAD,
    DENY
}

enum class ToolkitId(val label: String, val description: String) {
    BROWSER("Browser Engine", "Navigate tabs, inspect DOM refs (@e1, @e2), click, type, and scroll."),
    SANDBOX("Mobile Sandbox", "Read, write, and organize files inside the isolated workspace."),
    WEB_FETCH("HTTP Web Fetch", "Fetch public web APIs and documents when Sandbox Internet is enabled."),
    DEVICE("Device & Clipboard", "Copy outputs to clipboard and manage active browser tabs.")
}

enum class McpTransport(val label: String) {
    JSON_RPC_HTTP("MCP JSON-RPC (HTTP)"),
    REST_WEBHOOK("REST Webhook")
}

data class LlmProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val apiKey: String = "",
    val dialect: ProviderDialect = ProviderDialect.OPENAI_COMPAT,
    val isKeyless: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsVision: Boolean = false,
    val isBuiltIn: Boolean = false
)

data class SystemPromptPreset(
    val id: String,
    val name: String,
    val prompt: String,
    val isBuiltIn: Boolean = false
)

data class AgentSkill(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val triggerKeywords: List<String> = emptyList(),
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false
)

data class McpToolInfo(
    val name: String,
    val description: String,
    val inputSchemaJson: String = "{}"
)

data class McpConnectorConfig(
    val id: String,
    val name: String,
    val endpointUrl: String,
    val transport: McpTransport = McpTransport.JSON_RPC_HTTP,
    val authHeaderName: String = "Authorization",
    val authHeaderValue: String = "",
    val enabled: Boolean = true,
    val discoveredTools: List<McpToolInfo> = emptyList()
)

data class PendingApprovalAction(
    val id: String,
    val callId: String = "",
    val toolName: String,
    val riskLevel: ToolRiskLevel = ToolRiskLevel.MUTATING,
    val summary: String,
    val targetUrlOrPath: String,
    val argumentsPreview: String
)

data class CitationItem(
    val title: String,
    val url: String
)

object AgentDefaults {

    val defaultProviders: List<LlmProviderConfig> = listOf(
        LlmProviderConfig(
            id = "kilo-gateway",
            name = "Kilo Code Gateway",
            baseUrl = "https://api.kilo.ai/api/gateway/chat/completions",
            modelId = "kilo-auto/free",
            apiKey = "",
            dialect = ProviderDialect.OPENAI_COMPAT,
            isKeyless = true,
            supportsReasoning = true,
            supportsVision = false,
            isBuiltIn = true
        ),
        LlmProviderConfig(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            modelId = "gpt-4.1-mini",
            apiKey = "",
            dialect = ProviderDialect.OPENAI_COMPAT,
            isKeyless = false,
            supportsReasoning = false,
            supportsVision = true,
            isBuiltIn = true
        ),
        LlmProviderConfig(
            id = "anthropic",
            name = "Anthropic",
            baseUrl = "https://api.anthropic.com/v1/messages",
            modelId = "claude-sonnet-4-20250514",
            apiKey = "",
            dialect = ProviderDialect.ANTHROPIC,
            isKeyless = false,
            supportsReasoning = true,
            supportsVision = true,
            isBuiltIn = true
        ),
        LlmProviderConfig(
            id = "openrouter",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/chat/completions",
            modelId = "openrouter/auto",
            apiKey = "",
            dialect = ProviderDialect.OPENAI_COMPAT,
            isKeyless = false,
            supportsReasoning = true,
            supportsVision = true,
            isBuiltIn = true
        ),
        LlmProviderConfig(
            id = "groq",
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1/chat/completions",
            modelId = "llama-3.3-70b-versatile",
            apiKey = "",
            dialect = ProviderDialect.OPENAI_COMPAT,
            isKeyless = false,
            supportsReasoning = false,
            supportsVision = false,
            isBuiltIn = true
        ),
        LlmProviderConfig(
            id = "google-gemini",
            name = "Google Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/models",
            modelId = "gemini-2.5-flash",
            apiKey = "",
            dialect = ProviderDialect.GEMINI_REST,
            isKeyless = false,
            supportsReasoning = true,
            supportsVision = true,
            isBuiltIn = true
        ),
        LlmProviderConfig(
            id = "ollama-local",
            name = "Ollama Local",
            baseUrl = "http://127.0.0.1:11434/v1/chat/completions",
            modelId = "llama3.2",
            apiKey = "",
            dialect = ProviderDialect.OPENAI_COMPAT,
            isKeyless = true,
            supportsReasoning = false,
            supportsVision = false,
            isBuiltIn = true
        )
    )

    val defaultPrompts: List<SystemPromptPreset> = listOf(
        SystemPromptPreset(
            id = "true-agent",
            name = "Autonomous Agent",
            prompt = """
You are the built-in Agent inside Star Browser.
Be direct, accurate, and concise. Answer general questions directly without unnecessary preamble.
Only claim a browser or file action succeeded if a structured tool call confirmed it; otherwise report the real error or user denial.
Format replies cleanly with Markdown and [Title](https://url) links when citing sources.
            """.trimIndent(),
            isBuiltIn = true
        ),
        SystemPromptPreset(
            id = "research-analyst",
            name = "Research Analyst",
            prompt = """
You are the Agent in Research Analyst mode. Prioritize primary sources, verify details, highlight uncertainties honestly, and present structured findings with [Source Title](https://url) citations.
            """.trimIndent(),
            isBuiltIn = true
        ),
        SystemPromptPreset(
            id = "concise-operator",
            name = "Concise Operator",
            prompt = """
You are the Agent in Concise Operator mode. Execute tasks directly with minimal prose and report verified results in 2-5 crisp lines.
            """.trimIndent(),
            isBuiltIn = true
        )
    )

    val defaultSkills: List<AgentSkill> = listOf(
        AgentSkill(
            id = "skill-page-brief",
            name = "Page Summarizer & Citations",
            description = "Distills long articles into key takeaways with clickable section citations.",
            instructions = "When summarizing a webpage, structure output as Overview, Key Points, and Verified Source Links using [Title](url) Markdown links.",
            triggerKeywords = listOf("summarize", "summary", "tldr", "key points", "takeaways"),
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-form-navigator",
            name = "Interactive Form Operator",
            description = "Guides multi-step search, filtering, and form interactions using DOM snapshots.",
            instructions = "Always call browser_snapshot first to discover fresh @eN element refs before using browser_click, browser_fill, or browser_select, then re-snapshot after navigation.",
            triggerKeywords = listOf("search", "click", "fill", "login", "filter", "form", "open", "navigate"),
            enabled = true,
            isBuiltIn = true
        ),
        AgentSkill(
            id = "skill-sandbox-notes",
            name = "Sandbox Note Archiver",
            description = "Saves structured research notes, JSON exports, or snippets to the mobile sandbox.",
            instructions = "When saving outputs or notes, store clean Markdown or JSON files inside the mobile sandbox via sandbox_write and report the exact saved filename.",
            triggerKeywords = listOf("save", "note", "export", "file", "sandbox", "write"),
            enabled = true,
            isBuiltIn = true
        )
    )

    val defaultEnabledToolkits: Set<ToolkitId> = setOf(
        ToolkitId.BROWSER,
        ToolkitId.SANDBOX,
        ToolkitId.WEB_FETCH,
        ToolkitId.DEVICE
    )

    fun serializeProviders(items: List<LlmProviderConfig>): String {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("baseUrl", p.baseUrl)
                put("modelId", p.modelId)
                put("apiKey", p.apiKey)
                put("dialect", p.dialect.name)
                put("isKeyless", p.isKeyless)
                put("supportsReasoning", p.supportsReasoning)
                put("supportsVision", p.supportsVision)
                put("isBuiltIn", p.isBuiltIn)
            })
        }
        return arr.toString()
    }

    fun deserializeProviders(json: String?): List<LlmProviderConfig> {
        if (json.isNullOrBlank()) return defaultProviders
        return runCatching {
            val arr = JSONArray(json)
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        LlmProviderConfig(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            baseUrl = o.getString("baseUrl"),
                            modelId = o.getString("modelId"),
                            apiKey = o.optString("apiKey", ""),
                            dialect = runCatching { ProviderDialect.valueOf(o.optString("dialect", "OPENAI_COMPAT")) }
                                .getOrDefault(ProviderDialect.OPENAI_COMPAT),
                            isKeyless = o.optBoolean("isKeyless", false),
                            supportsReasoning = o.optBoolean("supportsReasoning", false),
                            supportsVision = o.optBoolean("supportsVision", false),
                            isBuiltIn = o.optBoolean("isBuiltIn", false)
                        )
                    )
                }
            }
            list.ifEmpty { defaultProviders }
        }.getOrDefault(defaultProviders)
    }

    fun serializePrompts(items: List<SystemPromptPreset>): String {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("prompt", p.prompt)
                put("isBuiltIn", p.isBuiltIn)
            })
        }
        return arr.toString()
    }

    fun deserializePrompts(json: String?): List<SystemPromptPreset> {
        if (json.isNullOrBlank()) return defaultPrompts
        return runCatching {
            val arr = JSONArray(json)
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SystemPromptPreset(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            prompt = o.getString("prompt"),
                            isBuiltIn = o.optBoolean("isBuiltIn", false)
                        )
                    )
                }
            }
            list.ifEmpty { defaultPrompts }
        }.getOrDefault(defaultPrompts)
    }

    fun serializeSkills(items: List<AgentSkill>): String {
        val arr = JSONArray()
        items.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("description", s.description)
                put("instructions", s.instructions)
                put("triggerKeywords", JSONArray(s.triggerKeywords))
                put("enabled", s.enabled)
                put("isBuiltIn", s.isBuiltIn)
            })
        }
        return arr.toString()
    }

    fun deserializeSkills(json: String?): List<AgentSkill> {
        if (json.isNullOrBlank()) return defaultSkills
        return runCatching {
            val arr = JSONArray(json)
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val kwArr = o.optJSONArray("triggerKeywords") ?: JSONArray()
                    val keywords = buildList {
                        for (k in 0 until kwArr.length()) add(kwArr.optString(k))
                    }.filter { it.isNotBlank() }
                    add(
                        AgentSkill(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            description = o.optString("description", ""),
                            instructions = o.optString("instructions", ""),
                            triggerKeywords = keywords,
                            enabled = o.optBoolean("enabled", true),
                            isBuiltIn = o.optBoolean("isBuiltIn", false)
                        )
                    )
                }
            }
            list.ifEmpty { defaultSkills }
        }.getOrDefault(defaultSkills)
    }

    fun serializeConnectors(items: List<McpConnectorConfig>): String {
        val arr = JSONArray()
        items.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("endpointUrl", c.endpointUrl)
                put("transport", c.transport.name)
                put("authHeaderName", c.authHeaderName)
                put("authHeaderValue", c.authHeaderValue)
                put("enabled", c.enabled)
                put("discoveredTools", JSONArray().apply {
                    c.discoveredTools.forEach { t ->
                        put(JSONObject().apply {
                            put("name", t.name)
                            put("description", t.description)
                            put("inputSchemaJson", t.inputSchemaJson)
                        })
                    }
                })
            })
        }
        return arr.toString()
    }

    fun deserializeConnectors(json: String?): List<McpConnectorConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val toolsArr = o.optJSONArray("discoveredTools") ?: JSONArray()
                    val tools = buildList {
                        for (j in 0 until toolsArr.length()) {
                            val to = toolsArr.getJSONObject(j)
                            add(
                                McpToolInfo(
                                    name = to.getString("name"),
                                    description = to.optString("description", ""),
                                    inputSchemaJson = to.optString("inputSchemaJson", "{}")
                                )
                            )
                        }
                    }
                    add(
                        McpConnectorConfig(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            endpointUrl = o.getString("endpointUrl"),
                            transport = runCatching { McpTransport.valueOf(o.optString("transport", "JSON_RPC_HTTP")) }
                                .getOrDefault(McpTransport.JSON_RPC_HTTP),
                            authHeaderName = o.optString("authHeaderName", "Authorization"),
                            authHeaderValue = o.optString("authHeaderValue", ""),
                            enabled = o.optBoolean("enabled", true),
                            discoveredTools = tools
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun serializeCitations(items: List<CitationItem>): String {
        val arr = JSONArray()
        items.forEach { c ->
            arr.put(JSONObject().apply {
                put("title", c.title)
                put("url", c.url)
            })
        }
        return arr.toString()
    }

    fun deserializeCitations(json: String?): List<CitationItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(CitationItem(o.getString("title"), o.getString("url")))
                }
            }
        }.getOrDefault(emptyList())
    }
}
