package com.privatemessenger.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object SupabaseClient {
    private val http = OkHttpClient()

    private val base: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
            .removeSuffix("/rest/v1")
            .removeSuffix("/auth/v1")
            .trimEnd('/')

    private val key get() = BuildConfig.SUPABASE_KEY.trim()
    var accessToken: String? = null
        private set
    var userId: String? = null
        private set

    private fun request(method: String, path: String, body: String? = null, prefer: String? = null): String {
        if (base.isBlank()) error("SUPABASE_URL не настроен")
        if (key.isBlank()) error("SUPABASE_KEY не настроен")
        val url = base + if (path.startsWith('/')) path else "/$path"
        val builder = Request.Builder().url(url)
            .header("apikey", key)
            .header("Authorization", "Bearer ${accessToken ?: key}")
        if (prefer != null) builder.header("Prefer", prefer)
        if (body != null) builder.header("Content-Type", "application/json; charset=utf-8")
        val requestBody = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            "PATCH" -> builder.patch(requestBody ?: "{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            "DELETE" -> builder.delete(requestBody)
            else -> error("Unsupported HTTP method")
        }
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try {
                    val json = JSONObject(text)
                    json.optString("msg").ifBlank {
                        json.optString("message").ifBlank {
                            json.optString("error_description").ifBlank { json.optString("hint").ifBlank { text } }
                        }
                    }
                } catch (_: Exception) { text }
                throw IOException("Supabase ${response.code}: $message")
            }
            return text
        }
    }

    private fun authEmail(username: String) = "${username.lowercase()}@nexo.app"

    fun signIn(username: String, password: String) {
        val json = request("POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", authEmail(username)).put("password", password).toString())
        val o = JSONObject(json)
        accessToken = o.getString("access_token")
        userId = o.getJSONObject("user").getString("id")
    }

    fun signUp(username: String, password: String) {
        val json = request("POST", "/auth/v1/signup",
            JSONObject()
                .put("email", authEmail(username))
                .put("password", password)
                .put("data", JSONObject().put("username", username.lowercase()))
                .toString())
        val o = JSONObject(json)
        val token = o.optString("access_token")
        if (token.isNotBlank() && o.has("user")) {
            accessToken = token
            userId = o.getJSONObject("user").getString("id")
        }
    }

    fun signOut() {
        try { if (accessToken != null) request("POST", "/auth/v1/logout") }
        finally { accessToken = null; userId = null }
    }

    fun searchProfiles(query: String): JSONArray {
        val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        return JSONArray(request("GET", "/rest/v1/rpc/search_profiles?search_term=$q"))
    }

    fun profiles(): JSONArray = searchProfiles("")

    fun directConversation(otherUserId: String): String {
        val json = request("POST", "/rest/v1/rpc/create_direct_conversation",
            JSONObject().put("other_user", otherUserId).toString())
        return json.trim().trim('"')
    }

    fun messages(conversationId: String): JSONArray = JSONArray(
        request("GET", "/rest/v1/messages?conversation_id=eq.$conversationId&select=id,sender_id,body,created_at&order=created_at.asc&limit=500")
    )

    fun send(conversationId: String, text: String) {
        val uid = userId ?: error("Не выполнен вход")
        request("POST", "/rest/v1/messages",
            JSONObject().put("conversation_id", conversationId).put("sender_id", uid).put("body", text).toString())
    }
}
