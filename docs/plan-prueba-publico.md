# Plan de prueba con público (2026-11-18)

Preparado el 2026-11-17 para el agente que lo ejecuta mañana. Todo corre contra **STAGING**
(`mbbdrmicjobydprvbpsj`). No se toca producción, ni hoy ni mañana.

- **Android:** `pe.ruma.app`.
- **PWA:** https://ruma-staging.vercel.app.
- **Panel:** `dashboard/`.

Reglas para mañana:

- Las pruebas de `supabase/tests/*.py` **escriben** en STAGING. Córrelas una a la vez y nunca mientras haya testers activos.
- Toda limpieza de datos (§2) necesita el **OK del dueño** antes del primer `update`/`delete`.
- Consulta de solo lectura, el patrón que se usa en todo el plan:
  ```bash
  npx supabase db query --remote=mbbdrmicjobydprvbpsj --read-only --agent no --output-format text "<sql>"
  ```
- Las escrituras SQL de limpieza van en el SQL Editor de STAGING, o con el mismo comando sin `--read-only`, y solo después del OK.

---

## 1. Flujo principal de punta a punta

| # | Paso | Actor | Qué hace | Qué debe verse | Qué debe guardarse (verificar con SQL) |
|---|---|---|---|---|---|
| 1 | Registro familia | Familia | Onboarding → dirección → crear cuenta como Eco-Hero | Home verde, saldo 0 pts, "Ruma" en todo el texto (nunca "MAKI") | `auth.users` + `profiles.role='generador'` + `generator_profiles` + `addresses` |
| 2 | Registro rider | Rider | Crear cuenta eligiendo Eco-Rider | **Toda la interfaz en azul** (PWA: `[data-role="rider"]`), pestañas de rider | `profiles.role='eco_rider'` + `rider_profiles` |
| 3 | Escaneo | Familia | Cámara → mover el celular (gate de giroscopio) → foto → "Tu material está siendo analizado por IA" → resultado → "Otra foto" si hace falta → confirmar | Material, cantidad, marca si se ve; con 3 botellas y luego "esas 3 + 1" el contador dice 4, no 7; una foto de una pantalla se rechaza | `detections` (`status='pending'`, `provenance`) + `detection_items` (marca en jsonb). Solo los escribe `detect-material` |
| 4 | Pedir recojo | Familia | Desde el resultado o desde Home → franja horaria → pedir | Tarjeta "Buscando Eco-Rider" / seguimiento | `pickups.status='requested'` + `pickup_items` (`reported_quantity`, `condition='limpia'`) |
| 5 | Despacho | Sistema → rider | `rpc/assign_nearest_rider` (rider disponible ≤ 20 km) o tablero abierto | Rider: "Nuevo recojo asignado"; familia: "X recogerá tu reciclaje" | `pickups.rider_id`, `status='assigned'`, filas en `notifications` para ambos |
| 6 | En camino | Rider | Iniciar ruta → navegación por calles | Familia ve el punto del rider moverse (polling) y la ETA | `status` `en_route` → `arriving`; `rider_profiles.last_location_at` fresco |
| 7 | En la puerta | Rider | Confirmar cantidad y **estado** por material: limpia / aplastada / sucia (con líquido) → completar | Pantalla de completado; familia: "Recojo completado", puntos "pendientes" | `pickup_items.confirmed_quantity`, `pickup_items.condition`; `status='completed'` vía `rpc/complete_pickup`; `generator_profiles.points_pending` sube |
| 8 | Entrega al acopio | Rider | "Entregar en acopio" → Centro de Acopio Cartavio | Entrega en camino | `rpc/create_delivery` → `deliveries.status='en_camino'` + `delivery_pickups` |
| 9 | Pesaje | Operador (`operador@ruma.test`, panel `dashboard/`) | Recibir → pesar kg por material → liquidar | Entrega `liquidada` en el panel | `deliveries` `recibida` → `pesada` → `liquidada`, `total_kg`, `generator_points`, `rider_amount` |
| 10 | Liquidación | Sistema (`settle_delivery`) | — | Familia: "¡Tus Eco-Puntos ya están disponibles!"; rider ve su pago | Ver la regla abajo; `point_transactions`, `rider_earnings`, `generator_profiles.points_balance` |
| 11 | Canje | Familia | Canjear (Yape / donación / arbitrios) | Saldo baja; canje "pendiente" | `redemptions` (la BD fija `kind`, `monetary_value`, `status`) |
| 12 | Referidos | Familia | Compartir código `?ref=XXXX`; un amigo se registra con él | Lista de invitados: Pendiente / Pagado / El próximo mes | `referral_codes`, `referrals` (paga recién cuando el invitado completa y se pesa) |
| 13 | Planes | Familia | Ver Ruma Plus / prueba gratis | Estado del plan | `plan_subscriptions` |
| 14 | Notificaciones | Ambos | — | Avisos **dentro de la app** en cada paso. **Push no llega**: faltan los secretos (ver `docs/push-setup.md`) | `notifications` sí; `push_tokens` = 0 hoy, `push_queue` sin enviar |

