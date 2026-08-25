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
    private var tab = 1
    private var currentChat: String? = null
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView

    private val bg = Color.rgb(244, 248, 252)
    private val surface = Color.WHITE
    private val ink = Color.rgb(35, 43, 55)
    private val muted = Color.rgb(115, 128, 145)
    private val blue = Color.rgb(36, 107, 255)
    private val purple = Color.rgb(90, 53, 232)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (SupabaseClient.accessToken == null) auth() else home(tab)
    }

    override fun onDestroy() {
        searchJob?.cancel(); pollJob?.cancel(); scope.cancel(); super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(w: Int = -1, h: Int = -2) = LinearLayout.LayoutParams(w, h)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun round(c: Int, r: Int = 18) = GradientDrawable().apply { setColor(c); cornerRadius = dp(r).toFloat() }
    private fun grad() = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(blue, purple)).apply { cornerRadius = dp(22).toFloat() }
    private fun text(s: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply { this.text=s; textSize=size; setTextColor(color); if(bold) typeface=Typeface.DEFAULT_BOLD }
    private fun root() = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg) }

    private fun insets(v: View) {
        val l=v.paddingLeft; val t=v.paddingTop; val r=v.paddingRight; val b=v.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, ins ->
            val bars=ins.getInsets(WindowInsetsCompat.Type.systemBars()); val ime=ins.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(l, t+bars.top, r, b+maxOf(bars.bottom, ime.bottom)); ins
        }
        ViewCompat.requestApplyInsets(v)
    }

    private fun icon(id: Int, size: Int = 28, alpha: Float = 1f) = ImageView(this).apply {
        setImageResource(id); this.alpha=alpha; scaleType=ImageView.ScaleType.CENTER_INSIDE; contentDescription=null
    }

    private fun iconButton(id: Int, size: Int = 46, bgDrawable: android.graphics.drawable.Drawable? = null) = ImageButton(this).apply {
        setImageResource(id); background=bgDrawable; scaleType=ImageView.ScaleType.CENTER; contentDescription="Nexo"; setPadding(dp(9),dp(9),dp(9),dp(9))
    }

    private fun auth() {
        pollJob?.cancel()
        val r=root().apply { gravity=Gravity.CENTER_HORIZONTAL; setPadding(dp(28),dp(24),dp(28),dp(20)) }
        r.addView(ImageView(this).apply { setImageResource(R.drawable.ic_nexo); scaleType=ImageView.ScaleType.CENTER_INSIDE }, lp(dp(108),dp(108)).apply{bottomMargin=dp(10)})
        r.addView(text("nexo",34f,ink,true).apply{gravity=Gravity.CENTER}, lp().apply{bottomMargin=dp(5)})
        r.addView(text("Общайся. Ищи. Будь рядом.",15f,muted).apply{gravity=Gravity.CENTER}, lp().apply{bottomMargin=dp(24)})
        val nick=EditText(this).apply{hint="Уникальный ник";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT;background=round(surface);setPadding(dp(16),0,dp(16),0)}
        val pass=EditText(this).apply{hint="Пароль";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;background=round(surface);setPadding(dp(16),0,dp(16),0)}
        r.addView(nick,lp().apply{height=dp(54);bottomMargin=dp(9)}); r.addView(pass,lp().apply{height=dp(54);bottomMargin=dp(11)})
        val login=Button(this).apply{text="Войти";isAllCaps=false;background=grad();setTextColor(Color.WHITE)}
        val reg=Button(this).apply{text="Создать аккаунт";isAllCaps=false;background=round(surface);setTextColor(blue)}
        r.addView(login,lp().apply{height=dp(52);bottomMargin=dp(8)});r.addView(reg,lp().apply{height=dp(52)})
        r.addView(text("3–24 символа • a-z, 0-9, _",12f,muted).apply{gravity=Gravity.CENTER},lp().apply{topMargin=dp(13)})
        setContentView(r); insets(r)
        login.setOnClickListener{authDo(nick.text.toString(),pass.text.toString(),false)}
        reg.setOnClickListener{authDo(nick.text.toString(),pass.text.toString(),true)}
    }

    private fun authDo(username:String,password:String,register:Boolean){
        val n=username.trim().lowercase()
        if(!Regex("[a-z0-9_]{3,24}").matches(n)){toast("Ник: 3–24 символа, только a-z, 0-9 и _");return}
        if(password.length<6){toast("Пароль минимум 6 символов");return}
        scope.launch{try{withContext(Dispatchers.IO){if(register)SupabaseClient.register(n,password)else SupabaseClient.signIn(n,password)};home(1)}catch(e:Exception){toast(e.message?:"Ошибка авторизации")}}
    }

    private fun home(which:Int){tab=which;pollJob?.cancel();currentChat=null;when(which){0->search();1->chats();2->settings();else->profile()}}

    private fun shell(title:String, body:View, subtitle:String?=null){
        val r=root(); val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(18),dp(8),dp(12),dp(5))}
        val titles=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};titles.addView(text(title,28f,ink,true));if(subtitle!=null)titles.addView(text(subtitle,13f,muted))
        head.addView(titles,lp(0,dp(58)).apply{weight=1f});r.addView(head);r.addView(body,lp(-1,0).apply{weight=1f});r.addView(nav());setContentView(r);insets(r)
    }

    private fun nav():View{
        val bar=LinearLayout(this).apply{gravity=Gravity.CENTER;setBackgroundColor(surface);setPadding(dp(6),dp(4),dp(6),dp(4))}
        val ids=intArrayOf(R.drawable.ic_search,R.drawable.ic_chat,R.drawable.ic_settings,R.drawable.ic_person);val names=arrayOf("Поиск","Чаты","Настройки","Профиль")
        for(i in ids.indices){val cell=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;isClickable=true;setPadding(0,dp(3),0,dp(2))};val iv=icon(ids[i],25,if(i==tab)1f else .45f);cell.addView(iv,lp(dp(34),dp(28)));cell.addView(text(names[i],10f,if(i==tab)blue:muted,i==tab).apply{gravity=Gravity.CENTER});bar.addView(cell,lp(0,dp(56)).apply{weight=1f});cell.setOnClickListener{home(i)}}
        return bar
    }

    private fun quickCard(drawable:Int,title:String,action:()->Unit):View{
        val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;background=round(Color.rgb(207,234,251),20);isClickable=true}
        c.addView(icon(drawable,32),lp(-1,dp(58)));c.addView(text(title,12f,ink,true).apply{gravity=Gravity.CENTER});c.setOnClickListener{action()};return c
    }

    private fun chats(){
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(2),dp(14),dp(8))}
        val quick=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false};val cards=LinearLayout(this).apply{setPadding(0,dp(3),dp(6),dp(9))}
        cards.addView(quickCard(R.drawable.ic_search,"Найти людей"){home(0)},lp(dp(112),dp(116)).apply{rightMargin=dp(10)})
        cards.addView(quickCard(R.drawable.ic_contacts,"Новый чат"){home(0)},lp(dp(112),dp(116)).apply{rightMargin=dp(10)})
        cards.addView(quickCard(R.drawable.ic_add,"Пригласить"){toast("Приглашения скоро")},lp(dp(112),dp(116)));quick.addView(cards);body.addView(quick,lp().apply{height=dp(126)})
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(ScrollView(this).apply{addView(list)},lp().apply{weight=1f});shell("Nexo",body,"Твои последние разговоры")
        scope.launch{try{val data=withContext(Dispatchers.IO){SupabaseClient.chats()};list.removeAllViews();if(data.length()==0){list.addView(text("Пока нет чатов\nНайди пользователя и начни разговор",16f,muted).apply{gravity=Gravity.CENTER;setPadding(0,dp(30),0,dp(30))});return@launch};for(i in 0 until data.length()){val x=data.getJSONObject(i);val cid=x.optString("conversation_id");val name=x.optString("other_username").ifBlank{"Пользователь"};val last=x.optString("last_message").ifBlank{"Нет сообщений"};val row=chatRow(name,last);list.addView(row,lp().apply{bottomMargin=dp(7)});if(cid.isNotBlank())row.setOnClickListener{openChat(cid,name)}}}catch(e:Exception){list.removeAllViews();list.addView(text("Не удалось загрузить чаты\n${e.message?:"Ошибка"}",14f,muted).apply{setPadding(dp(12),dp(24),dp(12),dp(24))})}}
    }

    private fun chatRow(name:String,last:String):LinearLayout{val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=round(surface,18);setPadding(dp(12),dp(9),dp(12),dp(9))};val av=text(name.take(1).uppercase(),18f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=grad()};row.addView(av,lp(dp(50),dp(50)).apply{rightMargin=dp(12)});val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};info.addView(text("@$name",16f,ink,true));info.addView(text(last,13f,muted).apply{maxLines=1});row.addView(info,lp(0,dp(58)).apply{weight=1f});return row}

    private fun search(){
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(3),dp(14),dp(8))};val line=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=round(surface,18);setPadding(dp(10),0,dp(8),0)}
        val input=EditText(this).apply{hint="Поиск по нику";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT;background=null;setPadding(dp(8),0,dp(8),0)};line.addView(icon(R.drawable.ic_search,25),lp(dp(34),dp(48)));line.addView(input,lp(0,dp(54)).apply{weight=1f});body.addView(line,lp().apply{height=dp(54);bottomMargin=dp(10)})
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(ScrollView(this).apply{addView(list)},lp().apply{weight=1f});shell("Поиск",body,"Найди людей по уникальному нику")
        fun render(a:JSONArray){list.removeAllViews();if(a.length()==0){list.addView(text(if(input.text.isNullOrBlank())"Начни вводить ник"else"Пользователь не найден",16f,muted).apply{setPadding(dp(12),dp(24),dp(12),dp(24))});return};for(i in 0 until a.length()){val x=a.getJSONObject(i);val id=x.optString("id");val n=x.optString("username");val d=x.optString("display_name").ifBlank{n};val row=chatRow(n,d);list.addView(row,lp().apply{bottomMargin=dp(7)});if(id.isNotBlank())row.setOnClickListener{openUser(id,n)}}}
        input.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){searchJob?.cancel();searchJob=scope.launch{delay(220);try{render(withContext(Dispatchers.IO){SupabaseClient.searchProfiles(s?.toString().orEmpty())})}catch(e:Exception){toast(e.message?:"Ошибка поиска")}}};override fun afterTextChanged(e:android.text.Editable?){}})
    }

    private fun settings(){
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(8))};fun row(t:String,s:String):View{val r=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=round(surface,18);setPadding(dp(16),dp(8),dp(10),dp(8))};val b=LinearLayout(this@NexoActivity).apply{orientation=LinearLayout.VERTICAL};b.addView(text(t,16f,ink,true));b.addView(text(s,12f,muted));r.addView(b,lp(0,dp(62)).apply{weight=1f});r.addView(Switch(this@NexoActivity).apply{isChecked=true},lp(dp(58),dp(50)));return r};body.addView(row("Уведомления","Новые сообщения и события"),lp().apply{bottomMargin=dp(8)});body.addView(row("Звук сообщений","Звуковые уведомления"),lp().apply{bottomMargin=dp(8)});body.addView(row("Предпросмотр сообщений","Текст в уведомлениях"),lp().apply{bottomMargin=dp(8)});body.addView(text("Nexo\nВерсия 1.0\n\nБыстрый мессенджер на Supabase.",14f,muted).apply{setPadding(dp(16),dp(20),dp(16),0)},lp().apply{weight=1f});shell("Настройки",body,"Персонализируй Nexo")
    }

    private fun profile(){
        val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(8))};val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;background=grad();setPadding(dp(18),dp(20),dp(18),dp(20))};val av=text("N",30f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=round(Color.argb(55,255,255,255),36)};card.addView(av,lp(dp(72),dp(72)).apply{bottomMargin=dp(10)});val nick=text("@username",17f,Color.WHITE,true).apply{gravity=Gravity.CENTER};card.addView(nick);body.addView(card,lp().apply{bottomMargin=dp(12)})
        val name=EditText(this).apply{hint="Имя";setSingleLine(true);background=round(surface,18);setPadding(dp(16),0,dp(16),0)};val bio=EditText(this).apply{hint="О себе";maxLines=4;background=round(surface,18);setPadding(dp(16),dp(12),dp(16),dp(12))};val save=Button(this).apply{text="Сохранить профиль";isAllCaps=false;background=grad();setTextColor(Color.WHITE)};val logout=Button(this).apply{text="Выйти";isAllCaps=false;background=round(surface);setTextColor(Color.rgb(210,65,75))};body.addView(name,lp().apply{height=dp(54);bottomMargin=dp(8)});body.addView(bio,lp().apply{height=dp(105);bottomMargin=dp(10)});body.addView(save,lp().apply{height=dp(52);bottomMargin=dp(8)});body.addView(logout,lp().apply{height=dp(52)});body.addView(Space(this),lp().apply{weight=1f});shell("Профиль",body,"Твои данные Nexo")
        scope.launch{try{val p=withContext(Dispatchers.IO){SupabaseClient.myProfile()};val u=p.optString("username");nick.text="@${u.ifBlank{"username"}}";name.setText(p.optString("display_name"));bio.setText(p.optString("bio"));av.text=u.take(1).uppercase().ifBlank{"N"}}catch(e:Exception){toast(e.message?:"Ошибка профиля")}}
        save.setOnClickListener{scope.launch{try{withContext(Dispatchers.IO){SupabaseClient.updateProfile(name.text.toString(),bio.text.toString())};toast("Профиль сохранён")}catch(e:Exception){toast(e.message?:"Ошибка сохранения")}}};logout.setOnClickListener{scope.launch{withContext(Dispatchers.IO){SupabaseClient.signOut()};auth()}}
    }

    private fun openUser(id:String,name:String){scope.launch{try{val cid=withContext(Dispatchers.IO){SupabaseClient.directConversation(id)};if(cid.isBlank())error("Supabase не вернул ID чата");openChat(cid,name)}catch(e:Exception){toast(e.message?:"Не удалось открыть чат")}}}

    private fun openChat(cid:String,name:String){pollJob?.cancel();currentChat=cid
        val r=root();val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=surface;setPadding(dp(5),dp(2),dp(10),dp(2))};val back=iconButton(R.drawable.ic_back,50);head.addView(back,lp(dp(50),dp(56)));val av=text(name.take(1).uppercase().ifBlank{"N"},17f,Color.WHITE,true).apply{gravity=Gravity.CENTER;background=grad()};head.addView(av,lp(dp(44),dp(44)).apply{rightMargin=dp(10)});val titles=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};titles.addView(text("@$name",18f,ink,true));titles.addView(text("Nexo",12f,muted));head.addView(titles,lp(0,dp(56)).apply{weight=1f});val more=iconButton(R.drawable.ic_more,44);head.addView(more,lp(dp(44),dp(50)));r.addView(head)
        messages=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12))};scroll=ScrollView(this).apply{addView(messages);setBackgroundColor(bg);isFillViewport=true};r.addView(scroll,lp().apply{weight=1f})
        val composer=LinearLayout(this).apply{gravity=Gravity.BOTTOM;background=surface;setPadding(dp(7),dp(6),dp(7),dp(6))};val attach=iconButton(R.drawable.ic_attach,44);val input=EditText(this).apply{hint="Сообщение";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;maxLines=4;background=round(bg,22);setPadding(dp(14),dp(5),dp(10),dp(5))};val mic=iconButton(R.drawable.ic_mic,44);val send=iconButton(R.drawable.ic_send,48,grad());composer.addView(attach,lp(dp(44),dp(50)));composer.addView(input,lp(0,-2).apply{weight=1f;leftMargin=dp(3);rightMargin=dp(3)});composer.addView(mic,lp(dp(44),dp(50)));composer.addView(send,lp(dp(48),dp(50)));r.addView(composer);setContentView(r);insets(r)
        back.setOnClickListener{pollJob?.cancel();currentChat=null;home(1)};more.setOnClickListener{toast("Действия чата скоро")};attach.setOnClickListener{toast("Вложения скоро")};mic.setOnClickListener{toast("Голосовые сообщения скоро")};send.setOnClickListener{val body=input.text.toString().trim();if(body.isBlank())return@setOnClickListener;send.isEnabled=false;scope.launch{try{withContext(Dispatchers.IO){SupabaseClient.send(cid,body)};input.text.clear();refresh(cid,true)}catch(e:Exception){toast(e.message?:"Ошибка отправки")}finally{send.isEnabled=true}}}
        scope.launch{try{refresh(cid,true)}catch(e:Exception){showChatError(e.message?:"Не удалось загрузить сообщения")};pollJob=launch{while(isActive&&currentChat==cid){delay(1500);try{refresh(cid,false)}catch(_:Exception){}}}}
    }

    private var lastCount=-1
    private suspend fun refresh(cid:String,force:Boolean){val a=withContext(Dispatchers.IO){SupabaseClient.messages(cid)};if(!force&&a.length()==lastCount)return;lastCount=a.length();withContext(Dispatchers.Main){if(!::messages.isInitialized||currentChat!=cid)return@withContext;messages.removeAllViews();if(a.length()==0)messages.addView(text("Начни разговор 👋",16f,muted).apply{gravity=Gravity.CENTER;setPadding(0,dp(30),0,dp(30))});for(i in 0 until a.length()){val x=a.getJSONObject(i);val mine=x.optString("sender_id")==SupabaseClient.userId;val wrap=LinearLayout(this@NexoActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=if(mine)Gravity.END else Gravity.START};val bubble=text(x.optString("body"),16f,ink).apply{setPadding(dp(15),dp(10),dp(15),dp(10));background=round(if(mine)Color.rgb(215,235,255)else surface,18)};wrap.addView(bubble,lp(0,-2).apply{weight=1f;leftMargin=if(mine)dp(58)else 0;rightMargin=if(mine)0 else dp(58)});messages.addView(wrap,lp().apply{topMargin=dp(3);bottomMargin=dp(3)})};scroll.post{scroll.fullScroll(View.FOCUS_DOWN)}}}

    private fun showChatError(msg:String){if(!::messages.isInitialized)return;if(messages.childCount==0)messages.addView(text("Не удалось загрузить сообщения\n$msg",14f,muted).apply{gravity=Gravity.CENTER;setPadding(dp(12),dp(30),dp(12),dp(30))})else toast(msg)}
}
