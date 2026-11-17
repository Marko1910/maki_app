# EcoRider: rutas y navegación dentro de Ruma

Investigación 2026-11-03. Alcance: inspección Android/PWA, fuentes primarias y dos
consultas públicas al proveedor actual. No se modificó código de navegación ni se
reprodujo el fallo en un teléfono. Las causas específicas del dispositivo siguen
pendientes de observar; los defectos de control de estado descritos sí están en código.

## Conclusión

Es viable conservar el mapa y mostrar trazado, giros, progreso y voz dentro de Ruma.
Ya existe cálculo por calles: Android usa osmdroid para dibujar y OSRM/FOSSGIS para
calcular. La PWA usa Leaflet y el mismo servicio. Google Maps solo se abre desde
«Guía por voz» en Android; no es quien calcula el trazado interno actual.

La prioridad es reparar la integración y el seguimiento, no implementar Dijkstra
desde cero sobre los dibujos del mapa. Los mosaicos son imágenes, no el grafo vial.
Navegar dentro de la app no implica calcular sin Internet: el motor puede seguir
en un servidor. Navegación completamente offline exige otra fase con grafo regional,
motor local, actualizaciones y mapas offline permitidos por el proveedor.

## Evidencia del servicio real

Dos consultas consecutivas, separadas y sin datos privados, a puntos públicos de Lima:

- Route: `-77.034,-12.057;-77.043,-12.046`, `routed-car`, GeoJSON y `steps=true`.
  Respuesta `Ok`, 136 puntos, 2825.1 m, 409.4 s y 15 pasos.
- Trip: los anteriores y `-77.028,-12.048`, con exactamente los parámetros de la
  app: `source=first&roundtrip=false&destination=any`. Respuesta `Ok`, un recorrido,
  168 puntos. No probamos carga, precisión vial ni estabilidad a largo plazo.

