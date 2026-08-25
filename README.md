# Private Messenger

Expo/React Native Android messenger scaffold with Supabase and Cloudflare.

## Local
1. `cp .env.example .env`
2. Fill Supabase URL + publishable key.
3. `npm install`
4. Apply `supabase/migrations/0001_messenger.sql` in Supabase SQL Editor.
5. `npx expo start`

## APK
Install EAS CLI and run:
`npx eas login`
`npx eas build:configure`
`npx eas build --platform android --profile production`

## Cloudflare
`cd worker && npm install`
`npx wrangler login`
Set production secret:
`npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY --env production`
Then:
`npm run deploy:production`

## Security
The included crypto module is a development scaffold only. It does NOT implement Signal Protocol/Double Ratchet or multi-device key agreement. Do not market this build as production-grade E2EE until that layer is replaced with an audited cryptographic implementation.
Never put Supabase service-role/secret keys in the mobile app.
