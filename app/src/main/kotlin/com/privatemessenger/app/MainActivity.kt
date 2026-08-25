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
import org.json.JSONArray

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var searchJob: Job? = null
    private var chatPoll: Job? = null
    private var selectedTab = 1
    private var lastCount = -1
    private lateinit var messageList: LinearLayout
    private lateinit var messageScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (SupabaseClient.accessToken != null) showHome(selectedTab) else showAuth()
    }
    override fun onDestroy() { searchJob?.cancel(); chatPoll?.cancel(); scope.cancel(); super.onDestroy() }

    private fun root() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(248,249,252)) }
    private fun applyInsets(v: View) {
        val l=v.paddingLeft; val t=v.paddingTop; val r=v.paddingRight; val b=v.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, ins ->
            val bars=ins.getInsets(WindowInsetsCompat.Type.systemBars()); val ime=ins.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(l,t+bars.top,r,b+maxOf(bars.bottom,ime.bottom)); ins
        }; ViewCompat.requestApplyInsets(v)
    }
    private fun dp(x:Int)= (x*resources.displayMetrics.density).toInt()
    private fun lp(w:Int=-1,h:Int=-2)=LinearLayout.LayoutParams(w,h)
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()

    private fun showAuth() {
        chatPoll?.cancel()
        val r=root().apply { gravity=Gravity.CENTER_HORIZONTAL; setPadding(dp(28),dp(24),dp(28),dp(20)) }
        r.addView(ImageView(this).apply { setImageResource(R.drawable.ic_nexo); scaleType=ImageView.ScaleType.CENTER_INSIDE }, lp(dp(86),dp(86)).apply{bottomMargin=dp(12)})
        r.addView(TextView(this).apply { text="nexo"; textSize=34f; typeface=Typeface.DEFAULT_BOLD; gravity=Gravity.CENTER },lp().apply{bottomMargin=dp(8)})
        r.addView(TextView(this).apply { text="Мессенджер нового поколения"; textSize=16f; gravity=Gravity.CENTER; setTextColor(Color.DKGRAY) },lp().apply{bottomMargin=dp(28)})
        val nick=EditText(this).apply{hint="Ник";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT}
        val pass=EditText(this).apply{hint="Пароль";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD}
        r.addView(nick,lp().apply{height=dp(54);bottomMargin=dp(10)});r.addView(pass,lp().apply{height=dp(54);bottomMargin=dp(12)})
        val login=Button(this).apply{text="Войти";isAllCaps=false};val reg=Button(this).apply{text="Создать аккаунт";isAllCaps=false}
        r.addView(login,lp().apply{height=dp(52);bottomMargin=dp(8)});r.addView(reg,lp().apply{height=dp(52)})
        r.addView(TextView(this).apply{text="Ник: 3–24 символа • a-z, 0-9, _";textSize=12f;gravity=Gravity.CENTER;setTextColor(Color.GRAY)},lp().apply{topMargin=dp(14)})
        setContentView(r);applyInsets(r)
        login.setOnClickListener{auth(nick.text.toString(),pass.text.toString(),false)};reg.setOnClickListener{auth(nick.text.toString(),pass.text.toString(),true)}
    }
    private fun auth(u:String,p:String,reg:Boolean){
        val n=u.trim().lowercase();if(!Regex("[a-z0-9_]{3,24}").matches(n)){toast("Ник: 3–24 символа, только a-z, 0-9 и _");return};if(p.length<6){toast("Пароль минимум 6 символов");return}
        scope.launch{try{withContext(Dispatchers.IO){if(reg)SupabaseClient.register(n,p)else SupabaseClient.signIn(n,p)};showHome(1)}catch(e:Exception){toast(e.message?:"Ошибка авторизации")}}
    }

    private fun showHome(tab:Int){selectedTab=tab;chatPoll?.cancel();when(tab){0->showSearch();1->showChats();2->showSettings();else->showProfile()}}
    private fun shell(title:String,content:View){
        val r=root();val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(18),dp(6),dp(18),dp(4))}
        bar.addView(TextView(this).apply{text=title;textSize=27f;typeface=Typeface.DEFAULT_BOLD},lp(0,dp(54)).apply{weight=1f});r.addView(bar)
        r.addView(content,lp(-1,0).apply{weight=1f});r.addView(bottomNav());setContentView(r);applyInsets(r)
    }
    private fun bottomNav():View{
        val nav=LinearLayout(this).apply{gravity=Gravity.CENTER;setBackgroundColor(Color.WHITE);setPadding(0,dp(4),0,dp(4))}
        val items=listOf("⌕\nПоиск","▣\nЧаты","⚙\nНастройки","●\nПрофиль")
        items.forEachIndexed{i,label->val b=TextView(this).apply{text=label;textSize=12f;gravity=Gravity.CENTER;setTextColor(if(i==selectedTab) Color.rgb(65,92,220) else Color.DKGRAY);setTypeface(null,if(i==selectedTab)Typeface.BOLD else Typeface.NORMAL);setPadding(0,dp(4),0,dp(4));isClickable=true};nav.addView(b,lp(0,dp(60)).apply{weight=1f});b.setOnClickListener{showHome(i)}}
        return nav
    }

    private fun showSearch(){
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(4),dp(16),dp(8))}
        val search=EditText(this).apply{hint="Поиск пользователей по нику";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT;setPadding(dp(16),0,dp(16),0)};content.addView(search,lp().apply{height=dp(54);bottomMargin=dp(8)})
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};content.addView(ScrollView(this).apply{addView(list)},lp().apply{weight=1f});shell("Поиск",content)
        fun render(a:JSONArray){list.removeAllViews();if(a.length()==0){list.addView(TextView(this).apply{text=if(search.text.isNullOrBlank())"Введите ник для поиска"else"Пользователь не найден";textSize=16f;setTextColor(Color.GRAY);setPadding(dp(8),dp(24),dp(8),dp(24))});return};for(i in 0 until a.length()){val o=a.getJSONObject(i);val id=o.getString("id");val n=o.optString("username");val d=o.optString("display_name").ifBlank{n};val row=TextView(this).apply{text="$d\n@$n";textSize=16f;setTextColor(Color.DKGRAY);setPadding(dp(16),dp(12),dp(16),dp(12));setBackgroundColor(Color.WHITE)};list.addView(row,lp().apply{bottomMargin=dp(6)});row.setOnClickListener{openChat(id,n)}}}
        search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){searchJob?.cancel();searchJob=scope.launch{delay(220);try{render(withContext(Dispatchers.IO){SupabaseClient.searchProfiles(s?.toString().orEmpty())})}catch(e:Exception){toast(e.message?:"Ошибка поиска")}}};override fun afterTextChanged(e:Editable?) {}})
        scope.launch{try{render(withContext(Dispatchers.IO){SupabaseClient.searchProfiles("")})}catch(e:Exception){toast(e.message?:"Ошибка")}}
    }

    private fun showChats(){
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(4),dp(12),dp(8))};val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};content.addView(ScrollView(this).apply{addView(list)},lp().apply{weight=1f});shell("Чаты",content)
        scope.launch{try{val a=withContext(Dispatchers.IO){SupabaseClient.chats()};list.removeAllViews();if(a.length()==0){list.addView(TextView(this@MainActivity).apply{text="Здесь появятся ваши чаты";textSize=16f;gravity=Gravity.CENTER;setTextColor(Color.GRAY);setPadding(0,dp(40),0,dp(40))});return@launch};for(i in 0 until a.length()){val o=a.getJSONObject(i);val cid=o.getString("conversation_id");val n=o.optString("other_username");val last=o.optString("last_message").ifBlank{"Нет сообщений"};val row=LinearLayout(this@MainActivity).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(10));setBackgroundColor(Color.WHITE)};row.addView(TextView(this@MainActivity).apply{text="@${n}";textSize=17f;typeface=Typeface.DEFAULT_BOLD},lp(0,dp(58)).apply{weight=1f});row.addView(TextView(this@MainActivity).apply{text=last;textSize=13f;setTextColor(Color.GRAY);maxLines=1},lp(dp(150),dp(58)));list.addView(row,lp().apply{bottomMargin=dp(6)});row.setOnClickListener{openExistingChat(cid,n)}}}catch(e:Exception){toast(e.message?:"Не удалось загрузить чаты")}}
    }

    private fun showSettings(){
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(8))}
        content.addView(Switch(this).apply{text="Уведомления";textSize=17f;isChecked=true;setPadding(dp(12),dp(8),dp(12),dp(8))},lp().apply{height=dp(60)})
        content.addView(Switch(this).apply{text="Звук сообщений";textSize=17f;isChecked=true;setPadding(dp(12),dp(8),dp(12),dp(8))},lp().apply{height=dp(60)})
        content.addView(TextView(this).apply{text="Nexo\nВерсия 1.0\n\nПростой и быстрый мессенджер на Supabase.";textSize=16f;setTextColor(Color.GRAY);setPadding(dp(12),dp(24),dp(12),0)},lp().apply{weight=1f});shell("Настройки",content)
    }

    private fun showProfile(){
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(8),dp(20),dp(8))};val nick=TextView(this).apply{textSize=18f;setTextColor(Color.GRAY)};val name=EditText(this).apply{hint="Имя";setSingleLine(true)};val bio=EditText(this).apply{hint="О себе";setSingleLine(false);maxLines=4};val save=Button(this).apply{text="Сохранить";isAllCaps=false};val logout=Button(this).apply{text="Выйти";isAllCaps=false};content.addView(nick,lp().apply{bottomMargin=dp(12)});content.addView(name,lp().apply{height=dp(54);bottomMargin=dp(10)});content.addView(bio,lp().apply{height=dp(100);bottomMargin=dp(12)});content.addView(save,lp().apply{height=dp(52);bottomMargin=dp(8)});content.addView(logout,lp().apply{height=dp(52)});content.addView(Space(this),lp().apply{weight=1f});shell("Профиль",content)
        scope.launch{try{val p=withContext(Dispatchers.IO){SupabaseClient.myProfile()};nick.text="@${p.optString("username")}";name.setText(p.optString("display_name"));bio.setText(p.optString("bio"))}catch(e:Exception){toast(e.message?:"Ошибка профиля")}}
        save.setOnClickListener{scope.launch{try{withContext(Dispatchers.IO){SupabaseClient.updateProfile(name.text.toString(),bio.text.toString())};toast("Профиль сохранён")}catch(e:Exception){toast(e.message?:"Ошибка сохранения")}}}
        logout.setOnClickListener{scope.launch{withContext(Dispatchers.IO){SupabaseClient.signOut()};showAuth()}}
    }

    private fun openExistingChat(cid:String,name:String){openChatInternal(cid,name)}
    private fun openChat(otherId:String,name:String){scope.launch{try{val cid=withContext(Dispatchers.IO){SupabaseClient.directConversation(otherId)};openChatInternal(cid,name)}catch(e:Exception){toast(e.message?:"Не удалось открыть чат")}}}
    private fun openChatInternal(cid:String,name:String){
        chatPoll?.cancel();lastCount=-1
        val r=root();val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setBackgroundColor(Color.WHITE)};val back=Button(this).apply{text="‹";textSize=28f;isAllCaps=false};header.addView(back,lp(dp(56),dp(56)));header.addView(TextView(this).apply{text="@$name";textSize=20f;typeface=Typeface.DEFAULT_BOLD;gravity=Gravity.CENTER_VERTICAL},lp(0,dp(56)).apply{weight=1f});r.addView(header)
        messageList=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12))};messageScroll=ScrollView(this).apply{addView(messageList)};r.addView(messageScroll,lp().apply{weight=1f})
        val row=LinearLayout(this).apply{gravity=Gravity.BOTTOM;setPadding(dp(8),dp(6),dp(8),dp(6));setBackgroundColor(Color.WHITE)};val input=EditText(this).apply{hint="Сообщение";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;maxLines=4};val send=Button(this).apply{text="➤";isAllCaps=false};row.addView(input,lp(0,-2).apply{weight=1f});row.addView(send,lp(dp(58),dp(54)));r.addView(row);setContentView(r);applyInsets(r)
        back.setOnClickListener{chatPoll?.cancel();showHome(1)};send.setOnClickListener{val text=input.text.toString().trim();if(text.isBlank())return@setOnClickListener;send.isEnabled=false;scope.launch{try{withContext(Dispatchers.IO){SupabaseClient.send(cid,text)};input.text.clear();refresh(cid)}catch(e:Exception){toast(e.message?:"Ошибка отправки")}finally{send.isEnabled=true}}}
        scope.launch{refresh(cid);chatPoll=launch{while(isActive){delay(1200);try{refresh(cid)}catch(_:Exception){}}}}
    }
    private suspend fun refresh(cid:String){val a=withContext(Dispatchers.IO){SupabaseClient.messages(cid)};if(a.length()==lastCount)return;lastCount=a.length();messageList.removeAllViews();for(i in 0 until a.length()){val o=a.getJSONObject(i);val mine=o.optString("sender_id")==SupabaseClient.userId;val bubble=TextView(this).apply{text=o.optString("body");textSize=16f;setTextColor(Color.BLACK);setPadding(dp(15),dp(10),dp(15),dp(10));setBackgroundColor(if(mine) Color.rgb(220,235,255) else Color.WHITE);gravity=if(mine)Gravity.END else Gravity.START};messageList.addView(bubble,lp().apply{topMargin=dp(3);bottomMargin=dp(3);leftMargin=if(mine)dp(55)else 0;rightMargin=if(mine)0 else dp(55)})};messageScroll.post{messageScroll.fullScroll(View.FOCUS_DOWN)}}
}
