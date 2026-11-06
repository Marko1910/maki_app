# Propuesta de inicio Eco-Hero: reto, progreso, racha y wallet

Fecha: 2026-11-05. Estado: propuesta para discutir; no implementada ni validada
con usuarios. Conserva la variante visual de la PWA elegida por el usuario.
La adaptación visual Android iniciada anteriormente queda pendiente de terminar
y verificar; este documento no supone que esa adaptación esté instalada.

## Dirección de producto

Ruma ayuda a separar y registrar residuos, coordinar su recojo y recibir valor
por material confirmado. El objetivo es un hábito útil y reciclaje correcto,
no maximizar aperturas, fotografías ni cantidad de residuos generados.

En el código actual, `points_balance` es saldo canjeable, `points_pending` es
saldo pendiente y `current_streak_days` es una racha existente. Los retos actuales
avanzan al liquidar el recojo: `advance_challenges` se llama desde el flujo de
acopio en `supabase/sql/2026-10-19_cadena_entregas.sql`. La función detect-material
lee además `streak_multiplier`. No está verificada aquí la regla completa que
actualiza la racha existente; no debe reinterpretarse ni cambiarse por una edición
visual. Una racha nueva de hábitos requiere una definición y datos propios.

## Recomendación para discutir

Un único anillo de meta semanal elegida por la persona; una acción sugerida para
hoy. Por ejemplo, 3 días con una acción válida por semana. Esa frecuencia es una
hipótesis de diseño, no una meta sustentada por datos actuales de Ruma.

- Arco: días válidos de la semana / meta elegida, limitado visualmente a 100 %.
- Centro principal: `2 de 3` y `días esta semana`; el número explica el arco.
- Centro secundario, si cabe con letra grande: `Racha: 4 semanas`. Si no cabe,
  mover la racha inmediatamente debajo del anillo.
- Mensaje: `Te falta 1 día para completar tu meta`.
- Debajo: wallet compacta con saldo confirmado, equivalencia y pendientes
  separados; acceso al detalle y canjes.
- Una acción principal: registrar residuos. Sugerencia diaria contextual sin
  imponer cuotas de generación de residuos ni un segundo sistema de puntos.
- Seguimiento de recojo activo permanece visible y accesible.

El anillo mide la semana; la racha mide semanas consecutivas cumpliendo la meta.
Una racha no tiene un total natural: no calcular un porcentaje arbitrario de ella.
No colocar un número grande de racha rodeado por un arco que mide otra cosa.

## Reglas propuestas para el hábito

1. Cuenta como máximo un día por fecha de America/Lima con un registro válido
   guardado por el servidor. Abrir cámara, iniciar sesión, reintentar el mismo
   guardado o repetir el mismo material no debe añadir días ni progreso extra.
2. Este hecho reconoce participación, no peso confirmado, dinero ni impacto
   ambiental verificado. La wallet conserva la liquidación actual en acopio.
3. Si posteriormente se incorporan ejercicios de clasificación, podrían contar
   para el hábito al completarse con feedback; actualmente no se asume que existan.
   No otorgar puntos canjeables por leer, abrir la app o marcar tareas manualmente.
4. El día se registra por la acción de la familia, no por la llegada del rider.
   No penalizar demoras de recojo o validación externas.
5. Guardados sin confirmación de servidor quedan pendientes de sincronización;
   no anunciar avance confirmado antes del acuse. Diseñar antes de implementar
   la atribución de fecha para sincronización tardía y sus controles de integridad.
6. Al completar la meta, mantener el anillo lleno hasta terminar la semana;
   seguir mostrando el total alcanzado si se supera. La racha aumenta una sola
   vez por semana cumplida. Los días restantes son libres.
7. Cambios de meta aplican desde la semana siguiente. Una semana incumplida
   reinicia la racha futura sin borrar historial, logros ni saldo. Mensaje neutral,
   sin alarmas de pérdida ni cargos para recuperar rachas.
8. Sin meta, mostrar `Elige tu ritmo`; sin datos por fallo de red, mostrar error
   y reintento, nunca cero como si se hubiera perdido progreso.

Una opción estrictamente diaria puede explorarse después con usuarios que la
prefieran; evitar presentarla como requisito para recibir el servicio o canjear.

## Presentación y validación

Fondo y fuentes de la PWA; un anillo verde, contorno neutro y extremos redondeados.
Wallet compacta translúcida. No meter puntos, soles, racha y porcentaje como cuatro
cifras competidoras dentro del círculo. Animar solo cambios confirmados y respetar
reducción de movimiento. Accesibilidad: valor y meta verbalizados, texto ampliado
y contraste, sin depender del color.

Probar con familias si entienden qué llena el anillo, cuándo crece la racha y
cuáles puntos pueden canjear. Comparar participación sostenida, registros útiles,
duplicados/correcciones y entregas confirmadas; no usar solo aperturas como éxito.

## Evidencia y límites

- Silverman y Barasch, siete estudios sobre rachas: destacar rachas intactas
  favoreció la participación respecto a destacar rachas rotas. Los dominios
  estudiados no validan por sí solos una frecuencia óptima para reciclaje peruano.
  https://academic.oup.com/jcr/article/49/6/1095/6623414
- Luo, Zelenika y Zhao: un juego de clasificación con feedback inmediato mejoró
  exactitud y mostró efectos en residencias universitarias. Apoya explorar
  aprendizaje útil; no demuestra la eficacia de un anillo diario en Ruma.
  https://doi.org/10.1016/j.jenvman.2018.11.061
- Trash to treasure (2025): 26 entrevistas y experimento de uso con 18 participantes;
  evidencia exploratoria sobre gamificación de reciclaje, no garantía de retención.
  https://doi.org/10.1016/j.resconrec.2024.108108

La preferencia por meta semanal flexible y wallet separada es una recomendación
de diseño basada en estas fuentes y en el flujo actual, pendiente de validación.

## Propuesta visual creada en Stitch

- Proyecto: [Ruma PWA](https://stitch.withgoogle.com/projects/3649521857212731107).
- Primera propuesta: `ed7233dccaa64954be25d9dc5156cb7f`, «Ruma · Inicio — Meta
  semanal, racha y wallet». Su captura mostró una fuente serif de respaldo;
  no usar esa tipografía como referencia.
- Revisión solicitada por el usuario: **«Ruma · Inicio — Verde fluido / Manrope»**,
  pantalla `7ee50915aee24c05a5f425bcfdfd04df`, confirmada en el lienzo del proyecto.
  Manrope con respaldo sans serif explícito, números jerarquizados, degradado
  esmeralda en arco y CTA y fondo suave menta/marfil. Wallet compacta debajo.
- Captura descargada y revisada: `.gstack/stitch/home-refined.png`;
  HTML de referencia: `.gstack/stitch/home-refined.html`.
- Cifras ilustrativas: 2 de 3 días, racha de 4 semanas, 4 250 puntos disponibles
  equivalentes a S/ 4.25 y 180 pendientes. No son datos de una cuenta real.
- Es una propuesta visual en Stitch. No implementa todavía metas, rachas nuevas
  ni cambios de tipografía Manrope en Android/PWA. Antes de integrar, adaptar
  accesibilidad, áreas seguras e interacciones al código de cada plataforma.
