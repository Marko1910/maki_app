Investigaré referencias actuales, rendimiento y fuentes de modelos 3D. No leeré archivos locales; separaré los datos verificables de los rangos de diseño que recomiendo para Ruma.

La hipótesis principal es que el panel fijo consume espacio que el texto necesita: una página puede tener mucho vacío y, a la vez, una columna de lectura comprimida. También conviene separar dos decisiones: pedir imágenes 3D no obliga a cargar un motor 3D; para Ruma, los renders estáticos parecen encajar mejor con el presupuesto de rendimiento.

Encontré referencias útiles de climate tech que presentan materiales e infraestructura con calidad publicitaria, y casos de fintech latinoamericana fechados en 2025. Hay dos distinciones técnicas importantes: el LCP puede no reflejar la aparición de una escena WebGL, y una licencia CC0 del modelo no elimina los requisitos de acceso de la API que lo distribuye.

**1. Diagnóstico de densidad**

**El principal sospechoso es el reparto del espacio, no la cantidad total de espacio vacío.** Un panel fijo puede ocupar media pantalla mientras obliga a comprimir toda la argumentación en la otra mitad. Sin ver la página, esto es una hipótesis basada en tu descripción.

Ejemplo: viewport de **1440 px**, panel de texto del 50% y padding lateral de 80 px:

`1440 × 0,5 − 80 × 2 = 560 px útiles`

En 1280 px quedan **480 px**. Un titular de 72 px empieza a ocupar demasiadas líneas; debajo, párrafo, CTA y métricas forman una torre. El aire alrededor del teléfono no ayuda a separar esos elementos.

Estos serían mis objetivos para Ruma. **Son especificaciones propuestas, no un supuesto estándar universal de “landing 2026”.** Asumo `1rem = 16px`.

| Variable | Escritorio, 1280–1600 px | Móvil, 360–430 px |
|---|---|---|
| Contenedor general | **1120–1280 px**, aproximadamente 80–90% del viewport en ese intervalo | Viewport menos **40–48 px** |
| Columna de lectura | **520–680 px**, buscando **50–70 caracteres reales por línea** | **312–382 px**, normalmente 32–45 caracteres |
| Cuerpo | **18–20 px**, interlínea **1,5–1,65**: 27–33 px | **17–18 px**, interlínea **1,5–1,6** |
| H1 | **64–88 px**, interlínea **1,02–1,1**; 2–3 líneas | **38–48 px**, interlínea **1,08–1,15**; 3–4 líneas |
| H2 | **40–52 px**, interlínea **1,1–1,2** | **28–36 px**, interlínea **1,15–1,25** |
| Relación H1/cuerpo | Aproximadamente **3,5–4,5×** | **2,2–2,7×** |
| Separación titular → explicación | **24–32 px** | **20–24 px** |
| Explicación → CTA | **28–40 px** | **24–32 px** |
| Separación entre grupos relacionados | **32–48 px** | **24–32 px** |
| Separación entre secciones, de contenido a contenido | **112–160 px** | **64–96 px** |
| Separación entre columnas | **48–80 px** | Apilar; **32–48 px** entre bloques |
| Padding interior de tarjetas, si hacen falta | **28–40 px** | **20–24 px** |

