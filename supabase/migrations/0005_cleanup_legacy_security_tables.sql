-- Keep the database aligned with the intentionally simple messenger architecture.
drop table if exists public.conversation_keys cascade;
drop table if exists public.device_keys cascade;
drop table if exists public.devices cascade;

alter table public.messages add column if not exists body text;
