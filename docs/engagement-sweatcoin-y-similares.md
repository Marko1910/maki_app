# Sweatcoin y apps parecidas: qué haría a Ruma menos plana

Fecha: 2026-11-11. Investigación de escritorio y propuesta. No se modificó código ni base de datos, y no se
hizo ningún commit.

Este documento continúa estos tres y no repite lo que ya establecen:

- `docs/retencion-incentivos-research.md`: Duolingo, Strava, Pokémon GO, Recyclebank, Bower, DRS, ranking
  relativo, sorteos, «tu ruma» en WebP.
- `docs/resenas-usuarios-diseno.md`: lo que critican los usuarios de Fetch, Ibotta y otras.
- `docs/canje-minimo-y-arbitrios.md`: mínimo de canje de S/ 1, pago semanal y arbitrios.

Cuando una mecánica ya se trató allí, aquí solo se añade lo nuevo.

## Cómo leer este documento

- **[V]**: verificado en una fuente independiente (estudio, ley, prensa con datos). Tiene enlace.
- **[V-emp]**: cifra que publica la propia empresa o un proveedor suyo. Es cierta como declaración, pero
  nadie la auditó.
- **[V-res]**: visto solo en el resumen del buscador, porque la página devolvió 403 (el centro de ayuda de
  Sweatcoin, Business of Apps, BBVA). Hay que abrir el enlace antes de citarlo en un pitch.
- **[R]**: recomendación o cálculo mío. Son hipótesis para el piloto, no hechos.
- **[Código]**: lo que hay en el repositorio.
- Las citas textuales tienen menos de 15 palabras y van en su idioma original.

**Base de costos [R, con datos de `canje-minimo-y-arbitrios.md`]**

- Piloto de 180 hogares activos con 2 recojos al mes por hogar.
- Los puntos emitidos son unos S/ 1,92 por hogar al mes: el 40 % del valor del material.
- Cada recojo ya pierde unos S/ 2,42 por el costo del rider.
- Por eso, **todo sol gastado en «enganche» empeora una cuenta que hoy es negativa.** Las ideas que no
  cuestan efectivo o que paga un tercero (una bodega, una marca o la municipalidad) van primero.

---

## 0. Resumen

1. **El truco de Sweatcoin no es pagar por caminar, es que el saldo crece solo todos los días.** Convierte
   algo que la persona ya hace (caminar) en una moneda que ve subir a diario, sin metas que fallar. Además
   tiene una vitrina de ofertas baratas que **rota a diario** y que se alcanza con uno o dos días de pasos
   [V-emp, BJSM 2019].
2. **Ruma ya tiene lo que a Sweatcoin le falta: paga en soles.** La queja número uno de Sweatcoin es que su
   moneda no vale casi nada y que sus «ofertas» exigen comprar. En cambio, Ruma tiene lo que a Sweatcoin le
   sobra: entre la acción y el pago pasan semanas, y la acción no es diaria. Por eso se siente plana.
3. **Ruma no debe copiar el pago diario.** Pagar por abrir la app o por escanear cada día premia generar o
   escanear de más. Debe copiar tres cosas: **el progreso visible diario sin dinero, la oferta que rota y lo
   social**. El dinero sigue llegando al pesar.
4. **El loop diario recomendado** (sección 4): una notificación a la hora en que se bota la basura, «echar a la
   bolsa» (la foto que ya existe o un toque de «hoy no»), ver cómo sube la bolsa del hogar y la barra de la
   cuadra, y luego la pregunta del día de Rumi y la oferta del día de la bodega. Tarda menos de un minuto.
   Lo que se repite cada semana es el recojo, y cada mes, el pesaje, el canje y la temporada.
5. **Las 5 ideas principales**, con su costo en dinero real por hogar al mes:
   1. Bolsa del hogar con la pregunta diaria de Rumi: de S/ 0 a S/ 0,10.
   2. «Mercadito Ruma» (oferta del día y canje en bodega): S/ 0, porque la bodega pone el descuento.
   3. Cuadra contra cuadra: de S/ 0,17 a S/ 0,33.
   4. Temporada mensual con misiones de calidad y «raspadita del pesaje»: de S/ 0,20 a S/ 0,30, idealmente
      con patrocinio.
   5. Referidos que pagan al primer pesaje: unos S/ 2 por hogar nuevo, de S/ 0,20 a S/ 0,40 por hogar activo.
6. **Lo que descarto:**
   - la ruleta diaria por abrir la app;
   - el álbum de botellas por marca (incentiva comprar);
   - el escaneo de boletas: en las inspecciones de SUNAT, el 59 % de los locales no emitía boleta [V];
   - la suscripción que multiplica lo que se gana, como el Premium de Sweatcoin.

---

## 1. Sweatcoin a fondo

### 1.1 El loop central: pasos que se vuelven moneda

**Verificado**