La referencia de **50–75 caracteres por línea** tiene respaldo de investigación de legibilidad; Visa también recomienda una interlínea próxima a 1,5. No confundas `60ch` con exactamente 60 caracteres: depende de la fuente y del texto. [Baymard](https://baymard.com/research-articles/line-length-readability), [Visa Design System](https://design.visa.com/base-elements/typography/usage/).

**Causas medibles de la sensación de “apretado”:**

- **Columna estrecha para el tamaño del titular.** Si el H1 ocupa cinco líneas en escritorio, ensancha su caja o edita el mensaje antes de reducir toda la tipografía.
- **Demasiada información dentro de un viewport obligatorio.** Un hero de `100vh` con navegación, titular, descripción, dos botones, métricas y teléfono acaba comprimiendo algo. Usa altura según contenido.
- **Poco contraste entre escalas.** H1 de 36 px y cuerpo de 18 px pueden funcionar, pero dan poca jerarquía a una presentación ambiciosa. Cinco tamaños bastan; distribuirlos mal es el problema.
- **Espaciado uniforme.** Si todo está separado por 24 px, cuesta distinguir una nueva sección de otro párrafo. La separación entre secciones debería ser unas **3–4 veces** la separación interna de sus grupos.
- **Exceso de límites visibles.** Bordes, divisores, cajas y paneles fijos fragmentan la pantalla aunque estén vacíos.
- **Texto comprimido editorialmente.** Para el hero propondría **8–14 palabras de titular**, **25–40 de explicación**, un CTA dominante y una prueba breve. El resto pertenece a las secciones siguientes.

**Cambiaría el split fijo por un recorrido que recupere todo el ancho después del hero.** Las dos columnas pueden existir puntualmente; el teléfono no necesita reservar media pantalla durante toda la tesis de inversión.

Como verificación de accesibilidad, el diseño debe soportar que el usuario aumente interlínea y espaciados sin recortes. WCAG no obliga a usar esos valores como diseño inicial. [W3C: Text Spacing](https://www.w3.org/WAI/WCAG22/Understanding/text-spacing.html).

**2. Qué hace que se vea cara y no austera**

**Revocaría las prohibiciones absolutas de degradados, sombras y curvas.** Dos pesos y cinco tamaños pueden funcionar perfectamente. Lo que perjudica aquí es haber eliminado recursos expresivos antes de decidir qué necesita comunicar Ruma.

Mi dirección sería: **materiales reales, iluminación cuidada, tipografía amplia, composición editorial y evidencia operativa**. El aspecto costoso viene de la coherencia entre esas decisiones.

| Recurso | Cuándo suma | Aplicación concreta para Ruma |
|---|---|---|
| **Degradado** | Cuando representa iluminación, distancia o una transición deliberada | Un degradado amplio entre dos verdes cercanos, situado detrás del render y alineado con su luz |
| **Sombras suaves** | Cuando explican dónde está apoyado un objeto o qué elemento está elevado | Contacto definido y sombra amplia que pierde intensidad; en UI, punto de partida `0 24px 64px rgb(8 38 32 / 12%)` |
| **Glass** | En una capa funcional superpuesta a contenido visual | Como máximo una navegación flotante; fondo suficientemente opaco y blur de **12–20 px** como prueba inicial |
| **Grano** | Cuando aporta una textura editorial casi imperceptible | **1–2% de opacidad**, estático y preferentemente fuera de la imagen principal comprimida |
| **Viñeta** | Cuando concentra la mirada sobre el material | Oscurecimiento periférico suave, aproximadamente **5–10%**; evitar el borde negro cinematográfico |
| **Radios** | Cuando suavizan controles y superficies sin convertir todo en cápsulas | **8–12 px** en controles y **16–24 px** en contenedores visuales seleccionados |

Estos valores son puntos de partida de dirección de arte, no hallazgos estadísticos.

**El degradado delata una plantilla cuando no tiene una función reconocible:** varios halos arbitrarios, texto arcoíris, bordes luminosos y tarjetas idénticas con resplandor. Tampoco una sombra larga vuelve caro un objeto: si contradice la iluminación, parece pegado.

El vidrio sigue vigente en 2025–2026, pero incluso Apple lo reserva principalmente para controles y navegación y desaconseja invadir el contenido con él. Copiar el efecto en todas las tarjetas financieras sería una mala lectura de esa tendencia. [Apple: Materials](https://developer.apple.com/design/human-interface-guidelines/materials).

**Modo oscuro.** Funciona cuando hay un objeto, material o producto que gana presencia contra el fondo: metal, maquinaria, una interfaz luminosa. No proporciona confianza financiera automáticamente. Para Ruma usaría **hero verde profundo + cuerpo papel + cierre oscuro**, dejando las métricas y explicaciones sobre superficies claras. Las ventajas del modo oscuro dependen del contexto y de las preferencias, no del sector. [NN/g: Dark Mode](https://www.nngroup.com/articles/dark-mode-users-issues/).

**Acento al 10% frente a fondos completos.** El 10% es una heurística, no una cuota. Propondría **5–10% de menta en las secciones de lectura**, concentrada en acciones y datos destacados. Una sección completa menta puede funcionar como cambio de capítulo; repetirla constantemente elimina su poder de énfasis.

Calculando el contraste de vuestra paleta:

- Menta `#5FD4A8` sobre papel `#F2F5F3`: **1,67:1**. No sirve para texto informativo, tampoco grande.
- Verde profundo sobre menta: **8,77:1**.
- Verde profundo sobre papel: **14,62:1**.

Por tanto: **botón menta con texto verde profundo**, no texto blanco o papel. Los mínimos WCAG son 4,5:1 para texto normal y 3:1 para texto grande. [W3C: Contrast Minimum](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).

**Nueve referencias reales.** Son sitios vigentes consultados en octubre de 2026; algunos pertenecen ya a scaleups. Distingo los casos documentados en 2025: no atribuyo una fecha de rediseño sin evidencia.

| Referencia | Qué hace bien y qué tomaría |
|---|---|
| [Aira](https://www.airahome.com/en-gb) — climate tech | Convierte infraestructura doméstica en producto deseable mediante contexto residencial y dirección de imagen; especialmente relevante para Ruma. |
| [Climeworks](https://climeworks.com/) — climate tech | Da protagonismo a tecnología e infraestructura y separa sus líneas de negocio con claridad: ambición industrial comprensible. |
| [Charm Industrial](https://charmindustrial.com/) — climate tech | Une una idea visual reconocible con explicación del proceso y un registro de actividad: identidad y evidencia trabajan juntas. |
| [Watershed](https://watershed.com/) — climate tech | Presenta producto, resultados y datos con jerarquía; demuestra que una marca climática no necesita depender de hojas verdes. |
| [Ualá](https://www.uala.com.ar/) — fintech LatAm | Organiza un ecosistema amplio con un lenguaje consistente y distinción entre públicos; [rediseño documentado en 2025](https://www.paisanos.io/projects/uala-website). |
| [Félix](https://www.felixpago.com/) — fintech para LatAm | Hace visible el uso real del producto mediante una conversación y una demostración concreta; [caso de diseño de 2025](https://www.crayon.ws/work/felix). |
| [Clara](https://www.clara.com/) — fintech LatAm | Combina producto tangible, interfaz y resultados cuantificados para transmitir madurez empresarial. |
| [Revolut](https://www.revolut.com/) — producto móvil | Construye una presentación aspiracional alrededor del producto y mantiene una acción de descarga inequívoca. |
| [Oura](https://ouraring.com/) — hardware + app | Relaciona objeto, personas y capturas de la aplicación; la tecnología adquiere valor dentro de una experiencia cotidiana. |

Para la imagen 3D, la referencia más útil es el [trabajo de Found para Aira](https://www.found-studio.com/work/aira/): ambientes diseñados para el producto, iluminación consistente y detalles materiales.

**Hay además una incoherencia de conversión:** si la audiencia principal son inversionistas, descargar la app no debería ser su única salida. Mantendría ese CTA y añadiría **“Contactar al equipo” o “Ver presentación”**. Un render no sustituye hogares activos, recurrencia, kilos recuperados o economía por recojo; esas pruebas deben ocupar espacio protagonista cuando existan.

**3. 3D: la decisión técnica**

**Elegiría renders de Blender servidos como imágenes responsive AVIF/WebP.**

Los siguientes son **rangos estimados de transferencia para planificar**, no mediciones de archivos que aún no existen. Supongo una escena de producto sencilla, pocos objetos y optimización deliberada. Incluyen los recursos indicados; el peso de un `.glb` por sí solo no representa el coste de una experiencia 3D.

| Opción | Peso adicional esperado | LCP e INP | Complejidad y cuándo compensa |
|---|---|---|---|
| **a. Render estático AVIF/WebP** | **60–180 KB móvil**, **120–280 KB escritorio**, por imagen seleccionada | Descarga y decodificación previsibles; sin motor JS ni trabajo continuo. La imagen puede ser el LCP | Integración baja; trabajo artístico medio. Ideal cuando la perspectiva elegida comunica todo |
| **b. Three.js / React Three Fiber + GLB** | Aproximadamente **250–700 KB de JS comprimido**, según stack; **0,3–3 MB** de escena, texturas y entorno. Total orientativo **0,6–4 MB** | Compite por red y CPU; carga, preparación y render pueden retrasar interacciones. Añade coste GPU | Alta: materiales, dispositivos, fallback, carga y mantenimiento. Compensa si manipular el objeto explica algo necesario |
| **c. Spline embebido en vivo** | Reservaría **1–5 MB totales** para una escena sencilla; **5–15 MB o más** con texturas y efectos abundantes | Runtime, escena y recursos adicionales; riesgo de arranque tardío y consumo sostenido | Prototipo fácil, optimización menos controlable. Útil para interacción expresiva con presupuesto amplio |
| **d. Vídeo loop renderizado** | **0,5–2 MB** para unos 5–8 s a 720p moderadamente comprimidos, más póster; puede subir mucho | Un póster rápido protege la presentación inicial; descarga y decodificación siguen consumiendo recursos | Media. Compensa si el movimiento cuenta una transformación que una imagen no explica |

Los rangos de bibliotecas cambian según versión, importaciones y compresión. **No sería serio prometer “Three añade 300 ms” sin una escena, dispositivo y perfil de red concretos.**

La documentación de R3F advierte del coste de WebGL y recomienda render bajo demanda cuando corresponde. Spline recomienda reducir geometría, texturas, luces y posprocesado: el embed no elimina ese trabajo. [R3F: Scaling Performance](https://r3f.docs.pmnd.rs/advanced/scaling-performance), [Spline: optimización](https://docs.spline.design/exporting-your-scene/how-to-optimize-your-scene).

**Dos trampas de medición:**

- **Un canvas WebGL no es un candidato LCP convencional.** Puedes conseguir un LCP del texto aparentemente bueno mientras la imagen principal sigue vacía. Hay que observar también cuándo aparece la escena.
- En vídeo, el póster o el primer fotograma pueden determinar el LCP. Un póster rápido no demuestra que el vídeo esté listo. [Definición de LCP](https://web.dev/articles/lcp).

**Presupuesto que propondría para Ruma:**

- Hero móvil: intentar **60–100 KB**, con revisión visual de transparencias y detalles.
- Recursos necesarios antes del LCP: **200–250 KB transferidos** como objetivo inicial.
- Página completa después del scroll: aproximadamente **450–650 KB**, si las nuevas imágenes sustituyen recursos y se cargan selectivamente.
- **LCP <2,5 s e INP <200 ms en percentil 75**, separados por móvil/escritorio. [Google: Core Web Vitals](https://developers.google.com/search/docs/appearance/core-web-vitals).

“4G” necesita concretarse. A **1,6 Mbps**, la capacidad ideal es aproximadamente **200 KB/s**: descargar 500 KB consume 2,5 segundos antes de sumar latencia y procesamiento. Tus **320 KB actuales no garantizan nada por sí solos**; importa cuánto se necesita para mostrar el hero y cómo se descubre.

En Astro usaría `<Picture>`/`<Image>` con variantes AVIF/WebP, `srcset`, `sizes`, dimensiones explícitas y prioridad alta únicamente para el hero. **No cargaría esa imagen con lazy loading.** Las imágenes inferiores sí pueden diferirse. [Astro: imágenes](https://docs.astro.build/en/guides/images/), [web.dev: optimizar LCP](https://web.dev/articles/optimize-lcp).

Si después queréis movimiento, añadiría un loop debajo del primer bloque, con póster y carga diferida real. `preload="none"` no basta para impedir la descarga de un vídeo con autoplay. [web.dev: carga diferida de vídeo](https://web.dev/articles/lazy-loading-video).

**Para este caso, Three/R3F y Spline en vivo añaden coste sin una necesidad funcional demostrada.** El cliente pidió imágenes 3D; un render estático satisface exactamente eso.

**4. 3D: dirección de arte**

Propondría una idea visual concreta: **material doméstico recuperado presentado con el cuidado de una materia prima valiosa**. El objeto debe comunicar transformación y orden.

| Objeto | Opinión para Ruma |
|---|---|
| **Botella PET** | Útil como elemento secundario. Sin etiqueta, ligeramente deformada, con espesor y reflejos creíbles. Sola resulta demasiado genérica |
| **Cartón** | Muy apropiado: pliegues, fibra y cantos aportan tactilidad. Mejor apilado o enfardado que una caja de comercio electrónico intacta |
| **Chatarra** | Útil en detalles o como material separado. Una montaña oxidada introduce una estética de abandono y puede dominar negativamente |
| **Cubo/ruma de material prensado** | **La mejor pieza protagonista.** Relaciona el nombre con volumen recuperado y valor material. Debe conservar irregularidades reconocibles |
| **Teléfono flotando** | Útil para demostrar que la app existe. Uno, con captura auténtica y perspectiva moderada; dispositivo Android o neutro |
| **Monedas** | **Las descartaría.** Sugieren cashback, token o dinero fácil; desplazan la tesis de reciclaje hacia una promesa financiera |
| **Camión** | Solo si explica la operación real. Como protagonista puede hacer que Ruma parezca una empresa de flota; evitar vehículos que sugieran activos inexistentes |

**No mezclaría PET, cartón y metal en un único fardo “bonito”.** Mostrar materiales segregados comunica una operación más creíble. Si Ruma no prensa materiales, la imagen puede representar el destino de la cadena, pero no debe sugerir que posee esa infraestructura.

**Estilos:**

- **PBR realista controlado: recomendado.** Materiales plausibles, pequeñas imperfecciones y luz de estudio. No necesita suciedad extrema ni hiperrealismo.
- **Claymorphism: descartado para el hero.** Redondear y plastificar todo elimina las diferencias entre materiales. En este contexto remite a kits de ilustración de principios de los 2020.
- **Low poly: útil para un diagrama secundario.** Como protagonista transmite más fácilmente juego o maqueta que operación industrial.
- **Iridiscencia: evitar como acabado general.** Un cartón cromado o una botella holográfica dejan de hablar de reciclaje y se acercan a una estética Web3.
- **Translucidez: sí, donde corresponda físicamente.** PET, vidrio o ciertas piezas plásticas. Hacer transparente cualquier objeto por tendencia debilita su significado.

La valoración de qué envejece mejor es mi criterio de dirección de arte, no una clasificación objetiva por año.

**Receta inicial de iluminación y composición:**

1. **Una pieza principal y 2–3 elementos secundarios.** No una nube de diez iconos flotantes.
2. Cámara equivalente a **70–100 mm**, vista de tres cuartos y perspectiva moderada.
3. Luz principal amplia, aproximadamente a **45°**, con tamaño de **2–3 veces el objeto** para sombras suaves.
4. Relleno **1,5–2 pasos más débil**; contraluz leve solo si hace falta separar PET o metal.
5. Luz principal neutra: conservar cartón marrón y aluminio gris. Reservar la menta para detalles.
6. Contacto visible con una superficie y sombras con una dirección común.
7. Componer para el espacio real del titular: reservar **35–45% del encuadre** si compartirán escena. Crear un encuadre móvil distinto.
8. Personalizar geometría, deformaciones, materiales y disposición. Comprar o descargar una malla es compatible con una imagen propia; conservar su presentación de catálogo es lo que la delata.

Complementaría el 3D con **una fotografía auténtica de recojo o clasificación en Trujillo**. La imagen construye deseo; la fotografía demuestra que la operación existe.

**5. Fuentes CC0 de modelos 3D**

**Empezaría por Poly Haven para materiales y modelos realistas; Poly Pizza para geometría sencilla que se pueda trabajar.** “Descarga gratuita” y “CC0” no son equivalentes.

| Fuente / modelo verificado | Uso concreto | Acceso y observación |
|---|---|---|
| [Poly Pizza — Water Bottle, Quaternius](https://poly.pizza/m/KpxDpidn1Z) | Base para botella de agua/plástico | **CC0**, FBX/glTF; low poly, requiere trabajar silueta y material para un hero cercano |
| [Poly Pizza — Soda, Quaternius](https://poly.pizza/m/IqXSdKUMnY) | Base para lata de bebida | **CC0**, FBX/glTF; añadir pestaña, rebordes y deformaciones si habrá primeros planos |
| [Poly Haven — Cardboard Box 01](https://polyhaven.com/a/cardboard_box_01) | Cartón con pliegues y desgaste | **CC0**, modelo y texturas; buena base para una composición realista |
| [Poly Haven — Metal Trash Can](https://polyhaven.com/a/metal_trash_can) | Contenedor metálico | **CC0**; comprobar que su tipología represente la operación local |
| [Poly Haven — Plastic Bottle Gallon](https://polyhaven.com/a/plastic_bottle_gallon) | Envase plástico con asa | **CC0**; no confundirlo visualmente con una botella PET de bebidas |
| [Poly Pizza — Debris Pile, Quaternius](https://poly.pizza/m/WrIiMMxyEP) | Base de composición de residuos | **CC0**; es escombro genérico low poly, no una pila de chatarra realista terminada |
| [Kenney — City Kit Industrial](https://kenney.nl/assets/city-kit-industrial) | Diagrama de infraestructura o cadena logística | **CC0**, descarga de paquete; apropiado para diagramas, no para el objeto protagonista realista |

**Poly Haven: API recomendable.** Su [documentación oficial](https://polyhaven.com/our-api) describe listado de assets, metadatos y enlaces de descarga:

- `GET https://api.polyhaven.com/assets`
- `GET https://api.polyhaven.com/info/cardboard_box_01`
- `GET https://api.polyhaven.com/files/cardboard_box_01`

Los endpoints predeterminados no requieren clave; solicitan un `User-Agent` identificable. Los assets son CC0, pero integrar su **API en vivo** en un producto exige crédito al servicio. Descargar los materiales para producir renders no implica que la landing deba consultar esa API.

**Poly Pizza: descarga por ficha y API con cuenta.** La sección de [configuración de API](https://poly.pizza/settings/api) requiere iniciar sesión. El catálogo mezcla CC0 y CC-BY: verifica la licencia del modelo concreto, incluso cuando sea del mismo autor que otro asset.

**Sketchfab: útil para buscar, con más fricción.** Buscaría exactamente:

- `plastic bottle`, `PET bottle`, `crushed plastic bottle`
- `cardboard box`, `cardboard bale`
- `aluminum can`, `crushed can`
- `wheelie bin`, `recycling bin`
- `scrap metal`, `metal offcuts`, `compressed metal bale`

Aplicaría los filtros **Downloadable + CC0**, y comprobaría la ficha antes de usarlo. Sketchfab documenta tanto el [filtro CC0](https://www.sketchfab.com/blogs/community/refine-downloadable-model-searches-with-new-license-filters/) como su [Download API](https://sketchfab.com/developers/download-api): la descarga requiere autenticación, y la API entrega enlaces temporales; no es un repositorio de URLs permanentes anónimas.

**No confirmé un modelo de chatarra realista CC0 suficientemente adecuado para recomendarlo como protagonista.** Para Ruma modelaría unas pocas chapas, perfiles y latas deformadas y construiría la composición en Blender. Para una ruma de cartón o PET, también priorizaría una composición propia: es la pieza con mayor potencial para convertirse en un elemento reconocible de la marca.

Los archivos originales pueden pesar decenas o cientos de MB —la ficha de Cardboard Box 01, por ejemplo, ofrece una configuración de unos 37 MB—. Eso es material de producción: **al navegador solo llega el render comprimido elegido para cada pantalla**.

--- tokens: 1139919
