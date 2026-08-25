package com.privatemessenger.app

import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var messages: LinearLayout
    private var conversationId: String? = null
    private var lastRendered = -1

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); showLogin() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun showLogin() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,48,48,48) }
        val title = TextView(this).apply { text="Private Messenger"; textSize=28f }
        val email = EditText(this).apply { hint="Email"; inputType=33 }
        val password = EditText(this).apply { hint="Пароль"; inputType=129 }
        val login = Button(this).apply { text="Войти" }
        val register = Button(this).apply { text="Создать аккаунт" }
        root.addView(title); root.addView(email); root.addView(password); root.addView(login); root.addView(register)
        setContentView(root)
        login.setOnClickListener { auth(email.text.toString(), password.text.toString(), false) }
        register.setOnClickListener { auth(email.text.toString(), password.text.toString(), true) }
    }

    private fun auth(email:String, password:String, signup:Boolean) {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_KEY.isBlank()) { toast("Настройте SUPABASE_URL и SUPABASE_KEY"); return }
        if (email.isBlank() || password.length < 6) { toast("Введите email и пароль (минимум 6 символов)"); return }
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { if (signup) SupabaseClient.signUp(email,password) else SupabaseClient.signIn(email,password) }
                if (signup) toast("Аккаунт создан. Если включено подтверждение email, подтвердите адрес и войдите.") else openChat()
            } catch (e:Exception) { toast(e.message ?: "Ошибка авторизации") }
        }
    }

    private fun openChat() {
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)}
        val title=TextView(this).apply{text="Private Messenger";textSize=22f}
        messages=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(this).apply{addView(messages)}
        val row=LinearLayout(this)
        val input=EditText(this).apply{hint="Сообщение";inputType=1}
        val send=Button(this).apply{text="Отправить"}
        row.addView(input,LinearLayout.LayoutParams(0,-2,1f));row.addView(send)
        root.addView(title);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));root.addView(row);setContentView(root)
        send.setOnClickListener { val value=input.text.toString().trim(); if(value.isNotEmpty()) { scope.launch { try { val cid=conversationId ?: withContext(Dispatchers.IO){SupabaseClient.ensureConversation()}.also{conversationId=it}; withContext(Dispatchers.IO){SupabaseClient.send(cid,value)}; input.text.clear(); refresh(cid) } catch(e:Exception){toast(e.message ?: "Ошибка отправки")} } } }
        scope.launch { try { conversationId=withContext(Dispatchers.IO){SupabaseClient.ensureConversation()}; refresh(conversationId!!) } catch(e:Exception){toast(e.message ?: "Ошибка загрузки чата")} }
    }

    private suspend fun refresh(cid:String) { val arr=withContext(Dispatchers.IO){SupabaseClient.messages(cid)}; if(arr.length()==lastRendered)return; lastRendered=arr.length(); messages.removeAllViews(); for(i in 0 until arr.length()){ val o=arr.getJSONObject(i); messages.addView(TextView(this).apply{text=o.getString("ciphertext");textSize=17f;setPadding(8,12,8,12)}) } }
    private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
}
