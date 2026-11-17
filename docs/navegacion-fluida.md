# Navegación fluida y enganche del inicio

Estado al 2026-11-16. Describe lo que existe en el código. Hecho para la **familia**; el rider queda pendiente
(paquetes A2 y B3 de `docs/plan-de-trabajo.md`). Nada de esto se ha verificado todavía en un teléfono.

## Objetivo

Que pasar de una pantalla a otra se sienta continuo, que nunca haya una pantalla en blanco mientras carga, y que el
inicio diga **una sola cosa** que vale la pena hacer ahora, sin presión ni culpa. Nada de esto paga puntos.

## Transiciones entre pantallas

**Android** (`navigation/NavExt.kt`): `rumaEnter`, `rumaExit`, `rumaPopEnter` y `rumaPopExit`, aplicadas al NavHost de
`AppRoot`, `GeneratorRoot` y `RiderRoot`.
- Hacia adelante: fundido + deslizamiento horizontal de 24 dp (entra 260 ms, sale 200 ms).
- Hacia atrás: el mismo movimiento en sentido contrario.
- Entre dos pestañas de la barra inferior: solo fundido, porque son hermanas, no un nivel más profundo.
- Las duraciones las escala el ajuste del sistema «Quitar animaciones» (Compose usa el animador del sistema).

**PWA** (`src/lib/flow.ts`, estilos en `src/styles.css`, sección «Navegación fluida»):
- `useTransitionLocation()` en `App.tsx` retrasa un instante la ubicación que dibuja `<Routes>` para que la API
  View Transitions fotografíe la pantalla vieja; `data-nav` en `<html>` dice si se va hacia adelante o atrás.
- Misma curva: salida 200 ms, entrada 260 ms, 24 px.
- Sin la API, o con `prefers-reduced-motion`, cambia al instante; sin View Transitions, un fundido corto de `main`
  (solo opacidad, para no despegar la barra inferior fija).
- La barra de pestañas (`.tabbar`) y la etiqueta de entorno tienen su propio `view-transition-name` y no se deslizan.
- `returnHome` y `goBack` evitan que «Atrás» reabra la cámara o el formulario después de pedir un recojo, y llevan al
  inicio del rol cuando la pantalla se abrió sin historial (notificación, enlace compartido).

## Esqueletos de carga

- Android: `RumaSkeleton(heights, label)` en `ui/components/RumaScaffold.kt`, con un pulso de opacidad de 800 ms.
  Usado en 7 pantallas de la familia: historial, inicio, recojo, recojos, rankings, seguimiento y wallet.
- PWA: `Skeleton({ blocks, label })` en `components/ui.tsx`, usado en las pantallas de `Family.tsx` (inicio, wallet,
  historial, ranking, perfil) y en `Pickup.tsx`.
- Los esqueletos llevan descripción para lectores de pantalla y respetan la reducción de movimiento.
- Las listas siguen resolviendo a uno de tres estados: cargando, vacío o error con reintento (`RumaError`/`Failure`).

## Háptica

- Android: `LongPress` al confirmar un recojo y al confirmar un canje (`GeneratorRoot.kt`) y al disparar en la cámara
  (`GeneratorCameraScreen.kt`).
- PWA: `tick()` (vibración de 12 ms; iOS la ignora) en el obturador, al elegir la meta semanal, al confirmar el
  recojo, en el canje y en un paso del rider (`Work.tsx`).

## «Siguiente mejor acción»

`ui/screens/generator/NextStep.kt` (Android, tarjeta `NextStepCard` en el inicio) y `pwa/src/lib/nextStep.ts`, gemelos
que se mantienen a la par. Una función pura elige **una** tarjeta, en este orden:

1. Hay un recojo en curso: «Ver mi recojo».
2. Hay un escaneo guardado sin recojo: «Pide tu recojo».
3. El saldo alcanza el mínimo de cobro (el menor `min_points` de las opciones Yape/Plin): «Ya puedes cobrar».
4. Cero días con escaneo esta semana: «Registra tus residuos».
5. Falta un día para la meta: «Te falta 1 día para tu meta».
6. Saldo desde la mitad del mínimo: «Tu primer cobro está cerca».
7. Si no, «Registra lo de hoy» (con otro texto si la meta semanal ya se cumplió).

Regla de redacción: cada línea dice qué obtienes, nunca qué pierdes. Pruebas: `NextStepTest.kt` y
`pwa/scripts/check-next.ts`.

## Progreso semanal y «Tu ruma»

- `data/WeekProgress.kt` y `pwa/src/lib/week.ts`: días distintos con al menos un escaneo guardado en la semana
  (lunes a domingo, fecha de Lima), la meta semanal (1 a 7 días, por defecto **1**: una barra baja mantiene más
  rachas) y la racha de semanas seguidas que cumplieron la meta. Es solo participación: **no paga puntos** y no toca
  la `current_streak_days` del servidor. La meta se guarda en el dispositivo.
- «Tu ruma»: el montón del mes crece en cinco etapas (renders `ruma_1..5`) según los días del mes con escaneo
  (0, 2, 4, 6 y 8). Crece con la constancia, no con el volumen, para no premiar producir más basura.
- Pruebas: `WeekProgressTest.kt` y `pwa/scripts/check-week.ts`. Fundamento: `docs/retencion-incentivos-research.md`.

## Qué falta

- Rider: esqueletos y háptica en `ui/screens/rider/*`, `RiderRoot.kt` y `pwa/src/screens/rider/*` (A2, B3).
- Correr `:app:testDebugUnitTest` y ver las transiciones en un teléfono real; la PWA en iPhone (Safari 18 tiene View
  Transitions; en versiones anteriores cae al fundido).
- Hay pruebas de navegación de la PWA en `pwa/scripts/navigation.test.mjs` y `navigation.browser.mjs`.
