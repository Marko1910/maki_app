# Plan de trabajo: cerrar las cuatro líneas a medias y renombrar MAKI → Ruma

Revisado el 2026-11-16 (23:30 a 23:50, hora de Lima) contra el repo (rama `feat/ruma-pwa`, nada commiteado de estas
líneas) y contra STAGING (`mbbdrmicjobydprvbpsj`) en solo lectura. Producción no tiene nada de esto.

## 0. Aviso antes de empezar

**Alguien más editó el árbol mientras se hacía esta revisión.** A las 23:30 los dos builds fallaban; a las 23:35:40
cambiaron `pwa/src/lib/api.ts` y `GeneratorInviteScreen.kt` (no fue el planificador) y desde entonces los dos pasan.
Antes de lanzar ejecutores hay que confirmar que no queda ningún agente o sesión escribiendo en el repo.

| Build | 23:30 | 23:45 |
|---|---|---|
| `./gradlew :app:compileDebugKotlin -q` | FALLA: `GeneratorInviteScreen.kt:307:36 Unresolved reference 'padding'` (faltaba `import androidx.compose.foundation.layout.padding`) | pasa |
| `npx tsc -b --noEmit` en `pwa/` | FALLA: `Invite.tsx(4,10) TS2305: '"../../lib/api"' has no exported member 'applyReferralCode'` y `'loadReferralSummary'`, más 19 errores TS2339 derivados (faltaban las dos funciones y el tipo `ReferralSummary` en `api.ts`) | pasa |

También pasan: `node scripts/check-fold.ts`, `check-invite.ts`, `check-next.ts`, `check-week.ts` (en `pwa/`) y
`node --test supabase/functions/send-push/webpush.test.mjs` (vector del RFC 8291). No se corrieron las pruebas
`*_staging.py` porque escriben en STAGING, ni `testDebugUnitTest`.

## 1. Estado de cada línea

### 1.1 Notificaciones push: código terminado, parado en los secretos del dueño

Hecho y comprobado:
- STAGING: tablas `push_tokens` y `push_queue`; funciones `register_push_token(4 args)`, `unregister_push_token`,
  `push_dispatch`, `push_not_before`, `push_notification`; cron `ruma-push-dispatch` (cada minuto) y
  `ruma-push-cleanup`; columna `notifications.push` (45 avisos ya marcados).
- Edge Function `send-push` desplegada (versión 2, `verify_jwt=false`, del 12-11 a la misma hora que los archivos
  locales). `webpush.ts` pasa el vector del RFC.
- Android: `data/MakiPush.kt`, `MakiPushService` en el manifiesto, dependencia de Firebase y plugin
  `google-services` que solo se aplica si existe `app/google-services.json`; `navigation/NotificationAsk.kt`.
- PWA: `pwa/public/push-sw.js`, `importScripts` en `pwa/vite.config.ts`, `src/components/Push.tsx`,
  `registerPushToken` y la baja al cerrar sesión en `src/lib/api.ts`.
- `scripts/setup-push-secrets.sh` (145 líneas, con `--dry-run`), `docs/push-setup.md` (166 líneas, completo) y
  `supabase/tests/push_staging.py` (194 líneas, completo). El agente sí llegó a escribir los tres.

Falta:
- **Secretos (dueño):** el vault de STAGING tiene 0 secretos, así que la cola no llama a nadie. No existe
  `app/google-services.json`. `push_tokens` está vacía: ningún dispositivo se registró nunca.
- La verificación final: `python supabase/tests/push_staging.py` y la prueba de punta a punta en un teléfono.
- El encabezado de `supabase/sql/2026-11-12_push_notifications.sql` todavía dice «APLICAR en STAGING primero»
  aunque ya está aplicado.
- `docs/push-setup.md` manda registrar la app en Firebase como `com.example.maki`. Con el renombrado decidido
  debe decir `pe.ruma.app` (ver 4, decisión D1).

Roto: nada.

### 1.2 Referidos: base y Android completos, la PWA sin conectar

