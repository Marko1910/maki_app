"use client";

import { useEffect, useRef, useState } from "react";
import maplibregl, { Map as MLMap, Marker } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { hace, minutesSince, esc } from "@/lib/supabase";
import type { RiderVivo, RecojoActivo, Centro } from "@/lib/queries";

const SIN_SENAL_MIN = 15;
const LIMA: [number, number] = [-76.9968, -11.9956];

/** Teselas de OpenStreetMap, sin API key — igual que osmdroid en la app Android. */
const ESTILO: maplibregl.StyleSpecification = {
  version: 8,
  sources: {
    osm: {
      type: "raster",
      tiles: ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      tileSize: 256,
      attribution: "© OpenStreetMap",
    },
  },
  layers: [{ id: "osm", type: "raster", source: "osm" }],
};

/** Pin redondo con el color del diseño; `hueco` = aún sin rider. */
function pin(color: string, glifo: string, size = 32, hueco = false) {
  const el = document.createElement("div");
  el.style.cssText = `width:${size}px;height:${size}px;border-radius:50%;
    background:${hueco ? "#fff" : color};border:${hueco ? `2px solid ${color}` : "2px solid #fff"};
    box-shadow:0 2px 8px rgb(26 26 26 / .35);display:grid;place-items:center;
    font-size:${Math.round(size * 0.45)}px;color:${hueco ? color : "#fff"};cursor:pointer`;
  el.textContent = glifo;
  return el;
}

/** Pin del rider con su nombre SIEMPRE visible: saber quién es cada punto sin hacer clic. */
function pinRider(nombre: string, color: string, sinSenal: boolean) {
  const wrap = document.createElement("div");
  wrap.style.cssText = "display:flex;flex-direction:column;align-items:center;gap:3px;cursor:pointer";

  const punto = document.createElement("div");
  punto.style.cssText = `width:34px;height:34px;border-radius:50%;background:${color};
    border:3px solid #fff;box-shadow:0 2px 10px rgb(26 26 26 / .4);display:grid;place-items:center;
    font-size:15px;color:#fff`;
  punto.textContent = "⚲";

  // Un rider sin señal late despacio para que se note sin leer la etiqueta.
  if (!sinSenal) punto.animate(
    [{ boxShadow: `0 0 0 0 ${color}66` }, { boxShadow: `0 0 0 12px ${color}00` }],
    { duration: 2200, iterations: Infinity },
  );

  const tag = document.createElement("div");
  tag.textContent = nombre;
  tag.style.cssText = `background:#fff;color:#1A1A1A;font:800 10px Inter,sans-serif;
    padding:2px 7px;border-radius:7px;white-space:nowrap;box-shadow:0 1px 4px rgb(26 26 26 / .25);
    border-left:3px solid ${color}`;

  wrap.append(punto, tag);
  return wrap;
}

