# Mínimo de canje y descuento de arbitrios: efecto en el negocio

Fecha: 2026-11-11. Investigación de escritorio. No se modificó código, base de datos ni otros documentos;
STAGING se leyó en solo lectura y producción no se tocó.

Complementa `docs/resenas-usuarios-diseno.md` (Q3: el umbral lejano), `docs/retencion-incentivos-research.md`
y `entrevistas/analisis-segregacion-mdsc.md`.

## Cómo leer este documento

- **[V]** verificado en una fuente externa, con enlace. Si solo se vio en el resumen del buscador y no en la
  página, se dice.
- **[R]** leído en el repositorio o en STAGING el 2026-11-11.
- **[S]** supuesto o cálculo propio. Los números de hogares, kilos y recojos del piloto son supuestos del
  modelo de costos, no mediciones.
- **[REC]** recomendación.

**Dato nuevo del dueño (2026-11-11), no documentado por escrito:** la municipalidad le dijo que el descuento
de arbitrios por reciclar es factible si lo aprueba el Concejo Municipal. La entrevista escrita anterior decía
que hoy no existe. Todo el capítulo 3 parte de ese dato verbal.

---

## 0. Resumen

1. **Hoy el primer cobro por Yape queda a 6-8 recojos (3-4 meses) para un hogar normal y a 17 recojos para
   quien entrega una bolsa chica de botellas.** Es exactamente el «nunca voy a cobrar» de las reseñas.
2. **El mínimo que logra el primer cobro en 2-3 recojos es S/ 1 (1.000 pts).** Con S/ 2 un hogar probable
   necesita 4 recojos; solo llega en 3 si cada recojo trae 3 kg, que es el supuesto optimista del modelo.
3. **Bajar el mínimo casi no cuesta caja**: el adelanto es de unos S/ 300-400 una sola vez en un piloto de
   180 hogares. Lo que sí cuesta es el trabajo de pagar muchos canjes chicos, y eso se controla pagando una
   vez por semana y canjeando todo el saldo, no lotes fijos.
4. **No se encontró ninguna tarifa pública para que una empresa envíe pagos por Yape.** Existen dos
   productos (Pagos Masivos Yape en Telecrédito y la API de dispersión vía procesadores), ambos sin precio
   publicado. Hay que cotizarlos antes de escalar; en el piloto el pago es manual.
5. **El descuento de arbitrios es legal y tiene precedentes** (art. 70 del D.L. 1278; art. 47 del reglamento
   de la Ley 29419; Lurigancho-Chosica 15 % en 2024; Huacho 30 % sobre deuda atrasada en 2016). Se aprueba por
   ordenanza del Concejo.
6. **Con S/ 55 al año y 78,9 % de morosidad, el arbitrio no reemplaza a Yape**: es un segundo canje que no le
   cuesta efectivo a Ruma y que a la municipalidad le sirve sobre todo como programa para ponerse al día.
7. **Hallazgo de seguridad:** la base no valida el mínimo ni el monto del canje; los manda el cliente.
   Debe cerrarse antes de pagar canjes reales (sección 5).

---

## 1. Economía por recolector

### 1.1 Qué hay hoy en STAGING [R]

`redemption_options` (leído el 2026-11-11):

| Código | Nombre | Regla | `min_points` | Orden | Activo |
|---|---|---|---:|---:|---|
| `YAPE_PLIN` | Yape / Plin | 1.000 pts = S/ 1,00 · Mínimo S/ 5,00 | 5.000 | 1 | sí |
| `MUNI_ARBITRIOS` | Descuento en Arbitrios | 1.000 pts = S/ 1,00 | 1.000 | 2 | sí |
| `STORE_DISC` | Descuento Tiendas Eco | 500 pts = 5 % | 500 | 2 | sí |
| `DONATE_TREE` | Donar a Reforestación | 200 pts = 1 árbol | 200 | 3 | sí |

`materials` y `v_material_prices`:

| Material | Precio de acopio | `points_per_kg` | Por unidad |
|---|---:|---:|---|
| PET | S/ 1,00/kg | 400 | 10 pts por botella (25 g) |
| Cartón | S/ 0,60/kg | 240 | 12 pts por pieza (50 g) |

Dos cosas que cambian las cuentas:

- **El cartón está sobrepagado.** El acopio entrevistado paga S/ 0,30/kg, no S/ 0,60. El script
  `supabase/sql/2026-10-28_precios_de_entrevista.sql` lo corrige a 120 pts/kg pero no está aplicado en ningún
  entorno: es una decisión de negocio pendiente. Las tablas de abajo dan los dos casos.
- **Cada canje es un lote fijo de `min_points`.** Android y la PWA canjean exactamente `min_points`
  (`GeneratorViewModel.kt` `buildWallet`, `Family.tsx` `createRedemption(o.id, o.kind, o.min_points, …)`).
  Con mínimo S/ 5, cada cobro es de S/ 5; con mínimo S/ 1, un saldo de S/ 3 son tres canjes.

### 1.2 Puntos por recojo [S sobre precios R]

No hay ningún peso medido por recojo en Cartavio. Los tamaños son supuestos de los documentos del repo.

| Recojo | Kg | Puntos (catálogo) | En soles | Puntos si el cartón baja a 120/kg |
|---|---:|---:|---:|---:|
| A. Bolsa chica: 30 botellas PET (`retencion-incentivos-research.md`) | 0,75 | 300 | S/ 0,30 | 300 |
| B. Hogar probable: 40 botellas + 1 kg de cartón | 2 | 640 | S/ 0,64 | 520 |
| C. Modelo de costos: 3 kg, mitad PET y mitad cartón | 3 | 960 | S/ 0,96 | 780 |
| D. 3 kg solo PET (120 botellas) | 3 | 1.200 | S/ 1,20 | 1.200 |
| E. 3 kg solo cartón | 3 | 720 | S/ 0,72 | 360 |
| F. `dashboard/lib/economia.ts`: 5 kg, mitad y mitad | 5 | 1.600 | S/ 1,60 | 1.300 |

