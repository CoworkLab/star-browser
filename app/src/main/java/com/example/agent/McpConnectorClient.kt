package com.example.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class McpConnectorClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun discoverTools(connector: McpConnectorConfig): Result<List<McpToolInfo>> =
        withContext(Dispatchers.IO) {
            try {
                if (connector.transport == McpTransport.REST_WEBHOOK) {
                    return@withContext Result.success(
                        listOf(
                            McpToolInfo(
                                name = "webhook_call",
                                description = "Invoke ${connector.name} REST endpoint (${connector.endpointUrl})",
                                inputSchemaJson = "{\"type\":\"object\"}"
                            )
                        )
                    )
                }
                // JSON-RPC initialize (best-effort)
                try {
                    rpcCall(
                        connector = connector,
                        method = "initialize",
                        params = JSONObject().apply {
                            put("protocolVersion", "2024-11-05")
                            put("capabilities", JSONObject())
                            put("clientInfo", JSONObject().put("name", "StarBrowser").put("version", "1.0"))
                        },
                        id = 1
                    )
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Throwable) {
                }
                val resp = rpcCall(connector, "tools/list", JSONObject(), id = 2)
                val toolsArr = resp.optJSONObject("result")?.optJSONArray("tools")
                    ?: error("Connector did not return a valid MCP tools/list array.")
                val list = buildList {
                    for (i in 0 until toolsArr.length()) {
                        val t = toolsArr.getJSONObject(i)
                        add(
                            McpToolInfo(
                                name = t.getString("name"),
                                description = t.optString("description", ""),
                                inputSchemaJson = t.optJSONObject("inputSchema")?.toString() ?: "{}"
                            )
                        )
                    }
                }
                Result.success(list)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    suspend fun callTool(
        connector: McpConnectorConfig,
        toolName: String,
        arguments: JSONObject
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (connector.transport == McpTransport.REST_WEBHOOK) {
                val reqBuilder = Request.Builder()
                    .url(connector.endpointUrl)
                    .post(arguments.toString().toRequestBody(jsonMedia))
                    .header("Content-Type", "application/json")
                if (connector.authHeaderName.isNotBlank() && connector.authHeaderValue.isNotBlank()) {
                    reqBuilder.header(connector.authHeaderName, connector.authHeaderValue)
                }
                val out = executeCancellable(reqBuilder.build()) { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Webhook HTTP ${resp.code}: ${body.take(200)}")
                    body.ifBlank { "HTTP ${resp.code} OK" }
                }
                return@withContext Result.success(out)
            }

            val resp = rpcCall(
                connector = connector,
                method = "tools/call",
                params = JSONObject().apply {
                    put("name", toolName)
                    put("arguments", arguments)
                },
                id = 3
            )
            if (resp.has("error")) {
                val err = resp.getJSONObject("error")
                error("MCP Error ${err.optInt("code")}: ${err.optString("message")}")
            }
            Result.success(resp.optJSONObject("result")?.toString() ?: resp.toString())
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private suspend fun rpcCall(
        connector: McpConnectorConfig,
        method: String,
        params: JSONObject,
        id: Int
    ): JSONObject {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val reqBuilder = Request.Builder()
            .url(connector.endpointUrl)
            .post(payload.toString().toRequestBody(jsonMedia))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        if (connector.authHeaderName.isNotBlank() && connector.authHeaderValue.isNotBlank()) {
            reqBuilder.header(connector.authHeaderName, connector.authHeaderValue)
        }
        return executeCancellable(reqBuilder.build()) { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                error("MCP HTTP ${resp.code}: ${body.take(200)}")
            }
            JSONObject(body)
        }
    }

    private suspend fun <T> executeCancellable(
        request: Request,
        block: (okhttp3.Response) -> T
    ): T = withContext(Dispatchers.IO) {
        val call = client.newCall(request)
        val handle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            coroutineContext.ensureActive()
            call.execute().use { resp ->
                coroutineContext.ensureActive()
                block(resp)
            }
        } finally {
            handle?.dispose()
        }
    }
}
