# Avisos push de Ruma: puesta en marcha

Estado al 2026-11-12 (paquete de Firebase corregido a `pe.ruma.app` el 2026-11-16): todo el código y la base están listos y aplicados en **STAGING**. Falta lo que solo
puede hacer el dueño: crear el proyecto de Firebase y cargar los secretos. Producción no se ha tocado.

## Cómo funciona

```
trg_pickup_notify ──► notifications (columna push: none | normal | urgent)
                          │  trg_notification_push: solo si push <> 'none' y el usuario tiene algún dispositivo
                          ▼
                      push_queue ──► push_dispatch() ──pg_net, firmado──► Edge Function send-push
                          ▲              ▲                                   ├─ Android: Firebase (FCM HTTP v1)
                          │              └─ pg_cron cada minuto              └─ PWA / iPhone: Web Push (VAPID)
                          └─ send-push marca sent_at, o deja el error para el reintento
```

- **La base decide** qué se envía y cuándo. La app solo registra su token (`rpc/register_push_token`) y lo da de
  baja al cerrar sesión (`rpc/unregister_push_token`). Nadie lee `push_tokens` ni `push_queue` desde la app.
- **Silencio nocturno**: lo `normal` que nace entre las 21:00 y las 08:00 (Lima) sale a las 08:00. Lo `urgent`
  (un recojo en vivo) sale siempre.
- **Reintentos**: 4 intentos (al instante y a 1, 3 y 7 minutos). Un aviso urgente caduca a los 30 minutos y uno
  normal a las 12 horas: nadie recibe «tu Eco-Rider va en camino» dos horas tarde.
- **Tokens muertos** (app desinstalada, suscripción vencida) se borran solos cuando Firebase o el navegador lo dicen.

### Qué se envía

| Aviso | A quién | Push |
|---|---|---|
| «X recogerá tu reciclaje» (rider asignado) | familia | urgente |
| «Nuevo recojo asignado» | rider | urgente |
| «Tu Eco-Rider va en camino» | familia | urgente |
| «Recojo completado» | familia | urgente |
| «¡Tus Eco-Puntos ya están disponibles!» (el acopio pesó; aviso nuevo) | familia | normal |
| «Tu recojo no sumó puntos» (el acopio no verificó material) | familia | normal |
| «Recojo cancelado» | rider | urgente |
| «Buscando otro Eco-Rider» | familia | urgente |
| «Tu Eco-Rider está llegando», «X llegó» | familia | no (pasos repetidos; quedan en la app) |
| «Recojo completado» del rider | rider | no (lo acaba de marcar él) |
| Consejos, precios, retos, rankings y cualquier aviso futuro | — | no, salvo que quien lo escriba ponga `push` |

Cambiar una regla es cambiar el valor de `push` en el `insert` correspondiente de `notify_pickup_change`
(`supabase/sql/2026-11-12_push_reglas.sql`). No hay nada que tocar en las apps.

## Pasos del dueño

### 1. Proyecto de Firebase

1. Entra a <https://console.firebase.google.com> → **Agregar proyecto** → nombre «Ruma». Google Analytics no hace falta.
2. En el proyecto: **Agregar app → Android**.
   - Nombre del paquete: **`pe.ruma.app`** (exacto). Es el `applicationId` definitivo (Google
     Play rechaza `com.example.*`); el código ya se renombró a ese paquete (2026-11-16). Si registraras otro
     nombre, el build falla con «No matching client found for package name».
   - Sobrenombre: Ruma. El SHA-1 no hace falta para los avisos.
   - Descarga **`google-services.json`** y guárdalo en **`app/google-services.json`** (junto a `app/build.gradle.kts`),
     en el momento que indica el punto anterior.
   - Ignora los pasos «agrega el SDK» de la consola: ya están hechos.
3. **Configuración del proyecto → Cloud Messaging**: comprueba que «API de Firebase Cloud Messaging (V1)» diga
   *Habilitada* (es lo normal en un proyecto nuevo).

No hay que escribir ningún `firebase.xml` a mano: el plugin `com.google.gms.google-services` lee ese JSON y genera
los valores (`google_app_id`, `gcm_defaultSenderId`, `google_api_key`, `project_id`). Mientras el archivo no exista
el plugin no se aplica, la app compila igual y los avisos push simplemente no hacen nada.
`google-services.json` no es un secreto (viaja dentro de cada APK); se puede versionar.

### 2. Clave de la cuenta de servicio

**Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada**. Descarga el JSON y guárdalo
**fuera del repo** (por ejemplo `C:\Users\<tú>\ruma-firebase.json`). Este archivo sí es secreto: quien lo tenga
puede enviar avisos a todos los usuarios.

### 3. Secretos

En Git Bash, desde la raíz del repo, con la CLI de Supabase en la cuenta dueña del proyecto:

```bash
npx supabase login
bash scripts/setup-push-secrets.sh /c/Users/<tú>/ruma-firebase.json
```

El script no muestra ningún valor. Deja:

- secretos de la función: `PUSH_HOOK_SECRET`, `FCM_SERVICE_ACCOUNT`, `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT`;
- vault de la base: `push_hook_secret` (la misma firma) y `push_function_url`;
- `pwa/.env.staging.local` y `pwa/.env.development.local` con `VITE_VAPID_PUBLIC_KEY` (la clave pública, que va
  dentro de la PWA).

Volver a ejecutarlo es seguro: renueva la firma en los dos lados y no toca las claves VAPID (con `--rotate-vapid`
sí las cambia). `--dry-run` ensaya sin escribir nada. Después puedes borrar el JSON de la cuenta de servicio o
guardarlo en tu gestor de contraseñas.

