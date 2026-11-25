package com.example.agent

import org.json.JSONArray
import org.json.JSONObject

enum class ParamType(val jsonSchemaType: String) {
    STRING("string"),
    INTEGER("integer"),
    BOOLEAN("boolean"),
    OBJECT("object")
}

data class ToolParamSpec(
    val name: String,
    val type: ParamType,
    val description: String,
    val required: Boolean = false,
    val enumValues: List<String> = emptyList()
)

data class ToolDefinition(
    val canonicalName: String,
    val wireName: String,
    val toolkit: ToolkitId,
    val description: String,
    val parameters: List<ToolParamSpec>,
    val isMutating: Boolean,
    val riskLevel: ToolRiskLevel = if (isMutating) ToolRiskLevel.MUTATING else ToolRiskLevel.READ_ONLY,
    val timeoutMs: Long = 15_000L
)

data class ValidatedToolCall(
    val callId: String,
    val canonicalName: String,
    val wireName: String,
    val arguments: JSONObject,
    val definition: ToolDefinition
)

data class ToolExecutionRecord(
    val callId: String,
    val toolName: String,
    val argumentsJson: String,
    val success: Boolean,
    val timedOut: Boolean,
    val outputOrError: String,
    val durationMs: Long,
    val skippedByUser: Boolean = false
)

sealed class ToolValidationResult {
    data class Valid(val call: ValidatedToolCall) : ToolValidationResult()
    data class Invalid(val canonicalName: String, val reason: String) : ToolValidationResult()
}

object ToolRegistry {

    private val refRegex = Regex("^@?e[1-9][0-9]*$")

