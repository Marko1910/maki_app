# Piloto con los recicladores de «San Pedro» como Eco-Riders: de quién es el material

Fecha: 2026-11-11. Estado: análisis y recomendación. No se modificó código ni base de datos.
El texto legal debe revisarlo un abogado antes de firmar nada.

Etiquetas: **[V]** verificado en una fuente externa leída para este informe · **[Repo]** dato o código del
repositorio · **[C]** cálculo mío con números del repositorio · **[S]** supuesto sin verificar ·
**[R]** recomendación.

---

## 0. Decisión

**Opción A, con tres añadidos («A+»): el material es del reciclador, Ruma pesa, registra y paga los puntos
del vecino con un tope, y no se alquila local ni se compra prensa en el piloto.**

1. Cada reciclador de «San Pedro» es dueño de lo que recoge y lo vende donde quiera. Ruma no compra, no
   guarda y no revende material.
2. Ruma no le paga tarifa por recojo. Su pago es el material, más rutas con casas que ya separaron.
3. El dueño pesa cada lote en un punto de pesaje, delante del reciclador, y los dos lo aceptan en la app.
4. Los puntos del vecino los paga Ruma de su bolsillo, como costo de captación, con un tope mensual escrito.
   Siguen dependiendo del pesaje.
5. A las 4 semanas se decide con datos si la asociación aporta por kilo o si conviene una venta conjunta
   (variante B', sección 2.4).

Por qué, en cuatro líneas:

- **Legal.** La ley nombra a la asociación formalizada como receptora del material del vecino, y su registro
  municipal la autoriza a recolectar y comercializar. Ruma no es EO-RS: si compra y revende (B y C) entra en
  «comercialización», que exige registro ante el MINAM (sección 1.2).
- **Dinero.** El material de un recojo vale S/ 1,95 a 2,49 y la tarifa actual del rider es S/ 3,10 [Repo][C].
  Pagar la tarifa cuesta más que regalar el material. A+ baja la pérdida por recojo de S/ 2,42 a entre
  S/ 1,00 y 1,78, y evita unos S/ 17.100 de maquinaria y S/ 16.400 de costo físico en dos meses [C].
- **Fraude.** Si Ruma no cobra margen al reciclador, él no gana nada saltándose la app. En B y C sí.
- **Amistad.** El dueño nunca es comprador ni vendedor del material de sus amigos. Solo pesa, con testigo
  y registro. Es el arreglo con menos motivos de pelea.

El costo de esta decisión: en A+ los puntos del vecino dejan de salir del margen del material y pasan a ser
un subsidio de Ruma. Es pequeño (S/ 0,26 a 0,32 por kilo), pero no escala solo. Por eso lleva tope y fecha de
revisión.

---

## 1. Lo verificado

### 1.1 Del repositorio

| Dato | Valor | Fuente |
|---|---|---|
| Recicladores formalizados | 19, Asociación «San Pedro», sin convenio ni presupuesto municipal | `entrevistas/analisis-segregacion-mdsc.md` (respuesta escrita de la MDSC, sin nombre ni cargo) |
| Tarifa del rider | S/ 2,50 por viaje + S/ 0,30 por km; S/ 3,10 por recojo a 2 km | `build_costos.py`, trigger `trg_set_rider_fee` en STAGING |
| Kilos por recojo | 3 kg (supuesto del modelo) | `Ruma_costos_2026-11.xlsx`, hoja «Supuestos» |
| Margen por recojo del modelo | ingreso 2,49 − costo 4,91 = **−S/ 2,42** | hoja «Punto de equilibrio», piloto distrital |
| Composición del costo | rider 3,10 · puntos 1,00 · otros 0,82 (IA, costal, precinto, volantes) | misma hoja |
| Precios | PET S/ 1,00/kg; cartón S/ 0,30/kg en la entrevista al acopio, S/ 0,60 en el catálogo | `supabase/sql/2026-10-28_precios_de_entrevista.sql` (no aplicado) |
| Puntos | 40 % del valor, 1.000 pts = S/ 1. PET 400 pts/kg, cartón 240 pts/kg | memoria de economía de puntos |
| El precio no sube con el volumen | S/ 1.000/t igual para 2 t que para 20 t | misma entrevista al acopio |
| Piloto del modelo | 180 recolectores, 360 recojos y 1.080 kg al mes, 2 riders | `HANDOFF-claude-diagrama-profesora.md` |
| Libro de la profesora | costo físico de 2 meses S/ 24.362,80; compra de equipos S/ 20.979 | `Ruma_costos_diagrama_profesora.xlsx`, hoja 1 |
| Dentro de esa compra | prensa S/ 12.000, transpaleta S/ 1.100, mesa S/ 400, 2 triciclos S/ 3.600 | deducido de la depreciación de la hoja 1 [C] |
| `settle_delivery` | solo la llama el operador del centro; reparte el peso por el conteo del rider; `platform_fee` = bruto − puntos; registra entrada de stock | `supabase/sql/2026-11-06_seguridad_cantidades.sql` |

### 1.2 Normativa leída para este informe

**D.L. 1278, art. 34, texto del D.L. 1501 [V]:** «Los generadores de residuos municipales se encuentran
obligados a entregar los residuos debidamente segregados a asociaciones de recicladores formalizados u
operadores de residuos sólidos debidamente autorizados o a las municipalidades que presten el servicio».
Leído en la compilación con concordancias:
https://snp.org.pe/wp-content/uploads/2022/04/04.-Ley-de-gestion-integral-de-RRSS.pdf

**D.L. 1278, art. 60, texto del D.L. 1501 [V]:** para prestar servicios de residuos «o realizar actividades
de comercialización», las Empresas Operadoras de Residuos Sólidos «deben estar debidamente registradas ante
el Ministerio del Ambiente». Misma compilación.

**Reglamento de la Ley 29419, D.S. 005-2010-MINAM [V]**, texto de 2010 del SPIJ copiado por WIEGO:
https://www.wiego.org/wp-content/uploads/2019/09/Decreto-Supremo-No-005-2010-MINAM_%20Reglamento-Ley-No29419-Regula-Actividad-de-Recicladores-Peru.pdf

- Art. 17: el manejo selectivo, que incluye recolección selectiva, acondicionamiento **y comercialización**,
  lo pueden hacer las organizaciones de recicladores con personería jurídica inscritas en Registros Públicos e
  incorporadas al programa municipal, o las empresas comercializadoras registradas.
- Art. 38: la inscripción en el Registro Municipal de Organizaciones de Recicladores Autorizados «los autoriza
  a realizar los servicios de manejo selectivo de residuos sólidos» en el distrito. Art. 39: requisitos
  (ficha registral, padrón de socios, plan de trabajo) y renovación obligatoria.
- Art. 21: la recolección selectiva puede hacerla una organización formalizada e inscrita en la municipalidad.
- Art. 19: cartón y «todos los plásticos» están entre los residuos autorizados.
- Art. 8: la organización cumple el Plan Técnico Operativo (sectores, rutas, horarios y frecuencias) y remite a
  la municipalidad **un reporte mensual** de lo recuperado.
- Arts. 10 a 12: equipo de protección obligatorio. Arts. 15 y 16: triciclos y motofurgones permitidos; los
  motorizados necesitan tarjeta de propiedad, licencia y SOAT.
- Art. 22: quien almacena y acondiciona debe cumplir los requisitos de infraestructura de la ley de residuos.
- Arts. 46 y 47: los incentivos al vecino previstos son municipales; el «bono» es un descuento de arbitrios
  por ordenanza.

**Verificado solo por resumen de búsqueda, sin leer el texto [S]:**

- La Ley 32212 (2024) modifica, entre otros, los arts. 24, 33, 36 y 60 del D.L. 1278 y **no lista el art. 34**.
  La página devolvió error 403: https://lpderecho.pe/ley-32212-gestion-manejo-residuos-solidos/
  El art. 60 puede haber cambiado otra vez.
- El D.S. 001-2022-MINAM modificó el reglamento de la Ley 29419. No se leyó:
  https://busquedas.elperuano.pe/dispositivo/NL/2028907-1
- Ley 29419, art. 5: los gobiernos locales regulan la actividad de los recicladores. Citado por ordenanzas.

**No verificado [S]:** que «San Pedro» esté inscrita y con renovación vigente en el registro municipal (la
MDSC cita la Resolución de Alcaldía 018-2024-MDSC y la Partida 11240307, sin copia); que sus socios trabajen en
Cartavio; a qué acopio venden y a qué precio; cuántos tienen teléfono con datos.

### 1.3 Lo que la norma no dice

- No encontré artículo que diga de quién es el material una vez entregado. La lectura natural es que pasa al
  reciclador formalizado que lo recibe [S].
- No encontré norma que prohíba ni que regule que un privado premie al vecino con puntos. La ley solo prevé
  incentivos municipales [S].
- Pagar por recojo a una persona a la que la app le fija rutas y horarios puede leerse como relación laboral
  (primacía de la realidad) [S, consultar abogado]. Es otro motivo para no pagar tarifa.

---

## 2. Las opciones

Números por recojo de 3 kg, mitad PET y mitad cartón en peso [S], con el cartón a S/ 0,30 salvo que se indique.
Valor del material: 1,5 × 1,00 + 1,5 × 0,30 = **S/ 1,95** (S/ 2,40 con cartón a 0,60) [C].
Puntos con el catálogo de hoy: 1,5 × 0,40 + 1,5 × 0,24 = **S/ 0,96**. Con el cartón corregido a 120 pts/kg:
S/ 0,78 [C].

### 2.1 Opción A: el reciclador es dueño y vende donde quiera; Ruma pesa, registra y paga puntos

| Flujo | Recorrido |
|---|---|
| Material | vecino → reciclador (dueño) → punto de pesaje → el comprador que el reciclador elija |
| Dinero | comprador → reciclador (todo). Ruma → vecino (puntos, por Yape). Ruma no cobra nada |
| Datos | foto del vecino, conteo en la puerta, peso del lote. Todo queda en Ruma |

- **Quién paga los puntos:** Ruma, de su caja. No hay margen del material del que sacarlos.
  1.080 kg al mes cuestan S/ 346 con el catálogo actual y S/ 281 con el cartón corregido [C].
- **Fraude.**
  - Saltarse la app: el reciclador no gana nada, porque Ruma no le quita ningún porcentaje. El vecino quiere
    sus puntos y pedirá por la app. Es el incentivo más limpio de las tres opciones.
  - Inflar el peso: el riesgo sube. Como el reciclador conserva el material, puede sumar al lote lo que juntó
    en la calle; no le cuesta nada y genera puntos para los vecinos de ese lote. Con una cuenta de su propia
    casa, cobra el 40 % del valor de material ajeno. Hace falta tope (sección 3.3).
  - Material mojado: sube el peso y los puntos; al reciclador le da igual porque el acopio se lo descontará
    a él después. Quien pesa debe rechazar o descontar.
- **Legal:** es la figura del art. 34. El vecino entrega a un reciclador de asociación formalizada, que
  comercializa con su autorización municipal. Ruma presta software y pesaje, no maneja residuos [S sobre que
  pesar no sea «manejo»; consultar].
- **Economía [C]:** margen por recojo 0 − 0,96 − 0,82 = **−S/ 1,78**. Sin costal ni precinto (el reciclador usa
  sus sacos, −0,60) y con el cartón corregido: **−S/ 1,00**. Al mes, con 360 recojos: −S/ 641 a −S/ 360,
  frente a −S/ 872 del modelo. Ingreso de Ruma: cero.
- **Para el reciclador [C]:** S/ 1,95 por parada, menos que los S/ 3,10 de la tarifa. Una ruta de 8 paradas da
  24 kg, unos S/ 15,60. Solo le conviene si las paradas van agrupadas por sector y día, y como añadido a su
  recorrido habitual.
- **Amigos:** nadie le debe dinero a nadie. El único roce posible es el peso.

### 2.2 Opción B: el reciclador es dueño pero vende en el acopio de Ruma a precio publicado; Ruma enfarda y revende

| Flujo | Recorrido |
|---|---|
| Material | vecino → reciclador → acopio de Ruma (compra) → fardos → mayorista |
| Dinero | mayorista → Ruma → reciclador (precio publicado) y vecino (puntos) |
| Datos | igual que A, más stock y ventas |

- **Quién paga los puntos:** Ruma, de la diferencia entre lo que cobra al mayorista y lo que paga al
  reciclador. Si le paga el precio de mercado completo, la diferencia es solo el premio por enfardar.
  Si le paga el 60 % para financiar el 40 % del vecino, el reciclador recibe S/ 1,17 por parada [C].
- **Fraude:** pagarle menos que el acopio de al lado lo empuja a vender por fuera lo mejor y traer a Ruma lo
  peor, o a recoger sin app. Ruma compra, así que el peso mojado y el material ajeno ahora los paga Ruma.
- **Legal:** comprar y revender es comercializar. Ruma necesitaría registro EO-RS ante el MINAM (art. 60) y
  el local, los requisitos de infraestructura. Hoy no los tiene.
- **Economía [C]:** el acopio propio cuesta unos S/ 3.370 al mes (local 1.500, luz y agua 180, operario 1.510,
  prensa 180) para un material que vale S/ 702 al mes. La entrevista dice que el precio no sube con el volumen;
  el premio por fardo no está verificado. Si fuera S/ 0,20 por kilo, harían falta 16.850 kg al mes, unas
  16 veces el piloto.
- **Amigos:** el dueño pasa a ser el comprador que fija el precio a sus amigos. Es la relación que más se rompe.

### 2.3 Opción C: Ruma compra todo y paga al reciclador por recojo o por kilo

| Flujo | Recorrido |
|---|---|
| Material | vecino → reciclador (por cuenta de Ruma) → acopio → comprador |
| Dinero | comprador → Ruma → reciclador (tarifa) y vecino (puntos) |
| Datos | igual que hoy |

- Es el modelo que la app implementa hoy. Los puntos salen del material.
- **Economía [C]:** con solo PET y cartón, 1,95 − 0,96 − 3,10 − 0,82 = **−S/ 2,93** por recojo; el modelo, con
  cinco materiales, da −S/ 2,42. La tarifa sola supera el valor de lo recogido.
- **Fraude:** pago por recojo premia partir recojos y recojos vacíos; pago por kilo premia el peso mojado.
  El reciclador puede quedarse con parte del material, que ya no es suyo.
- **Legal:** la peor. Ruma es dueña y vendedora sin ser EO-RS, y la tarifa con rutas asignadas se parece a un
  empleo. Además compite con la asociación por el mismo material.
- **Amigos:** los convierte en proveedores pagados por Ruma. Cada ajuste de tarifa es un conflicto.

### 2.4 Variante B': venta conjunta de la asociación, con Ruma como servicio

La asociación, no Ruma, junta el material de sus socios, lo vende en lote y reparte. Ruma pesa, lleva la
cuenta de cuánto es de cada socio y cobra una cuota por kilo que financia los puntos. Legalmente es limpia
(vende quien está autorizada) y devuelve los puntos al margen del material. No la recomiendo para empezar:
exige un lugar de acopio de la asociación, capital para adelantar pagos a socios que viven al día y una
confianza en las cuentas que todavía no existe. Es el destino si A+ funciona, no el punto de partida.

### 2.5 Comparación

| | A | B | C | A+ (elegida) |
|---|---|---|---|---|
| Dueño del material | reciclador | reciclador, luego Ruma | Ruma | reciclador |
| Puntos del vecino | caja de Ruma | margen de reventa | margen del material | caja de Ruma, con tope |
| Margen por recojo [C] | −1,78 | −0,82 variable, más ~3.370/mes fijos | −2,93 | −1,00 a −1,78 |
| Motivo para saltarse la app | ninguno | alto | medio | ninguno |
| Riesgo de inflar peso | alto | lo paga Ruma | lo paga Ruma | acotado con tope y doble firma |
| Registro EO-RS de Ruma | no [S] | sí | sí | no [S] |
| Local y prensa | no | sí | sí | no |
| Riesgo para la amistad | bajo | alto | alto | el más bajo |

---

## 3. La opción elegida, en detalle [R]

### 3.1 Flujo

1. El vecino fotografía y pide el recojo, como hoy. Ve «Reciclador formalizado · Asociación San Pedro».
2. El recojo va a un reciclador verificado. Mejor por sector y día fijo que al instante: así cada salida junta
   varias paradas, que es lo único que hace rentable una parada de S/ 1,95.
3. En la puerta el reciclador cuenta, como hoy. Sin cambios.
4. Al terminar la ruta pasa por el punto de pesaje. El dueño pesa por material, toma foto del visor y registra.
   El reciclador acepta el peso en su app. Se lleva su material.
5. Al aceptar, se liquidan los puntos de los vecinos de ese lote.
6. Cada mes Ruma genera el reporte de kilos por material que la asociación debe entregar a la municipalidad.

### 3.2 Qué pone cada parte

| Ruma | El reciclador |
|---|---|
| Casas que ya separaron, con dirección y hora | Recoger en la ventana acordada |
| Pesaje con registro que puede enseñarle a su comprador | Pesar cada lote antes de venderlo |
| El reporte mensual para la municipalidad | Contar en la puerta con honestidad |
| Chaleco, guantes y botas (S/ 835 en el libro de la profesora [Repo]) | Vehículo, sacos y venta por su cuenta |
| Los puntos del vecino | Nada de dinero durante el piloto |

### 3.3 Reglas contra el fraude

- Los puntos se pagan sobre **el menor** entre el peso del lote y el estimado de puerta (conteo × peso medio
  del material) × 1,25. Lo que el reciclador añada de la calle no genera puntos.
- Tope por hogar: 15 kg por semana [S, ajustar con datos]. Lo que pase del tope queda marcado, sin pago
  automático.
- Material mojado o con líquido: quien pesa anota los kilos rechazados; no generan puntos.
- Plazo: lote pesado dentro de las 72 horas del recojo. Vencido el plazo, el operador libera los puntos por el
  estimado de puerta y el reciclador queda marcado. El vecino no paga la demora de otro.
- Ningún reciclador recoge en su propia casa ni en la de su familia directa con su cuenta.

### 3.4 Presupuesto de puntos [C]

| Tamaño del piloto | Kilos al mes | Puntos al mes |
|---|---|---|
| 60 hogares, 120 recojos | 360 | S/ 94 a 115 |
| 180 hogares, 360 recojos (el del modelo) | 1.080 | S/ 281 a 346 |

Tope recomendado: S/ 400 al mes. Si se alcanza, los recojos siguen y los puntos nuevos esperan al mes
siguiente, con aviso en la app. Antes de empezar conviene aplicar la corrección del cartón (240 → 120 pts/kg,
script del 2026-10-28): hoy Ruma pagaría al vecino el 80 % de lo que vale el cartón.

### 3.5 Efecto en los costos [C]

- No se compran prensa, transpaleta, mesa ni triciclos: **S/ 17.100** de los S/ 20.979 de inversión.
- Salen del costo físico de dos meses: riders en planilla 7.365, operario 3.020, local y adecuación 4.560,
  insumos y mantenimiento de prensa y triciclos 1.102, seguridad y SCTR 387. Total **≈ S/ 16.430**; el costo
  físico baja de 24.363 a unos 7.930.
- Se quedan: balanza de plataforma (S/ 239), balanzas colgantes, teléfonos y datos para los recicladores que
  no tengan (en un estudio de Belo Horizonte solo el 11 % de los catadores tenía internet,
  `docs/retencion-incentivos-research.md`), EPP y el supervisor.
- Es un cálculo sobre la hoja 1, no una regeneración del libro. El libro de la profesora es un ejercicio de
  curso y puede quedarse como está.

### 3.6 La relación con los amigos

- Las reglas son las mismas para los 19. La invitación sale por la presidenta de la asociación, no por amistad.
- El dueño no compra, no vende, no presta ni adelanta dinero, y no recomienda comprador.
- Cada pesaje lleva foto del visor y la aceptación del reciclador en su app. Sin aceptación no hay liquidación.
- Los recojos se reparten por sector y turno reservado, nunca a dedo.
- Las disputas de peso se resuelven repesando en el momento. Si persiste, decide la presidenta.
- Todo está escrito, firmado y con fecha de revisión a las 4 semanas. Cualquiera sale cuando quiera.

---

## 4. Qué cambia en la app y en la base de datos

Nada de esto está hecho. Todo va primero a STAGING, en un script fechado en `supabase/sql/`.

### 4.1 Imprescindible para empezar

1. **Punto de pesaje como centro.** Crear una fila en `collection_centers` («Punto de pesaje Ruma · Cartavio»)
   con una columna nueva `weigh_only boolean`. La cuenta del dueño es el operador de ese centro. El panel de
   acopio y `create_delivery` sirven tal cual: el reciclador abre la entrega desde «Entregar en acopio».
2. **`settle_delivery`, rama `weigh_only`:**
   - no inserta la entrada en `center_stock_movements`: el material no entra al stock de Ruma;
   - `platform_fee` deja de ser «bruto − puntos»; guarda el costo del incentivo, −(puntos ÷ 1.000);
   - `gross_amount` se conserva como valor de referencia del material del reciclador, no como ingreso;
   - los puntos por recojo se calculan sobre el menor entre su parte del peso y su estimado de puerta × 1,25,
     con el tope semanal por hogar (dos valores en `platform_settings`);
   - exige `rider_accepted_at` (columna nueva en `deliveries`) y que el operador no sea el rider del lote.
3. **Aceptación del peso.** Una RPC `accept_delivery_weight(delivery_id)` que solo puede llamar el rider de la
   entrega cuando está `pesada`. Un botón en la app del rider.
4. **Tarifa del rider en cero.** Revisar la definición viva de `trg_set_rider_fee`: si lee parámetros, ponerlos
   en 0; si los tiene fijos, que devuelva 0 para riders de asociación. `settle_pickup` sigue igual y paga 0.
5. **Riders cerrados.** En `rider_profiles`: `association text`, `formal_id text` (carné) y `verified_at`. Solo
   el operador verifica. `assign_nearest_rider` y el tablero abierto ignoran a los no verificados.
6. **Textos (Android y PWA, `RumaRepository` y `api.ts` a la par).**
   - Rider: «Ganancia» pasa a «Tu material: 24 kg · valor estimado S/ 15,60» (kg × `v_material_prices`);
     «Entregar en acopio» pasa a «Pesar mi material».
   - Vecino: «Tus puntos se liberan cuando tu reciclador pesa el material (máximo 72 horas)».
7. **Catálogo.** Solo PET y cartón activos; aplicar la corrección del cartón; ocultar el canje de arbitrios,
   que no tiene ordenanza.
8. **Panel.** `dashboard/lib/economia.ts` y `queries.ts` no deben sumar `gross_amount` de entregas `weigh_only`
   como ingreso.

### 4.2 Después, si los datos lo piden

- **Peso en la puerta.** Columna `pickup_items.confirmed_weight_kg` con balanza colgante, protegida por
  `trg_pickup_item_guard` igual que `confirmed_quantity`. El cartón no se cuenta bien por unidades; si la
  diferencia entre puerta y lote sale alta, esto es lo primero.
- **Foto del visor.** `delivery_items.photo_url` y `rejected_kg`. Al inicio basta la foto en el teléfono.
- **Liberación automática a las 72 horas.** Al inicio la hace el operador a mano una vez por semana.
- **Rutas por sector y día** en lugar de despacho al más cercano. Al inicio se resuelve con el turno reservado
  que ya existe (`RiderShiftWorker`) y un día fijo por zona comunicado a los vecinos.
- **Reporte mensual** por asociación y material: una vista en `ruma_analytics` y exportar CSV.

### 4.3 Respuestas directas

- **Rol del rider:** socio verificado de la asociación, dueño de su carga, sin tarifa.
- **Quién pesa:** el dueño, como operador del punto de pesaje, con balanza de plataforma de 300 kg, por
  material, delante del reciclador.
- **Cómo se registra:** en `delivery_items`, por el panel de acopio que ya existe, más la aceptación del rider.
- **¿Los puntos dependen del pesaje?** Sí. Pendientes al completar el recojo, disponibles al liquidar el lote.
  El pesaje sigue siendo el momento de pago, que es lo que recomienda la investigación de retención.

---

## 5. Borrador de acuerdo con la asociación

Para revisión de un abogado. No es un contrato de trabajo ni de compraventa.

1. **Partes y objeto.** Ruma y la Asociación de Recicladores «San Pedro» acuerdan un piloto de 4 semanas en
   Cartavio para recoger PET y cartón separados por los vecinos que usan la app Ruma.
2. **Propiedad del material.** Todo lo que un socio recoge es suyo desde que el vecino se lo entrega. Lo vende
   a quien quiera y al precio que consiga. Ruma no compra, no vende ni almacena material.
3. **Sin pago ni relación laboral.** Ruma no paga tarifa, sueldo ni comisión. Cada socio decide qué turnos
   toma. Ruma no fija horarios obligatorios ni exclusividad.
4. **Qué da Ruma.** La app, los pedidos de recojo, el pesaje con registro, el reporte mensual de kilos para
   la municipalidad y un equipo de protección por socio participante.
5. **Qué da el socio.** Recoger en la ventana acordada, contar en la puerta, tratar bien al vecino y pesar
   cada lote en el punto de pesaje dentro de las 72 horas y antes de venderlo.
6. **Pesaje.** Se pesa por material, con el socio presente, y se guarda foto del visor. El peso vale cuando el
   socio lo acepta en su app. Si no está de acuerdo, se repesa en el momento.
7. **Puntos del vecino.** Los paga Ruma, con su dinero, según el peso aceptado y hasta el tope mensual que
   Ruma fije. El socio no descuenta nada de lo suyo.
8. **Juego limpio.** No se añade al lote material que no venga de recojos de la app. No se recoge en la casa
   propia ni de familiares directos con la cuenta de socio. El material mojado o sucio se descuenta del peso.
9. **Reparto de recojos.** Por sector y turno, con las mismas reglas para todos los socios. La asociación
   entrega el padrón de participantes y Ruma verifica cada cuenta.
10. **Datos.** Ruma usa los datos de recojos y pesos para operar y para reportes agregados. La asociación recibe
    los suyos cada mes. Los datos personales de socios y vecinos se tratan según la Ley 29733.
11. **Faltas y salida.** Tres recojos incumplidos sin aviso o una falta al punto 8 suspenden la cuenta, con
    aviso a la presidenta. Cualquier parte termina el acuerdo con 7 días de aviso, sin deudas.
12. **Revisión.** En la semana 4 las partes revisan los resultados y deciden si siguen igual, si la asociación
    aporta por kilo para los puntos o si pasan a una venta conjunta. Nada cambia sin acuerdo escrito.

Anexos que pedir antes de firmar: constancia vigente del registro municipal de la asociación, Resolución de
Alcaldía 018-2024-MDSC, padrón de socios y, si se puede, un visto bueno escrito de la municipalidad.

---

## 6. Métricas a 4 semanas

Los umbrales son propuestas [R]: no hay referente peruano publicado. La semana 0 fija la línea base preguntando
a cada reciclador cuántos kilos y cuántas horas hace hoy.

| Métrica | Cómo se mide | Señal de que funciona |
|---|---|---|
| Recicladores activos | socios con al menos una ruta en la semana 4 | 5 o más de los 19 |
| Kilos por hora de ruta | kg pesados ÷ horas entre primer recojo y pesaje | 30 % más que su línea base |
| Paradas por ruta y kg por parada | `pickups` por entrega; mediana de kg | 6 o más paradas; 3 kg o más |
| Recojos cumplidos en su ventana | `completed` ÷ asignados | 85 % o más |
| Lotes pesados en 72 horas | entregas `liquidada` a tiempo | 90 % o más |
| Diferencia puerta contra lote | (peso del lote − estimado de puerta) ÷ estimado | mediana dentro de ±15 %; menos de 1 de cada 10 lotes sobre +25 % |
| Hogares que repiten | hogares con 2 o más recojos pesados en 4 semanas | la mitad o más |
| Costo de puntos | S/ pagados ÷ kg pesados; total del mes | S/ 0,32 por kg o menos; bajo el tope |
| Costo neto de Ruma por kilo | (puntos + variables) ÷ kg | menor que los S/ 0,81 por kg del modelo (2,42 ÷ 3) |
| Recojos sin app | encuesta a 10 hogares: «¿le recogieron sin pedirlo en la app?» | 1 o ninguno |
| Disputas de peso | pesajes no aceptados | 2 o menos, todas resueltas |
| Disposición a aportar | pregunta a cada socio activo: ¿seguiría?, ¿aportaría S/ 0,10 por kilo? | 70 % seguiría |

Regla de decisión en la semana 4:

- Kilos por hora suben y los socios siguen: continuar y negociar el aporte por kilo.
- Los socios siguen pero la diferencia de peso es alta: añadir el peso en la puerta antes de crecer.
- Menos de 3 socios activos o kg por parada bajo 2: el material solo no paga la parada. Probar un bono de
  arranque de S/ 0,50 por parada verificada, con tope, o cerrar el piloto.

---

## 7. Preguntas que hay que hacer antes de empezar

1. A la presidenta: ¿cuántos socios trabajan en Cartavio, con qué vehículo y cuántos tienen teléfono con datos?
2. A los socios: ¿a quién venden hoy el PET y el cartón, a qué precio y cuántos kilos hacen al día?
3. A los socios: ¿irían por 3 kg a una casa?, ¿cuántos kilos tiene que haber para que valga el viaje?
4. A la municipalidad: ¿la asociación está inscrita y vigente en el registro municipal?, ¿puede Ruma operar
   la app y el pesaje con ellos sin registro EO-RS?
5. Al abogado: los tres supuestos de la sección 1.3 y el estado del art. 60 tras la Ley 32212.
