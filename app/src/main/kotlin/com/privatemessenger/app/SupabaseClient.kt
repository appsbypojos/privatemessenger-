package com.privatemessenger.app

import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object SupabaseClient {
    private val http = OkHttpClient()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_KEY
    var accessToken: String? = null
    var userId: String? = null

    private fun request(method: String, path: String, body: String? = null, prefer: String? = null): String {
        val b = Request.Builder().url(base + path).header("apikey", key).header("Authorization", "Bearer ${accessToken ?: key}")
        if (prefer != null) b.header("Prefer", prefer)
        val requestBody = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        when (method) { "POST" -> b.post(requestBody ?: "".toRequestBody()); "PATCH" -> b.patch(requestBody ?: "".toRequestBody()); "DELETE" -> b.delete(requestBody) }
        http.newCall(b.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Supabase ${response.code}: $text")
            return text
        }
    }

    fun signIn(email: String, password: String): String {
        val json = request("POST", "/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password).toString())
        val o = JSONObject(json); accessToken = o.getString("access_token"); userId = o.getJSONObject("user").getString("id"); return o.getString("access_token")
    }

    fun signUp(email: String, password: String): String = request("POST", "/auth/v1/signup", JSONObject().put("email", email).put("password", password).toString())

    fun ensureConversation(): String {
        val uid = userId ?: error("not authenticated")
        val existing = request("GET", "/rest/v1/conversation_members?user_id=eq.$uid&select=conversation_id&limit=1")
        val arr = JSONArray(existing)
        if (arr.length() > 0) return arr.getJSONObject(0).getString("conversation_id")
        val created = request("POST", "/rest/v1/conversations", "{}", "return=representation")
        val cid = JSONArray(created).getJSONObject(0).getString("id")
        request("POST", "/rest/v1/conversation_members", JSONObject().put("conversation_id", cid).put("user_id", uid).toString())
        return cid
    }

    fun messages(conversationId: String): JSONArray = JSONArray(request("GET", "/rest/v1/messages?conversation_id=eq.$conversationId&select=id,sender_id,ciphertext,created_at&order=created_at.asc"))

    fun send(conversationId: String, text: String) {
        val uid = userId ?: error("not authenticated")
        request("POST", "/rest/v1/messages", JSONObject().put("conversation_id", conversationId).put("sender_id", uid).put("ciphertext", text).put("nonce", "none").toString())
    }
}