Hecho y comprobado:
- STAGING: `referral_codes` (14 códigos), `referrals`, `apply_referral_code`, `attach_referral`,
  `my_referral_summary`, `pay_referral_on_weigh`, `referral_prize`, `referral_code_for`; ajustes en
  `platform_settings` (2.000 / 1.000 pts, 1 kg, tope 5 al mes, 60 global, 7 días). El encabezado del SQL dice 13/13.
- Android: `GeneratorInviteScreen.kt` (330 líneas, con `InviteViewModel`, `InvitePrompt`, `InvitePromptCard`), ruta
  `GenRoutes.INVITE` en `GeneratorRoot`, fila «Invita y gana» en `GeneratorProfileScreen`, tarjeta en el inicio,
  campo de código en `RegisterScreen` y `AuthViewModel`, `loadReferralSummary` / `applyReferralCode` en
  `MakiRepository`, DTO en `Dtos.kt`.
- PWA: `src/screens/family/Invite.tsx` (97 líneas), `src/lib/referral.ts`, `scripts/check-invite.ts`, y las dos
  llamadas en `api.ts`.

Falta:
- **PWA: la pantalla no se puede abrir.** `src/App.tsx` no tiene ninguna ruta ni import de `Invite`; no hay fila
  en el Perfil ni tarjeta en el inicio de `Family.tsx`; `signUp` en `api.ts` no envía `referral_code` y
  `Auth.tsx` no tiene campo de código. Como nadie importa `Invite.tsx`, tampoco se ejecuta su
  `captureRef(location.search)`: un enlace `/?ref=CODIGO` hoy se pierde.
- **`docs/referidos.md` no existe**, y el encabezado de `2026-11-12_referidos.sql` lo cita.
- Volver a correr `python supabase/tests/referidos_staging.py` (el SQL cambió el 16-11 para sumar el bono del plan).

Roto: nada ahora. Suciedad de pruebas en STAGING, por diseño de la prueba: 9 cuentas `ref-*@maki.test`,
6 referidos (3 pagados a `generador@maki.test`, 3 con `tope_mensual`).

### 1.3 Navegación fluida y enganche: hecho en la familia, sin documento y sin rider

Hecho:
- Android: transiciones `makiEnter/Exit/PopEnter/PopExit` en `NavExt.kt`, aplicadas en `AppRoot`,
  `GeneratorRoot` y `RiderRoot`; tarjeta «siguiente mejor acción» (`NextStep.kt`, `NextStepCard` en
  `GeneratorHomeScreen`, `NextStepTest.kt`); progreso semanal (`data/WeekProgress.kt`, `WeekProgressTest.kt`);
  esqueletos `MakiSkeleton` en 7 pantallas de la familia; háptica en dos confirmaciones de `GeneratorRoot`.
- PWA: View Transitions en `src/lib/flow.ts` + `useTransitionLocation` en `App.tsx`; `nextStep.ts`, `week.ts`;
  `Skeleton` en `ui.tsx`, usado en `Family.tsx` y `Pickup.tsx`; `tick()` (vibración) en 5 sitios.

Falta:
- **`docs/navegacion-fluida.md` no existe.**
- El rider quedó fuera: ni esqueletos ni háptica en `ui/screens/rider/*`, `RiderRoot.kt`, `pwa/src/screens/rider/*`.
- Correr `:app:testDebugUnitTest` y mirar las transiciones en un teléfono (nada de esto se verificó en dispositivo).

Roto: nada.

### 1.4 Planes de pago: solo existe la base

Hecho y comprobado:
- STAGING: `plans` (GRATIS; PLUS a S/ 4,90 al mes, S/ 39,90 al año, 14 días de prueba, beneficios
  `redeem_min_points:1000`, `dispatch_priority:1`, `raffle_tickets_bonus:2`), `plan_subscriptions`,
  `plan_payment_config`; `request_plan`, `start_plan_trial`, `cancel_my_plan`, `my_plan`,
  `activate_plan_subscription`, `reject_plan_payment`, `expire_plan_subscriptions`, `user_benefits`,
  `raffle_tickets`; cron `ruma-plans-expire`. El encabezado dice 21/21.