**Regla de liquidación desde el 2026-11-17** (`supabase/sql/2026-11-17_puntos_estado_pago_mixto.sql`; los valores están en `platform_settings`):

- **Puntos de la familia:**
  - Base: unidades contadas en la puerta × `materials.points_per_unit` × factor de estado. El factor es limpia 1,00, aplastada 1,10 y sucia 0,50.
  - Tope: la parte del peso que toca a ese material × `points_per_kg` × 1,25.
  - Bono por recojo: +10 % desde 30 unidades y +20 % desde 80.
- **Pago del rider:**
  - S/ 0,35 por kg pesado, que se paga al liquidar y ya no al completar.
  - Más S/ 6,00 por una entrega con 12 recojos o más.
  - `pickups.rider_earnings` es solo una estimación hasta que se pesa.
- **Prueba manual de la regla:** un recojo con 30 PET limpias debe llevar el +10 %. Calcula a mano lo que debe salir y compáralo con `point_transactions`.

---

## 2. Datos actuales de STAGING (leídos el 2026-11-17, solo lectura)

**Qué hay:**

- **Recojos:**
  - 1 `requested`: `PU-AFBBA095`, del 2026-11-09, de `generador@ruma.test` (id `1111…`).
  - 78 `completed` y 33 `cancelled`.
- **Entregas:** 5 `en_camino`, 1 `recibida`, 2 `pesada` sin liquidar y 20 `liquidada`.
- **Canjes:** 18 `pending` y 3 `completed`.
- **Cuentas (25):**
  - Seed `*@ruma.test`: generador, torres, quispe, rojas, rider, lucia y operador.
  - 15 cuentas `ref-2026111…-{x,y,z}@ruma.test`, que crea `referidos_staging.py` en cada corrida.
  - 3 cuentas reales: `gabriel.sampen@tecsup.edu.pe`, `grissel.llican@tecsup.edu.pe` (rider) y `aliciayachib@gmail.com`. Esta última está **sin confirmar el correo**: es la señal de que la confirmación bloquea el registro.
- **Centros:** Centro de Acopio Cartavio (`CA-CARTAVIO`, abierto), SJL Norte, EcoPunto Mall SJL y Recicladora Los Olivos (cerrado).
- **Precios (`v_material_prices`):** PET 1,00, ALU 4,00, VID 0,20, CAR 0,60 y PIL 0,00 S/ por kg.
- **Visión (`vision_provider_status`):**
  - En verde: `gemini-lite`, `gemini-lite-latest` y `gemini-3.5-lite`. El último OK fue el 2026-11-12.
  - `groq-qwen`: su último estado fue 429 (el 2026-11-06), con el cooldown ya vencido.
  - `groq-scout` (404, modelo retirado), `gemini` (timeout) y `groq-qwen36` (400) son viejos, de septiembre.
