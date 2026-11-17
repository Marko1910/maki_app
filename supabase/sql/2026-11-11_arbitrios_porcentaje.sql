-- Ruma: el descuento de arbitrios se canjea en PORCENTAJE del arbitrio anual, no en soles (2026-11-11).
-- APLICADO en STAGING el 2026-11-11 y verificado con supabase/tests/arbitrios_porcentaje_staging.py (6/6;
-- gastó un 5 % real = 2.750 pts de generador@maki.test, que queda 'pending').
-- Falta repetirlo en producción (después de 2026-11-11_canje_seguro.sql, que este script extiende).
--
-- El arbitrio de limpieza de una vivienda en Santiago de Cao es S/ 55 al año. Con la equivalencia de
-- docs/canje-minimo-y-arbitrios.md (1.000 pts = S/ 1, tope del 50 % del arbitrio al año, a aprobar por el
-- Concejo Municipal): 1 % del arbitrio = S/ 0,55 = 550 pts.
--   * MUNI_ARBITRIOS: points_per_unit 550, unit_value 0.55, min_points 2750 (5 %). quantity = puntos de %.
--   * metadata {"unit":"percent","percent_per_unit":1,"arbitrio_anual":55,"max_percent_year":50}: las apps
--     muestran la opción en % con esos datos.
--   * validate_redemption (definición de 2026-11-11_canje_seguro.sql, sin quitarle nada) suma un tope: si la
--     opción trae metadata->>'max_percent_year', lo canjeado por ese usuario con esa opción en el año
--     calendario de Lima (sin cancelados ni fallidos) más lo nuevo no pasa ese máximo.
-- Las apps no cambian cómo canjean: mandan option_id y min_points y el servidor fija el valor.

begin;

update public.redemption_options
   set points_per_unit = 550,
       unit_value      = 0.55,
       min_points      = 2750,
       description     = '5 % de tu arbitrio anual por 2.750 pts · hasta 50 % al año',
       metadata        = coalesce(metadata, '{}'::jsonb)
                         || '{"unit":"percent","percent_per_unit":1,"arbitrio_anual":55,"max_percent_year":50}'::jsonb
 where code = 'MUNI_ARBITRIOS';

create or replace function public.validate_redemption()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_balance integer;
  v_opt public.redemption_options;
  v_max numeric;
  v_per numeric;
  v_used numeric;
begin
  if new.option_id is null then
    raise exception 'Elige una opción de canje.' using errcode = '22023';
  end if;
  select * into v_opt from public.redemption_options where id = new.option_id;
  if not found or not v_opt.is_active then
    raise exception 'Esa opción de canje no está disponible.' using errcode = '22023';
  end if;
  if new.points_spent is null or new.points_spent < greatest(v_opt.min_points, 1) then
    raise exception 'El canje mínimo es de % pts.', v_opt.min_points using errcode = '22023';
  end if;
  if v_opt.points_per_unit > 0 and mod(new.points_spent, v_opt.points_per_unit) <> 0 then
    raise exception 'Canjea en múltiplos de % pts.', v_opt.points_per_unit::int using errcode = '22023';
  end if;

  -- The server values the redemption; whatever the client sent is overwritten.
  new.kind := v_opt.kind;
  new.monetary_value := case when v_opt.points_per_unit > 0
                             then round(new.points_spent / v_opt.points_per_unit * v_opt.unit_value, 2) else 0 end;
  new.quantity := case when v_opt.points_per_unit > 0 then new.points_spent / v_opt.points_per_unit else 1 end;
  new.status := 'pending';
  new.processed_at := null;
  new.payout_reference := null;

  -- The wallet row lock also serialises this user's yearly cap: two taps can't both fit under it.
  select points_balance into v_balance
    from public.generator_profiles where profile_id = new.user_id for update;
  if v_balance is null then
    raise exception 'No encontramos tu billetera de Eco-Puntos.' using errcode = '22023';
  end if;

  v_max := nullif(v_opt.metadata->>'max_percent_year', '')::numeric;
  if v_max is not null then
    v_per := coalesce(nullif(v_opt.metadata->>'percent_per_unit', '')::numeric, 1);
    select coalesce(sum(r.quantity), 0) * v_per into v_used
      from public.redemptions r
     where r.user_id = new.user_id
       and r.option_id = new.option_id
       and r.status not in ('cancelled', 'failed')
       and (r.created_at at time zone 'America/Lima') >= date_trunc('year', now() at time zone 'America/Lima');
    if v_used + new.quantity * v_per > v_max then
      raise exception 'Ya usaste el % %% de descuento de este año (máximo % %%).',
        trim_scale(v_used), trim_scale(v_max) using errcode = '22023';
    end if;
  end if;

  if v_balance < new.points_spent then
    raise exception 'Saldo insuficiente: tienes % pts.', v_balance using errcode = '22023';
  end if;
  return new;
end;
$$;

commit;
