# Private Messenger

Expo/React Native private messenger starter with Supabase Auth, PostgreSQL/RLS, Realtime and device-local message encryption.

## Setup

```bash
npm install
npx expo start
```

Create `.env` (never commit it):

```env
EXPO_PUBLIC_SUPABASE_URL=https://YOUR_PROJECT.supabase.co
EXPO_PUBLIC_SUPABASE_KEY=YOUR_PUBLISHABLE_KEY
```

Run `supabase/migrations/0001_messenger.sql` in the Supabase SQL Editor, or apply it with the Supabase CLI.

## Android APK

```bash
npm install -g eas-cli
eas login
eas build:configure
eas build --platform android --profile production
```

Before release, configure a real E2EE key exchange between recipients. The included crypto layer encrypts the local message payload with a device key and is not, by itself, a Signal-compatible multi-device E2EE protocol.
