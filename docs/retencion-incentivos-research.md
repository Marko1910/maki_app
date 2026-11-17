# Retención e incentivos para Ruma: investigación y propuesta

Fecha: 2026-11-11. Estado: investigación de escritorio y propuesta. Nada de esto se ha implementado ni
probado con usuarios de Ruma. El código se leyó, no se modificó, y no se tocó ninguna base de datos.

## Cómo leer este documento

Cada afirmación lleva una etiqueta:

- **[V]** verificado con una fuente independiente: estudio, organismo público o prensa que cita datos.
- **[V-emp]** cifra que publica la propia empresa o un proveedor interesado, como TOMRA, Rive o Recyclebank.
  Es cierta como declaración, pero nadie la auditó.
- **[R]** recomendación o inferencia mía. Son hipótesis para probar en el piloto, no hechos.
- **[Código]** lo que existe hoy en el repositorio, con la ruta del archivo.

Cuando una búsqueda no encontró un dato, por ejemplo la retención de Bower o el efecto de los clubes de
Strava, el documento lo dice en vez de rellenar el hueco.

---

## 0. Resumen: las cinco recomendaciones

1. **El hábito se mide en semanas y recojos verificados, nunca en kilos ni en fotos.** Ruma ya tiene un
   anillo semanal y una racha por semanas. Conviene bajar la meta por defecto a 1 recojo o 1 día por semana,
   que es el ritmo natural de un hogar que junta PET y cartón. Bajar la exigencia elevó en más de 40 % a
   quienes sostenían una racha de 7 días en Duolingo [V]. Los multiplicadores de puntos por racha y los
   premios del ranking por puntos premian el volumen y deben revisarse.
2. **El momento de pago es el pesaje en el acopio.** Hoy pasa en silencio. Debe ser la notificación y la
   celebración principales: "Tu material se pesó: +240 pts disponibles". Sustituir el consejo diario
   aleatorio por notificaciones disparadas por eventos, con tope diario y sin urgencias falsas.
3. **Lo que mueve es lo social y lo colectivo, más que los céntimos.** Proponer una meta de Cartavio o de la
   cuadra con una barra comunitaria, retroalimentación del barrio y reconocimiento visible. El efecto de
   informar a un barrio sobre su propio reciclaje está medido en campo [V]. El ranking debe ser relativo, sin
   exponer a los últimos y ordenado por semanas activas.
4. **Una "ruma" que crece, dibujada en 3D y exportada como imágenes WebP por etapas.** Coste de ejecución
   casi nulo en Android 7 y en iPhone. Las capas sólidas son lo ya confirmado y las translúcidas lo
   pendiente. Celebraciones con animación nativa (Compose o CSS) antes que con Lottie. Rive, three.js y
   Filament quedan para la fase 2, si se justifican.
5. **Medir antes de añadir.** La primera semana se dedica a una vista de cohortes D1/D7/D30 y W1/W4, recojos
   por hogar al mes, tasa de canje y señales de fraude (kg por recojo, diferencia entre lo declarado y lo
   pesado). Después vienen un sorteo mensual con boletos por recojo verificado y un programa de referidos que
   paga solo cuando el material se pesa.

---

## 1. Lo que ya existe en Ruma (lectura del código)

| Pieza | Dónde | Observación relevante para retención |
|---|---|---|
| Anillo de meta semanal (días con un escaneo guardado) y racha de semanas | [Código] `pwa/src/lib/week.ts`, `app/.../data/WeekProgress.kt` | Cuenta días distintos según el calendario de Lima. No paga, y está bien así. La meta elegida (por defecto 3) vive en `localStorage` de la PWA, así que el servidor no puede medirla ni la sincroniza entre dispositivos. |
| Racha del servidor `current_streak_days` y `streak_multiplier` | [Código] `detect-material/index.ts` (l. ~695-711) | **El multiplicador de racha entra en los puntos del escaneo.** Si la racha es diaria, premia partir el material en muchos recojos pequeños, lo que encarece la ruta del rider por kilo. |
| Ranking de cuadra y de riders, con premios por puesto (`ranking_rewards.reward_points`) | [Código] `Family.tsx` `Leaderboard`, `api.ts` | Si ordena por puntos, premia el volumen. Además expone a los últimos puestos (ver §3.4). |
| Retos que avanzan al liquidar (`advance_challenges`) | [Código] `supabase/sql/2026-10-19_cadena_entregas.sql` | Buen diseño: avanzan con material verificado, no con fotos. |
| Logros e insignias, CO₂ evitado y equivalente en árboles | [Código] `Family.tsx` (perfil), `Records.tsx` | Están en el perfil, lejos del momento de la acción. |
| Notificación diaria (`EcoTipsWorker`) | [Código] `app/.../data/EcoTipsWorker.kt` | Es una frase aleatoria o "Subió el precio… ¡Buen momento para reciclar antes de que baje!". Lo segundo es urgencia de mercado que el hogar no puede aprovechar con unos céntimos (ver §3.6). |
| Wallet: `points_pending` y `points_balance`; canje Yape/Plin | [Código] `api.ts`, `redemption_options` | Existe la separación entre pendiente y disponible, que es la base de una buena celebración del pesaje. |
| Mercado Vivo y asistente de chat | [Código] `ui/screens/market/`, `ruma-assistant` | Sirven para aprender y para dar contenido de vuelta, no como bucle diario. |
| Propuesta previa del inicio | `docs/home-progress-ux-proposal.md` | Coincide con este documento: hábito útil, no maximizar aperturas ni residuos. Esta investigación la respalda con datos. |

**Economía por recojo.** [R] Es un cálculo con los precios del repositorio: PET a 400 pts/kg, que con
1.000 pts = S/1 son S/0,40/kg, y cartón a 240 pts/kg. Si una botella de PET de 500 ml pesa unos 20-25 g, que
es un supuesto, 30 botellas son unos 0,6-0,75 kg: **240-300 pts, unos S/0,25-0,30.** Un hogar activo podría
juntar S/1-3 al mes. Con esos montos el dinero sirve como *señal* de que el material vale, no como motivo.
Así se confirma la premisa del encargo.

---

