# Debate: ¿quién mide y cómo para acreditar los puntos? (2026-11-18)

Documento para el dueño de Ruma. Decide cómo se mide lo que cada recolector entrega: unidades declaradas, peso en casa,
peso en la puerta, conteo a mano o un híbrido. Convención: **[V]** = dato verificado en una fuente citada;
**[D]** = dato que el dueño dio como contexto del proyecto (no verificado aquí); **[O]** = opinión o inferencia mía.

## Resumen ejecutivo

1. **No hay evidencia directa** que compare «pagar por unidad» contra «pagar por peso» en reciclaje doméstico de botellas.
   La búsqueda solo halló evidencia indirecta (sistemas de depósito, tarifas por peso de basura, Plastic Bank). Lo que sigue
   es un debate razonado, no un veredicto empírico. El piloto es el experimento que falta.
2. **Lo que sí se sabe [V]:** los sistemas de depósito pagan por unidad porque «un depósito por un envase» es simple;
   el pago por peso exige conversiones y actualizaciones, y en California el consumidor suele cobrar menos con él.
   Las tarifas por peso de basura reducen basura pero en Noruega dividieron la motivación y subió el botado ilegal.
   Plastic Bank (el comparable más cercano) **pesa en una sucursal** con balanza y verifica contra recibos del comprador.
3. **El dinero en juego por saco es minúsculo [D]:** 100 botellas de 500 ml ≈ 2 kg ≈ S/ 2 de material y S/ 1 para el
   usuario. Un fraude del 25 % (tolerancia) cuesta ~S/ 0,25 por saco. **Verificar cuesta más que el fraude posible**;
   por eso la opción A, con balanza en el acopio, es la barata.
4. **Recomendación:** piloto con **A** para familias (con una auditoría de conteo a mano de una muestra pequeña, que es
   además el mejor dato para entrenar la IA) y **E** para negocios, colegios y edificios: pago **por kg pesado en el
   acopio o por el rider** (balanza colgante) porque generan más y no aceptan «0,3 kg» como migaja. B queda descartada.

## Hechos del sistema actual (del repo y del contexto)

- Pago por botella limpia 10 pts, 1.000 pts = S/ 1, factor por estado, bono +10 % desde 30 y +20 % desde 80 unidades,
  tope por peso medido (`docs/puntos-por-botella.md`). El puntaje se acredita al pesar en el acopio (`settle_delivery`).
- Pesos típicos [D]: botella 500 ml ~20 g, 3 L ~60 g, lata ~15 g. PET ~S/ 1/kg. Una botella de 500 ml = 50 por kg.
- **Ya existe una balanza en el diseño**: el tope «400 pts/kg × 1,25» ya hace que el peso mande si el conteo se infla.

## Debate por opción

### A. Usuario declara/escanea unidades; rider solo confirma recibido (+foto); acopio pesa cada saco y cuenta a mano solo los sacos fuera de ±25 %

- **Mejor argumento a favor:** es la única que da recompensa **inmediata y legible** («+10 pts» al escanear) sin pedir
  nada al usuario fuera de la cámara, y deja la verificación en un punto fijo donde la balanza ya existe y el costo por
  saco es de segundos. El rider no se frena en la puerta (tiempo = costo, S/ 3 a 7 por recojo [D]).
- **Mejor argumento en contra:** la tolerancia ±25 % no es neutra. Una mezcla de botellas de 500 ml y 3 L, tapas,
  etiquetas, líquido residual o tierra mueve el peso por saco más de 25 % sin que nadie mienta (20 g vs 60 g por
  unidad [D]). Resultado: muchos «falsos positivos» que obligan a contar a mano, o bien un usuario honesto al que se le
  recorta un pago y percibe injusticia. Con una sola foto del saco, el rider no certifica nada.
- **Mitigación [O]:** calibrar la tolerancia con datos del piloto (peso real por unidad declarada, por tipo de saco), no
  fijarla de antemano; ofrecer la rectificación visible («tu saco pesó 2,3 kg, equivale a ~110 botellas, te acreditamos 110»).

### B. El usuario pesa en casa y declara kg; pago por kg (o kg → unidades)

