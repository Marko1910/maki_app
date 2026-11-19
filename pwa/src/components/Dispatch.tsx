import { useEffect, useState } from "react";
import { RumaMap, useHeading } from "./ui";
import type { LatLng } from "../lib/util";
import "./dispatch.css";

// Pre-rendered 3D frames in public/anim (Blender: landing/3d/ruma-fardo-v2.blend, the Eco-Rider tricycle, the Ruma pin).
const RIDER = { name: "rider", count: 12, fps: 14 };
const CITY = { name: "city", count: 24, fps: 12 };
const src = (name: string, i: number) => `/anim/${name}-${String(i).padStart(2, "0")}.webp`;
const reducedMotion = () => typeof window !== "undefined" && !!window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

/** Cycles a frame sequence; frozen on the first frame when [playing] is off or the person asked for less motion. */
function Sprite({ name, count, fps, playing = true, className }: { name: string; count: number; fps: number; playing?: boolean; className?: string }) {
  const [i, setI] = useState(0);
  useEffect(() => {
    // Decode every frame up front so the loop never flashes an empty image.
    for (let k = 0; k < count; k++) new Image().src = src(name, k);
  }, [name, count]);
  useEffect(() => {
    if (!playing || reducedMotion()) return;
    const t = window.setInterval(() => setI((k) => (k + 1) % count), 1000 / fps);
    return () => window.clearInterval(t);
  }, [playing, count, fps]);
  return <img className={className} src={src(name, i)} alt="" draggable={false} />;
}

/** A map centred on [at] with the spinning 3D Ruma pin standing on that point; [radar] adds the searching pulse. */
/**
 * A map centred on [at] with the 3D Ruma pin standing on that point, as a real map marker: it moves with the map
 * when the family drags it (it used to be a sprite over the map's centre, left on the wrong street after a drag).
 * It turns with the phone's compass instead of spinning: its 24 frames are a full turn, 15° apart, so the frame
 * shown is the heading. Without a compass it stands still. Twin of PinMap in DispatchScene.kt.
 */
export function PinMap({ at }: { at: LatLng }) {
  const heading = useHeading();
  const frame = heading == null ? 0 : Math.round(heading / 15) % 24;
  const image = `/anim/pin-${String(frame).padStart(2, "0")}.webp`;
  return <div className="pin-map"><RumaMap pins={[{ ...at, image, tipY: 0.78 }]} /></div>;
}

/**
 * The dispatch moment: while nobody has taken the pickup, a 3D mini city shows the house pulsing like a radar
 * while an Eco-Rider circles the block; once a rider accepts, they ride in on the cargo tricycle and stop.
 * Remount with a key when the rider changes.
 */
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export function DispatchScene({ riderName }: { riderName?: string; at?: LatLng | null }) {
  const [arrived, setArrived] = useState(reducedMotion);
  if (!riderName) {
    return (
      <section className="dispatch glass" role="status">
        {/* A 3D mini city: the house under the Ruma pin pulses like a radar while an Eco-Rider circles the block. */}
        <Sprite {...CITY} className="dispatch-city" />
        <p className="t-row">Buscando al Eco-Rider más cercano…</p>
        <p className="muted t-small">Tu pedido ya está en el tablero de los Eco-Riders de tu zona.</p>
      </section>
    );
  }
  return (
    <section className="dispatch glass" role="status">
      <div className="dispatch-road" aria-hidden="true">
        <div className={`dispatch-drive${arrived ? " done" : ""}`} onAnimationEnd={() => setArrived(true)}>
          <Sprite {...RIDER} playing={!arrived} className="dispatch-rider" />
        </div>
        {arrived && <span className="dispatch-ok">✓</span>}
      </div>
      <p className="t-row">¡{riderName} aceptó tu recojo!</p>
      <p className="muted t-small">Vendrá en su triciclo dentro de tu ventana horaria.</p>
    </section>
  );
}
