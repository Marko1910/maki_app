# Análisis de la entrevista a la Municipalidad Distrital de Santiago de Cao (MDSC)

Fecha del análisis: 2026-11-11. Fuente: `entrevistas/Segregación MDSC.docx` (8 preguntas, todas con respuesta).
Guion de origen: `entrevistas/Ruma-preguntas-de-campo.pdf`, sección 4 «Municipalidad».
El documento no registra **quién respondió, su cargo ni la fecha**; las respuestas tienen redacción de
escrito formal (no de conversación), así que se tratan como respuesta institucional por escrito.

Convención: **[DATO]** cifra o documento citado por la municipalidad · **[OPINIÓN]** postura sin respaldo
numérico · **[SUPUESTO]** cálculo o interpretación de este análisis, no dicho por la MDSC.

---

## 1. Resumen ejecutivo

1. **No existe descuento de arbitrios por segregar** en Santiago de Cao, y el arbitrio de una vivienda es de
   apenas **S/ 55 al año**. El canje «Descuento en Arbitrios» que hoy muestra Ruma no tiene respaldo legal en el
   distrito y, aun si lo tuviera, vale muy poco.
2. **La morosidad es de 78,9 %** (875 de 4.150 predios pagan). Cuatro de cada cinco vecinos no pagan arbitrios:
   un descuento no los mueve, y la municipalidad recauda del orden de S/ 48.000–60.000 al año por limpieza [SUPUESTO].
3. **El programa de segregación (Programa RECICLA) está suspendido** por falta de personal; el empadronamiento de
   viviendas está inactivo. Hay un vacío operativo real que Ruma puede llenar.
4. **Ya existe un actor formal**: la Asociación de Recicladores «San Pedro», 19 recicladores formalizados, que
   recoge los inorgánicos con medios propios y **sin presupuesto municipal**. Es aliado natural o competidor directo.
5. **La municipalidad no pagará por recolección selectiva sola**: la considera «ineficiente» y solo ve justificado
   contratar una EO-RS para el servicio integral. El ingreso de S/ 1.500/mes por convenio municipal del modelo de
   costos queda **sin sustento** para este distrito.
6. **El incentivo que sí funciona es el trueque**: el ECOTRUEQUE de febrero de 2026 en Cartavio recuperó más de
   3.700 botellas a cambio de 843 kg de compost. Hay respuesta vecinal al PET en el mismo centro poblado del piloto.
7. **Ya no hay incentivo económico del MEF** por valorización (verificado para 2025), pero la municipalidad sigue
   obligada a reportar en SIGERSOL: lo que le sirve de Ruma son **datos para ese reporte**, no un servicio pagado.
8. Quedan sin cubrir: volúmenes de residuos, plan distrital, presupuesto de limpieza, centros de acopio e interés
   en un panel o en datos. Ninguna de esas preguntas estaba en el guion.

---

## 2. Preguntas respondidas

### P1. ¿Tienen ordenanza de segregación en la fuente con descuento en arbitrios? ¿Número y año?

- **Respuesta:** No. «Actualmente no contamos con descuento en arbitrios por segregación en la fuente». Sí existe
  la **Ordenanza Municipal N.º 018-2017-MDSC**, que aprueba la formalización de recicladores para el Programa de
  Segregación en la Fuente, hoy denominado «Programa RECICLA» (R.M. N.º 098-2023-MINAM).
- **Qué significa para Ruma:** el canje `MUNI_ARBITRIOS` («1.000 pts = S/ 1,00 de descuento en tus arbitrios
  municipales», socio «Municipalidad») promete algo que en este distrito no existe. Para ofrecerlo haría falta una
  ordenanza nueva del concejo. La ley sí lo permite (ver sección 3.1), así que es una propuesta posible, no un hecho.