- **Push:** `push_tokens` = 0.

**Qué estorbaría a los testers:**

1. **El recojo abierto `PU-AFBBA095`.** Un tester rider lo vería en el tablero abierto, a nombre de la familia seed.
2. **Las 5 entregas `en_camino` y las 2 `pesada`.** El operador las ve en el panel y puede confundirlas con las de los testers.
3. **Las cuentas `ref-*` y los saldos inflados del seed.** Ensucian rankings y tableros si estos son visibles para todos.
4. **Las riders seed disponibles cerca de Cartavio** (`rider@`, `lucia@`). `assign_nearest_rider` podría darle a una cuenta seed el recojo de un tester, y nadie lo atendería.

**Consultas exactas** (todas de solo lectura):

```sql
-- a) recojos abiertos
select id, code, status, generator_id, rider_id, requested_at from pickups where status not in ('completed','cancelled','failed');
-- b) entregas sin liquidar
select id, code, status, rider_id, center_id, created_at from deliveries where status <> 'liquidada' order by created_at;
-- c) cuentas y confirmación
select u.email, p.role, u.email_confirmed_at is not null conf, u.last_sign_in_at from auth.users u join profiles p on p.id=u.id order by u.created_at;
-- d) saldos
select u.email, g.points_balance, g.points_pending, g.points_lifetime from generator_profiles g join auth.users u on u.id=g.profile_id order by g.points_balance desc;
select u.email, r.is_available, r.earnings_balance, r.last_location_at, r.current_latitude, r.current_longitude from rider_profiles r join auth.users u on u.id=r.profile_id;
-- e) operador de Cartavio
select u.email, o.center_id, c.name from operator_profiles o join auth.users u on u.id=o.profile_id join collection_centers c on c.id=o.center_id;
-- f) visión y precios
select provider, last_status, last_ok_at, cooldown_until from vision_provider_status order by last_ok_at desc nulls last;
select * from v_material_prices;
-- g) ajustes de liquidación
select condition_crushed_factor, condition_dirty_factor, weight_cap_factor, bulk_tier1_units, bulk_tier1_bonus, bulk_tier2_units, bulk_tier2_bonus, rider_per_kg, rider_route_base, rider_route_min_stops from platform_settings;
```

**Limpieza propuesta.** Nada se ejecuta sin el OK del dueño.

| # | Acción | SQL | ¿Confirma el dueño? |
|---|---|---|---|
| L1 | Cancelar el recojo viejo | `update pickups set status='cancelled', cancel_reason='limpieza prueba publico', cancelled_at=now() where code='PU-AFBBA095';` (desde el SQL Editor; `trg_pickup_guard` solo frena a `authenticated`) | **Sí** |
| L2 | Las 5 entregas `en_camino` y las 2 `pesada` | Liquidarlas o rechazarlas desde el panel como operador (así se respeta `settle_delivery`). No borrarlas | **Sí**: él decide si liquidar o rechazar |
| L3 | Riders seed fuera del despacho | `update rider_profiles set is_available=false where profile_id in (select id from auth.users where email in ('rider@ruma.test','lucia@ruma.test'));`. Volver a `true` cuando el agente corra las pruebas | **Sí** |
| L4 | Cuentas `ref-*` | Dejarlas. Borrarlas arrastra libros contables; basta con que no aparezcan en rankings (verificarlo en la app) | Informativo |
| L5 | Los 18 canjes `pending` | Dejarlos; son de pruebas. Solo avisar al dueño de que nadie debe pagarlos | Informativo |
| L6 | Cuenta sin confirmar `aliciayachib@gmail.com` | Confirmarla a mano en Auth → Users si es una tester real | **Sí** |

---

## 3. Pruebas automáticas existentes

Las de este grupo **no escriben**. Se pueden correr en cualquier momento.

