-- 2026-11-17 · Aviso de rider asignado sin minutos falsos
-- APLICADO en STAGING (mbbdrmicjobydprvbpsj) el 2026-11-17. Falta repetirlo en producción (después de 2026-11-17_puntos_estado_pago_mixto.sql).
-- Re-aplicado el 2026-11-18: el aviso "llegó" dice que el rider recoge el saco y el acopio lo pesa.
-- Un recojo pedido para mañana decía "Llega en ~3 min" (los minutos son la distancia del rider ahora, no su visita).
-- Si la fecha programada es posterior a hoy (hora de Lima), el aviso dice el día y la ventana.

CREATE OR REPLACE FUNCTION public.notify_pickup_change()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
  v_rider text;
  v_gen   text;
  v_prev  text;
begin
  -- El acopio pesó la entrega: los puntos dejan de estar «por confirmar».
  if tg_op = 'UPDATE' and old.generator_points_final is null and new.generator_points_final is not null then
    insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push)
    values (new.generator_id, 'points_earned',
            case when new.generator_points_final > 0 then '¡Tus Eco-Puntos ya están disponibles!'
                 else 'Tu recojo no sumó puntos' end,
            case when new.generator_points_final > 0
                 then '+' || new.generator_points_final || ' Eco-Puntos: el centro de acopio pesó tu recojo. Ya puedes canjearlos.'
                 else 'El centro de acopio no pudo verificar el material de este recojo.' end,
            'pickup', new.id, 'normal');
  end if;

  if tg_op = 'UPDATE' and new.status is not distinct from old.status
     and new.rider_id is not distinct from old.rider_id then
    return null;
  end if;

  select coalesce(display_name, 'Tu Eco-Rider') into v_rider from public.profiles where id = new.rider_id;
  select coalesce(display_name, 'Una familia')  into v_gen   from public.profiles where id = new.generator_id;

  if new.rider_id is not null and (tg_op = 'INSERT' or old.rider_id is distinct from new.rider_id) then
    insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push) values
      (new.generator_id, 'rider_assigned', coalesce(v_rider, 'Un Eco-Rider') || ' recogerá tu reciclaje',
       case when new.scheduled_date > (now() at time zone 'America/Lima')::date
            then 'Pasará ' || coalesce(lower(new.window_label), 'el ' || to_char(new.scheduled_date, 'DD/MM')) || '. Te avisamos cuando salga.'
            else coalesce('Llega en ~' || new.eta_minutes || ' min. ', '') || 'Síguelo en vivo desde la app.' end, 'pickup', new.id, 'urgent'),
      (new.rider_id, 'rider_assigned', 'Nuevo recojo asignado',
       v_gen || coalesce(' · ' || new.window_label, '') || '. Ábrelo en tu ruta.', 'pickup', new.id, 'urgent');
  end if;

  if tg_op = 'UPDATE' and new.status is distinct from old.status then
    case new.status
      when 'en_route' then
        insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push)
        values (new.generator_id, 'pickup_update', 'Tu Eco-Rider va en camino',
                v_rider || ' salió hacia tu casa. Míralo acercarse en el mapa.', 'pickup', new.id, 'urgent');
      when 'arriving' then
        insert into public.notifications (user_id, kind, title, body, reference_type, reference_id)
        values (new.generator_id, 'rider_arriving', 'Tu Eco-Rider está llegando',
                v_rider || ' está a pocos metros. ¡Ten listos tus residuos!', 'pickup', new.id);
      when 'collecting' then
        insert into public.notifications (user_id, kind, title, body, reference_type, reference_id)
        values (new.generator_id, 'pickup_update', v_rider || ' llegó',
                'Está recogiendo tu saco. El acopio lo pesará y verificará, y ahí se acreditan tus puntos.', 'pickup', new.id);
      when 'completed' then
        insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push) values
          (new.generator_id, 'points_earned', 'Recojo completado',
           coalesce('+' || nullif(new.generator_points_awarded, 0) || ' Eco-Puntos por confirmar en el centro de acopio. ', '')
             || '¡Gracias por reciclar!', 'pickup', new.id, 'urgent');
        if new.rider_id is not null then
          insert into public.notifications (user_id, kind, title, body, reference_type, reference_id)
          values (new.rider_id, 'points_earned', 'Recojo completado',
                  'Recojo de ' || v_gen || ' listo. Estimado S/' || to_char(coalesce(new.rider_earnings, 0), 'FM990.00') || ': se paga cuando el acopio lo pese.',
                  'pickup', new.id);
        end if;
      when 'cancelled' then
        if new.rider_id is not null then
          insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push)
          values (new.rider_id, 'pickup_update', 'Recojo cancelado',
                  v_gen || ' canceló el recojo. Ya no está en tu ruta.', 'pickup', new.id, 'urgent');
        end if;
      when 'requested' then
        if old.rider_id is not null then
          select coalesce(display_name, 'Tu Eco-Rider') into v_prev from public.profiles where id = old.rider_id;
          insert into public.notifications (user_id, kind, title, body, reference_type, reference_id, push)
          values (new.generator_id, 'pickup_update', 'Buscando otro Eco-Rider',
                  v_prev || ' no podrá ir. Tu recojo volvió a la cola y otro Eco-Rider lo tomará.', 'pickup', new.id, 'urgent');
        end if;
      else null;
    end case;
  end if;
  return null;
end
$function$;