- **Confianza: alta [DATO].** Cita norma y número. Verificado: la R.M. 098-2023-MINAM existe, se firmó el
  15/03/2023 y se publicó el 17/03/2023 (la fecha que da la MDSC es la de publicación). La ordenanza 018-2017-MDSC
  **no se pudo verificar en línea**: pedir copia.

### P2. ¿Cuánto paga al año de arbitrio de limpieza pública una casa promedio?

- **Respuesta:** «Según la estructura de costos vigente (año 2013)»: vivienda S/ 55,00 al año (≈ S/ 4,60/mes);
  negocio pequeño S/ 58,80; mediano S/ 63,70; grande S/ 68,60.
- **Qué significa para Ruma:**
  - El supuesto del panel (`dashboard/lib/economia.ts`: arbitrio anual de **S/ 600** y descuento de 10 %) está
    **sobrestimado unas 11 veces** para este distrito. Un 10 % real serían S/ 5,50 al año.
  - Pagar el arbitrio completo con puntos exige 55.000 pts = unos **137 kg de PET al año** por recolector (a
    400 pts/kg) [SUPUESTO]. El arbitrio es un premio pequeño, no el gancho principal.
  - Una estructura de costos de 2013 sugiere que el arbitrio no se actualiza hace más de una década: la
    municipalidad subsidia la limpieza con otras fuentes y tiene poco margen para pagar servicios nuevos.
- **Confianza: media-alta [DATO].** Cifras precisas, pero la frase dice tasa «proyectada» y remite a 2013; no
  queda claro si es lo que se cobra hoy. Falta el cuadro tarifario en PDF que el guion pedía llevarse.

### P3. ¿Cuántas viviendas están empadronadas en el programa y cuántas cumplen de verdad?

- **Respuesta:** No dio números. «El empadronamiento de viviendas se encuentra inactivo»; la ejecución y la
  supervisión en campo están «temporalmente suspendidas debido a limitaciones de personal».
- **Qué significa para Ruma:** no hay padrón que heredar ni línea base de participación; Ruma tendría que
  construir el suyo, y ese padrón digital (recolectores activos, kg por material) es justo lo que la municipalidad
  no puede producir hoy. También indica que nadie en la municipalidad tendrá tiempo para operar un panel complejo.
- **Confianza: alta sobre el estado (admisión contra interés propio); nula sobre cantidades.** Falta saber cuántas
  viviendas hubo empadronadas cuando el programa funcionaba y desde cuándo está suspendido.

### P4. ¿Cuál es la morosidad del distrito en arbitrios?

- **Respuesta:** «Nivel de morosidad crítico de aproximadamente 78,9 %. De un total de 4.150 predios afectos,
  únicamente 875 contribuyentes cumplen». Fuente declarada: Reporte Anual SIGERSOL 2025.
- **Qué significa para Ruma:**
  - La aritmética cuadra (875 / 4.150 = 21,1 % paga).
  - Recaudación anual estimada: entre S/ 48.000 y S/ 60.000 (875 × S/ 55–68,60) frente a una emisión de al menos
    S/ 228.000 [SUPUESTO]. Un convenio de S/ 1.500/mes (S/ 18.000/año) equivaldría al **30–37 % de todo lo que
    recauda** por limpieza pública.
  - El descuento de arbitrios solo interesa al 21 % que paga. Para el 79 % restante el incentivo debe ser otro
    (efectivo, Yape, trueque).
  - Ángulo a favor: el D.L. 1278 obliga al concejo a aprobar estrategias para reducir la morosidad (sección 3.1);
    «paga parte de tu arbitrio con reciclaje» podría presentarse como una de ellas.
- **Confianza: alta [DATO].** Cifra con numerador, denominador y fuente oficial. No se revisó el reporte SIGERSOL.

### P5. ¿Tienen convenio con alguna asociación de recicladores? ¿Cuántos formalizados hay?