| Orden | Comando (desde la raíz o desde `pwa/`) | Cubre |
|---|---|---|
| A1 | `cd pwa && node scripts/check-fold.ts` | Cómo se suman varias fotos (MAX dentro de una foto, una marca repetida no duplica) |
| A2 | `cd pwa && node scripts/check-next.ts` | La tarjeta "siguiente paso" del Home |
| A3 | `cd pwa && node scripts/check-week.ts` | La meta semanal y la racha en hora de Lima |
| A4 | `cd pwa && node scripts/check-invite.ts` | El texto y el enlace de los referidos (nunca habla de soles) |
| A5 | `node --test supabase/tests/vision-measurement.test.mjs` (requiere `npm install --no-save @electric-sql/pglite`) | El contrato de `commit_detection` y su procedencia, en PGlite local |
| A6 | `node supabase/tests/e2e-staging-chain.mjs --dry-run` | El plan de la cadena, sin red |
| A7 | `.\gradlew.bat :app:testDebugUnitTest` | Las pruebas JVM de Android (RoutePlanner, NextStep, pileTally…) |

Las de este otro grupo **escriben en STAGING**. Van una a la vez, antes de abrir la prueba a los testers y con las riders seed disponibles (revertir L3 mientras corren):

| Orden | Comando | Cubre | Escribe |
|---|---|---|---|
| B1 | `python supabase/tests/seguridad_cantidades_staging.py` | Guardias de `pickup_items`, reparto de kg por conteo en la puerta | Recojos de prueba + 1 entrega liquidada |
| B2 | `python supabase/tests/puntos_estado_staging.py` | **Regla nueva**: estado × unidad, tope por peso, bonos 30/80, rider por kg + base de ruta | 14 recojos + 2 entregas liquidadas (paga al rider seed) |
| B3 | `python supabase/tests/canje_seguro_staging.py` | `validate_redemption` | 1 donación de 200 pts |
| B4 | `python supabase/tests/arbitrios_porcentaje_staging.py` | Tope anual y 5 % de arbitrios | Hasta 2.750 pts de canje |
| B5 | `python supabase/tests/referidos_staging.py` | Referidos de punta a punta | 3 cuentas `ref-*`, 3 recojos, 1 entrega; toca `referral_monthly_cap` y lo restaura |
| B6 | `python supabase/tests/planes_staging.py` | Planes, prueba gratis, vencimiento | Suscripciones, 1 canje Yape, 3 recojos cancelados |
| B7 | `python supabase/tests/push_staging.py` | Cola push y reglas (sin secretos, el envío real falla: es lo esperado) | Tokens y avisos de prueba que borra al final |
| B8 (opcional) | `node supabase/tests/e2e-staging-chain.mjs` | La cadena HTTP real: recojo → despacho → entrega → panel | Deja el recojo y la entrega para inspección; **aborta si el despacho elige a otro rider** |

Si una prueba falla, no se corre la siguiente. Se lee el mensaje y se avisa al dueño.

**Huecos que ninguna prueba cubre** (los cubre el §4):

- `detect-material` real con una foto: IA, marca y antispoof. Ninguna prueba automática llama al modelo.
- El registro por la interfaz con confirmación de correo. Las pruebas crean las cuentas por SQL.
- El pesaje y la liquidación desde la interfaz del panel `dashboard/`.
- La pantalla del rider para marcar `condition`, en Android y en la PWA.
- La entrega real de push, y la cámara y el permiso de movimiento en iOS.
- La concurrencia de más de 3 escaneos a la vez.

---

## 4. Checklist manual en celular

Equipos:

- **Galaxy A12 (Android)** con adb. Desde Git Bash, con `MSYS_NO_PATHCONV=1`.
- **iPhone (Safari)** con la PWA agregada a la pantalla de inicio.
- **PC** con el panel `dashboard/` (`npm run dev` en `dashboard/`) como `operador@ruma.test`.