El caso B es el que me parece más cercano a un hogar que junta dos semanas [S]: 120 botellas en quince días
(caso D) es mucho para una familia. El libro `Ruma_costos_2026-11.xlsx` usa 3 kg por recojo y 2 recojos al
mes por hogar activo, ambos rotulados SUPUESTO, y da S/ 0,996 de puntos por recojo con su mezcla de cinco
materiales.

### 1.3 Recojos hasta el primer cobro

Recojos necesarios (redondeado hacia arriba), precios del catálogo:

| Mínimo Yape | A (300) | B (640) | C (960) | D (1.200) | E (720) | F (1.600) |
|---|---:|---:|---:|---:|---:|---:|
| **S/ 5 (hoy)** | 17 | 8 | 6 | 5 | 7 | 4 |
| S/ 2 | 7 | 4 | 3 | 2 | 3 | 2 |
| S/ 1,50 | 5 | 3 | 2 | 2 | 3 | 1 |
| **S/ 1** | 4 | 2 | 2 | 1 | 2 | 1 |

Con 2 recojos al mes [S], hoy el primer cobro tarda entre 2 y 8,5 meses. Si se aplica el precio real del
cartón, B y C necesitan 10 y 7 recojos para llegar a S/ 5; con S/ 1 siguen llegando en 2.

**Mínimo que logra 2-3 recojos:** S/ 1 lo logra en todos los casos salvo la bolsa chica (4). S/ 1,50 lo logra
para B y C. S/ 2 solo lo logra si el recojo trae 3 kg o más. Como el peso real por recojo es desconocido y
probablemente menor que 3 kg, **S/ 1 es el único valor que cumple el objetivo sin depender del supuesto
optimista** [REC].

Nota aparte: un recojo de 0,75 kg le cuesta a Ruma S/ 3,10 de rider (S/ 2,50 + S/ 0,30/km × 2 km, libro de
costos) para un material que vale S/ 0,75. Ningún mínimo de canje arregla eso; lo arregla juntar más por
recojo. Es una razón para no premiar recojos chicos y frecuentes.

---

## 2. Costos y riesgos de bajar el mínimo

### 2.1 Costo por transferencia Yape/Plin

**Verificado**