export default function Mapa({
  centro, riders, recojos,
}: { centro: Centro | null; riders: RiderVivo[]; recojos: RecojoActivo[] }) {
  const cont = useRef<HTMLDivElement>(null);
  const mapa = useRef<MLMap | null>(null);
  const marcas = useRef<Marker[]>([]);
  const [grande, setGrande] = useState(false);

  // El mapa se crea una sola vez, con el centro que haya; si las coordenadas del
  // acopio llegan después (primer render sin datos), se recoloca sin recrearlo.
  useEffect(() => {
    if (!cont.current || mapa.current) return;
    const m = new maplibregl.Map({
      container: cont.current,
      style: ESTILO,
      center: LIMA,
      zoom: 13,
      attributionControl: { compact: true },
      scrollZoom: true,
      doubleClickZoom: true,
      dragRotate: false,          // una sola orientación: es un mapa operativo, no turístico
      touchPitch: false,
    });
    m.addControl(new maplibregl.NavigationControl({ showCompass: false, showZoom: true }), "top-right");
    m.addControl(new maplibregl.FullscreenControl(), "top-right");
    m.addControl(new maplibregl.ScaleControl({ maxWidth: 110, unit: "metric" }), "bottom-right");
    mapa.current = m;
    return () => { m.remove(); mapa.current = null; };
  }, []);

  // El contenedor cambia de alto al expandir: MapLibre necesita que se lo digan.
  useEffect(() => {
    const t = setTimeout(() => mapa.current?.resize(), 260);
    return () => clearTimeout(t);
  }, [grande]);

  // Marcadores: se borran y repintan en cada poll.
  useEffect(() => {
    const m = mapa.current;
    if (!m) return;
    marcas.current.forEach((x) => x.remove());
    marcas.current = [];

    const pop = (html: string) =>
      new maplibregl.Popup({ offset: 20, closeButton: false }).setHTML(
        `<div style="font:600 12px Inter,sans-serif;color:#1A1A1A;line-height:1.55">${html}</div>`,
      );

    const puntos: [number, number][] = [];

    if (centro?.latitude != null && centro?.longitude != null) {
      const c: [number, number] = [centro.longitude, centro.latitude];
      puntos.push(c);
      marcas.current.push(
        new maplibregl.Marker({ element: pin("#D97A2B", "▣", 40) })
          .setLngLat(c)
          .setPopup(pop(`<b>${esc(centro.name)}</b><br>Centro de acopio`))
          .addTo(m),
      );
    }

    for (const r of riders) {
      // Sin coordenadas no se dibuja: aparece en la lista, no en un punto inventado.
      if (r.current_latitude == null || r.current_longitude == null) continue;
      const min = minutesSince(r.last_location_at);
      const sinSenal = min === null || min >= SIN_SENAL_MIN;
      const color = sinSenal ? "#6B7280" : "#185FA5";
      const nombre = r.profiles?.display_name ?? "Eco-Rider";
      const p: [number, number] = [r.current_longitude, r.current_latitude];
      puntos.push(p);
      marcas.current.push(
        new maplibregl.Marker({ element: pinRider(nombre, color, sinSenal) })
          .setLngLat(p)
          .setPopup(pop(
            `<b>${esc(nombre)}</b><br>${esc(r.rider_code ?? "")} · ${esc(r.vehicle_type)}<br>` +
            `<span style="color:${color}">${sinSenal
              ? `sin actualización ${hace(r.last_location_at)} · última posición conocida`
              : `ubicación ${hace(r.last_location_at)}`}</span>`,
          ))
          .addTo(m),
      );
    }

    for (const p of recojos) {
      if (p.latitude == null || p.longitude == null) continue;
      const esperando = !p.rider_id;
      const pt: [number, number] = [p.longitude, p.latitude];
      puntos.push(pt);
      marcas.current.push(
        new maplibregl.Marker({ element: pin("#0F6E56", "⌂", 26, esperando) })
          .setLngLat(pt)
          .setPopup(pop(
            `<b>${esc(p.generator?.display_name ?? "Familia")}</b><br>${esc(p.code ?? "")}<br>` +
            `<span style="color:#0F6E56">${esperando ? "esperando rider" : esc(p.status)}</span>`,
          ))
          .addTo(m),
      );
    }

    // Encuadre inicial: que todo quepa. Después ya manda el usuario y no se le mueve.
    if (puntos.length && !m.getCanvas().dataset.encuadrado) {
      const b = puntos.reduce((bb, p) => bb.extend(p), new maplibregl.LngLatBounds(puntos[0], puntos[0]));
      m.fitBounds(b, { padding: 70, maxZoom: 15, duration: 0 });
      m.getCanvas().dataset.encuadrado = "1";
    }
  }, [centro, riders, recojos]);

  function verTodo() {
    const m = mapa.current;
    if (!m || !marcas.current.length) return;
    const pts = marcas.current.map((x) => x.getLngLat());
    const b = pts.reduce((bb, p) => bb.extend(p), new maplibregl.LngLatBounds(pts[0], pts[0]));
    m.fitBounds(b, { padding: 70, maxZoom: 15, duration: 500 });
  }

  const conSenal = riders.filter((r) => {
    const mm = minutesSince(r.last_location_at);
    return mm !== null && mm < SIN_SENAL_MIN && r.current_latitude != null;
  }).length;
  const sinUbicacion = riders.filter((r) => r.current_latitude == null).length;

  return (
    <div className="relative">
      <div
        ref={cont}
        className="rounded-[14px] overflow-hidden border border-border transition-[height] duration-200"
        style={{ height: grande ? 620 : 300 }}
      />

      {/* Controles propios, además de los de MapLibre arriba a la derecha. */}
      <div className="absolute top-3 left-3 z-10 flex gap-1.5">
        <button onClick={() => mapa.current?.zoomIn({ duration: 220 })} title="Acercar"
          className="w-9 h-9 rounded-xl bg-white/95 backdrop-blur shadow-sm text-lg font-bold text-text hover:bg-white">+</button>
        <button onClick={() => mapa.current?.zoomOut({ duration: 220 })} title="Alejar"
          className="w-9 h-9 rounded-xl bg-white/95 backdrop-blur shadow-sm text-lg font-bold text-text hover:bg-white">−</button>
        <button onClick={verTodo} title="Ver todo"
          className="h-9 px-3 rounded-xl bg-white/95 backdrop-blur shadow-sm text-[11px] font-bold text-text hover:bg-white">
          Ver todo
        </button>
        <button onClick={() => setGrande(!grande)} title={grande ? "Reducir" : "Ampliar"}
          className="h-9 px-3 rounded-xl bg-white/95 backdrop-blur shadow-sm text-[11px] font-bold text-text hover:bg-white">
          {grande ? "Reducir" : "Ampliar"}
        </button>
      </div>

      <div className="absolute left-3 bottom-3 z-10 flex flex-wrap items-center gap-3 bg-white/95
                      backdrop-blur rounded-xl px-3 py-2 shadow-sm">
        {([
          ["#185FA5", "Ubicación reciente", false],
          ["#6B7280", "Sin actualización", false],
          ["#0F6E56", "Domicilio asignado", false],
          ["#0F6E56", "Esperando rider", true],
          ["#D97A2B", "Acopio", false],
        ] as [string, string, boolean][]).map(([c, t, hueco]) => (
          <span key={t} className="flex items-center gap-1.5 text-[11px] font-bold">
            <span className="w-3.5 h-3.5 rounded-full"
              style={hueco ? { background: "#fff", border: `2px solid ${c}` } : { background: c }} />
            {t}
          </span>
        ))}
        <span className="text-[11px] text-text2 border-l border-border pl-3">
          {conSenal} de {riders.length} riders con señal
          {sinUbicacion > 0 && ` · ${sinUbicacion} sin ubicación (solo en la lista)`}
        </span>
      </div>
    </div>
  );
}