- **Respuesta:** No hay convenio formal de prestación de servicios; se trabaja «de manera articulada» con la
  **Asociación de Recicladores «San Pedro»**, 19 recicladores formalizados, presidida por la Sra. Herma Nicolasa
  Valderrama Fernández, Partida Electrónica N.º 11240307, formalizada por Resolución de Alcaldía N.º 018-2024-MDSC.
- **Qué significa para Ruma:**
  - Es el dato más accionable de la entrevista: hay 19 personas ya formalizadas que hacen el trabajo del
    Eco-Rider. Reclutarlas resuelve la oferta de riders y, probablemente, el encaje legal (sección 3.2).
  - Si Ruma entra con riders propios, compite por el mismo PET y cartón con una asociación que la municipalidad
    protege.
  - Que no exista convenio formal muestra que la MDSC no acostumbra firmar ni pagar: su modo de trabajar es la
    «articulación» sin dinero.
- **Confianza: alta [DATO].** Nombres, partida registral y resolución. Nota: la ordenanza de 2017 y la resolución
  de 2024 comparten el número 018; puede ser coincidencia, conviene confirmar ambos documentos. No se sabe cuántos
  de los 19 están activos ni si cubren Cartavio.

### P6. ¿Quién hace hoy la recolección selectiva, con qué vehículos y con qué presupuesto anual?

- **Respuesta:** Dos frentes. Los inorgánicos los recoge la Asociación «San Pedro» «utilizando sus propios medios
  y sin asignación de presupuesto municipal directo». La municipalidad recoge orgánicos y produce compost, y hace
  campañas **ECOTRUEQUE**: en febrero de 2026, en el C.P. Cartavio, «se logró recuperar más de 3.700 botellas de
  plástico, entregando a cambio 843 kg de compost orgánico».
- **Qué significa para Ruma:**
  - **Presupuesto para reciclables: cero.** No hay partida que Ruma pueda capturar o sustituir.
  - Las 3.700 botellas son unos 75–110 kg de PET, es decir S/ 75–110 a precio de acopio [SUPUESTO: 20–30 g por
    botella, S/ 1,00/kg]. Como señal de participación es buena; como volumen de negocio es mínimo. Sirve de
    referencia para dimensionar el piloto: una campaña entera equivale a muy pocos recojos.
  - El vecino de Cartavio ya entrega botellas a cambio de un bien no monetario (≈ 0,23 kg de compost por
    botella). El compost municipal podría ser una opción de canje de Ruma sin costo en efectivo.
  - La municipalidad está orgullosa de ECOTRUEQUE («exitosas campañas»): presentarse como su versión permanente
    y medible es mejor entrada que presentarse como proveedor.
- **Confianza: media.** La cifra del ECOTRUEQUE es concreta [DATO] pero no se encontró publicada en línea. La
  pregunta quedó **respondida a medias**: no dice vehículos, frecuencia, rutas, a quién vende la asociación ni
  cuánto cuesta el frente de orgánicos. «Exitosas» es valoración propia [OPINIÓN].

### P7. La meta de residuos ya no está en el Programa de Incentivos. ¿Siguen reportando valorización al MINAM?

- **Respuesta:** Como municipalidad Tipo E solo tenía el Indicador 3.3 (valorización de orgánicos). El Compromiso 3
  existió en 2024; «para los años 2025 y 2026 este compromiso ya no figura en el PI». Ya no reportan metas con fines
  de incentivo, pero mantienen el compostaje y el **reporte anual obligatorio en SIGERSOL** (el de 2025 se
  oficializó en marzo de 2026).
- **Qué significa para Ruma:**
  - Confirma la advertencia del guion: no se puede vender a Ruma como ayuda para cobrar una meta del MEF.
  - Aun con la meta vigente, a este distrito solo le medían orgánicos: los inorgánicos (PET, cartón) nunca le
    dieron dinero. El argumento económico por esa vía no existió ni existe.
  - Lo que queda es la obligación de reportar. El «Reporte para municipalidades» (F2 de
    `docs/panel-duenos-analisis.md`) debería salir con los campos que pide SIGERSOL: es el único entregable con
    demanda comprobada.
