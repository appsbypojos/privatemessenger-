alter table public.profiles
  add constraint profiles_username_format
  check (username is null or username ~ '^[a-z0-9_]{3,24}$');

create unique index if not exists profiles_username_lower_idx
  on public.profiles (lower(username))
  where username is not null;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  requested_username text;
begin
  requested_username := lower(trim(coalesce(new.raw_user_meta_data->>'username', split_part(coalesce(new.email,''),'@',1))));
  insert into public.profiles(id, username, display_name)
  values (new.id, requested_username, requested_username)
  on conflict (id) do update
    set username = coalesce(excluded.username, public.profiles.username),
        display_name = coalesce(excluded.display_name, public.profiles.display_name),
        updated_at = now();
  return new;
end;
$$;

create or replace function public.search_profiles(search_username text)
returns table(id uuid, username text, display_name text)
language sql
security invoker
stable
as $$
  select p.id, p.username, p.display_name
  from public.profiles p
  where p.id <> auth.uid()
    and lower(p.username) like '%' || lower(trim(search_username)) || '%'
  order by lower(p.username)
  limit 50;
$$;

grant execute on function public.search_profiles(text) to authenticated;
