create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique,
  display_name text,
  created_at timestamptz not null default now()
);
create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table if not exists public.conversation_members (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(conversation_id,user_id)
);
create table if not exists public.messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete restrict,
  ciphertext text not null,
  nonce text not null,
  created_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index if not exists messages_conversation_created_idx on public.messages(conversation_id,created_at);

alter table public.profiles enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.messages enable row level security;

create policy profiles_read on public.profiles for select to authenticated using (true);
create policy profiles_self_insert on public.profiles for insert to authenticated with check(id=auth.uid());
create policy profiles_self_update on public.profiles for update to authenticated using(id=auth.uid()) with check(id=auth.uid());
create policy conversation_member_read on public.conversations for select to authenticated using(exists(select 1 from public.conversation_members m where m.conversation_id=id and m.user_id=auth.uid()));
create policy member_read on public.conversation_members for select to authenticated using(user_id=auth.uid() or exists(select 1 from public.conversation_members m where m.conversation_id=conversation_id and m.user_id=auth.uid()));
create policy member_insert_self on public.conversation_members for insert to authenticated with check(user_id=auth.uid());
create policy message_member_read on public.messages for select to authenticated using(exists(select 1 from public.conversation_members m where m.conversation_id=messages.conversation_id and m.user_id=auth.uid()));
create policy message_member_insert on public.messages for insert to authenticated with check(sender_id=auth.uid() and exists(select 1 from public.conversation_members m where m.conversation_id=messages.conversation_id and m.user_id=auth.uid()));
create policy message_sender_update on public.messages for update to authenticated using(sender_id=auth.uid()) with check(sender_id=auth.uid());

create or replace function public.handle_new_user() returns trigger language plpgsql security definer set search_path=public as $$
begin insert into public.profiles(id,username,display_name) values(new.id,split_part(coalesce(new.email,''),'@',1),coalesce(new.raw_user_meta_data->>'display_name',split_part(coalesce(new.email,''),'@',1))) on conflict(id) do nothing; return new; end; $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users for each row execute function public.handle_new_user();

alter publication supabase_realtime add table public.messages;