Para ver lo que se guarda, corre las consultas a–g del §2 después de cada bloque.

| # | Pantalla | Android (A12) | iPhone (PWA) | Esperado |
|---|---|---|---|---|
| 1 | Instalar | `adb uninstall com.example.maki` (la app vieja) y luego `.\gradlew.bat :app:installDebug` | Abrir la URL → Compartir → "Agregar a inicio" | Un solo ícono "Ruma"; la PWA muestra la marca STAGING |
| 2 | Onboarding + registro familia | Cuenta nueva con un correo real | Cuenta nueva con otro correo | Llega el correo de confirmación (o se confirma a mano, §6); entra al Home verde |
| 3 | Registro rider | Segunda cuenta como Eco-Rider | Ídem | Interfaz **azul**, pestañas de rider |
| 4 | Cámara: permiso | Aceptar la cámara | Aceptar la cámara **y el permiso de movimiento, que se pide con un toque** | Sin pantalla negra; si se niega, aparece un mensaje claro |
| 5 | Escaneo con botellas reales | 3 botellas PET de una marca visible (Coca-Cola, San Luis…) moviendo el celular | Ídem | Cantidad 3, marca correcta, respuesta en menos de ~10 s |
| 6 | "Otra foto" | Las mismas 3 + 1 | Ídem | El total es 4, no 7 |
| 7 | Antifraude | Fotografiar una foto de botellas en otra pantalla | Ídem | Rechazo con mensaje en español |
| 8 | Sin movimiento | Celular quieto sobre la mesa | Ídem | Pide mover el celular; no envía la foto |
| 9 | Corregir cantidad | Pasos por marca en el resultado | Ídem | Cambia `reported_quantity`, no cambia los puntos |
| 10 | Pedir recojo | Pedir con franja | Ídem | `pickups.requested` → asignado a la cuenta rider del tester (con las seed apagadas, L3) |
| 11 | Seguimiento | La familia ve al rider moverse | Ídem (con la PWA abierta: iOS no rastrea en segundo plano) | El punto se mueve, ETA, avisos en la app |
| 12 | Rider: navegación | Iniciar ruta, instrucciones y voz | Ídem | Ruta por calles (OSRM); sin red, línea recta |
| 13 | Rider: en la puerta | Cantidad + estado (limpia / aplastada / sucia) | Ídem | `pickup_items.condition` guardado; completar funciona |
| 14 | Rider: entregar | "Entregar en acopio" → Cartavio | Ídem | `deliveries.en_camino` |
| 15 | Panel: pesar | — (PC) | — | Recibir → pesar → liquidar; puntos = cálculo manual (§1) |
| 16 | Familia: puntos | Saldo disponible sube | Ídem | Aviso "¡Tus Eco-Puntos ya están disponibles!" |
| 17 | Canje | Canje mínimo | Ídem | El saldo baja; el canje queda `pending` |
| 18 | Referidos | Compartir el código, abrir el enlace en el otro celular | Ídem | El código llega prellenado al registro |
| 19 | Chat Rumi | Preguntar "¿dónde boto pilas?" | Ídem | Responde en español con contexto |
| 20 | Mercado Vivo | Abrir la pestaña | Ídem | Precios = `v_material_prices` |
| 21 | Cerrar sesión / volver a entrar | — | — | No cae en una cuenta seed (`ensureSignedIn` es un atajo de demo: verificar que no se active con una sesión real) |

---

## 5. Guía para los testers (copiar y enviar)

