package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.agent.AgentDefaults
import com.example.agent.AgentSkill
import com.example.agent.ExecutionMode
import com.example.agent.LlmProviderConfig
import com.example.agent.McpConnectorConfig
import com.example.agent.SystemPromptPreset
import com.example.agent.ToolkitId
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.TextScalePreset
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "star_browser_prefs")

enum class SearchEngine(val label: String, val queryTemplate: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    KAGI("Kagi", "https://kagi.com/search?q=")
}

data class BrowserSettingsSnapshot(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentPreset: AccentPreset = AccentPreset.LAVENDER,
    val textScalePreset: TextScalePreset = TextScalePreset.STANDARD,
    val adBlockEnabled: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    val desktopByDefault: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val totalBlockedCount: Int = 0,
    val sandboxInternetEnabled: Boolean = true,
    val sandboxFolderUri: String? = null,
    val activeProviderId: String = "kilo-gateway",
    val providers: List<LlmProviderConfig> = AgentDefaults.defaultProviders,
    val activePromptId: String = "true-agent",
    val prompts: List<SystemPromptPreset> = AgentDefaults.defaultPrompts,
    val skills: List<AgentSkill> = AgentDefaults.defaultSkills,
    val enabledToolkits: Set<ToolkitId> = AgentDefaults.defaultEnabledToolkits,
    val mcpConnectors: List<McpConnectorConfig> = emptyList(),
    val executionMode: ExecutionMode = ExecutionMode.AUTO,
    val reasoningRequested: Boolean = false
)

class BrowserPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PRESET = stringPreferencesKey("accent_preset")
        val TEXT_SCALE_PRESET = stringPreferencesKey("text_scale_preset")
        val AD_BLOCK = booleanPreferencesKey("ad_block_enabled")
        val JAVASCRIPT = booleanPreferencesKey("javascript_enabled")
        val DESKTOP_DEFAULT = booleanPreferencesKey("desktop_by_default")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val BLOCKED_COUNT = intPreferencesKey("total_blocked_count")
        val SANDBOX_INTERNET = booleanPreferencesKey("sandbox_internet_enabled")
        val SANDBOX_FOLDER_URI = stringPreferencesKey("sandbox_folder_uri")
        val ACTIVE_PROVIDER_ID = stringPreferencesKey("active_provider_id")
        val PROVIDERS_JSON = stringPreferencesKey("providers_json")
        val ACTIVE_PROMPT_ID = stringPreferencesKey("active_prompt_id")
        val PROMPTS_JSON = stringPreferencesKey("prompts_json")
        val SKILLS_JSON = stringPreferencesKey("skills_json")
        val TOOLKITS = stringPreferencesKey("enabled_toolkits")
        val MCP_CONNECTORS_JSON = stringPreferencesKey("mcp_connectors_json")
        val EXECUTION_MODE = stringPreferencesKey("execution_mode")
        val REASONING_REQUESTED = booleanPreferencesKey("reasoning_requested")
    }

    val settingsFlow: Flow<BrowserSettingsSnapshot> = context.dataStore.data.map { prefs ->
        val theme = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
        }.getOrDefault(ThemeMode.DARK)

        val accent = runCatching {
            AccentPreset.valueOf(prefs[Keys.ACCENT_PRESET] ?: AccentPreset.LAVENDER.name)
        }.getOrDefault(AccentPreset.LAVENDER)

        val textScale = runCatching {
            TextScalePreset.valueOf(prefs[Keys.TEXT_SCALE_PRESET] ?: TextScalePreset.STANDARD.name)
        }.getOrDefault(TextScalePreset.STANDARD)

        val engine = runCatching {
            SearchEngine.valueOf(prefs[Keys.SEARCH_ENGINE] ?: SearchEngine.GOOGLE.name)
        }.getOrDefault(SearchEngine.GOOGLE)

        val mode = runCatching {
            ExecutionMode.valueOf(prefs[Keys.EXECUTION_MODE] ?: ExecutionMode.AUTO.name)
        }.getOrDefault(ExecutionMode.AUTO)

        val toolkitsRaw = prefs[Keys.TOOLKITS]
        val toolkits = if (toolkitsRaw == null) {
            AgentDefaults.defaultEnabledToolkits
        } else {
            toolkitsRaw.split(",").mapNotNull { token ->
                runCatching { ToolkitId.valueOf(token.trim()) }.getOrNull()
            }.toSet()
        }

        BrowserSettingsSnapshot(
            themeMode = theme,
            accentPreset = accent,
            textScalePreset = textScale,
            adBlockEnabled = prefs[Keys.AD_BLOCK] ?: true,
            javaScriptEnabled = prefs[Keys.JAVASCRIPT] ?: true,
            desktopByDefault = prefs[Keys.DESKTOP_DEFAULT] ?: false,
            searchEngine = engine,
            totalBlockedCount = prefs[Keys.BLOCKED_COUNT] ?: 0,
            sandboxInternetEnabled = prefs[Keys.SANDBOX_INTERNET] ?: true,
            sandboxFolderUri = prefs[Keys.SANDBOX_FOLDER_URI]?.takeIf { it.isNotBlank() },
            activeProviderId = prefs[Keys.ACTIVE_PROVIDER_ID] ?: "kilo-gateway",
            providers = AgentDefaults.deserializeProviders(prefs[Keys.PROVIDERS_JSON]),
            activePromptId = prefs[Keys.ACTIVE_PROMPT_ID] ?: "true-agent",
            prompts = AgentDefaults.deserializePrompts(prefs[Keys.PROMPTS_JSON]),
            skills = AgentDefaults.deserializeSkills(prefs[Keys.SKILLS_JSON]),
            enabledToolkits = toolkits,
            mcpConnectors = AgentDefaults.deserializeConnectors(prefs[Keys.MCP_CONNECTORS_JSON]),
            executionMode = mode,
            reasoningRequested = prefs[Keys.REASONING_REQUESTED] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setAccentPreset(accent: AccentPreset) = context.dataStore.edit { it[Keys.ACCENT_PRESET] = accent.name }
    suspend fun setTextScalePreset(scale: TextScalePreset) = context.dataStore.edit { it[Keys.TEXT_SCALE_PRESET] = scale.name }
    suspend fun setAdBlockEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.AD_BLOCK] = enabled }
    suspend fun setJavaScriptEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.JAVASCRIPT] = enabled }
    suspend fun setDesktopByDefault(enabled: Boolean) = context.dataStore.edit { it[Keys.DESKTOP_DEFAULT] = enabled }
    suspend fun setSearchEngine(engine: SearchEngine) = context.dataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    suspend fun incrementBlockedCount() = context.dataStore.edit {
        it[Keys.BLOCKED_COUNT] = (it[Keys.BLOCKED_COUNT] ?: 0) + 1
    }
    suspend fun setSandboxInternetEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.SANDBOX_INTERNET] = enabled }
    suspend fun setSandboxFolderUri(uri: String?) = context.dataStore.edit {
        if (uri.isNullOrBlank()) it.remove(Keys.SANDBOX_FOLDER_URI)
        else it[Keys.SANDBOX_FOLDER_URI] = uri
    }
    suspend fun setActiveProviderId(id: String) = context.dataStore.edit { it[Keys.ACTIVE_PROVIDER_ID] = id }
    suspend fun saveProviders(list: List<LlmProviderConfig>) = context.dataStore.edit {
        it[Keys.PROVIDERS_JSON] = AgentDefaults.serializeProviders(list)
    }
    suspend fun setActivePromptId(id: String) = context.dataStore.edit { it[Keys.ACTIVE_PROMPT_ID] = id }
    suspend fun savePrompts(list: List<SystemPromptPreset>) = context.dataStore.edit {
        it[Keys.PROMPTS_JSON] = AgentDefaults.serializePrompts(list)
    }
    suspend fun saveSkills(list: List<AgentSkill>) = context.dataStore.edit {
        it[Keys.SKILLS_JSON] = AgentDefaults.serializeSkills(list)
    }
    suspend fun saveToolkits(toolkits: Set<ToolkitId>) = context.dataStore.edit {
        it[Keys.TOOLKITS] = toolkits.joinToString(",") { t -> t.name }
    }
    suspend fun saveConnectors(list: List<McpConnectorConfig>) = context.dataStore.edit {
        it[Keys.MCP_CONNECTORS_JSON] = AgentDefaults.serializeConnectors(list)
    }
    suspend fun setExecutionMode(mode: ExecutionMode) = context.dataStore.edit { it[Keys.EXECUTION_MODE] = mode.name }
    suspend fun setReasoningRequested(enabled: Boolean) = context.dataStore.edit { it[Keys.REASONING_REQUESTED] = enabled }
}