- `supabase/tests/planes_staging.py` (121 líneas).

Falta:
- **Todas las pantallas.** No hay ni una referencia a `my_plan` / `request_plan` en `app/`, `pwa/` ni `dashboard/`:
  ni pantalla de Planes en Android, ni en la PWA, ni la vista del operador para activar o rechazar un pago.
- **`docs/planes-de-pago.md` no existe**, y el encabezado del SQL lo cita.
- `plan_payment_config`: `yape_number`, `yape_holder`, `yape_qr_url` y `business_contact` están en NULL (dueño).

Roto: nada. Suciedad de pruebas: 5 suscripciones de `generador@maki.test` (3 vencidas, 2 canceladas).

## 2. Paquetes de ejecución

Los archivos compartidos (`App.tsx`, `api.ts`, `Family.tsx`; `GeneratorRoot.kt`, `GenRoutes.kt`,
`MakiRepository.kt`, `Dtos.kt`, `GeneratorProfileScreen.kt`) obligan a repartir **por plataforma**, no por línea:
un ejecutor por carril, y dentro del carril los paquetes van en serie.

| Carril | Dueño exclusivo de | Paquetes, en orden |
|---|---|---|
| A. Android | `app/**` | A1 → A2 |
| B. PWA | `pwa/**` | B1 → B2 → B3 |
| C. Base y documentos | `docs/**` (menos este plan), `supabase/sql/**`, `supabase/tests/**` | C1 → C2 |
| D. Panel | `dashboard/**` | D1 |
| Dueño | secretos, Firebase, Yape | O1, O2 |
| Final, solo | todo el repo | R1 → R2 → R3 → R4 → R5 |

Los carriles A, B, C y D corren en paralelo entre sí. Solo el carril C toca STAGING: las pruebas `*_staging.py`
escriben sobre `generador@maki.test` y cambian `platform_settings`, así que nunca dos a la vez.

### P0. Congelar y confirmar el punto de partida (serie, antes de todo, 15 min)
- Objetivo: saber que nadie más escribe y que el árbol compila.
- Archivos: ninguno.
- Pasos: cerrar las sesiones o agentes que sigan abiertos; `git status --short`; se recomienda que el dueño haga
  un commit del estado actual para poder volver atrás (ningún agente hace commits sin que él lo pida).
- Verificación:
  ```bash
  ./gradlew :app:compileDebugKotlin -q
  ./gradlew :app:testDebugUnitTest
  cd pwa && npx tsc -b --noEmit && node scripts/check-fold.ts && node scripts/check-invite.ts && node scripts/check-next.ts && node scripts/check-week.ts
  ```

### A1. Android: pantalla de Planes (agente normal, paralelo con B, C, D; 3 a 4 h)
- Objetivo: ver el plan, pedir Ruma Plus, empezar la prueba, cancelar; estado «pago en revisión».
- Archivos: nuevo `ui/screens/generator/GeneratorPlansScreen.kt` (con su ViewModel dentro, como hace Invite);
  `data/Dtos.kt`, `data/MakiRepository.kt`, `navigation/GenRoutes.kt`, `navigation/GeneratorRoot.kt`,
  `ui/screens/generator/GeneratorProfileScreen.kt` (una fila).
- Pasos: leer el contrato en `supabase/sql/2026-11-12_planes.sql` (`my_plan`, `request_plan`, `start_plan_trial`,
  `cancel_my_plan`); DTO + llamadas en el repositorio; pantalla con los componentes compartidos (`makiCard`,
  `NavRow`, `StatusChip`, `PrimaryButton`, `MakiLoading/Empty/Error`), un solo héroe; si `yape_number` es NULL,
  decir «se publicará pronto» (la solicitud igual se guarda); los `features` con estado `pronto` se muestran como tales.
  La app nunca decide un beneficio.
- Verificación: `./gradlew :app:compileDebugKotlin -q` y `./gradlew :app:lintDebug`.

