-- Ruma · 2026-10-28
-- NO APLICADO en ningún entorno.
-- Crea el almacén de fotos de perfil. Sin esto, `profiles.avatar_url` existe pero
-- no hay dónde poner el archivo: la API de Storage devuelve `[]` de buckets.
--
-- Ejecutar en el editor SQL de STAGING (mbbdrmicjobydprvbpsj) y, cuando la app
-- lo use sin sorpresas, repetirlo en producción (kqwyygxpcsmworvmjqdv).
--
-- No se pudo aplicar desde la sesión de trabajo: crear un bucket exige
-- privilegios que ni la clave anónima ni la sesión de un usuario tienen.

begin;

-- 2 MB y solo imágenes. El límite vive en el bucket y no en el cliente porque
-- el cliente es el único sitio donde un límite no se puede hacer cumplir.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'avatars', 'avatars', true, 2097152,
  array['image/jpeg', 'image/png', 'image/webp']
)
on conflict (id) do update
   set public = excluded.public,
       file_size_limit = excluded.file_size_limit,
       allowed_mime_types = excluded.allowed_mime_types;

-- Público para leer: la foto de un rider la ve la familia que lo espera, y una
-- URL firmada que caduca convertiría cada lista en una tanda de peticiones.
-- La foto de perfil no es un dato reservado; el resto del perfil sí, y eso lo
-- protege RLS en `profiles`, no aquí.
drop policy if exists avatars_lectura_publica on storage.objects;
create policy avatars_lectura_publica on storage.objects
  for select using (bucket_id = 'avatars');

-- Escribir: solo dentro de la carpeta con tu propio uid. Sin esta condición
-- cualquier autenticado podría reemplazar la foto de cualquier otro.
drop policy if exists avatars_escritura_propia on storage.objects;
create policy avatars_escritura_propia on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

drop policy if exists avatars_actualizacion_propia on storage.objects;
create policy avatars_actualizacion_propia on storage.objects
  for update to authenticated
  using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

drop policy if exists avatars_borrado_propio on storage.objects;
create policy avatars_borrado_propio on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

commit;

-- Convención de ruta que espera la app: `<uid>/avatar.jpg`.
-- La URL pública queda en
--   <SUPABASE_URL>/storage/v1/object/public/avatars/<uid>/avatar.jpg
-- y es la que se guarda en `profiles.avatar_url`.

-- Verificación posterior:
-- select id, public, file_size_limit, allowed_mime_types from storage.buckets
--  where id = 'avatars';
-- select policyname, cmd from pg_policies
--  where schemaname = 'storage' and tablename = 'objects'
--    and policyname like 'avatars%' order by policyname;
