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
            .removeSuffix("/functions/v1")
            .trimEnd('/')

    private val key get() = BuildConfig.SUPABASE_KEY.trim()

    var accessToken: String? = null
        private set
    var userId: String? = null
        private set

    private fun emailFor(username: String) = "${username.lowercase()}@auth.nexo.app"

    private fun request(method: String, path: String, body: String? = null, auth: Boolean = true): String {
        if (base.isBlank()) error("SUPABASE_URL не настроен")
        if (key.isBlank()) error("SUPABASE_KEY не настроен")

        val url = base + if (path.startsWith('/')) path else "/$path"
        val builder = Request.Builder().url(url)
            .header("apikey", key)
            .header("Accept", "application/json")
        if (auth) builder.header("Authorization", "Bearer ${accessToken ?: key}")
        if (body != null) builder.header("Content-Type", "application/json; charset=utf-8")

        val requestBody = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            else -> error("Unsupported HTTP method")
        }

        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try {
                    val json = JSONObject(text)
                    json.optString("error")
                        .ifBlank { json.optString("msg") }
                        .ifBlank { json.optString("message") }
                        .ifBlank { json.optString("error_description") }
                        .ifBlank { text }
                } catch (_: Exception) { text }
                throw IOException("Supabase ${response.code}: $message")
            }
            return text
        }
    }

    fun register(username: String, password: String) {
        val normalized = username.trim().lowercase()
        require(normalized.matches(Regex("[a-z0-9_]{3,24}"))) {
            "Ник: 3–24 символа, только a-z, 0-9 и _"
        }
        require(password.length >= 6) { "Пароль должен содержать минимум 6 символов" }

        // Nexo uses a synthetic email internally so the user only needs a unique nickname.
        // Registration goes directly through Supabase Auth; there is no /functions/v1 endpoint.
        val json = request(
            "POST",
            "/auth/v1/signup",
            JSONObject()
                .put("email", emailFor(normalized))
                .put("password", password)
                .put("data", JSONObject().put("username", normalized).put("display_name", normalized))
                .toString(),
            auth = false
        )

        val o = JSONObject(json)
        val session = o.optJSONObject("session")
        if (session != null && session.has("access_token")) {
            accessToken = session.getString("access_token")
            userId = o.getJSONObject("user").getString("id")
        } else {
            // If email confirmation is enabled, Auth may return no session.
            // The app will surface the actual Supabase response instead of a fake 404.
            throw IOException("Supabase: регистрация создана, но сессия не выдана. Отключите Confirm email в Auth → Providers → Email.")
        }
    }

    fun signIn(username: String, password: String) {
        val normalized = username.trim().lowercase()
        require(normalized.matches(Regex("[a-z0-9_]{3,24}"))) { "Некорректный ник" }
        val json = request(
            "POST",
            "/auth/v1/token?grant_type=password",
            JSONObject().put("email", emailFor(normalized)).put("password", password).toString(),
            auth = false
        )
        val o = JSONObject(json)
        accessToken = o.getString("access_token")
        userId = o.getJSONObject("user").getString("id")
    }

    fun signOut() {
        try { if (accessToken != null) request("POST", "/auth/v1/logout") }
        finally { accessToken = null; userId = null }
    }

    fun searchProfiles(query: String): JSONArray = JSONArray(
        request(
            "POST",
            "/rest/v1/rpc/search_profiles",
            JSONObject().put("search_username", query.trim().lowercase()).toString()
        )
    )

    fun directConversation(otherUserId: String): String {
        val json = request(
            "POST",
            "/rest/v1/rpc/create_direct_conversation",
            JSONObject().put("other_user", otherUserId).toString()
        )
        return json.trim().trim('"')
    }

    fun messages(conversationId: String): JSONArray = JSONArray(
        request(
            "GET",
            "/rest/v1/messages?conversation_id=eq.$conversationId&select=id,sender_id,body,created_at&order=created_at.asc&limit=500"
        )
    )

    fun send(conversationId: String, text: String) {
        val uid = userId ?: error("Не выполнен вход")
        request(
            "POST",
            "/rest/v1/messages",
            JSONObject().put("conversation_id", conversationId).put("sender_id", uid).put("body", text).toString()
        )
    }
}
