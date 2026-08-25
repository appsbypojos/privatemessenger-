package com.privatemessenger.app

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var messages: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showLogin()
    }

    private fun showLogin() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 48, 48, 48) }
        val title = TextView(this).apply { text = "Private Messenger"; textSize = 28f }
        val email = EditText(this).apply { hint = "Email" }
        val password = EditText(this).apply { hint = "Пароль"; inputType = 0x81 }
        val login = Button(this).apply { text = "Войти" }
        val register = Button(this).apply { text = "Создать аккаунт" }
        root.addView(title); root.addView(email); root.addView(password); root.addView(login); root.addView(register)
        setContentView(root)
        login.setOnClickListener { showChat(email.text.toString()) }
        register.setOnClickListener { showChat(email.text.toString()) }
    }

    private fun showChat(email: String) {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val title = TextView(this).apply { text = "Private Messenger"; textSize = 22f }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(messages) }
        val row = LinearLayout(this)
        val input = EditText(this).apply { hint = "Сообщение" }
        val send = Button(this).apply { text = "Отправить" }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(send)
        root.addView(title); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(row)
        setContentView(root)
        send.setOnClickListener {
            val value = input.text.toString().trim()
            if (value.isNotEmpty()) { messages.addView(TextView(this).apply { text = value; textSize = 17f; setPadding(8, 12, 8, 12) }); input.text.clear() }
        }
    }
}
