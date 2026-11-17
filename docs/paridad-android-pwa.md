# Paridad visual Android ↔ PWA (2026-11-11)

El diseño de referencia es la PWA (`pwa/`, proyecto Stitch «Ruma PWA»). Comparación hecha leyendo
el código de cada gemela; no se tocó la PWA ni `supabase/`. Una línea por pantalla:
**estado antes** · diferencias encontradas · qué se cambió en Android.

## Piezas compartidas (`ui/components/`, `ui/theme/`)

El patrón que más separaba a Android de la PWA era la **lista agrupada**: la PWA pone las filas en
una sola tarjeta de vidrio separadas por una línea fina (`.glass.list`), y Android dibujaba una
tarjeta blanca con sombra por fila. Se agregó el vocabulario de la PWA una vez, en
`ui/components/RumaList.kt`:

- `RumaList` + `RumaRow` (`.glass.list` y sus filas; chevron solo si la fila abre algo, `meta` para la hora en la línea del título, `highlight` para «tú» / no leída).
- `GlassBadge` (`.badge` neutro de vidrio; con relleno sirve de número de parada o medalla).
- `FilterPill` (`.filter`), `SmallButton` (`.btn-small`), `RumaStepper` (`.stepper`), `GlassChip` (`.chip`), `HeroChip` (`.chip-hero`), `HeroRule` (`.hero-rule`), `MetricCard` (`Metric`), `ChoiceCard` (`.choice` con radio y espacio para un chip), `RumaBars` (`Bars`), `TextAction` (`.btn-text`), `InlineLink`, `RankHero` / `RankRow` (el `Leaderboard` de ambos roles) e `initialsOf`.
- `RumaScaffold.kt`: `DetailHeader` titula a 20 sp (el `TopBar` con flecha de la PWA usa `t-card`) y acepta `subtitle`; nuevo `ScreenHeader` para pestañas (26 sp, sin flecha); `BackButton` de vidrio.
- `RumaUi.kt`: `PrimaryButton` con el degradado del rol (`--button`), `SecondaryButton` de vidrio con borde del acento al 35 %, `StatusChip` en píldora, `RumaInputField` con etiqueta en overline, pozo blanco al 72 %, borde fino y `helper`, y el héroe con el brillo radial de la esquina (`--hero`).
- Tokens nuevos: `RumaColors.Hair`, `SurfaceField`, `HeroShine`; `RumaGradient.button(color)`.

Estos cambios globales también alcanzan, sin editar sus archivos, a las pantallas que lleva la
otra sesión (Inicio, Perfil, Seguimiento, Recojo, Cámara, Resultado, Chat): verán el título de
detalle a 20 sp, botones con degradado y chips en píldora.

## Recolector (Eco-Hero)