    val builtInTools: List<ToolDefinition> = listOf(
        ToolDefinition(
            canonicalName = "browser.open",
            wireName = "browser_open",
            toolkit = ToolkitId.BROWSER,
            description = "Open a URL or domain in the browser tab and wait for load completion.",
            parameters = listOf(
                ToolParamSpec("url", ParamType.STRING, "Full URL or domain (e.g. https://www.youtube.com)", required = true),
                ToolParamSpec("new_tab", ParamType.BOOLEAN, "Open in a new tab instead of active tab", required = false)
            ),
            isMutating = true,
            riskLevel = ToolRiskLevel.NAVIGATION,
            timeoutMs = 20_000L
        ),
        ToolDefinition(
            canonicalName = "browser.snapshot",
            wireName = "browser_snapshot",
            toolkit = ToolkitId.BROWSER,
            description = "Inspect the active page DOM, readable text, and interactive element refs (@e1, @e2, ...).",
            parameters = emptyList(),
            isMutating = false,
            timeoutMs = 10_000L
        ),
        ToolDefinition(
            canonicalName = "browser.get_text",
            wireName = "browser_get_text",
            toolkit = ToolkitId.BROWSER,
            description = "Read the clean extracted text content of the current webpage.",
            parameters = emptyList(),
            isMutating = false,
            timeoutMs = 10_000L
        ),
        ToolDefinition(
            canonicalName = "browser.click",
            wireName = "browser_click",
            toolkit = ToolkitId.BROWSER,
            description = "Click an interactive element identified by its @eN ref from browser_snapshot.",
            parameters = listOf(
                ToolParamSpec("ref", ParamType.STRING, "Element ref such as @e1", required = true)
            ),
            isMutating = true,
            timeoutMs = 10_000L
        ),
        ToolDefinition(
            canonicalName = "browser.fill",
            wireName = "browser_fill",
            toolkit = ToolkitId.BROWSER,
            description = "Replace the value of an input or textarea element identified by @eN.",
            parameters = listOf(
                ToolParamSpec("ref", ParamType.STRING, "Element ref such as @e2", required = true),
                ToolParamSpec("value", ParamType.STRING, "Text value to fill", required = true)
            ),
            isMutating = true,
            timeoutMs = 10_000L
        ),
        ToolDefinition(
            canonicalName = "browser.type",
            wireName = "browser_type",
            toolkit = ToolkitId.BROWSER,
            description = "Append text to an input element identified by @eN.",
            parameters = listOf(
                ToolParamSpec("ref", ParamType.STRING, "Element ref such as @e2", required = true),
                ToolParamSpec("value", ParamType.STRING, "Text to append", required = true)
            ),
            isMutating = true,
            timeoutMs = 10_000L
        ),
        ToolDefinition(
            canonicalName = "browser.press",
            wireName = "browser_press",
            toolkit = ToolkitId.BROWSER,
            description = "Press a keyboard key such as Enter, Escape, or Tab on the active page.",
            parameters = listOf(
                ToolParamSpec("key", ParamType.STRING, "Key name (Enter, Escape, Tab, ArrowDown, ArrowUp)", required = true)
            ),
            isMutating = true,
            timeoutMs = 8_000L
        ),
        ToolDefinition(
            canonicalName = "browser.select",
            wireName = "browser_select",
            toolkit = ToolkitId.BROWSER,
            description = "Select an option in a <select> dropdown identified by @eN.",
            parameters = listOf(
                ToolParamSpec("ref", ParamType.STRING, "Select element ref such as @e4", required = true),
                ToolParamSpec("value", ParamType.STRING, "Option value or label", required = true)
            ),
            isMutating = true,
            timeoutMs = 10_000L
        ),
        ToolDefinition(
            canonicalName = "browser.check",
            wireName = "browser_check",
            toolkit = ToolkitId.BROWSER,
            description = "Set a checkbox or radio button (@eN) to checked or unchecked.",
            parameters = listOf(
                ToolParamSpec("ref", ParamType.STRING, "Checkbox or radio ref such as @e5", required = true),
                ToolParamSpec("checked", ParamType.BOOLEAN, "Desired checked state", required = true)
            ),
            isMutating = true,
            timeoutMs = 10_000L
        ),
        ToolDefinition(
            canonicalName = "browser.scroll",
            wireName = "browser_scroll",
            toolkit = ToolkitId.BROWSER,
            description = "Scroll the active webpage or scroll an element ref into view.",
            parameters = listOf(
                ToolParamSpec("direction", ParamType.STRING, "Scroll direction", required = true, enumValues = listOf("up", "down", "left", "right")),
                ToolParamSpec("pixels", ParamType.INTEGER, "Scroll distance in pixels (100-3000)", required = false),
                ToolParamSpec("ref", ParamType.STRING, "Optional element ref to scroll into view", required = false)
            ),
            isMutating = false,
            timeoutMs = 8_000L
        ),
        ToolDefinition(
            canonicalName = "browser.back",
            wireName = "browser_back",
            toolkit = ToolkitId.BROWSER,
            description = "Navigate back in the active browser tab history.",
            parameters = emptyList(),
            isMutating = true,
            riskLevel = ToolRiskLevel.NAVIGATION,
            timeoutMs = 15_000L
        ),
        ToolDefinition(
            canonicalName = "browser.forward",
            wireName = "browser_forward",
            toolkit = ToolkitId.BROWSER,
            description = "Navigate forward in the active browser tab history.",
            parameters = emptyList(),
            isMutating = true,
            riskLevel = ToolRiskLevel.NAVIGATION,
            timeoutMs = 15_000L
        ),
        ToolDefinition(
            canonicalName = "browser.reload",
            wireName = "browser_reload",
            toolkit = ToolkitId.BROWSER,
            description = "Reload the active browser tab.",
            parameters = emptyList(),
            isMutating = true,
            riskLevel = ToolRiskLevel.NAVIGATION,
            timeoutMs = 20_000L
        ),
        ToolDefinition(
            canonicalName = "browser.wait",
            wireName = "browser_wait",
            toolkit = ToolkitId.BROWSER,
            description = "Pause briefly to allow dynamic webpage content to settle.",
            parameters = listOf(
                ToolParamSpec("ms", ParamType.INTEGER, "Wait duration in milliseconds (100-10000)", required = true)
            ),
            isMutating = false,
            timeoutMs = 12_000L
        ),
        ToolDefinition(
            canonicalName = "sandbox.list",
            wireName = "sandbox_list",
            toolkit = ToolkitId.SANDBOX,
            description = "List all files stored in the mobile sandbox workspace.",
            parameters = emptyList(),
            isMutating = false,
            timeoutMs = 8_000L
        ),
        ToolDefinition(
            canonicalName = "sandbox.read",
            wireName = "sandbox_read",
            toolkit = ToolkitId.SANDBOX,
            description = "Read a text file from the isolated mobile sandbox workspace.",
            parameters = listOf(
                ToolParamSpec("path", ParamType.STRING, "Relative file path inside sandbox", required = true)
            ),
            isMutating = false,
            timeoutMs = 8_000L
        ),
        ToolDefinition(
            canonicalName = "sandbox.write",
            wireName = "sandbox_write",
            toolkit = ToolkitId.SANDBOX,
            description = "Write or append text to a file in the mobile sandbox workspace.",
            parameters = listOf(
                ToolParamSpec("path", ParamType.STRING, "Relative file path inside sandbox", required = true),
                ToolParamSpec("content", ParamType.STRING, "File text content", required = true),
                ToolParamSpec("append", ParamType.BOOLEAN, "Append instead of overwriting", required = false)
            ),
            isMutating = true,
            timeoutMs = 8_000L
        ),
        ToolDefinition(
            canonicalName = "sandbox.delete",
            wireName = "sandbox_delete",
            toolkit = ToolkitId.SANDBOX,
            description = "Delete a file from the mobile sandbox workspace.",
            parameters = listOf(
                ToolParamSpec("path", ParamType.STRING, "Relative file path inside sandbox", required = true)
            ),
            isMutating = true,
            riskLevel = ToolRiskLevel.DESTRUCTIVE_OR_EXTERNAL,
            timeoutMs = 8_000L
        ),
        ToolDefinition(
            canonicalName = "web.fetch",
            wireName = "web_fetch",
            toolkit = ToolkitId.WEB_FETCH,
            description = "Fetch text or JSON from a public HTTPS/HTTP endpoint when Sandbox Internet is enabled.",
            parameters = listOf(
                ToolParamSpec("url", ParamType.STRING, "HTTP or HTTPS URL to fetch", required = true)
            ),
            isMutating = false,
            timeoutMs = 15_000L
        ),
        ToolDefinition(
            canonicalName = "device.clipboard_copy",
            wireName = "device_clipboard_copy",
            toolkit = ToolkitId.DEVICE,
            description = "Copy text to the Android system clipboard.",
            parameters = listOf(
                ToolParamSpec("text", ParamType.STRING, "Text to place on clipboard", required = true)
            ),
            isMutating = true,
            timeoutMs = 5_000L
        )
    )