- **Confianza: alta sobre el hecho, media sobre el detalle.** Verificado: el PI 2025 (D.S. 099-2025-EF) tiene
  cinco compromisos (MINSA, MEF, MIDIS, MVCS, MININTER) y ninguno de residuos. **No verificado:** que 2026 tampoco
  lo incluya, la clasificación Tipo E de la MDSC y el número de indicador 3.3.

### P8. ¿Qué tendría que pasar para que la municipalidad le pague a un tercero por este servicio?

- **Respuesta:** «Tercerizar únicamente la recolección de residuos aprovechables resulta ineficiente». Lo
  «adecuado y normativo» sería contratar una **EO-RS** para la gestión integral (recolección total, transporte,
  acopio y disposición final). «Fraccionar el servicio limitándolo solo al material reciclable no representa una
  ventaja operativa ni económica para la municipalidad».
- **Qué significa para Ruma:**
  - Es un **no** al modelo «la municipalidad paga a Ruma por recoger reciclables». No pidió precio ni condiciones:
    descartó la figura.
  - La respuesta habla de pagar por un *servicio de recolección*. No dice nada sobre pagar por *datos*, un panel o
    un programa de incentivos: esa pregunta no se hizo y sigue abierta.
  - Cualquier pago municipal pasaría por contratación pública y por un proveedor registrado como EO-RS ante el
    MINAM. Ruma no lo es.
  - Camino realista: convenio de cooperación **sin costo** (la municipalidad pone aval, difusión y quizá compost;
    Ruma pone la operación y los datos), con ingresos por el material y, más adelante, por datos a marcas.
- **Confianza: media [OPINIÓN técnica].** Es el criterio de quien respondió, bien argumentado pero sin cifra ni
  norma citada, y no necesariamente la posición del alcalde o del concejo. La parte normativa es correcta en lo
  esencial (sección 3.2).

---

## 3. Hallazgos frente a los supuestos del producto y de los costos

### 3.1 Contraste con supuestos

| Supuesto de Ruma | Dónde está | Lo que dice la MDSC | Veredicto |
|---|---|---|---|
| La municipalidad paga S/ 1.500/mes por convenio desde la etapa Piloto (`muni_fee`, `on_muni`, 1 convenio en el mes 1 del plan) | `docs/costos/scripts/build_costos.py` | No paga por recolección selectiva; presupuesto de reciclables = 0; recauda ~S/ 48–60 mil/año | **Contradicho** para este distrito. Poner 0 en el caso base del piloto |
| Arbitrio anual del hogar S/ 600, descuento 10 % | `dashboard/lib/economia.ts` | S/ 55 al año, sin descuento vigente | **Contradicho** (≈ 11 veces menos) |
| «Ruma trabaja con la municipalidad: los puntos bajan el recibo de arbitrios» | `supabase/sql/2026-10-09_precios_reales_y_arbitrios.sql`, billetera de Android y PWA | No hay ordenanza de descuento | **Contradicho hoy.** Ocultar el canje en Cartavio o rotularlo «próximamente» hasta que haya ordenanza |
| El descuento de arbitrios es un incentivo relevante | producto | 78,9 % no paga arbitrios | **Contradicho** como incentivo principal |
| Existe un programa municipal de segregación sobre el cual montarse | producto | Programa RECICLA con ordenanza, pero suspendido y sin padrón | **Parcial**: hay marco legal, no hay operación |
| Hay recicladores formalizados (Ley 29419) | producto | 19, en la Asociación «San Pedro» | **Confirmado** |
| El vecino entrega PET si recibe algo a cambio | producto | ECOTRUEQUE Cartavio, 3.700+ botellas | **Confirmado** (evento puntual, incentivo no monetario) |
| La meta del MEF ya no sirve como argumento | guion de campo | Confirmado, y nunca cubrió inorgánicos aquí | **Confirmado** |
| Interés municipal en un panel o en pagar por datos | modelo de negocio | No se preguntó | **Sin evidencia** |
| Volúmenes de residuos, plan distrital (PIGARS es provincial), centros de acopio, presupuesto de limpieza | costos | No se preguntó | **Sin evidencia** |

