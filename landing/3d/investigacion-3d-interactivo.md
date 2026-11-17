**Elijo B: turntable de Cycles.** Para este fardo, la iluminación, el cartón y la sombra ya resuelven el aspecto visual. Un GLB puede pesar menos, pero reproducir ese resultado exige rehacer materiales e iluminación; `model-viewer` tampoco conserva automáticamente el aspecto de Cycles. La GTX 1650 no es el problema principal: lo son los móviles, las texturas y el coste de mantener WebGL activo.

### 1. Assets y carga

| Parámetro | Decisión |
|---|---|
| Vistas | **144 frames, cada 2,5°**, sin duplicar 360° |
| Escritorio | **640 × 560 px**, presentación máxima recomendada: 480 px |
| Móvil | **512 × 448 px** |
| Formato | WebP individual, RGB con pérdida, alfa conservado; calidad inicial 80 |
| Presupuesto escritorio | **25–40 KB/frame → 3,6–5,8 MB** |
| Presupuesto móvil | **15–25 KB/frame → 2,2–3,6 MB** |
| Primer paint | Tu WebP original de **288 KB**, directamente en HTML |
| Precarga | Después de decodificar el póster, en tiempo ocioso, **2 descargas simultáneas** |
| Memoria | Blobs comprimidos + caché de **9 bitmaps**, nunca 144 imágenes decodificadas |
| Reduced motion / ahorro de datos | Póster; cargar secuencia cuando el usuario interactúe. Sin autogiro ni inercia con reduced motion |

Los pesos son **presupuestos estimados**, no mediciones del fardo. Exporta primero 12 ángulos repartidos y mide. Mantén cámara, exposición, encuadre y luces fijos; gira el objeto sobre su centro y vuelve a calcular su sombra. Frame 0 debe coincidir con el póster.

**144 vistas es mi compromiso para arrastre convincente; no equivale a rotación continua.** A 30°/s aparecen 12 vistas distintas por segundo. Un `requestAnimationFrame` a 60 Hz no inventa las intermedias. No mezclaría frames mediante crossfade: duplica flejes y contornos.