- La conversión es de 1.000 pasos verificados = 1 sweatcoin, menos un 5 % de comisión, o sea **0,95 por
  cada 1.000 pasos** [V-res] ([centro de ayuda](https://help.sweatco.in/hc/en-us/articles/360012673992-How-many-steps-does-it-take-to-earn-1-sweatcoin)).
  Una reseña de terceros afirma que la conversión efectiva media es del 65 % de los pasos [V-res]
  ([Medium](https://medium.com/@kookyvampire/sweatcoin-deep-dive-55e3e398bb0c)).
- **Antifraude**: un segundo algoritmo con GPS que **solo paga pasos al aire libre** [V-emp]
  ([Derlyatka et al., BJSM 2019](https://doi.org/10.1136/bjsports-2018-099739), co-escrito por los fundadores).
- **Sin metas explícitas**. El artículo lo dice así: «there are no explicit goals a user has to achieve».
  La meta queda implícita en el producto que la persona quiere comprar [V-emp, BJSM].
- **El mecanismo psicológico** sale de los grupos focales del mismo artículo: los usuarios
  «look back and feel good about their achievements». Mirar lo ganado hoy motiva; una meta futura no
  cumplida desmotiva [V-emp, BJSM].
- **El dueño valora más su propia moneda.** En una encuesta, la gente valoraba las monedas ajenas en
  US$ 0,01-0,05 y las propias en US$ 0,50-1,00 [V-emp] ([RevenueCat, entrevista al CEO](https://www-docs.revenuecat.com/blog/growth/anton-derlyatka-sweatcoin-sub-club-podcast-2025)).
- **Evita la culpa.** Según el resumen de esa entrevista, Sweatcoin no usa rachas ni recordatorios que hagan
  sentir culpable, y el saldo «keeps on growing» [V-emp].

**Qué significa para Ruma [R]**

- Ruma también convierte algo cotidiano (botar envases) en valor.
- La diferencia es que **el contador de Sweatcoin sube solo y a diario, y el de Ruma sube a saltos y semanas
  después**, en el pesaje.
- El arreglo no es pagar antes. Es **mostrar a diario lo que el hogar ya juntó**, como saldo pendiente y
  translúcido (ya existe la idea de capas en `retencion-incentivos-research.md` §4), y dejar que el pesaje
  lo vuelva sólido.

### 1.2 Marketplace y ofertas

**Verificado**

- **Dos velocidades de premio.** El BJSM describe «a daily rotation of low-priced offers» que se alcanzan con 1
  o 2 días de actividad, junto a productos grandes (TV, celulares) que toman unos 12 meses [V-emp].
- **Tamaño.** En 2018 tenía 300 socios con ofertas regulares; reseñas posteriores hablan de más de 600
  [V-emp, BJSM] ([reseña](https://alejandrorioja.com/how-does-sweatcoin-work/)).
- **Precios variables.** Su centro de ayuda dice que los precios de las ofertas fluctúan con la oferta y la
  demanda, para que haya stock [V-res] ([centro de ayuda](https://help.sweatco.in/hc/en-us/articles/360012916471)).
  Es un control de inflación: cuando hay demasiadas monedas, los productos suben de precio.
- **Quién paga.** Las marcas pagan por aparecer en la vitrina y por los clientes que consiguen. El usuario no
  recibe efectivo: canjea dentro de la app [V-res] ([ProductMint](https://productmint.com/sweatcoin-business-model-how-does-sweatcoin-make-money/)).

**Qué significa para Ruma [R]**

- La rotación diaria de ofertas baratas es **la razón para abrir la app aunque hoy no haya caminado mucho**.
- En Ruma, la equivalente es la «oferta del día» de una bodega o de una marca (idea 2).
- Ruma no necesita precios variables, porque su moneda está atada al sol (1.000 pts = S/ 1). Esa estabilidad
  es una ventaja que hay que **decir**.

### 1.3 Referidos («invite friends»)

**Verificado**

- **Premio.** Varias reseñas dicen que paga 5 sweatcoins por amigo que se registra; no se encontró tope
  [V-res] ([Gathering Dreams](https://gatheringdreams.com/sweatcoin-review-legit-scam/)).
- **El invitado tiene que hacer la acción.** Los términos oficiales exigen que el invitado se registre con un
  identificador nuevo y que tenga **más de cero pasos**. Prohíben las cuentas múltiples: «A user cannot create
  multiple accounts and be counted as multiple successful invites» [V-emp]
  ([términos del programa](https://promote.sweatco.in/terms)).
- **Programa de «influencers».** Se entra a partir de 20 invitaciones válidas, y paga en efectivo por PayPal
  o Wise según el país [V-emp] (mismos términos).
- **Sorteo por invitar.** Una campaña de Google Play daba un boleto de sorteo por cada amigo registrado
  [V-res] ([Google Play](https://play.google.com/store/apps/eventdetails/4829609319467219457)).

**Qué significa para Ruma [R]**

- La regla buena es «el invitado debe haber caminado». En Ruma sería «el invitado debe haber tenido un
  **pesaje**», en una dirección distinta (idea 7).

### 1.4 La economía de la moneda: SWEAT y la «inflación» de premios

**Verificado**

- **Una segunda moneda.** En 2022 Sweatcoin lanzó un token cripto (SWEAT) aparte de la moneda de la app.
  Repartió más de 4.700 millones de tokens a más de 13,5 millones de cuentas [V-res]
  ([InvestingCube](https://www.investingcube.com/sweat-crypto-price-prediction-what-next-after-crashing-cryptocurrencies/)).
- **Una caída grande.** El token perdió alrededor del 83 % en 2022 [V-res] ([CoinCodex](https://coincodex.com/crypto/sweatcoin)).
- **Cada vez cuesta más ganarlo.** Cada SWEAT nuevo requiere un poco más de pasos que el anterior. Una
  fuente dice que en 2023 la recompensa bajó de 1 a 0,33 SWEAT por cada 1.000 pasos [V-res] (CoinCodex;
  [Coinlore](https://www.coinlore.com/coin/sweatcoin-sweat-economy)).
- **Los usuarios de la app no lo querían.** Las reseñas de 1 estrella dicen que el paso a la cripto añadió
  complejidad y volatilidad que el usuario casual no pidió [V, análisis de reseñas]
  ([Unstar](https://unstar.app/blog/sweatcoin-stepn-winwalk-rewardy-evidation-walking-apps-that-pay-ranked-2026)).

**Lección para Ruma [R]**

- **Una sola moneda, atada al sol y sin cripto.**
- Cuando cambie el precio del material, lo que se ajusta son los puntos por kg (`points_per_kg`), nunca el
  valor del punto. La regla 1.000 pts = S/ 1 debe verse fija (Q4 de las reseñas).

### 1.5 Suscripción y niveles

**Verificado**

- **El esquema antiguo.** Tenía cuatro niveles que se pagaban **en sweatcoins**, de Mover (gratis, 5 al día) a
  Breaker (30 al mes, 20 al día) [V-res] ([Maple Money](https://maplemoney.com/sweatcoin-review/),
  [Tom's Guide](https://www.tomsguide.com/us/sweatcoin-faq,review-5465.html)). Eran a la vez un tope y un
  sumidero de monedas.
- **El esquema actual.** Hoy hay un Premium en dinero, que una reseña cifra en US$ 4,99 al mes y que duplica
  lo que se gana [V-res] ([DollarSprout](https://dollarsprout.com/sweatcoin-review/)).
- **Los topes diarios.** Para el token, se gana con entre 3.000 y 10.000 pasos al día en el plan gratis y con
  hasta 20.000 en Premium [V-res] ([centro de ayuda](https://help.sweatco.in/hc/en-us/articles/4435873170833-How-much-SWEAT-can-I-earn-a-day)).
- **Por qué cobran.** El CEO explica que el modelo de publicidad «would only take us so far» [V-emp] (RevenueCat).

**Lección para Ruma [R]**

- **Nunca vender un multiplicador de lo que se gana a hogares de ingresos bajos.** Las reseñas de Sweatcoin lo
  leen como pagar para cobrar un poco menos de nada.
- Lo que sí sirve es la idea del **tope como sumidero**: Ruma ya tiene un tope natural (lo pesado).

### 1.6 Retos y sorteos

**Verificado**

- **«Races».** Son retos con meta de pasos y fecha límite; quien llega entra a un sorteo, con un boleto por
  persona [V-emp] ([bases](https://production.sweatco.in/system/pdf/race-prize-draw-tcs.pdf)).
- **Sorteos con cuota de entrada en monedas.** El «Mini Draw» costaba 2 sweatcoins; los sorteos de la
  billetera exigían bloquear 25 SWEAT y pagar 1 SWEAT [V-res]
  ([Google Play](https://play.google.com/store/apps/eventdetails/4829379255666608319),
  [Blockworks](https://blockworks.co/news/move-to-earn-prize-for-walking)).
- **El «2x Daily Boost».** Cada día el usuario elige 20 minutos en los que sus pasos cuentan doble [V-res]
  ([CoinGecko](https://www.coingecko.com/learn/what-is-sweat-sweatcoin-sweat-economy)).

**Lección para Ruma [R]**

- Ruma ya tiene un reto que se parece a las «Races»: `challenges` avanza al liquidar
  ([Código] `supabase/sql/2026-10-19_cadena_entregas.sql`). Le falta **fecha, evento y sorteo**.
- **No** copiar la cuota de entrada en puntos. Con los montos de Ruma, convierte el saldo en apuesta.
- El «Daily Boost» sí tiene un análogo sano: la franja de recojo preferente.

### 1.7 Por qué la gente la abre a diario y qué critica

**Por qué la abre [R, sobre lo verificado arriba]**

- El saldo sube solo, sin hacer nada nuevo.
- La oferta barata del día puede agotarse.
- Hay retos con fecha.
- El conteo de pasos es una herramienta de salud útil por sí misma. Una reseña favorable lo resume: «as a
  free step-counting motivator it is fine» [V] (Unstar).

**Lo que critica [V]**

Trustpilot tiene 3,3/5 con 5.920 reseñas ([Trustpilot](https://www.trustpilot.com/review/sweatco.in)).

| Queja | Cita | ¿Le pasa a Ruma? [R] |
|---|---|---|
| La moneda no compra nada | «it is next to impossible to cash your sweat coins in for anything worthwhile» | No: Ruma paga por Yape. Hay que decirlo en todas partes. |
| Ofertas que exigen comprar | Las 1 estrella hablan de «deals» que no son gratis ([Unstar](https://unstar.app/blog/sweatcoin-stepn-winwalk-rewardy-evidation-walking-apps-that-pay-ranked-2026)) | Riesgo si el Mercadito ofrece «descuentos» que obligan a gastar. Por eso el canje en bodega va primero (idea 2). |
| Demasiada publicidad | «way too many ads» | No hay anuncios en Ruma. Mantenerlo. |
| El refuerzo diario que no da nada | «Daily boosts often returns zero value» | Es un aviso para cualquier caja sorpresa: nunca «cero». |
| Sorteos que nadie gana | «I never win and I work so hard» | Publicar a los ganadores y tener un premio mínimo garantizado. |
| Pasos que no se convierten | Reseñas sobre la conversión del «boost» y el soporte | Es el equivalente a «mi material no se pesó»: estado visible y «¿Algo no cuadra?» (Q10 de las reseñas). |

### 1.8 Retención publicada

Ninguna cifra es independiente.

- **Usuarios mensuales.** En octubre de 2018: «20 million downloads (October 2018) with 5 million monthly
  users» [V-emp, BJSM]. Es decir, el 25 % de las instalaciones activas en un mes.
- **Cambio de conducta.** En 6.000 usuarios, «mean increase of 19.5% over the 6-month period after download»
  frente a los 3 meses previos [V-emp, BJSM]. El estudio de JMIR con 5.892 usuarios no tiene grupo de
  control y sus autores incluyen a los fundadores, que son accionistas
  ([JMIR mHealth 2019](https://mhealth.jmir.org/2019/7/e12445)).
- **Una cifra relativa.** Hubo un 15 % más de retención tras usar la analítica de un proveedor; es un caso de
  estudio del propio proveedor y no da la tasa absoluta [V-emp] ([MoEngage](https://www.moengage.com/casestudy/sweatcoin-advanced-analytics-case-study/)).
- **Escala e ingresos.** Hoy declara más de 200 millones de usuarios [V-emp]
  ([Healthcare Technology Report, 2025](https://thehealthcaretechnologyreport.com/making-movement-count-sweatcoins-anton-derlyatka/)).
  Un agregador da US$ 20 millones de ingresos en 2023, frente a 22 millones en 2022, y una caída del 60 % en
  descargas [V-res] ([Business of Apps](https://www.businessofapps.com/data/sweatcoin-statistics/)).
- **Lo que dice un estudio independiente.** Un ensayo de un solo brazo con otra app de premios por ejercicio,
  no Sweatcoin, vio ganancias a los 3 meses que no se sostenían 12 meses después de terminar el programa [V]
  ([BMC Public Health 2021](https://bmcpublichealth.biomedcentral.com/counter/pdf/10.1186/s12889-021-10794-w.pdf)).

**Lectura [R]**

- El efecto de Sweatcoin existe mientras el incentivo sigue. Su apuesta, y la de Ruma, es que el incentivo
  **no se apaga nunca** porque lo financia el negocio: las marcas en Sweatcoin, el material en Ruma.

---

## 2. Otras apps de «gana haciendo algo cotidiano»

En **¿Funciona en Ruma?** se juzga el presupuesto (casi cero por hogar), el usuario (hogar de ingresos medios o
bajos, Android de gama baja, Cartavio) y la acción (semanal, no diaria) [R].

### 2.1 Escanear y comprar

**Fetch (EE. UU.): puntos por escanear cualquier boleta**

- Qué engancha [V-emp]:
  - Paga «at least 25 points for every scan», de cualquier tienda
    ([Fetch](https://fetch.com/learn-how-to-use-fetch-and-get-extra-points)).
  - Suma bonos por marcas destacadas, juegos («Fetch Play») y referidos que pagan cuando el amigo escanea su
    primera boleta.
- Escala [V-emp]: 12,5 millones de usuarios mensuales, 11 millones de boletas al día y US$ 500 millones de
  ingresos anualizados en el 4.º trimestre de 2024 ([Fetch newsroom](https://business.fetch.com/newsroom/fetch-reports-strong-momentum-for-2025)).
- Quién paga [V]: las marcas pagan «the value of the points plus an agreed-upon markup» cuando se compra su
  producto. Kimberly-Clark llevó su programa de lealtad de Huggies a Fetch por los datos de canasta
  ([AdExchanger, 2022](https://www.adexchanger.com/advertiser/fetch-brings-on-facebook-cpg-vet-to-build-deeper-relationships-with-big-brands-like-kimberly-clark/)).
- Mínimo de canje [V-emp]: 3.000 pts. Los puntos vencen tras 90 días sin actividad [V-res]
  ([Fetch FAQ](https://www.fetch.com/faq), [reseña](https://thecollegeinvestor.com/33560/fetch-rewards-review/)).
- ¿Funciona en Ruma? **El modelo de cobro sí; la mecánica de boletas no.**
  - Lo que sí se copia es que la marca paga solo por resultados, más un margen. Ruma ya tiene el dato que
    vende Fetch, pero del lado del descarte: `v_brand_zone`.
  - Las boletas no sirven en Cartavio: en las inspecciones de SUNAT de 2024-2025, el 59 % de los locales no
    emitía boleta [V] ([SUNAT](https://www.sunat.gob.pe/salaprensa/2025/agosto/NotaPrensaN0552025.doc)).
  - Además, premiar boletas premia comprar.

**Ibotta (EE. UU.): reembolsos por productos concretos**

- Qué engancha [V]:
  - Los «bonuses» por completar varias ofertas.
  - **«Teamwork»**: una meta mensual de equipo en la que el que te invitó queda en tu equipo y todos cobran
    si el equipo llega ([Doctor of Credit](https://www.doctorofcredit.com/ibotta-review-earn-cash-back-store-purchases-online-shopping/),
    blog antiguo; no se confirmó si sigue).
- Cifras [V, S-1 y resultados]:
  - Las redenciones por usuario cayeron de 48,7 (2022) a 31,1 (2023) ([S-1](https://www.sec.gov/Archives/edgar/data/1538379/000162828024012708/ibottas-1.htm)).
  - El crecimiento vino de terceros que muestran sus ofertas (por ejemplo, Walmart), no de su propia app
    ([3T 2024](https://www.streetinsider.com/Press+Releases/Ibotta+Reports+Third+Quarter+2024+Financial+Results/23974238.html)).
- ¿Funciona en Ruma? **El «Teamwork» sí, y es gratis**: es la base de la idea 3.
  - Lección extra: hasta Ibotta crece más **dentro de apps ajenas** que en la suya. Ruma podría vivir como
    sección de Yape Promos o de un municipio antes que pedir abrir otra app [R].

**Shopkick (EE. UU.): puntos por entrar a la tienda y escanear códigos de barras**

- Qué engancha [V-res]:
  - Da «kicks» por entrar a una tienda aliada (con Bluetooth), por escanear códigos de productos (5-30 kicks)
    y por comprar ([Money Done Right](https://moneydoneright.com/shopkick-review)).
- ¿Funciona en Ruma? **No.** Paga por ir a mirar, y lo pagan cadenas grandes. En Cartavio la bodega es el
  equivalente, y la idea 2 lo cubre sin hardware.

**Cash App Boosts (EE. UU.): descuento al instante con su tarjeta**

- Qué engancha [V-res]:
  - Una sola oferta activa a la vez, que se activa antes de pagar y que rota
    ([CreditCards.com](https://www.creditcards.com/card-advice/cash-app-boost/)).
  - Esa escasez («elige una») obliga a mirar qué hay hoy.
- ¿Funciona en Ruma? **Sí, como regla de diseño de la oferta del día**: una sola, que cambia cada día. No
  necesita tarjeta: la valida la bodega.

### 2.2 Jugar y la suerte

**Mistplay (Canadá y EE. UU.): puntos por jugar juegos móviles**

- Qué engancha [V-emp]:
  - Niveles por jugador y por juego.
  - Paga menos cuanto más tiempo llevas en un juego, lo que empuja a probar otro
    ([divulgación de Mistplay](https://www.mistplay.com/legal/economy-disclosure)).
  - Los desarrolladores pagan más por jugador de lo que se reparte en premios
    ([ProductMint](https://productmint.com/how-does-mistplay-make-money/)).
- ¿Funciona en Ruma? **No.** Paga por adquirir usuarios de juegos y depende del tiempo en pantalla.

**Lucky Day (EE. UU.): raspaditas y loterías gratis pagadas con anuncios**

- Qué engancha [V-res]:
  - Raspaditas diarias y sorteos de US$ 250-1.000, financiados con anuncios de más de 30 segundos
    ([Money Pantry](https://moneypantry.com/lucky-day-app-review/)).
  - No se encontró ningún dato oficial de pagos.
- ¿Funciona en Ruma? **No como loop diario.**
  - El modelo de anuncios en un Android de gama baja degrada la app.
  - La suerte diaria se parece a una apuesta: el gasto en cajas sorpresa se asocia con el juego problemático
    (n = 7.422; η² = 0,054), aunque el estudio no puede decir qué causa qué [V]
    ([Zendle y Cairns, PLOS ONE 2018](https://eprints.whiterose.ac.uk/139116/1/journal.pone.0206767.pdf)).
  - Una versión acotada sí cabe: la raspadita **ligada al pesaje**, con premio mínimo garantizado (idea 5).

### 2.3 Hábito, mascota y salud

**Duolingo (lo nuevo frente al documento de retención)**

- Qué engancha [V-emp]:
  - Las rachas con amigos: quien tiene al menos una tiene un 22 % más de probabilidad de completar la lección
    del día. Es una correlación ([Duolingo](https://blog.duolingo.com/friend-streak/)).
  - Se comparten con hasta 5 personas y cada una tiene que aceptar.
- ¿Funciona en Ruma? **Sí, a escala de cuadra o familia** (idea 3).
  - En Ruma la «racha compartida» es semanal: «tú y tu vecina cumplieron 4 semanas».

**Pokémon GO**

- Qué engancha:
  - Eventos con fecha y en la calle.
  - Según el titular de PocketGamer.biz, tiene 20 millones de jugadores semanales casi nueve años después
    [V] ([PocketGamer.biz](https://www.pocketgamer.biz/pokmon-go-has-20m-weekly-active-players-nearly-nine-years-after-its-global-launch)).
  - Niantic declara 2,7 millones de asistentes a sus eventos de 2019 [V-emp] (vía Wikipedia, sin verificar).
- ¿Funciona en Ruma? **Sí, el calendario de eventos**: el «Día Ruma» del barrio una vez al mes (idea 4).

**Strava**

- Qué engancha [V-emp]:
  - Los retos patrocinados por marcas, con insignia, descuento o sorteo.
  - A la marca le cuestan desde US$ 30.000-50.000 según la página de negocios de Strava [V-res], o desde
    US$ 20.000 según una guía anterior citada por
    [Modern Retail](https://www.modernretail.co/marketing/brands-like-chipotle-duer-hoka-are-partnering-with-strava-to-make-branded-workouts/)
    (también [Sacra](https://sacra.com/chat/h/44db1331-42f9-4709-9663-076d7eed3cfa/)).
- ¿Funciona en Ruma? **Sí, el formato a escala de pueblo**: «Temporada de diciembre, auspiciada por X»
  (ideas 4 y 9). El precio, obviamente, no.

**Finch (mascota de autocuidado)**

- Qué engancha [V, analistas]:
  - El ave crece con las tareas del día.
  - Un widget la muestra viva aunque no abras la app.
  - Sale de «aventura» unas 6 horas, lo que crea una ventana para volver sin insistir: «anti-nag».
- Retención: D1 54 % y D7 37 %, según datos de Sensor Tower citados por Deconstructor of Fun
  ([Deconstructor of Fun](https://www.deconstructoroffun.com/blog/x0hd2ssr80y5n7gv0w967pg7hwd7tl)).
  Corrige el «D1 ~60 % sin fuente» del documento de retención: ahora hay un dato de analista.
- ¿Funciona en Ruma? **En parte.** Lo valioso es **el widget y la cita que se cumple sola**, no la mascota.
  - Para Ruma: un widget de Android con la bolsa del hogar y la barra de la cuadra (ola 3).
  - La ruma 3D ya cumple el papel de «criatura que crece».

**Forest (enfoque que planta árboles reales)**

- Qué engancha [V-emp]:
  - Lo virtual se vuelve real: 2.500 monedas = un árbol real plantado por Trees for the Future.
  - El fundador estimó que un árbol cuesta 2-3 semanas de uso.
  - Tope de 5 árboles reales por cuenta de pago ([Quartz](https://qz.com/1112713/urb-tech-dependence-with-an-app-that-plants-trees-as-a-reward),
    [Forest](https://forestapp.cc/llms-full.txt)).
- ¿Funciona en Ruma? **Sí, el puente de lo virtual a lo real en el barrio** («la cuadra que llega a la meta
  gana una banca o un macetero hecho con su PET»). Hoy `DONATE_TREE` existe sin aliado (canje doc §4.2): o
  se consigue uno real o se quita.

### 2.4 Latinoamérica y Perú

**Yape Promos (Perú)**

- Qué engancha [V]:
  - Una sección de descuentos que se pagan con Yape.
  - Se lanzó en septiembre de 2022 para unos 10 millones de usuarios
    ([Infomercado](https://infomercado.pe/yape-promos-la-nueva-funcionalidad-del-aplicativo-que-espera-registrar-mas-500-mil-promociones-a-fin-de-ano-280922-mc/)).
  - No se encontró cuántos la usan.
- ¿Funciona en Ruma? **Es el formato que el usuario peruano ya entiende.** El «Mercadito Ruma» debería
  parecerse a esto, no a un catálogo de premios.
  - Hay una alianza posible a futuro: que las ofertas de Ruma aparezcan en Yape Promos [R].

**Plin (BBVA, Interbank, Scotiabank)**

- Qué engancha [V-res]:
  - Sorteos semanales y diarios por transferir.
  - Y, cada vez más, **«premios sin sorteos»**: S/ 10 por afiliarse y cobrar; hasta S/ 300 si recibes 12
    plineos en el mes ([BBVA](https://www.bbva.pe/personas/servicios-digitales/plin/gana-sin-sorteos.html)).
- ¿Funciona en Ruma? **El giro de «sorteo» a «meta con premio seguro» es la lección.** El hogar peruano
  conoce los dos formatos; el seguro genera menos desconfianza (Q4 de las reseñas).

**Tpaga (Colombia)**

- Qué engancha [V]:
  - Integró **tiendas de barrio sin cuenta bancaria** mediante QR.
  - En 2018 tenía unos 150 comercios y 10.000 usuarios activos
    ([Portafolio](https://www.portafolio.co/negocios/las-tiendas-de-barrio-se-integran-a-la-red-de-la-aplicacion-tpaga-515023)).
- ¿Funciona en Ruma? **Sí, la bodega como punto de canje.** Es la base de la idea 2.

**Rappi Pay / RappiCard (Perú)**

- Qué engancha [V]:
  - Cashback del 2-5 % en créditos de Rappi.
  - Perú Retail informa que el negocio de tarjetas de Rappibank deja de operar en Perú; la fecha no está
    confirmada ([Perú Retail](https://www.peru-retail.com/?p=289226)).
- ¿Funciona en Ruma? **No.** El cashback por gasto no aplica a quien recicla.
  - Lección: un programa de recompensas que depende de un socio financiero puede desaparecer.

**Bonus (Perú, coalición de Primax, Cencosud y Delosi)**

- Qué engancha [V]:
  - Unos 6 millones de afiliados.
  - Puntos de muchas marcas en una sola cuenta, que vencen cada 2 años y se pueden convertir en soles
    ([Gestión](https://gestion.pe/economia/empresas/bonus-y-su-estrategia-para-acumular-mas-puntos-sumar-mas-socios-y-atraer-a-jovenes-cencosud-primax-delosi-noticia/)).
- ¿Funciona en Ruma? **Es la meta lejana:** que los puntos de Ruma sirvan en muchas tiendas.

**Programas de marcas con PET en Perú**

- Arca Continental Lindley (Coca-Cola) y Tambo tienen estaciones de reciclaje con premios en 20 tiendas de
  Lima ([Corresponsables](https://www.corresponsables.com/per/actualidad/arca-continental-lindley-y-tiendas-tambo-inauguran-programa-de-reciclaje-con-recompensas-para-clientes/)).
- Coca-Cola, Lindley y Wong tuvieron máquinas que daban cupones por envases [V]
  ([Perú Retail](https://www.peru-retail.com/peru-coca-cola-supermercados-wong-instalan-maquinas-descuentos/)).
- **«Bodega Sin Residuos»**, de Coca-Cola, Lindley y Recicla Latam, convierte bodegas en puntos de acopio de
  PET [V] ([Mercado Negro](https://www.mercadonegro.pe/marketing/juntos-la-plataforma-de-coca-cola-para-trabajar-en-la-mejora-del-medio-ambiente-en-america-latina)).
  No se encontró su mecánica de premio.
- **Por qué les interesa [V]:** la Ley 30884 obliga a los fabricantes de botellas PET a incluir al menos un
  15 % de PET reciclado posconsumo ([SINIA](https://sinia.minam.gob.pe/novedades/ley-ndeg-30884-regula-consumo-bienes-plastico-un-solo-uso-que-generan)).
  En 2025 se presentaron proyectos para subirlo al 30 % y exigir que se recolecte en Perú, sin confirmar si
  se aprobaron ([Congreso](https://api.congreso.gob.pe/spley-portal-service/archivo/MzI0Nzg5/pdf)).
- ¿Funciona en Ruma? **Sí: son los compradores naturales de la idea 9.** Ya gastan en reciclaje con premios
  y necesitan PET recolectado con trazabilidad. Ruma les ofrece kilos pesados, marca y zona.

**El caso más parecido fuera de Perú: Polytag con Ocado y Bower (Reino Unido)**

- Qué pasó [V-emp]:
  - Un código QR único por botella; el usuario lo escanea al reciclar y recibe 20 peniques.
  - En la prueba de Ocado, el 87 % de quienes escanearon reciclaron y cobraron.
  - Se reclamaron más de 20.000 premios en 56 días
    ([Packaging Insights](https://www.packaginginsights.com/news/incentivized-recycling-qr-codes-set-to-boost-uk-milk-bottle-circularity-and-digital-drs.html)).
- Lección [R]: **la marca paga por el dato de «si, cuándo y dónde» se recicla su envase.** Es exactamente lo
  que Ruma ya mide con la marca detectada y el pesaje. La diferencia es que Ruma no tiene un código por
  botella, así que la marca detectada sirve para reportes agregados y no para pagar por unidad.

---

## 3. Ideas para que Ruma sea menos plana

Cada idea tiene:

- **Costo en dinero**: soles reales por hogar activo al mes, con el piloto de 180 hogares.
- **Desarrollo**: tiempo para un desarrollador en este repositorio, en Android y PWA.
- **Riesgo**: fraude, incentivo a generar más basura y complejidad.

Las cifras de costo son [R].

### Idea 1. Bolsa del hogar con la pregunta diaria de Rumi: el loop diario

- **Qué es.**
  - La pantalla de inicio muestra **la bolsa del hogar**: lo declarado desde el último recojo, en capas
    translúcidas, y cuánto falta para que el recojo «valga el viaje».
  - Cada día hay una acción de menos de 30 segundos: echar a la bolsa (la foto que ya existe, o un toque de
    «hoy no eché nada»).
  - Después, una **pregunta o dato de Rumi** de una sola respuesta, por ejemplo: «¿La tapa va con la
    botella?».
  - Al responder, se ve la barra de la cuadra y la oferta del día (idea 2).
- **Evidencia.**
  - Sweatcoin funciona porque el saldo sube a diario y la persona «mira atrás y se siente bien» [V-emp, BJSM].
  - Finch abre a diario con progreso visible y sin regaños [V, analistas].
  - Duolingo: una barra baja y clara sostiene la racha (documento de retención).
- **Costo en dinero.**
  - Si la pregunta paga un **boleto** del sorteo mensual o de la temporada, el costo es S/ 0.
  - Si paga puntos (5 pts al día con tope de 100 al mes), el máximo es **S/ 0,10 por hogar al mes**: S/ 18 al
    mes en el piloto.
  - Recomiendo boletos, no puntos.
- **Desarrollo.**
  - De 4 a 6 días.
  - Una tabla `daily_tips` precargada, con 60-90 preguntas que se generan una sola vez con el modelo, las
    revisa una persona y se cargan por script en STAGING.
  - Una RPC que registra la respuesta (una por hogar y día).
  - Reutilizar la animación de la ruma (`pwa/public/ruma/`, `pwa/public/anim/bale-*`) [Código].
  - **No** llamar a `ruma-assistant` por hogar y día: la capa gratuita de los modelos tiene límites de uso
    (`maki-capacidad-ia`).
- **Riesgo.**
  - **Escanear de más.** Si el hogar fotografía cada día toda la bolsa, la declaración se infla. El escaneo
    no paga y el pesaje manda, así que el daño es de datos, no de dinero.
    - La pantalla debe pedir «solo lo nuevo de hoy».
    - Hay que revisar cómo se acumulan varios escaneos de días distintos en un mismo recojo
      (`create_pickup_from_detection`). No se leyó su cuerpo en este trabajo.
  - **Generar basura.** Bajo, porque nada se paga por volumen diario.
  - **Fatiga.** Las preguntas se acaban; en el piloto hay que rotar el banco cada mes.
- **Se apoya en:** Rumi (personaje e imagen `pwa/public/rumi/rumi.webp`), la cámara y la detección, la ruma 3D
  y el anillo semanal.

### Idea 2. «Mercadito Ruma»: la oferta del día y el canje en bodega

- **Qué es.** Es el marketplace de Sweatcoin a escala de Cartavio, en dos piezas:
  - **El canje en bodega.** Una opción de canje más, junto a Yape y arbitrios.
    - Un hogar con 1.000 pts muestra un código en su bodega aliada, y el bodeguero lo valida con un PIN.
    - La bodega da S/ 1 en productos, o más (S/ 1,20) si quiere atraer clientes: ese extra lo pone ella.
    - Ruma le paga a la bodega en **una sola transferencia semanal**.
  - **La oferta del día.** Una sola, que cambia cada día, al estilo de Cash App Boosts, puesta por la bodega o
    por una marca. Por ejemplo: «Hoy en Bodega Rosita: 2x1 en pan con tu código Ruma». La financia quien la
    publica.
- **Evidencia.**
  - La rotación diaria de ofertas baratas es el núcleo del marketplace de Sweatcoin [V-emp, BJSM].
  - Yape Promos ya enseñó este formato en Perú [V].
  - Tpaga integró bodegas sin cuenta bancaria [V].
  - El usuario peruano prefiere el premio seguro (la tendencia de Plin) [V-res].
- **Costo en dinero.**
  - **S/ 0 de más.** El canje en bodega consume los mismos puntos que el canje por Yape, que ya están en el
    40 %.
  - Incluso ahorra trabajo: 1 pago semanal por bodega en vez de un Yape por hogar. Según
    `canje-minimo-y-arbitrios.md` §2.4, cada pago manual cuesta S/ 0,25 de tiempo.
- **Desarrollo.**
  - De 1,5 a 2 semanas: tablas de aliados y ofertas, código de canje de un solo uso y lista semanal para el
    operador.
  - Además, trabajo de campo: firmar con 5-10 bodegas.
- **Riesgo.**
  - **Fraude.** Un bodeguero coludido podría validar canjes falsos. Lo limitan tres cosas: el canje solo usa
    puntos ya pesados, el código es de un solo uso y está ligado a un hogar, y la bodega solo valida, no
    crea.
  - **«Ofertas que exigen comprar»**, la queja de Sweatcoin. Por eso el canje en bodega, que es dinero, va
    primero, y la oferta del día va después.
  - **Basura.** Si la oferta es «compra gaseosa», empuja a comprar envases. Regla: no ofertas de bebidas
    embotelladas.
  - **Complejidad.** Hace falta un operador que visite bodegas.
  - **Legal.** Revisar si el canje en especie necesita algo más que las bases y la factura de la bodega.
- **Se apoya en:** `redemption_options` (una opción `BODEGA` nueva), la wallet, el canje seguro
  (`2026-11-11_canje_seguro.sql`, por revisar) y el panel de operador.

### Idea 3. Cuadra contra cuadra, con la meta del barrio

- **Qué es.**
  - Cada cuadra es un equipo. Su marcador es **el % de hogares de la cuadra que tuvieron un recojo pesado este
    mes**, no los kilos.
  - La cuadra ganadora del mes recibe algo colectivo y visible: una mención municipal, un macetero o una banca
    hecha de PET, o un vale para la bodega de la cuadra.
  - La barra de Cartavio («vamos 412 kg de 1 tonelada») está encima.
- **Evidencia.**
  - La retroalimentación del barrio en reciclaje (Schultz 1999) y Opower, en el documento de retención [V].
  - El «Teamwork» de Ibotta [V, blog].
  - Las rachas con amigos de Duolingo, +22 % [V-emp].
- **Costo en dinero.**
  - Un premio de S/ 30-60 al mes para la cuadra ganadora: **S/ 0,17-0,33 por hogar al mes**.
  - Con un premio municipal o de patrocinador, S/ 0.
- **Desarrollo.** De 1 a 1,5 semanas: asignar `block_id` a la dirección, una vista mensual por cuadra y la
  barra en el inicio. Ya existe un ranking por cuadra [Código], así que es reordenarlo por participación.
- **Riesgo.**
  - Cuadras con 2 hogares no compiten: agrupar en «sectores» de 10-20 hogares.
  - Vergüenza para la cuadra última: no publicar el último puesto.
  - Si se cuenta en kilos, premia el volumen. Por eso se cuenta en % de hogares activos.
- **Se apoya en:** el ranking por cuadra, el feed de notificaciones (`trg_pickup_notify`) y la meta municipal de
  «viviendas que participan» (documento de retención §2.2).

### Idea 4. Temporada mensual con misiones de calidad y el «Día Ruma» del barrio

- **Qué es.**
  - Cada mes, una temporada con nombre (por ejemplo, «Diciembre: cartón seco») y 3-4 misiones:
    - una de calidad: «tu cartón llegó plegado y seco», que confirma el rider;
    - una de constancia: «2 semanas activas»;
    - una social: «invita a un vecino que tenga su primer pesaje»;
    - una de aprender: «responde 10 preguntas de Rumi».
  - El **pase es gratis.** Completarlo da una insignia, un adorno para la ruma 3D (bandera, sombrero de
    Rumi) y boletos para el sorteo de la temporada.
  - Un sábado al mes, el «Día Ruma»: recojo con doble boleto y foto de la cuadra.
- **Evidencia.**
  - Los eventos con fecha sostienen a Pokémon GO [V].
  - Las «Races» de Sweatcoin terminan en sorteo [V-emp].
  - Los retos patrocinados de Strava [V-emp].
  - La novedad decae a las ~4 semanas (documento de retención §2.4): una temporada mensual la renueva.
- **Costo en dinero.**
  - S/ 0 en insignias y adornos.
  - El sorteo de temporada (S/ 50 al mes, ya propuesto en el documento de retención) suma **S/ 0,28 por hogar
    al mes**, o S/ 0 si lo auspicia una marca (idea 9).
- **Desarrollo.** De 1,5 a 2 semanas.
  - La tabla `challenges` ya existe y avanza al liquidar [Código]. Le faltan agruparse por temporada, una
    pantalla de temporada y los adornos de la ruma: 3-5 renders nuevos en Blender, como WebP.
- **Riesgo.**
  - Misiones de volumen («recicla 10 kg») empujan a generar: solo calidad, constancia y social.
  - Hay que publicar a los ganadores del sorteo para no repetir «I never win».
  - Complejidad: como máximo 4 misiones; en un Android de gama baja, una sola pantalla.
- **Se apoya en:** `challenges`, la ruma 3D en WebP, la confirmación del rider en la puerta y Rumi.

### Idea 5. «Raspadita del pesaje» (con tope, nunca diaria)

- **Qué es.**
  - Cuando el acopio pesa un recojo, aparece **una** raspadita en la celebración del pesaje.
  - Siempre da algo: mínimo 20 pts, valor medio unos 100 pts y tope de 2.000 pts. La probabilidad está
    publicada.
  - No hay raspadita por abrir la app ni se compra con puntos.
- **Evidencia.**
  - Los premios variables pequeños sí mueven: la lotería diaria de Volpp y Loewenstein, en el documento de
    retención [V].
  - Plin y otros bancos peruanos usan sorteos y «raspa y gana» [V-res].
  - En contra: el gasto en cajas sorpresa se asocia con el juego problemático [V, Zendle y Cairns], y en
    Sweatcoin se critica «Daily boosts often returns zero value» [V]. El diseño responde con premio mínimo,
    sin pago y sin frecuencia diaria.
- **Costo en dinero.** Unos 100 pts de media por recojo y 2 recojos al mes: **S/ 0,20 por hogar al mes**
  (S/ 36 al mes en el piloto). Se puede financiar con el auspicio de la temporada.
- **Desarrollo.** De 3 a 4 días. El sorteo se hace **en la base** dentro de `settle_delivery` o en una RPC
  posterior, nunca en el cliente («el cliente pide, la base decide»). En la PWA, una animación con CSS.
- **Riesgo.**
  - Sensación de apuesta: el premio mínimo, el valor medio publicado y la ausencia de pago lo separan de una
    apuesta.
  - **Recojos partidos para tener más raspaditas.** El tope es una por recojo con un peso mínimo (por ejemplo,
    1 kg) y no más de 4 al mes.
  - Legal: bases ante Indecopi, como cualquier promoción (documento de retención §3.3). Validar con un abogado.
- **Se apoya en:** la celebración del pesaje ya aplicada (`resenas-usuarios-diseno.md`, mejora 4) y
  `settle_delivery`.

### Idea 6. Feed de impacto del barrio

- **Qué es.** Una tira en el inicio con hechos reales del barrio, en tiempo casi real. Por ejemplo:
  - «La cuadra 4 juntó 38 kg esta semana».
  - «Don Julio (Eco-Rider) hizo 12 recojos hoy».
  - «El acopio pesó 210 kg el viernes».
  - «Cartavio va en el 41 % de la meta».
- **Evidencia.** La retroalimentación normativa (Schultz 1999) [V]. El valor de la relación con la persona que
  recoge, en las reseñas de Olio (E4) [V].
- **Costo en dinero.** S/ 0.
- **Desarrollo.** De 3 a 5 días: una vista agregada sin datos personales y la tira en el inicio.
- **Riesgo.**
  - Privacidad: nunca dirección ni nombre del hogar sin su permiso; el nombre del rider, sí con su
    consentimiento.
  - Cifras infladas: solo lo pesado.
- **Se apoya en:** `deliveries`, `pickups`, `trg_pickup_notify` y las notificaciones push nuevas
  (`supabase/sql/2026-11-12_push_notifications.sql`; verificar si ya están aplicadas).

### Idea 7. Referidos que pagan al primer pesaje

- **Qué es.** El invitado recibe 1.000 pts en su **primer pesaje**. El invitante recibe 1.000 pts cuando el
  invitado completa su **segundo** pesaje. Es uno por dirección, y la dirección debe ser distinta. Detalle en
  el documento de retención §3.5.
- **Evidencia.**
  - Los clientes referidos valen al menos un 16 % más y se quedan más tiempo (banco, unos 10.000 clientes) [V]
    ([Schmitt, Skiera y Van den Bulte, *J. Marketing* 2011](https://faculty.wharton.upenn.edu/wp-content/uploads/2012/04/Schmitt-Skiera-vandenBulte-2011-Referral-Programs-Customer-Value.pdf)).
  - Sweatcoin exige que el invitado haya caminado [V-emp].
  - Duolingo: solo +3 % de usuarios nuevos (documento de retención).
- **Costo en dinero.**
  - S/ 2 por hogar nuevo **verificado**, una sola vez.
  - Si el 10-20 % de los hogares trae a uno al mes, son **S/ 0,20-0,40 por hogar activo al mes**.
  - Para comparar, Finch paga más de US$ 20 por instalación [V, Deconstructor of Fun].
- **Desarrollo.** De 4 a 6 días: código de invitación, columna `referred_by`, pago en la base al liquidar, y
  controles de dirección y dispositivo.
- **Riesgo.**
  - Hogares inventados: el pago exige pesaje real y una dirección distinta, y el rider ve la puerta.
  - El «mismo hogar con dos cuentas»: uno por dirección.
- **Se apoya en:** `settle_delivery`, la dirección del hogar y el rider en la puerta.

### Idea 8. La ruma y Rumi como «criatura» con widget (después)

- **Qué es.**
  - La ruma 3D ya crece por etapas. Se suma un **widget de Android** que la muestra con la barra de la cuadra
    y un mensaje del día de Rumi.
  - En el pesaje, Rumi «se lleva» la ruma al acopio: una animación con renders de Blender.
- **Evidencia.** El widget de Finch como presencia sin regaños; D1 54 % y D7 37 % [V, analistas]. Las
  animaciones de hito suben un 1,7 % el D7 en Duolingo (documento de retención) [V-emp].
- **Costo en dinero.** S/ 0.
- **Desarrollo.** De 2 a 3 semanas: un `AppWidgetProvider` con RemoteViews, que funciona en minSdk 24, más los
  renders. En la PWA no hay widget en iOS, así que esto solo es para Android.
- **Riesgo.** Batería y memoria en gama baja: actualizar el widget como mucho cada pocas horas, con imágenes
  estáticas. Que parezca infantil: probar el tono con las usuarias reales.
- **Se apoya en:** `pwa/public/anim/` (bale, city, pin, rider, ring) y `ruma-1..5.webp` [Código], Blender y
  Rumi.

### Idea 9. Marcas: patrocinio de temporada y reporte, no un pago por botella

- **Qué es.**
  - Ruma vende a una marca (Arca Continental Lindley, AJE, San Miguel Industrias o una marca local) el
    **patrocinio de la temporada**: nombre, premio del sorteo y ofertas del Mercadito.
  - A cambio, un **reporte mensual agregado**: kg de PET pesados en Cartavio, % con su marca detectada y
    zonas. Le sirve para su meta de reciclaje y para la Ley 30884.
  - Más adelante, un «bono de marca» **sobre peso confirmado**, nunca por botella comprada.
- **Evidencia.**
  - Fetch cobra a las marcas el valor de los puntos más un margen [V].
  - Strava vende retos patrocinados [V-emp].
  - Polytag, Ocado y Bower venden el dato de reciclaje [V-emp].
  - Las embotelladoras peruanas ya gastan en programas de reciclaje con premios [V].
  - La obligación del 15 % de PET reciclado [V].
- **Costo en dinero.** S/ 0. Es un ingreso. Para orientar [R]: un patrocinio de S/ 500-1.000 al mes cubre el
  sorteo, la raspadita y el premio de la cuadra de todo el piloto (ideas 3-5).
- **Desarrollo.**
  - De 1 a 2 semanas para el reporte, porque `v_brand_zone` ya existe [Código], y para el espacio del
    patrocinador.
  - Lo caro es vender: con 180 hogares el volumen es pequeño, y el argumento es el piloto replicable más que
    los kilos.
- **Riesgo.**
  - **Incentivo a comprar más bebidas.** Nunca premiar comprar la marca; premiar devolver bien.
  - Datos personales: solo agregados, conforme a la Ley 29733.
  - **La marca detectada por la IA no es fiable para pagar** (CLAUDE.md: la marca «nunca paga puntos»). Sirve
    para reportes con margen de error declarado, no para pagos.
  - *Greenwashing* de la marca: el reporte dice kilos pesados, no «botellas salvadas».
- **Se apoya en:** la detección de marca (`brands.ts`, 54 marcas), `v_brand_zone`, `ruma_analytics` y
  Mercado Vivo.

### Idea 10. Lo que descarto o dejo para mucho después

| Idea | Por qué no ahora [R] |
|---|---|
| **Ruleta o caja sorpresa diaria por abrir** | Premia abrir, no reciclar. Se parece a una apuesta [V, Zendle y Cairns]. Sweatcoin cosecha «returns zero value». La raspadita del pesaje (idea 5) toma lo bueno sin esto. |
| **Álbum de botellas por marca** | Empuja a comprar marcas para completar el álbum (ya descartado en el documento de retención §3). Alternativa: un álbum de **hitos** (primer pesaje, cartón perfecto, cuadra ganadora). |
| **Escaneo de boletas de compra** | El 59 % de los locales inspeccionados no emitía boleta [V, SUNAT]. Hay fraude con boletas falsas y premia comprar. Además, SUNAT ya sortea hasta S/ 100.000 por pedir boleta ([Andina](https://andina.pe/agencia/noticia-mef-y-sunat-lanzan-campana-yo-pido-boleta-por-peru-1040927.aspx)): competir con eso no tiene sentido. |
| **Suscripción que multiplica lo ganado** | Es el modelo de Sweatcoin Premium y se lee como «pagar para cobrar». En hogares de ingresos bajos es un error de marca. |
| **Contenido educativo que paga puntos a diario sin tope** | Sí, con boletos o con tope de 100 pts al mes (idea 1). Sin tope es la «gamificación de Gardenscapes»: neutra en Duolingo. |
| **Cripto o segunda moneda** | Es la peor crítica de Sweatcoin [V]. |
| **Ligas semanales por grupos** | Sin masa crítica quedan vacías (documento de retención §3). |

---

## 4. El loop diario que debe tener Ruma [R]

Ruma tiene tres ritmos, y cada uno necesita su momento. **El error sería forzar dinero en el ritmo diario.**

| Ritmo | Disparador | Acción (menos de 1 min) | Recompensa | Lo que queda invertido |
|---|---|---|---|---|
| **Diario** | Notificación a la hora en que el hogar bota la basura; la elige el hogar, por defecto 19:00. Como máximo una al día, nunca de 21:00 a 08:00 | «Echar a la bolsa»: foto de lo nuevo o «hoy no». Luego la pregunta de Rumi (1 toque) | La bolsa del hogar sube; la barra de la cuadra se mueve; se gana un boleto; aparece la oferta del día | Progreso hacia «tu recojo ya vale el viaje» |
| **Semanal** | «Tu bolsa ya está para recojo» o el sábado del Día Ruma | Pedir recojo con un toque | Conocer al rider (el mismo de siempre); semana cumplida en el anillo | Racha semanal y misión de constancia |
| **Mensual** | «Tu material se pesó» | Abrir la celebración: raspadita y saldo disponible | Soles reales (Yape, bodega, arbitrio); premio de la cuadra; cierre de la temporada | Nueva temporada; ruma decorada |

**Reglas del loop diario**

- **Nada diario paga soles.** Los soles llegan solo con el pesaje. Lo diario paga progreso, información y
  boletos.
- **«Hoy no eché nada» también es una respuesta válida y sostiene el hábito.** Copia la falta de metas de
  Sweatcoin y el «anti-nag» de Finch. No hay racha diaria que perder.
- **Es la misma pantalla en Android y en la PWA.** En iPhone la notificación solo llega con la PWA instalada,
  y no hay widget.

**Métrica del loop**

- **Días con apertura por semana por hogar activo** (meta inicial: 3 o más).
- **W4 de recojo pesado**: que no baje.
- Requiere el evento `app_open`, que todavía no existe (documento de retención §5, fase 1).

---

## 5. Propuesta priorizada en 3 olas [R]

Los costos en dinero son por hogar activo al mes. Las métricas de validación usan las definiciones del
documento de retención §5. **Antes de cualquier ola** van dos cosas que ya están recomendadas: cerrar
`validate_redemption` (canje doc §5) y llevar el mínimo de Yape a S/ 1. Sin eso, ninguna mecánica nueva
compensa un «nunca voy a cobrar».

### Ola 1: 2 semanas. El loop diario, sin gastar

| Pieza | Idea | Dinero | Desarrollo |
|---|---|---|---|
| Bolsa del hogar + «hoy no» | 1 | S/ 0 | 3 días |
| Pregunta diaria de Rumi con boletos (60 preguntas revisadas) | 1 | S/ 0 | 3 días |
| Feed de impacto del barrio en el inicio | 6 | S/ 0 | 3 días |
| Notificación diaria a la hora elegida, con tope | — | S/ 0 | 1-2 días, sobre las push del 2026-11-12 |
| Evento `app_open` y vista de cohortes | — | S/ 0 | 1-2 días |

- **Total:** S/ 0 por hogar al mes y unas 2 semanas de una persona.
- **Métrica que la valida:**
  - Días con apertura por semana: 3 o más en la mediana de los hogares activos.
  - % de hogares que responden la pregunta de Rumi 3 o más días por semana.
- **Métricas de daño:**
  - % que desactiva las notificaciones.
  - Kg por recojo estable.
  - Diferencia entre lo declarado y lo pesado que no crezca.

### Ola 2: 1-2 meses. El barrio y el Mercadito

| Pieza | Idea | Dinero | Desarrollo |
|---|---|---|---|
| Canje en bodega (5-10 bodegas) y oferta del día | 2 | S/ 0 | 2 semanas + campo |
| Cuadra contra cuadra por % de hogares activos | 3 | S/ 0,17-0,33 | 1-1,5 semanas |
| Primera temporada con misiones de calidad y el Día Ruma | 4 | S/ 0-0,28 | 1,5-2 semanas |
| Raspadita del pesaje, tras la revisión legal | 5 | S/ 0,20 | 3-4 días |
| Referidos que pagan al pesaje | 7 | S/ 0,20-0,40 | 4-6 días |

- **Total:** de S/ 0,57 a S/ 1,21 por hogar al mes **sin patrocinio**, entre un 30 % y un 63 % más que los
  S/ 1,92 de puntos actuales. Por eso la ola 2 debe salir **con la venta de la ola 3 empezada**.
- **Métrica que la valida:**
  - W4 y W8 de recojo pesado, comparados con la línea base de las primeras 4 semanas.
  - Recojos por hogar activo al mes.
  - % de hogares de cada sector activos en el mes.
  - % de canjes que se hacen en bodega.
  - % de referidos con pesaje.

### Ola 3: después (3 meses o más). Quién lo paga y la presencia

| Pieza | Idea | Dinero | Desarrollo |
|---|---|---|---|
| Patrocinio de temporada y reporte mensual a una marca | 9 | ingreso | 1-2 semanas + venta |
| Widget de Android con la ruma y Rumi | 8 | S/ 0 | 2-3 semanas |
| Ofertas de marca en el Mercadito; bono de marca solo sobre peso confirmado | 2 y 9 | lo paga la marca | 2 semanas |
| Ofertas de Ruma dentro de Yape Promos o de la app municipal | — | por negociar | por definir |
| Ligas, si hay más de 100 hogares activos por zona | doc. retención | S/ 0 | 2-3 semanas |

- **Métrica que la valida:**
  - **Ingreso de terceros por hogar activo ≥ costo de las ideas 3-5 y 7**, es decir, que el enganche no lo
    pague el margen del material.
  - D30 de apertura.
  - Retención W12.

---

## 6. Lagunas y límites

- **Sweatcoin no publica retención absoluta.** Las únicas cifras (25 % de usuarios mensuales sobre descargas en
  2018 y +19,5 % de pasos) vienen de artículos escritos con sus fundadores, sin grupo de control.
- **El centro de ayuda de Sweatcoin, Business of Apps y BBVA devolvieron 403.** Esos datos se tomaron del
  resumen del buscador [V-res].
- **No se confirmó el esquema actual de niveles de Sweatcoin.** Las fuentes hablan de un Premium en dinero que
  reemplazó a los niveles pagados en sweatcoins.
- **Lucky Day, Shopkick y Cash App Boosts** solo tienen reseñas de terceros. Cash App parece haber
  renombrado Boosts como «Offers».
- **No se encontró cuántos usan Yape Promos** ni la mecánica de premio de «Bodega Sin Residuos».
- **No se sabe si los proyectos de 2025 que suben el PET reciclado al 30 % se aprobaron.**
- **Ninguna de las ideas se ha probado con hogares de Cartavio.** Los costos son cálculos con los supuestos del
  libro de costos (180 hogares, 2 recojos al mes, S/ 1,92 de puntos por hogar).
- **No se leyó el cuerpo de `create_pickup_from_detection`.** Antes de la idea 1 hay que confirmar cómo se
  suman en un recojo los escaneos de días distintos.
- **No se verificó en qué entornos están aplicados** `2026-11-11_canje_seguro.sql` y
  `2026-11-12_push_notifications.sql`. Hay que revisar sus cabeceras antes de construir sobre ellos.
