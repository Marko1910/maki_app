# Planes de pago: Ruma Plus

Estado al 2026-11-16. Lo que existe es **solo la base** (STAGING, `mbbdrmicjobydprvbpsj`), del script
`supabase/sql/2026-11-12_planes.sql`. No hay pantalla de Planes en Android, en la PWA ni en el panel del operador
(paquetes A1, B2 y D1 de `docs/plan-de-trabajo.md`). Producción no lo tiene. Lo que sigue en la sección de
psicología y de límites legales es **análisis y criterio de diseño, no asesoría legal ni resultado de pruebas con
usuarios**.

## Catálogo actual (leído de STAGING el 2026-11-16)

| Plan | Mensual | Anual | Prueba | Lo que la base aplica (`benefits`) |
|---|---|---|---|---|
| `GRATIS` («Ruma») | S/ 0 | — | — | nada: reciclar, cobrar y Rumi son gratis |
| `PLUS` («Ruma Plus») | **S/ 4,90** | **S/ 39,90** | **14 días** | `redeem_min_points: 1000`, `dispatch_priority: 1`, `raffle_tickets_bonus: 2` |

El anual equivale a S/ 3,33 al mes: 32 % menos que 12 × S/ 4,90 = S/ 58,80 (ahorro de S/ 18,90). Los precios viven en
la tabla `plans` y se cambian con un `UPDATE`, sin tocar las apps; el monto de una solicitud sale siempre del
catálogo, nunca de la app.

Beneficios que la base **aplica de verdad** (el cliente no puede activarlos con un flag):
1. **Cobro desde S/ 1.** `validate_redemption` usa el menor entre el mínimo de la opción y el del plan (1.000 pts),
   siempre en unidades enteras (1.000 pts = S/ 1). El plan gratis cobra desde S/ 5.
2. **Recojo primero en el tablero.** `trg_pickup_priority` fija `pickups.priority` al crear el recojo (un PATCH no lo
   cambia) y el tablero de riders ordena por prioridad y luego por antigüedad.
3. **Boletos del sorteo.** `raffle_tickets(uid, mes)` cuenta 1 por recojo pesado del mes (tope 4) y, si hubo al menos
   uno y el plan estuvo vigente ese mes, suma el bono (+2). Pagar no da boletos sin reciclar.
4. **Bono de referidos.** `referral_bonus_points` suma al premio de «invita y gana» (`docs/referidos.md`). Ningún plan
   lo define hoy; es decisión del dueño.

Lo que la gente ve en `plans.features` es texto con estado `activo` o `pronto`. Hoy figuran como **pronto**: los
boletos del sorteo (la base ya los cuenta, pero no hay premio, fecha ni bases publicados), recojo con hora fija,
protección de racha, ofertas del Mercadito y plan familiar. No se vende como activo lo que no funciona.

## Cómo se paga hoy (manual, por Yape)

1. La familia pide el plan: `rpc/request_plan(plan, 'month' | 'year')`. La base crea una solicitud
   `pending_payment` con un código `RUMA-XXXXXX` (sin 0/O/1/I, para dictarlo o escribirlo en el mensaje del Yape).
   Hay una sola solicitud abierta por persona: un segundo toque devuelve la misma; cambiar de opción retira la
   anterior.
2. La familia yapea el monto exacto con el código en el mensaje. Los datos salen de `plan_payment_config`.
3. Un **operador** ve llegar el Yape y llama `activate_plan_subscription(id, número de operación)` o
   `reject_plan_payment(id, motivo)`. Solo `is_operator()`. Un pago adelantado se encola tras el periodo vigente.
   Al activar, la familia recibe una notificación: «No se renueva solo».
4. **No hay cobro automático.** Nada se renueva solo: al vencer, `user_benefits` deja de dar beneficios en ese mismo
   instante (el cron diario `ruma-plans-expire`, 05:05, solo pone el estado en `expired` para que se lea bien).