144 frames de 640 × 560 decodificados ocupan **196,9 MiB**, aunque descarguen 5 MB. Nueve ocupan **12,3 MiB**, más póster, canvas y buffers del navegador. El código libera los bitmaps expulsados con [`ImageBitmap.close()`](https://developer.mozilla.org/en-US/docs/Web/API/ImageBitmap/close).

### 2. Visor y física

Valores: una anchura de arrastre = 360°; media móvil de **80 ms**; velocidad máxima **720°/s**; decaimiento **0,92 por cada 16,667 ms**, semivida **138,5 ms**. Autogiro **30°/s**, máximo **4 segundos**; cualquier interacción lo cancela definitivamente.

`touch-action: pan-y pinch-zoom` conserva scroll vertical y zoom. El navegador puede emitir `pointercancel` al asumir el gesto; hay que tratarlo como cancelación, sin inercia. [Referencia de Pointer Events y `touch-action`](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/touch-action).

Sustituye el hash del póster y rellena cada array con sus **144 URLs reales**, ordenadas de 0° a 357,5°. Los hashes no se pueden construir a partir del índice.

```html
<style>
.turntable {
  position: relative;
  width: min(100%, 480px);
  aspect-ratio: 8 / 7;
  touch-action: pan-y pinch-zoom;
  user-select: none;
  cursor: grab;
}
.turntable.dragging { cursor: grabbing; }
.turntable:focus-visible {
  outline: 2px solid #5FD4A8;
  outline-offset: 6px;
}
.turntable > img,
.turntable > canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
}
.turntable > [hidden] { display: none !important; }
.viewer-hint { color: #F2F5F3; font-size: 13px; }
</style>

<div id="fardo" class="turntable" tabindex="0"
     role="slider" aria-label="Orientación del fardo de cartón"
     aria-valuemin="0" aria-valuemax="357.5" aria-valuenow="0"
     aria-valuetext="0 grados" aria-describedby="fardo-hint">
  <img src="/_blob/HASH_POSTER"
       width="1600" height="1400"
       alt="" draggable="false"
       loading="eager" fetchpriority="high" decoding="async">
  <canvas hidden aria-hidden="true"></canvas>
</div>
<p id="fardo-hint" class="viewer-hint" role="status">
  Arrastra horizontalmente o usa ← y →.
</p>

<script type="application/json" id="fardo-frames">
{"small": [], "large": []}
</script>

<script>
function mountTurntable(el, urls, width, height) {
  const N = 144, STEP = 360 / N;
  if (urls.length !== N) throw new Error("Se requieren 144 URLs.");

  const poster = el.querySelector("img");
  const canvas = el.querySelector("canvas");
  const hint = document.getElementById("fardo-hint");
  const ctx = canvas.getContext("2d");
  const ac = new AbortController();
  const rm = matchMedia("(prefers-reduced-motion: reduce)");
  const saveData = navigator.connection?.saveData === true;
  const cache = new Map();
  const blobs = new Array(N);
  const lambda = -Math.log(0.92) / (1000 / 60);
  const mod = (x, n) => ((x % n) + n) % n;
  const on = (target, event, fn) =>
    target.addEventListener(event, fn, { signal: ac.signal });

  canvas.width = width;
  canvas.height = height;

  let dead = false, started = false, ready = false, warm = false;
  let visible = false, busy = false, drawn = -1, wanted = 0;
  let angle = 0, velocity = 0, raf = 0, previous = 0;
  let auto = !rm.matches && !saveData, autoTime = 0;
  let pointer = null, axis = "", x0 = 0, y0 = 0, lastX = 0;
  let gain = 1, samples = [], idle = null, bootRaf = 0;

  function stopAuto() { auto = false; }

  function fail() {
    destroy();
    hint.textContent = "Vista estática disponible.";
  }

  // Un solo decodificador pendiente. Si cambia el destino, salta al último.
  async function paint() {
    wanted = mod(Math.round(angle / STEP), N);
    if (dead || !ready || busy) return;
    busy = true;

    try {
      while (!dead && wanted !== drawn) {
        const i = wanted;
        let bitmap = cache.get(i);

        if (!bitmap) {
          bitmap = await createImageBitmap(blobs[i]);
          if (dead) { bitmap.close(); return; }
        }

        cache.delete(i);
        cache.set(i, bitmap);
        while (cache.size > 9) {
          const oldest = cache.keys().next().value;
          cache.get(oldest).close();
          cache.delete(oldest);
        }

        if (i !== wanted) continue;

        ctx.clearRect(0, 0, width, height);
        ctx.drawImage(bitmap, 0, 0, width, height);
        drawn = i;
        canvas.hidden = false;
        poster.hidden = true;
        el.setAttribute("aria-valuenow", String(i * STEP));
        el.setAttribute("aria-valuetext", `${i * STEP} grados`);
      }
    } catch {
      if (!dead) fail();
    } finally {
      busy = false;
    }
  }

  async function start() {
    if (dead || started) return;
    started = true;
    el.setAttribute("aria-busy", "true");
    hint.textContent = "Cargando vistas…";

    let next = 0;
    async function worker() {
      while (!dead) {
        const i = next++;
        if (i >= N) return;
        const response = await fetch(urls[i], { signal: ac.signal });
        if (!response.ok) throw new Error("No se pudo cargar un frame.");
        const blob = await response.blob();
        if (dead) return;
        blobs[i] = blob;
      }
    }

    try {
      await Promise.all([worker(), worker()]);
      if (dead) return;
      ready = true;
      el.removeAttribute("aria-busy");
      hint.textContent = "Arrastra horizontalmente o usa ← y →.";
      paint();
      kick();
    } catch {
      if (!dead) fail();
    }
  }

  function running() {
    return !dead && ready && visible && !document.hidden &&
      pointer === null && !rm.matches &&
      (auto || Math.abs(velocity) > 0.003);
  }

  function kick() {
    if (!raf && running()) raf = requestAnimationFrame(tick);
  }

  function tick(now) {
    raf = 0;
    if (!running()) { previous = 0; return; }

    const dt = previous ? Math.min(now - previous, 50) : 0;
    previous = now;

    if (auto) {
      const advance = Math.min(dt, 4000 - autoTime);
      angle += 0.030 * advance;
      autoTime += advance;
      if (autoTime >= 4000) auto = false;
    } else {
      // Integración exponencial: igual física a 30, 60 o 120 Hz.
      const decay = Math.exp(-lambda * dt);
      angle += velocity * (1 - decay) / lambda;
      velocity *= decay;
      if (Math.abs(velocity) <= 0.003) velocity = 0;
    }

    paint();
    if (running()) kick();
    else previous = 0;
  }

  function suspend() {
    cancelAnimationFrame(raf);
    raf = 0;
    previous = 0;
  }

  function sample(now) {
    samples.push({ t: now, a: angle });
    samples = samples.filter(s => now - s.t <= 80);
    const first = samples[0];
    velocity = samples.length < 2 ? 0 :
      Math.max(-0.72, Math.min(0.72,
        (angle - first.a) / Math.max(1, now - first.t)));
  }

  function finish(e, cancelled = false) {
    if (pointer === null || e.pointerId !== pointer) return;
    const id = pointer;

    if (!cancelled && axis === "x") sample(performance.now());
    else velocity = 0;
    if (rm.matches) velocity = 0;

    pointer = null; // Antes de release: lostpointercapture será inocuo.
    axis = "";
    el.classList.remove("dragging");
    if (el.hasPointerCapture(id)) el.releasePointerCapture(id);
    previous = 0;
    kick();
  }

  on(el, "pointerdown", e => {
    if (!e.isPrimary || e.button !== 0 || pointer !== null) return;
    stopAuto();
    start();
    suspend();
    velocity = 0;
    pointer = e.pointerId;
    x0 = lastX = e.clientX;
    y0 = e.clientY;
    axis = "";
    gain = 360 / Math.max(1, el.getBoundingClientRect().width);
    samples = [{ t: performance.now(), a: angle }];
    el.setPointerCapture(pointer);
    el.classList.add("dragging");
  });

  on(el, "pointermove", e => {
    if (e.pointerId !== pointer) return;
    const dx = e.clientX - x0, dy = e.clientY - y0;

    if (!axis) {
      if (Math.hypot(dx, dy) < 6) return;
      axis = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
    }

    if (axis === "x") {
      angle += (e.clientX - lastX) * gain;
      sample(performance.now());
      paint();
    }
    lastX = e.clientX;
  });

  on(el, "pointerup", e => finish(e));
  on(el, "pointercancel", e => finish(e, true));
  on(el, "lostpointercapture", e => finish(e, true));

  on(el, "keydown", e => {
    const directions = { ArrowLeft: -1, ArrowRight: 1 };
    if (!(e.key in directions)) return;
    e.preventDefault();
    stopAuto();
    start();
    velocity = 0;
    angle += directions[e.key] * STEP * (e.shiftKey ? 4 : 1);
    paint();
  });

  function cancelGesture() {
    if (pointer !== null) finish({ pointerId: pointer }, true);
    velocity = 0;
    suspend();
  }

  on(window, "blur", () => {
    stopAuto();
    cancelGesture();
  });

  on(document, "visibilitychange", () => {
    if (document.hidden) cancelGesture();
    else kick();
  });

  on(rm, "change", () => {
    if (rm.matches) {
      stopAuto();
      velocity = 0;
      suspend();
    }
  });

  function maybePreload() {
    if (warm && visible && !rm.matches && !saveData) start();
  }

  const observer = new IntersectionObserver(([entry]) => {
    visible = entry.isIntersecting;
    if (visible) { maybePreload(); kick(); }
    else cancelGesture();
  });
  observer.observe(el);

  // El póster tiene prioridad. Sin petición de frames desde el parser.
  poster.decode().catch(() => {}).then(() => {
    if (dead) return;
    bootRaf = requestAnimationFrame(() => {
      const arm = () => {
        if (dead) return;
        warm = true;
        maybePreload();
      };
      idle = "requestIdleCallback" in window
        ? requestIdleCallback(arm, { timeout: 1200 })
        : setTimeout(arm, 200);
    });
  });

  function destroy() {
    if (dead) return;
    dead = true;
    ac.abort(); // Fetches y todos los listeners.
    observer.disconnect();
    suspend();
    cancelAnimationFrame(bootRaf);
    if (idle !== null) {
      if ("requestIdleCallback" in window) cancelIdleCallback(idle);
      else clearTimeout(idle);
    }
    if (pointer !== null && el.hasPointerCapture(pointer))
      el.releasePointerCapture(pointer);
    pointer = null;
    cache.forEach(bitmap => bitmap.close());
    cache.clear();
    blobs.length = 0;
    canvas.hidden = true;
    canvas.width = canvas.height = 1;
    poster.hidden = false;
    el.classList.remove("dragging");
    el.removeAttribute("aria-busy");
  }

  return destroy;
}

// También permite volver a montar el visor sin acumular listeners.
window.destroyTurntable?.();
{
  const assets = JSON.parse(
    document.getElementById("fardo-frames").textContent
  );
  const small = matchMedia("(max-width: 600px)").matches ||
    navigator.connection?.saveData === true;

  window.destroyTurntable = mountTurntable(
    document.getElementById("fardo"),
    small ? assets.small : assets.large,
    small ? 512 : 640,
    small ? 448 : 560
  );
}
</script>
```

El código mantiene el póster hasta completar la descarga de la secuencia y decodificar la vista elegida. Si falla una descarga, conserva la imagen estática. No necesitas CDN, import maps ni dependencias.

### 3. Sistema de scroll

**IntersectionObserver, entrada una sola vez.** A **31/10/2026**, las animaciones de scroll están disponibles en Chrome/Edge 115+, Safari 26+ y Firefox 159+, publicado el 27 de octubre. Siguen existiendo navegadores instalados anteriores. Para este sistema no usaría polyfill: necesitas disparar entradas con duración temporal, no hacer que la opacidad dependa continuamente de la posición del scroll. [Chrome](https://developer.chrome.com/docs/css-ui/scroll-driven-animations), [WebKit](https://webkit.org/blog/17333/webkit-features-in-safari-26-0/), [Firefox 159](https://developer.mozilla.org/en-US/docs/Mozilla/Firefox/Releases/159).

| Elemento | Duración | Desplazamiento | Retardo |
|---|---:|---:|---:|
| Título de bloque | 420 ms | 12 px verticales | 0 ms |
| Texto breve asociado | 360 ms | 8 px | 60 ms |
| Imagen/captura | 560 ms | 16 px | 120 ms |
| Tarjetas hermanas | 420 ms | 12 px | 0 / 60 / 120 ms; máximo 180 ms |

Curva única: **`cubic-bezier(.22, 1, .36, 1)`**. En móvil: máximo **12 px**. Para esta landing, **más de 24 px** empieza a sentirse como una entrada teatral.

Anima bloques de contenido, no secciones completas de 2,5 pantallas. No animes navegación, CTA principal, primer viewport, párrafos largos, cifras mediante contadores, ni cada palabra. No anides elementos con reveal.

**Veinte pantallas de landing también requieren recorte editorial.** Añadir movimiento no corrige una longitud innecesaria.

CSS en `<head>`; script al final del documento:

```html
<style>
[data-reveal] {
  --duration: 420ms;
  --distance: 12px;
  --delay: 0ms;
}
[data-reveal="text"] {
  --duration: 360ms;
  --distance: 8px;
}
[data-reveal="image"] {
  --duration: 560ms;
  --distance: 16px;
}

.reveal-wait {
  opacity: 0;
  transform: translateY(var(--distance));
}
.reveal-in {
  animation: ruma-enter var(--duration)
    cubic-bezier(.22, 1, .36, 1) var(--delay) both;
}
@keyframes ruma-enter {
  from { opacity: 0; transform: translateY(var(--distance)); }
  to   { opacity: 1; transform: none; }
}
@media (max-width: 600px) {
  [data-reveal="image"] { --distance: 12px; }
}
@media (prefers-reduced-motion: reduce) {
  .reveal-wait, .reveal-in {
    opacity: 1;
    transform: none;
    animation: none;
  }
}
</style>

<!-- Un bloque local; repetir dentro de las secciones. -->
<div>
  <h2 data-reveal>Reciclar debe ser sencillo.</h2>
  <p data-reveal="text" style="--delay:60ms">
    Texto breve de apoyo.
  </p>
  <figure data-reveal="image" style="--delay:120ms">
    <!-- Usar las dimensiones reales para reservar espacio. -->
    <img src="/_blob/HASH_CAPTURA"
         width="1080" height="2400"
         loading="lazy" decoding="async"
         alt="Pantalla de programación de recojo">
  </figure>
</div>

<script>
function mountReveals(root = document) {
  const elements = [...root.querySelectorAll("[data-reveal]")];
  const rm = matchMedia("(prefers-reduced-motion: reduce)");
  if (rm.matches || !("IntersectionObserver" in window))
    return () => {};

  const ac = new AbortController();
  const pending = new Set();

  function show(el, animate) {
    pending.delete(el);
    observer.unobserve(el);
    el.classList.remove("reveal-wait");
    if (animate) el.classList.add("reveal-in");
    else el.classList.remove("reveal-in");
  }

  const observer = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (entry.isIntersecting) show(entry.target, true);
    }
  }, {
    threshold: 0,
    rootMargin: "0px 0px -48px 0px"
  });

  // Lo visible cuando llega el JS permanece visible.
  // Solo se oculta contenido todavía fuera de pantalla.
  for (const el of elements) {
    if (el.getBoundingClientRect().top < innerHeight + 24) continue;
    pending.add(el);
    el.classList.add("reveal-wait");
    observer.observe(el);
  }

  root.addEventListener("animationend", e => {
    if (e.animationName === "ruma-enter")
      e.target.classList.remove("reveal-in");
  }, { signal: ac.signal });

  // Un enlace enfocado nunca debe quedar oculto por el reveal.
  root.addEventListener("focusin", e => {
    const el = e.target.closest?.("[data-reveal]");
    if (el) show(el, false);
  }, { signal: ac.signal });

  function destroy() {
    observer.disconnect();
    ac.abort();
    pending.clear();
    elements.forEach(el =>
      el.classList.remove("reveal-wait", "reveal-in"));
  }

  rm.addEventListener("change", e => {
    if (e.matches) destroy();
  }, { signal: ac.signal });

  return destroy;
}

window.destroyReveals?.();
window.destroyReveals = mountReveals();
</script>
```

Esto evita el flash de **visible → oculto → visible** cuando tarda el JS: no oculta contenido ya visible. Sin JS, todo se lee. Reserva tamaño con `width`/`height` o `aspect-ratio`; anima solamente `transform` y `opacity`. No añadas `will-change` permanente.

**El error que abarata la página:** obligar al lector a esperar repetidamente. Entradas de 800–1200 ms, cascadas de diez elementos, desplazamientos grandes y contenido que vuelve a desaparecer al subir.

### 4. Fondo, capturas y contrastes

Usaría **seis bandas continuas para ocho secciones**: uniría visualmente 2–3 y 5–6. Así hay ritmo sin convertir cada sección en una franja independiente.

Luminancia relativa sRGB lineal, escala 0–1:

| Sección | Banda | Fondo | Luminancia |
|---|---:|---|---:|
| 1 · Hero | 1 | `#082620` | 0,01542 |
| 2 · Problema | 2 | `#F2F5F3` | 0,90653 |
| 3 · Oportunidad | 2 | `#F2F5F3` | 0,90653 |
| 4 · Operación | 3 | `#E2EBE5` | 0,81243 |
| 5 · App | 4 | `#CFDED4` | 0,70261 |
| 6 · Evidencia de producto | 4 | `#CFDED4` | 0,70261 |
| 7 · Modelo / tracción | 5 | `#123C32` | 0,03591 |
| 8 · Inversión / cierre | 6 | `#082620` | 0,01542 |

Capturas: **placa verde oscura localizada + marco fino**, sin teléfono tridimensional, perspectiva ni viñeta. Mantén la interfaz frontal y legible. Recorta una pantalla solo si el recorte responde a una función concreta que estás explicando.

```css
:root {
  --forest: #082620;
  --forest-raised: #123C32;
  --mint: #5FD4A8;
  --paper: #F2F5F3;
  --sage-light: #E2EBE5;
  --sage: #CFDED4;
  --ink-secondary: #456157;
  --line-dark: #254C3F;
}

.section-1, .section-8 {
  background: var(--forest);
  color: var(--paper);
}
.section-2, .section-3 {
  background: var(--paper);
  color: var(--forest);
}
.section-4 {
  background: var(--sage-light);
  color: var(--forest);
}
.section-5, .section-6 {
  background: var(--sage);
  color: var(--forest);
}
.section-7 {
  background: var(--forest-raised);
  color: var(--paper);
}

.app-stage {
  /* Placa local: no ocupa toda la banda. */
  background: var(--forest-raised);
  padding: clamp(24px, 5vw, 56px);
  border-radius: 24px;
  display: grid;
  place-items: center;
}
.app-screen {
  width: min(100%, 296px);
  padding: 5px;
  background: var(--forest);
  border: 1px solid var(--mint);
  border-radius: 25px;
}
.app-screen img {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 19px;
}
```

Contrastes calculados con `(Lmáx + 0,05) / (Lmín + 0,05)`, colores opacos:

| Primer plano / fondo | Contraste |
|---|---:|
| `#082620` / `#F2F5F3` | **14,62:1** |
| `#082620` / `#E2EBE5` | **13,18:1** |
| `#082620` / `#CFDED4` | **11,50:1** |
| `#456157` / `#F2F5F3` | **6,17:1** |
| `#456157` / `#CFDED4` | **4,85:1** |
| `#F2F5F3` / `#123C32` | **11,13:1** |
| `#5FD4A8` / `#082620` | **8,77:1** |
| `#5FD4A8` / `#123C32` | **6,68:1** |
| Blanco / `#F2F5F3` | **1,10:1** |
| Blanco / `#CFDED4` | **1,40:1** |
| `#5FD4A8` / `#F2F5F3` | **1,67:1** |

El blanco de las capturas apenas se separa del papel; cambiarlo solamente por salvia tampoco basta. La placa oscura establece el límite. Usa mint para acentos sobre oscuro y botones con texto `#082620`; sobre fondos claros, usa texto `#082620` o `#456157`.
