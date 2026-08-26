create or replace function public.set_message_edited_at()
returns trigger
language plpgsql
as $$
begin
  if new.body is distinct from old.body then
    new.edited_at = now();
  end if;
  return new;
end;
$$;

drop trigger if exists messages_set_edited_at on public.messages;
create trigger messages_set_edited_at before update on public.messages for each row execute function public.set_message_edited_at();