    private val byWireOrCanonical: Map<String, ToolDefinition> = buildMap {
        builtInTools.forEach { def ->
            put(def.wireName, def)
            put(def.canonicalName, def)
        }
    }

    fun availableTools(
        mode: ExecutionMode,
        enabledToolkits: Set<ToolkitId>,
        mcpConnectors: List<McpConnectorConfig>,
        sandboxInternetEnabled: Boolean
    ): List<ToolDefinition> {
        val base = builtInTools.filter { tool ->
            tool.toolkit in enabledToolkits &&
                (mode != ExecutionMode.ASK || !tool.isMutating) &&
                (tool.canonicalName != "web.fetch" || sandboxInternetEnabled)
        }
        val dynamicMcp = if (mode == ExecutionMode.ASK) {
            emptyList()
        } else {
            mcpConnectors.filter { it.enabled }.flatMap { conn ->
                conn.discoveredTools.map { t ->
                    val safeWire = "mcp_${sanitizeName(conn.id)}_${sanitizeName(t.name)}"
                    ToolDefinition(
                        canonicalName = "mcp.${conn.name}.${t.name}",
                        wireName = safeWire,
                        toolkit = ToolkitId.WEB_FETCH,
                        description = "[MCP: ${conn.name}] ${t.description.ifBlank { t.name }}",
                        parameters = listOf(
                            ToolParamSpec("arguments", ParamType.OBJECT, "JSON arguments for ${t.name}", required = false)
                        ),
                        isMutating = true,
                        riskLevel = ToolRiskLevel.DESTRUCTIVE_OR_EXTERNAL,
                        timeoutMs = 20_000L
                    )
                }
            }
        }
        return base + dynamicMcp
    }