| Pantalla | Antes | Diferencias | Cambio |
|---|---|---|---|
| Mi Wallet | parcial | Opciones en tarjetas sueltas con botón ancho e insignias de color; historial con icono por fila; saldo y soles en líneas separadas | Héroe con puntos y «= S/x» en una línea, «Eco-Puntos · +N por confirmar», regla, barra y pista; opciones en una `RumaList` con `SmallButton` «Canjear» (lleno el sugerido) o «Te faltan / N pts»; historial en lista con el monto en color dinero. Se mantienen el diálogo de confirmación y el bloque «Por confirmar» |
| Mi Historial | distinto | Filtros blancos/verdes sólidos, una tarjeta por evento, medidas sueltas en dp | `FilterPill`, overline por día y una `RumaList` por día con badge, subtítulo, chip de estado (acento si está en curso) y puntos en color dinero; solo tokens |
| Puntos de acopio | parcial | Mapa 220 dp; precios en filas; tarjeta con dirección/icono; dos botones secundarios | Mapa 200 dp con radio de tarjeta; «Precios de hoy» como rejilla 2×N de celdas de vidrio (sin `FlowRow`); tarjeta con nombre + chip Abierto/Cerrado, dirección · distancia, materiales y «Cómo llegar» (primario) / «Llamar» (secundario) |
| Notificaciones | distinto | Tarjetas blancas con sombra e insignias de color, cabecera propia | `DetailHeader` con «Marcar leídas»; una `RumaList` con badge neutro, hora a la derecha del título y fila tintada si no está leída |
| Ranking | distinto | Sin héroe; una tarjeta por familia; muestra datos de ejemplo en el dispositivo mientras carga | Héroe «Tu posición» (#N, nombre, distancia al 1.º, puntos); todos en una `RumaList` con medalla, iniciales, chip «Tú» y racha; premios plegados tras una fila. Carga real o estado vacío (ajuste puntual en `loadRankings` cuando no hay periodo abierto) |
| Mercado Vivo (ambos roles) | parcial | Héroe con el precio como cifra + dos `MetricTile`; una tarjeta por material | `ScreenHeader`; héroe «Lo más buscado hoy» con chip Sube/Baja, nombre y precio a 26 sp; precios en `RumaList` con código, chip de demanda con punto de color y precio en color dinero; noticias con «Fuente · fecha» y «Abrir» |
| Editar perfil (ambos roles) | distinto | Héroe de color con la inicial; campos sueltos; correo en tarjeta aparte; botón al final de la lista | Iniciales en círculo de vidrio, «Tus datos» en una sola tarjeta con Nombre / Teléfono / Correo (solo lectura) y sus ayudas; «Guardar cambios» fijo abajo (`RumaBottomAction`) |
| Inicio, Perfil, Seguimiento, Recojo(s), Cámara, Resultado, Chat | — | — | No tocados: los edita otra sesión |

## Eco-Rider

| Pantalla | Antes | Diferencias | Cambio |
|---|---|---|---|
| Inicio (dashboard) | parcial | Héroe abría con «Ganado hoy»; botones «Salir ahora / Programar salida»; progreso como `MetricTile` + `NavRow` sueltos | Héroe «Tu turno» + interruptor, estado, regla y «Ganado hoy»; «Programar salida» / chip de salida + «Quitar» como texto; siguiente parada con overline, dirección y «distancia · hora»; «Tus recojos» y «Tu progreso» como listas agrupadas |
| Tablero | distinto | Flecha atrás en una pestaña; héroe-resumen que la PWA no tiene; pines verdes | `ScreenHeader`; mapa primero (solicitudes en gris, «Tú» en azul); tarjetas con chip de distancia, ventana · antigüedad, materiales; botón lleno solo en la más cercana |
| Ruta del día | parcial | Flecha atrás; héroe con el tiempo + `MetricTile`; una tarjeta por parada con raíl | `ScreenHeader`; mapa, héroe «Terminas ~h» con Distancia / Ganancia estimada bajo la regla; paradas en `RumaList` con número en círculo azul y «Navegar» pequeño; «Iniciar navegación» |
| Ganancias | distinto | Barra manual, tarjeta «hoy» aparte, gráfico propio, retiro con borde azul | Héroe «Ganado este mes» con hoy, regla, barra y meta; «Últimos 7 días» con `RumaBars` (cifra arriba, hoy a pleno); «Disponible para retiro» con botón; movimientos por día en lista |
| Estadísticas | distinto | Seis tiles con icono de color; barras grises; insignias azules | Héroe con Reciclado y CO₂; gráfico `RumaBars`; resto de cifras como `MetricCard`; insignias de vidrio con aro dorado (bloqueadas atenuadas) en una tarjeta, tres por fila |
| Historial | distinto | Filtros sólidos, tarjeta por recojo con icono de estado | `FilterPill`, overline por día, `RumaList` con badge, dirección, chip de estado + hora y ganancia en color dinero |
| Perfil | parcial | Flecha atrás en una pestaña; identidad centrada con icono de bici; filas sueltas; fila «Configuración» que solo decía «pronto» | `ScreenHeader`; héroe con iniciales, nombre y rol, regla y chips vehículo / código / ★; `MetricCard` ×3; «Tu cuenta» en una lista; «Cerrar sesión» como botón de vidrio rojo. Se quitó la fila «Configuración» (era solo un aviso) |
| Ranking | distinto | Podio en tarjetas con borde de medalla + lista aparte; premios en tarjetas | Mismo `RankHero` + `RankRow` que la familia; podio y resto en una sola lista; premios plegados |
| Notificaciones | distinto | Igual que la de la familia | Igual que la de la familia, con el azul del rider |
| Entregar en acopio | distinto | Héroe «Seleccionado» que la PWA no tiene; una tarjeta por recojo y por acopio con bordes de color; entregas anteriores en tarjetas | Recojos en una `RumaList` con casilla (badge azul con check / vacío, rol de checkbox), código, materiales y monto en color dinero; acopios como `ChoiceCard` de radio con chip Abierto/Cerrado; entregas anteriores en lista con chip de estado (verde solo si está liquidada). El resumen de lo seleccionado pasa a la barra fija de abajo, encima de «Salir hacia el acopio» (sigue en `RumaBottomAction`) |
| Navegación | parcial | Indicación en un héroe degradado con icono; píldoras de estado sobre el mapa; hoja de vidrio; ETA arriba a la derecha; voz y llamar como dos botones anchos | Indicación en tarjeta azul plana: «En N m» encima de la maniobra (región viva para lectores); «Centrar» blanco; hoja sólida con «Destino · parada · estado» + «Activar voz» como texto, nombre, dirección, referencia, avisos de GPS / ruta con «Reintentar ruta», métricas «Por calles» y «Llegada estimada» entre líneas finas; «Llamar» + «He llegado al punto» en una fila; «No puedo ir · liberar» en rojo. Se mantienen TTS, GPS, «Abrir en otra app» y los textos que usa `RiderNavigationDeviceTest` |
| Confirmar recolección | distinto | El héroe contaba unidades; `MetricTile` de puntos/ganancia/peso; un material por tarjeta con botones redondos | Tarjeta con familia, dirección y ventana; «Cuenta lo que recibes» en una `RumaList` con el código del material y un `RumaStepper` de ancho fijo (y «Faltan / Sobran N»); nota de que el conteo es lo que se registra; héroe «Ganarás» con peso total y puntos del recolector. Botón fijo abajo (`RumaBottomAction`) |
| Recojo completado | parcial | Sin el check; dos `MetricTile`; chip con las paradas restantes | Check en círculo de vidrio, título y familia centrados, héroe «Tus ganancias», «Resumen del recojo» como pares etiqueta/valor, tarjeta con las paradas que quedan y los mismos botones |

## Entrada

| Pantalla | Antes | Diferencias | Cambio |
|---|---|---|---|
| Onboarding | parcial | Página 1 centrada sin panel; pasos en tarjetas con insignias de color; aviso de privacidad en caja verde; puntos de paso cortos | Panel de vidrio con el logo y chip «+ 240 pts», título a la izquierda; pasos como número grande + texto + badge y ejemplo «10 botellas PET +100 pts»; privacidad en texto suave; puntos de 8 dp con el actual a 28 dp; «Saltar» en acento; último botón «Continuar» |
| Rol | distinto | Héroe verde y filas que navegaban al tocar, más «Centro de Acopio» (solo aviso) | Logo en placa, título centrado, «Tu rol» con dos `ChoiceCard` de radio y «Continuar»; «Ya tengo cuenta · Iniciar sesión». Se quitó la opción de acopio (era solo un aviso) |
| Login | parcial | «¿Olvidaste tu contraseña?» y «Continuar con Google» (avisos), error como bloque con reintento, spinner extra | Logo, «Entra y sigue reciclando», selector de rol en píldoras (se mantiene: rellena la cuenta demo), una tarjeta con los dos campos, error en rojo, botón y «¿No tienes cuenta? Regístrate» en una línea |
| Registro | parcial | Insignia de rol arriba a la derecha; botón dentro de la tarjeta | Solo flecha atrás; título; tarjeta con los campos (la ayuda de la dirección va como `helper`); botón y enlace fuera de la tarjeta |

## Pendiente

- `GeneratorRoot.kt` (no tocado): para mostrar en Mercado el atajo «Para ti: ¿qué junto esta semana?» de la familia, pasar `onAsk = { nav.navigate(GenRoutes.CHAT) }` a `MarketScreen` en la ruta del mercado del recolector. Sin eso el héroe simplemente no muestra esa fila.
- Sin verificación en dispositivo: solo compilación y pruebas JVM.

## Segunda pasada (2026-11-11): las cuatro pantallas del Eco-Rider que faltaban

Entregar en acopio, Navegación, Confirmar y Completado (tabla de arriba). `ConfirmMaterialUi` gana
`code` (una línea en `RiderViewModel.toConfirmUi`) para mostrar el código del material como la PWA.
`RumaRow` acepta `chevron = false` y `ChoiceCard` un `trailing`. `RiderRoot.kt` no se tocó.
