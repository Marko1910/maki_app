# Traspaso a Claude: medición del conteo

Fecha: 2026-11-03. El usuario pide dejar esta tarea documentada y centrar el trabajo
actual en la navegación del EcoRider. No desplegar la medición como parte de rutas.

## Estado real

Implementado localmente, sin commit ni despliegue de estos cambios. Staging y
producción **NO verificados ni actualizados** para esta medición. La sesión de
Codex no tiene el conector Supabase que usó Claude. No inferir acceso actual de
las memorias de sesiones anteriores.

Archivos del trabajo:

- `supabase/functions/detect-material/index.ts`: token v2 opaco con procedencia
  firmada; modelo solicitado y versión reportada si existe; mezcla/legacy separados.
- `supabase/functions/detect-material/index.test.mjs`: 29 pruebas aprobadas,
  incluyendo primer proveedor falla → respaldo responde → preview → commit.
- `supabase/sql/2026-11-03_vision_measurement.sql`: nueva columna `provenance`,
  reemplazo transaccional de `commit_detection` con octavo argumento opcional,
  copia al primer `completed` y vistas privadas de concordancia.
- `supabase/tests/vision-measurement.test.mjs`: prueba PostgreSQL local con PGlite
  aprobada. Cubre idempotencia, rollback, cantidades ausentes/cero, conservación
  frente a liquidación, reapertura sin añadir filas, cohorte de prueba y permisos.
- `supabase/docs/vision-measurement.md`: investigación, debate con agente,
  fuentes, consultas, comandos y limitaciones.
- `CLAUDE.md`: guía actualizada para encontrar este trabajo.

No incluir automáticamente `skills-lock.json`, herramientas, fotos, dashboard ni
landing: ya estaban modificados o sin seguimiento antes de esta tarea.

## Continuación concreta

1. Inspeccionar esquema vivo de STAGING `mbbdrmicjobydprvbpsj`: tipos/columnas de
   detections, detection_items, pickups, pickup_items; unicidad de sesión y material
   por recojo; grants, políticas y triggers de finalización; definición actual de
   `commit_detection`, `complete_pickup`, `settle_pickup`, `settle_delivery`.
   El vínculo esperado es `pickups.detection_id → detections.id`, corroborado en
   historial pero pendiente de verificar en vivo.
2. Comparar la RPC viva con la que se conserva en la nueva migración: se tomó como
   base el script de 2026-10-19. No reemplazar cambios posteriores no versionados.
3. Ejecutar las pruebas locales indicadas en el documento de investigación.
4. Aplicar primero SQL en staging, como propietario. Es un script de aplicación
   única, no una migración repetible. No usar CASCADE para resolver dependencias.
5. Verificar grants de la nueva firma de 8 argumentos (solo service_role),
   compatibilidad de llamada de 7 argumentos y ausencia de acceso cliente al
   esquema `ruma_analytics`.
6. Desplegar después `detect-material` con el contenido real del repositorio.
   El código nuevo requiere la RPC ampliada; invertir el orden rompe commits.
7. Probar un recojo controlado: preview/commit, reintento, finalización por rider,
   lectura administrativa del snapshot, liquidación y comprobación de que el
   snapshot no cambió. Revisar que puntos/pagos conservan comportamiento.
8. Actualizar la cabecera SQL con resultados y fecha reales. Producción solo tras
   verificación y dentro de las instrucciones del usuario para ese entorno.

Si se revierte solo la Edge Function, los tokens v2 en vuelo no serán reconocidos
por el código viejo: prever una ventana de compatibilidad o reinicio de escaneos.
No eliminar snapshots ni inventar procedencia histórica para facilitar rollback.

## Límites que deben conservarse

Concordancia operativa, no evaluación ciega: rider ve cantidades precargadas.
No hay backfill fiable. No se evalúan marcas ni fotos rechazadas. Alias `latest`
pueden cambiar. Cuentas `@ruma.test` se separan, pero otras cuentas pueden usarse
para pruebas también. Android y PWA usan resoluciones distintas y todavía no se
segmentan por plataforma. Las pruebas PGlite usan una fixture mínima: no sustituyen
las políticas, triggers y verificaciones del staging real.