- **A favor:** pago alineado a lo que de verdad vale en el acopio; sin conteo; sencillo de auditar luego en el acopio.
- **En contra:** casi nadie tiene balanza en casa [O], el dato es auto-declarado sin control, y «0,3 kg = S/ 0,30» o 
  «0,3 kg = 3 pts» se lee como migaja: se pierde la recompensa inmediata y el efecto de «cuánto me falta» por botella.
  En el diseño del sistema también rompe la gamificación de «juntar 30 botellas» (la barra de progreso). Evidencia
  indirecta [V]: la tarifa por peso de basura en Ulstein (Noruega) **dividió la motivación y aumentó el botado ilegal**
  (Heller y Vatn 2017); un incentivo monetario de peso puede desplazar la motivación normativa. Es basura, no reciclaje, así
  que es analogía, no prueba.

### C. El rider pesa en la puerta con balanza colgante

- **A favor:** es la medición con **menos tiempo entre acción y recompensa** que sea confiable; la persona ve el peso
  al momento y se resuelve la disputa delante del dueño, que es lo que mejor construye confianza. Evita el
  «el acopio me recortó» porque lo vio.
- **En contra:** añade 1–3 min por parada, depende de que la balanza del rider esté calibrada (y del rider honesto: él cobra
  por kg pesado en acopio [D]), y puede llevar a peleas en la puerta. Para un saco de 2 kg, la precisión de una balanza
  colgante de 50 kg es mala [O: las colgantes baratas tienen resolución de ~10–50 g; confirmar con el modelo real].

### D. El acopio cuenta a mano todo

- **A favor:** es la «verdad terreno» más exacta para unidades (útil para entrenar IA) y cero ambigüedad de pago por unidad.
- **En contra:** mano de obra proporcional al volumen: 100 botellas por saco son decenas de segundos a minutos. A 10.000
  botellas/día, serían horas de conteo. No escala y encarece lo que vale S/ 1/kg [D]. Solo sirve como muestra.

### E. Híbrido por tipo de usuario

- **A favor:** cada segmento paga según lo que de verdad genera y posee (balanza, volumen); evita forzar a la bodega a «escanear
  500 botellas» o a la familia a pesar.
- **En contra:** dos reglas = más texto de UX, más casos de soporte y más superficie para explotar la diferencia
  (la familia que se hace pasar por «negocio» para pagar por kg si fuera más barato o viceversa). Exige una definición
  clara y verificable del tipo de cuenta.

## Tabla comparativa

| Criterio | A: declara + acopio pesa | B: pesa en casa | C: rider pesa | D: acopio cuenta todo | E: híbrido |
|---|---|---|---|---|---|
| Recompensa inmediata (+10 pts) | Alta | Baja | Media | Baja (tarda) | Mixta |
| Fricción para el usuario | Baja | Alta (balanza) | Baja | Baja | Media |
| Percepción de justicia | Media (depende de la tolerancia) | Alta si hay balanza, pero sin control | Alta (lo ve) | Media | Media |
| Tiempo del rider | Mínimo | Mínimo | +1–3 min/parada [O] | Mínimo | Variable |
| Mano de obra del acopio | Baja (balanza + muestra) | Baja | Baja | **Muy alta** | Baja a media |
| Escalabilidad | Alta | Alta | Media | **Baja** | Media |
| Fraude/disputa | Techo bajo (≤25 % de ~S/ 1); disputas por tolerancia | Alto, sin control | Medio (colusión rider-usuario) | Bajo | Hay arbitraje de tipo |
| Dato para IA (unidades verificadas) | Parcial: solo los fuera de tolerancia + muestra | Nulo | Nulo | **Máximo** | Parcial |
| Encaje con el modelo actual (Ruma) | Ya casi igual | Cambio grande | Cambio medio (ya hay conteo del rider) | Cambio grande | Cambio medio |

## Tabla por segmento (lo que cada uno elegiría, inferencia mía [O] salvo lo marcado)

| Segmento | ¿Tiene balanza? | ¿Cuánto genera? | Elegiría | Por qué |
|---|---|---|---|---|
| Familia | Rara vez (balanza de cocina de 5 kg, no de saco) | Un saco de 50–150 botellas al mes; ~1–3 kg | **A** | Quiere ver «+10 pts» al escanear y no pesar. El monto es pequeño, no vale la fricción. |
| Bodega | Muchas tienen balanza de comercio (para venta) | Cajas de botellas, latas y cartón; decenas de kg/semana | **E por kg (C o acopio)** | Piensa en kg y en soles; no quiere escanear 200 botellas. Aprecia la balanza del rider delante de ella. |
| Restaurante | A veces (cocina) | Alto: botellas de gaseosa, vidrio, latas, cartón; difícil contar | **E por kg** | Volumen y mezcla: el conteo a mano es inviable; el kg mide mejor. |
| Colegio | Pocas, pero hay un responsable | Picos (campañas); mucha botella de 500 ml | **E por kg + reporte** | Quiere un acta con el peso y un reporte para su programa ambiental; el pago a puntos es secundario. |
| Edificio/condominio | Pocas; sí tiene portería | Mucho, con acopio común de varias familias | **E por kg del saco comunal + reparto** | Un solo saco compartido: el reparto por familia se anota por unidad si escanean, o por prorrata. |
| Oficina | Rara | Bajo y recurrente (botellas, papel, latas) | **A** | Pocos kilos; recompensa simbólica; lo que valoran es el certificado. |