Nota aparte, ya conocida por el equipo: el modelo usa cartón a S/ 0,60/kg y la entrevista al acopio dio S/ 0,30
(`supabase/sql/2026-10-28_precios_de_entrevista.sql`). Esta entrevista no aporta precios.

### 3.2 Normativa verificada

- **R.M. N.º 098-2023-MINAM** — incorpora la denominación «Programa RECICLA» a la guía de la R.M. 138-2021-MINAM
  (programa de segregación en la fuente y recolección selectiva). Firmada el 15/03/2023, publicada el 17/03/2023.
  Verificado. https://busquedas.elperuano.pe/dispositivo/NL/2161052-1
- **D.L. N.º 1278, Ley de Gestión Integral de Residuos Sólidos** (texto con las modificaciones del D.L. 1501,
  consultado en una compilación con concordancias, no en El Peruano):
  https://snp.org.pe/wp-content/uploads/2022/04/04.-Ley-de-gestion-integral-de-RRSS.pdf
  - **Descuento de arbitrios permitido** (art. 70 según la compilación; confirmar numeración): «Las
    municipalidades pueden efectuar descuentos en el pago de arbitrios como incentivo a los ciudadanos que cumplan
    con realizar la segregación en la fuente […] entre otro tipo de incentivos». Es facultad, no obligación.
  - **A quién debe entregar el vecino** (art. 34): los generadores de residuos municipales entregan sus residuos
    segregados «a asociaciones de recicladores formalizados u operadores de residuos sólidos debidamente
    autorizados o a las municipalidades que presten el servicio».
  - **EO-RS** (art. 60): para prestar servicios de residuos o comercializarlos deben estar registradas ante el MINAM.
  - **Municipalidad distrital** (art. 24): promueve la formalización de asociaciones de recicladores y las
    comunica al MINAM para el Registro Nacional de Recicladores; supervisa a recicladores formales e informales.
  - **Morosidad**: los concejos «deben aprobar estrategias para […] aumentar la recaudación y reducir la
    morosidad» (número de artículo sin confirmar).
  - D.L. 1501 (El Peruano): https://busquedas.elperuano.pe/dispositivo/NL/1866220-2
  - **Pendiente:** la Ley 32212 (2024) volvió a modificar varios de estos artículos y **no se revisó**; las citas
    de arriba pueden haber cambiado. https://lpderecho.pe/ley-32212-gestion-manejo-residuos-solidos/
- **Ley N.º 29419, Ley que regula la actividad de los recicladores**, y su reglamento **D.S. 005-2010-MINAM**
  (vigente según SINIA). El art. 7 de la ley dispone que los gobiernos locales implementan programas de incentivos
  a la segregación en la fuente, que pueden incluir la reducción del pago de tarifas; verificado por cita en
  ordenanzas municipales, el PDF de la ley está escaneado y no se leyó limpio.
  https://faolex.fao.org/docs/pdf/per90106.pdf ·
  https://sinia.minam.gob.pe/normas/reglamento-ley-no-29419-que-regula-actividad-recicladores
- **Programa de Incentivos 2025, D.S. N.º 099-2025-EF** — cinco compromisos, ninguno de residuos ni a cargo del
  MINAM. Verificado. https://busquedas.elperuano.pe/dispositivo/NL/2402215-1
  El Compromiso 3 de 2024 («Sistema Integrado de Manejo de Residuos Sólidos Municipales») sí existió:
  https://mef.gob.pe/contenidos/presu_publ/capacita/2024/26042024_PI_Compromiso_3_implementacion_SI_manejo_residuos_solidos_municipales.pdf

