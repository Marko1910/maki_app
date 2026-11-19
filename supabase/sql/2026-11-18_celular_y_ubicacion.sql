-- 2026-11-18 · Un recojo necesita cómo llamar a la familia y dónde encontrarla
-- APLICADO en STAGING (mbbdrmicjobydprvbpsj) el 2026-11-18. Falta repetirlo en producción.
--
-- Ninguna cuenta real tenía celular (0 de 8): el registro no lo pedía y el botón "Llamar" del rider nunca aparecía.
-- Y un recojo sin GPS ni dirección (cuenta sin dirección guardada, celular sin señal) salía sin ubicación: el rider
-- no tenía a dónde ir. La base de datos decide, así que vale también para los APK ya instalados (v1-v3), que muestran
-- este mensaje y tienen el celular en Perfil > Editar perfil.

create or replace function public.require_pickup_contact()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  -- ponytail: the seeded *@ruma.test accounts have no phone on purpose, so no real number gets the tests' calls.
  if exists (select 1 from auth.users u where u.id = new.generator_id and lower(u.email) like '%@ruma.test') then
    return new;
  end if;
  if coalesce((select length(regexp_replace(coalesce(p.phone, ''), '\D', '', 'g')) from profiles p where p.id = new.generator_id), 0) < 9 then
    raise exception 'Agrega tu número de celular para que el Eco-Rider pueda llamarte (Perfil > Editar perfil).';
  end if;
  if new.latitude is null and new.address_id is null then
    raise exception 'Activa tu ubicación o escribe tu dirección para que el Eco-Rider te encuentre.';
  end if;
  return new;
end $$;
revoke all on function public.require_pickup_contact() from public, anon, authenticated;

drop trigger if exists trg_pickup_contact on public.pickups;
create trigger trg_pickup_contact before insert on public.pickups
  for each row execute function public.require_pickup_contact();
