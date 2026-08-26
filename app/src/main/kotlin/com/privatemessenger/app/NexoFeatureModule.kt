package com.privatemessenger.app

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Adds real chat actions without replacing the existing chat screen. */
object NexoFeatureModule {
    private const val ATTACH_REQ = 7101
    private const val CALL_REQ = 7102
    private val http = OkHttpClient()

    fun install(activity: Activity, conversationId: String, root: ViewGroup) {
        val buttons = ArrayList<ImageButton>()
        collectButtons(root, buttons)
        buttons.firstOrNull { sameIcon(activity, it, R.drawable.ic_attach) }?.setOnClickListener {
            activity.startActivity(Intent(activity, NexoAttachmentActivity::class.java).apply {
                putExtra("conversation_id", conversationId)
            })
        }
        buttons.firstOrNull { sameIcon(activity, it, R.drawable.ic_mic) }?.setOnClickListener {
            activity.startActivity(Intent(activity, NexoVoiceActivity::class.java).apply {
                putExtra("conversation_id", conversationId)
            })
        }
        buttons.firstOrNull { sameIcon(activity, it, R.drawable.ic_call) }?.setOnClickListener {
            requestCall(activity, conversationId, "audio")
        }
        buttons.firstOrNull { sameIcon(activity, it, R.drawable.ic_more) }?.setOnClickListener {
            Toast.makeText(activity, "Звонок, вложения и голосовые сообщения доступны", Toast.LENGTH_SHORT).show()
        }
    }

    private fun collectButtons(view: View, out: MutableList<ImageButton>) {
        if (view is ImageButton) out += view
        if (view is ViewGroup) for (i in 0 until view.childCount) collectButtons(view.getChildAt(i), out)
    }

    private fun sameIcon(activity: Activity, button: ImageButton, res: Int): Boolean {
        val a = button.drawable?.constantState ?: return false
        val b = activity.getDrawable(res)?.constantState ?: return false
        return a == b
    }

    private fun requestCall(activity: Activity, conversationId: String, kind: String) {
        val uid = SupabaseClient.userId ?: return
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val other = with(Dispatchers.IO) {
                    val base = BuildConfig.SUPABASE_URL.trimEnd('/')
                    val key = BuildConfig.SUPABASE_KEY.trim()
                    val req = Request.Builder()
                        .url("$base/rest/v1/conversation_members?conversation_id=eq.$conversationId&user_id=neq.$uid&select=user_id&limit=1")
                        .header("apikey", key)
                        .header("Authorization", "Bearer ${SupabaseClient.accessToken ?: key}")
                        .get().build()
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) error("Supabase ${r.code}")
                        val arr = JSONArray(r.body?.string().orEmpty())
                        if (arr.length() == 0) error("Собеседник не найден")
                        arr.getJSONObject(0).getString("user_id")
                    }
                }
                with(Dispatchers.IO) {
                    val base = BuildConfig.SUPABASE_URL.trimEnd('/')
                    val key = BuildConfig.SUPABASE_KEY.trim()
                    val body = JSONObject()
                        .put("conversation_id", conversationId)
                        .put("caller_id", uid)
                        .put("callee_id", other)
                        .put("kind", kind)
                        .put("status", "ringing")
                        .toString()
                    val req = Request.Builder()
                        .url("$base/rest/v1/call_sessions")
                        .header("apikey", key)
                        .header("Authorization", "Bearer ${SupabaseClient.accessToken ?: key}")
                        .header("Content-Type", "application/json")
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build()
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) error(r.body?.string().orEmpty().ifBlank { "Supabase ${r.code}" })
                    }
                }
                Toast.makeText(activity, "Вызов отправлен", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(activity, e.message ?: "Не удалось начать вызов", Toast.LENGTH_LONG).show()
            }
        }
    }
}