Nota: los volúmenes por segmento son estimaciones mías, no datos medidos. **El piloto debe medirlos** (ver abajo).

## Evidencia: qué se encontró y qué no

**Sistemas de depósito y retorno (DRS):**
- La métrica de desempeño de los DRS se mide en **unidades**, no en peso (Reloop, defensor de los DRS, parte interesada)
  y el valor del depósito se correlaciona con mayor tasa de recolección. Es sobre cuánto se paga, no sobre cómo se mide.
- TOMRA (fabricante de máquinas de retorno, parte interesada) afirma que el pago por peso en California obliga a actualizar el
  peso promedio estatal y que el consumidor suele cobrar menos; el pago por unidad es «un depósito por un envase».
  → Fuente de industria; consistente con la lógica, no un estudio independiente.
- Las máquinas de retorno (RVM) cuentan por unidad con lector de código. Ruma, sin máquina, replica la unidad con
  cámara y contrapeso en el acopio.

**Economía conductual:**
- La revisión 2024 que hallé indica que los esquemas «pagas por lo que tiras» reducen casi siempre la basura total, y que los
  de **peso** rinden mejor que los de volumen (estudio preliminar de conferencia, no verificado a fondo).
  Pero son **tarifas** (castigo), no **recompensas** (premio). La asimetría pérdida/ganancia hace que no se trasladen
  directamente [O].
- Heller y Vatn (2017): la tarifa por peso dividió la motivación y aumentó el botado ilegal en un municipio noruego.
- Un experimento de campo reciente (aceptado en *Ecological Economics*, 2026, citado por un buscador) halló que incentivar
  el reciclaje activa a los de menores ingresos «por la oportunidad de ingreso». Fuente secundaria, sin lectura del texto.
- **Inmediatez de la recompensa:** la búsqueda no encontró un experimento de campo que pruebe la recompensa inmediata en
  reciclaje. La preferencia por lo inmediato es un hallazgo general de la economía conductual (descuento hiperbólico),
  pero **aquí es una inferencia mía [O], no un dato verificado para reciclaje**.

**Apps y programas (cómo pagan y registran):**
- **Plastic Bank [V, fuente de la propia empresa]:** el colector lleva el plástico a una sucursal; allí lo **separan y pesan por
  tipo y color** y se paga a precio de mercado; los recibos del comprador se cruzan semanalmente con el registro de la app y
  se anula lo que no cuadra. No hay auditoría independiente publicada de cuán bien funciona. Es el análogo más cercano a la
  opción A/C: peso en un punto controlado, no en casa.
- **Ecotrash (Perú):** trabaja con empresas y edificios multifamiliares; no atiende casas unifamiliares «porque recoger
  volúmenes pequeños no es viable logísticamente»; el usuario ve en la app el volumen reciclado y gana ecopuntos.
  [V, nota de prensa.] Valida que **el volumen pequeño por casa es el problema logístico**, y que los negocios y edificios
  son el segmento natural.
- **Ecolana (México):** mapa de puntos de reciclaje y app con puntos por reciclar, retos y canje; no hallé cómo registra el
  peso. No cuento un hecho que no vi.
- **Programa Recicla / segregación en la fuente (Minam, Perú):** formaliza recicladores y equipa centros de acopio;
  la comercialización va por asociaciones de recicladores formalizados. **No hallé** precios por kg ni cómo se
  registra la entrega. Pendiente: consultar la guía del Minam o una municipalidad.
- No hallé información de Cyclos ni de Recycle Coach (RC es de agenda de recolección, no de pago): no los cito.

## Recomendación

**Piloto de Cartavio:**
1. **Familias → opción A**, con tres ajustes:
   - Mostrar «+X pts **pendientes**» al escanear (recompensa inmediata) y «acreditados» al pesar. Ya es así.
   - El acopio pesa **cada saco** y registra el peso. Solo cuenta a mano los **fuera de tolerancia** y **una muestra
     aleatoria (propongo 1 de cada 10 sacos [O])** aunque cuadren: esa muestra es el conjunto de verdad terreno para entrenar la IA y para
     calibrar la tolerancia.
   - **No fijar ±25 % de antemano:** empezar con ±35 % y estrecharla con los datos del piloto, para no recortar a gente honesta
     por mezclas de tamaños. Y a quien quede fuera, acreditar el conteo a mano y avisarle por qué.