    private val urlLikeRegex = Regex(
        """(https?://|www\.|\blocalhost(:\d+)?\b|\b127\.0\.0\.1(:\d+)?\b|\b[a-z0-9-]+\.(com|org|net|io|dev|ai|edu|gov|co|in)\b)""",
        RegexOption.IGNORE_CASE
    )

    private val browserCueRegex = Regex(
        """\b(open|visit|go to|navigate|browse|browser|website|webpage|page|tab|tabs|click|tap|fill|type|scroll|back|forward|reload|refresh|youtube|google|github|wikipedia|reddit|hacker news|run|launch|load|snapshot|dom|link|links|site|search|find|look up|lookup|summarize|summary|article|read|kholo|dekho|padho)\b""",
        RegexOption.IGNORE_CASE
    )
    private val pageContextCueRegex = Regex(
        """\b(this page|current page|this site|on screen|what does it say|extract|explain this|this article)\b""",
        RegexOption.IGNORE_CASE
    )
    private val sandboxCueRegex = Regex(
        """\b(sandbox|file|files|note|notes|save|write|read|delete|export|workspace|archive|folder|txt|json|csv|md)\b""",
        RegexOption.IGNORE_CASE
    )
    private val fetchCueRegex = Regex(
        """\b(fetch|api|apis|http|https|endpoint|scrape|download|weather|stock|price|news)\b""",
        RegexOption.IGNORE_CASE
    )
    private val deviceCueRegex = Regex(
        """\b(clipboard|copy)\b""",
        RegexOption.IGNORE_CASE
    )

    fun selectToolsForTurn(
        userPrompt: String,
        hasActiveWebpage: Boolean,
        hasGrabbedPage: Boolean,
        hasAttachments: Boolean,
        mode: ExecutionMode,
        enabledToolkits: Set<ToolkitId>,
        mcpConnectors: List<McpConnectorConfig>,
        sandboxInternetEnabled: Boolean
    ): List<ToolDefinition> {
        val allAvailable = availableTools(mode, enabledToolkits, mcpConnectors, sandboxInternetEnabled)
        if (allAvailable.isEmpty()) return emptyList()

        val hasUrl = urlLikeRegex.containsMatchIn(userPrompt)
        val hasMcpEnabled = mcpConnectors.any { it.enabled && it.discoveredTools.isNotEmpty() }

        val wantsBrowser = hasUrl ||
            hasGrabbedPage ||
            browserCueRegex.containsMatchIn(userPrompt) ||
            (hasActiveWebpage && pageContextCueRegex.containsMatchIn(userPrompt))

        val wantsSandbox = hasAttachments || sandboxCueRegex.containsMatchIn(userPrompt)
        val wantsFetch = hasUrl || fetchCueRegex.containsMatchIn(userPrompt)
        val wantsDevice = deviceCueRegex.containsMatchIn(userPrompt)

        if (!wantsBrowser && !wantsSandbox && !wantsFetch && !wantsDevice && !hasMcpEnabled) {
            return emptyList()
        }

        return allAvailable.filter { def ->
            when {
                def.canonicalName.startsWith("mcp.") -> true
                def.toolkit == ToolkitId.BROWSER -> wantsBrowser
                def.toolkit == ToolkitId.SANDBOX -> wantsSandbox
                def.toolkit == ToolkitId.WEB_FETCH -> wantsFetch || wantsBrowser
                def.toolkit == ToolkitId.DEVICE -> wantsDevice || wantsBrowser
                else -> true
            }
        }
    }

