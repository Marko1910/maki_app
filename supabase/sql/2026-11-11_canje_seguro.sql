-- Ruma: el canje lo valora la base, no la app (2026-11-11).
-- APLICADO en STAGING el 2026-11-11 y verificado con supabase/tests/canje_seguro_staging.py (5/5).
-- Falta repetirlo en producción.
--
-- Hueco: validate_redemption solo comprobaba que el saldo alcanzara. points_spent, monetary_value, kind,
-- status y los datos de pago llegaban tal cual desde el cliente, así que una app alterada podía gastar
-- 1 punto y anotar S/ 100, canjear sin opción o crear el canje ya como pagado.
-- Ahora, en cada INSERT:
--   * option_id es obligatorio y la opción debe estar activa; kind sale de la opción.
--   * points_spent >= min_points y en unidades enteras de points_per_unit.
--   * monetary_value = points_spent / points_per_unit * unit_value (lo que mande el cliente se ignora).
--   * status nace 'pending'; processed_at y payout_reference vacíos (los llena el operador al pagar).
-- Nadie inserta canjes desde funciones del servidor (comprobado 2026-11-11), así que la regla vale para todos.
-- Las apps no cambian: ya mandan option_id y min_points; el valor que envían se recalcula igual.

begin;

create or replace function public.validate_redemption()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  v_balance integer;
  v_opt public.redemption_options;
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

  select points_balance into v_balance
    from public.generator_profiles where profile_id = new.user_id for update;
  if v_balance is null then
    raise exception 'No encontramos tu billetera de Eco-Puntos.' using errcode = '22023';
  end if;
  if v_balance < new.points_spent then
    raise exception 'Saldo insuficiente: tienes % pts.', v_balance using errcode = '22023';
  end if;
  return new;
end;
$$;

-- Defense in depth: the app only inserts; updates and deletes belong to the operator's tools.
revoke update, delete on public.redemptions from authenticated, anon;

commit;
