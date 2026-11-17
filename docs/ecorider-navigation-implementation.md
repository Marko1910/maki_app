# Navegación EcoRider — implementación local

Actualizado 2026-11-05. Plataformas: Android nativo y PWA para iPhone/Android.
No desplegado en Vercel ni instalado en un teléfono. No se modificó el backend.
La medición de visión sigue separada y pendiente de staging según su handoff.

## Cambios

- Política de peticiones comprobable: una petición en curso, generación por destino,
  reintentos estacionarios con espera creciente (3–30 s), descarte de respuestas
  antiguas y actualización periódica. Un fallo de refresco no borra el trazado
  válido del mismo destino; la pantalla lo identifica como sin actualizar.
- Recálculo por desvío sobre segmentos, no por avanzar 40 m. Dos fixes alejados,
  mínimo 10 s entre recalculaciones de una ruta válida y refresco normal a 60 s.
- Cliente de rutas con maniobras OSRM, validación de geometría/orden, timeout y
  límite de una petición por segundo por instancia. La PWA aborta peticiones;
  Android cancela el coroutine y descarta su resultado, aunque una llamada HTTP
  bloqueante ya empezada puede terminar hasta su timeout de 10 s.
- Ruta diaria incluye rider→parada activa; la parada activa permanece delante.
  Respuestas tardías no restauran estados antiguos. Paradas sin coordenadas no se
  eliminan de la lista. La PWA suma cuatro minutos estimados de servicio por parada,
  igual que Android. Los puntos sin ubicación no forman parte del trazado.
- Sin calles disponibles no se dibujan rectas como si fueran vías.
- Maniobras y distancias restantes por proyección de GPS sobre segmentos.
  Voz en español dentro de ambas apps, activada por el usuario; Maps opcional
  en Android. La voz se detiene al salir/ocultar la navegación.
- GPS: rechaza fixes de más de 15 s o con precisión peor de 60 m para guiar.
  Android conserva accuracy/time y pausa la captura fuera de RESUMED. PWA muestra
  errores de permisos y permite reintento. No se infiere llegada con GPS inválido.
- Mapa: ruta con borde blanco y acento del rider, ubicación propia distinta,
  control para recentrar y respeto del paneo/zoom manual. Corrección de una ruta
  nueva con igual cantidad de puntos que la anterior en Leaflet.
- UI: estados de cálculo/error/GPS, estimaciones explícitas, controles táctiles,
  panel Android desplazable para no tapar todo el mapa, diseño PWA adaptable y
  área segura. Los textos de marcadores web se insertan como texto, no HTML.
- PWA: una selección de recojo que desaparece ya no cambia silenciosamente
  la acción a otro recojo.

## Verificación