    fun resolveDefinition(
        rawName: String,
        availableTools: List<ToolDefinition>
    ): ToolDefinition? {
        val normalized = rawName.trim()
        return availableTools.firstOrNull {
            it.wireName.equals(normalized, ignoreCase = true) ||
                it.canonicalName.equals(normalized, ignoreCase = true)
        } ?: byWireOrCanonical[normalized]?.takeIf { def ->
            availableTools.any { it.canonicalName == def.canonicalName }
        }
    }

    fun validateToolCall(
        callId: String,
        rawName: String,
        arguments: JSONObject,
        availableTools: List<ToolDefinition>
    ): ToolValidationResult {
        val knownAny = byWireOrCanonical[rawName.trim()]
        val def = resolveDefinition(rawName, availableTools)
            ?: return if (knownAny != null) {
                ToolValidationResult.Invalid(
                    knownAny.canonicalName,
                    "Tool '${knownAny.canonicalName}' is currently unavailable or disabled in this mode/toolkit."
                )
            } else {
                ToolValidationResult.Invalid(
                    rawName,
                    "Unknown tool '$rawName'. Only registered tools may be invoked."
                )
            }

        for (spec in def.parameters) {
            val hasKey = arguments.has(spec.name) && !arguments.isNull(spec.name)
            if (spec.required && !hasKey) {
                return ToolValidationResult.Invalid(
                    def.canonicalName,
                    "Missing required parameter '${spec.name}' for ${def.canonicalName}."
                )
            }
            if (!hasKey) continue

            val rawVal = arguments.opt(spec.name)
            when (spec.type) {
                ParamType.STRING -> {
                    val str = rawVal as? String ?: return ToolValidationResult.Invalid(
                        def.canonicalName,
                        "Parameter '${spec.name}' must be a string."
                    )
                    if (str.isBlank() && spec.required) {
                        return ToolValidationResult.Invalid(
                            def.canonicalName,
                            "Parameter '${spec.name}' must not be blank."
                        )
                    }
                    if (spec.enumValues.isNotEmpty() && spec.enumValues.none { it.equals(str, ignoreCase = true) }) {
                        return ToolValidationResult.Invalid(
                            def.canonicalName,
                            "Parameter '${spec.name}' must be one of ${spec.enumValues.joinToString(", ")}."
                        )
                    }
                }
                ParamType.INTEGER -> {
                    if (rawVal !is Number) {
                        return ToolValidationResult.Invalid(
                            def.canonicalName,
                            "Parameter '${spec.name}' must be an integer."
                        )
                    }
                }
                ParamType.BOOLEAN -> {
                    if (rawVal !is Boolean) {
                        return ToolValidationResult.Invalid(
                            def.canonicalName,
                            "Parameter '${spec.name}' must be a boolean."
                        )
                    }
                }
                ParamType.OBJECT -> {
                    if (rawVal !is JSONObject) {
                        return ToolValidationResult.Invalid(
                            def.canonicalName,
                            "Parameter '${spec.name}' must be a JSON object."
                        )
                    }
                }
            }
        }

        // Domain-specific semantic validations
        when (def.canonicalName) {
            "browser.open" -> {
                val url = arguments.optString("url").trim()
                val lower = url.lowercase()
                if (lower.startsWith("javascript:") || lower.startsWith("file:") ||
                    lower.startsWith("data:") || lower.startsWith("content:")
                ) {
                    return ToolValidationResult.Invalid(def.canonicalName, "Blocked unsafe URL scheme in browser.open.")
                }
                val isLocalhost = lower.startsWith("localhost") || lower.startsWith("127.0.0.1")
                if (url.contains(" ") || (!url.contains(".") && !isLocalhost &&
                        !lower.startsWith("http://") && !lower.startsWith("https://"))
                ) {
                    return ToolValidationResult.Invalid(def.canonicalName, "Invalid URL '$url' for browser.open.")
                }
            }
            "browser.click", "browser.fill", "browser.type", "browser.select", "browser.check" -> {
                val ref = arguments.optString("ref").trim()
                if (!refRegex.matches(ref)) {
                    return ToolValidationResult.Invalid(def.canonicalName, "Invalid element ref '$ref'. Expected @e1, @e2, etc.")
                }
            }
            "browser.scroll" -> {
                if (arguments.has("pixels")) {
                    val px = arguments.optInt("pixels", 600)
                    if (px !in 50..5000) {
                        return ToolValidationResult.Invalid(def.canonicalName, "Scroll pixels must be between 50 and 5000.")
                    }
                }
                if (arguments.has("ref")) {
                    val ref = arguments.optString("ref").trim()
                    if (ref.isNotEmpty() && !refRegex.matches(ref)) {
                        return ToolValidationResult.Invalid(def.canonicalName, "Invalid scroll ref '$ref'.")
                    }
                }
            }
            "browser.wait" -> {
                val ms = arguments.optInt("ms", -1)
                if (ms !in 50..10_000) {
                    return ToolValidationResult.Invalid(def.canonicalName, "Wait duration must be between 50 and 10000 ms.")
                }
            }
            "web.fetch" -> {
                val url = arguments.optString("url").trim().lowercase()
                if (!url.startsWith("https://") && !url.startsWith("http://")) {
                    return ToolValidationResult.Invalid(def.canonicalName, "web.fetch requires an http:// or https:// URL.")
                }
            }
        }

        return ToolValidationResult.Valid(
            ValidatedToolCall(
                callId = callId,
                canonicalName = def.canonicalName,
                wireName = def.wireName,
                arguments = arguments,
                definition = def
            )
        )
    }