La combinación Trip figura como no admitida en documentación OSRM 5.24, pero
aparece admitida en 26.5 y el endpoint real la aceptó. **No diagnosticar este caso
como NotImplemented basándose solo en documentación antigua.**
[API antigua](https://project-osrm.org/docs/v5.24.0/api/),
[API actual](https://project-osrm.org/docs/v26.5.0/http).

## Hallazgos en el repositorio

| Lugar | Hallazgo e impacto |
| --- | --- |
| `StreetRouter.kt` | Consulta Route/Trip y conserva geometría, distancia y tiempo. No pide `steps=true` ni parsea maniobras. Excepciones/errores terminan en null sin estado visible. |
| `RiderViewModel.onNavPosition` | Guarda `navFrom` antes de saber si hubo éxito. Después de fallo, el mismo origen no reintenta hasta moverse 40 m. Esto puede dejar sin trazado a quien espera parado. |
| `RiderViewModel.selectStop` | Borra `navRoad`, pero no `navFrom`/`navPickupId`: volver al mismo destino cerca del último origen puede dejar la pantalla sin ruta hasta moverse. |
| `RiderViewModel.onNavPosition` | Respuestas simultáneas del mismo pickup solo comprueban su id; una respuesta vieja puede sustituir a una nueva. No hay cancelación ni número de generación. |
| `RiderNavigationScreen` | Sin geometría dibuja una recta entre rider y destino. Esa recta no demuestra una calle transitable. Sin GPS o destino no se obtiene una ruta útil. |
| `RumaLocation` | Reduce Location a lat/lng: se pierden precisión, velocidad, rumbo y tiempo para decidir si la posición sirve para navegar. Puede reutilizar un fix de hasta 20 s. |
| `OsmMap` | Reconstruye overlays y reencuadra toda la ruta cuando cambian marcadores/trazado. Puede deshacer el zoom/paneo del rider; no hay modo seguimiento/progreso. |
| `streetTrip` Android | Con una parada en curso usa esa parada como origen para las restantes. La geometría/tiempo resultante omite el tramo rider→parada en curso; con solo esa parada, retorna sin calcular calles. |
| `RoutePlanner` | Vecino más cercano + 2-opt sobre Haversine: es heurística geométrica, no optimización del tiempo por calles. Sus comentarios «efectivamente óptimo» y «correcto para ordenar» son demasiado fuertes. |
| `TRAFFIC_FACTOR=1.3` | Solo multiplica el tiempo después de calcular. No observa tráfico ni cambia el camino elegido. No debe describirse como tráfico incluido. |
| `pwa/.../Work.tsx` | También marca origen antes del éxito; no invalida respuestas viejas/cambio de destino y los errores GPS se ignoran. La voz lee destino/ETA una vez, no giros. |
| `pwa/src/lib/util.ts` | Fetch de rutas sin timeout/cancelación explícitos. No conserva maniobras. |

## Qué se está optimizando

La ruta **más corta** minimiza metros; la **más rápida estimada** minimiza tiempo
de viaje. El costo de una calle debe incluir velocidad, accesos, sentido y giros;
con tráfico, puede depender de la hora. Sin datos de tráfico no se promete el
camino más rápido en las condiciones actuales. Moto, bicicleta y triciclo necesitan
perfiles adecuados; la moto actual usa car y triciclo usa bike, aproximaciones
que requieren validación local.

Hay tres problemas diferentes:

1. Un destino: encontrar calles desde GPS hasta el punto.
2. Varias recogidas: elegir orden sobre una matriz de tiempos por calles.
3. Navegar: asociar GPS al recorrido, avanzar maniobras y recalcular al desviarse.

Un algoritmo de camino mínimo resuelve el primero; no completa por sí solo los otros.

## Algoritmos y motores

| Opción | Aplicación y decisión |
| --- | --- |
| Dijkstra | Referencia exacta con costos no negativos. Requiere el grafo vial y restricciones. No aporta rehacerlo en Kotlin para reparar un trazado que OSRM ya devuelve. |
| A* | Dirige búsqueda a un destino con una heurística admisible. Si el costo es tiempo, no se deben sumar metros sin convertirlos a una cota temporal. Útil dentro de un motor, no sobre tiles. |
| CH / Multi-Level Dijkstra | Aceleraciones para redes viales. OSRM ofrece ambas y recomienda MLD como opción general. Reutilizar el motor existente es el camino más directo. |
| OSRM Trip | Orden aproximado para varias paradas; usa fuerza bruta para menos de 10 puntos y farthest insertion desde 10. No garantiza mejor solución en general ni maneja ventanas de entrega/capacidad. |
| Matriz de tiempos + optimización dirigida | Mejora real frente a Haversine. `t(A,B)` puede diferir de `t(B,A)`: no reutilizar la fórmula simétrica de 2-opt sobre esta matriz sin recalcular el costo de las aristas invertidas. |
| OR-Tools | Adecuado cuando entren ventanas horarias, capacidad, duración de servicio, acopio final o varios riders. Necesita matriz vial; no dibuja ni descubre calles. |
| Valhalla | Alternativa si se requieren perfiles más ricos, instrucciones o costos configurables. Ofrece moto (marcada beta) y motor_scooter; eso no garantiza cobertura perfecta ni tráfico vivo en Lima. No exige cambiar el mapa visible. |

[A* — NetworkX](https://networkx.org/documentation/stable/reference/algorithms/generated/networkx.algorithms.shortest_paths.astar.astar_path.html),
[OSRM — algoritmos](https://github.com/Project-OSRM/osrm-backend),
[OSRM — servicios](https://project-osrm.org/docs/v26.5.0/http),
[OR-Tools — ruteo](https://developers.google.com/optimization/routing),
[Valhalla — API](https://valhalla.github.io/valhalla/api/route/api-reference/).

Para una futura implementación propia del orden, una opción exacta con pocas
paradas es programación dinámica por subconjuntos (Held–Karp), O(n²·2ⁿ), sobre
matriz dirigida; el límite debe medirse en dispositivo. Para más paradas usar
heurísticas con presupuesto temporal. No es necesario incorporarlo en la primera
corrección: Route/Trip ya están integrados y permiten avanzar antes.

## Implementación recomendada por fases

### 1. Trazado fiable y estados explícitos

- Crear estados loading/ready/stale/error/location-unavailable en lugar de null.
- Identificar cada cálculo por destino, perfil y generación. Cancelar o ignorar
  respuestas superadas, incluso si pertenecen al mismo pickup.
- Separar último intento, último éxito y origen del trazado activo. Reintentar
  fallos transitorios con espera creciente aunque el rider no se mueva.
- Resetear estado al cambiar/reabrir destino. No depender solo del umbral de 40 m.
- Conservar una ruta válida del mismo destino como desactualizada si falla el
  refresco. No conservar una del destino anterior.
- No mostrar una recta como camino vial. Estado explícito si solo hay orientación
  aproximada; ofrecer reintento y aplicación externa como alternativa voluntaria.
- Corregir inicio del recorrido y ETA para incluir rider→parada activa, sin cambiar
  el destino ya comprometido con una familia. Mantener orden manual del operador.

### 2. Navegación integrada

- Pedir `steps=true`; DTOs de maniobra, ubicación, calle, distancia, duración y
  geometría por paso. Manejar salidas de rotonda, giro, continuidad y llegada.
- GPS continuo mientras se navega, conservando accuracy/timestamp/bearing/speed.
  Ciclo de vida y consumo deben probarse; pantalla apagada necesita tratamiento
  Android específico, no asumir que el loop de Compose seguirá ejecutándose.
- Proyectar posición sobre segmentos cercanos del recorrido y estimar progreso.
  No elegir solo el vértice más cercano; en cruces considerar rumbo/progreso previo.
- Recalcular cuando varias posiciones suficientemente precisas indiquen desvío,
  con umbral dependiente de accuracy, intervalo mínimo y exclusión de peticiones
  simultáneas. No recalcular porque simplemente avanzó por la ruta correcta.
- Marcador propio, tramo pendiente, botón recentrar y seguimiento que respete el
  paneo manual. La cámara no debe encuadrar todo el viaje en cada actualización.
- Instrucción visual + voz local con TextToSpeech, deduplicadas por maniobra/etapa.
  Los umbrales de aviso se calibran por velocidad y pruebas; evitar instrucciones
  insistentes o anunciadas con ubicación imprecisa.

[Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech).

### 3. Operación y optimización

- Una matriz de tiempos dirigida para ordenar recogidas, con duración en cada
  domicilio y acopio final cuando corresponda. Respetar parada activa y restricciones.
- Servicio backend de ruteo con límites globales y caché; un cliente por rider
  con throttle no controla la carga total de toda la flota.
- Elegir OSRM propio o proveedor con capacidad acordada antes de escalar.
  El servidor público actual limita a 1 petición/s y prohíbe uso intenso.
  El proxy por sí solo no elimina esas condiciones.
- Para tráfico real, incorporar una fuente con cobertura comprobada en la zona,
  y velocidades/tiempos actualizados en el motor. Ningún algoritmo crea esos datos.

[Política del servidor FOSSGIS](https://routing.openstreetmap.de/about.html).

## Pruebas de aceptación

1. GPS válido + destino: trazado vial visible; falta permiso/GPS: explicación útil.
2. Primera consulta falla, rider quieto: reintenta y se recupera.
3. A→B→A, volver a la misma parada y respuestas fuera de orden: nunca ruta ajena/vieja.
4. Una sola parada activa: incluye origen del rider y el tramo completo.
5. Seguir ruta: avanza progreso sin llamadas continuas. Desviarse: recalcula con GPS fiable.
6. Calle de un solo sentido, rotonda, sentidos opuestos, calle paralela y punto fuera
   de vía: no confundir posición ni prometer acceso al predio sin comprobar.
7. Moto/bici/pie: perfil correcto, y evaluación específica de las restricciones del triciclo.
8. Pérdida de red: no inventa ruta recta; conserva ruta previa etiquetada cuando sirve.
9. Prueba real y replay de trazas: tiempo hasta dibujar, llamadas/minuto, antigüedad
   del trazado, error de ETA, falsos desvíos y anuncios duplicados.

## Mantenimiento del mapa

Conservar osmdroid permite esta mejora incremental, aunque el proyecto está
archivado desde noviembre de 2024. Una migración futura de renderizado debe
planificarse aparte: no es requisito para dibujar las rutas que ya devuelve OSRM.
[Estado oficial de osmdroid](https://github.com/osmdroid/osmdroid).

Actualización 2026-11-05: las correcciones de integración, estados, mapa y guía
en primer plano se implementaron localmente en Android y PWA. Este texto conserva
la investigación inicial; consultar `ecorider-navigation-implementation.md` para
el alcance real, pruebas y limitaciones restantes.