Pruebas Android: `NavigationRoutePolicyTest`, `StreetRouterTest`, `RoutePlannerTest`:
16 pruebas aprobadas. Compilación y APK debug generados con JDK 21.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
.\gradlew.bat :app:testDebugUnitTest --tests '*NavigationRoutePolicyTest' --tests '*StreetRouterTest' --tests '*RoutePlannerTest' :app:assembleDebug --offline
```

PWA: TypeScript y `npm run build:staging`; cinco pruebas de lógica en
`node --test pwa/scripts/navigation.test.mjs`.

Prueba navegador reproducible: iniciar Vite en puerto 5174 y ejecutar
`node pwa/scripts/navigation.browser.mjs`. Requiere Playwright resoluble o
`PLAYWRIGHT_MODULE` apuntando a su `index.mjs`. Usa Edge instalado por defecto
(`PLAYWRIGHT_CHANNEL` configurable). Intercepta TODAS las peticiones a Supabase
y al router con datos sintéticos; no altera recojos reales.

Cinco escenarios aprobados: 390×844, 360×800, 844×390, GPS denegado y router caído.
Comprueba recuperación estacionaria, geometría sin rectas inventadas, recentrado,
desbordamiento horizontal y errores de ejecución. Capturas revisadas en
`.gstack/navigation-qa/` (ignoradas por Git).

## Prueba en Android físico — 2026-11-05

Samsung Galaxy A12 (SM-A125M), Android 12, pantalla 720×1600. Actualización
debug instalada conservando los datos. `RiderNavigationDeviceTest`: **4/4 aprobadas**
(22.971 s). Verifica controles de mapa y voz, callback local de llegada, ausencia
de GPS, reintento estacionario y una consulta real a OSRM desde la red del teléfono.
La pantalla usa coordenadas y destino sintéticos mediante `positionSource`; no
escribe recojos ni requiere una sesión. Las capturas muestran una geometría
sintética de prueba, no el resultado de OSRM.

Capturas revisadas: `.gstack/android-device-qa/navigation-qa/`. Mapa y acciones
visibles, panel desplazable y atribución OSM presente. El control de centrar se
deshabilita sin posición. No se registraron excepciones AndroidRuntime durante
esta ejecución.

```powershell
adb shell am instrument -w -e class pe.ruma.app.RiderNavigationDeviceTest pe.ruma.app.test/androidx.test.runner.AndroidJUnitRunner
```

`RiderHardwareServicesTest` comprueba por separado GPS real y síntesis de voz;
requiere permiso de ubicación concedido por el usuario, señal y voz española.
Reproduce una frase audible; no guarda ni envía la posición a un backend.

La primera ejecución de servicios pasó voz pero no obtuvo posición en 8 segundos,
con permiso preciso y ubicación del sistema activos. Se encontró que navegación
esperaba exclusivamente al proveedor satelital. Se corrigió `RumaLocation` para
solicitar GPS y red simultáneamente, aceptar la primera posición de ≤60 m y ≤15 s,
y retirar todos los listeners al terminar/cancelar. Un caché reciente pero impreciso
ya no impide buscar una posición mejor. Referencia API:
[Android LocationManager](https://developer.android.com/reference/android/location/LocationManager).

Repetición tras la corrección: **5/6 aprobadas**, 30.984 s. Las cuatro pruebas de
navegación y la síntesis de voz pasan. `freshLocationFromDevice` sigue sin obtener
un fix aceptable dentro del plazo, pese a permisos y proveedores activos. No se
considera resuelto ni validado el posicionamiento real; repetir con mejor señal
y durante un recorrido. No se guardaron coordenadas en esta documentación.
La finalización de TTS confirma el motor; la audibilidad no fue confirmada por
el usuario al cerrar esta ejecución. Las 16 pruebas JVM pasan tras el cambio.
APK actualizado instalado en el dispositivo conservando datos y permisos.

## Límites y siguiente validación

- Se verificó Android nativo en el A12, sin realizar un recorrido físico.
  Los tamaños móviles en Chromium no
  sustituyen Safari real, sensores, permisos, voces instaladas ni consumo de batería.
- Probar en iPhone Safari y como PWA instalada; voz requiere mantener la app abierta.
  No se promete navegación iOS con pantalla bloqueada, ni servicio Android en segundo plano.
- Proyección local de segmentos no equivale a map matching completo: cruces,
  retornos y calles paralelas requieren replay/recorrido real antes de confiar
  en la guía como navegador profesional. La voz disponible depende del dispositivo.
- OSRM actual usa perfil de auto para moto y bici para triciclo; validar accesos
  locales. No hay tráfico vivo, garantías de ruta óptima ni navegación offline.
- Límite de cliente no regula una flota completa. Antes de escalar, contratar
  capacidad o alojar el motor, conforme a la política FOSSGIS.
- Falta decidir matriz dirigida/ventanas/capacidad (fase avanzada del documento
  de investigación). No se entrenó ni añadió IA para rutas.
- Probar timeout seguido de recuperación, desvío, cambio de recojo, pérdida de GPS,
  paneo manual, giro/rotonda, llegada y reanudación desde segundo plano en hardware.
