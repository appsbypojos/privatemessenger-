package com.privatemessenger.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class NexoAttachmentActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val http = OkHttpClient()
    private val pick = 7201
    private var conversationId = ""

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        conversationId = intent.getStringExtra("conversation_id").orEmpty()
        if (conversationId.isBlank()) { finish(); return }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, pick)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pick) return
        if (resultCode != RESULT_OK || data?.data == null) { finish(); return }
        upload(data.data!!)
    }

    private fun upload(uri: Uri) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { uploadBytes(uri) }
                SupabaseClient.send(conversationId, "📎 ${result.first}\n${result.second}")
                Toast.makeText(this@NexoAttachmentActivity, "Вложение отправлено", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@NexoAttachmentActivity, e.message ?: "Ошибка вложения", Toast.LENGTH_LONG).show()
            } finally { finish() }
        }
    }

    private fun uploadBytes(uri: Uri): Pair<String, String> {
        val resolver = contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "file-${System.currentTimeMillis()}"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Не удалось прочитать файл")
        require(bytes.size <= 25 * 1024 * 1024) { "Файл больше 25 МБ" }

        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_KEY.trim()
        val path = "${SupabaseClient.userId}/${conversationId}/${UUID.randomUUID()}-$name"
        val upload = Request.Builder()
            .url("$base/storage/v1/object/chat-attachments/$path")
            .header("apikey", key)
            .header("Authorization", "Bearer ${SupabaseClient.accessToken ?: key}")
            .header("Content-Type", mime)
            .header("x-upsert", "false")
            .post(bytes.toRequestBody(mime.toMediaType()))
            .build()
        http.newCall(upload).execute().use { r ->
            if (!r.isSuccessful) error("Загрузка: ${r.code} ${r.body?.string().orEmpty()}")
        }
        val publicUrl = "$base/storage/v1/object/public/chat-attachments/$path"
        val meta = JSONObject()
            .put("conversation_id", conversationId)
            .put("uploader_id", SupabaseClient.userId)
            .put("storage_path", path)
            .put("file_name", name)
            .put("mime_type", mime)
            .put("file_size", bytes.size)
            .toString()
        val insert = Request.Builder()
            .url("$base/rest/v1/message_attachments")
            .header("apikey", key)
            .header("Authorization", "Bearer ${SupabaseClient.accessToken ?: key}")
            .header("Content-Type", "application/json")
            .post(meta.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(insert).execute().use { r ->
            if (!r.isSuccessful) error("Метаданные: ${r.code} ${r.body?.string().orEmpty()}")
        }
        return name to publicUrl
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
