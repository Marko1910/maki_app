# Reseñas de usuarios de apps parecidas: qué significa para el diseño de Ruma

Fecha: 2026-11-11. Complementa `docs/retencion-incentivos-research.md` (mecánicas de retención); aquí solo
hay lo que dicen los usuarios en reseñas públicas y qué cambia en las pantallas.

## Cómo leer este documento

- Las citas son textuales, de menos de 15 palabras, en su idioma original, con enlace a la página donde
  aparecen. Se leyeron el 2026-11-11 a través de una herramienta que resume la página: **conviene abrir el
  enlace antes de reutilizar una cita en un pitch**, porque las reseñas se mueven de página y pueden editarse.
- Las reseñas son una muestra autoseleccionada (escribe quien está muy molesto o muy contento). Sirven para
  ver **qué** duele, no **cuánto**. Los únicos porcentajes son de dos análisis de terceros sobre Fetch e
  Ibotta, y son proporciones aproximadas de reseñas negativas, no de usuarios.
- "Aplicado" = cambiado en este pase en Android y en la PWA. "Propuesto" = pendiente.

### Lo que no se encontró

- **Ecolana, Cataki, Ecotrash, TuPoinT y EcoVen**: ninguna reseña de tienda accesible. Las fichas de Google
  Play no devolvieron reseñas y la búsqueda solo trajo prensa y tesis. No hay ninguna cita de una app
  peruana de reciclaje.
- **Reddit**: ninguna búsqueda devolvió hilos sobre estas apps.
- **Recyclebank**: solo quejas sueltas en PissedConsumer vistas en el resumen del buscador (premios que
  desaparecen del catálogo, puntos que dejan de sumar); no se pudieron leer textualmente, así que no se citan.
