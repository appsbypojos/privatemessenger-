package com.privatemessenger.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var searchJob: Job? = null
    private var chatPoll: Job? = null
    private var selectedTab = 1
    private var currentChatId: String? = null
    private var lastCount = -1
    private lateinit var messageList: LinearLayout
    private lateinit var messageScroll: ScrollView

    private val bg = Color.rgb(244, 248, 252)
    private val surface = Color.WHITE
    private val ink = Color.rgb(35, 43, 55)
    private val muted = Color.rgb(115, 128, 145)
    private val accent = Color.rgb(55, 137, 232)
    private val accent2 = Color.rgb(104, 73, 226)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (SupabaseClient.accessToken == null) showAuth() else showHome(selectedTab)
    }

    override fun onDestroy() {
        searchJob?.cancel()
        chatPoll?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun params(w: Int = -1, h: Int = -2) = LinearLayout.LayoutParams(w, h)
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun rounded(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun gradient() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(accent, accent2)
    ).apply { cornerRadius = dp(22).toFloat() }
    private fun label(text: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun root() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bg)
    }
    private fun applyInsets(view: View) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(left, top + bars.top, right, bottom + maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun showAuth() {
        chatPoll?.cancel()
        val r = root().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), dp(20))
        }
        r.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_nexo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, params(dp(92), dp(92)).apply { bottomMargin = dp(12) })
        r.addView(label("nexo", 34f, ink, true).apply { gravity = Gravity.CENTER }, params().apply { bottomMargin = dp(6) })
        r.addView(label("Общайся. Ищи. Будь рядом.", 15f, muted).apply { gravity = Gravity.CENTER }, params().apply { bottomMargin = dp(26) })

        val nick = EditText(this).apply {
            hint = "Уникальный ник"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val pass = EditText(this).apply {
            hint = "Пароль"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        r.addView(nick, params().apply { height = dp(54); bottomMargin = dp(10) })
        r.addView(pass, params().apply { height = dp(54); bottomMargin = dp(12) })
        val login = Button(this).apply {
            text = "Войти"
            isAllCaps = false
            background = gradient()
            setTextColor(Color.WHITE)
        }
        val register = Button(this).apply {
            text = "Создать аккаунт"
            isAllCaps = false
            background = rounded(surface)
            setTextColor(accent)
        }
        r.addView(login, params().apply { height = dp(52); bottomMargin = dp(8) })
        r.addView(register, params().apply { height = dp(52) })
        r.addView(label("3–24 символа • a-z, 0-9, _", 12f, muted).apply { gravity = Gravity.CENTER }, params().apply { topMargin = dp(14) })
        setContentView(r)
        applyInsets(r)
        login.setOnClickListener { authenticate(nick.text.toString(), pass.text.toString(), false) }
        register.setOnClickListener { authenticate(nick.text.toString(), pass.text.toString(), true) }
    }

    private fun authenticate(username: String, password: String, register: Boolean) {
        val n = username.trim().lowercase()
        if (!Regex("[a-z0-9_]{3,24}").matches(n)) {
            toast("Ник: 3–24 символа, только a-z, 0-9 и _")
            return
        }
        if (password.length < 6) {
            toast("Пароль минимум 6 символов")
            return
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (register) SupabaseClient.register(n, password) else SupabaseClient.signIn(n, password)
                }
                showHome(1)
            } catch (e: Exception) {
                toast(e.message ?: "Ошибка авторизации")
            }
        }
    }

    private fun showHome(tab: Int) {
        selectedTab = tab
        chatPoll?.cancel()
        currentChatId = null
        when (tab) {
            0 -> showSearch()
            1 -> showChats()
            2 -> showSettings()
            else -> showProfile()
        }
    }

    private fun shell(title: String, content: View, subtitle: String? = null) {
        val r = root()
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(5))
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label(title, 28f, ink, true))
        if (subtitle != null) titles.addView(label(subtitle, 13f, muted))
        bar.addView(titles, params(0, dp(58)).apply { weight = 1f })
        r.addView(bar)
        r.addView(content, params(-1, 0).apply { weight = 1f })
        r.addView(bottomNav())
        setContentView(r)
        applyInsets(r)
    }

    private fun bottomNav(): View {
        val nav = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(surface)
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }
        val items = listOf("⌕\nПоиск", "▣\nЧаты", "⚙\nНастройки", "●\nПрофиль")
        items.forEachIndexed { index, text ->
            val b = TextView(this).apply {
                this.text = text
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (index == selectedTab) accent else muted)
                setTypeface(null, if (index == selectedTab) Typeface.BOLD else Typeface.NORMAL)
                isClickable = true
            }
            nav.addView(b, params(0, dp(58)).apply { weight = 1f })
            b.setOnClickListener { showHome(index) }
        }
        return nav
    }

    private fun actionCard(icon: String, title: String, color: Int, action: () -> Unit): View {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(color, 20)
            isClickable = true
        }
        c.addView(label(icon, 25f, ink, true).apply { gravity = Gravity.CENTER })
        c.addView(label(title, 12f, ink, true).apply { gravity = Gravity.CENTER })
        c.setOnClickListener { action() }
        return c
    }

    private fun showChats() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(8))
        }
        val quick = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val cards = LinearLayout(this).apply { setPadding(0, dp(3), dp(6), dp(9)) }
        cards.addView(actionCard("⌕", "Найти людей", Color.rgb(207, 234, 251)) { showHome(0) }, params(dp(112), dp(116)).apply { rightMargin = dp(10) })
        cards.addView(actionCard("✉", "Пригласить", Color.rgb(214, 231, 246)) { toast("Приглашения скоро") }, params(dp(112), dp(116)).apply { rightMargin = dp(10) })
        cards.addView(actionCard("👥", "Новый чат", Color.rgb(198, 226, 242)) { showHome(0) }, params(dp(112), dp(116)))
        quick.addView(cards)
        content.addView(quick, params().apply { height = dp(126) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(ScrollView(this).apply { addView(list) }, params().apply { weight = 1f })
        shell("Nexo", content, "Твои последние разговоры")
        scope.launch {
            try {
                val chats = withContext(Dispatchers.IO) { SupabaseClient.chats() }
                list.removeAllViews()
                if (chats.length() == 0) {
                    list.addView(label("Пока нет чатов\nНайди пользователя и начни разговор", 16f, muted).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, dp(30), 0, dp(30))
                    })
                    return@launch
                }
                for (i in 0 until chats.length()) {
                    val item = chats.getJSONObject(i)
                    val cid = item.optString("conversation_id")
                    val name = item.optString("other_username").ifBlank { "Пользователь" }
                    val last = item.optString("last_message").ifBlank { "Нет сообщений" }
                    val row = LinearLayout(this@MainActivity).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        background = rounded(surface, 18)
                        setPadding(dp(12), dp(9), dp(12), dp(9))
                    }
                    row.addView(TextView(this@MainActivity).apply {
                        text = name.take(1).uppercase()
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        background = gradient()
                    }, params(dp(50), dp(50)).apply { rightMargin = dp(12) })
                    val info = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                    info.addView(label("@$name", 16f, ink, true))
                    info.addView(label(last, 13f, muted).apply { maxLines = 1 })
                    row.addView(info, params(0, dp(58)).apply { weight = 1f })
                    list.addView(row, params().apply { bottomMargin = dp(7) })
                    if (cid.isNotBlank()) row.setOnClickListener { openExistingChat(cid, name) }
                }
            } catch (e: Exception) {
                list.removeAllViews()
                list.addView(label("Не удалось загрузить чаты\n${e.message ?: "Ошибка"}", 14f, muted).apply {
                    setPadding(dp(12), dp(24), dp(12), dp(24))
                })
            }
        }
    }

    private fun showSearch() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(3), dp(14), dp(8))
        }
        val search = EditText(this).apply {
            hint = "Поиск по нику"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(surface, 18)
        }
        content.addView(search, params().apply { height = dp(54); bottomMargin = dp(10) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(ScrollView(this).apply { addView(list) }, params().apply { weight = 1f })
        shell("Поиск", content, "Найди людей по уникальному нику")

        fun render(results: JSONArray) {
            list.removeAllViews()
            if (results.length() == 0) {
                list.addView(label(if (search.text.isNullOrBlank()) "Начни вводить ник" else "Пользователь не найден", 16f, muted).apply {
                    setPadding(dp(12), dp(24), dp(12), dp(24))
                })
                return
            }
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val id = item.optString("id")
                val name = item.optString("username")
                val display = item.optString("display_name").ifBlank { name }
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(surface, 18)
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                }
                row.addView(TextView(this).apply {
                    text = name.take(1).uppercase()
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    background = gradient()
                }, params(dp(46), dp(46)).apply { rightMargin = dp(12) })
                val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                info.addView(label(display, 16f, ink, true))
                info.addView(label("@$name", 13f, muted))
                row.addView(info, params(0, -2).apply { weight = 1f })
                list.addView(row, params().apply { bottomMargin = dp(7) })
                if (id.isNotBlank()) row.setOnClickListener { openChat(id, name) }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(220)
                    try {
                        render(withContext(Dispatchers.IO) { SupabaseClient.searchProfiles(s?.toString().orEmpty()) })
                    } catch (e: Exception) {
                        toast(e.message ?: "Ошибка поиска")
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    private fun showSettings() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        fun setting(title: String, subtitle: String): View {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(surface, 18)
                setPadding(dp(16), dp(8), dp(10), dp(8))
            }
            val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            box.addView(label(title, 16f, ink, true))
            box.addView(label(subtitle, 12f, muted))
            row.addView(box, params(0, dp(62)).apply { weight = 1f })
            row.addView(Switch(this@MainActivity).apply { isChecked = true }, params(dp(58), dp(50)))
            return row
        }
        content.addView(setting("Уведомления", "Новые сообщения и события"), params().apply { bottomMargin = dp(8) })
        content.addView(setting("Звук сообщений", "Звуковые уведомления"), params().apply { bottomMargin = dp(8) })
        content.addView(setting("Предпросмотр сообщений", "Показывать текст в уведомлениях"), params().apply { bottomMargin = dp(8) })
        content.addView(label("Nexo\nВерсия 1.0\n\nБыстрый мессенджер на Supabase.", 14f, muted).apply {
            setPadding(dp(16), dp(20), dp(16), 0)
        }, params().apply { weight = 1f })
        shell("Настройки", content, "Персонализируй Nexo")
    }

    private fun showProfile() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = gradient()
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }
        val avatar = TextView(this).apply {
            text = "N"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.argb(60, 255, 255, 255), 32)
        }
        card.addView(avatar, params(dp(72), dp(72)).apply { bottomMargin = dp(10) })
        val nick = label("@username", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        card.addView(nick)
        content.addView(card, params().apply { bottomMargin = dp(12) })
        val name = EditText(this).apply { hint = "Имя"; setSingleLine(true); background = rounded(surface, 18); setPadding(dp(16), 0, dp(16), 0) }
        val bio = EditText(this).apply { hint = "О себе"; maxLines = 4; background = rounded(surface, 18); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        val save = Button(this).apply { text = "Сохранить профиль"; isAllCaps = false; background = gradient(); setTextColor(Color.WHITE) }
        val logout = Button(this).apply { text = "Выйти"; isAllCaps = false; background = rounded(surface); setTextColor(Color.rgb(210, 65, 75)) }
        content.addView(name, params().apply { height = dp(54); bottomMargin = dp(8) })
        content.addView(bio, params().apply { height = dp(105); bottomMargin = dp(10) })
        content.addView(save, params().apply { height = dp(52); bottomMargin = dp(8) })
        content.addView(logout, params().apply { height = dp(52) })
        content.addView(Space(this), params().apply { weight = 1f })
        shell("Профиль", content, "Твои данные Nexo")
        scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { SupabaseClient.myProfile() }
                val u = p.optString("username")
                nick.text = "@${u.ifBlank { "username" }}"
                name.setText(p.optString("display_name"))
                bio.setText(p.optString("bio"))
                avatar.text = u.take(1).uppercase().ifBlank { "N" }
            } catch (e: Exception) {
                toast(e.message ?: "Ошибка профиля")
            }
        }
        save.setOnClickListener {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { SupabaseClient.updateProfile(name.text.toString(), bio.text.toString()) }
                    toast("Профиль сохранён")
                } catch (e: Exception) {
                    toast(e.message ?: "Ошибка сохранения")
                }
            }
        }
        logout.setOnClickListener {
            scope.launch {
                withContext(Dispatchers.IO) { SupabaseClient.signOut() }
                showAuth()
            }
        }
    }

    private fun openExistingChat(conversationId: String, name: String) {
        if (conversationId.isBlank()) {
            toast("Не удалось определить чат")
            return
        }
        openChatInternal(conversationId, name)
    }

    private fun openChat(otherUserId: String, name: String) {
        if (otherUserId.isBlank()) {
            toast("Не удалось определить пользователя")
            return
        }
        scope.launch {
            try {
                val cid = withContext(Dispatchers.IO) { SupabaseClient.directConversation(otherUserId) }
                if (cid.isBlank()) throw IllegalStateException("Supabase не вернул ID чата")
                openChatInternal(cid, name)
            } catch (e: Exception) {
                toast(e.message ?: "Не удалось открыть чат")
            }
        }
    }

    private fun openChatInternal(conversationId: String, name: String) {
        chatPoll?.cancel()
        currentChatId = conversationId
        lastCount = -1

        val r = root()
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(surface)
            setPadding(dp(5), dp(2), dp(12), dp(2))
        }
        val back = Button(this).apply {
            text = "‹"
            textSize = 30f
            isAllCaps = false
            setTextColor(accent)
            background = null
            contentDescription = "Назад"
        }
        header.addView(back, params(dp(52), dp(56)))
        header.addView(TextView(this).apply {
            text = name.take(1).uppercase().ifBlank { "N" }
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = gradient()
        }, params(dp(44), dp(44)).apply { rightMargin = dp(10) })
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title.addView(label("@$name", 18f, ink, true))
        title.addView(label("Nexo", 12f, muted))
        header.addView(title, params(0, dp(56)).apply { weight = 1f })
        r.addView(header)

        messageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        messageScroll = ScrollView(this).apply {
            addView(messageList, ScrollView.LayoutParams(-1, -2))
            setBackgroundColor(bg)
            isFillViewport = true
        }
        r.addView(messageScroll, params().apply { weight = 1f })

        val composer = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setBackgroundColor(surface)
        }
        val input = EditText(this).apply {
            hint = "Сообщение"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            background = rounded(bg, 22)
            setPadding(dp(16), dp(5), dp(12), dp(5))
        }
        val send = Button(this).apply {
            text = "➤"
            textSize = 18f
            isAllCaps = false
            background = gradient()
            setTextColor(Color.WHITE)
            contentDescription = "Отправить"
        }
        composer.addView(input, params(0, -2).apply { weight = 1f; rightMargin = dp(7) })
        composer.addView(send, params(dp(54), dp(54)))
        r.addView(composer)
        setContentView(r)
        applyInsets(r)

        back.setOnClickListener { chatPoll?.cancel(); currentChatId = null; showHome(1) }
        send.setOnClickListener {
            val body = input.text.toString().trim()
            if (body.isBlank()) return@setOnClickListener
            send.isEnabled = false
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { SupabaseClient.send(conversationId, body) }
                    input.text.clear()
                    refreshMessages(conversationId, false)
                } catch (e: Exception) {
                    toast(e.message ?: "Ошибка отправки")
                } finally {
                    send.isEnabled = true
                }
            }
        }

        scope.launch {
            try {
                refreshMessages(conversationId, true)
            } catch (e: Exception) {
                // A network/RLS error must never crash the activity.
                showChatError(e.message ?: "Не удалось загрузить сообщения")
            }
            chatPoll = launch {
                while (isActive && currentChatId == conversationId) {
                    delay(1500)
                    try {
                        refreshMessages(conversationId, false)
                    } catch (e: Exception) {
                        // Keep the chat screen alive during temporary network failures.
                    }
                }
            }
        }
    }

    private fun showChatError(message: String) {
        if (!::messageList.isInitialized) return
        if (messageList.childCount == 0) {
            messageList.addView(label("Не удалось загрузить сообщения\n$message", 14f, muted).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(30), dp(12), dp(30))
            })
        } else {
            toast(message)
        }
    }

    private suspend fun refreshMessages(conversationId: String, force: Boolean) {
        val results = withContext(Dispatchers.IO) { SupabaseClient.messages(conversationId) }
        if (!force && results.length() == lastCount) return
        lastCount = results.length()
        withContext(Dispatchers.Main) {
            if (!::messageList.isInitialized || currentChatId != conversationId) return@withContext
            messageList.removeAllViews()
            if (results.length() == 0) {
                messageList.addView(label("Начни разговор 👋", 16f, muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(30), 0, dp(30))
                })
            }
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val mine = item.optString("sender_id") == SupabaseClient.userId
                val wrap = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = if (mine) Gravity.END else Gravity.START
                }
                val bubble = label(item.optString("body"), 16f, ink).apply {
                    setPadding(dp(15), dp(10), dp(15), dp(10))
                    background = rounded(if (mine) Color.rgb(215, 235, 255) else surface, 18)
                }
                wrap.addView(bubble, params(0, -2).apply {
                    weight = 1f
                    leftMargin = if (mine) dp(58) else 0
                    rightMargin = if (mine) 0 else dp(58)
                })
                messageList.addView(wrap, params().apply { topMargin = dp(3); bottomMargin = dp(3) })
            }
            messageScroll.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }
}
