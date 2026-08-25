import 'react-native-url-polyfill/auto';
import { createClient } from '@supabase/supabase-js';

const url = process.env.EXPO_PUBLIC_SUPABASE_URL;
const key = process.env.EXPO_PUBLIC_SUPABASE_KEY;

if (!url || !key) {
  console.warn('Set EXPO_PUBLIC_SUPABASE_URL and EXPO_PUBLIC_SUPABASE_KEY before using the app.');
}

export const supabase = createClient(url ?? 'https://placeholder.invalid', key ?? 'placeholder', {
  auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: false }
});