5. **Prueba gratis:** `rpc/start_plan_trial('PLUS')`, una vez por persona en la vida, sin datos de pago, 14 días.
6. **Cancelar** es un toque y sin motivo (`rpc/cancel_my_plan`): una solicitud sin pagar se retira («No se cobró
   nada»); un plan pagado mantiene sus beneficios hasta la fecha pagada.
7. `my_plan()` devuelve todo lo que necesita la pantalla en una llamada: plan vigente, solicitud abierta, si la prueba
   sigue disponible, beneficios, boletos y datos de pago.

**Pendiente del dueño (O2):** en `plan_payment_config`, `yape_number`, `yape_holder`, `yape_qr_url` y
`business_contact` están en NULL. Mientras tanto la app debe decir «se publicará pronto»; la solicitud igual se guarda.

Pasarela (Culqi, Mercado Pago) más adelante: su webhook verificado llamaría a la misma lógica de
`activate_plan_subscription` desde una Edge Function con la service key, nunca desde la app.

## Análisis psicológico ético: por qué alguien lo compraría

El producto no puede apoyarse en miedo ni en confusión. Se apoya en cuatro ideas honestas y las dos primeras son
decisiones de presentación, no de la base.

1. **Anclaje, con un ancla real.** El precio mensual (S/ 4,90) sirve de referencia para el anual (S/ 39,90): al verlos
   juntos, el anual se entiende como «pagas poco más de 8 meses y usas 12» (12 × 4,90 = 58,80 frente a 39,90). El ancla es
   honesta porque ambos precios existen y se pueden pagar; no se muestra un precio tachado inventado ni un
   «antes S/ X» que nunca rigió. Mostrar también S/ 3,33 al mes ayuda a comparar con otros gastos pequeños.
2. **Plan anual.** Quien ya recicla cada semana tiene el hábito y confía en el servicio; el anual le ahorra el
   trámite de yapear cada mes (en este modelo manual, además, reduce el trabajo del operador). Se ofrece como una
   opción, no como el camino por defecto oculto: los dos se muestran con el mismo peso y el usuario elige.
3. **Momento de compra.** La disposición a pagar es mayor cuando el beneficio se acaba de sentir, no en frío:
   - tras la **prueba de 14 días**, cuando ya cobró desde S/ 1 o vio su recojo salir primero;
   - cuando el saldo está entre S/ 1 y S/ 5 (el plan gratis aún no deja cobrar): «Con Plus cobras ya». Es el momento
     en el que el beneficio es concreto y verificable. Debe decir también que con S/ 5 cobra gratis;
   - nunca en medio de una acción (escaneo, confirmación de recojo) ni con una ventana que bloquee.
   La tarjeta «siguiente mejor acción» del inicio (`docs/navegacion-fluida.md`) no debe usarse para empujar el plan.
4. **Valor tangible y modesto.** Es un plan de comodidad, no de más puntos («Más comodidad, no más puntos»): nadie
   gana más dinero por pagar. Esa honestidad es parte de la propuesta y reduce el reclamo de «me prometieron algo».

### Sin dark patterns (reglas de diseño que el plan ya cumple o que las pantallas deben cumplir)

- Sin cobro automático ni renovación escondida: cada periodo se paga a mano y la activación lo dice.
- La prueba gratis no pide datos de pago ni se convierte sola en cobro.
- Cancelar es un toque, sin motivo, sin pantallas de retención ni culpa («¿seguro que quieres perder…?»).
- Precio completo y en soles a la vista antes de pedir; nada de cargos que aparezcan después.
- Sin cuenta regresiva falsa, sin «quedan 3 cupos» inventados, sin casillas premarcadas, sin bloquear el plan gratis
  para empujar el de pago. Lo gratuito (recojo, puntos, cobro desde S/ 5, Rumi) sigue completo.
- Lo que no funciona todavía se marca «pronto» y no se usa para vender.
- Los beneficios los decide la base; la pantalla solo los muestra.