### 4. Compilar y publicar

```powershell
.\gradlew.bat :app:installDebug          # Android, ya con app/google-services.json
```

```bash
cd pwa && npm run build:staging           # la clave pública VAPID entra en la compilación
# publicar dist como siempre (ver CLAUDE.md, sección PWA)
```

### 5. Comprobar

1. **Secretos en su sitio.** En el SQL Editor de STAGING:

   ```sql
   select net.http_post(
     url := (select decrypted_secret from vault.decrypted_secrets where name = 'push_function_url'),
     body := '{"health":true}'::jsonb,
     headers := jsonb_build_object('Content-Type', 'application/json', 'x-push-secret',
       (select decrypted_secret from vault.decrypted_secrets where name = 'push_hook_secret')));
   -- unos segundos después:
   select status_code, content from net._http_response order by id desc limit 1;
   ```

   Debe dar `200` y `{"fcm":true,"webpush":true}`. Un `403` significa que la firma del vault y la de la función
   no coinciden: repite el script.

2. **Prueba automática**: `python supabase/tests/push_staging.py`. Con los secretos puestos, el «PENDIENTE» del
   final se convierte en tres comprobaciones más (la base llama a la función, la función responde 200, la cola
   registra el resultado).

3. **De punta a punta, Android** (requiere el APK ya con `app/google-services.json`, es decir, después del
   renombrado a `pe.ruma.app` o con los dos paquetes registrados). Entra como `generador@ruma.test` en un teléfono, pide un recojo y pulsa
   «Confirmar recojo»: ahí aparece el permiso de notificaciones; acéptalo. Cierra la app. Desde otro teléfono o
   la PWA entra como `rider@ruma.test`, toma el recojo y márcalo en camino. Al primer teléfono le llega «X
   recogerá tu reciclaje» y luego «Tu Eco-Rider va en camino»; al tocarlo se abre el mapa de **ese** recojo.

4. **De punta a punta, iPhone.** En Safari abre `ruma-staging.vercel.app`, **Compartir → Añadir a pantalla de
   inicio**, abre Ruma desde ese ícono (iOS 16.4 o posterior), entra y toca **Activar avisos** (en
   Notificaciones, en la pantalla del recojo en espera o en el inicio del rider). Cierra la PWA y repite el
   recojo del punto 3.

5. **Si algo no llega**: `select * from push_queue order by created_at desc limit 10;` — `sent_at` con fecha es
   enviado; `last_error` dice por qué no (`FCM_SERVICE_ACCOUNT no configurado`, `fcm 401`, `webpush 403`…).
   `select * from push_tokens;` muestra qué dispositivos hay.

## Lo que falta para producción

1. Aplicar en producción, en este orden, `supabase/sql/2026-11-12_push_notifications.sql` y
   `supabase/sql/2026-11-12_push_reglas.sql`, y actualizar sus encabezados. Requiere activar `pg_net` y `pg_cron`.
2. Desplegar la función: `npx supabase functions deploy send-push --project-ref <ref de producción> --no-verify-jwt`.
3. Secretos de producción: `RUMA_PROJECT_REF=<ref de producción> RUMA_ALLOW_PRODUCTION=si bash scripts/setup-push-secrets.sh <clave.json>`.
   El script escribe la clave pública VAPID en los `.env` de staging: para producción muévela a
   `pwa/.env.production.local` (las claves VAPID son distintas por proyecto).
4. El mismo proyecto de Firebase sirve para los dos entornos (staging y producción) con la app `pe.ruma.app`.
   Registrarla con ese paquete desde el principio evita rehacer Firebase al publicar en Play Store.
5. La app Android sigue apuntando a staging (`data/SupabaseConfig.kt`).
6. No verificado en dispositivo: por indicación no se instaló nada en el teléfono. La entrega real (FCM y
   Web Push) solo se puede comprobar con los secretos puestos, en el paso 5.

## Decisiones

- **Plugin `google-services` con AGP 9.1.1: sí.** Las notas oficiales no declaran compatibilidad con AGP 9 para
  este plugin (sí para los de Crashlytics, Performance y App Distribution:
  <https://firebase.google.com/support/release-notes/android>), así que se comprobó en este repo: con la versión
  4.5.0 y un `google-services.json` de ensayo, `:app:processDebugGoogleServices` terminó bien sobre AGP 9.1.1 /
  Gradle 9.3.1 y generó los cuatro valores. Se aplica solo si el archivo existe. En Windows el daemon de Gradle
  deja el JSON bloqueado: para reemplazarlo, `.\gradlew.bat --stop` primero.
- **Web Push sin librería.** `supabase/functions/send-push/webpush.ts` implementa el cifrado (RFC 8291) y VAPID
  (RFC 8292) con WebCrypto, unas 60 líneas, y `webpush.test.mjs` lo compara byte a byte con el ejemplo del RFC.
  Si algún día se prefiere una librería, la alternativa para Deno es `jsr:@negrel/webpush`.
- **Service worker**: se mantiene `generateSW`; los manejadores `push` y `notificationclick` están en
  `pwa/public/push-sw.js` y entran con `workbox.importScripts`. No hizo falta pasar a `injectManifest`.
- **Permiso de notificaciones en Android**: ya no se pide en frío al abrir la app. La familia lo ve tras
  «Confirmar recojo»; el rider, al iniciar turno o tomar un recojo.
- **Límites conocidos**: un reintento tras un fallo parcial puede repetir el aviso en el dispositivo que sí lo
  recibió (la etiqueta por recojo hace que reemplace al anterior, no que se apile). El tope de «un aviso no
  transaccional al día» de la investigación no se implementó porque hoy no existe ningún aviso de ese tipo con push.