- **Bower** (hoy Plovie): una sola reseña en Trustpilot. Es una reseña, no un patrón.
- **Méliuz** (Brasil): el retraso del cashback del Galaxy S25 está documentado en prensa
  ([Tecnoblog](https://tecnoblog.net/noticias/cade-o-cashback-samsung-e-meliuz-falam-sobre-galaxy-s25/)),
  que parafrasea a los usuarios y no los cita: el plazo prometido de 60 días pasó a 120-150.

## Fuentes revisadas

| App | Qué es | Fuente | Nota global al leerla |
|---|---|---|---|
| Fetch | Puntos por boletas (EE. UU.) | [Trustpilot](https://www.trustpilot.com/review/fetchrewards.com), [análisis de 178 reseñas de 1-3★](https://unstar.app/blog/does-fetch-actually-pay-points-not-awarded-app-reviews-2026) | 2,0/5 (2.110 reseñas) |
| Ibotta | Cashback (EE. UU.) | [análisis de 154 reseñas negativas de Google Play](https://unstar.app/blog/does-ibotta-actually-pay-maintenance-fee-cash-out-reviews-2026) | — |
| Trashie | Reciclaje de ropa con premios | [Trustpilot](https://www.trustpilot.com/review/trashie.io) | 3,8/5 (198) |
| Bower / Plovie | Reciclaje con premios (nórdicos) | [Trustpilot](https://www.trustpilot.com/review/getbower.com) | 1 reseña |
| Beruby | Cashback (Portugal/Brasil) | [Portal da Queixa](https://portaldaqueixa.com/brands/beruby/complaints/beruby-saldo-por-confirmar-que-nunca-se-confirma-135338625) | — |
| myTOMRA | Depósito-retorno (Australia) | [App Store AU](https://apps.apple.com/au/app/mytomra/id1303242160?see-all=reviews) | 4,8/5 (16 mil) |
| BottleDrop | Depósito-retorno (Oregón) | [App Store US](https://apps.apple.com/us/app/id1276690586?see-all=reviews) | 2,2/5 (118) |
| Olio | Compartir comida entre vecinos | [Trustpilot](https://www.trustpilot.com/review/olioex.com), [JustUseApp](https://justuseapp.com/en/app/1008237086/olio/reviews) | 4,1/5 (2.608) |
| Too Good To Go | Hábito local de recojo | [Trustpilot](https://www.trustpilot.com/review/toogoodtogo.co.uk) | 3,7/5 (118 mil) |

## Quejas frecuentes

| # | Patrón | Citas | Pantalla de Ruma | Cambio |
|---|---|---|---|---|
| Q1 | **Saldo "pendiente" que no se sabe cuándo ni si llega.** Es la queja más repetida en apps de premio. | «Já estou aguardando a quase um ano a confirmação do meu saldo» ([Beruby](https://portaldaqueixa.com/brands/beruby/complaints/beruby-saldo-por-confirmar-que-nunca-se-confirma-135338625)) · «they leave the receipt pending for weeks, so you can't dispute it» ([Ibotta](https://unstar.app/blog/does-ibotta-actually-pay-maintenance-fee-cash-out-reviews-2026)) · «I have been waiting for 90 days.» ([Fetch](https://www.trustpilot.com/review/fetchrewards.com)) | Wallet, Inicio, Programar recojo, ayuda de la cámara | **Aplicado.** La wallet explica los puntos «por confirmar» con sus tres pasos (recogido → pesado → disponible) y dice qué falta. Se corrigieron dos textos que prometían el abono «cuando el Eco-Rider confirme/recoja»: el abono es al pesar. Ruma no promete un plazo porque el sistema no lo garantiza. |
| Q2 | **Puntos que cambian o desaparecen sin explicación.** | «They deducted a large amount of points without any explanation» · «they erase your points without warning or explanation» ([Fetch](https://www.trustpilot.com/review/fetchrewards.com)) · «now my account says I only have $8» ([Trashie](https://www.trustpilot.com/review/trashie.io)) | Wallet (Android), Notificaciones (Android), Detalle del recojo | **Aplicado.** Android mostraba un saldo de ejemplo (4.250 pts) y avisos de ejemplo («gana 250 pts») mientras cargaba o si fallaba la red; ahora muestra un esqueleto, un error con reintento o un vacío real. La wallet de Android vuelve a leer el saldo en cada visita: antes quedaba el de la primera carga y un abono no se veía. La tarjeta «por confirmar» avisa que el monto puede ajustarse con el peso. |
| Q3 | **Umbral de canje inalcanzable o que se mueve.** 11 % de las reseñas negativas de Ibotta; ~5 % en Fetch. | «I've been stuck at 9$ for years.» ([Ibotta](https://unstar.app/blog/does-ibotta-actually-pay-maintenance-fee-cash-out-reviews-2026)) · «its about impossible. keep raising points as I collect them» ([Fetch](https://unstar.app/blog/does-fetch-actually-pay-points-not-awarded-app-reviews-2026)) | Wallet | **Aplicado:** cada opción dice «Te faltan N pts» en vez de «Saldo insuficiente». **Propuesto (negocio):** con S/0,25-0,30 por recojo, un mínimo de S/5 son unos 17 recojos; revisar `min_points` para que el primer canje llegue en 2-3 recojos. |
| Q4 | **El premio cambia de naturaleza** (monedas → sorteo, dinero → puntos devaluados). | «Now you can earn tickets to a lottery instead of earning "coins"» · «I have never seen any evidence of someone actually winning this lottery.» ([Bower](https://www.trustpilot.com/review/getbower.com)) · «I could only use my Trashie Cash for 50% of my order» ([Trashie](https://www.trustpilot.com/review/trashie.io)) | Wallet; el sorteo propuesto en el documento de retención | **Propuesto.** La regla 1.000 pts = S/1 debe verse fija en la wallet. Si llega el sorteo mensual, que sea un extra con ganadores publicados, nunca un reemplazo del saldo. |
| Q5 | **Saldo que caduca o cuenta que se cierra al cobrar.** | «Losing your entire point balance after just three months is frustrating.» ([Fetch](https://unstar.app/blog/does-fetch-actually-pay-points-not-awarded-app-reviews-2026)) · «I'm told by Olio I'm being banned for having a duplicate account.» ([Olio](https://www.trustpilot.com/review/olioex.com)) | Wallet, registro | **Propuesto.** Decidir y escribir en la wallet si los puntos caducan. Hoy la app no dice nada, y no se agregó ninguna frase porque no hay una regla que citar. |
| Q6 | **El pago va a un medio que el usuario no eligió, o el botón no funciona.** | «it gave me a voucher instead of crediting my grandchild's bank account» ([myTOMRA](https://apps.apple.com/au/app/mytomra/id1303242160?see-all=reviews)) · «The transfer button is entirely non-functioning.» ([BottleDrop](https://apps.apple.com/us/app/id1276690586?see-all=reviews)) | Wallet, canje | **Propuesto.** El canje confirma el monto, pero no muestra a qué número de Yape va ni el estado del canje después. `redemptions.status` ya existe; falta saber qué valores usa el operador para mostrarlos. |
| Q7 | **El recojo que se cancela o nadie responde.** | «had 1 order cancelled when arrived to pick it up» ([TGTG](https://www.trustpilot.com/review/toogoodtogo.co.uk)) · «you cancelled it, claiming there's no food?» · «was never answered for an offering» ([Olio](https://www.trustpilot.com/review/olioex.com), [JustUseApp](https://justuseapp.com/en/app/1008237086/olio/reviews)) | Seguimiento en espera | Ya resuelto en parte: la espera dice qué pasa ahora y qué sigue. **Propuesto:** si nadie toma el recojo dentro de la ventana, decirlo y ofrecer reprogramar con un toque. |
| Q8 | **Sesión que se cae y avisos que no llegan.** 10-12 % de las negativas de Fetch e Ibotta son bucles de inicio de sesión. | «Hate that it logs me out all the time.» ([myTOMRA](https://apps.apple.com/au/app/mytomra/id1303242160?see-all=reviews)) · «I have to reset my password every other time I use it.» ([BottleDrop](https://apps.apple.com/us/app/id1276690586?see-all=reviews)) · «Notification badges don't clear» ([Olio](https://justuseapp.com/en/app/1008237086/olio/reviews)) | Login, Notificaciones | Ruma ya guarda la sesión y la bienvenida tiene 3 pasos con «Saltar». **Aplicado en Android:** vacío real en Notificaciones y «Marcar leídas» con área táctil de 48 dp. |
| Q9 | **App lenta o que parece colgada.** | «OLIO takes up to 10 seconds to refresh at times» ([Olio](https://justuseapp.com/en/app/1008237086/olio/reviews)) · «It doesn't work when you need it to work.» ([BottleDrop](https://apps.apple.com/us/app/id1276690586?see-all=reviews)) | Inicio, Wallet, Historial | **Aplicado.** Esqueletos con la forma de la pantalla en vez de un spinner en el centro. |
| Q10 | **Sin ayuda a mano cuando algo sale mal.** | «Has no link to a help function or web page» ([myTOMRA](https://apps.apple.com/au/app/mytomra/id1303242160?see-all=reviews)) · «Customer service has become entirely nonexistent» ([Fetch](https://www.trustpilot.com/review/fetchrewards.com)) | Detalle del recojo, Wallet | **Propuesto.** Un «¿Algo no cuadra?» en el detalle del recojo que abra WhatsApp del operador del piloto con el código del recojo. |

## Elogios frecuentes

| # | Patrón | Citas | Pantalla de Ruma | Cambio |
|---|---|---|---|---|
| E1 | **«Simplemente funciona»: alta rápida y pocos pasos.** | «Quick to download, no fuss sign up with Paypal.» ([myTOMRA](https://apps.apple.com/au/app/mytomra/id1303242160?see-all=reviews)) · «It's quick and easy to list items» ([Olio](https://www.trustpilot.com/review/olioex.com)) · «The app is very easy to navigate and use.» ([TGTG](https://www.trustpilot.com/review/toogoodtogo.co.uk)) | Bienvenida, cámara, estados vacíos | **Aplicado:** cada lista vacía ofrece el paso que la llena (Registrar residuos, Pedir recojo, Ver mi wallet). No se tocó la bienvenida: ya es corta. |
| E2 | **Ver el saldo siempre a la vista.** Es lo único que se elogia de BottleDrop. | «It keeps track of how much you have» ([BottleDrop](https://apps.apple.com/us/app/id1276690586?see-all=reviews)) | Inicio, Wallet | **Aplicado.** El inicio distingue disponible y por confirmar, y dice de qué depende lo segundo. |
| E3 | **El momento en que el premio llega de verdad.** | «I filled 5 or 6 Take Back Bags and earned a $45 gift card.» ([Trashie](https://www.trustpilot.com/review/trashie.io)) · «it's always WORTH IT!» ([TGTG](https://www.trustpilot.com/review/toogoodtogo.co.uk)) | Inicio | **Aplicado.** Aviso animado «¡+N pts ya están en tu saldo!» al abrir la app después de un abono. |
| E4 | **Puntualidad y trato de la persona que recoge.** | «turned up approximately when they said they would» · «The food waste heros that I have been in contact with are amazing.» ([Olio](https://www.trustpilot.com/review/olioex.com)) | Seguimiento, detalle | Ya existe la tarjeta del Eco-Rider con nombre y llamada. **Propuesto:** mostrar si es el mismo rider de la vez anterior. |
| E5 | **Respuesta rápida de soporte.** | «I received an actual reply within 10 minutes or so!» ([Trashie](https://www.trustpilot.com/review/trashie.io)) | — | Ver Q10. |

## Los tres hallazgos más fuertes

1. **Lo que hace abandonar una app de premios no es que pague poco, es no saber si va a pagar.** El saldo
   pendiente sin fecha ni explicación (Q1) y los puntos que cambian sin aviso (Q2) llenan las reseñas de una
   estrella de Fetch, Ibotta y Beruby. Ruma tiene la misma estructura (pendiente → disponible) y la explicaba
   en el detalle del recojo, pero no en la wallet, y en dos pantallas decía algo falso.
2. **Un umbral de canje lejano se lee como «nunca voy a cobrar».** «Stuck at 9$ for years» es el riesgo
   concreto de Ruma con montos de céntimos por recojo (Q3). La interfaz ya dice cuánto falta; la decisión de
   fondo es el mínimo de canje.
3. **Lo que se elogia es aburrido: que funcione, que sea rápido y que el saldo esté a la vista** (E1, E2).
   Ninguna reseña positiva habla de insignias ni de rankings. myTOMRA tiene 4,8/5 con una app que hace una
   sola cosa.

## Mejoras aplicadas en este pase (Android y PWA)

| # | Mejora | Patrón | Android | PWA |
|---|---|---|---|---|
| 1 | «Por confirmar» explicado en la wallet con sus pasos y sin prometer plazo | Q1, E2 | `GeneratorWalletScreen.kt`, `GeneratorRoot.kt` | `Family.tsx` (Wallet) |
| 2 | Un solo mensaje verdadero sobre cuándo se acreditan los puntos | Q1 | `GeneratorPickupScreen.kt`, `GeneratorCameraScreen.kt`, `GeneratorHomeScreen.kt` | `Family.tsx` (Inicio). **Falta** `Pickup.tsx` línea 107, ver abajo |
| 3 | «Te faltan N pts» por opción de canje; «Ya puedes canjear» cuando alcanza | Q3 | `GeneratorWalletScreen.kt`, `GeneratorViewModel.kt` | `Family.tsx` (Wallet) |
| 4 | Aviso animado cuando llegan puntos al saldo | E3 | `GeneratorHomeScreen.kt`, `GeneratorViewModel.kt`, `RumaPrefs.kt` | `Family.tsx` (Inicio), `util.ts`, `styles.css` |
| 5 | Estados vacíos con el siguiente paso | E1 | `RumaScaffold.kt` (`RumaEmpty`), `GeneratorHistoryScreen.kt`, `GeneratorWalletScreen.kt` | `ui.tsx` (`Empty`), `Family.tsx` |
| 6 | Esqueletos de carga | Q9 | `RumaScaffold.kt` (`RumaSkeleton`), Inicio, Wallet, Historial | `ui.tsx` (`Skeleton`), `styles.css`, Inicio, Wallet, Historial |
| 7 | Nada de datos de ejemplo en el dispositivo; saldo releído en cada visita | Q2 | `GeneratorWalletScreen.kt`, `GeneratorNotificationsScreen.kt`, `GeneratorViewModel.kt` | No aplica: la PWA nunca los mostró |
| 8 | Áreas táctiles de 48 px | Q8 | Filtros del historial, «Marcar leídas» | Editar meta, días de la meta, Editar perfil, «Abrir» de Mercado |

**Pendiente de paridad.** `pwa/src/screens/family/Pickup.tsx` (línea 107) todavía dice «Los puntos se
acreditarán cuando el Eco-Rider confirme la recolección». Su gemelo de Android ya dice «Tus puntos se
acreditan cuando el acopio pese tu material, después del recojo». No se editó porque ese archivo estaba
reservado para otro trabajo en paralelo. Por la misma razón, el vacío de «Aún no tienes recojos» en
Seguimiento no tiene botón en ninguna de las dos apps.

**Límite del aviso de puntos.** Compara el saldo con el último que mostró ese teléfono. Celebra cualquier
aumento (pesaje o premio de ranking), no aparece en la primera apertura ni tras un canje, y no es una
notificación: con la app cerrada no avisa.

## Propuestas para después

1. Revisar `min_points` del canje por Yape para que el primer cobro llegue en 2-3 recojos (Q3). Es la que más
   pesa y no es de interfaz.
2. Mostrar el estado de cada canje y el número de Yape de destino (Q6). Requiere saber qué valores toma
   `redemptions.status`.
3. Escribir la regla de caducidad de puntos, o que no caducan, en la wallet (Q5).
4. «¿Algo no cuadra?» en el detalle del recojo, hacia el WhatsApp del operador (Q10).
5. Recojo que nadie toma dentro de su ventana: decirlo y reprogramar (Q7).
6. Android: `GeneratorRankingsScreen` y `GeneratorPickupScreen` todavía muestran datos de ejemplo mientras
   cargan (`state ?: Sample…`). El ranking los muestra para siempre si no hay periodo abierto; necesita un
   estado vacío propio.
7. Android: si la lectura de notificaciones falla, la pantalla queda cargando; falta un error con reintento.
8. Leer las reseñas de 1-3★ de Ecolana y Cataki directamente en Google Play desde un teléfono: es el único
   referente latinoamericano y no se pudo consultar.