> **¡Gracias por probar Ruma!** ♻️
> Ruma te paga por reciclar: escaneas tus botellas y latas con la cámara, un Eco-Rider pasa a recogerlas y ganas puntos.
>
> **Esto es una prueba:** los puntos y pagos son de mentira, no valen dinero real ni se cobran. Puedes borrar todo cuando quieras.
>
> **1. Instala**
> • Android: abre este enlace e instala el APK: [enlace del APK]
> • iPhone: abre https://ruma-staging.vercel.app en **Safari** → botón Compartir → "Agregar a pantalla de inicio".
>
> **2. Crea tu cuenta** con tu correo. Si te llega un correo de confirmación, ábrelo. Si no te llega en 5 minutos, escríbenos.
> Elige **Familia** (reciclas en casa) o **Eco-Rider** (recoges, pantalla azul); te diremos cuál te toca.
>
> **3. Prueba esto (Familia)**
> 1. Toca escanear, acepta la cámara (en iPhone también "movimiento") y mueve un poco el celular.
> 2. Toma una foto de 2 o 3 botellas o latas.
> 3. Pide el recojo.
> 4. Sigue al Eco-Rider en el mapa.
>
> **Si eres Eco-Rider:** activa tu disponibilidad, acepta el recojo, ve hasta la dirección y marca la cantidad y el estado (limpia, aplastada o con líquido).
>
> **4. Si algo falla**, mándanos por WhatsApp:
> • una captura de pantalla;
> • qué estabas haciendo;
> • tu celular (modelo) y la hora.
> Nada es "error tuyo": si algo te confunde, eso también nos sirve.

**Qué medir** (el dueño o el agente, en una hoja):

- **Tiempo hasta el primer escaneo**, desde que se abre el enlace. Sale de `auth.users.created_at` contra la primera `detections.created_at` de cada tester:
  ```sql
  select u.email, u.created_at, min(d.created_at) primer_escaneo, min(d.created_at)-u.created_at demora
  from auth.users u left join detections d on d.generator_id=u.id
  where u.created_at > '2026-11-18' group by 1,2 order by 2;
  ```
- **Dónde se pierden.** Es el embudo de quienes se registraron → escanearon → pidieron recojo → completaron; se cuenta por tester con `detections` y `pickups`.
- **Errores**:
  - Los reportes de WhatsApp.
  - `vision_provider_status` (429 o timeouts).
  - Los logs de las Edge Functions en el dashboard de Supabase.
  - Los recojos `requested` que llevan más de 15 minutos sin asignar.

---

## 6. Riesgos y bloqueantes

| Riesgo | Señal | Qué hacer |
|---|---|---|
| **La IA gratis aguanta ~3 escaneos a la vez** | Escaneos lentos, error "intenta de nuevo", 429 en `vision_provider_status` | Escalonar a los testers por tandas de 3–5 personas cada 15 min. Si se cae, avisar "reintenta en 1 minuto". Solución de fondo (decide el dueño): activar Gemini de pago (~$0,002 por escaneo) |
| **Confirmación de correo** bloquea el registro | Cuentas con `email_confirmed_at` nulo (ya pasa con `aliciayachib@gmail.com`) | Opción A (dueño): apagar "Confirm email" en Auth de STAGING solo durante la prueba. Opción B: confirmar a mano en Auth → Users al ver cada registro. Además, el SMTP por defecto de Supabase limita los correos por hora |
| **Vercel Hobby** (no comercial, con límites de ancho de banda) | La PWA no carga | Aceptable para una prueba gratuita. Si cae, `npm run dev -- --host` en una PC no sirve para iPhones externos: tener el APK como plan B |
| **La app vieja `com.example.maki`** en celulares | Dos íconos, o llama a `maki-assistant` | `adb uninstall com.example.maki`; decirles a los testers que la desinstalen. No borrar `maki-assistant` mañana |
| **Push sin secretos** | No llegan notificaciones con la app cerrada | Ya se sabe: avisarles a los testers que **mantengan la app abierta** durante el recojo. Los avisos dentro de la app sí funcionan. Cargar los secretos solo si el dueño lo hace (`docs/push-setup.md`) |
| **Riders seed** que se llevan los recojos de los testers | Recojo asignado a `rider@`/`lucia@` | Aplicar L3. Si ya pasó, el rider seed lo libera (`rpc/release_pickup`) iniciando sesión con esa cuenta |
| **Testers lejos de Cartavio** | `assign_nearest_rider` devuelve null (más de 20 km) | El recojo queda en el tablero abierto; un rider tester lo reclama |
| **iOS** | Sin ubicación en segundo plano, permiso de movimiento | Pedir que la PWA quede abierta; el permiso se otorga con un toque |
| **Pruebas B que corren durante la prueba** | Recojos y entregas extraños en el panel | Correr las B solo antes de abrir la prueba |