### A2. Android: háptica y esqueletos del rider (agente normal, después de A1; 1 a 2 h)
- Objetivo: que el rider tenga lo mismo que ya tiene la familia.
- Archivos: `navigation/RiderRoot.kt`, `ui/screens/rider/*`.
- Pasos: `MakiSkeleton` donde hoy hay `MakiLoading` a pantalla completa; `HapticFeedbackType.LongPress` en tomar,
  llegar y completar recojo. Nada nuevo en `MakiUi.kt`.
- Verificación: `./gradlew :app:compileDebugKotlin -q` y `./gradlew :app:testDebugUnitTest`.

### B1. PWA: conectar «Invita y gana» (agente normal, paralelo; 1 a 2 h)
- Objetivo: que la pantalla exista para el usuario y que el enlace `?ref=` funcione.
- Archivos: `src/App.tsx`, `src/lib/api.ts` (`signUp`), `src/screens/auth/Auth.tsx`, `src/screens/family/Family.tsx`,
  `src/screens/family/Invite.tsx`, `src/lib/referral.ts`.
- Pasos: ruta de `Invite` en `App.tsx`; llamar a `captureRef(location.search)` desde la entrada de la app (hoy
  vive en un módulo que nadie importa); `signUp` envía `referral_code` en los metadatos solo para el rol generador
  (igual que `MakiApi.signUp`) y luego `clearRef()`; campo de código en el registro, precargado con `savedRef()`;
  fila en el Perfil y la tarjeta del inicio que `Invite.tsx` ya exporta.
- Verificación: `cd pwa && npx tsc -b --noEmit && node scripts/check-invite.ts && npm run build:staging`.

### B2. PWA: pantalla de Planes (después de B1; 3 h)
- Objetivo y reglas: los de A1.
- Archivos: nuevo `src/screens/family/Plans.tsx`; `src/lib/api.ts`, `src/App.tsx`, `src/screens/family/Family.tsx`.
- Verificación: `cd pwa && npx tsc -b --noEmit && npm run build:staging`.

### B3. PWA: esqueletos y vibración del rider (después de B2; 1 h)
- Archivos: `src/screens/rider/Work.tsx`, `src/screens/rider/Records.tsx`.
- Verificación: `cd pwa && npx tsc -b --noEmit`.

### C1. Documentos que faltan y encabezados (agente normal, paralelo; 2 h)
- Objetivo: que cada SQL cite un documento que existe.
- Archivos: nuevos `docs/referidos.md`, `docs/planes-de-pago.md`, `docs/navegacion-fluida.md`; `docs/push-setup.md`
  (paquete `pe.ruma.app`, ver D1); encabezado de `supabase/sql/2026-11-12_push_notifications.sql`.
- Pasos: escribir cada documento a partir del encabezado del SQL y del código que ya existe (reglas, cifras,
  cómo probar, qué falta para producción). No inventar cifras: las de la base son las de la sección 1.
- Verificación: `grep -n "docs/" supabase/sql/2026-11-12_*.sql` y comprobar que cada ruta existe.

### C2. Pruebas contra STAGING (después de C1; un solo ejecutor, una prueba a la vez; 1 h)
- Archivos: `supabase/tests/*` solo si una prueba necesita arreglo.
- Verificación:
  ```bash
  python supabase/tests/planes_staging.py        # espera 21/21
  python supabase/tests/referidos_staging.py     # espera 13/13
  python supabase/tests/canje_seguro_staging.py  # espera 5/5 (planes redefine validate_redemption)
  python supabase/tests/push_staging.py          # sin secretos termina con un PENDIENTE; con O1 hecho, tres comprobaciones más
  node --test supabase/functions/send-push/webpush.test.mjs
  ```
- Si algo falla, se anota el fallo exacto; cambiar la base es otro paquete, con SQL fechado y revisado.

