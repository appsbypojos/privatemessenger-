create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  device_name text,
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);
create index if not exists devices_user_idx on public.devices(user_id);

create table if not exists public.device_keys (
  device_id uuid primary key references public.devices(id) on delete cascade,
  identity_public_key text not null,
  signed_prekey text,
  signed_prekey_signature text,
  updated_at timestamptz not null default now()
);

create table if not exists public.conversation_keys (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete cascade,
  encrypted_key text not null,
  key_version bigint not null default 1,
  created_at timestamptz not null default now(),
  primary key (conversation_id, device_id, key_version)
);

create table if not exists public.message_receipts (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  delivered_at timestamptz,
  read_at timestamptz,
  primary key (message_id, user_id)
);
create index if not exists message_receipts_user_idx on public.message_receipts(user_id);

create table if not exists public.blocks (
  blocker_id uuid not null references auth.users(id) on delete cascade,
  blocked_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  check (blocker_id <> blocked_id)
);
create index if not exists blocks_blocked_idx on public.blocks(blocked_id);

alter table public.devices enable row level security;
alter table public.device_keys enable row level security;
alter table public.conversation_keys enable row level security;
alter table public.message_receipts enable row level security;
alter table public.blocks enable row level security;

create policy devices_own on public.devices for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy device_keys_own on public.device_keys for all to authenticated
  using (exists (select 1 from public.devices d where d.id = device_id and d.user_id = (select auth.uid())))
  with check (exists (select 1 from public.devices d where d.id = device_id and d.user_id = (select auth.uid())));

create policy conversation_keys_member on public.conversation_keys for all to authenticated
  using (
    exists (select 1 from public.conversation_members cm where cm.conversation_id = conversation_id and cm.user_id = (select auth.uid()))
    and exists (select 1 from public.devices d where d.id = device_id and d.user_id = (select auth.uid()))
  )
  with check (
    exists (select 1 from public.conversation_members cm where cm.conversation_id = conversation_id and cm.user_id = (select auth.uid()))
    and exists (select 1 from public.devices d where d.id = device_id and d.user_id = (select auth.uid()))
  );

create policy receipts_member on public.message_receipts for all to authenticated
  using (exists (
    select 1 from public.messages m
    join public.conversation_members cm on cm.conversation_id = m.conversation_id
    where m.id = message_id and cm.user_id = (select auth.uid())
  ))
  with check (
    user_id = (select auth.uid()) and exists (
      select 1 from public.messages m
      join public.conversation_members cm on cm.conversation_id = m.conversation_id
      where m.id = message_id and cm.user_id = (select auth.uid())
    )
  );

create policy blocks_own on public.blocks for all to authenticated
  using (blocker_id = (select auth.uid()))
  with check (blocker_id = (select auth.uid()));