## 2. Casos reales: mecánica, métrica, qué funcionó y qué no

### 2.1 Recompensas por reciclar

**Recyclebank (EE. UU., desde 2004)**

- Mecánica: puntos por peso (contenedores con RFID) o por participación del barrio, canjeables por cupones
  de comercios.
- Métricas: la empresa reportó que un barrio de Filadelfia pasó del 30 % al 90 % de participación en meses y
  que la ciudad aumentó su desvío un 29 % en tres años [V-emp] ([Waste360](https://www.waste360.com/residential-waste/winn-reflects-on-recyclebank-s-record),
  [WHYY](https://whyy.org/articles/how-philadelphia-quadrupled-recycling-rates-in-four-years)). WHYY advierte
  que no está claro cuánto se debe a los premios [V].
- Qué no funcionó [V]: Filadelfia canceló Philacycle en 2019. Costaba entre 0,5 y 1,5 millones de dólares al
  año y se financiaba con la venta del reciclable; cuando los precios de los materiales cayeron, dejó de ser
  viable ([Waste Dive](https://www.wastedive.com/news/philadelphia-cancels-recycling-rewards-program/556248/),
  [phila.gov](https://www.phila.gov/2019-05-31-streets-dept-announces-the-discontinuance-of-the-philacycle-program)).
  Cincinnati lo dejó por baja participación y coste
  ([Waste360](https://waste360.com/waste-recycling/cincinnati-ends-recycling-rewards-program-with-recyclebank)).
  En Fort Worth solo se inscribieron 38.000 de 215.000 hogares
  ([Waste360](https://www.waste360.com/residential-waste/fort-worth-texas-altering-recyclebank-program-due-to-low-participation)).
  Phoenix cortó un contrato de 2 millones de dólares al año
  ([Recycling Today](https://www.recyclingtoday.com/news/phoenix-recyclebank-contract-canceled)).
- Lección para Ruma [R]: **el premio debe salir del margen del propio material, con un tope.** Ruma ya lo hace
  (40 % del valor). Ese es su blindaje frente al error de Recyclebank. Nada de premios fijos que no dependan
  del precio del PET y del cartón.

**Bower (Suecia y nórdicos, desde 2015)**

- Mecánica: escanear el código del envase, reciclarlo en un punto y recibir puntos que se canjean por dinero
  o descuentos. Las marcas financian parte de los premios: Probi y la marca propia de Coop Suecia
  ([Packaging Insights](https://www.packaginginsights.com/news/probi-to-reward-customers-for-recycling-by-partnering-with-bower-mobile-application.html),
  [Packaging Gateway](https://www.packaging-gateway.com/analyst-comment/coop-sweden-bower-recycling)).
- Métricas [V-emp]: unos 430.000 usuarios; "1 de cada 10 escanea cada mes"; el 70 % dice que mejoró su
  reciclaje, una cifra autodeclarada ([EU-Startups](https://www.eu-startups.com/2022/10/rallying-the-community-around-recycling-the-vision-of-suwar-mert-founder-of-bower/)).
- No se encontraron datos públicos de retención.
- Lección [R]: el 10 % de actividad mensual es una advertencia, porque aun con premios en dinero la mayoría
  abandona. El modelo de premios pagados por marcas encaja con Ruma, que ya detecta la marca. Pero hoy la
  marca "nunca paga puntos" por una razón antifraude: la lectura de la IA no es fiable. Una bonificación por
  marca solo cabría sobre peso confirmado (fase 2).

**Olio (Reino Unido, compartir comida)**

- Mecánica: regalar excedentes entre vecinos; motivación social y comunitaria.
- Métricas [V-emp]: el 65 % dice haber hecho amigos al compartir; las cifras de usuarios cambian entre
  versiones de su web (8,5 a 9,3 millones) ([Olio, impacto](https://olioapp.com/en/our-impact/)). Olio misma
  advierte que su cálculo de emisiones supone que "1 artículo compartido = 1 no producido".
- No hay datos públicos de retención.
- Lección [R]: el vínculo vecinal es un motor real. Para Ruma, la relación con el *mismo* Eco-Rider del barrio
  y la cuadra como equipo valen más que una insignia.

**TOMRA y los sistemas de depósito-retorno (DRS)**

- Mecánica: pagas un depósito al comprar y lo recuperas al devolver el envase en una máquina. Es aversión a
  la pérdida en estado puro: el dinero ya era tuyo.
- Métricas: Alemania 98 %, Finlandia 99 %, Noruega 92 % de retorno [V] ([Reloop](https://www.reloopplatform.org/wp-content/uploads/2025/11/20251124_Key-factors-impacting-DRS-performance_FactSheet_vFinal.pdf)).
  Lituania pasó del 34 % al 92 % en dos años tras lanzar su DRS en 2016 [V-emp]
  ([TOMRA](https://www.tomra.com/en/collection/reverse-vending/case-studies/roll-out-lithuania),
  [Seas at Risk](https://map.seas-at-risk.org/national-deposit-return-system/)).
- Qué no funcionó [V]: el fraude transfronterizo. En Michigan, donde el depósito es alto, se llevan latas
  compradas en otros estados; la industria estima pérdidas de hasta 13 millones de dólares al año y el estado
  dice que no las puede cuantificar ([Fox News](https://www.foxnews.com/politics/smugglers-still-cashing-in-on-michigan-can-refund),
  [Recycling Today](https://www.recyclingtoday.com/news/michigan-ag-bottle-deposit)).
- Caso latinoamericano, **Ecuador** (impuesto redimible de 2 centavos por botella PET): una tesis de la UTA
  sobre 2014-2024 concluye que el impuesto recauda pero no reduce de forma sostenida el uso de botellas y que
  su sistema de devolución es débil [V] ([repositorio UTA](https://repositorio.uta.edu.ec/items/e988e41c-fc2b-4ded-8189-89e7abf5bd64)).
- Lección [R]: un DRS funciona por la **conveniencia universal** (devolver en cualquier tienda) y por la
  pérdida. Ruma no cobra depósito, pero puede copiar el encuadre de pérdida *honesto*: "Tienes 300 pts
  esperando en tu bolsa" es tuyo y está pendiente, sin inventar pérdidas.

**Cataki (Brasil)**

- Mecánica: un "Tinder del reciclaje" que conecta a quien tiene material con catadores independientes, que
  negocian un precio por la recogida.
- Métricas: inconsistentes según la fuente, entre 10.000 y 350.000 descargas y entre 450 y 3.000 catadores
  [V-emp] ([Julius Baer](https://www.juliusbaer.com/it/insights/future-cities/cataki-a-recycling-match-made-online),
  [Catraca Livre](https://catracalivre.com.br/as-melhores-solucoes-sustentaveis/app-para-catadores-os-conecta-a-quem-quer-jogar-coisas-fora/)).
  Un estudio en Belo Horizonte halló que **solo el 11 % de los catadores encuestados tenía acceso a internet**
  [V] ([FMU](https://revistaseletronicas.fmu.br/index.php/inovae/article/download/2814/1812/10071)).
- Lección [R]: la barrera está en el lado del recolector. El Eco-Rider de Ruma necesita datos móviles y un
  equipo que funcione; es un coste del piloto, no un detalle.

**Recylink (Chile)**

- Mecánica: plataforma B2B de gestión de residuos industriales con trazabilidad, empresa B.
- Métricas [V-emp]: 20.046 t gestionadas y 3.279 tCO₂eq evitadas según su folleto de 2022
  ([CDT](https://www.cdt.cl/wp-content/uploads/2022/06/Brochure-Recylink-1.pdf)).
- No es un producto doméstico ni publica retención.
- Lección [R]: la trazabilidad y los reportes son lo que pagan empresas y municipios. Ruma ya tiene los datos
  (`v_brand_zone`, pesajes); un reporte municipal es un ingreso posible, no un mecanismo de retención.

**Ecolana (México)**

- Mecánica: mapa de más de 6.000 puntos de acopio y una moneda propia (Ecolanitas) que se gana con retos y
  se canjea por descuentos y entradas.
- Métricas: más de 200.000 descargas y una alianza con SIMEPRODE (Nuevo León, mayo de 2026) [V]
  ([Gobierno de Nuevo León](https://nl.gob.mx/es/boletines/simeprode-firma-alianza-con-ecolana-para-integrar-su-aplicacion-para-incrementar-el)).
  El 78 % de quienes buscan en la app son mujeres [V-emp] ([La Razón](https://www.razon.com.mx/mexico/mujeres-jovenes-son-interesadas-reciclar-hombres-participan-incentivos-482985)).
- Lección [R]: **la alianza municipal da escala y legitimidad.** Además, el perfil de quien recicla en casa
  puede ser mayoritariamente femenino: hay que probar el tono y los premios (recargas, canasta) con las
  usuarias reales de Cartavio.

### 2.2 Perú y Latinoamérica

- **Ecotrash** (Lima): app con 30 recolectores en 17 distritos; puntos canjeables; reporta subir hasta un
  40 % los ingresos de los recolectores [V-emp] ([Andina](https://andina.pe/ingles/noticia-ahora-ya-puedes-reciclar-tus-plasticos-solo-hacer-un-click-tu-celular-768741.aspx)).
- **TuPoinT** (Perú y Bolivia): recojo programado desde casa y puntos canjeables en comercios aliados,
  financiados por empresas [V-emp] ([Infomercado](https://infomercado.pe/emprendedores-tupoint-una-plataforma-que-revoluciona-el-reciclaje-en-peru-y-parte-de-bolivia-emprendimientos/)).
- **EcoVen**: máquinas de reciclaje con QR y "Ecopuntos" para descuentos [V-emp]
  ([Actualidad Ambiental](https://www.actualidadambiental.pe/?p=54098)).
- **ReciVeci** (Ecuador, anunciado para Lima en 2023): no se pudo confirmar que siga operando
  ([Primicias](https://www.primicias.ec/noticias/economia/startup-ecuatoriana-reciclaje-latinoamerica)).
- **Ninguno publica retención, recojos por usuario ni tasa de canje.** [V] Es la mayor laguna de la
  investigación: no hay un referente peruano con métricas. Ruma tendrá que fijar su línea base en las
  primeras 4 semanas del piloto.
- **Marco municipal** [V]: el programa de incentivos del MEF y el MINAM fijó a los municipios metas de
  "porcentaje de viviendas urbanas" en segregación en la fuente, por ejemplo el 20 % para ciudades tipo B en
  2014 ([MEF, meta MINAM tipo B 2014](https://www.mef.gob.pe/contenidos/presu_publ/migl/metas/MINAM_tipoB_2014.pdf)).
  Santiago de Cao, el distrito de Cartavio, tiene un programa de educación ambiental para 2023-2026 y un plan
  de residuos para Cartavio ([SINIA](https://sinia.minam.gob.pe/sites/default/files/archivos/public/docs/54%20PME%20MD%20Santiago%20de%20Cao%20%28Ascope%20-%20LA%20LIBERTAD%29.pdf),
  [UNITRU](https://alicia.concytec.gob.pe/vufind/Record/UNIT_581a42d1e79ac5f8548dbaf55b9b0d70)).
  [R] Ruma puede ofrecer a la municipalidad *el indicador que ya le piden*: viviendas que participan. Esa es
  una meta colectiva natural.
- **Bancos de basura en Indonesia (bank sampah)**, el análogo más cercano en un país de ingresos medios: en
  Depok participaba el 65 % de 182 hogares encuestados, y la distancia y la falta de información eran las
  barreras [V] ([MDPI y Undip, varios](https://ejournal.undip.ac.id/index.php/ilmulingkungan/article/download/53642/pdf)).
  Un estudio con 162 clientes concluye que la motivación "va más allá de lo económico" [V]
  ([Perbanas](https://journal.perbanas.ac.id/index.php/jebav/article/download/4110/1851/14816)).
- **Plastic Bank** (Filipinas, Indonesia): bonos en tokens además del pago en efectivo; una auditoría interna
  estima unos 250 dólares al mes para quien recolecta a tiempo completo en Indonesia [V-emp]
  ([Plastic Bank](https://plasticbank.com/collection-communities/)). Es relevante para el rider de Ruma, no
  para el hogar.
- **Contexto de pagos** [V]: Yape tiene unos 16 millones de usuarios activos al mes en Perú
  ([Perú Retail](https://www.peru-retail.com/yape-se-consolida-como-gigante-financiero-con-1-8-millones-de-prestamos-mensuales/)),
  así que el canje por Yape es la vía correcta.

### 2.3 Apps de hábito con buena retención

**Duolingo**: es el mejor documentado.

| Mecánica | Resultado | Fuente |
|---|---|---|
| Foco del equipo en la retención de usuarios actuales (CURR) | CURR +21 % en 4 años (−40 % de abandono diario de los mejores usuarios); DAU ×4,5 | [V-emp] [Lenny's / J. Mazal](https://www.lennysnewsletter.com/p/how-duolingo-reignited-user-growth) |
| Ligas semanales (tablas de unos 30 con ascenso y descenso) | +17 % de tiempo de aprendizaje; se triplicaron los usuarios muy comprometidos | [V-emp] ídem |
| Separar la racha de la meta diaria (basta 1 lección) | Retención D14 +3,3 %; usuarios con racha de 7+ días +40 % | [V-emp] [Duolingo blog](https://blog.duolingo.com/improving-the-streak) |
| Permitir 2 protectores de racha en vez de 1 | DAU +0,38 % (unas 200.000 personas más al día) | [V-emp] [Duolingo blog](https://blog.duolingo.com/how-duolingo-streak-builds-habit), [Mobbin](https://mobbin.com/blog/streaks-are-about-losing-a-study-of-859-streak-designs) |
| "Weekend Amulet" (protege el fin de semana) | +4 % vuelve a la semana; −5 % pierde la racha | [V-emp] vía [Mobbin y blog](https://mobbin.com/blog/streaks-are-about-losing-a-study-of-859-streak-designs) |
| Animaciones de hito de racha | Usuarios nuevos +1,7 % activos al día 7 | [V-emp] [Duolingo blog](https://blog.duolingo.com/how-duolingo-streak-builds-habit) |
| Rachas con amigos | +22 % completa la lección diaria | [V-emp] [Mobbin](https://mobbin.com/blog/streaks-are-about-losing-a-study-of-859-streak-designs) |
| Racha de 7 días | 3,6 veces más probable terminar el curso; 2,4 veces más probable volver al día siguiente (correlación, no causa) | [V-emp] [Duolingo blog](https://blog.duolingo.com/how-duolingo-streak-builds-habit) |
| Notificaciones con un bandit que penaliza repetir plantillas | Más lecciones completadas (el blog no da cifra) | [V] [KDD 2020, Yancey y Settles](https://research.duolingo.com/papers/yancey.kdd20.pdf) |

Lo que **no** funcionó en Duolingo [V-emp]: un contador de movimientos al estilo Gardenscapes salió
"completamente neutro". El programa de referidos solo subió un 3 % los usuarios nuevos. Atar la racha a la
experiencia acumulada (XP) confundió a los usuarios
([Lenny's](https://www.lennysnewsletter.com/p/how-duolingo-reignited-user-growth),
[Lenny's, rachas](https://www.lennysnewsletter.com/p/behind-the-product-duolingo-streaks)).

Lección [R]: la racha funciona cuando la barra es **baja y clara** y cuando perderla es **reparable**. Las
mecánicas copiadas de juegos sin relación con la tarea no mueven nada.

**Strava**

- Los clubes casi se cuadruplicaron en 2025, hasta un millón; Brasil es el mercado más social, con +800 % de
  clubes nuevos [V-emp] ([Strava Year in Sport, vía Webull/PR](https://www.webull.com/news/13961203642123264),
  [SGB](https://sgbonline.com/stravas-year-in-sport-trend-report-highlights-gen-zs-passion-for-run/)).
- **No se encontró ningún dato público sobre la retención de quienes se unen a clubes o retos.**
- Lección [R]: el resumen anual compartible ("Year in Sport") es una mecánica barata de reconocimiento
  social. En Ruma sería un "Tu año en Ruma" o "Tu mes en Ruma" para compartir por WhatsApp.

**Pokémon GO**

- En el estudio del BMJ, los jugadores caminaron +955 pasos al día en la primera semana, y **a las 6 semanas
  habían vuelto a su nivel previo** [V] ([BMJ / Howe et al. 2016, vía STAT](https://www.statnews.com/2016/12/13/pokemon-go/),
  [Smithsonian](https://www.smithsonianmag.com/smart-news/pokemon-go-craze-had-health-benefits-little-while-180961486/)).
- En 2016 su retención semanal era del 70 %, comparable a Candy Crush [V]
  ([Amplitude](https://amplitude.com/blog/pokemon-go-lost-players-won-game)).
- El modo de combate (PvP) se diseñó para recuperar a jugadores que se habían ido y, según Niantic, subió
  los usuarios activos diarios y mensuales [V-emp] ([GoNintendo](https://www.gonintendo.com/stories/331179-pokemon-go-s-pvp-mode-was-designed-to-bring-back-lapsed-players)).
- Lección [R]: la novedad decae en semanas. Lo que sostiene son los eventos con fecha y lo social
  (Community Day). Ruma necesita un calendario de retos mensuales, no un sistema estático.

**Finch (mascota virtual de autocuidado)**: se cita un D1 de alrededor del 60 %, pero solo en blogs y sin
fuente primaria. No se pudo verificar ([Monterail](https://www.monterail.com/blog/how-to-make-your-app-attract-users-every-day)).
Su mecanismo central, una mascota cuyo estado se ve en un widget, es plausible [R], y Finch usa un martillo
para "reparar" días perdidos ([Mobbin](https://mobbin.com/blog/streaks-are-about-losing-a-study-of-859-streak-designs)).

### 2.4 Evidencia académica transversal

- **Retroalimentación del barrio en reciclaje** [V]: en un experimento de campo con 605 hogares durante 17
  semanas, informar a cada hogar de su propio reciclaje o del de su barrio aumentó la frecuencia, la cantidad
  total y la cantidad por participación. **Ninguna intervención cambió la contaminación** (Schultz 1999,
  *Basic and Applied Social Psychology*) ([CBSM](https://cbsm.com/articles/32281-changing-behavior-with-normative-feedback-interventions-a-field-experiment-on-curbside-recycling)).
- **Comparación social persistente** [V]: los informes de energía de Opower siguieron funcionando dos años y,
  al suspenderse, el efecto decayó solo un 10-20 % al año (Allcott y Rogers, *AER* 2014)
  ([NBER w18492](https://www.nber.org/papers/w18492.pdf)). En Alemania el efecto fue bastante menor
  ([EfD](https://www.efdinitiative.org/sites/default/files/publications/ms_649_dp_18-16.pdf)): el contexto
  importa.
- **Progreso regalado (endowed progress)** [V, vía fuentes secundarias consistentes]: en un lavado de autos,
  una tarjeta de 10 sellos con 2 ya puestos se completó en el 34 % de los casos, frente al 19 % de una de 8 sin
  sellos, aunque ambas exigían 8 lavados (Nunes y Drèze 2006, *JCR*)
  ([Coglode](https://www.coglode.com/nuggets/endowed-progress-effect)).
- **Loterías frente a pagos fijos** [V]: la evidencia es mixta. Una lotería diaria de unos 1,40 dólares elevó
  la adherencia del 58 % al 81 % ([CMU / Volpp y Loewenstein](https://www.cmu.edu/dietrich/sds/docs/loewenstein/HomeBasedHealthMonitoring.pdf)).
  Pero en pasos diarios la lotería no superó al incentivo con encuadre de pérdida
  ([Penn Medicine](https://www.pennmedicine.org/news/news-releases/2016/february/to-encourage-physical-activity)).
- **Desplazamiento de la motivación (crowding out)** [V]: también mixto. Depende del tamaño del premio y de la
  motivación previa; un propósito ambiental explícito puede neutralizar el efecto negativo
  ([Springer Nature, resumen](https://communities.springernature.com/posts/extrinsic-rewards-and-crowding-out-of-prosocial-behaviour),
  [WIT Press](https://www.witpress.com/elibrary/wit-transactions-on-ecology-and-the-environment/231/37112)).
  [R] Con montos tan pequeños, el riesgo es que "S/0,30" suene a que tu esfuerzo vale poco. Conviene mostrar
  primero el impacto y la meta colectiva, y el dinero como acumulado.
- **Compromiso** [V]: en residencias universitarias, el reciclaje de papel llegó al 67 % con compromiso
  individual, al 54 % con incentivo y al 48 % con compromiso grupal, y el compromiso se mantuvo tras retirarlo
  (Wang y Katzev 1990) ([CBSM](https://cbsm.com/articles/31794-group-commitment-and-resource-conservation-two-field-experiments-on-promoting-recycling)).
- **La gamificación decae, pero no desaparece** [V]: un metaanálisis de 16 ensayos sobre actividad física
  halló un efecto pequeño a largo plazo, g = 0,15 (Mazeas et al., *JMIR* 2022)
  ([JMIR](https://jmir.org/2022/1/e26779/PDF)). Otro estudio longitudinal ve caer el efecto después de unas 4
  semanas (Rodrigues et al. 2022).
- **Rankings** [V]: los resultados son contradictorios. Hay estudios que recomiendan **rankings relativos** y
  no exhibir a los últimos ([HKU](https://hub.hku.hk/bitstream/10722/368255/1/content.pdf),
  [Emerald / Internet Research](https://www.emerald.com/insight/content/doi/10.1108/intr-12-2021-0897/full/html)).
- **CO₂ frente a árboles** [V]: en elecciones de viaje, el CO₂ en bruto movió más que las equivalencias en
  árboles (Mohanty et al., CHI 2023) ([arXiv](https://arxiv.org/pdf/2407.08897)). En energía no hubo
  diferencias significativas. [R] Conviene mostrar kilos y CO₂ como cifra principal y los árboles como apoyo.

---

## 3. Mecánicas priorizadas para Ruma

Escalas [R]: impacto esperado en retención y recojos (A/M/B) y coste de construcción en este repositorio
(B: días; M: 1-2 semanas; A: más).

| # | Mecánica | Evidencia | Impacto | Coste | Riesgo principal | Fase |
|---|---|---|---|---|---|---|
| 1 | Celebrar y notificar el **pesaje en acopio** (pendiente → disponible) | Hitos animados +1,7 % D7 [V-emp]; es el momento real de pago | A | B | Ninguno, si se dice la verdad | 0 |
| 2 | Meta semanal **baja** (1 recojo o 1 día) y racha semanal **reparable** | Barra baja +40 % de rachas de 7+ [V-emp]; protectores +0,38 % DAU [V-emp] | A | B | Partir recojos si la racha paga | 0-1 |
| 3 | **Meta colectiva** de cuadra y de Cartavio, con barra y retroalimentación del barrio | Schultz 1999 [V]; Opower persistente [V]; indicador municipal [V] | A | M | Desánimo si la meta es inalcanzable | 1 |
| 4 | Notificaciones **por evento** con tope y horario | Bandit de Duolingo [V]; fatiga [R] | A | B | Fatiga, culpa, urgencia falsa | 0 |
| 5 | Ranking **relativo** por semanas activas, sin exponer a los últimos | Rankings mixtos; recomendación de relativos [V] | M | B | Premiar volumen si se ordena por kg | 0 |
| 6 | **Progreso inicial** honesto (checklist de alta: dirección ✓, primera foto ✓, primer recojo ☐) | Endowed progress 34 % frente a 19 % [V] | M | B | Hinchar el progreso con pasos falsos | 0 |
| 7 | **Sorteo mensual**: 1 boleto por recojo verificado, con tope por hogar | Loterías mixtas pero útiles con montos chicos [V] | M | M | Legal (bases, datos personales); sensación de apuesta | 1 |
| 8 | **Referidos** que premian sobre todo al invitado y solo tras su primer pesaje | Premio al receptor > al emisor [V]; Duolingo +3 % [V-emp] | B-M | M | Cuentas falsas o el mismo hogar | 1 |
| 9 | **Retos temporales** mensuales de calidad (cartón plegado y seco, PET sin tapa ni líquido) | Eventos de Pokémon GO [V-emp]; novedad decae a ~4 semanas [V] | M | B (los retos ya existen) | Retos de volumen que incentiven residuos | 1 |
| 10 | **La Ruma** visual que crece (ver §4) | Mascota y widget de Finch [no verificado]; hitos +1,7 % [V-emp] | M | M | Rendimiento; premiar volumen | 1 |
| 11 | Impacto visible: kg y CO₂ propios y del barrio | CO₂ en bruto > árboles [V] | B-M | B | Cifras infladas (*greenwashing*) | 0 |
| 12 | Reconocimiento social: resumen mensual compartible y sticker físico "Hogar Ruma" en la puerta | Norma descriptiva visible [V, Schultz]; Year in Sport [R] | M | B-M | Privacidad de la dirección | 1-2 |
| 13 | Niveles con beneficios **no monetarios** (franja de recojo preferente, prioridad) | [R] | M | M | Complejidad | 2 |
| 14 | Ligas semanales por grupos de unos 20 hogares | Ligas +17 % [V-emp] | A con escala | A | Sin masa crítica en el piloto la liga queda vacía | 2 |
| 15 | Recompensas variables (cofres) | Contador de Gardenscapes neutro [V-emp] | B | M | Patrón oscuro de apuesta | No |
| 16 | Colección de marcas recicladas | [R] | B | M | **Incentiva comprar bebidas embotelladas** | No |

### 3.1 Rachas y protección de racha [R]

- Ruma ya cuenta *semanas* que cumplen la meta y no días. Es correcto para un hogar que junta material
  durante días. Mantenerlo.
- Bajar la meta por defecto a **1** y dejar que la persona la suba. Con 3 días por semana se empuja a escanear
  en exceso, y el escaneo no es la acción valiosa.
- Protección: **un comodín al mes automático** ("Esta semana te cubrimos; tu racha sigue en 6"), más uno
  ganable al completar un reto. Nunca venderlo.
- Revisar `streak_multiplier`: si multiplica puntos por una racha *diaria*, premia partir recojos. Opciones:
  quitarlo; aplicarlo solo por semanas cumplidas y con un mínimo de kilos por recojo; o convertirlo en boletos
  del sorteo en vez de puntos. Es una decisión de negocio que hay que tomar antes de anunciar rachas.

### 3.2 Metas colectivas por cuadra y municipalidad [R]

- Una barra única de Cartavio: "Diciembre: 1 tonelada de PET y cartón. Vamos 412 kg, 38 hogares".
- La cuadra como equipo: "Tu cuadra: 7 de 15 hogares reciclaron esta semana". Esa es la retroalimentación del
  barrio de Schultz.
- Pactar con la Municipalidad de Santiago de Cao un reconocimiento público al llegar a la meta, como un
  mural, una mención o una mejora en un parque. Ruma le entrega a cambio el indicador de viviendas
  participantes. Cuesta poco y no es dinero.

### 3.3 Sorteos [R]

- El boleto se gana con un **recojo pesado**, no con fotos ni con aperturas. Tope de unos 4 boletos por hogar
  al mes para que no gane quien más genera.
- Premio pequeño y frecuente (por ejemplo, S/50 por Yape o una canasta al mes), financiado con margen o con un
  patrocinador local.
- **Riesgo legal**: no se pudo confirmar si la autorización de promociones comerciales ante ONAGI sigue
  vigente. Hay fuentes que la dan por derogada en 2016
  ([IUS360](https://ius360.com/los-sorteos-y-la-publicidad-enganosa-christian-alonso-guzman-arias/)).
  Sí aplican las bases claras ante Indecopi y la protección de datos de la ANPD
  ([El Peruano](https://elperuano.pe/noticia/296166-tus-datos-personales-tambien-estan-protegidos-en-sorteos-premios-y-promociones-comerciales-senala-la-anpd)).
  **Validar con un abogado antes de lanzar.**

### 3.4 Ranking vecinal [R]

- Mostrar la posición propia más dos por encima y dos por debajo, nunca a los últimos de la lista.
- Ordenar por **semanas activas del mes**, con los kilos como desempate. Así compite la constancia y no la
  cantidad de residuos.
- Revisar `ranking_rewards` si paga puntos por puesto según puntos: es un premio al volumen.

### 3.5 Referidos [R]

- Pagar al **invitado** un bono en su primer recojo pesado y al invitante cuando el invitado completa su
  segundo recojo.
- Uno por dirección y por dispositivo, y la dirección debe ser distinta de la del invitante.
- No esperar un gran crecimiento: en Duolingo solo dio +3 % de usuarios nuevos. En un pueblo como Cartavio,
  el boca a boca y el rider pesan más.

### 3.6 Notificaciones y aversión a la pérdida [R]

Notificaciones por evento, en orden de valor:

1. Material pesado: puntos disponibles.
2. Rider en camino o llegando (ya existe).
3. "Tu cuadra va 7/15 esta semana" (domingo).
4. Recordatorio de racha **solo** si la meta semanal no se cumplió, el sábado.
5. Canje posible: "Ya tienes S/2,10 para Yape".

Reglas:

- Como máximo una notificación no transaccional al día y ninguna entre las 21:00 y las 08:00.
- No repetir la misma plantilla en una semana; esa es la idea de la penalización por recencia de Duolingo.
- Quitar "antes de que baje": crea una urgencia de mercado que, con céntimos, no beneficia al hogar.
- Aversión a la pérdida honesta: mostrar el saldo pendiente como "tuyo, en camino". Nada de "perderás tu
  racha" con cuenta regresiva ni de mascotas tristes. Mobbin documenta notificaciones a las 23:47 con 13
  minutos de cuenta atrás
  ([Mobbin](https://mobbin.com/blog/streaks-are-about-losing-a-study-of-859-streak-designs)): no copiar.

### 3.7 Riesgos

**Incentivos perversos**

- Generar más residuos (comprar más bebidas embotelladas) o traer material de fuera, como el fraude de
  Michigan.
- Contramedidas: premiar la constancia (semanas) y la calidad, no los kilos; poner un tope a los boletos y a
  las bonificaciones; **no** hacer colecciones de marcas.
- [R] Traer material de vecinos o de la calle sí es reciclaje real, pero distorsiona los rankings y puede
  competir con los recicladores de base formalizados. Conviene una regla visible: los rankings son por hogar
  y con tope.

**Fraude**

- Ruma ya tiene buena base: los puntos se liquidan al pesar en el acopio (`settle_delivery`) y el rider
  confirma en la puerta.
- Señales a vigilar: kg por recojo en caída (recojos partidos), diferencias sistemáticas entre lo declarado y
  lo pesado, referidos en la misma dirección o el mismo dispositivo, y un rider con tasas atípicas.

**Patrones oscuros**

- Evitar la culpa, la urgencia falsa, las cuentas atrás, los cofres aleatorios con dinero (se parecen a una
  apuesta) y los protectores de racha de pago.

**Fatiga**

- La novedad cae a las ~4-6 semanas (§2.4). Plan: retos mensuales que cambian, eventos con fecha y tope de
  notificaciones.
- Medir la tasa de desactivación de notificaciones como métrica de daño.

**Greenwashing**

- El CO₂ por kg debe tener una fuente citada en la app. Olio misma reconoce que sus supuestos son
  optimistas.

---

## 4. Elementos visuales y 3D sin matar el rendimiento

Restricciones del proyecto [Código]:

- Android con minSdk 24, que incluye Android 7, y teléfonos de gama baja.
- Compose sin dependencias de animación.
- PWA en React y Vite que hay que mantener ligera.
- En Android 7 Lottie desactiva la aceleración por hardware por defecto [V]
  ([changelog de lottie-android](https://github.com/XXiaoQing/lottie-android/blob/master/CHANGELOG.md)).

| Opción | Tecnología | Coste de rendimiento y tamaño | Veredicto |
|---|---|---|---|
| **A. 3D pre-renderizado en WebP** (6-8 etapas de "la ruma") | Modelar o generar en Blender y exportar PNG → WebP con transparencia | Ejecución casi nula: es una imagen. WebP con pérdida y alfa suele pesar unas 3 veces menos que PNG [V] ([Android Developers](https://developer.android.com/develop/ui/views/graphics/reduce-image-sizes)). Soportado desde API 17, cubre minSdk 24 [V]. En la PWA, `<img>` WebP | **Hacer primero** |
| **B. Celebraciones nativas** (confeti de 30-60 partículas, rebote del número, anillo que se llena) | Compose `Canvas` + `animate*AsState`; en la PWA, CSS o WAAPI con `transform` y `opacity`, respetando `prefers-reduced-motion` | Sin dependencias; solo se pinta durante unos 1,5 s | **Hacer primero** |
| **C. Lottie** | `lottie-compose`; en la web, `lottie-web` o dotLottie | Una librería más; hay que probar `RenderMode` en gama baja; las máscaras y los mattes son caros [V] ([commit RenderMode](https://skia.googlesource.com/external/github.com/airbnb/lottie-android/+/88bed53078a691b2f0b1eafb3d32071ab5cdb779)) | Fase 1-2, solo si un diseñador entrega animaciones que B no logra |
| **D. Rive** (mascota con máquina de estados reactiva) | Runtime de Rive para Android y web (WASM) | Wikimedia midió **+14,8 MB en el AAB**, unos 12,3 MB de librerías nativas en 4 ABIs, y estima +5-6 MB de descarga en ARM64 [V] ([Phabricator T433250](https://phabricator.wikimedia.org/T433250)). Los archivos `.riv` son pequeños; Duolingo usa Rive para sus personajes [V-emp] ([Rive](https://rive.app/blog/duolingo-s-ai-powered-video-call-brings-lily-to-life)) | Fase 2, si la mascota interactiva se valida con la opción A |
| **E. model-viewer o three.js en la PWA** ("ver mi ruma en 3D") | `<model-viewer>` con `poster` y carga diferida al tocar | La librería pesa unos cientos de KB (unos 285 KB gz según una fuente sin verificar) más el `.glb`; WebGL en gama baja. Pausa cuando no se ve [V] ([web.dev](https://web.dev/articles/model-viewer), [modelviewer.dev](https://modelviewer.dev/examples/loading/)) | Fase 2, opcional y detrás de un toque |
| **F. Filament o SceneView** en Android | Motor PBR de Google | Unos 3-5 MB según fuentes promocionales, sin verificar ([SceneView](https://sceneview.github.io/faq)); librerías nativas por ABI; más memoria GPU | No para el piloto |

### Concepto recomendado: "Tu ruma" [R]

- Un montón ordenado de fardos de PET y cartón que **crece por etapas**, por ejemplo: bolsa → costal →
  fardo pequeño → fardo → ruma → ruma con banderita del barrio.
- **Capas sólidas para lo pesado en el acopio y capas translúcidas o punteadas para lo pendiente.** Así se
  enseña la regla de la economía (los puntos llegan al pesar) sin texto.
- **La etapa avanza con semanas activas y recojos bien separados, no con kilos brutos.** Si avanza con kilos,
  premia generar residuos. Los kilos se muestran como número debajo.
- La misma pieza a escala barrio: "La ruma de Cartavio" en la meta colectiva, con una imagen por cada 25 %.
- Producción: un modelo en Blender (o generado y retocado) con 6-8 renders desde la misma cámara, exportados
  a WebP de 512 px (unos 20-60 KB cada uno, estimación sin medir). Se pueden empaquetar en el APK o servir
  desde la PWA con caché del service worker.
- El paso entre etapas se anima con un *crossfade* y un rebote en Compose o CSS (opción B). Parece 3D y cuesta
  como una imagen.

**Qué hacer primero**:

1. Las celebraciones nativas (B) en dos momentos: primer recojo completado y material pesado.
2. "Tu ruma" en WebP (A) en el inicio, junto al anillo semanal. No hace falta un segundo anillo: la ruma es
   la imagen y el anillo es la meta.
3. Medir el D7 y los recojos por hogar con y sin ellos antes de pensar en Rive o en WebGL.
4. Probar en un Android 7 o en un equipo de 2 GB de RAM real. Google recomienda probar en hardware físico de
   gama baja [V] ([Android Go](https://developer.android.com/guide/topics/androidgo/optimize)).

---

## 5. Propuesta en 3 fases

### Fase 0: con lo que ya existe (unos días)

1. **Medición**: crear en el esquema privado `ruma_analytics` vistas de solo lectura sobre `profiles`,
   `detections`, `pickups` y `redemptions` para obtener cohortes semanales. No hace falta un SDK de
   analítica. Hoy falta un evento de "abrió la app", así que el D1/D7/D30 de *aperturas* no puede medirse
   todavía. Mientras tanto se mide la actividad real (escaneo o recojo).
2. **Meta por defecto en 1** y la meta guardada en el servidor, no en `localStorage`, para poder medirla.
3. **Celebración del pesaje** cuando `points_pending` pasa a `points_balance`, y notificación de ese evento
   con el monto en S/ y en puntos.
4. **Notificaciones**: cambiar el `EcoTipsWorker` diario aleatorio por eventos (§3.6) y quitar la urgencia
   de precio.
5. **Ranking relativo** por semanas activas y revisión de `ranking_rewards` y `streak_multiplier` (§3.1, §3.4).
6. **Checklist de bienvenida** con progreso inicial honesto, porque la dirección y la primera foto ya están
   hechas.
7. **Impacto en el inicio**: kg y CO₂ propios del mes, con la fuente del factor visible al tocarlo.
8. **"Te faltan X pts para tu primer Yape"**: un canje temprano demuestra que el dinero es real. Revisar que
   el `min_points` de Yape/Plin sea alcanzable en 2-3 recojos.

### Fase 1: 1 a 2 semanas

1. Tabla mínima de eventos (`app_open`, `notification_opened`, `share`) escrita por RPC, con su vista de
   cohortes. Primero en STAGING, como manda el proyecto.
2. **Meta colectiva** de cuadra y de Cartavio, con barra en el inicio y resumen dominical por notificación.
3. **Comodín mensual** de racha, automático y gratuito.
4. **"Tu ruma"** en WebP con 6-8 etapas y capas de confirmado y pendiente. Las celebraciones con Compose y CSS.
5. **Retos mensuales de calidad** usando la tabla `challenges` existente: noviembre, cartón plegado y seco;
   diciembre, PET sin líquido.
6. **Sorteo mensual** con boletos por recojo pesado y tope por hogar, después de la revisión legal.
7. **Referidos** que pagan tras el pesaje, con controles de dirección y dispositivo.
8. Resumen mensual compartible (imagen generada) para WhatsApp.

### Fase 2: más adelante

- Ligas semanales por grupos de unos 20 hogares cuando haya masa crítica (más de 100 hogares activos por
  zona).
- Niveles con beneficios no monetarios: franja horaria preferente y prioridad en el despacho.
- Bonificación pagada por marcas (modelo Bower), solo sobre peso confirmado y con una marca verificable. Hoy
  la marca "nunca paga" por diseño, así que esto requiere rediseñar esa regla con cuidado.
- Mascota interactiva con Rive, o vista 3D opcional con model-viewer en la PWA, si la opción A demostró valor
  y el presupuesto del APK lo permite con un App Bundle por ABI.
- Sticker físico "Hogar Ruma" y reconocimiento municipal.
- Pruebas A/B sobre las mecánicas anteriores.

### Métricas a medir

Las definiciones son recomendaciones [R]. No se fijan metas numéricas porque no hay un referente peruano con
datos públicos (§2.2): las primeras 4 semanas del piloto son la línea base.

| Métrica | Definición propuesta | Por qué |
|---|---|---|
| **D1 / D7 / D30** | % de una cohorte de alta que vuelve a abrir la app *o* registra actividad exactamente el día N; reportar también la versión "N o más" | Comparabilidad con la industria. Requiere el evento de apertura de la fase 1 |
| **W1 / W4 (retención semanal)** | % de la cohorte con un recojo o escaneo válido en la semana 1 y en la 4 | **La métrica norte del hábito de Ruma**: reciclar es semanal, no diario |
| Activación | % de altas con **primer recojo pesado** en 14 días; tiempo hasta ese primer recojo | Sin primer pago no hay hábito |
| **Recojos por hogar activo al mes** | Recojos `completed` / hogares con al menos 1 recojo en el mes | Frecuencia real |
| Kg por recojo | Mediana mensual | Detectar recojos partidos (fraude o efecto del multiplicador) |
| **Tasa de canje** | % de hogares con saldo ≥ `min_points` que canjean en 30 días, y % de puntos emitidos que se canjean | Si nadie canjea, el dinero no se percibe real; si todos canjean al instante, mirar la retención después del canje |
| Tiempo hasta el primer canje | Días desde el alta | Prueba de confianza |
| Hogares activos por cuadra | % de viviendas de la cuadra activas en el mes | Meta colectiva y el indicador municipal |
| Calidad | % de recojos sin rechazo o contaminación en el acopio; diferencia entre lo declarado y lo pesado | Que el incentivo no degrade el material |
| Daño | % que desactiva notificaciones; desinstalaciones tras una notificación | Fatiga y patrones oscuros |
| Rider | Recojos por hora y kg por km | Que las mecánicas no hagan la ruta inviable |

---

## 6. Lagunas y límites de esta investigación

- No se hallaron métricas públicas de retención para Bower, Olio, Cataki, Recylink, Ecolana ni para ninguna
  app peruana. Las cifras de esas empresas son autodeclaradas.
- Casi todos los datos de Duolingo vienen de Duolingo (blog o exempleados). Son experimentos A/B reales, pero
  no auditados.
- No se encontró un ensayo aleatorizado de una app de reciclaje gamificada en hogares; el más cercano es un
  proyecto estudiantil no aleatorizado
  ([WPI, Carbon Crush](https://users.wpi.edu/~lparadise/STEMI.html)).
- No se encontraron datos de la composición de RAM del parque de teléfonos en Perú. Solo se sabe que la gama
  baja dominó las importaciones de 2023 según una fuente secundaria. Hay que probar en equipos reales de
  usuarios de Cartavio.
- El régimen legal de sorteos en Perú no quedó confirmado.