    fun toOpenAiToolsJson(tools: List<ToolDefinition>): JSONArray = JSONArray().apply {
        tools.forEach { tool ->
            put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.wireName)
                    put("description", tool.description)
                    put("parameters", buildParametersSchema(tool))
                })
            })
        }
    }

    fun toAnthropicToolsJson(tools: List<ToolDefinition>): JSONArray = JSONArray().apply {
        tools.forEach { tool ->
            put(JSONObject().apply {
                put("name", tool.wireName)
                put("description", tool.description)
                put("input_schema", buildParametersSchema(tool))
            })
        }
    }

    fun toGeminiToolsJson(tools: List<ToolDefinition>): JSONArray {
        val declarations = JSONArray().apply {
            tools.forEach { tool ->
                put(JSONObject().apply {
                    put("name", tool.wireName)
                    put("description", tool.description)
                    put("parameters", buildParametersSchema(tool))
                })
            }
        }
        return JSONArray().apply {
            put(JSONObject().put("functionDeclarations", declarations))
        }
    }

    private fun buildParametersSchema(tool: ToolDefinition): JSONObject {
        val props = JSONObject()
        val required = JSONArray()
        tool.parameters.forEach { p ->
            props.put(p.name, JSONObject().apply {
                put("type", p.type.jsonSchemaType)
                put("description", p.description)
                if (p.enumValues.isNotEmpty()) {
                    put("enum", JSONArray(p.enumValues))
                }
            })
            if (p.required) required.put(p.name)
        }
        return JSONObject().apply {
            put("type", "object")
            put("properties", props)
            if (required.length() > 0) put("required", required)
            put("additionalProperties", false)
        }
    }

    fun friendlyTitle(canonicalName: String): String = when (canonicalName) {
        "browser.open" -> "Open Page"
        "browser.snapshot" -> "Inspect DOM"
        "browser.get_text" -> "Read Page"
        "browser.click" -> "Click Element"
        "browser.fill" -> "Fill Input"
        "browser.type" -> "Type Text"
        "browser.press" -> "Press Key"
        "browser.select" -> "Select Option"
        "browser.check" -> "Toggle Check"
        "browser.scroll" -> "Scroll Page"
        "browser.back" -> "Navigate Back"
        "browser.forward" -> "Navigate Forward"
        "browser.reload" -> "Reload Page"
        "browser.wait" -> "Wait"
        "sandbox.list" -> "List Files"
        "sandbox.read" -> "Read File"
        "sandbox.write" -> "Write File"
        "sandbox.delete" -> "Delete File"
        "web.fetch" -> "HTTP Fetch"
        "device.clipboard_copy" -> "Copy Clipboard"
        else -> if (canonicalName.startsWith("mcp.")) {
            canonicalName.removePrefix("mcp.").replace(".", " · ")
        } else {
            canonicalName
        }
    }

    fun conciseTarget(canonicalName: String, argsJson: String?): String {
        if (argsJson.isNullOrBlank()) return ""
        val args = runCatching { JSONObject(argsJson) }.getOrNull() ?: return ""
        return when (canonicalName) {
            "browser.open", "web.fetch" -> args.optString("url")
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .trimEnd('/')
                .take(42)
            "browser.click" -> args.optString("ref")
            "browser.fill", "browser.type", "browser.select" -> {
                val ref = args.optString("ref")
                val v = args.optString("value").take(22)
                if (v.isNotBlank()) "$ref → \"$v\"" else ref
            }
            "browser.press" -> args.optString("key")
            "browser.scroll" -> args.optString("ref").ifBlank { args.optString("direction") }
            "browser.wait" -> "${args.optInt("ms")} ms"
            "sandbox.read", "sandbox.write", "sandbox.delete" -> args.optString("path").take(38)
            "device.clipboard_copy" -> "${args.optString("text").length} chars"
            else -> args.toString().take(40)
        }
    }

    private fun sanitizeName(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "tool" }
}

