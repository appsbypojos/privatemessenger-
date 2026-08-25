package com.privatemessenger.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private val bg = Color.rgb(244,248,252)
    private val surface = Color.WHITE
    private val ink = Color.rgb(35,43,55)
    private val muted = Color.rgb(115,128,145)
    private val accent = Color.rgb(55,137,232)
    private val accent2 = Color.rgb(104,73,226)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (SupabaseClient.accessToken != null) showHome(selectedTab) else showAuth()
    }
    override fun onDestroy() { searchJob?.cancel(); chatPoll?.cancel(); scope.cancel(); super.onDestroy() }
    private fun dp(x:Int)= (x*resources.displayMetrics.density).toInt()
    private fun lp(w:Int=-1,h:Int=-2)=LinearLayout.LayoutParams(w,h)
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
    private fun rounded(color:Int,radius:Int=18)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun gradient()=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(accent,accent2)).apply{cornerRadius=dp(24).toFloat()}
    private fun tv(s:String,size:Float,color:Int=ink,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD}
    private fun applyInsets(v:View){val l=v.paddingLeft;val t=v.paddingTop;val r=v.paddingRight;val b=v.paddingBottom;ViewCompat.setOnApplyWindowInsetsListener(v){view,ins->val bars=ins.getInsets(WindowInsetsCompat.Type.systemBars());val ime=ins.getInsets(WindowInsetsCompat.Type.ime());view.setPadding(l,t+bars.top,r,b+maxOf(bars.bottom,ime.bottom));ins};ViewCompat.requestApplyInsets(v)}
    private fun root()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}

    private fun showAuth(){
        chatPoll?.cancel()
        val r=root().apply{gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(28),dp(24),dp(28),dp(20))}
        r.addView(ImageView(this).apply{setImageResource(R.drawable.ic_nexo);scaleType=ImageView.ScaleType.CENTER_INSIDE},lp(dp(92),dp(92)).apply{bottomMargin=dp(12)})
        r.addView(tv("nexo",34f,ink,true).apply{gravity=Gravity.CENTER},lp().apply{bottomMargin=dp(6)})
        r.addView(tv("Общайся. Ищи. Будь рядом.",15f,muted).apply{gravity=Gravity.CENTER},lp().apply{bottomMargin=dp(26)})
        val nick=EditText(this).apply{hint="Уникальный ник";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT}
        val pass=EditText(this).apply{hint="Пароль";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD}
        r.addView(nick,lp().apply{height=dp(54);bottomMargin=dp(10)});r.addView(pass,lp().apply{height=dp(54);bottomMargin=dp(12)})
        val login=Button(this).apply{text="Войти";isAllCaps=false;background=gradient();setTextColor(Color.WHITE)}
        val reg=Button(this).apply{text="Создать аккаунт";isAllCaps=false;background=rounded(surface);setTextColor(accent)}
        r.addView(login,lp().apply{height=dp(52);bottomMargin=dp(8)});r.addView(reg,lp().apply{height=dp(52)})
        r.addView(tv("3–24 символа • a-z, 0-9, _",12f,muted).apply{gravity=Gravity.CENTER},lp().apply{topMargin=dp(14)})
        setContentView(r);applyInsets(r)
        login.setOnClickListener{auth(nick.text.toString(),pass.text.toString(),false)};reg.setOnClickListener{auth(nick.text.toString(),pass.text.toString(),true)}
    }
    private fun auth(u:String,p:String,reg:Boolean){val n=u.trim().lowercase();if(!Regex("[a-z0-9_]{3,24}").matches(n)){toast("Ник: 3–24 символа, только a-z, 0-9 и _");return};if(p.length<6){toast("Пароль минимум 6 символов");return};scope.launch{try{withContext(Dispatchers.IO){if(reg)SupabaseClient.register(n,p)else SupabaseClient.signIn(n,p)};showHome(1)}catch(e:Exception){toast(e.message?:"Ошибка авторизации")}}}
    private fun showHome(tab:Int){selectedTab=tab;chatPoll?.cancel();when(tab){0->showSearch();1->showChats();2->showSettings();else->showProfile()}}

    private fun shell(title:String,content:View,subtitle:String?=null){
        val r=root();val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(18),dp(8),dp(18),dp(5))};val tb=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};tb.addView(tv(title,28f,ink,true));if(subtitle!=null)tb.addView(tv(subtitle,13f,muted));bar.addView(tb,lp(0,dp(58)).apply{weight=1f});r.addView(bar);r.addView(content,lp(-1,0).apply{weight=1f});r.addView(bottomNav());setContentView(r);applyInsets(r)
    }
    private fun bottomNav():View{val nav=LinearLayout(this).apply{gravity=Gravity.CENTER;setBackgroundColor(surface);setPadding(dp(8),dp(5),dp(8),dp(5))};val items=listOf("⌕\nПоиск","▣\nЧаты","⚙\nНастройки","●\nПрофиль");items.forEachIndexed{i,label->val b=TextView(this).apply{text=label;textSize=12f;gravity=Gravity.CENTER;setTextColor(if(i==selectedTab)accent else muted);setTypeface(null,if(i==selectedTab)Typeface.BOLD else Typeface.NORMAL);setPadding(0,dp(4),0,dp(4));isClickable=true};nav.addView(b,lp(0,dp(58)).apply{weight=1f});b.setOnClickListener{showHome(i)}};return nav}
    private fun actionCard(icon:String,title:String,color:Int,action:()->Unit):View{val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(10),dp(10),dp(10),dp(10));background=rounded(color,20);isClickable=true};c.addView(tv(icon,25f,ink,true).apply{gravity=Gravity.CENTER});c.addView(tv(title,12f,ink,true).apply{gravity=Gravity.CENTER;maxLines=2});c.setOnClickListener{action()};return c}

    private fun showChats(){
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(2),dp(14),dp(8))}
        val quick=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false};val cards=LinearLayout(this).apply{setPadding(0,dp(3),dp(6),dp(9))}
        cards.addView(actionCard("⌕","Найти людей",Color.rgb(207,234,251)){showHome(0)},lp(dp(112),dp(116)).apply{rightMargin=dp(10)})
        cards.addView(actionCard("✉","Пригласить",Color.rgb(214,231,246)){toast("Приглашения скоро")},lp(dp(112),dp(116)).apply{rightMargin=dp(10)})
        cards.addView(actionCard("👥","Новый чат",Color.rgb(198,226,242)){showHome(0)},lp(dp(112),dp(116)));quick.addView(cards);content.addView(quick,lp().apply{height=dp(126)})
        val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};content.addView(ScrollView(this).apply{addView(list)},lp().apply{weight=1f});shell("Nexo",content,"Твои последние разговоры")
        scope.launch{try{val a=withContext(Dispatchers.IO){SupabaseClient.chats()};list.removeAllViews();if(a.length()==0){list.addView(tv("Пока нет чатов\nНайди пользователя и начни разговор",16f,muted).apply{gravity=Gravity.CENTER;setPadding(0,dp(30),0,dp(30))});return@launch};for(i in 0 until a.length()){val o=a.getJSONObject(i);val cid=o.getString("conversation_id");val n=o.optString("other_username");val last=o.optString("last_message").ifBlank{"Нет сообщений"};val row=LinearLayout(this@MainActivity).apply{gravity=Gravity.CENTER_VERTICAL;background=rounded(surface,18);setPadding(dp(12),dp(9),dp(12),dp(9))};val av=TextView(this@MainActivity).apply{text=n.take(1).uppercase();textSize=18f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=gradient()};row.addView(av,lp(dp(50),dp(50)).apply{rightMargin=dp(12)});val info=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL};info.addView(tv("@$n",16f,ink,true));info.addView(tv(last,13f,muted).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END});row.addView(info,lp(0,dp(58)).apply{weight=1f});list.addView(row,lp().apply{bottomMargin=dp(7)});row.setOnClickListener{openExistingChat(cid,n)}}}catch(e:Exception){toast(e.message?:"Не удалось загрузить чаты")}}
    }

    private fun showSearch(){
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(3),dp(14),dp(8))};val search=EditText(this).apply{hint="Поиск по нику";setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT;setPadding(dp(16),0,dp(16),0);background=rounded(surface,18)};content.addView(search,lp().apply{height=dp(54);bottomMargin=dp(10)});val list=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};content.addView(ScrollView(this).apply{addView(list)},lp().apply{weight=1f});shell("Поиск",content,"Найди людей по уникальному нику")
        fun render(a:JSONArray){list.removeAllViews();if(a.length()==0){list.addView(tv(if(search.text.isNullOrBlank())"Начни вводить ник"else"Пользователь не найден",16f,muted).apply{setPadding(dp(12),dp(24),dp(12),dp(24))});return};for(i in 0 until a.length()){val o=a.getJSONObject(i);val id=o.getString("id");val n=o.optString("username");val d=o.optString("display_name").ifBlank{n};val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=rounded(surface,18);setPadding(dp(14),dp(10),dp(14),dp(10))};val avatar=TextView(this).apply{text=n.take(1).uppercase();textSize=18f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=gradient()};row.addView(avatar,lp(dp(46),dp(46)).apply{rightMargin=dp(12)});val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};info.addView(tv(d,16f,ink,true));info.addView(tv("@$n",13f,muted));row.addView(info,lp(0,-2).apply{weight=1f});list.addView(row,lp().apply{bottomMargin=dp(7)});row.setOnClickListener{openChat(id,n)}}}
        search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){searchJob?.cancel();searchJob=scope.launch{delay(220);try{render(withContext(Dispatchers.IO){SupabaseClient.searchProfiles(s?.toString().orEmpty())})}catch(e:Exception){toast(e.message?:"Ошибка поиска")}}};override fun afterTextChanged(e:Editable?) {}})
        scope.launch{try{render(withContext(Dispatchers.IO){SupabaseClient.searchProfiles("")})}catch(_:Exception){}}
    }

    private fun showSettings(){val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(8))};fun setting(t:String,s:String):View{val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;background=rounded(surface,18);setPadding(dp(16),dp(8),dp(10),dp(8))};val box=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL};box.addView(tv(t,16f,ink,true));box.addView(tv(s,12f,muted));row.addView(box,lp(0,dp(62)).apply{weight=1f});row.addView(Switch(this@MainActivity).apply{isChecked=true},lp(dp(58),dp(50)));return row};content.addView(setting("Уведомления","Новые сообщения и события"),lp().apply{bottomMargin=dp(8)});content.addView(setting("Звук сообщений","Звуковые уведомления"),lp().apply{bottomMargin=dp(8)});content.addView(setting("Предпросмотр сообщений","Показывать текст в уведомлениях"),lp().apply{bottomMargin=dp(8)});content.addView(tv("Nexo\nВерсия 1.0\n\nБыстрый мессенджер на Supabase.",14f,muted).apply{setPadding(dp(16),dp(20),dp(16),0)},lp().apply{weight=1f});shell("Настройки",content,"Персонализируй Nexo")}

    private fun showProfile(){
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(8))}
        val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;background=gradient();setPadding(dp(18),dp(20),dp(18),dp(20))};val avatar=TextView(this).apply{text="N";textSize=30f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=rounded(Color.argb(60,255,255,255),32)};card.addView(avatar,lp(dp(72),dp(72)).apply{bottomMargin=dp(10)});val nick=tv("@username",17f,Color.WHITE,true).apply{gravity=Gravity.CENTER};card.addView(nick);content.addView(card,lp().apply{bottomMargin=dp(12)})
        val name=EditText(this).apply{hint="Имя";setSingleLine(true);background=rounded(surface,18);setPadding(dp(16),0,dp(16),0)};val bio=EditText(this).apply{hint="О себе";setSingleLine(false);maxLines=4;background=rounded(surface,18);setPadding(dp(16),dp(12),dp(16),dp(12))};val save=Button(this).apply{text="Сохранить профиль";isAllCaps=false;background=gradient();setTextColor(Color.WHITE)};val logout=Button(this).apply{text="Выйти";isAllCaps=false;background=rounded(surface);setTextColor(Color.rgb(210,65,75))};content.addView(name,lp().apply{height=dp(54);bottomMargin=dp(8)});content.addView(bio,lp().apply{height=dp(105);bottomMargin=dp(10)});content.addView(save,lp().apply{height=dp(52);bottomMargin=dp(8)});content.addView(logout,lp().apply{height=dp(52)});content.addView(Space(this),lp().apply{weight=1f});shell("Профиль",content,"Твои данные Nexo")
        scope.launch{try{val p=withContext(Dispatchers.IO){SupabaseClient.myProfile()};nick.text="@${p.optString("username")}";name.setText(p.optString("display_name"));bio.setText(p.optString("bio"));avatar.text=p.optString("username").take(1).uppercase()}catch(e:Exception){toast(e.message?:"Ошибка профиля")}}
        save.setOnClickListener{scope.launch{try{withContext(Dispatchers.IO){SupabaseClient.updateProfile(name.text.toString(),bio.text.toString())};toast("Профиль сохранён")}catch(e:Exception){toast(e.message?:"Ошибка сохранения")}}};logout.setOnClickListener{scope.launch{withContext(Dispatchers.IO){SupabaseClient.signOut()};showAuth()}}
    }

    private fun openExistingChat(cid:String,name:String)=openChatInternal(cid,name)
    private fun openChat(otherId:String,name:String){scope.launch{try{val cid=withContext(Dispatchers.IO){SupabaseClient.directConversation(otherId)};openChatInternal(cid,name)}catch(e:Exception){toast(e.message?:"Не удалось открыть чат")}}}
    private fun openChatInternal(cid:String,name:String){
        chatPoll?.cancel();lastCount=-1;val r=root();val header=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setBackgroundColor(surface);setPadding(dp(5),dp(2),dp(12),dp(2))};val back=Button(this).apply{text="‹";textSize=30f;isAllCaps=false;setTextColor(accent);background=null};header.addView(back,lp(dp(52),dp(56)));val av=TextView(this).apply{text=name.take(1).uppercase();textSize=17f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=gradient()};header.addView(av,lp(dp(44),dp(44)).apply{rightMargin=dp(10)});val h=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};h.addView(tv("@$name",18f,ink,true));h.addView(tv("Nexo",12f,muted));header.addView(h,lp(0,dp(56)).apply{weight=1f});r.addView(header)
        messageList=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(12),dp(12),dp(12))};messageScroll=ScrollView(this).apply{addView(messageList);setBackgroundColor(bg)};r.addView(messageScroll,lp().apply{weight=1f})
        val row=LinearLayout(this).apply{gravity=Gravity.BOTTOM;setPadding(dp(8),dp(6),dp(8),dp(6));setBackgroundColor(surface)};val input=EditText(this).apply{hint="Сообщение";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;maxLines=4;background=rounded(bg,22);setPadding(dp(16),dp(5),dp(12),dp(5))};val send=Button(this).apply{text="➤";textSize=18f;isAllCaps=false;background=gradient();setTextColor(Color.WHITE)};row.addView(input,lp(0,-2).apply{weight=1f;rightMargin=dp(7)});row.addView(send,lp(dp(54),dp(54)));r.addView(row);setContentView(r);applyInsets(r)
        back.setOnClickListener{chatPoll?.cancel();showHome(1)};send.setOnClickListener{val body=input.text.toString().trim();if(body.isBlank())return@setOnClickListener;send.isEnabled=false;scope.launch{try{withContext(Dispatchers.IO){SupabaseClient.send(cid,body)};input.text.clear();refresh(cid)}catch(e:Exception){toast(e.message?:"Ошибка отправки")}finally{send.isEnabled=true}}};scope.launch{refresh(cid);chatPoll=launch{while(isActive){delay(1200);try{refresh(cid)}catch(_:Exception){}}}}
    }
    private suspend fun refresh(cid:String){val a=withContext(Dispatchers.IO){SupabaseClient.messages(cid)};if(a.length()==lastCount)return;lastCount=a.length();messageList.removeAllViews();for(i in 0 until a.length()){val o=a.getJSONObject(i);val mine=o.optString("sender_id")==SupabaseClient.userId;val wrap=LinearLayout(this).apply{gravity=if(mine)Gravity.END else Gravity.START};val bubble=TextView(this).apply{text=o.optString("body");textSize=16f;setTextColor(ink);setPadding(dp(15),dp(10),dp(15),dp(10));background=rounded(if(mine)Color.rgb(215,235,255)else surface,18)};wrap.addView(bubble,lp(0,-2).apply{weight=1f;leftMargin=if(mine)dp(58)else 0;rightMargin=if(mine)0 else dp(58)});messageList.addView(wrap,lp().apply{topMargin=dp(3);bottomMargin=dp(3)})};messageScroll.post{messageScroll.fullScroll(View.FOCUS_DOWN)}}
}