**No verificado (tratar como supuesto):** Ordenanza 018-2017-MDSC, Resolución de Alcaldía 018-2024-MDSC, Partida
11240307, la cifra SIGERSOL 2025, el ECOTRUEQUE de febrero de 2026, la clasificación Tipo E, el Indicador 3.3 y la
ausencia del compromiso en el PI 2026. La MDSC no mencionó PIGARS ni plan distrital de manejo de residuos.

**Lectura legal para Ruma [SUPUESTO, requiere abogado]:** si el vecino solo puede entregar sus reciclables a una
asociación formalizada, a un operador autorizado o a la municipalidad, un Eco-Rider independiente que cobra por
recoger queda fuera de esas tres figuras. Las salidas son que los riders del piloto sean miembros de la Asociación
«San Pedro» (o de otra formalizada), o que Ruma se registre como EO-RS.

---

## 4. Riesgos y oportunidades para el piloto en Cartavio

### Riesgos

1. **Ingreso municipal inexistente.** El modelo cuenta con S/ 1.500/mes desde el piloto; aquí la respuesta es no
   y la capacidad de pago es mínima. Si otros distritos pequeños se parecen, la línea de convenios se cae entera.
2. **Promesa incumplible en la app.** El canje de arbitrios aparece activo sin ordenanza que lo respalde: riesgo
   de reclamo del recolector y de roce con la municipalidad.
3. **Encaje legal del Eco-Rider** (sección 3.2). Operar sin la asociación ni registro EO-RS expone a
   fiscalización municipal.
4. **Conflicto con la Asociación «San Pedro».** Ruma iría por el mismo material con el que viven 19 personas que
   la municipalidad formalizó. Un conflicto visible cierra la puerta municipal.
5. **Contraparte sin capacidad.** Si el área no tiene personal para supervisar su propio programa, tampoco lo
   tendrá para firmar, dar seguimiento o usar un panel. Todo lo que dependa de trabajo municipal se demorará.
6. **Volumen pequeño.** 4.150 predios en todo el distrito y una campaña que junta ~100 kg de PET: con solo PET y
   cartón, el ingreso por material puede no cubrir los recojos. El modelo ya no recuperaba la inversión en 60 meses.
7. **Fuente única y anónima.** Una sola respuesta escrita, sin nombre ni cargo; la postura de P8 puede no ser la
   del alcalde.

### Oportunidades

1. **Vacío operativo.** Programa RECICLA suspendido y sin padrón: Ruma puede ofrecerse como la forma de
   reactivarlo sin personal municipal, con registro digital de participantes y kilos.
2. **Riders ya formalizados.** Los 19 de «San Pedro» como Eco-Riders: oferta inmediata, legitimidad y encaje
   legal. Para ellos Ruma significa rutas con material asegurado y pesaje transparente.
3. **Demanda vecinal probada en Cartavio.** El ECOTRUEQUE demuestra que el vecino entrega PET; Ruma lo vuelve
   continuo y a domicilio. Co-organizar la próxima edición es un buen lanzamiento del piloto.
4. **Compost como canje.** La municipalidad ya produce compost y lo regala por botellas: opción de canje sin
   desembolso para Ruma ni para la MDSC.
5. **Datos para SIGERSOL.** El reporte anual es obligatorio y hoy los inorgánicos los mueve una asociación sin
   presupuesto ni, presumiblemente, registro sistemático. Un reporte mensual de kg valorizados es valor real a
   costo casi nulo, y la moneda de cambio para un convenio de cooperación.
6. **Ordenanza de descuento posible.** La ley la permite y la morosidad obliga al concejo a buscar estrategias.
   Con arbitrios de S/ 55 el costo fiscal sería bajo. Es una propuesta de mediano plazo, no un requisito del piloto.