- Entre personas, Yape y Plin se transfieren sin comisión desde la interoperabilidad de 2023 [V, visto en el
  resumen del buscador] ([Perú Retail](https://www.peru-retail.com/desde-hoy-ya-puedes-transferir-dinero-entre-yape-y-plin-sin-costo-adicional/),
  [La República](https://larepublica.pe/economia/2023/03/31/yape-desde-el-12-de-abril-todos-los-usuarios-podran-transferir-dinero-a-plin-1290592)).
- Yape tiene una herramienta de dispersión para que empresas paguen a números de celular. Se anunció el
  22/04/2025 y se contrata a través de procesadores de pago (Kashio, Prontopaga, Monnet). **La nota no publica
  comisión ni límites** [V] ([Perú Retail](https://www.peru-retail.com/yape-anuncia-nueva-solucion-para-que-empresas-realicen-pagos-directos-a-usuarios/)).
- Telecrédito BCP ofrece pagos masivos de haberes y proveedores y «Pago de haberes Yape»; las cuentas de
  ahorros no permiten pagos masivos, hace falta cuenta corriente [V, visto en el resumen del buscador]
  ([BCP](https://www.viabcp.com/empresas/telecredito-afiliacion)). **Sin tarifa publicada en lo que se pudo leer.**
- La comisión de 2,95 % de Yape Empresas es sobre las transacciones de cuentas de negocio que reciben más de
  S/ 25.750 al mes, según prensa de agosto de 2024 [V] ([La República](https://larepublica.pe/economia/2024/08/13/comision-de-295-en-yape-a-quienes-les-cobra-por-hacer-transferencias-yape-empresa-atmp-509561)).
  La nota no dice que se aplique a enviar dinero. Ruma no recibe pagos por Yape, así que no es su costo.

**No verificado (hay que cotizar)**

- El precio por pago de la dispersión Yape y de Pagos Masivos Yape en Telecrédito. No se encontró ningún
  tarifario; la página de la cuenta corriente pyme del BCP devolvió error 403.
- Los límites de Yape personal (S/ 500 por operación, S/ 2.000 al día) salen de prensa de 2024-2025 vista solo
  en el resumen del buscador ([Expreso](https://www.expreso.com.pe/economia/yape-sepa-como-pasar-de-transferir-s-500-a-s-2000-guia-completa-bcp-noticia/1217128/)).
  Confirmarlos en la app.
- Si los términos de Yape permiten usar una cuenta personal para pagar a clientes de un negocio. No se revisó.
- Plin, Interbank y otras billeteras: no se encontró ninguna tarifa empresarial.

El libro de costos ya lo trataba así: «Comisión por enviar pagos: 0 % — SUPUESTO, por verificar con el banco»
(`Supuestos` fila 33).

**Lo que significa [S]:** en el piloto se paga a mano desde un Yape personal, con comisión cero y costo de
tiempo. Con unos 180 canjes al mes de S/ 1-2 no se acerca a los límites diarios. A escala de ciudad hace falta
dispersión por API, y su precio decide el mínimo regular: si un pago costara, por ejemplo, S/ 0,50 [S, cifra
inventada para ilustrar], un canje de S/ 1 perdería la mitad en comisión y uno de S/ 2 la cuarta parte.

### 2.2 Efecto en caja

Datos del libro [R]: piloto de 180 hogares activos, 360 recojos al mes, S/ 358,56 de puntos emitidos al mes,
plazo de cobro de ventas de 30 días e inventario de 7 días (los tres últimos son SUPUESTO).

- Los puntos pasan a disponibles cuando el acopio pesa (`settle_delivery`). Ruma cobra ese material, según
  el supuesto del libro, 30 días después. Con canje inmediato, Ruma adelanta hasta un mes de puntos: unos
  **S/ 360 en el piloto**, unos S/ 5.000 en el escenario Ciudad (S/ 4.980 de puntos al mes).
- Con el mínimo de S/ 5 el hogar tarda unos 2,5 meses en poder cobrar, así que financia a Ruma: el saldo sin
  canjear ronda S/ 2,50 por hogar, unos S/ 450 en total. Con mínimo de S/ 1 baja a S/ 0,50-1 por hogar, unos
  S/ 90-180. **La diferencia es un adelanto único de S/ 270-360** [S].
- Para comparar: el mismo piloto paga S/ 1.116 al mes a los riders (360 × S/ 3,10) y tiene un costo total de
  S/ 16.590 al mes (`HANDOFF-claude-diagrama-profesora.md`). El mínimo de canje no mueve la caja.
- Si el acopio paga al contado al pesar, el adelanto desaparece. Esa pregunta («¿Paga en efectivo al
  momento?») sigue sin respuesta en `entrevistas/analisis-segregacion-mdsc.md` §6.2.
- El libro supone que el 100 % de los puntos se canjea, así que no cuenta con el ahorro de los puntos que
  nadie cobra. Un mínimo alto sí genera ese «ahorro», pero es el mismo hogar que se fue pensando que Ruma no
  paga. No es un ahorro que convenga buscar.

### 2.3 Fraude con montos pequeños

- **El mínimo no crea puntos.** Los puntos solo se vuelven disponibles tras el pesaje en acopio. Bajar el
  mínimo no abre una forma nueva de ganar puntos.
- **Sí acorta el tiempo para detectar.** Con S/ 5, un pesaje inflado o una colusión entre rider y familia
  tarda meses en salir como efectivo; con S/ 1 sale en días. Pagar una vez por semana devuelve una ventana de
  revisión de hasta 7 días sin que el hogar lo sienta como espera [REC].
- **El «primer canje rebajado» no se puede explotar** con cuentas duplicadas: no regala dinero, solo baja el
  umbral. Lo que sí se explotaría es un bono de bienvenida; no se propone ninguno.
- **Número de Yape equivocado o ajeno.** Hoy el canje no pide ni muestra el número de destino
  (`resenas-usuarios-diseno.md` Q6). Con más pagos, más errores irreversibles.
- **La base no valida el canje [R].** El disparador `validate_redemption` solo comprueba que
  `points_spent` sea positivo y no supere el saldo. La política de inserción solo exige `user_id = auth.uid()`.
  `points_spent`, `monetary_value`, `kind` y `option_id` los manda el cliente: un cliente modificado puede
  canjear por debajo del mínimo, o gastar 10 puntos declarando `monetary_value` 500. Mientras el operador
  pague leyendo `monetary_value`, eso es dinero. No se probó (habría sido escribir); se deduce del texto del
  disparador y de la política. Rompe la regla del proyecto «el cliente pide, la base decide».

### 2.4 Costo operativo de muchos canjes chicos

Supuestos [S]: S/ 1,92 de puntos por hogar al mes (dos recojos del caso C), 180 hogares, 1,5 minutos por pago
manual (abrir, verificar nombre, enviar, marcar como pagado) y S/ 10 la hora de quien paga.

| Escenario | Canjes al mes | Monto medio | Horas al mes | Costo de tiempo por canje | Sobre el monto |
|---|---:|---:|---:|---:|---:|
| S/ 5, lotes fijos (hoy) | 70 | S/ 5 | 1,7 | S/ 0,25 | 5 % |
| S/ 2, lotes fijos | 175 | S/ 2 | 4,4 | S/ 0,25 | 12,5 % |
| S/ 1, lotes fijos | 350 | S/ 1 | 8,7 | S/ 0,25 | 25 % |
| S/ 1, todo el saldo, pago semanal | ~180 | ~S/ 1,90 | 4,5 | S/ 0,25 | 13 % |

El problema de S/ 1 no es el mínimo sino el lote fijo. Canjeando todo el saldo y pagando en una tanda
semanal, S/ 1 cuesta lo mismo que S/ 2.

### 2.5 Comparación de escenarios

| | S/ 5 (actual) | S/ 2 | S/ 1 | Primer canje a S/ 1, luego S/ 2 | Canje acumulado mensual |
|---|---|---|---|---|---|
| Recojos al primer cobro (hogar B / C) | 8 / 6 | 4 / 3 | 2 / 2 | 2 / 2 | 2 / 2, pero espera al cierre |
| Tiempo al primer cobro | 3-4 meses | 1,5-2 meses | 1 mes | 1 mes | 1-2 meses |
| Canjes al mes (180 hogares) | 70 | 175 | 180-350 | ~180 el primer mes, luego ~90-175 | 180, en un solo día |
| Adelanto de caja (único) | 0 | ~S/ 200 | ~S/ 300-360 | ~S/ 250-300 | ~S/ 150 |
| Ventana para detectar fraude | meses | semanas | días (7 con pago semanal) | igual que S/ 1 al inicio | hasta 30 días |
| Riesgo de «nunca voy a cobrar» | alto | medio | bajo | bajo | medio: vuelve la espera sin fecha |
| Cambio en la app | ninguno | un dato | un dato | columna y regla nuevas | regla y textos nuevos |
| A escala, con comisión por pago | cómodo | aceptable | caro | aceptable | el más barato |

El canje acumulado mensual es el más barato de operar, pero reintroduce justo lo que las reseñas castigan: un
saldo que no se sabe cuándo llega. Solo funciona si la fecha de pago se muestra y se cumple («Se paga el
primer viernes del mes»). El pago semanal da casi el mismo ahorro con una espera que se tolera.

---

## 3. El descuento de arbitrios como canje alternativo

### 3.1 Base legal

- **D.L. 1278, art. 70** (texto del D.L. 1501): «Las municipalidades pueden efectuar descuentos en el pago de
  arbitrios como incentivo a los ciudadanos que cumplan con realizar la segregación en la fuente de los
  residuos que generan, en el marco del Programa de Segregación en la Fuente y Recolección Selectiva, entre
  otro tipo de incentivos» [V, leído en el texto]
  ([compilación con concordancias](https://snp.org.pe/wp-content/uploads/2022/04/04.-Ley-de-gestion-integral-de-RRSS.pdf)).
  Esto confirma el número de artículo que `analisis-segregacion-mdsc.md` dejaba pendiente.
- **Ley 32212** (El Peruano, 21/12/2024) volvió a modificar ese segundo párrafo y **mantiene la facultad**:
  descuentos a los generadores que cumplan su obligación de segregar, «entre otro tipo de incentivos que
  considere pertinente» [V, leído a través de un resumen de la página, no del PDF]
  ([El Peruano](https://busquedas.elperuano.pe/dispositivo/NL/2356497-1)). Era el pendiente de la entrevista.
- **D.L. 1278, art. 53:** los concejos «deben aprobar estrategias para avanzar hacia la sostenibilidad
  financiera del servicio de limpieza pública, aumentar la recaudación y reducir la morosidad» [V, misma
  compilación]. Es el argumento para el Concejo: es una obligación suya, bajo responsabilidad funcional.
- **Reglamento de la Ley 29419 (D.S. 005-2010-MINAM), arts. 46 y 47** [V, leído en el texto]
  ([copia en WIEGO](https://www.wiego.org/wp-content/uploads/2019/09/Decreto-Supremo-No-005-2010-MINAM_%20Reglamento-Ley-No29419-Regula-Actividad-de-Recicladores-Peru.pdf)).
  El art. 47 crea el «bono de incentivo» para los vecinos que segregan y entregan «a los recicladores
  autorizados por la Municipalidad […] al menos una vez por semana», y dice que el bono «será regulado por
  ordenanza municipal y permitirá un descuento en el pago de arbitrios del siguiente trimestre o año, siempre
  y cuando los pagos hayan sido efectuados puntualmente».
- **Ley 29419, art. 7** (programas de incentivos a la segregación): citado en los considerandos de la
  ordenanza de Lurigancho-Chosica; el texto de la ley no se leyó directamente.
- **Reglamento del D.L. 1278 (D.S. 014-2017-MINAM):** no se encontró en él un artículo sobre descuento de
  arbitrios. **No verificado.**

Tres consecuencias prácticas:

1. **Es facultad, no obligación.** Depende del voto del Concejo.
2. **El art. 47 trae dos condiciones que chocan con Cartavio.** «Pagos puntuales» deja fuera al 79 % moroso
   si se aplica literal. Y «recicladores autorizados por la Municipalidad» no describe a los amigos del dueño
   que hoy hacen de riders. El art. 70 del D.L. 1278, que es norma de mayor rango y posterior, es más amplio
   («entre otro tipo de incentivos»); la ordenanza debería apoyarse en él y resolver ambas cosas de forma
   expresa. **Requiere abogado.**
3. **Ratificación provincial: por confirmar.** El art. 40 de la Ley Orgánica de Municipalidades exige que las
   ordenanzas tributarias distritales sean ratificadas por la municipalidad provincial (aquí, Ascope). La
   ordenanza de Lurigancho-Chosica cita ese artículo y no menciona ratificación. Si un descuento necesita o
   no ese paso es lo primero que hay que preguntarle a la asesoría jurídica de la MDSC [S, de conocimiento
   general, no leído hoy].

### 3.2 Municipalidades que ya lo hacen

| Municipalidad | Norma | Descuento | Condiciones | Estado de la verificación |
|---|---|---|---|---|
| Lurigancho-Chosica (Lima) | Ordenanza 353-MDL, publicada el 08/07/2024 | 15 % del arbitrio de recojo de residuos | Empadronados en el «Programa RECICLA»; vigencia de 3 años; no devuelve pagos anteriores; el alcalde reglamenta por decreto | [V] [El Peruano](https://busquedas.elperuano.pe/dispositivo/NL/2304145-1). El anexo operativo no se pudo leer |
| Huacho (Prov. de Huaura) | O.M. 019-2016/MPH, 15/07/2016, «Bono Separa Ya» | **30 % de la deuda de arbitrios del año más antiguo pendiente** | Casa habitación; mínimo 2 meses segregando; registrado en el padrón; **aun con deuda en cobranza coactiva**; se pierde los meses en que no se segrega; los recicladores envían reporte mensual; solo 2016-2017 | [V, leído en el texto] [SINIA](https://sinia.minam.gob.pe/sites/default/files/siar-lima/archivos/public/docs/o.m_019-2016-mph.pdf) |
| Villa El Salvador (Lima) | Ordenanzas 174, 181 y 246-MVES, «Bono Verde», desde 2004 | 20 % mensual del arbitrio de limpieza | Un ticket por recojo semanal con **mínimo 5 kg**; 4 tickets = el descuento del mes; supervisión inopinada | [V, leído en el texto] [informe técnico MVES](https://munives.gob.pe/WebSite/file/info_bono.pdf). El informe es de alrededor de 2012; vigencia actual sin confirmar |
| Villa María del Triunfo (Lima) | Decreto de Alcaldía 010-2016-MVMT/A, «Bono Ambiental» | 20 % de la tasa de limpieza | 12 tickets = descuento del trimestre | [V] [SINIA](https://sinia.minam.gob.pe/normas/otorgan-incentivo-economico-tributario-traves-bono-ambiental-villa). Figura como vigente en SINIA |
| San Juan de Lurigancho (Lima) | Ordenanza de 2016, «Ecobono» (número no hallado) | 10 % de arbitrios de limpieza | **Estar al día**; entregar al menos una vez por semana | [V, prensa] [El Popular](https://elpopular.pe/actualidad-y-policiales/2016-06-06-san-juan-de-lurigancho-dara-incentivo-tributario-vecinos-que-reciclen) |

**Lo que no se encontró**, pese a buscarlo:

- **San Isidro, Miraflores y Surco:** ninguna ordenanza de descuento de arbitrios por reciclar. De Surco solo
  aparecieron beneficios por pronto pago y por ponerse al día. Miraflores y Jesús María tienen un plan de
  incentivos con CBC y Cencosud, sin indicar que sea sobre arbitrios
  ([Expreso](https://www.expreso.com.pe/actualidad/miraflores-y-jesus-maria-repotencian-sus-programas-de-reciclaje/847204/)).
  No los cite como precedentes.
- **Trujillo y La Libertad:** ninguna ordenanza que ate reciclaje y arbitrios. La provincial tiene la O.M.
  014-2024-MPT de segregación y una app «Recicla Trujillo» mencionada por GIZ, sin descuento confirmado.
- **Resultados de esos programas:** ninguna cifra de cuántos vecinos usaron el descuento ni de cuánto bajó la
  morosidad. El informe de Villa El Salvador admite que parte de los vecinos terminó vendiendo su material
  por su cuenta. Los porcentajes de arriba son lo que dice la norma, no lo que logró.

Dos lecturas útiles para Cartavio:

- **Huacho es el precedente para la morosidad:** el descuento se aplica a la deuda vieja, incluso en
  coactiva. Es el modelo «recicla y ponte al día».
- **Todos son porcentajes fijos por participar, no un monto por kilo o por punto.** Ninguno paga «S/ 1 por
  cada 1.000 puntos». La propuesta de Ruma sería nueva en eso, y por lo mismo conviene llevar las dos
  variantes (sección 3.4).

### 3.3 Quién financia el descuento

- En los cinco precedentes **la municipalidad renuncia a recaudación**. No hay un privado que reponga el
  dinero. Ruma no paga en efectivo.
- **Punto delicado que la municipalidad va a notar [S]:** el punto de Ruma representa el 40 % del valor de un
  material que Ruma ya vendió. Si el vecino canjea por arbitrio y la municipalidad pone el descuento, Ruma se
  queda con ese 40 %. Hay que decirlo en el convenio y no dejar que lo descubran. Tres formas honestas de
  plantearlo:
  1. **Ruma retiene el efectivo y entrega a cambio** la operación del Programa RECICLA sin costo, el padrón
     digital y el reporte mensual para SIGERSOL. Es lo que pide el encargo y lo que más caja ahorra.
  2. **Cofinanciado:** Ruma transfiere a la municipalidad una parte (por ejemplo la mitad) de lo canjeado.
     Ahorra menos caja y es más fácil de aprobar.
  3. **Bono por participación, sin gastar puntos:** un porcentaje fijo para el hogar activo, como en Lurigancho
     o Villa El Salvador. No ahorra caja a Ruma, pero es la figura con más precedentes y la más simple.

### 3.4 Cuántos puntos equivaldrían y qué tope

Arbitrio de vivienda: S/ 55 al año [dato de la MDSC, estructura de 2013; falta el cuadro tarifario].

- Misma tasa que Yape: **1.000 pts = S/ 1 de descuento.** Dos monedas distintas confunden.
- Un hogar del caso C genera unos 23.000 pts al año (S/ 23); uno del caso B, unos 15.400 (S/ 15); con el
  precio real del cartón, S/ 12-19. Es decir, **entre el 22 % y el 42 % de su arbitrio**.
- Pagar los S/ 55 completos exige 55.000 pts: 137 kg de PET al año.
- **Tope propuesto [REC]: 50 % del arbitrio anual, S/ 27,50 = 27.500 pts por predio y por año.** Casi ningún
  hogar lo alcanza, así que no frustra, y le da a Rentas un techo exacto de lo que puede dejar de cobrar.
- Canje desde 1.000 pts, en múltiplos de 1.000. Se alcanza en 2 recojos, igual que el Yape propuesto.

### 3.5 Cómo ayuda con la morosidad

Cifras de la MDSC: 4.150 predios, 875 pagan (21,1 %). Recaudación estimada: 875 × S/ 55 = S/ 48.125 al año [S].

Supuesto de trabajo [S]: 180 hogares en Ruma, con la misma proporción de pagadores que el distrito (38 pagan,
142 no), y S/ 20 de puntos por hogar al año llevados íntegros al arbitrio.

| Diseño | Qué pasa | Efecto en la recaudación municipal |
|---|---|---|
| A. Solo para quien está al día (art. 47 literal) | 38 hogares descuentan S/ 20 | −S/ 760 al año (1,6 % de lo recaudado). Con el tope, máximo −S/ 1.045 |
| B. A más «recicla y ponte al día» (tipo Huacho) | Además, un moroso puede aplicar sus puntos si paga el resto del año en curso: paga S/ 35 que antes no pagaba | +S/ 35 por cada moroso que entra. Con 22 de 142 (15 %) la municipalidad ya queda igual que antes; con 28 (20 %) gana unos S/ 220 netos |
| C. Bono fijo del 20 % al hogar activo | 38 hogares descuentan S/ 11 | −S/ 418 al año |

Lectura:

- El costo fiscal es mínimo en cualquier diseño: menos de S/ 1.100 al año para 180 hogares.
- **El diseño B es el único que puede dejarle dinero a la municipalidad**, porque convierte a quien hoy paga
  cero. Es el argumento central ante el Concejo, y encaja con el art. 53.
- Para el vecino moroso, B cambia la cuenta: en vez de S/ 55 que no piensa pagar, S/ 35 más sus botellas.
  Si eso basta para que pague es una hipótesis: ninguno de los precedentes publica resultados.
- Lo que no se debe prometer: que el descuento por sí solo mueva al 79 %. Para quien no paga y no piensa
  pagar, el arbitrio vale cero y el Yape sigue siendo el premio.

### 3.6 Qué datos de Ruma necesita la municipalidad

Para justificar la ordenanza (informe técnico) y para aplicarla (Rentas):

| Dato | Para qué | ¿Existe hoy? [R] |
|---|---|---|
| Viviendas participantes por mes y por sector | Indicador del Programa RECICLA; padrón que hoy no tienen | Sí: `profiles`, `pickups` |
| Kg pesados por material y por mes | Reporte SIGERSOL; toneladas desviadas del relleno | Sí: pesaje en acopio (`deliveries`) |
| Frecuencia de entrega por vivienda | El art. 47 pide entrega semanal; la ordenanza puede fijar otra | Sí: `pickups` completados |
| Código de contribuyente o de predio de cada hogar | Rentas no puede aplicar un descuento a un usuario de app | **No.** Hay que pedirlo y validarlo contra el padrón municipal |
| Relación mensual de canjes: predio, soles, fecha | Es el documento que Rentas carga a la cuenta | Parcial: `redemptions` no guarda el predio |
| Costo evitado: kg × costo por tonelada de recolección y disposición | Contrapeso del costo fiscal | **No.** Falta el costo por tonelada, que es dato municipal |
| A quién se vendió el material | Trazabilidad que pide el MINAM | Sí: `deliveries` y acopio |

Villa El Salvador sustentó su bono con esta misma cuenta: ahorro en limpieza pública por residuos
recuperados, ingreso de los recicladores y horas de servicio comunitario. Sirve de plantilla de informe.

### 3.7 Cómo cambia el modelo

- Cada S/ 1 canjeado por arbitrio es S/ 1 que Ruma no envía por Yape (con la variante 1 de 3.3).
- Tamaño [S]: el piloto emite unos S/ 4.300 de puntos al año. Con el diseño A irían al arbitrio unos S/ 760
  (18 %); con el B, unos S/ 1.320 (31 %); el techo teórico, si los 180 hogares canjearan todo por arbitrio,
  es el total.
- **En soles es poco: S/ 60-110 al mes en el piloto.** El modelo sigue perdiendo S/ 2,42 por recojo por el
  costo del rider; el arbitrio no cambia ese resultado.
- Lo que sí cambia:
  - Un premio alcanzable que no le cuesta efectivo a Ruma ni comisión de transferencia.
  - Legitimidad: Ruma pasa de «una app» a operador del programa municipal, y los riders tienen por dónde
    quedar autorizados (el problema del art. 34).
  - El libro de costos puede reemplazar el convenio de S/ 1.500 al mes, que la entrevista dejó sin sustento,
    por un convenio sin pago con ahorro de canjes. Es una línea mucho más pequeña, pero real.
- `dashboard/lib/economia.ts` usa S/ 600 de arbitrio anual y 10 % de descuento; con S/ 55 y tope de 50 %, el
  descuento máximo es S/ 27,50, no S/ 60, y el número de puntos necesario es 27.500.

### 3.8 Riesgo político

- **Depende de un voto.** El dato es verbal y de un funcionario; la respuesta escrita anterior decía que no
  existe descuento y que el área no tiene personal.
- **Cambio de gestión.** Si hay elecciones municipales cerca, una ordenanza aprobada ahora puede no
  reglamentarse o no renovarse [S: calendario electoral no verificado]. Huacho la limitó a dos años fiscales;
  Lurigancho-Chosica, a tres.
- **Capacidad de Rentas.** Aplicar descuentos predio por predio es trabajo manual para una municipalidad que
  suspendió su propio programa por falta de personal. Si Rentas no los carga, Ruma queda como quien prometió.
- **Asociación «San Pedro».** El art. 47 habla de recicladores autorizados. La asociación formalizada puede
  objetar que el beneficio se ate a una empresa y a riders que no lo son.
- **Promesa adelantada.** El canje ya aparece activo en la app sin ordenanza. Cada día así es un riesgo de
  reclamo.
- **Mitigación [REC]:** el canje de arbitrios nunca debe ser la única salida de los puntos. Yape queda
  siempre disponible; si el descuento se cae, los puntos reservados vuelven al saldo.

---

## 4. Recomendación

### 4.1 Mínimo y forma del canje

1. **Yape/Plin: mínimo de 1.000 pts (S/ 1) durante las 4 semanas de validación.** Es un solo dato en
   `redemption_options`, sin cambio de código.
2. **Pago en una tanda semanal, con día fijo y visible** («Pagamos los viernes»). Da 7 días para revisar
   pesajes raros y concentra el trabajo.
3. **Canjear todo el saldo disponible, no lotes de 1.000**, y como máximo un canje por semana por hogar. Esto
   sí es cambio de código y es lo que evita 350 pagos de S/ 1 al mes.
4. **Mínimo regular según el dato medido:** al terminar las 4 semanas, fijarlo en el doble de la mediana de
   puntos por recojo pesado, redondeado a 500. Si la mediana es 640, el mínimo queda en 1.000; si es 960,
   puede subir a 2.000 manteniendo el **primer canje en S/ 1, una sola vez**.
5. **No pasar de S/ 2 mientras el pago sea manual**, y recotizar cuando haya tarifa de dispersión.
6. Antes de pagar un solo canje real: cerrar la validación del canje en la base (sección 5).

Qué descarté: dejar S/ 5 (contradice toda la evidencia de reseñas); el canje solo mensual (esconde la
fecha de cobro); un bono de bienvenida en puntos (ese sí invita a cuentas duplicadas).

### 4.2 Opciones y orden en la wallet

| Orden | Opción | Estado en Cartavio | Texto |
|---:|---|---|---|
| 1 | Yape / Plin | Activa, mínimo S/ 1 | «1.000 pts = S/ 1 · Pagamos los viernes» |
| 2 | Descuento en arbitrios | **Hoy: visible pero no canjeable** («Próximamente»). Con ordenanza: activa, desde 1.000 pts | Hoy: «En trámite con la Municipalidad de Santiago de Cao». Después: «1.000 pts = S/ 1 menos en tu arbitrio · hasta S/ 27,50 al año» |
| — | Tiendas Eco y Reforestación | Desactivar en el piloto salvo que exista un aliado real que las respalde | — |

- Yape va primero: cuatro de cada cinco hogares no pagan arbitrios.
- Arbitrios segundo y con la misma tasa, para que no haya que comparar monedas.
- Tiendas y árboles tienen el mismo problema que tenía arbitrios: prometen algo sin un socio conocido. No
  se encontró en el repo ningún aliado que los respalde [R]; si existe, se quedan.
- Opción a explorar: compost municipal como canje, que la MDSC ya regala en el ECOTRUEQUE. No cuesta
  efectivo a nadie.

### 4.3 Pasos con la municipalidad

**Qué pedir**

1. **La ordenanza**: que la Gerencia a cargo de limpieza pública y la de Rentas emitan sus informes y que el
   proyecto entre a la agenda del Concejo con fecha. Preguntar ahí mismo si necesita ratificación de la
   Municipalidad Provincial de Ascope.
2. **Un convenio de cooperación sin costo**: que reconozca a Ruma como aliado operativo del Programa RECICLA
   en Cartavio, que autorice o registre a los Eco-Riders (art. 34 del D.L. 1278 y art. 47 del reglamento de la
   Ley 29419) y que fije el intercambio de datos.
3. **Los datos y el procedimiento de Rentas**: cuadro tarifario vigente, predios y pagadores de Cartavio,
   costo por tonelada de recolección y disposición final, cómo se valida el código de contribuyente y quién
   carga el descuento. Con nombre y cargo del responsable.

**Qué documento llevar al Concejo**: un expediente corto con cinco piezas.

1. Carta de presentación y solicitud.
2. Proyecto de ordenanza (borrador abajo).
3. Informe técnico sustentatorio al estilo del de Villa El Salvador: costo fiscal máximo (tabla 3.5), kilos
   desviados, viviendas participantes, efecto esperado en la morosidad. Con los datos de las 4 semanas de
   piloto, no con proyecciones.
4. Proyecto de convenio.
5. Precedentes: Ordenanza 353-MDL de Lurigancho-Chosica y O.M. 019-2016/MPH de Huacho, impresas.

**Borrador de puntos de la ordenanza** (para que lo redacte la asesoría jurídica; no es un texto legal):

1. **Objeto.** Aprobar un incentivo tributario para los predios de uso casa habitación que segregan PET y
   cartón y los entregan al Programa RECICLA del distrito.
2. **Base legal.** Art. 70 y art. 53 del D.L. 1278 y sus modificatorias (D.L. 1501, Ley 32212); arts. 46 y 47
   del D.S. 005-2010-MINAM; art. 40 de la Ley 27972; Norma IV del Título Preliminar del Código Tributario;
   Ordenanza 018-2017-MDSC.
3. **Beneficio.** Descuento en el arbitrio de limpieza pública equivalente al valor certificado de lo
   entregado, a razón de S/ 1 por cada 1.000 puntos del registro, con **tope de 50 % del arbitrio anual del
   predio**. Variante alternativa: 20 % fijo para el predio con participación acreditada.
4. **Beneficiarios.** El titular del predio registrado, o quien él autorice. Un beneficio por predio.
5. **Condición de participación.** Entregas acreditadas con peso en al menos 2 de cada 4 semanas, o la
   frecuencia que fije el reglamento. El beneficio se suspende los meses sin entrega.
6. **Contribuyentes con deuda.** Pueden acogerse si pagan el saldo del ejercicio en curso; el descuento se
   aplica primero a ese ejercicio. Opcional: descuento adicional sobre la deuda más antigua, como en Huacho.
7. **Acreditación.** Constancia mensual emitida por el operador del registro (Ruma) con predio, kilos y
   monto, remitida a Rentas dentro de los primeros días de cada mes.
8. **Aplicación.** Rentas carga el descuento a la cuota siguiente. No genera devolución ni saldo a favor en
   efectivo.
9. **Recolección.** A cargo de recicladores u operadores autorizados o registrados por la municipalidad para
   el programa.
10. **Control.** La municipalidad puede verificar pesajes y visitar predios; el fraude anula el beneficio.
11. **Protección de datos.** Uso de los datos del vecino solo para aplicar el beneficio, con su
    consentimiento.
12. **Vigencia.** Ejercicio 2027, prorrogable por decreto de alcaldía. Informe de resultados al Concejo cada
    seis meses.
13. **Reglamentación.** Por decreto de alcaldía en 30 días.

**Puntos del convenio:** sin pago entre las partes; quién financia el descuento (la municipalidad) y qué
entrega Ruma a cambio; formato y fecha del reporte mensual; tratamiento de datos; qué pasa con los puntos
reservados si la ordenanza se deroga; plazo de un año y causal de término.

### 4.4 Métricas para validar en 4 semanas

| Métrica | Cómo se mide | Qué decide |
|---|---|---|
| Mediana de puntos por recojo pesado | `detections` y liquidación del acopio | El mínimo regular (regla de 4.1) |
| Recojos y días hasta el primer canje | Alta → primer `redemptions` | Si se cumple «2-3 recojos». Meta: mediana ≤ 3 |
| % de hogares que canjean dentro de 7 días de alcanzar el mínimo | `redemptions` contra saldo | Si el dinero se percibe real. Meta de partida: más de la mitad [S] |
| Retención en semana 4 de quien cobró frente a quien no | Recojo en la semana 4 | Si el primer cobro retiene. Es la apuesta de todo este documento |
| Canjes por semana, minutos por pago, pagos fallidos | Registro del operador | Si S/ 1 es operable a mano |
| Monto medio del canje y % del saldo sin canjear | `redemptions`, `points_balance` | Caja y lote |
| Kg por recojo, semana a semana | Pesaje | Detectar recojos partidos para cobrar antes |
| Diferencia entre declarado, puerta y pesado | `ruma_analytics` | Fraude y calidad del conteo |
| Hogares que dan su código de contribuyente y dicen si pagan arbitrios | Pregunta en la wallet o por el rider | Demanda real del descuento; va al informe técnico |
| Kg desviados y viviendas participantes | Pesaje y `pickups` | Las dos cifras que necesita el Concejo |

No hay un referente peruano publicado para ninguna de estas tasas (`retencion-incentivos-research.md` §2.2):
las 4 semanas son la línea base, y las metas de la tabla son puntos de partida.

---

## 5. Qué cambiaría en la app (solo lista, nada implementado)

**`redemption_options`**

- `YAPE_PLIN`: `min_points` de 5.000 a 1.000; `description` sin «Mínimo S/ 5,00» y con el día de pago.
- `MUNI_ARBITRIOS`: hoy no canjeable en Cartavio. `is_active` solo tiene dos estados y al apagarlo desaparece
  de la wallet; para mostrar «Próximamente» hace falta un estado propio (por ejemplo en `metadata`). Con
  ordenanza: tope anual por predio y ámbito por distrito, también en `metadata` o en columnas.
- `STORE_DISC` y `DONATE_TREE`: desactivar en el piloto si no hay aliado.
- `MUNI_ARBITRIOS` y `STORE_DISC` comparten `sort_order` 2: desempatar.
- Primer canje rebajado: un mínimo aparte para el primer canje y la regla en la base, no en el cliente.
- Staging primero y producción después, con script fechado en `supabase/sql/`.

**Base y reglas (lo más urgente)**

- `validate_redemption` debe leer la opción y decidir: mínimo, monto en soles calculado en la base a partir de
  los puntos, `kind` coherente con la opción, estado inicial forzado a pendiente. Hoy todo eso lo manda el
  cliente.
- Límite de un canje por semana por hogar y tope anual de arbitrios, también en la base.
- Canje de todo el saldo: hoy es un lote fijo de `min_points` en ambas apps.
- Número de Yape o Plin de destino y nombre del titular guardados en el canje (`payout_reference` existe y
  no se llena).
- Arbitrios: código de contribuyente o de predio del hogar; el canje reserva los puntos y los devuelve si la
  municipalidad lo rechaza.
- Vista o exportación mensual para Rentas: predio, soles, fecha.

**Estado del canje**

- `redemption_status` ya tiene `pending`, `processing`, `completed`, `failed`, `cancelled` [R]. Falta
  mostrarlos al usuario.
- Yape: «Solicitado» → «Se paga el viernes» → «Pagado al 9XX XXX XXX» o «No se pudo pagar: revisa tu número».
- Arbitrios: «Reservado» → «Enviado a la municipalidad» → «Aplicado a tu arbitrio» o «Rechazado: puntos
  devueltos».
- Panel del operador: lista de canjes pendientes de la semana para pagar en tanda y marcar como pagados.

**Textos**

- Wallet de Android y PWA: «Faltan N pts para tu primer cobro de S/ 1»; el valor de respaldo de 5.000 pts y
  S/ 5 en `buildWallet` (`GeneratorViewModel.kt`) y los datos de ejemplo de `GeneratorWalletScreen.kt` dicen
  «Mínimo S/ 5.00».
- Tarjeta de arbitrios: «Próximamente» mientras no haya ordenanza; hoy el botón dice «Canjear descuento».
- `Family.tsx` línea 351: «Puntos, canjes y descuento de arbitrios» promete el descuento desde el perfil.
- Comentario de `supabase/sql/2026-10-09_precios_reales_y_arbitrios.sql`: «MAKI trabaja con la
  municipalidad», que hoy no es cierto.
- Asistente `ruma-assistant`: revisar que no ofrezca el descuento de arbitrios como disponible.
- Notificación «Ya tienes S/ 1 para tu primer Yape» (propuesta 5 de `retencion-incentivos-research.md` §3.6).
- Regla de caducidad de puntos escrita en la wallet, sea cual sea (Q5 de las reseñas).
- `dashboard/lib/economia.ts`: arbitrio de S/ 55 y tope de 50 % en lugar de S/ 600 y 10 %.

---

## 6. Lo que no se pudo verificar

- Tarifa por pago de la dispersión Yape, de Pagos Masivos Yape y de cualquier producto empresarial de Plin o
  Interbank. Límites vigentes de Yape y si sus términos permiten pagos de negocio desde una cuenta personal.
- Si el descuento necesita ratificación de la Municipalidad Provincial de Ascope.
- El texto de la Ley 32212 se leyó a través de un resumen de la página de El Peruano, no del PDF.
- El anexo operativo de la ordenanza de Lurigancho-Chosica y la vigencia actual de los programas de Villa El
  Salvador, Villa María del Triunfo, San Juan de Lurigancho y Huacho (este último venció en 2017).
- Resultados reales de cualquiera de esos programas: participación, monto descontado o efecto en morosidad.
- Programas de San Isidro, Miraflores, Surco y Trujillo: no se halló ninguno con descuento de arbitrios.
- Peso real por recojo en Cartavio, recojos por hogar al mes y cuántos hogares del piloto pagan arbitrios.
- Si el acopio paga al contado o a plazo.
- La fecha de las próximas elecciones municipales.
- Que la falla de validación del canje sea explotable en la práctica: se dedujo de la definición del
  disparador y de la política, sin escribir en la base.