2. **Negocios, colegios, edificios, restaurantes → E por kg:** el rider pesa en la puerta con una balanza colgante
   (opción C para sacos ≥ 5 kg, donde la resolución de la balanza no es problema) o se pesa en el acopio con el código
   del saco. Se paga por kg con tarifa por material; mostrar al usuario «Tu entrega: 14 kg, equivale a ~700 botellas».
3. **Descartada B** (nadie tiene balanza y mata el «+10 pts») y **D como régimen** (no escala; solo como muestra).

**Qué medir en el piloto para confirmarlo** (todo en la base, con el código del saco):
- Peso real por unidad declarada, por saco y por segmento (distribución; decide la tolerancia).
- Porcentaje de sacos fuera de tolerancia y, de ellos, cuántos fueron error honesto y cuántos fraude (conteo a mano).
- Error de conteo de la IA vs conteo a mano (muestra 1 de 10), por tamaño de botella y por marca.
- Disputas por cada 100 sacos y tiempo para resolverlas; tiempo del acopio por saco; tiempo del rider por parada.
- Retención: familias que piden un segundo recojo a 30 días, y si caen cuando el pago se acredita tarde frente a temprano.
- Volumen real por segmento (kg/mes) para decidir si E por kg compensa el rider.
- Ver si los negocios que reciben pago por kg entregan más que los que pagan por unidad (si se prueba un brazo A en un par).

## Fuentes

- Heller y Vatn (2017), *The divisive and disruptive effect of a weight-based waste fee*, Ecological Economics 131: <https://ideas.repec.org/a/eee/ecolec/v131y2017icp275-285.html>
- Ek y Söderberg (2024), ensayos aleatorios de retroalimentación social sobre residuos: <https://letsrecycle.com/wp-content/uploads/2024/08/Ek-Soderberg-2024.pdf>
- Tarifas por peso vs recojo vs volumen (conferencia FAERE 2018, preliminar): <https://faere2018.sciencesconf.org/210162/Gnonlonfin_et_Kocoglu_2018_1.pdf>
- TOMRA, elementos de DRS de alto desempeño (fabricante de RVM, parte interesada): <https://www.tomra.com/en/discover/reverse-vending/feature-articles/key-elements-of-high-performing-deposit-return-systems-five>
- Reloop, desempeño de DRS (advocacy): <https://reloopplatform.org/wp-content/uploads/2023/05/RELOOP_Factsheet_Performance_May2024_Web.pdf>
- The Can Challenge (UEA), incentivos y difusión: <https://research-portal.uea.ac.uk/en/publications/the-can-challenge-understanding-the-best-ways-to-incentivise-recy/>
- Plastic Bank, cómo funciona (fuente de la empresa): <https://docs.plasticbank.com/en/articles/9807179-how-plastic-bank-works>
- Plastic Bank, tecnología: <https://docs.plasticbank.com/en/articles/9807657-our-technology>
- Ecotrash (Perú), Andina: <https://andina.pe/ingles/noticia-ahora-ya-puedes-reciclar-tus-plasticos-solo-hacer-un-click-tu-celular-768741.aspx>
- Ecotrash, Infomercado: <https://infomercado.pe/ecotrash-la-plataforma-medioambientalista-que-fue-reconocida-por-national-geographic-y-pepsico/>
- Ecolana (México), Expansión: <https://expansion.mx/tecnologia/2021/05/17/reciclar-puede-ser-mas-sencillo-a-traves-de-la-tecnologia>
- Minam, Programa de incentivos y recicladores (El Peruano): <https://elperuano.pe/noticia/294507-minan-mejora-gestion-de-residuos-en-11-ciudades-e-impulsa-formalizacion-de-recicladores>
- Minam, guía de segregación en la fuente (2015): <https://redrrss.minam.gob.pe/material/20150302175316.pdf>
- Repo: `docs/puntos-por-botella.md`, `docs/investigacion-pago-motorizados.md`, `CLAUDE.md`.

Limitaciones: la búsqueda web fue limitada (una pasada por tema); varios resultados son fuentes de industria. Lo no
verificado está marcado [O] y debe confirmarse con datos del piloto.
