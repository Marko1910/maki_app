# Panel de dueños de Ruma — análisis

Fecha: 2026-11-06. Estado: análisis, nada implementado. Datos medidos en STAGING (`mbbdrmicjobydprvbpsj`).

## Para qué sirve

Un panel solo para el equipo dueño de Ruma (no para acopios ni municipalidades) que responda:

1. **Dónde operamos**: mapa del Perú por distrito (municipalidad distrital) con recolectores, EcoRiders y acopios.
2. **Cómo va cada municipalidad**: recolectores activos, recojos, kg confirmados por material, CO₂ evitado,
   pagos a riders, puntos emitidos y canjeados, tiempos de asignación y cancelaciones.
3. **Qué se puede vender**: estudio de mercado de marcas y envases por zona y semana.

## Lo que existe hoy (medido)

| Pieza | Estado |
|---|---|
| Panel del acopio (`dashboard/`, Next.js + MapLibre + ECharts) | Por centro: entregas, pesaje, liquidación, stock, pagos, mercado, call center. Base reutilizable para el de dueños. |
| `districts` | **3 filas** (SJL, Comas, Ate) con `name`, `region`. Sin ubigeo, provincia ni geometría. |
| `zones` | 3 filas, cuelgan de un distrito. |
| Direcciones | 5; todas con coordenadas (`geog`), solo 3 con distrito. Las cuentas de prueba están en Cartavio pero los distritos cargados son de Lima. |
| Rol de dueño | **No existe.** `operador` está atado a un centro (`current_operator_center()`). |
| Marcas | `detection_items.brands` + vista `v_brand_zone` (zona = `block_label` o distrito). Hoy 59 unidades de 11 marcas: casi todo de prueba. |
| Calidad del conteo | Medición IA vs. rider activa en staging (`ruma_analytics`, 2026-11-06). La **exactitud de la marca no se mide**. |
| Consentimiento para usar datos | **Ninguna columna** de consentimiento, términos ni cesión a terceros. |
| Fotos | No se guardan (bien para privacidad; no hay material para auditar marcas). |

## Qué falta construir, en orden

### F0 — Cimientos de datos (sin esto el mapa miente)

1. **Catálogo de ubigeo**: ampliar `districts` con `ubigeo`, provincia, departamento y geometría simplificada.
   Fuente abierta recomendada: HDX «Peru – Subnational Administrative Boundaries» (COD-AB, niveles 0–3, producido
   por el IGN). Verificar los códigos contra el ubigeo del INEI: las fuentes difieren (≈1 867–1 874 distritos según
   el corte), así que el código, no el nombre, es la llave.
2. **Asignar distrito por coordenadas**: `addresses.district_id` y `collection_centers.district_id` calculados con
   PostGIS (`ST_Contains` sobre la geometría) al guardar, no escritos por el cliente. Rellenar las filas existentes.
3. **Rol de dueño** (`admin` de plataforma): el panel lee solo vistas agregadas en un esquema privado, desde el
   servidor de Next.js. La `service_role` nunca llega al navegador; las vistas no se exponen en PostgREST.
4. **Consentimiento** en el registro: casillas separadas y sin marcar (uso de datos para estudios agregados de
   mercado; cesión a terceros), con fecha y versión de los términos. Sin esto no se vende nada.

### F1 — Panel de dueños, MVP

- Mapa coroplético por distrito (MapLibre ya está en el panel) con selector de métrica y periodo.
- Ficha de municipalidad: KPIs del periodo y tendencia semanal; lista de acopios; riders activos y en turno.
- **Privacidad en el mapa**: recolectores como densidad (hexágonos o conteo por distrito), nunca puntos de casas.
  Riders en vivo solo como conteo por distrito; la posición exacta solo para operación, no para reportes.
- Métricas (todas desde la base, nunca del cliente): recolectores activos 30 días, recojos completados, kg
  confirmados por material, CO₂, S/ pagados a riders, puntos emitidos/canjeados, minutos hasta asignación,
  tasa de cancelación, concordancia IA/rider (`ruma_analytics.model_count_metrics`).

### F2 — Reporte para municipalidades

Exportable por distrito y mes (PDF/CSV) para convenios: material desviado del relleno, participación y CO₂.
Es la puerta para que las municipalidades paguen o promuevan Ruma.

### F3 — Estudio de mercado de marcas

Producto: participación de cada marca dentro de su categoría (agua, gaseosa, cerveza…) por distrito y semana,
presentaciones (500 ml, 650 ml…) y evolución. Posible segundo comprador, además de marketing: productores con
obligaciones de responsabilidad extendida del productor sobre envases (**hipótesis por verificar** con la norma de
residuos sólidos vigente).

Condiciones antes de vender:

1. **Legal** — Ley N.º 29733 y su nuevo reglamento (D.S. 016-2024-JUS, vigente desde el 30/03/2025): el
   tratamiento requiere consentimiento previo, libre, expreso e inequívoco, y la transferencia a terceros también.
   Vender solo agregados anonimizados; revisar con un abogado si el agregado sale del alcance de la ley y qué
   bancos de datos hay que inscribir ante la autoridad.
2. **Umbral mínimo por celda**: no publicar una celda (distrito × semana × marca) con menos de N personas distintas
   (proponer N = 10) para que nadie sea identificable en un distrito pequeño.
3. **Calidad**: medir la exactitud de la marca antes de cobrar por ella. Propuesta: el rider confirma la marca en la
   puerta en una muestra aleatoria, igual que ya confirma la cantidad; cruzarlo en `ruma_analytics`.
4. **Volumen**: con los datos actuales no hay muestra. Fijar el mínimo por distrito y semana antes de ofrecerlo.

## Riesgos

- Mezclar cuentas de prueba (`@ruma.test`) con reales: todas las vistas deben excluirlas, como ya hace la medición.
- Un mapa con distritos vacíos se lee como "fracaso": mostrar cobertura (dónde operamos) separada de desempeño.
- Datos de ubicación de riders: son personas trabajando; limitar quién los ve y cuánto tiempo se guardan.

## Reparto sugerido

- **F0 (SQL + backend)**: Claude, staging primero, con pruebas como las de `ruma_analytics`.
- **F1 (pantallas Next.js)**: Codex, sobre vistas ya probadas.
- **Consentimiento en Android y PWA**: un lote cada uno.
- **Revisión legal de F3**: abogado; no es trabajo de código.

## Fuentes

- Reglamento D.S. 016-2024-JUS (texto oficial): https://www.congreso.gob.pe/Docs/DGP/DIDP/files/ds_016-2024-jus.pdf
- Vigencia del reglamento: https://www.elperuano.pe/noticia/266995-minjusdh-el-lunes-31-regira-nuevo-reglamento-de-ley
- Límites administrativos COD-AB (HDX): https://data.humdata.org/dataset/54fc7f4d-f4c0-4892-91f6-2fe7c1ecf363
- Límites distritales con ubigeo INEI (comunidad): https://github.com/juaneladio/peru-geojson
