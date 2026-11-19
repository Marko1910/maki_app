import { useState } from "react";
import { Empty, Failure, Skeleton, RumaMap, TopBar, type MapPin } from "../../components/ui";
import { loadCenters, loadPrices, type Row } from "../../lib/api";
import { soles, useAsync } from "../../lib/util";

/* ---------- Puntos de acopio: tap a card, the map flies to it ---------- */

export function Centers() {
  const { data, loading, error, reload } = useAsync(async () => {
    const [centers, prices] = await Promise.all([loadCenters(), loadPrices()]);
    return { centers, prices };
  });
  const [picked, setPicked] = useState<string | null>(null);
  const centers: Row[] = data?.centers ?? [];
  const chosen = centers.find((c) => c.id === picked && c.latitude != null) ?? null;
  const pins: MapPin[] = centers.filter((c) => c.latitude != null).map((c) => ({
    lat: c.latitude, lng: c.longitude, label: c.name, selected: c.id === picked,
    // The chosen one keeps the accent; the rest step back so it reads at a glance.
    color: picked && c.id !== picked ? "#55615A" : undefined,
  }));
  return (
    <main className="screen">
      <TopBar title="Puntos de Acopio" />
      {loading ? <Skeleton label="Cargando puntos de acopio…" blocks={[200, 88, 88, 88]} /> : error ? <Failure text={error} retry={() => void reload()} /> : <>
        {pins.length > 0 && <RumaMap pins={pins} focus={chosen ? { lat: chosen.latitude, lng: chosen.longitude } : null} />}
        {!!data?.prices.length && <section className="glass pad stack">
          <h2 className="t-card">Precios de hoy</h2>
          <div className="grid2">{data.prices.map((p) => <div key={p.code} className="glass" style={{ padding: 12 }}><p className="muted t-small">{p.name}</p><p className="money">{soles(p.price_per_kg)}/kg</p></div>)}</div>
          <p className="muted t-small">Los puntos se acreditan cuando el Eco-Rider recoge y el acopio pesa el material.</p>
        </section>}
        {centers.length ? centers.map((c) => (
          <section className="glass pad stack center-card" key={c.id} data-selected={c.id === picked} onClick={() => setPicked(c.id)}>
            <div className="between">
              <button className="t-row center-pick" aria-pressed={c.id === picked} aria-label={`Ver ${c.name} en el mapa`} disabled={c.latitude == null} onClick={(e) => { e.stopPropagation(); setPicked(c.id); }}>{c.name}</button>
              <span className={`chip ${c.is_open ? "chip-accent" : "chip-off"}`}>{c.is_open ? "Abierto" : "Cerrado"}</span>
            </div>
            {c.address_line && <p className="muted t-small">{c.address_line}</p>}
            <p className="t-small">{(c.center_materials ?? []).map((m: Row) => m.materials?.name).filter(Boolean).join(", ")}</p>
            <div className="grid2">
              <a className={`btn ${c.latitude != null ? "btn-primary" : "btn-neutral"}`} aria-disabled={c.latitude == null} href={c.latitude != null ? `https://www.google.com/maps/dir/?api=1&destination=${c.latitude},${c.longitude}` : undefined} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()}>Cómo llegar</a>
              <a className={`btn ${c.phone ? "btn-secondary" : "btn-neutral"}`} aria-disabled={!c.phone} href={c.phone ? `tel:${c.phone}` : undefined} onClick={(e) => e.stopPropagation()}>Llamar</a>
            </div>
          </section>
        )) : <Empty title="Aún no hay acopios cerca" text="Cuando se registre un centro en tu zona aparecerá aquí con sus precios." icon="pin" />}
      </>}
    </main>
  );
}
