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
        get() = BuildConfig.SUPABASE_URL.trim()
            .trimEnd('/')
            .removeSuffix("/rest/v1")
            .removeSuffix("/auth/v1")
            .removeSuffix("/functions/v1")
            .trimEnd('/')

    private val key: String
        get() = BuildConfig.SUPABASE_KEY.trim()

    var accessToken: String? = null
        private set

    var userId: String? = null
        private set

    private fun emailFor(username: String): String = "${username.lowercase()}@auth.nexo.app"

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        auth: Boolean = true
    ): String {
        if (base.isBlank()) error("SUPABASE_URL не настроен")
        if (key.isBlank()) error("SUPABASE_KEY не настроен")

        val builder = Request.Builder()
            .url(base + if (path.startsWith('/')) path else "/$path")
            .header("apikey", key)
            .header("Accept", "application/json")

        if (auth) {
            builder.header("Authorization", "Bearer ${accessToken ?: key}")
        }

        if (body != null) {
            builder.header("Content-Type", "application/json; charset=utf-8")
        }

        val rb = body?.toRequestBody("application/json; charset=utf-8".toMediaType())

        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(
                rb ?: "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            "PATCH" -> builder.patch(
                rb ?: "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            else -> error("Unsupported HTTP method: $method")
        }

        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val message = try {
                    JSONObject(text).let { json ->
                        json.optString("message")
                            .ifBlank { json.optString("msg") }
                            .ifBlank { json.optString("error_description") }
                            .ifBlank { text }
                    }
                } catch (e: Exception) {
                    text
                }

                throw IOException("Supabase ${response.code}: $message")
            }

            return text
        }
    }

    fun register(username: String, password: String) {
        val n = username.trim().lowercase()
        require(n.matches(Regex("[a-z0-9_]{3,24}"))) {
            "Ник: 3–24 символа, только a-z, 0-9 и _"
        }
        require(password.length >= 6) {
            "Пароль должен содержать минимум 6 символов"
        }

        val payload = JSONObject()
            .put("email", emailFor(n))
            .put("password", password)
            .put(
                "data",
                JSONObject()
                    .put("username", n)
                    .put("display_name", n)
            )
            .toString()

        val json = request("POST", "/auth/v1/signup", payload, false)
        val o = JSONObject(json)
        val session = o.optJSONObject("session")
            ?: throw IOException(
                "Регистрация создана, но сессия не выдана. Отключите Confirm email в Supabase."
            )

        accessToken = session.getString("access_token")
        userId = o.getJSONObject("user").getString("id")
    }

    fun signIn(username: String, password: String) {
        val n = username.trim().lowercase()
        require(n.matches(Regex("[a-z0-9_]{3,24}"))) {
            "Некорректный ник"
        }

        val payload = JSONObject()
            .put("email", emailFor(n))
            .put("password", password)
            .toString()

        val o = JSONObject(
            request("POST", "/auth/v1/token?grant_type=password", payload, false)
        )

        accessToken = o.getString("access_token")
        userId = o.getJSONObject("user").getString("id")
    }

    fun signOut() {
        try {
            if (accessToken != null) {
                request("POST", "/auth/v1/logout")
            }
        } finally {
            accessToken = null
            userId = null
        }
    }

    fun myProfile(): JSONObject {
        val uid = userId ?: error("Не выполнен вход")
        val a = JSONArray(
            request(
                "GET",
                "/rest/v1/profiles?id=eq.$uid&select=id,username,display_name,bio,created_at&limit=1"
            )
        )
        return if (a.length() > 0) a.getJSONObject(0) else JSONObject()
    }

    fun updateProfile(displayName: String, bio: String) {
        request(
            "POST",
            "/rest/v1/rpc/update_my_profile",
            JSONObject()
                .put("new_display_name", displayName)
                .put("new_bio", bio)
                .toString()
        )
    }

    fun searchProfiles(query: String): JSONArray = JSONArray(
        request(
            "POST",
            "/rest/v1/rpc/search_profiles",
            JSONObject().put("search_term", query.trim().lowercase()).toString()
        )
    )

    fun chats(): JSONArray = JSONArray(
        request("POST", "/rest/v1/rpc/list_user_chats", "{}")
    )

    fun directConversation(otherUserId: String): String = request(
        "POST",
        "/rest/v1/rpc/create_direct_conversation",
        JSONObject().put("other_user", otherUserId).toString()
    ).trim().trim('"')

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
            JSONObject()
                .put("conversation_id", conversationId)
                .put("sender_id", uid)
                .put("body", text)
                .toString()
        )
    }
}
