import { useEffect, useState } from 'react';
import { FlatList, Pressable, SafeAreaView, StyleSheet, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { supabase } from '@/src/lib/supabase';
import { decryptText, encryptText } from '@/src/lib/crypto';

type Message = { id:string; conversation_id:string; sender_id:string; ciphertext:string; nonce:string; created_at:string };

export default function Chat() {
  const [userId, setUserId] = useState('');
  const [conversationId, setConversationId] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [plain, setPlain] = useState<Record<string,string>>({});

  useEffect(() => {
    let channel: ReturnType<typeof supabase.channel> | undefined;
    (async () => {
      const { data: { user } } = await supabase.auth.getUser();
      if (!user) { router.replace('/'); return; }
      setUserId(user.id);

      let { data: membership } = await supabase.from('conversation_members').select('conversation_id').eq('user_id', user.id).limit(1).maybeSingle();
      let cid = membership?.conversation_id;
      if (!cid) {
        const { data: conv, error } = await supabase.from('conversations').insert({}).select('id').single();
        if (error) throw error;
        cid = conv.id;
        await supabase.from('conversation_members').insert({ conversation_id: cid, user_id: user.id });
      }
      setConversationId(cid);
      const { data } = await supabase.from('messages').select('*').eq('conversation_id', cid).order('created_at', { ascending: true });
      const list = (data ?? []) as Message[];
      setMessages(list);
      const decoded: Record<string,string> = {};
      for (const m of list) decoded[m.id] = await decryptText(m.ciphertext, m.nonce);
      setPlain(decoded);

      channel = supabase.channel(`messages:${cid}`).on('postgres_changes',
        { event:'INSERT', schema:'public', table:'messages', filter:`conversation_id=eq.${cid}` },
        async payload => {
          const m = payload.new as Message;
          setMessages(prev => prev.some(x => x.id === m.id) ? prev : [...prev, m]);
          const p = await decryptText(m.ciphertext, m.nonce);
          setPlain(prev => ({...prev, [m.id]:p}));
        }).subscribe();
    })().catch(e => console.error(e));
    return () => { if (channel) supabase.removeChannel(channel); };
  }, []);

  async function send() {
    const value = text.trim();
    if (!value || !conversationId || !userId) return;
    setBusy(true);
    const { ciphertext, nonce } = await encryptText(value);
    const { error } = await supabase.from('messages').insert({ conversation_id:conversationId, sender_id:userId, ciphertext, nonce });
    if (!error) setText('');
    else console.error(error);
    setBusy(false);
  }

  async function logout() { await supabase.auth.signOut(); router.replace('/'); }

  return <SafeAreaView style={styles.container}>
    <View style={styles.header}><Text style={styles.title}>Чат</Text><Pressable onPress={logout}><Text>Выйти</Text></Pressable></View>
    <FlatList data={messages} keyExtractor={x=>x.id} contentContainerStyle={{padding:12}}
      renderItem={({item}) => <View style={[styles.message, item.sender_id===userId && styles.mine]}>
        <Text>{plain[item.id] ?? '…'}</Text><Text style={styles.time}>{new Date(item.created_at).toLocaleTimeString()}</Text>
      </View>} />
    <View style={styles.composer}><TextInput style={styles.input} value={text} onChangeText={setText} placeholder="Сообщение" multiline />
      <Pressable style={styles.send} disabled={busy} onPress={send}><Text style={{color:'#fff'}}>Отправить</Text></Pressable></View>
  </SafeAreaView>;
}

const styles=StyleSheet.create({
  container:{flex:1,backgroundColor:'#f5f6f8'}, header:{height:60,paddingHorizontal:16,flexDirection:'row',alignItems:'center',justifyContent:'space-between',backgroundColor:'#fff'},title:{fontSize:20,fontWeight:'700'},
  message:{alignSelf:'flex-start',backgroundColor:'#fff',padding:10,borderRadius:12,marginVertical:4,maxWidth:'80%'},mine:{alignSelf:'flex-end'},time:{fontSize:10,color:'#888',marginTop:3},
  composer:{flexDirection:'row',padding:10,gap:8,backgroundColor:'#fff'},input:{flex:1,borderWidth:1,borderColor:'#ddd',borderRadius:12,padding:10,maxHeight:100},send:{backgroundColor:'#111',paddingHorizontal:14,borderRadius:12,justifyContent:'center'}
});