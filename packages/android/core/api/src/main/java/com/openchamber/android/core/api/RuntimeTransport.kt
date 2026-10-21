package com.openchamber.android.core.api

import com.openchamber.android.core.api.json.OpenChamberJson
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A request that failed. The HTTP status is kept apart from the server's
 * message, because the OpenChamber server answers a rejected client token
 * with a body that says so and a status that only says "no".
 */
class ApiException(
    val status: Int,
    override val message: String,
    val body: String? = null,
) : IOException(message) {
    val isUnauthorized: Boolean get() = status == 401 || status == 403
}

/**
 * The HTTP layer every client in the app shares.
 *
 * It owns three things the callers must not repeat: the bearer token, the
 * percent-encoded `x-opencode-directory` header, and the translation of a
 * failed request into an [ApiException]. The base URL is loopback, so there is
 * no host discovery here.
 */
class RuntimeTransport(
    private val baseUrl: HttpUrl,
    private val tokenProvider: () -> String?,
    private val client: OkHttpClient = defaultClient(),
) {
    val base: HttpUrl get() = baseUrl

    suspend fun get(
        path: String,
        query: Map<String, String?> = emptyMap(),
        directory: String? = null,
    ): String = execute(Request.Builder().url(url(path, query)).get(), directory)

    suspend fun post(
        path: String,
        body: JsonElement? = null,
        query: Map<String, String?> = emptyMap(),
        directory: String? = null,
    ): String {
        val payload = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE) ?: EMPTY_BODY
        return execute(Request.Builder().url(url(path, query)).post(payload), directory)
    }

    suspend fun delete(
        path: String,
        query: Map<String, String?> = emptyMap(),
        directory: String? = null,
    ): String = execute(Request.Builder().url(url(path, query)).delete(), directory)
    fun url(path: String, query: Map<String, String?> = emptyMap()): HttpUrl {
        val builder = baseUrl.newBuilder()
        for (segment in path.trim('/').split('/')) {
            if (segment.isNotEmpty()) builder.addPathSegment(segment)
        }
        for ((key, value) in query) {
            if (value != null) builder.addQueryParameter(key, value)
        }
        return builder.build()
    }

    private suspend fun execute(request: Request.Builder, directory: String?): String =
        withContext(Dispatchers.IO) {
            val token = tokenProvider()
            if (!token.isNullOrBlank()) {
                request.header("Authorization", "Bearer $token")
            }
            if (!directory.isNullOrBlank()) {
                request.header("x-opencode-directory", encodeDirectory(directory))
            }
            val response = try {
                client.newCall(request.build()).execute()
            } catch (error: IOException) {
                throw ApiException(0, error.message ?: "The request did not reach the server")
            }
            response.use {
                val body = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    throw ApiException(it.code, errorMessage(it.code, body), body)
                }
                body
            }
        }

    private fun errorMessage(status: Int, body: String): String {
        val parsed = runCatching {
            OpenChamberJson.decodeFromString<com.openchamber.android.core.api.dto.ApiErrorDto>(body)
        }.getOrNull()
        return parsed?.error?.takeIf { it.isNotBlank() }
            ?: "The server answered $status"
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = "".toRequestBody(null)

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        /**
         * The directory header carries a filesystem path, and the server
         * percent-encodes it before it reaches OpenCode. Encoding it here too
         * keeps a path with a space or a percent sign from arriving as a
         * different path.
         */
        fun encodeDirectory(directory: String): String =
            URLEncoder.encode(directory, StandardCharsets.UTF_8.name())
    }
}
