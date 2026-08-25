package com.privatemessenger.app

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var conversationId: String? = null
    private var selectedName = ""
    private var lastMessageCount = -1
    private var pollingJob: Job? = null
    private lateinit var messageList: LinearLayout
    private lateinit var messageScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLogin()
    }

    override fun onDestroy() {
        pollingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun showLogin() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply { text = "Messenger"; textSize = 30f; gravity = Gravity.CENTER }
        val subtitle = TextView(this).apply { text = "Войдите через Supabase"; textSize = 16f; gravity = Gravity.CENTER }
        val email = EditText(this).apply { hint = "Email"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val password = EditText(this).apply { hint = "Пароль"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val login = Button(this).apply { text = "Войти" }
        val register = Button(this).apply { text = "Создать аккаунт" }
        root.addView(title, lp()); root.addView(subtitle, lp())
        root.addView(email, lp()); root.addView(password, lp())
        root.addView(login, lp()); root.addView(register, lp())
        setContentView(root)
        login.setOnClickListener { authenticate(email.text.toString().trim(), password.text.toString(), false) }
        register.setOnClickListener { authenticate(email.text.toString().trim(), password.text.toString(), true) }
    }

    private fun authenticate(email: String, password: String, signup: Boolean) {
        if (email.isBlank() || password.length < 6) { toast("Введите email и пароль (минимум 6 символов)"); return }
        scope.launch {
            setBusy(true)
            try {
                withContext(Dispatchers.IO) { if (signup) SupabaseClient.signUp(email, password) else SupabaseClient.signIn(email, password) }
                if (signup && SupabaseClient.accessToken == null) {
                    toast("Аккаунт создан. Подтвердите email и затем войдите.")
                } else showUsers()
            } catch (e: Exception) { toast(e.message ?: "Ошибка авторизации") }
            finally { setBusy(false) }
        }
    }

    private fun showUsers() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = "Люди"; textSize = 26f }
        val logout = Button(this).apply { text = "Выйти" }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f)); header.addView(logout)
        val users = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(users) }
        root.addView(header); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        logout.setOnClickListener { scope.launch { withContext(Dispatchers.IO) { SupabaseClient.signOut() }; showLogin() } }
        scope.launch {
            try {
                val arr = withContext(Dispatchers.IO) { SupabaseClient.profiles() }
                users.removeAllViews()
                if (arr.length() == 0) users.addView(TextView(this@MainActivity).apply { text = "Пока нет других пользователей."; textSize = 17f; setPadding(8, 24, 8, 24) })
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    val id = p.getString("id")
                    val name = p.optString("display_name").ifBlank { p.optString("username") }.ifBlank { "Пользователь" }
                    val button = Button(this@MainActivity).apply { text = name; textSize = 18f; gravity = Gravity.START or Gravity.CENTER_VERTICAL }
                    users.addView(button, LinearLayout.LayoutParams(-1, 64.dp()).apply { bottomMargin = 8.dp() })
                    button.setOnClickListener { openChat(id, name) }
                }
            } catch (e: Exception) { toast(e.message ?: "Не удалось загрузить пользователей") }
        }
    }

    private fun openChat(otherUserId: String, name: String) {
        selectedName = name
        pollingJob?.cancel()
        lastMessageCount = -1
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(12, 8, 12, 8) }
        val back = Button(this).apply { text = "‹" }
        val title = TextView(this).apply { text = name; textSize = 22f }
        header.addView(back, LinearLayout.LayoutParams(56.dp(), 52.dp())); header.addView(title)
        messageList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12) }
        messageScroll = ScrollView(this).apply { addView(messageList) }
        val row = LinearLayout(this).apply { setPadding(8, 8, 8, 8) }
        val input = EditText(this).apply { hint = "Сообщение"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; maxLines = 4 }
        val send = Button(this).apply { text = "➤" }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(send, LinearLayout.LayoutParams(60.dp(), -2))
        root.addView(header); root.addView(messageScroll, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(row)
        setContentView(root)
        back.setOnClickListener { pollingJob?.cancel(); showUsers() }
        send.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener
            send.isEnabled = false
            scope.launch {
                try {
                    val cid = conversationId ?: withContext(Dispatchers.IO) { SupabaseClient.directConversation(otherUserId) }.also { conversationId = it }
                    withContext(Dispatchers.IO) { SupabaseClient.send(cid, text) }
                    input.text.clear(); refresh(cid)
                } catch (e: Exception) { toast(e.message ?: "Ошибка отправки") }
                finally { send.isEnabled = true }
            }
        }
        scope.launch {
            try {
                conversationId = withContext(Dispatchers.IO) { SupabaseClient.directConversation(otherUserId) }
                refresh(conversationId!!)
                pollingJob = launch {
                    while (isActive) { delay(2000); try { refresh(conversationId!!) } catch (_: Exception) {} }
                }
            } catch (e: Exception) { toast(e.message ?: "Не удалось открыть чат") }
        }
    }

    private suspend fun refresh(cid: String) {
        val arr = withContext(Dispatchers.IO) { SupabaseClient.messages(cid) }
        if (arr.length() == lastMessageCount) return
        lastMessageCount = arr.length()
        messageList.removeAllViews()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val mine = o.optString("sender_id") == SupabaseClient.userId
            val text = o.optString("body").ifBlank { o.optString("ciphertext") }
            val bubble = TextView(this).apply {
                this.text = text
                textSize = 17f
                setTextColor(Color.BLACK)
                setPadding(18, 12, 18, 12)
                gravity = if (mine) Gravity.END else Gravity.START
            }
            val wrapper = FrameLayout(this)
            wrapper.addView(bubble, FrameLayout.LayoutParams(-1, -2).apply { leftMargin = 8.dp(); rightMargin = 8.dp(); topMargin = 3.dp(); bottomMargin = 3.dp() })
            messageList.addView(wrapper)
        }
        messageScroll.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun setBusy(busy: Boolean) { /* UI remains responsive; network runs on IO */ }
    private fun lp() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp() }
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
