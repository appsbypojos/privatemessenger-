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
    private val base get() = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key get() = BuildConfig.SUPABASE_KEY
    var accessToken: String? = null
        private set
    var userId: String? = null
        private set

    private fun request(method: String, path: String, body: String? = null, prefer: String? = null): String {
        if (base.isBlank() || key.isBlank()) error("Supabase не настроен")
        val builder = Request.Builder()
            .url(base + path)
            .header("apikey", key)
            .header("Authorization", "Bearer ${accessToken ?: key}")
        if (prefer != null) builder.header("Prefer", prefer)
        if (body != null) builder.header("Content-Type", "application/json")
        val requestBody = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody("application/json".toMediaType()))
            "PATCH" -> builder.patch(requestBody ?: "{}".toRequestBody("application/json".toMediaType()))
            "DELETE" -> builder.delete(requestBody)
        }
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Supabase ${response.code}: $text")
            return text
        }
    }

    fun signIn(email: String, password: String) {
        val json = request("POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password).toString())
        val o = JSONObject(json)
        accessToken = o.getString("access_token")
        userId = o.getJSONObject("user").getString("id")
    }

    fun signUp(email: String, password: String) {
        val json = request("POST", "/auth/v1/signup",
            JSONObject().put("email", email).put("password", password).toString())
        val o = JSONObject(json)
        if (o.has("access_token") && o.optString("access_token").isNotBlank()) {
            accessToken = o.getString("access_token")
            userId = o.getJSONObject("user").getString("id")
        }
    }

    fun signOut() {
        try { if (accessToken != null) request("POST", "/auth/v1/logout") } finally {
            accessToken = null
            userId = null
        }
    }

    fun profiles(): JSONArray {
        val uid = userId ?: error("not authenticated")
        return JSONArray(request("GET", "/rest/v1/profiles?id=neq.$uid&select=id,username,display_name&order=display_name.asc&limit=100"))
    }

    fun directConversation(otherUserId: String): String {
        val json = request("POST", "/rest/v1/rpc/create_direct_conversation",
            JSONObject().put("other_user", otherUserId).toString())
        return json.trim().trim('"')
    }

    fun messages(conversationId: String): JSONArray = JSONArray(
        request("GET", "/rest/v1/messages?conversation_id=eq.$conversationId&select=id,sender_id,body,ciphertext,created_at&order=created_at.asc&limit=500")
    )

    fun send(conversationId: String, text: String) {
        val uid = userId ?: error("not authenticated")
        request("POST", "/rest/v1/messages",
            JSONObject().put("conversation_id", conversationId)
                .put("sender_id", uid)
                .put("body", text)
                .put("ciphertext", text)
                .put("nonce", "none").toString())
    }
}