## Límites legales a verificar con Indecopi (y SUNAT) antes de cobrar

Esta lista es una guía de preguntas, **no una conclusión legal**: confirmar con Indecopi y con un abogado antes de
publicar precios o abrir el cobro. Referencias que conviene revisar:

- **Código de Protección y Defensa del Consumidor (Ley 29571):** información clara y veraz del precio final en soles
  (con impuestos incluidos), de las condiciones, la duración y lo que incluye; qué cuenta como publicidad engañosa
  si un beneficio figura como activo y no lo es (por eso los «pronto»); cláusulas abusivas; el derecho a reclamar
  (libro de reclamaciones, que puede ser virtual) y atención de reclamos.
- **Cancelación y renovación:** hoy no hay renovación automática, lo que simplifica. Si algún día se cobra con tarjeta
  y se renueva solo, hay que revisar el aviso previo, el consentimiento expreso y la baja tan fácil como el alta.
- **Desistimiento / devolución** de un servicio digital pagado por adelantado (sobre todo el anual): definir la
  política de reembolso y escribirla en la pantalla antes de pagar.
- **Prueba gratis:** informar su duración y que termina sin cobro; el uso de una sola prueba por persona debe estar
  dicho en los términos.
- **Sorteo mensual:** un sorteo o promoción comercial puede tener reglas de autorización y bases publicadas ante la
  autoridad competente. Verificar con Indecopi y el ministerio que corresponda **antes** de pasar los boletos de
  «pronto» a «activo». Que un plan de pago dé boletos extra en un sorteo es justamente lo que hay que consultar.
- **Invita y gana** (premios por referir): confirmar que los términos y la publicidad cumplen la misma norma.
- **Comprobantes y tributos (SUNAT):** boleta de venta electrónica por cada cobro, IGV si corresponde según el
  régimen del negocio, y registro de ingresos. Con Yape manual hay que emitirlas igual.
- **Datos personales (Ley 29733):** el número de operación del Yape y los datos de pago son datos personales;
  política de privacidad y finalidad claras.
- **Dinero de usuarios:** el canje de puntos a efectivo y el cobro del plan son flujos distintos; confirmar que no
  se mezclan en la contabilidad ni en las explicaciones al usuario.

## Advertencia: Vercel Hobby no permite uso comercial

La PWA está desplegada en Vercel **Hobby** (cuenta mrproductions), plan de uso **no comercial**. Cobrar un plan, aun
por Yape manual, es uso comercial: antes de abrir el cobro hay que pasar a **Vercel Pro** u otro alojamiento. Esto ya
está anotado en `CLAUDE.md` y como D8 en `docs/plan-de-trabajo.md`. Es requisito previo, no una mejora.

## Cómo probar

```bash
python supabase/tests/planes_staging.py        # espera 21/21
python supabase/tests/canje_seguro_staging.py  # espera 5/5 (este script redefine validate_redemption)
```

`planes_staging.py` escribe en STAGING para `generador@ruma.test`: su única prueba gratis (primera ejecución), una
solicitud Plus activada y luego vencida, un canje real de 1.000 pts que queda `pending`, tres recojos de prueba
cancelados y una solicitud anual retirada. Deja suciedad por diseño. Vencer una suscripción mueve las fechas con la
CLI de Supabase porque nadie puede editarlas por la API.

## Qué falta

1. Pantallas: Android (A1), PWA (B2) y vista del operador en el panel (D1). Sin D1 nadie cierra el pago manual.
2. Datos de Yape y confirmar precios (O2, D4).
3. Decidir el sorteo (D5) y el bono de referidos de Plus (D4).
4. Verificar con Indecopi lo anterior y pasar a Vercel Pro (D8).
5. Producción: aplicar `2026-11-11_arbitrios_porcentaje.sql` antes de este script, luego este, y después
   `2026-11-12_referidos.sql`; actualizar el encabezado con la fecha.
