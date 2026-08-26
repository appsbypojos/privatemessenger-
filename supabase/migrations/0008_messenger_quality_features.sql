create table if not exists public.message_reactions (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  reaction text not null check (char_length(reaction) between 1 and 16),
  created_at timestamptz not null default now(),
  primary key (message_id, user_id, reaction)
);

create index if not exists message_reactions_message_idx on public.message_reactions(message_id);

create table if not exists public.message_reads (
  message_id uuid not null references public.messages(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  read_at timestamptz not null default now(),
  primary key (message_id, user_id)
);

create index if not exists message_reads_user_idx on public.message_reads(user_id);

alter table public.message_reactions enable row level security;
alter table public.message_reads enable row level security;

drop policy if exists reactions_select_member on public.message_reactions;
create policy reactions_select_member on public.message_reactions for select to authenticated using (exists (select 1 from public.messages m join public.conversation_members cm on cm.conversation_id=m.conversation_id where m.id=message_id and cm.user_id=auth.uid()));
drop policy if exists reactions_insert_self on public.message_reactions;
create policy reactions_insert_self on public.message_reactions for insert to authenticated with check (user_id=auth.uid() and exists (select 1 from public.messages m join public.conversation_members cm on cm.conversation_id=m.conversation_id where m.id=message_id and cm.user_id=auth.uid()));
drop policy if exists reactions_delete_self on public.message_reactions;
create policy reactions_delete_self on public.message_reactions for delete to authenticated using (user_id=auth.uid());

drop policy if exists reads_select_member on public.message_reads;
create policy reads_select_member on public.message_reads for select to authenticated using (user_id=auth.uid() or exists (select 1 from public.messages m join public.conversation_members cm on cm.conversation_id=m.conversation_id where m.id=message_id and cm.user_id=auth.uid()));
drop policy if exists reads_insert_self on public.message_reads;
create policy reads_insert_self on public.message_reads for insert to authenticated with check (user_id=auth.uid() and exists (select 1 from public.messages m join public.conversation_members cm on cm.conversation_id=m.conversation_id where m.id=message_id and cm.user_id=auth.uid()));
drop policy if exists reads_update_self on public.message_reads;
create policy reads_update_self on public.message_reads for update to authenticated using (user_id=auth.uid()) with check (user_id=auth.uid());

drop policy if exists messages_update_sender on public.messages;
create policy messages_update_sender on public.messages for update to authenticated using (sender_id=auth.uid()) with check (sender_id=auth.uid());
drop policy if exists messages_delete_sender on public.messages;
create policy messages_delete_sender on public.messages for delete to authenticated using (sender_id=auth.uid());

do $$ begin alter publication supabase_realtime add table public.message_reactions; exception when duplicate_object then null; end $$;
do $$ begin alter publication supabase_realtime add table public.message_reads; exception when duplicate_object then null; end $$;
