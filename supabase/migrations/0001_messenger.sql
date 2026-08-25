create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique,
  display_name text,
  avatar_path text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.conversation_members (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete restrict,
  ciphertext text not null,
  nonce text not null,
  created_at timestamptz not null default now(),
  edited_at timestamptz,
  deleted_at timestamptz
);

create index if not exists messages_conversation_created_idx on public.messages(conversation_id, created_at desc);
create index if not exists conversation_members_user_idx on public.conversation_members(user_id);

alter table public.profiles enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;

drop policy if exists profiles_select_authenticated on public.profiles;
create policy profiles_select_authenticated on public.profiles for select to authenticated using (true);
drop policy if exists profiles_insert_self on public.profiles;
create policy profiles_insert_self on public.profiles for insert to authenticated with check (id = auth.uid());
drop policy if exists profiles_update_self on public.profiles;
create policy profiles_update_self on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

drop policy if exists conversations_member_select on public.conversations;
create policy conversations_member_select on public.conversations for select to authenticated using (exists (select 1 from public.conversation_members cm where cm.conversation_id=id and cm.user_id=auth.uid()));
drop policy if exists conversations_member_insert on public.conversations;
create policy conversations_member_insert on public.conversations for insert to authenticated with check (true);

drop policy if exists members_select on public.conversation_members;
create policy members_select on public.conversation_members for select to authenticated using (user_id=auth.uid() or exists (select 1 from public.conversation_members cm where cm.conversation_id=conversation_id and cm.user_id=auth.uid()));
drop policy if exists members_insert_self on public.conversation_members;
create policy members_insert_self on public.conversation_members for insert to authenticated with check (user_id=auth.uid());

drop policy if exists messages_select_member on public.messages;
create policy messages_select_member on public.messages for select to authenticated using (exists (select 1 from public.conversation_members cm where cm.conversation_id=messages.conversation_id and cm.user_id=auth.uid()));
drop policy if exists messages_insert_member_sender on public.messages;
create policy messages_insert_member_sender on public.messages for insert to authenticated with check (sender_id=auth.uid() and exists (select 1 from public.conversation_members cm where cm.conversation_id=messages.conversation_id and cm.user_id=auth.uid()));

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles(id, username, display_name)
  values (
    new.id,
    split_part(coalesce(new.email,''),'@',1),
    coalesce(new.raw_user_meta_data->>'display_name', split_part(coalesce(new.email,''),'@',1))
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute procedure public.handle_new_user();

do $$ begin
  alter publication supabase_realtime add table public.messages;
exception when duplicate_object then null;
end $$;