7. **Venta de datos de marcas intacta.** Nada de la entrevista la afecta; depende de volumen y consentimiento,
   no de la municipalidad.

---

## 5. Preguntas de seguimiento recomendadas (por prioridad)

Antes de volver: anotar nombre, cargo y fecha de quien responde.

**Prioridad 1 — deciden si el piloto es viable y legal**

1. ¿Firmarían un convenio de cooperación **sin costo** para la municipalidad, en el que Ruma opera la recolección
   de PET y cartón en Cartavio junto con la Asociación «San Pedro» y entrega un reporte mensual? ¿Quién lo firma
   y cuánto demora?
2. ¿Puede una empresa recoger reciclables a domicilio en el distrito si sus recolectores son recicladores
   formalizados de la asociación? ¿O exigen registro EO-RS?
3. ¿Nos presentan a la presidenta de la Asociación «San Pedro»? ¿Cuántos de los 19 están activos, cubren Cartavio,
   con qué vehículos trabajan y a qué acopio venden?
4. ¿Hay centros de acopio con licencia en el distrito? ¿Cuáles y dónde?

**Prioridad 2 — el modelo de ingresos**

5. ¿Qué datos de inorgánicos les pide SIGERSOL y cómo los consiguen hoy? ¿Les serviría recibirlos cada mes ya
   calculados? (Primero qué hacen hoy; después mostrar el reporte.)
6. Si no pagarían por recolección, ¿pagarían por información: un reporte o panel con participación por sector y
   kg valorizados? Usar escalera: ¿S/ 200 al mes? ¿S/ 500? ¿S/ 1.000? Si la respuesta es no, ¿qué darían a cambio
   (aval, difusión, local, compost)?
7. ¿Cuál es el presupuesto anual de limpieza pública y cuánto cubre la recaudación de arbitrios? ¿Qué partida
   financia el compostaje y el ECOTRUEQUE?
8. ¿Evaluaría el concejo una ordenanza de descuento de arbitrios por segregación? ¿Quién la propone y qué
   necesitaría ver para aprobarla?

**Prioridad 3 — dimensionar el piloto**

9. ¿Cuántas toneladas de residuos genera el distrito al día y cuántas Cartavio? ¿Tienen estudio de
   caracterización (porcentaje de PET y cartón)? Pedir el PDF.
10. ¿Cuántos de los 4.150 predios están en Cartavio? ¿Cuántas viviendas llegaron a estar empadronadas en el
    programa y desde cuándo está suspendido?
11. ECOTRUEQUE: ¿cuántas ediciones al año, cuántas personas participaron en febrero, cuántos kilos pesaron las
    botellas, quién se las llevó y a qué precio? ¿Podemos participar en la próxima?
12. ¿Tienen plan distrital de manejo de residuos vigente? ¿Qué meta de valorización fija?
13. ¿Dónde hacen la disposición final y cuánto pagan por tonelada? (Permite valorar cada tonelada desviada.)
14. ¿La estructura de costos de 2013 es la que se cobra hoy? ¿Hay actualización en trámite?

**Documentos por pedir:** cuadro tarifario de arbitrios, Ordenanza 018-2017-MDSC, Resolución de Alcaldía
018-2024-MDSC, reporte SIGERSOL 2025, estudio de caracterización y plan distrital de residuos.

---

## 6. Preguntas aún sin respuesta

### 6.1 Sección Municipalidad: respondidas, con huecos

Las 8 preguntas del guion tienen respuesta. Quedaron incompletas:

- **P3:** no dio el número de viviendas empadronadas ni las que cumplen.
- **P6:** no dijo vehículos ni presupuesto anual.
- **P2:** no se obtuvo el PDF del cuadro tarifario («Antes de irse: pedir el PDF…»).

### 6.2 Resto del guion, todavía no preguntado