---

## 7. Orden de ejecución para el agente de mañana

1. **Leer** este plan, `CLAUDE.md` y `docs/push-setup.md`.
   - Listo cuando: conoces las reglas de STAGING.
2. **Estado inicial**: correr las consultas a–g del §2 con el comando `--read-only` y comparar con los números del §2.
   - Listo cuando: las diferencias están anotadas.
3. **Pruebas sin escritura**:
   ```bash
   cd pwa && node scripts/check-fold.ts && node scripts/check-next.ts && node scripts/check-week.ts && node scripts/check-invite.ts
   ```
   Después, `.\gradlew.bat :app:testDebugUnitTest` desde la raíz.
   - Listo cuando: todo sale `ok` y `BUILD SUCCESSFUL`.
4. **Pruebas que escriben** (B1 → B7), una a la vez desde la raíz y antes de que entre ningún tester:
   ```bash
   python supabase/tests/seguridad_cantidades_staging.py
   python supabase/tests/puntos_estado_staging.py
   python supabase/tests/canje_seguro_staging.py
   python supabase/tests/arbitrios_porcentaje_staging.py
   python supabase/tests/referidos_staging.py
   python supabase/tests/planes_staging.py
   python supabase/tests/push_staging.py
   ```
   - Listo cuando: cada una termina sin `FAIL` ni traceback. Si una falla, **parar** y avisar al dueño.
5. **Salud de la visión**: `select provider,last_status,last_ok_at,cooldown_until from vision_provider_status order by last_ok_at desc nulls last;`. Después, un escaneo real desde el A12.
   - Listo cuando: algún `gemini-*` tiene `last_ok_at` de hoy y el escaneo respondió.
6. **Limpieza** (§2, L1–L3 y L6): mostrar el SQL al dueño, esperar su OK y ejecutarlo. Releer las consultas a y b.
   - Listo cuando: 0 recojos abiertos, las entregas viejas resueltas como decidió el dueño y las riders seed en `is_available=false`.
7. **Confirmación de correo**: el dueño elige la opción A o B del §6.
   - Listo cuando: un registro nuevo desde el iPhone entra al Home.
8. **Instalar** en el A12:
   ```bash
   MSYS_NO_PATHCONV=1 adb uninstall com.example.maki
   ./gradlew :app:assembleDebug :app:installDebug
   ```
   Publicar el APK de `app/build/outputs/apk/debug/` donde diga el dueño.
   - Listo cuando: hay un solo ícono Ruma y el APK está compartible.
9. **Checklist manual** del §4, filas 1–21, con el A12, el iPhone y el panel.
   - Listo cuando: cada fila está marcada OK o tiene su falla anotada con una captura.
10. **Enviar** la guía del §5 con el enlace del APK.
    - Listo cuando: el dueño la envió.
11. **Durante la prueba**, cada 30 min:
    - correr la consulta de "qué medir" del §5 y `vision_provider_status`;
    - buscar recojos `requested` de más de 15 min: `select code, requested_at from pickups where status='requested' and requested_at < now()-interval '15 min';`.
    - Listo cuando: los incidentes están anotados y respondidos.
12. **Al cierre**:
    - Resumir el embudo, los tiempos y las fallas.
    - Volver a poner las riders seed en `is_available=true` si el dueño quiere.
    - No borrar datos de testers sin su OK.
    - Listo cuando: el resumen está entregado al dueño.
