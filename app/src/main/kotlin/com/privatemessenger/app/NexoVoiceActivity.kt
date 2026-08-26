package com.privatemessenger.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
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
import java.io.File
import java.util.UUID

class NexoVoiceActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val http = OkHttpClient()
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var conversationId = ""
    private var recording = false
    private val permission = 7301

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        conversationId = intent.getStringExtra("conversation_id").orEmpty()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
        }
        val title = TextView(this).apply { text = "Голосовое сообщение"; textSize = 22f }
        val status = TextView(this).apply { text = "Нажми «Записать» и говори"; textSize = 16f }
        val button = Button(this).apply { text = "Записать" }
        val send = Button(this).apply { text = "Отправить"; isEnabled = false }
        root.addView(title); root.addView(status); root.addView(button); root.addView(send)
        setContentView(root)

        button.setOnClickListener {
            if (recording) stopRecording(status, send, button) else startRecording(status, button)
        }
        send.setOnClickListener { upload() }
    }

    private fun startRecording(status: TextView, button: Button) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), permission)
            return
        }
        try {
            file = File(cacheDir, "voice-${UUID.randomUUID()}.m4a")
            recorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setMaxDuration(120000)
                setOutputFile(file!!.absolutePath)
                prepare()
                start()
            }
            recording = true
            button.text = "Остановить"
            status.text = "Идёт запись… максимум 2 минуты"
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            Toast.makeText(this, e.message ?: "Не удалось начать запись", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording(status: TextView, send: Button, button: Button) {
        try { recorder?.stop() } catch (_: Exception) { file?.delete() }
        recorder?.release(); recorder = null
        recording = false
        button.text = "Записать заново"
        status.text = "Запись готова"
        send.isEnabled = file?.exists() == true
    }

    private fun upload() {
        val f = file ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val base = BuildConfig.SUPABASE_URL.trimEnd('/')
                    val key = BuildConfig.SUPABASE_KEY.trim()
                    val path = "${SupabaseClient.userId}/${conversationId}/${f.name}"
                    val bytes = f.readBytes()
                    val upload = Request.Builder()
                        .url("$base/storage/v1/object/chat-attachments/$path")
                        .header("apikey", key)
                        .header("Authorization", "Bearer ${SupabaseClient.accessToken ?: key}")
                        .header("Content-Type", "audio/mp4")
                        .post(bytes.toRequestBody("audio/mp4".toMediaType()))
                        .build()
                    http.newCall(upload).execute().use { r ->
                        if (!r.isSuccessful) error("Загрузка: ${r.code} ${r.body?.string().orEmpty()}")
                    }
                    val url = "$base/storage/v1/object/public/chat-attachments/$path"
                    SupabaseClient.send(conversationId, "🎙 Голосовое сообщение\n$url")
                }
                Toast.makeText(this@NexoVoiceActivity, "Голосовое отправлено", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@NexoVoiceActivity, e.message ?: "Ошибка отправки", Toast.LENGTH_LONG).show()
            } finally { finish() }
        }
    }

    override fun onDestroy() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        scope.cancel()
        super.onDestroy()
    }
}
