import React, { useEffect, useState } from 'react';
import { Alert, FlatList, SafeAreaView, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { supabase } from './src/lib/supabase';
import { encryptForStorage, decryptFromStorage } from './src/lib/crypto';

type Message = { id: string; sender_id: string; ciphertext: string; nonce: string; created_at: string; text?: string };

export default function App() {
  const [session, setSession] = useState<any>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSession(data.session));
    const { data: listener } = supabase.auth.onAuthStateChange((_event, next) => setSession(next));
    return () => listener.subscription.unsubscribe();
  }, []);

  useEffect(() => {
    if (!session) return;
    (async () => {
      const { data: membership } = await supabase.from('conversation_members').select('conversation_id').eq('user_id', session.user.id).limit(1).maybeSingle();
      if (membership?.conversation_id) setConversationId(membership.conversation_id);
    })();
  }, [session]);

  useEffect(() => {
    if (!conversationId) return;
    let active = true;
    const load = async () => {
      const { data, error } = await supabase.from('messages').select('*').eq('conversation_id', conversationId).order('created_at', { ascending: true });
      if (error) return;
      const decoded = await Promise.all((data ?? []).map(async m => ({ ...m, text: await decryptFromStorage(m.ciphertext, m.nonce).catch(() => '🔒 Unable to decrypt') })));
      if (active) setMessages(decoded);
    };
    load();
    const channel = supabase.channel(`conversation:${conversationId}`).on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'messages', filter: `conversation_id=eq.${conversationId}` }, async payload => {
      const m: any = payload.new;
      const text = await decryptFromStorage(m.ciphertext, m.nonce).catch(() => '🔒 Unable to decrypt');
      if (active) setMessages(prev => [...prev, { ...m, text }]);
    }).subscribe();
    return () => { active = false; supabase.removeChannel(channel); };
  }, [conversationId]);

  async function auth() {
    setBusy(true);
    const result = session ? await supabase.auth.signOut() : await supabase.auth.signInWithPassword({ email: email.trim(), password });
    setBusy(false);
    if (result.error) Alert.alert('Ошибка', result.error.message);
  }

  async function register() {
    setBusy(true);
    const { error } = await supabase.auth.signUp({ email: email.trim(), password });
    setBusy(false);
    if (error) Alert.alert('Ошибка регистрации', error.message); else Alert.alert('Готово', 'Проверьте email, если подтверждение включено.');
  }

  async function send() {
    if (!draft.trim() || !session || !conversationId) return;
    const encrypted = await encryptForStorage(draft.trim());
    setDraft('');
    const { error } = await supabase.from('messages').insert({ conversation_id: conversationId, sender_id: session.user.id, ...encrypted });
    if (error) Alert.alert('Ошибка отправки', error.message);
  }

  if (!session) return <SafeAreaView style={styles.container}><View style={styles.card}><Text style={styles.title}>Private Messenger</Text><TextInput placeholder="Email" autoCapitalize="none" keyboardType="email-address" value={email} onChangeText={setEmail} style={styles.input}/><TextInput placeholder="Пароль" secureTextEntry value={password} onChangeText={setPassword} style={styles.input}/><TouchableOpacity disabled={busy} onPress={auth} style={styles.button}><Text style={styles.buttonText}>Войти</Text></TouchableOpacity><TouchableOpacity onPress={register}><Text style={styles.link}>Создать аккаунт</Text></TouchableOpacity></View></SafeAreaView>;

  return <SafeAreaView style={styles.container}><View style={styles.header}><Text style={styles.title}>Private Messenger</Text><TouchableOpacity onPress={auth}><Text>Выйти</Text></TouchableOpacity></View>{!conversationId ? <View style={styles.empty}><Text>Нет доступных чатов.</Text><Text style={styles.muted}>Создайте conversation и добавьте текущего пользователя через Supabase.</Text></View> : <><FlatList style={styles.list} data={messages} keyExtractor={m => m.id} renderItem={({ item }) => <View style={[styles.message, item.sender_id === session.user.id && styles.mine]}><Text>{item.text}</Text></View>}/><View style={styles.composer}><TextInput value={draft} onChangeText={setDraft} placeholder="Сообщение" style={styles.messageInput}/><TouchableOpacity onPress={send} style={styles.send}><Text style={styles.buttonText}>➤</Text></TouchableOpacity></View></>}</SafeAreaView>;
}

const styles = StyleSheet.create({ container:{flex:1,backgroundColor:'#f5f7fb'}, card:{margin:24,padding:24,backgroundColor:'white',borderRadius:20,marginTop:100}, title:{fontSize:24,fontWeight:'700'}, input:{backgroundColor:'#f0f2f5',padding:14,borderRadius:12,marginTop:12}, button:{backgroundColor:'#111827',padding:15,borderRadius:12,marginTop:14,alignItems:'center'}, buttonText:{color:'white',fontWeight:'700'}, link:{textAlign:'center',marginTop:18}, header:{padding:16,flexDirection:'row',justifyContent:'space-between',alignItems:'center',backgroundColor:'white'}, list:{padding:12}, message:{alignSelf:'flex-start',backgroundColor:'white',padding:12,borderRadius:14,marginVertical:4,maxWidth:'80%'}, mine:{alignSelf:'flex-end',backgroundColor:'#dbeafe'}, composer:{flexDirection:'row',padding:10,backgroundColor:'white'}, messageInput:{flex:1,backgroundColor:'#f0f2f5',padding:12,borderRadius:14}, send:{width:48,height:48,borderRadius:24,backgroundColor:'#111827',alignItems:'center',justifyContent:'center',marginLeft:8}, empty:{padding:24,alignItems:'center'}, muted:{color:'#6b7280',textAlign:'center',marginTop:8}}
