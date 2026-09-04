-- Run this once in the Supabase SQL editor.
-- The admin password is intentionally not stored in source control.

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null,
    approval_status text not null default 'PENDING'
        check (approval_status in ('PENDING', 'APPROVED', 'DECLINED')),
    subscription_plan text not null default 'NONE'
        check (subscription_plan in ('NONE', 'ONE_DAY', 'TWO_DAYS', 'THREE_DAYS', 'LIFETIME')),
    subscription_expires_at timestamptz,
    is_admin boolean not null default false,
    created_at timestamptz not null default timezone('utc', now()),
    updated_at timestamptz not null default timezone('utc', now())
);

create or replace function public.create_profile_for_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
    insert into public.profiles (id, email)
    values (new.id, coalesce(new.email, ''));
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute procedure public.create_profile_for_new_user();

-- Backfill accounts that were created before the trigger existed. This is
-- important for the administrator account and keeps existing users usable.
insert into public.profiles (id, email)
select id, coalesce(email, '')
from auth.users
on conflict (id) do update
set email = excluded.email,
    updated_at = timezone('utc', now());

alter table public.profiles enable row level security;

grant usage on schema public to authenticated;
grant select on public.profiles to authenticated;

create or replace function public.current_user_is_admin()
returns boolean
language sql
security definer set search_path = public
stable
as $$
    select exists (
        select 1 from public.profiles
        where id = auth.uid() and is_admin = true
    );
$$;

revoke all on function public.current_user_is_admin() from public;
grant execute on function public.current_user_is_admin() to authenticated;

drop policy if exists "Users can read their own profile" on public.profiles;
create policy "Users can read their own profile"
    on public.profiles for select
    using (auth.uid() = id);

drop policy if exists "Admins can read all non-admin profiles" on public.profiles;
create policy "Admins can read all non-admin profiles"
    on public.profiles for select
    using (public.current_user_is_admin());

create or replace function public.admin_set_subscription(target_user_id uuid, plan text)
returns public.profiles
language plpgsql
security definer set search_path = public
as $$
declare
    updated_profile public.profiles;
    expiry timestamptz;
begin
    if not public.current_user_is_admin() then
        raise exception 'Only an administrator can approve users';
    end if;

    if plan not in ('ONE_DAY', 'TWO_DAYS', 'THREE_DAYS', 'LIFETIME') then
        raise exception 'Invalid subscription plan';
    end if;

    expiry := case plan
        when 'ONE_DAY' then timezone('utc', now()) + interval '1 day'
        when 'TWO_DAYS' then timezone('utc', now()) + interval '2 days'
        when 'THREE_DAYS' then timezone('utc', now()) + interval '3 days'
        else null
    end;

    update public.profiles
    set approval_status = 'APPROVED',
        subscription_plan = plan,
        subscription_expires_at = expiry,
        updated_at = timezone('utc', now())
    where id = target_user_id
    returning * into updated_profile;

    return updated_profile;
end;
$$;

create or replace function public.admin_decline_user(target_user_id uuid)
returns public.profiles
language plpgsql
security definer set search_path = public
as $$
declare
    updated_profile public.profiles;
begin
    if not public.current_user_is_admin() then
        raise exception 'Only an administrator can decline users';
    end if;

    update public.profiles
    set approval_status = 'DECLINED',
        subscription_plan = 'NONE',
        subscription_expires_at = null,
        updated_at = timezone('utc', now())
    where id = target_user_id
    returning * into updated_profile;

    return updated_profile;
end;
$$;

revoke all on function public.admin_set_subscription(uuid, text) from public;
grant execute on function public.admin_set_subscription(uuid, text) to authenticated;
revoke all on function public.admin_decline_user(uuid) from public;
grant execute on function public.admin_decline_user(uuid) to authenticated;

-- After creating the administrator in Supabase Auth, run this without the password:
-- update public.profiles set is_admin = true where email = 'aalamdiwan555@gmail.com';