### D1. Panel: activar y rechazar pagos de planes (agente normal, paralelo; 2 a 3 h)
- Objetivo: que el operador vea las solicitudes `pending_payment` y las active (`activate_plan_subscription`) o
  rechace (`reject_plan_payment`). Sin esto, el pago manual por Yape no tiene quién lo cierre.
- Archivos: `dashboard/app/(panel)/planes/page.tsx` (nuevo), `dashboard/lib/queries.ts`, el menú en
  `dashboard/app/(panel)/layout.tsx`.
- Verificación: `cd dashboard && npx tsc --noEmit && npm run build`.

### O1. Secretos de push (DUEÑO; ningún agente: el sistema bloquea esas escrituras; 30 a 45 min)
- Pasos, tal como están en `docs/push-setup.md`: crear el proyecto de Firebase; descargar la clave de la cuenta
  de servicio fuera del repo; `npx supabase login`; `bash scripts/setup-push-secrets.sh --dry-run <clave.json>` y
  luego sin `--dry-run`.
- La clave de la cuenta de servicio no depende del nombre del paquete: se puede hacer hoy. Web Push (PWA) queda
  funcionando con solo esto más `npm run build:staging` y publicar.
- Verificación: la consulta `{"health":true}` del paso 5 del documento debe dar `200` y `{"fcm":true,"webpush":true}`;
  después, `python supabase/tests/push_staging.py`.

### O2. Datos de Yape y precios (DUEÑO; 10 min)
- Un `update public.plan_payment_config set yape_number = …, yape_holder = …, yape_qr_url = …, business_contact = …`
  en el SQL Editor de STAGING, y confirmar o cambiar los precios de `plans`.

### V1. Verificación en el teléfono (dueño con un agente al lado; después de A, B, C, D y O1; 1 h)
- `.\gradlew.bat :app:installDebug`; pasos 3 y 4 de «Comprobar» en `docs/push-setup.md`; recorrer Invita y gana,
  Planes y las transiciones. El push de Android se prueba después del renombrado (ver D1).

## 3. Renombrado MAKI → Ruma (al final, en serie, un solo ejecutor, nadie más en el repo; 4 a 5 h)

Condiciones de entrada: todos los paquetes anteriores cerrados, builds en verde, y el dueño con un commit del
estado previo. Cada paso termina compilando antes de empezar el siguiente.

### Qué NO se renombra
- La carpeta `D:\proyects0_0\Maki_app\MAKI`: la renombra el dueño al final. Al hacerlo cambia también la ruta de
  la memoria de Claude (`...\projects\D--proyects0-0-Maki-app-MAKI\memory`): hay que copiarla a la carpeta nueva.
- Los proyectos de Supabase `MAKI_APP_STAGING` / `MAKI_APP_PRODUCTION` y sus refs.
- Los scripts ya aplicados de `supabase/sql/` (son el registro de lo que se ejecutó) y el historial de git.
- `presentacion/`, `entrevistas/`, `docs/costos/` y los documentos de investigación: historia del proyecto.
- Las clases CSS `maki-*` y las claves `maki.modulos.callcenter` / `maki:ajustes` del panel: opcionales, sin efecto
  para el usuario; si se cambian, en un paso aparte.
- Identificadores `Ruma*` que ya existen en Android (`RumaArt`, `RumaCard`, `RumaLevel`, `RumaLogo`): revisar antes
  que ningún `Maki*` renombrado choque con ellos (hoy no hay `MakiArt`, `MakiCard`, `MakiLevel` ni `MakiLogo`).

### R1. Paquete Android → `pe.ruma.app` (1 h)
- `git mv` de `app/src/{main,test,androidTest}/java/com/example/maki` a `.../java/pe/ruma/app`.
- `app/build.gradle.kts`: `namespace` y `applicationId`. Reemplazo exacto de `com.example.maki` → `pe.ruma.app`
  en las 90 fuentes de `app/src` (`package`, `import`, y los nombres completos sueltos como los de `GeneratorRoot.kt:191`).
- Verificación: `./gradlew clean :app:compileDebugKotlin -q` y
  `grep -rn "com\.example\.maki" app settings.gradle.kts` sin resultados.

