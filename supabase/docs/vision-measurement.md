# Medición del conteo de Ruma

Investigación y debate de diseño: 2026-11-03. Este documento explica las decisiones;
la cabecera del script SQL correspondiente indica si se aplicó en cada entorno.

Estado de esta entrega: **no aplicado en staging ni en producción**. La sesión de
implementación no dispone del MCP de Supabase para verificar el esquema vivo y
desplegar. El script preparado es
`supabase/sql/2026-11-03_vision_measurement.sql`.

## Qué podemos medir

Medimos **concordancia operativa del conteo de Ruma con el conteo registrado por el
rider al completar el recojo**, por material y procedencia del escaneo. No es una
prueba ciega de precisión de un modelo: el resultado incluye el modelo de visión,
la normalización de marcas y la combinación de fotografías de `foldPhotos`.

El rider ve cantidades precargadas desde la declaración de la familia. En Android
esto ocurre en `RiderViewModel` al preparar y confirmar `confirmQty`. Por tanto,
coincidir puede significar que comprobó el conteo o que aceptó el valor mostrado.
No debemos presentar el porcentaje como «la IA tiene X% de precisión».

NIST recomienda acompañar las mediciones de exactitud con conjuntos representativos
y una metodología explícita. Aplicado aquí, la telemetría de recojos complementa
una futura evaluación independiente, pero no la reemplaza.
[NIST: características de confianza](https://airc.nist.gov/airmf-resources/airmf/3-sec-characteristics/)

## Problemas encontrados en el código anterior

1. `commit_detection` recibía `PROVIDERS[0].model` aunque la respuesta ganadora
   viniera de otro proveedor. El campo histórico no demuestra quién respondió.
2. `settle_delivery` convierte el peso del acopio a unidades estimadas y sobrescribe
   `pickup_items.confirmed_quantity`. Consultarlo después no recupera el conteo
   original del rider.
3. Los tokens protegían cantidades, usuario, sesión y grupo; la procedencia no
   estaba firmada. Un campo `provider` enviado por el cliente no sería confiable.
4. Una sesión puede combinar varias fotografías y varios proveedores. El conteo
   final no puede atribuirse arbitrariamente al último o al primer modelo.
5. Los datos históricos incluyen pruebas. Las cuentas `@maki.test` deben quedar
   separadas de las demás; ese dominio es una señal de prueba, no una garantía
   de que todos los demás registros sean de uso real.

El vínculo existente es `pickups.detection_id -> detections.id`. Las definiciones
de `create_pickup_from_detection` y `complete_pickup` recuperadas del historial de
Claude corroboran ese vínculo. Ese historial es evidencia de contexto, no una
verificación del esquema desplegado actualmente.

## Decisión tras contrastar alternativas

| Alternativa | Decisión y motivo |
| --- | --- |
| Vista directa sobre los campos actuales | Descartada: atribuiría respuestas al modelo incorrecto y mezclaría conteos del rider con estimaciones del acopio. |
| Usar la declaración de la familia como referencia | Descartada: es editable y no constituye una comprobación independiente. Se conserva como contexto. |
| Guardar JSON dentro de `model_version` | Evita cambiar la RPC, pero sobrecarga una columna textual con una estructura incompatible para futuros consumidores. Preferimos procedencia dedicada. |
| Procedencia `jsonb` en la inserción de la detección | Elegida: queda junto al resultado y dentro de la misma transacción e idempotencia. |
| Actualizar procedencia después de `commit_detection` | Descartada: introduce una ventana de fallo y un reintento podría alterar la atribución de una detección ya guardada. |
| Firmar modelo y revisión del procesamiento dentro del token | Elegida: Android y PWA ya transportan el token como texto opaco, sin requerir un cambio de contrato en sus observaciones. |
| Copia independiente al pasar a `completed` | Elegida: conserva los valores que existían al completar, aunque el acopio liquide después. |
| Sobrescribir datos históricos para completar huecos | Descartada: inventaría procedencia y conteos que ya no podemos demostrar. |
| Consultar dos modelos siempre o entrenar una red ahora | No resuelve la falta de etiquetas fiables. Primero instrumentar; una comparación controlada o entrenamiento vendrán con datos adecuados. |

### Procedencia y compatibilidad

El servidor firma procedencia y revisión del procesamiento junto a las cantidades.
La verificación de la firma precede a su uso para analítica. Cambiar el modelo,
la revisión, el usuario, la sesión, el grupo o los conteos debe invalidar el token.
Los tokens antiguos pueden conservar compatibilidad funcional, pero su procedencia
es desconocida. Una sesión con un token antiguo y uno nuevo sigue teniendo
procedencia incompleta; no debe convertirse artificialmente en «un solo modelo».

La procedencia resume un conjunto ordenado y sin duplicados de las fuentes usadas.
Una sesión con varias fuentes queda en una categoría mixta separada. No se divide
su error entre los modelos: las reglas de máximo y deduplicación hacen que esa
división no sea identificable con los datos disponibles.

La procedencia conserva el identificador solicitado en `model`. Cuando la respuesta
del proveedor incluye un identificador `model` válido, también lo firma y guarda
como `resolved_model`. Esto registra lo informado por el proveedor; no garantiza
que ese identificador sea una versión inmutable. Un alias `latest` puede cambiar
de modelo sin que cambie el nombre configurado. Sin una versión resuelta distinta,
el reporte identifica el alias, no unos pesos inmutables.
[Google: versiones y alias de modelos](https://ai.google.dev/gemini-api/docs/models?hl=en)

La ampliación de `commit_detection` necesita un parámetro de procedencia con valor
predeterminado para las llamadas antiguas. Sustituir la firma anterior se hace en
una transacción, sin `CASCADE`, y restableciendo los permisos de ejecución
explícitamente. Primero se aplica la migración en staging; después se despliega
la función que envía el parámetro nuevo. Un conflicto de sesión debe devolver la
detección original sin modificar sus cantidades ni su procedencia.

El script es de **aplicación única**, no una migración repetible: tras aplicarlo,
la firma antigua ya no existe. Ejecutarlo otra vez debe fallar y revertirse, no
usarse como procedimiento de actualización. Registrar su aplicación en la cabecera
y preparar otra migración para cualquier cambio posterior.

### Copia del conteo al completar

La copia guarda el conteo original de `detection_items`, la declaración vigente y
`confirmed_quantity` antes de la liquidación. Incluye materiales presentes solo
en uno de los lados. Ausencia y cero no son equivalentes: una cantidad desconocida
se conserva como `NULL`, y no participa en métricas que requieren ambos conteos.
Un cero confirmado sí es una observación evaluable.

La transición y la copia pertenecen a la misma transacción. Si otra guarda impide
completar el recojo, la copia también se revierte. Repetir la operación no crea
otra observación ni reescribe la primera. No se reconstruyen copias retroactivas
desde recojos ya liquidados.

Actualmente los clientes escriben los materiales mediante varios PATCH y después
llaman a `complete_pickup`. La copia describe el estado persistido al completar;
no demuestra que todos los valores provengan de una única operación humana.
Introducir bloqueos globales de padre e hijos solo para esta analítica podría
interferir con la liquidación. Una futura RPC que confirme todos los materiales y
complete el recojo atómicamente debe tratarse como un cambio explícito del flujo
operativo, con pruebas de concurrencia propias.

### Acceso

La tabla de copias y las vistas de medición quedan fuera del acceso de `anon` y
`authenticated`. La lectura administrativa debe usar `service_role` o el
propietario de la base. No se incluye correo, dirección ni nombre en los agregados;
la separación de cuentas de prueba guarda una categoría, no el correo original.

Una vista no hereda automáticamente el aislamiento esperado: PostgreSQL usa por
defecto los permisos de su propietario. Si más adelante se abre un panel para
operadores, habrá que diseñar su autorización y RLS; conceder acceso a la vista
actual no basta. [PostgreSQL: permisos de vistas](https://www.postgresql.org/docs/current/sql-createview.html),
[Supabase: RLS en vistas](https://supabase.com/docs/guides/database/postgres/row-level-security)

## Cómo interpretar los resultados

Para cada material y cohorte, mostrar cantidad de muestras y de recojos distintos,
coincidencia exacta, error absoluto medio en unidades y sesgo firmado
`conteo_ia - conteo_rider`. El error absoluto informa magnitud; el sesgo indica
si tiende a contar de más (positivo) o de menos (negativo). El denominador de cada indicador debe
incluir únicamente observaciones con ambos conteos disponibles.

Evitar el error porcentual por observación cuando el rider confirma cero. MAE
mantiene las unidades del conteo y no requiere dividir entre la referencia.
[scikit-learn: MAE](https://scikit-learn.org/stable/modules/generated/sklearn.metrics.mean_absolute_error.html),
[scikit-learn: evaluación de regresión](https://scikit-learn.org/stable/modules/model_evaluation.html)

Limitaciones que deben acompañar cualquier reporte:

- Solo se observan escaneos guardados que llegan a un recojo completado. No se
  mide la tasa de rechazo, disponibilidad del proveedor ni materiales omitidos
  en imágenes rechazadas.
- El rider puede aceptar la cantidad precargada; no es un anotador ciego.
- La familia puede cambiar el conjunto de residuos entre la foto y la entrega.
- La mezcla de fotos y `foldPhotos` también pueden producir diferencias.
- Varias filas del mismo recojo no son muestras humanas independientes.
- Los proveedores de respaldo reciben casos seleccionados por latencia y errores;
  comparar sus promedios no demuestra causalmente que uno sea mejor.
- La procedencia actual no distingue plataforma ni resolución de captura. Las
  diferencias de preprocesamiento entre Android y PWA pueden influir en el resultado.
- No existe confirmación por marca. Esta medición no evalúa reconocimiento de marcas.
- Los modelos desconocidos, mixtos y las cuentas de prueba se analizan por separado.

Para elegir un modelo con evidencia, el paso posterior es un conjunto de imágenes
representativas con cantidades anotadas de forma independiente, evaluado por cada
candidato con las mismas imágenes y reglas. Requiere definir consentimiento y
retención antes de almacenar fotografías de usuarios.

## Comprobaciones necesarias

- Firma alterada, caducada o de otra sesión/usuario/grupo: rechazada.
- Tokens antiguos y mezcla antiguo/nuevo: compatibles y señalados como desconocidos.
- Varios proveedores o revisiones: categoría mixta, orden estable y sin duplicados.
- Fallo del primer proveedor, respuesta del respaldo, preview, transporte opaco del
  token y commit: procedencia conservada de extremo a extremo sin red real.
- Reintento del commit: conserva cantidades y procedencia originales.
- Conteo cero, material ausente y cantidad sin confirmar: tratados distintamente.
- Liquidación o edición posterior: no modifica la copia.
- Transición rechazada: no deja copia; transición repetida: no duplica muestras.
- Cuentas de prueba: agregadas separadamente.
- Clientes anónimos y autenticados: sin lectura ni escritura de analítica.
- Aplicación transaccional, llamada antigua de siete argumentos y nueva con
  procedencia: comportamiento comprobado sin modificar los pagos.

Las pruebas locales verifican lógica y permisos del esquema de prueba. No sustituyen
la inspección y verificación en staging, ni deben presentarse como un despliegue.

Desde la raíz del repositorio:

```powershell
node --test supabase/functions/detect-material/index.test.mjs
node --test supabase/tests/vision-measurement.test.mjs
```

La segunda prueba necesita `@electric-sql/pglite` resoluble por Node. Si ya existe
en otra ubicación local, se puede indicar su entrada sin instalar dependencias en
la app:

```powershell
$env:PGLITE_MODULE = 'C:/ruta/al/paquete/pglite/dist/index.js'
node --test supabase/tests/vision-measurement.test.mjs
```

La fixture SQL es deliberadamente mínima: ejecuta PostgreSQL con PGlite y valida
el contrato nuevo, pero no replica todas las políticas ni todos los triggers del
Supabase desplegado. La revisión de staging debe comprobar esas interacciones.

## Consulta administrativa

Después de aplicar y verificar en staging, ejecutar desde el SQL Editor o una
conexión administrativa autorizada:

```sql
select *
from ruma_analytics.model_count_metrics
order by compared_samples desc;

-- Cohorte con procedencia única y sin el dominio de pruebas conocido.
select *
from ruma_analytics.model_count_metrics
where attribution = 'single' and not test_account
order by material_id, sources;
```

`sources` conserva modelo, proveedor y revisión del procesamiento. Las métricas
actuales abarcan todo el periodo desde `first_sample_at` hasta `last_sample_at`;
para otro intervalo hay que filtrar `ruma_analytics.count_samples` y volver a
agregarlo, no filtrar únicamente las fechas extremas de un agregado existente.
La primera consulta puede devolver cero filas hasta completar nuevos recojos.
Eso no significa una precisión de cero ni un fallo de medición.
