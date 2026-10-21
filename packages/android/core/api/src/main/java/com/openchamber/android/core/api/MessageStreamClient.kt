package com.openchamber.android.core.api

import com.openchamber.android.core.api.dto.WireEventDto
import com.openchamber.android.core.api.json.OpenChamberJson
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The two ways the app reads the global event stream.
 *
 * The WebSocket at `/api/global/event/ws` is the preferred one. The server
 * sends frames of the shape `{ type: "event", payload: {...} }`, and the
 * server-sent event stream at `/api/global/event` sends the same payload
 * inside a `data:` line. Both are read here so the sync layer sees one kind
 * of event.
 */
class MessageStreamClient(
    private val baseUrl: HttpUrl,
    private val tokenProvider: () -> String?,
    private val client: OkHttpClient = RuntimeTransport.defaultClient(),
) {
    fun webSocketEvents(): Flow<WireEventDto> = callbackFlow {
        val request = Request.Builder()
            .url(websocketUrl(GLOBAL_WS_PATH))
            .apply { tokenProvider()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .build()
        val socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                parseFrame(text)?.let { trySend(it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                close()
            }
        })
        awaitClose { socket.close(1000, null) }
    }

    fun serverSentEvents(): Flow<WireEventDto> = flow {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(GLOBAL_SSE_PATH.trim('/')).build())
            .apply { tokenProvider()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") } }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, "The event stream answered ${response.code}")
            }
            val source = response.body?.source() ?: throw IOException("The event stream had no body")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                parseFrame(payload)?.let { emit(it) }
            }
        }
    }

    private fun websocketUrl(path: String): HttpUrl = baseUrl.newBuilder()
        .scheme(if (baseUrl.isHttps) "wss" else "ws")
        .addPathSegments(path.trim('/'))
        .build()

    companion object {
        const val GLOBAL_WS_PATH = "/api/global/event/ws"
        const val GLOBAL_SSE_PATH = "/api/global/event"

        /**
         * Reads one frame. A frame is either the event payload itself or a
         * wrapper around it, and the server uses both shapes across the two
         * transports.
         */
        fun parseFrame(raw: String): WireEventDto? {
            val parsed = runCatching { OpenChamberJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject
                ?: return null
            val payload = parsed["payload"] as? JsonObject ?: parsed
            return runCatching { OpenChamberJson.decodeFromJsonElement(WireEventDto.serializer(), payload) }
                .getOrNull()
        }
    }
}
