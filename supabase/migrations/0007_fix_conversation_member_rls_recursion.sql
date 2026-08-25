-- Fix the recursive conversation_members SELECT policy that caused Supabase REST 500s.
-- The old policy queried conversation_members from inside its own policy:
--   user_id = auth.uid() OR EXISTS (SELECT ... FROM conversation_members ...)
-- which PostgreSQL rejects as infinite recursion.

drop policy if exists members_select on public.conversation_members;
drop policy if exists members_select_self on public.conversation_members;

create policy members_select_self
  on public.conversation_members
  for select
  to authenticated
  using (user_id = (select auth.uid()));

-- The existing messages policies can now prove membership using the caller's
-- own membership row without recursively evaluating conversation_members.
