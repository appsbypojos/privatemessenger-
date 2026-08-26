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

class NexoActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var searchJob: Job? = null
    private var pollJob: Job? = null
    private var selectedTab = 1
    private var currentChat: String? = null
    private var lastMessageCount = -1
    private lateinit var messageList: LinearLayout
    private lateinit var messageScroll: ScrollView

    private val bg = Color.rgb(244, 248, 252)
    private val surface = Color.WHITE
    private val ink = Color.rgb(35, 43, 55)
    private val muted = Color.rgb(115, 128, 145)
    private val blue = Color.rgb(36, 107, 255)
    private val purple = Color.rgb(90, 53, 232)
    private val softBlue = Color.rgb(218, 239, 255)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = bg
        window.navigationBarColor = surface
        if (SupabaseClient.accessToken == null) showAuth() else showTab(selectedTab)
    }

    override fun onDestroy() {
        searchJob?.cancel()
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun lp(width: Int = -1, height: Int = -2): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height)

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun rounded(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun gradient(radius: Int = 22) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(blue, purple)
    ).apply { cornerRadius = dp(radius).toFloat() }

    private fun label(
        value: String,
        size: Float,
        color: Int = ink,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
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
            v.setPadding(
                left,
                top + bars.top,
                right,
                bottom + maxOf(bars.bottom, ime.bottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun icon(resource: Int, alpha: Float = 1f) = ImageView(this).apply {
        setImageResource(resource)
        this.alpha = alpha
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        contentDescription = "Nexo"
    }

    private fun iconButton(resource: Int, background: android.graphics.drawable.Drawable? = null) =
        ImageButton(this).apply {
            setImageResource(resource)
            this.background = background ?: Color.TRANSPARENT.let { rounded(it, 24) }
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "Nexo"
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

    private fun showAuth() {
        pollJob?.cancel()
        val view = root().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(22), dp(28), dp(18))
        }

        view.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_nexo)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            },
            lp(dp(116), dp(116)).apply { bottomMargin = dp(8) }
        )
        view.addView(
            label("nexo", 34f, ink, true).apply { gravity = Gravity.CENTER },
            lp().apply { bottomMargin = dp(4) }
        )
        view.addView(
            label("Общайся. Ищи. Будь рядом.", 15f, muted).apply { gravity = Gravity.CENTER },
            lp().apply { bottomMargin = dp(24) }
        )

        val nick = EditText(this).apply {
            hint = "Уникальный ник"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = rounded(surface)
            setPadding(dp(16), 0, dp(16), 0)
        }
        val password = EditText(this).apply {
            hint = "Пароль"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = rounded(surface)
            setPadding(dp(16), 0, dp(16), 0)
        }
        view.addView(nick, lp().apply { height = dp(54); bottomMargin = dp(9) })
        view.addView(password, lp().apply { height = dp(54); bottomMargin = dp(11) })

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
            setTextColor(blue)
        }
        view.addView(login, lp().apply { height = dp(52); bottomMargin = dp(8) })
        view.addView(register, lp().apply { height = dp(52) })
        view.addView(
            label("3–24 символа • a-z, 0-9, _", 12f, muted).apply { gravity = Gravity.CENTER },
            lp().apply { topMargin = dp(13) }
        )

        setContentView(view)
        applyInsets(view)
        login.setOnClickListener { authenticate(nick.text.toString(), password.text.toString(), false) }
        register.setOnClickListener { authenticate(nick.text.toString(), password.text.toString(), true) }
    }

    private fun authenticate(username: String, password: String, register: Boolean) {
        val nick = username.trim().lowercase()
        if (!Regex("[a-z0-9_]{3,24}").matches(nick)) {
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
                    if (register) SupabaseClient.register(nick, password)
                    else SupabaseClient.signIn(nick, password)
                }
                showTab(1)
            } catch (e: Exception) {
                toast(e.message ?: "Ошибка авторизации")
            }
        }
    }

    private fun showTab(tab: Int) {
        selectedTab = tab.coerceIn(0, 3)
        pollJob?.cancel()
        currentChat = null
        when (selectedTab) {
            0 -> showSearch()
            1 -> showChats()
            2 -> showSettings()
            else -> showProfile()
        }
    }

    private fun shell(title: String, subtitle: String, body: View) {
        val view = root()
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(8), dp(12), dp(4))
        }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(label(title, 27f, ink, true))
        titleColumn.addView(label(subtitle, 13f, muted))
        header.addView(titleColumn, lp(0, dp(58)).apply { weight = 1f })
        view.addView(header)
        view.addView(body, lp(-1, 0).apply { weight = 1f })
        view.addView(bottomHud())
        setContentView(view)
        applyInsets(view)
    }

    private fun bottomHud(): View {
        val hud = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(surface)
            setPadding(dp(6), dp(4), dp(6), dp(3))
        }
        val resources = intArrayOf(
            R.drawable.ic_search,
            R.drawable.ic_chat,
            R.drawable.ic_settings,
            R.drawable.ic_person
        )
        val names = arrayOf("Поиск", "Чаты", "Настройки", "Профиль")

        for (i in resources.indices) {
            val selected = i == selectedTab
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                setPadding(0, dp(2), 0, dp(1))
            }
            val iconView = icon(resources[i], if (selected) 1f else 0.45f)
            if (selected) {
                iconView.background = gradient(24)
                iconView.setPadding(dp(6), dp(6), dp(6), dp(6))
            }
            cell.addView(iconView, lp(dp(42), dp(34)))
            cell.addView(
                label(names[i], 10f, if (selected) blue else muted, selected).apply {
                    gravity = Gravity.CENTER
                }
            )
            hud.addView(cell, lp(0, dp(58)).apply { weight = 1f })
            cell.setOnClickListener { showTab(i) }
        }
        return hud
    }

    private fun quickCard(resource: Int, title: String, action: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(207, 234, 251), 20)
            isClickable = true
            setPadding(dp(6), dp(4), dp(6), dp(6))
        }
        card.addView(icon(resource), lp(-1, dp(62)))
        card.addView(label(title, 12f, ink, true).apply { gravity = Gravity.CENTER })
        card.setOnClickListener { action() }
        return card
    }

    private fun showChats() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(8))
        }
        val quickScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val cards = LinearLayout(this).apply { setPadding(0, dp(3), dp(6), dp(8)) }
        cards.addView(quickCard(R.drawable.ic_search, "Найти людей") { showTab(0) }, lp(dp(112), dp(116)).apply { rightMargin = dp(10) })
        cards.addView(quickCard(R.drawable.ic_contacts, "Новый чат") { showTab(0) }, lp(dp(112), dp(116)).apply { rightMargin = dp(10) })
        cards.addView(quickCard(R.drawable.ic_add, "Пригласить") { toast("Приглашения скоро") }, lp(dp(112), dp(116)))
        quickScroll.addView(cards)
        body.addView(quickScroll, lp().apply { height = dp(126) })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        body.addView(scroll, lp().apply { weight = 1f })
        shell("Nexo", "Твои последние разговоры", body)

        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) { SupabaseClient.chats() }
                list.removeAllViews()
                if (data.length() == 0) {
                    list.addView(
                        label("Пока нет чатов\nНайди пользователя и начни разговор", 16f, muted).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, dp(30), 0, dp(30))
                        }
                    )
                    return@launch
                }
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val conversationId = item.optString("conversation_id")
                    val name = item.optString("other_username").ifBlank { "Пользователь" }
                    val last = item.optString("last_message").ifBlank { "Нет сообщений" }
                    val row = chatRow(name, last)
                    list.addView(row, lp().apply { bottomMargin = dp(7) })
                    if (conversationId.isNotBlank()) {
                        row.setOnClickListener { openChat(conversationId, name) }
                    }
                }
            } catch (e: Exception) {
                list.removeAllViews()
                list.addView(label("Не удалось загрузить чаты\n${e.message ?: "Ошибка"}", 14f, muted).apply {
                    setPadding(dp(12), dp(24), dp(12), dp(24))
                })
            }
        }
    }

    private fun chatRow(name: String, preview: String): LinearLayout {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(surface, 18)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            isClickable = true
        }
        val avatar = label(name.take(1).uppercase().ifBlank { "N" }, 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = gradient(26)
        }
        row.addView(avatar, lp(dp(50), dp(50)).apply { rightMargin = dp(12) })
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label("@$name", 16f, ink, true))
        info.addView(label(preview, 13f, muted).apply { maxLines = 1 })
        row.addView(info, lp(0, dp(58)).apply { weight = 1f })
        return row
    }

    private fun showSearch() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(3), dp(14), dp(8))
        }
        val searchBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(surface, 18)
            setPadding(dp(10), 0, dp(8), 0)
        }
        val input = EditText(this).apply {
            hint = "Поиск по нику"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = null
            setPadding(dp(8), 0, dp(8), 0)
        }
        searchBar.addView(icon(R.drawable.ic_search), lp(dp(34), dp(48)))
        searchBar.addView(input, lp(0, dp(54)).apply { weight = 1f })
        body.addView(searchBar, lp().apply { height = dp(54); bottomMargin = dp(10) })

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(ScrollView(this).apply { addView(list) }, lp().apply { weight = 1f })
        shell("Поиск", "Найди людей по уникальному нику", body)

        fun render(results: JSONArray) {
            list.removeAllViews()
            if (results.length() == 0) {
                list.addView(label(if (input.text.isNullOrBlank()) "Начни вводить ник" else "Пользователь не найден", 16f, muted).apply {
                    setPadding(dp(12), dp(24), dp(12), dp(24))
                })
                return
            }
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val id = item.optString("id")
                val username = item.optString("username")
                val display = item.optString("display_name").ifBlank { username }
                val row = chatRow(username, display)
                list.addView(row, lp().apply { bottomMargin = dp(7) })
                if (id.isNotBlank()) row.setOnClickListener { openUser(id, username) }
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = Unit
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
        })
    }

    private fun showSettings() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

        fun settingRow(title: String, subtitle: String, checked: Boolean): View {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(surface, 18)
                setPadding(dp(16), dp(8), dp(10), dp(8))
            }
            val iconResource = when (title) {
                "Уведомления" -> R.drawable.ic_bell
                "Звук сообщений" -> R.drawable.ic_mic
                else -> R.drawable.ic_chat
            }
            row.addView(icon(iconResource), lp(dp(42), dp(50)).apply { rightMargin = dp(8) })
            val textColumn = LinearLayout(this@NexoActivity).apply { orientation = LinearLayout.VERTICAL }
            textColumn.addView(label(title, 16f, ink, true))
            textColumn.addView(label(subtitle, 12f, muted))
            row.addView(textColumn, lp(0, dp(62)).apply { weight = 1f })
            row.addView(Switch(this@NexoActivity).apply { isChecked = checked }, lp(dp(58), dp(50)))
            return row
        }

        body.addView(settingRow("Уведомления", "Новые сообщения и события", true), lp().apply { bottomMargin = dp(8) })
        body.addView(settingRow("Звук сообщений", "Звуковые уведомления", true), lp().apply { bottomMargin = dp(8) })
        body.addView(settingRow("Предпросмотр", "Текст в уведомлениях", true), lp().apply { bottomMargin = dp(8) })
        body.addView(label("Nexo\nВерсия 1.0\n\nБыстрый мессенджер на Supabase.", 14f, muted).apply {
            setPadding(dp(16), dp(20), dp(16), 0)
        }, lp().apply { weight = 1f })
        shell("Настройки", "Персонализируй Nexo", body)
    }

    private fun showProfile() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val avatar = label("N", 30f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.argb(55, 255, 255, 255), 40)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = gradient()
            setPadding(dp(18), dp(20), dp(18), dp(20))
        }
        card.addView(avatar, lp(dp(76), dp(76)).apply { bottomMargin = dp(10) })
        val nickLabel = label("@username", 17f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
        card.addView(nickLabel)
        body.addView(card, lp().apply { bottomMargin = dp(12) })

        val displayName = EditText(this).apply {
            hint = "Имя"
            setSingleLine(true)
            background = rounded(surface, 18)
            setPadding(dp(16), 0, dp(16), 0)
        }
        val bio = EditText(this).apply {
            hint = "О себе"
            maxLines = 4
            background = rounded(surface, 18)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val save = Button(this).apply {
            text = "Сохранить профиль"
            isAllCaps = false
            background = gradient()
            setTextColor(Color.WHITE)
        }
        val logout = Button(this).apply {
            text = "Выйти"
            isAllCaps = false
            background = rounded(surface)
            setTextColor(Color.rgb(210, 65, 75))
        }
        body.addView(displayName, lp().apply { height = dp(54); bottomMargin = dp(8) })
        body.addView(bio, lp().apply { height = dp(105); bottomMargin = dp(10) })
        body.addView(save, lp().apply { height = dp(52); bottomMargin = dp(8) })
        body.addView(logout, lp().apply { height = dp(52) })
        body.addView(Space(this), lp().apply { weight = 1f })
        shell("Профиль", "Твои данные Nexo", body)

        scope.launch {
            try {
                val profile = withContext(Dispatchers.IO) { SupabaseClient.myProfile() }
                val username = profile.optString("username")
                nickLabel.text = "@${username.ifBlank { "username" }}"
                displayName.setText(profile.optString("display_name"))
                bio.setText(profile.optString("bio"))
                avatar.text = username.take(1).uppercase().ifBlank { "N" }
            } catch (e: Exception) {
                toast(e.message ?: "Ошибка профиля")
            }
        }

        save.setOnClickListener {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        SupabaseClient.updateProfile(displayName.text.toString(), bio.text.toString())
                    }
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

    private fun openUser(userId: String, username: String) {
        scope.launch {
            try {
                val conversationId = withContext(Dispatchers.IO) {
                    SupabaseClient.directConversation(userId)
                }
                if (conversationId.isBlank()) error("Supabase не вернул ID чата")
                openChat(conversationId, username)
            } catch (e: Exception) {
                toast(e.message ?: "Не удалось открыть чат")
            }
        }
    }

    private fun openChat(conversationId: String, username: String) {
        pollJob?.cancel()
        currentChat = conversationId
        lastMessageCount = -1

        val view = root()
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(surface)
            setPadding(dp(5), dp(2), dp(8), dp(2))
        }
        val back = iconButton(R.drawable.ic_back)
        val avatar = label(username.take(1).uppercase().ifBlank { "N" }, 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = gradient(24)
        }
        val titleColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(label("@$username", 18f, ink, true))
        titleColumn.addView(label("Nexo", 12f, muted))
        val call = iconButton(R.drawable.ic_call)
        val more = iconButton(R.drawable.ic_more)

        header.addView(back, lp(dp(48), dp(54)))
        header.addView(avatar, lp(dp(44), dp(44)).apply { rightMargin = dp(10) })
        header.addView(titleColumn, lp(0, dp(56)).apply { weight = 1f })
        header.addView(call, lp(dp(44), dp(50)))
        header.addView(more, lp(dp(44), dp(50)))
        view.addView(header)

        messageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        messageScroll = ScrollView(this).apply {
            addView(messageList)
            setBackgroundColor(bg)
            isFillViewport = true
        }
        view.addView(messageScroll, lp().apply { weight = 1f })

        val composer = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            setBackgroundColor(surface)
            setPadding(dp(7), dp(6), dp(7), dp(6))
        }
        val attach = iconButton(R.drawable.ic_attach)
        val input = EditText(this).apply {
            hint = "Сообщение"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            background = rounded(bg, 22)
            setPadding(dp(14), dp(5), dp(10), dp(5))
        }
        val mic = iconButton(R.drawable.ic_mic)
        val send = iconButton(R.drawable.ic_send, gradient(26))
        composer.addView(attach, lp(dp(44), dp(50)))
        composer.addView(input, lp(0, -2).apply { weight = 1f; leftMargin = dp(3); rightMargin = dp(3) })
        composer.addView(mic, lp(dp(44), dp(50)))
        composer.addView(send, lp(dp(48), dp(50)))
        view.addView(composer)

        setContentView(view)
        applyInsets(view)
        NexoFeatureModule.install(this, conversationId, view)

        back.setOnClickListener { pollJob?.cancel(); currentChat = null; showTab(1) }
        call.setOnClickListener { toast("Звонки скоро") }
        more.setOnClickListener { toast("Действия чата скоро") }
        attach.setOnClickListener { toast("Вложения скоро") }
        mic.setOnClickListener { toast("Голосовые сообщения скоро") }
        send.setOnClickListener {
            val body = input.text.toString().trim()
            if (body.isBlank()) return@setOnClickListener
            send.isEnabled = false
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { SupabaseClient.send(conversationId, body) }
                    input.text.clear()
                    refreshMessages(conversationId, true)
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
                showChatError(e.message ?: "Не удалось загрузить сообщения")
            }
            pollJob = launch {
                while (isActive && currentChat == conversationId) {
                    delay(1500)
                    try { refreshMessages(conversationId, false) } catch (_: Exception) { }
                }
            }
        }
    }

    private suspend fun refreshMessages(conversationId: String, force: Boolean) {
        val data = withContext(Dispatchers.IO) { SupabaseClient.messages(conversationId) }
        if (!force && data.length() == lastMessageCount) return
        lastMessageCount = data.length()
        withContext(Dispatchers.Main) {
            if (!::messageList.isInitialized || currentChat != conversationId) return@withContext
            messageList.removeAllViews()
            if (data.length() == 0) {
                messageList.addView(label("Начни разговор 👋", 16f, muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(30), 0, dp(30))
                })
            }
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val mine = item.optString("sender_id") == SupabaseClient.userId
                val row = LinearLayout(this@NexoActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = if (mine) Gravity.END else Gravity.START
                }
                val bubbleColor = if (mine) softBlue else surface
                val bubble = label(item.optString("body"), 16f, ink).apply {
                    setPadding(dp(15), dp(10), dp(8), dp(8))
                    background = rounded(bubbleColor, 18)
                }
                val bubbleColumn = LinearLayout(this@NexoActivity).apply {
                    orientation = LinearLayout.VERTICAL
                }
                bubbleColumn.addView(bubble)
                if (mine) {
                    val status = LinearLayout(this@NexoActivity).apply {
                        gravity = Gravity.END
                        setPadding(0, 0, dp(7), dp(4))
                    }
                    status.addView(icon(R.drawable.ic_done_all, 0.75f), lp(dp(22), dp(16)))
                    bubbleColumn.addView(status)
                }
                row.addView(
                    bubbleColumn,
                    lp(0, -2).apply {
                        weight = 1f
                        leftMargin = if (mine) dp(58) else 0
                        rightMargin = if (mine) 0 else dp(58)
                    }
                )
                messageList.addView(row, lp().apply { topMargin = dp(3); bottomMargin = dp(3) })
            }
            messageScroll.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
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
}
