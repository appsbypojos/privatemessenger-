alter table public.device_keys
  add column if not exists agreement_public_key text,
  add column if not exists protocol_version integer not null default 1;

create index if not exists device_keys_protocol_version_idx
  on public.device_keys(protocol_version);

comment on column public.device_keys.identity_public_key is
  'Public identity signing key. Private key remains in Android Keystore.';
comment on column public.device_keys.agreement_public_key is
  'Public ECDH agreement key. Private key remains in Android Keystore.';
