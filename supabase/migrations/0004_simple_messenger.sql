-- Simple messenger: plaintext message body, direct 1:1 conversation helper.
alter table public.messages add column if not exists body text;

create or replace function public.create_direct_conversation(other_user uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  me uuid := auth.uid();
  existing_id uuid;
  new_id uuid;
begin
  if me is null then raise exception 'not authenticated'; end if;
  if other_user is null or other_user = me then raise exception 'invalid recipient'; end if;
  if not exists (select 1 from auth.users where id = other_user) then raise exception 'user not found'; end if;

  select c.id into existing_id
  from conversations c
  join conversation_members a on a.conversation_id = c.id and a.user_id = me
  join conversation_members b on b.conversation_id = c.id and b.user_id = other_user
  where (select count(*) from conversation_members x where x.conversation_id = c.id) = 2
  limit 1;

  if existing_id is not null then return existing_id; end if;

  insert into conversations default values returning id into new_id;
  insert into conversation_members(conversation_id, user_id) values (new_id, me), (new_id, other_user);
  return new_id;
end;
$$;

grant execute on function public.create_direct_conversation(uuid) to authenticated;

do $$ begin
  alter publication supabase_realtime add table public.messages;
exception when duplicate_object then null;
end $$;
