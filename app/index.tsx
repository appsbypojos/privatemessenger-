import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, SafeAreaView, StyleSheet, Text, TextInput, View } from 'react-native';
import { router } from 'expo-router';
import { supabase } from '@/src/lib/supabase';

export default function Index() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => {
      if (data.session) router.replace('/chat');
      setLoading(false);
    });
  }, []);

  async function signIn() {
    setBusy(true); setError('');
    const { error } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
    if (error) setError(error.message); else router.replace('/chat');
    setBusy(false);
  }

  async function signUp() {
    setBusy(true); setError('');
    const { error } = await supabase.auth.signUp({ email: email.trim(), password });
    if (error) setError(error.message);
    else setError('Проверьте email для подтверждения аккаунта.');
    setBusy(false);
  }

  if (loading) return <View style={styles.center}><ActivityIndicator /></View>;

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.title}>Private Messenger</Text>
        <Text style={styles.subtitle}>Приватный мессенджер</Text>
        <TextInput style={styles.input} placeholder="Email" autoCapitalize="none" keyboardType="email-address" value={email} onChangeText={setEmail} />
        <TextInput style={styles.input} placeholder="Пароль" secureTextEntry value={password} onChangeText={setPassword} />
        {!!error && <Text style={styles.error}>{error}</Text>}
        <Pressable style={styles.button} disabled={busy} onPress={signIn}><Text style={styles.buttonText}>{busy ? '...' : 'Войти'}</Text></Pressable>
        <Pressable style={styles.secondary} disabled={busy} onPress={signUp}><Text>Создать аккаунт</Text></Pressable>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container:{flex:1,justifyContent:'center',padding:20,backgroundColor:'#f5f6f8'},
  center:{flex:1,alignItems:'center',justifyContent:'center'},
  card:{backgroundColor:'#fff',padding:24,borderRadius:18,gap:12},
  title:{fontSize:28,fontWeight:'700'}, subtitle:{color:'#666',marginBottom:8},
  input:{borderWidth:1,borderColor:'#ddd',borderRadius:10,padding:13},
  button:{backgroundColor:'#111',padding:14,borderRadius:10,alignItems:'center'},
  buttonText:{color:'#fff',fontWeight:'700'}, secondary:{padding:14,alignItems:'center'},
  error:{color:'#b00020'}
});