### R2. Identificadores `Maki*` → `Ruma*` (1 a 1,5 h)
- Unas 2.800 apariciones de 36 identificadores (`MakiColors` 942, `MakiSpace` 566, `MakiType` 399…). Reemplazo
  con límite de palabra y mayúsculas exactas: `\bMaki(?=[A-Z])` → `Ruma`, `MAKITheme` → `RumaTheme`,
  `Theme.MAKI` → `Theme.Ruma` (`res/values/themes.xml` y las dos líneas del manifiesto).
- Renombrar los archivos (`MakiApp.kt`, `MakiApi.kt`, `MakiRepository.kt`, `MakiPrefs.kt`, `MakiPush.kt`,
  `MakiUi.kt`, `MakiScaffold.kt`, `MakiList.kt`, `MakiDesign.kt`…) con `git mv`. En el manifiesto: `.MakiApp` y
  `.data.MakiPushService`. `settings.gradle.kts`: `rootProject.name`.
- Cadenas: `"maki"` (nombre de las preferencias), `"maki_eco_tips"`, `"maki_user_agent"`, `"MakiOsm"`. Se pueden
  cambiar sin migración porque la app nueva empieza sin datos.
- Verificación: `./gradlew :app:compileDebugKotlin -q`, `./gradlew :app:testDebugUnitTest`,
  `./gradlew :app:lintDebug` y `grep -rniE "maki" app/src` sin resultados.

### R3. `maki-assistant` → `ruma-assistant` (30 min de agente, más el despliegue)
- `git mv supabase/functions/maki-assistant supabase/functions/ruma-assistant`; cambiar el slug en
  `MakiRepository` (ya `RumaRepository`) y en `pwa/src/lib/api.ts:241`.
- Desplegar la nueva en STAGING **sin borrar la vieja**: `npx supabase functions deploy ruma-assistant --project-ref mbbdrmicjobydprvbpsj`.
  Los secretos son del proyecto, no de la función: no hay que volver a cargarlos.
- La vieja se borra solo cuando la PWA publicada y el APK instalado ya usen la nueva, y la borra el dueño.
- Verificación: abrir el chat de Rumi en la PWA de staging recién publicada y en el APK nuevo.

### R4. Cuentas `@maki.test` → `@ruma.test` en STAGING (1 h; el SQL lo ejecuta el dueño o un agente con su visto bueno)
- Nuevo script fechado en `supabase/sql/` que actualiza `auth.users.email` y `auth.identities` (el correo dentro
  de `identity_data`) para las 16 cuentas `%@maki.test`, más cualquier copia del correo en `public`. Antes de
  escribirlo, mirar en solo lectura cómo guarda este proyecto la identidad de correo (`provider_id`).
- Decisión del dueño: renombrar también las 9 cuentas `ref-*@maki.test` de las pruebas o borrarlas. Renombrarlas
  todas con la misma regla es lo más simple.
- En el mismo paso, el código que las cita: los `ensureSignedIn()` de los dos ViewModels, `supabase/tests/*`
  (28 apariciones en 8 archivos) y `docs/`.
- Verificación: entrar con `generador@ruma.test` / `Maki12345!` (la clave no cambia) y volver a correr las cinco
  pruebas de C2.

### R5. Documentos, memoria y barrido final (45 min)
- `CLAUDE.md` (quitar «the code keeps the MAKI names on purpose», comandos, nombres de clases, cuentas),
  `docs/push-setup.md`, comentarios de `pwa/` y de las Edge Functions (incluido el texto «de MAKI» del prompt de
  `detect-material`: cambiarlo obliga a subir `PIPELINE_VERSION` y redesplegar; si no se quiere, se deja).
- Memorias que citan `*@maki.test` o `com.example.maki`.
- Verificación: `grep -rniE "maki" --exclude-dir={node_modules,build,dist,.git,.gradle,.next,presentacion,entrevistas} .`
  y revisar que lo que quede esté en la lista «Qué NO se renombra».

