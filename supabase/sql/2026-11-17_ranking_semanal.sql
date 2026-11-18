-- 2026-11-17 · Ranking semanal en vivo (familias y riders)
-- APLICADO en STAGING (mbbdrmicjobydprvbpsj) el 2026-11-17. Falta repetirlo en producción.
--
-- Antes, leaderboard_periods / leaderboard_entries eran filas fijas del seed (semana 12-18 Oct, "Las Flores"):
-- un tester nuevo veía familias de ejemplo y no se encontraba. Ahora refresh_leaderboards() abre el periodo de
-- la semana en curso (lunes a domingo, hora de Lima), cierra los anteriores y recalcula el ranking:
--   familia: puntos de sus recojos completados esta semana (los pesados valen lo pesado; los aún sin pesar,
--            lo estimado al recoger). Sin bonos de retos ni referidos: el ranking mide reciclaje, y las cuentas
--            de las pruebas automáticas (bonos sin recojos) llenaban los primeros puestos.
--   rider:   los mismos puntos de los recojos que completó esta semana (el material que movió).
-- Entra todo usuario activo del rol, aunque tenga 0, para que cada tester se vea en la lista.
-- Las apps no cambian de consulta: siguen leyendo el periodo abierto y sus entradas.

create or replace function public.refresh_leaderboards()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  wk_start date := date_trunc('week', now() at time zone 'America/Lima')::date;
  wk_end   date := wk_start + 6;
  t0 timestamptz := wk_start::timestamp at time zone 'America/Lima';
  t1 timestamptz := (wk_start + 7)::timestamp at time zone 'America/Lima';
  r user_role;
  pid uuid;
begin
  update leaderboard_periods set status = 'closed' where status = 'open' and period_end < wk_start;

  foreach r in array array['generador', 'eco_rider']::user_role[] loop
    -- ponytail: one board for the whole pilot (Cartavio); per-district boards when there is a second town.
    select id into pid from leaderboard_periods
     where participant_role = r and period_type = 'weekly' and period_start = wk_start and status = 'open'
     limit 1;
    if pid is null then
      insert into leaderboard_periods (scope, block_label, participant_role, period_type, period_start, period_end, status)
      values ('district', 'Cartavio', r, 'weekly', wk_start, wk_end, 'open')
      returning id into pid;
    end if;

    drop table if exists _lb;
    create temp table _lb on commit drop as
    select p.id as user_id,
           coalesce(nullif(p.display_name, ''), split_part(p.full_name, ' ', 1), 'Vecino') as display_name,
           coalesce((
             select sum(coalesce(k.generator_points_final, k.generator_points_awarded, 0))
               from pickups k
              where k.status = 'completed' and k.completed_at >= t0 and k.completed_at < t1
                and (case when r = 'generador' then k.generator_id else k.rider_id end) = p.id
           ), 0) as points,
           coalesce(g.current_streak_days, 0) as streak_days,
           p.created_at
      from profiles p
      left join generator_profiles g on g.profile_id = p.id
     where p.role = r and coalesce(p.status::text, 'active') = 'active';

    delete from leaderboard_entries e where e.period_id = pid
      and not exists (select 1 from _lb where _lb.user_id = e.user_id);

    insert into leaderboard_entries (period_id, user_id, display_name, points, streak_days, rank)
    select pid, user_id, display_name, points::int, streak_days,
           row_number() over (order by points desc, created_at)
      from _lb
    on conflict (period_id, user_id) do update
       set display_name = excluded.display_name, points = excluded.points,
           streak_days = excluded.streak_days, rank = excluded.rank;
  end loop;
end $$;

revoke all on function public.refresh_leaderboards() from public, anon, authenticated;

select public.refresh_leaderboards();

select cron.schedule('ruma-leaderboards', '* * * * *', 'select public.refresh_leaderboards()');