object FakeToolCallDetector {

    private val fakeToolTextPatterns = listOf(
        Regex("""\{\s*"name"\s*:\s*"(browser|sandbox|web|device|mcp)[._][a-zA-Z0-9_.-]+"\s*,\s*"(parameters|arguments|args)""""),
        Regex("""\{\s*"action"\s*:\s*"tool"\s*,\s*"tool"\s*:"""),
        Regex("""```(?:json)?\s*\{\s*"name"\s*:\s*"(browser|sandbox|web|device|mcp)[._]""")
    )

    private val unverifiedSuccessPhrases = listOf(
        "opened ",
        "i've opened",
        "i have opened",
        "navigated to",
        "clicked ",
        "filled ",
        "saved the file",
        "completed successfully",
        "done!"
    )

    fun containsFakeToolCallJson(text: String): Boolean {
        if (text.isBlank()) return false
        return fakeToolTextPatterns.any { it.containsMatchIn(text) }
    }

    fun claimsSuccessDespiteFailures(
        replyText: String,
        records: List<ToolExecutionRecord>
    ): Boolean {
        if (records.isEmpty()) return false
        val allFailedOrUnverified = records.none { it.success }
        val lastFailed = records.lastOrNull()?.success == false
        if (!allFailedOrUnverified && !lastFailed) return false
        val lower = replyText.lowercase()
        val hasErrorHonesty = lower.contains("fail") ||
            lower.contains("error") ||
            lower.contains("could not") ||
            lower.contains("couldn't") ||
            lower.contains("unable") ||
            lower.contains("timed out") ||
            lower.contains("timeout") ||
            lower.contains("didn't") ||
            lower.contains("did not") ||
            lower.contains("blocked") ||
            lower.contains("denied") ||
            lower.contains("skipped") ||
            lower.contains("refused") ||
            lower.contains("cannot") ||
            lower.contains("can't") ||
            lower.contains("not possible")
        if (hasErrorHonesty) return false
        return unverifiedSuccessPhrases.any { lower.contains(it) }
    }
}