### Qué cambia en el celular
- `pe.ruma.app` es **otra app** para Android: se instala al lado de la vieja y no hereda nada. Se pierden la
  sesión, el onboarding visto, la dirección guardada, el perfil en caché, los permisos (cámara, ubicación,
  notificaciones), las tareas de WorkManager y las fotos de `filesDir/dataset/` (copiarlas antes si importan).
- Hay que desinstalar la vieja a mano: `adb uninstall com.example.maki`.
- El token de push es nuevo: el teléfono se registra otra vez al iniciar sesión.
- Probar los mapas: osmdroid manda un user agent propio (`maki_user_agent`) y ya dio problemas una vez.
- Play Store no acepta `com.example.*`: este cambio es requisito para publicar.

## 4. Dependencias y decisiones del dueño

Dependencias:
- P0 antes de todo. A, B, C y D en paralelo. C2 después de C1. V1 después de A, B, C, D y O1.
- O1 no bloquea a ningún agente: el código funciona sin secretos (no envía nada).
- El renombrado (R1 a R5) va al final, solo y en serie. Ningún otro paquete corre mientras tanto.
- Producción: nada de este plan la toca. La réplica (planes → referidos → push, en ese orden, que es el que
  exigen los encabezados) es un paquete posterior, después de V1.

Decisiones y tareas del dueño:
- **D1. Firebase y el nombre del paquete.** Registrar la app Android en Firebase como **`pe.ruma.app`**, no como
  `com.example.maki`, y guardar `app/google-services.json` **después** de R1. Si el archivo existe con otro
  paquete, el build de Android falla («No matching client found for package name»). Alternativa: registrar los
  dos paquetes en el mismo proyecto de Firebase y poner el archivo ya.
- **D2. Secretos** (O1): cuenta de Firebase, clave de la cuenta de servicio, `setup-push-secrets.sh`.
- **D3. Yape** (O2): número, titular, QR y contacto del negocio.
- **D4. Precios de Ruma Plus:** hoy S/ 4,90 al mes, S/ 39,90 al año, 14 días de prueba. Y si un plan suma
  `referral_bonus_points` (hoy ninguno).
- **D5. Sorteo:** los boletos se cuentan pero figuran como «pronto» hasta que haya premio, fecha y bases.
- **D6. Premios de referidos:** 2.000 pts al que invita y 1.000 al invitado, tope 5 al mes por persona y 60 en total.
- **D7. Cuentas `ref-*` de prueba:** renombrarlas o borrarlas (R4).
- **D8. Vercel Hobby** no admite uso comercial: cobrar planes obliga a pasar a Pro o a otro alojamiento.
- Un commit antes del renombrado, y renombrar la carpeta al final.

## 5. Esfuerzo

| Paquete | Quién | Paralelo | Esfuerzo |
|---|---|---|---|
| P0 Congelar y confirmar | agente | no, primero | 15 min |
| A1 Planes Android | agente | sí | 3 a 4 h |
| A2 Rider Android: háptica y esqueletos | agente | tras A1 | 1 a 2 h |
| B1 Invita y gana en la PWA | agente | sí | 1 a 2 h |
| B2 Planes PWA | agente | tras B1 | 3 h |
| B3 Rider PWA | agente | tras B2 | 1 h |
| C1 Documentos y encabezados | agente | sí | 2 h |
| C2 Pruebas en STAGING | agente | tras C1, una a la vez | 1 h |
| D1 Panel: activar planes | agente | sí | 2 a 3 h |
| O1 Secretos de push | dueño | cuando quiera | 30 a 45 min |
| O2 Yape y precios | dueño | cuando quiera | 10 min |
| V1 Verificación en teléfono | dueño + agente | tras todo lo anterior | 1 h |
| R1 a R5 Renombrado | un agente, solo | no, al final | 4 a 5 h |

Con cuatro carriles en paralelo, el trabajo de agentes anterior al renombrado cabe en una tanda de unas 6 a 8 horas
de reloj (lo marca el carril B o el A); el renombrado es una segunda tanda.
