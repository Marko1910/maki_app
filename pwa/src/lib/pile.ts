/** Tu ruma guardada: scans with no pickup yet. Twin of savedQty / savedSummary in GeneratorViewModel.kt. Pure, so scripts/check-pile.ts runs it. */

const PILE_LABEL: Record<string, string> = { PET: "botellas PET", ALU: "latas de aluminio", VID: "botellas de vidrio", CAR: "cajas de cartón", PIL: "pilas" };

/** The pile per material id; corrections made to the scan just saved (scan.items vs scan.ai) carry over. */
export function pileCounts(pile: { material_id: string; quantity: number }[], scan?: { items: { material_id?: string; quantity: number }[]; ai?: Record<string, number> } | null) {
  const out: Record<string, number> = {};
  for (const r of pile) out[r.material_id] = (out[r.material_id] ?? 0) + r.quantity;
  if (scan?.ai) for (const it of scan.items) if (it.material_id) out[it.material_id] = (out[it.material_id] ?? 0) + it.quantity - (scan.ai[it.material_id] ?? 0);
  for (const k of Object.keys(out)) if (out[k] <= 0) delete out[k];
  return out;
}

/** "7 botellas PET y 2 latas de aluminio guardadas", or null with nothing saved. */
export function pileSummary(counts: Record<string, number>, pile: { material_id: string; code: string; name: string }[]) {
  const byId = Object.fromEntries(pile.map((r) => [r.material_id, r]));
  const parts = Object.entries(counts).sort((a, b) => b[1] - a[1]).map(([id, n]) => {
    const label = PILE_LABEL[byId[id]?.code] ?? byId[id]?.name ?? "residuos";
    return `${n} ${n === 1 ? label.replace(/^([a-zá-ú]+)s(?= |$)/, "$1") : label}`;
  });
  if (!parts.length) return null;
  const single = parts.length === 1 && Object.values(counts)[0] === 1;
  return (parts.length > 1 ? `${parts.slice(0, -1).join(", ")} y ${parts[parts.length - 1]}` : parts[0]) + (single ? " guardada" : " guardadas");
}
