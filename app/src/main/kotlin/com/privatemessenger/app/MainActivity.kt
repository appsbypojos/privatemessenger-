package com.privatemessenger.app

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollingJob: Job? = null
    private var searchJob: Job? = null
    private var conversationId: String? = null
    private var lastMessageCount = -1
    private lateinit var messageList: LinearLayout
    private lateinit var messageScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (SupabaseClient.accessToken != null) showPeople() else showAuth()
    }

    override fun onDestroy() {
        pollingJob?.cancel(); searchJob?.cancel(); scope.cancel(); super.onDestroy()
    }

    private fun applyInsets(root: View) {
        val baseLeft = root.paddingLeft; val baseTop = root.paddingTop; val baseRight = root.paddingRight; val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(baseLeft, baseTop + bars.top, baseRight, baseBottom + maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun baseRoot() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }

    private fun showAuth() {
        pollingJob?.cancel()
        val root = baseRoot().apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(28.dp(), 24.dp(), 28.dp(), 20.dp()) }
        val logo = TextView(this).apply {
            text = "N"; textSize = 44f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(Color.BLACK)
        }
        root.addView(logo, LinearLayout.LayoutParams(76.dp(), 76.dp()).apply { bottomMargin = 18.dp() })
        root.addView(TextView(this).apply { text = "nexo"; textSize = 34f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER }, lp(0, 4.dp()))
        root.addView(TextView(this).apply { text = "Общайтесь по уникальному нику"; textSize = 16f; gravity = Gravity.CENTER; setTextColor(Color.DKGRAY) }, lp(0, 28.dp()))

        val username = EditText(this).apply { hint = "Уникальный ник"; singleLine = true; inputType = InputType.TYPE_CLASS_TEXT }
        val password = EditText(this).apply { hint = "Пароль"; singleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        root.addView(username, fieldLp()); root.addView(password, fieldLp())
        val login = Button(this).apply { text = "Войти"; isAllCaps = false }
        val register = Button(this).apply { text = "Создать аккаунт"; isAllCaps = false }
        root.addView(login, buttonLp()); root.addView(register, buttonLp())
        root.addView(TextView(this).apply { text = "Ник: 3–24 символа • a-z, 0-9, _"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(Color.GRAY) }, lp(0, 12.dp()))
        setContentView(root); applyInsets(root)
        login.setOnClickListener { authenticate(username.text.toString(), password.text.toString(), false) }
        register.setOnClickListener { authenticate(username.text.toString(), password.text.toString(), true) }
    }

    private fun authenticate(username: String, password: String, register: Boolean) {
        val nick = username.trim().lowercase()
        if (!Regex("^[a-z0-9_]{3,24}$").matches(nick)) { toast("Ник: 3–24 символа, только a-z, 0-9 и _"); return }
        if (password.length < 6) { toast("Пароль должен содержать минимум 6 символов"); return }
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (register) SupabaseClient.signUp(nick, password) else SupabaseClient.signIn(nick, password) }
                if (register && SupabaseClient.accessToken == null) {
                    toast("Аккаунт создан. Для входа только по нику отключи Confirm email в Supabase Auth.")
                } else showPeople()
            } catch (e: Exception) { toast(e.message ?: "Ошибка авторизации") }
        }
    }

    private fun showPeople() {
        pollingJob?.cancel(); conversationId = null; lastMessageCount = -1
        val root = baseRoot().apply { setPadding(18.dp(), 12.dp(), 18.dp(), 12.dp()) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "nexo"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, 52.dp(), 1f))
        val logout = Button(this).apply { text = "Выйти"; isAllCaps = false }
        header.addView(logout, LinearLayout.LayoutParams(-2, 52.dp())); root.addView(header)

        val search = EditText(this).apply { hint = "Поиск по нику"; singleLine = true; inputType = InputType.TYPE_CLASS_TEXT; setPadding(16.dp(), 0, 16.dp(), 0) }
        root.addView(search, LinearLayout.LayoutParams(-1, 54.dp()).apply { topMargin = 8.dp(); bottomMargin = 10.dp() })
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 4.dp(), 0, 4.dp()) }
        root.addView(ScrollView(this).apply { addView(results) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root); applyInsets(root)
        logout.setOnClickListener { scope.launch { withContext(Dispatchers.IO) { SupabaseClient.signOut() }; showAuth() } }

        fun render(arr: org.json.JSONArray) {
            results.removeAllViews()
            if (arr.length() == 0) {
                results.addView(TextView(this).apply { text = if (search.text.isNullOrBlank()) "Пользователи не найдены" else "Ничего не найдено"; textSize = 16f; setTextColor(Color.GRAY); setPadding(8.dp(), 24.dp(), 8.dp(), 24.dp()) }); return
            }
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i); val id = p.getString("id"); val nick = p.optString("username")
                val button = Button(this).apply { text = "@$nick"; textSize = 17f; isAllCaps = false; gravity = Gravity.START or Gravity.CENTER_VERTICAL }
                results.addView(button, LinearLayout.LayoutParams(-1, 58.dp()).apply { bottomMargin = 6.dp() })
                button.setOnClickListener { openChat(id, nick) }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel(); searchJob = scope.launch { delay(220); try { render(withContext(Dispatchers.IO) { SupabaseClient.searchProfiles(s?.toString().orEmpty()) }) } catch (e: Exception) { toast(e.message ?: "Ошибка поиска") } }
            }
        })
        scope.launch { try { render(withContext(Dispatchers.IO) { SupabaseClient.searchProfiles("") }) } catch (e: Exception) { toast(e.message ?: "Не удалось загрузить пользователей") } }
    }

    private fun openChat(otherUserId: String, name: String) {
        pollingJob?.cancel(); conversationId = null; lastMessageCount = -1
        val root = baseRoot()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp()) }
        val back = Button(this).apply { text = "‹"; textSize = 26f; isAllCaps = false }
        val chatTitle = TextView(this).apply { text = "@$name"; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setPadding(10.dp(), 0, 0, 0) }
        header.addView(back, LinearLayout.LayoutParams(56.dp(), 52.dp())); header.addView(chatTitle, LinearLayout.LayoutParams(0, 52.dp(), 1f)); root.addView(header)
        messageList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp()) }
        messageScroll = ScrollView(this).apply { addView(messageList) }; root.addView(messageScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this).apply { gravity = Gravity.BOTTOM; setPadding(8.dp(), 6.dp(), 8.dp(), 6.dp()) }
        val input = EditText(this).apply { hint = "Сообщение"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; maxLines = 4 }
        val send = Button(this).apply { text = "➤"; isAllCaps = false }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(send, LinearLayout.LayoutParams(58.dp(), 54.dp())); root.addView(row)
        setContentView(root); applyInsets(root)
        back.setOnClickListener { pollingJob?.cancel(); showPeople() }
        send.setOnClickListener {
            val text = input.text.toString().trim(); if (text.isBlank()) return@setOnClickListener; send.isEnabled = false
            scope.launch { try { val cid = conversationId ?: withContext(Dispatchers.IO) { SupabaseClient.directConversation(otherUserId) }.also { conversationId = it }; withContext(Dispatchers.IO) { SupabaseClient.send(cid, text) }; input.text.clear(); refresh(cid) } catch (e: Exception) { toast(e.message ?: "Ошибка отправки") } finally { send.isEnabled = true } }
        }
        scope.launch { try { val cid = withContext(Dispatchers.IO) { SupabaseClient.directConversation(otherUserId) }; conversationId = cid; refresh(cid); pollingJob = launch { while (isActive) { delay(1500); try { refresh(cid) } catch (_: Exception) {} } } } catch (e: Exception) { toast(e.message ?: "Не удалось открыть чат") } }
    }

    private suspend fun refresh(cid: String) {
        val arr = withContext(Dispatchers.IO) { SupabaseClient.messages(cid) }
        if (arr.length() == lastMessageCount) return
        lastMessageCount = arr.length(); messageList.removeAllViews()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val mine = o.optString("sender_id") == SupabaseClient.userId
            val bubble = TextView(this).apply { text = o.optString("body"); textSize = 16f; setTextColor(Color.BLACK); setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp()); gravity = if (mine) Gravity.END else Gravity.START }
            messageList.addView(bubble, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 3.dp(); bottomMargin = 3.dp() })
        }
        messageScroll.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun fieldLp() = LinearLayout.LayoutParams(-1, 56.dp()).apply { bottomMargin = 10.dp() }
    private fun buttonLp() = LinearLayout.LayoutParams(-1, 52.dp()).apply { bottomMargin = 8.dp() }
    private fun lp(top: Int, bottom: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = top; bottomMargin = bottom }
    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