Aviso de vocabulario: en la hoja de campo, «recolector» es quien lleva material al acopio (el reciclador), no el
actor «recolector» de Ruma (la persona que recicla en casa).

**Centro de acopio**

1. ¿Cuánto le paga usted al recolector por kilo de chatarra? ¿Y por el cartón? ¿Y por las botellas?
2. ¿Ese precio es igual para todos, o le paga mejor al que trae más cantidad?
3. ¿Cuánto le cuesta el flete hasta Arequipa? ¿Por tonelada o por viaje?
4. ¿Compra aluminio? ¿Y vidrio? ¿Cuánto le paga al recolector por cada uno y a cuánto los vende?
5. ¿Cómo distingue una lata de gaseosa de una lata de conservas? ¿Las paga igual?
6. En diciembre, ¿de cuánto a cuánto le baja el precio? ¿Y en enero a cuánto vuelve?
7. ¿Siempre logra juntar las 30 toneladas en 15 días, o hay quincenas en que se queda corto? ¿Qué hace cuando pasa?
8. ¿Qué material le rechaza al que se lo trae? ¿Cuánto le descuenta si viene sucio o mojado?
9. ¿Paga en efectivo al momento? ¿Pesa delante de la persona que trae el material?
10. ¿Hay una cantidad mínima para recibirle a alguien?
11. Si yo le garantizo una cantidad fija de toneladas al mes, ¿me pagaría algo por esa seguridad, o me daría mejor precio por kilo?
- Pedir una foto de la lista de precios de la pared.

**Recolector (a la salida del acopio)**

1. ¿A cuánto le acaban de pagar el kilo? ¿De cada material?
2. ¿Cuántos kilos trajo hoy y cuánto le pagaron en total?
3. ¿Y el mejor día que ha tenido? ¿Cuánto fue?
4. ¿Desde qué hora sale y hasta qué hora? ¿Cuántas paradas hace?
5. ¿Cuánto gasta al día en combustible, en el vehículo y en comida?
6. ¿Cuántos viajes hace al acopio en un día? ¿Cuánto le cabe por viaje?
7. ¿Siempre le pagan lo mismo, o le bajan el precio algunos días?
8. ¿Está registrado en la municipalidad? ¿Tiene carné de reciclador?
9. ¿Cómo consiguió a sus clientes fijos, los que siempre le guardan material?
10. ¿Qué le haría cambiarse a otro acopio?
11. Si el celular le avisara que a seis cuadras hay 30 kilos de cartón esperando, ¿iría? ¿Cuánto tendría que haber para que valga el viaje?

**Bodega o negocio**

1. ¿Cuántas cajas junta por semana? ¿Y botellas? (y pesarlo ahí mismo)
2. ¿Cada cuánto pasa el que recoge y qué se lleva exactamente?
3. Mientras junta el material, ¿dónde lo guarda? ¿Le estorba?
4. Si le pagaran S/ 2 por lo de una semana, ¿lo vendería en vez de regalarlo? ¿Y por S/ 5? ¿Y por S/ 10?
5. ¿Prefiere que le paguen en efectivo al momento, por Yape, o todo junto a fin de mes?
6. ¿Quién entregaría el material: usted o alguien que trabaja con usted?
7. ¿Qué pasa si un día no puede atender al que viene a recoger?
8. ¿Usaría una app en su celular, o preferiría que todo lo haga el que recoge y a usted solo le den su recibo?

Si el guion vigente del equipo es otro, esta lista debe contrastarse con él: el `.docx` solo contiene las 8
preguntas de la municipalidad.

### 6.3 Temas que el guion no cubre y esta entrevista dejó abiertos

Volúmenes de residuos y caracterización; plan distrital de residuos; centros de acopio autorizados; presupuesto
de limpieza pública; interés en un panel o en pagar por datos; disposición a aprobar una ordenanza de descuento;
situación legal de un tercero que recoge reciclables. Están recogidos en la sección 5.
