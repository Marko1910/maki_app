# Guion de Presentación — MAKI (3 min)

## Concurso de Soluciones Tecnológicas
**Equipo: DeepCoders** | **1 presentador** | **Duración: 3 minutos**

---

### SLIDE 1 — Portada
> *MAKI en grande + tagline + 2026*

**~15 seg.** Buenos días, jurado. Hoy les presento **MAKI**: una plataforma con inteligencia artificial que transforma el reciclaje doméstico desde el dispositivo que todos llevamos en el bolsillo: nuestro smartphone.

---

### SLIDE 2 — MAKI (¿Qué es?)
> *Texto izquierda, screenshot app derecha*

**~20 seg.** MAKI es una aplicación de dos caras. Del lado del hogar, los **Eco-Héroes** escanean sus residuos con la cámara, la IA identifica el material al instante y acumulan puntos canjeables por dinero vía Yape o Plin. Del otro lado, los **Eco-Riders** —recolectores urbanos en bicicleta— reciben rutas optimizadas para recoger los materiales. Cerramos el círculo: el hogar se beneficia, el recolector genera ingresos dignos y el planeta gana.

---

### SLIDE 3 — DeepCoders
> *Texto izquierda, foto del equipo derecha*

**~20 seg.** Somos **DeepCoders**, un equipo que combina visión por computadora, IA generativa y gamificación para resolver problemas ambientales reales. Lo que nos hace únicos es nuestro enfoque dual: empoderamos al ciudadano de a pie y también al recolector que mantiene viva la cadena del reciclaje. No es solo una app —es un ecosistema.

---

### SLIDE 4 — Introducción
> *Texto izquierda, gráfico de impacto derecha*

**~20 seg.** Contexto: en Perú se generan más de 8 millones de toneladas de residuos al año, pero solo el **1.9%** se recicla formalmente. El resto termina en rellenos o en el mar. ¿La causa? La gente no sabe clasificar, no tiene quién le recoja y no confía en el sistema. MAKI resuelve los tres problemas con una sola plataforma.

---

### SLIDE 5 — Contexto del Problema
> *3 bloques derecha, imagen ilustrativa izquierda*

**~25 seg.** Identificamos tres brechas críticas. **Educación**: el 70% no sabe clasificar bien —MAKI tiene un asistente de IA que resuelve dudas en tiempo real. **Logística**: la recolección selectiva es escasa —MAKI conecta hogares con recolectores independientes y les asigna rutas optimizadas. **Confianza**: no hay transparencia en el valor de los reciclables —MAKI muestra precios de mercado actualizados en vivo.

---

### SLIDE 6 — Solución
> *Dos columnas izquierda, screenshot app derecha*

**~40 seg.** Aquí está el corazón de MAKI. Un **Eco-Héroe** abre la app, pone sus residuos frente a la cámara y Gemini 2.5 Flash —nuestro modelo de visión— identifica el material: PET, aluminio, vidrio, cartón o pilas. No solo lo clasifica: evalúa la **calidad** del residuo y asigna puntos de forma automática y server-side, lo que significa que ni el usuario ni el código del teléfono pueden alterar los valores.

Además, implementamos detección de vida de **doble capa**: el giroscopio del teléfono mide el movimiento durante la captura —tiene que haber movimiento real porque estás rodeando el objeto— y la propia IA verifica que no sea una foto de una pantalla. Esto evita fraudes.

Del lado del **Eco-Rider**, la app muestra recogidas disponibles, traza la mejor ruta en OpenStreetMap y confirma la recolección. Todo transparente, todo trazable.

---

### SLIDE 7 — Beneficios e Impacto
> *4 bloques izquierda, infografía derecha*

**~25 seg.** El impacto de MAKI es cuádruple. **Ambiental**: cada punto representa un material que no llega al relleno. Medimos CO₂ evitado y agua ahorrada. **Social**: gamificamos el reciclaje con rachas, rankings vecinales y retos. **Económico**: los Eco-Riders generan ingresos justos y los hogares canjean sus puntos por dinero. **Tecnológico**: todo funciona con APIs que no requieren claves en el teléfono —las Edge Functions de Supabase protegen nuestra IA.

---

### SLIDE 8 — Cierre
> *Texto derecha, imagen inspiradora izquierda*

**~20 seg.** MAKI demuestra que reciclar puede ser eficiente, transparente y gratificante. Convertimos un problema ambiental en una oportunidad económica para todos. Esta no es solo una app: es el primer paso hacia una economía circular impulsada por inteligencia artificial. Los invitamos a sumarse al reciclaje inteligente.

---

### SLIDE 9 — Gracias
> *Gracias + contacto*

**~10 seg.** Muchas gracias. Estoy listo para sus preguntas.

---

## Tips clave

| Aspecto | Recomendación |
|---|---|
**Duración** | 3 minutos exactos. Ensaya con cronómetro |
**Ritmo** | Slide 6 es la más técnica — baja la velocidad ahí |
**Lenguaje corporal** | Manos visibles, camina si hay espacio, no te quedes detrás de un atril |
**Mirada** | Al jurado, no a la pantalla ni al piso |
**Voz** | Sube el volumen en las palabras clave: "doble capa", "server-side", "1.9%", "Gemini 2.5 Flash" |
**Cierre** | Los últimos 10 segundos son los que más pesan — míralos a los ojos y habla pausado |

---

*DeepCoders — MAKI: Inteligencia artificial para un reciclaje inteligente*
