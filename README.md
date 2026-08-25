# Private Messenger — Native Android

Native Android/Kotlin messenger. **Expo and EAS are not used.**

## Stack
- Android + Kotlin + Gradle
- Supabase Auth + PostgreSQL + RLS
- Cloudflare Worker for backend/API extensions
- OkHttp for Supabase REST

## Local Android build
Create `local.properties` in the repository root:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_KEY=YOUR_PUBLISHABLE_KEY
```

Then build:

```bash
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Supabase
Apply `supabase/migrations/0001_messenger.sql` to the project. The mobile app uses only the publishable key; never put a service-role key in the APK.

## GitHub Actions
`.github/workflows/android.yml` builds a debug APK and uploads it as an Actions artifact. It does not require Expo/EAS.

For the workflow to connect to Supabase during compilation, add repository secrets `SUPABASE_URL` and `SUPABASE_KEY` and extend the Gradle step with those environment variables if desired. The current workflow can also build the UI without backend configuration.

## Cloudflare
The Worker in `worker/` is optional for the current REST client and is intended for rate limiting, server-side API extensions, and future push/event services.

## Security
Transport to Supabase is HTTPS and database access is protected by RLS. This version is **not end-to-end encrypted**: message bodies are currently stored as ciphertext fields but the native client sends the message text. Do not describe this build as E2EE. A production E2EE layer should use an audited protocol such as Signal/Double Ratchet with secure device key storage before claiming E2EE.
