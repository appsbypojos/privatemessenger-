-- Nexo chat features: attachments, voice messages and call signaling.
create table if not exists public.message_attachments (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  message_id uuid references public.messages(id) on delete cascade,
  uploader_id uuid not null references auth.users(id) on delete cascade,
  storage_path text not null,
  file_name text not null,
  mime_type text not null,
  file_size bigint not null default 0,
  created_at timestamptz not null default now()
);

create index if not exists message_attachments_conversation_idx
  on public.message_attachments(conversation_id, created_at desc);

alter table public.message_attachments enable row level security;

drop policy if exists "members can read attachments" on public.message_attachments;
create policy "members can read attachments" on public.message_attachments
for select using (
  exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id = message_attachments.conversation_id
      and cm.user_id = auth.uid()
  )
);

drop policy if exists "members can create attachments" on public.message_attachments;
create policy "members can create attachments" on public.message_attachments
for insert with check (
  uploader_id = auth.uid()
  and exists (
    select 1 from public.conversation_members cm
    where cm.conversation_id = message_attachments.conversation_id
      and cm.user_id = auth.uid()
  )
);

create table if not exists public.call_sessions (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  caller_id uuid not null references auth.users(id) on delete cascade,
  callee_id uuid not null references auth.users(id) on delete cascade,
  kind text not null default 'audio' check (kind in ('audio','video')),
  status text not null default 'ringing' check (status in ('ringing','accepted','declined','ended','missed')),
  created_at timestamptz not null default now(),
  ended_at timestamptz
);

create index if not exists call_sessions_conversation_idx
  on public.call_sessions(conversation_id, created_at desc);

alter table public.call_sessions enable row level security;

drop policy if exists "participants can read calls" on public.call_sessions;
create policy "participants can read calls" on public.call_sessions
for select using (caller_id = auth.uid() or callee_id = auth.uid());

drop policy if exists "participants can create calls" on public.call_sessions;
create policy "participants can create calls" on public.call_sessions
for insert with check (caller_id = auth.uid());

drop policy if exists "participants can update calls" on public.call_sessions;
create policy "participants can update calls" on public.call_sessions
for update using (caller_id = auth.uid() or callee_id = auth.uid())
with check (caller_id = auth.uid() or callee_id = auth.uid());

insert into storage.buckets (id, name, public)
values ('chat-attachments', 'chat-attachments', true)
on conflict (id) do update set public = true;

drop policy if exists "authenticated can upload chat attachments" on storage.objects;
create policy "authenticated can upload chat attachments"
on storage.objects for insert to authenticated
with check (bucket_id = 'chat-attachments');

drop policy if exists "public can read chat attachments" on storage.objects;
create policy "public can read chat attachments"
on storage.objects for select using (bucket_id = 'chat-attachments');
