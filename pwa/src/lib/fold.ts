// What the scan counter shows. Mirrors foldPhotos in supabase/functions/detect-material/index.ts,
// so the phone says what commit will book. Check: node scripts/check-fold.ts
export type Photo = { quantity: number; brands: Record<string, number> };

/** A photo repeating a counted brand is the same pile framed again: it adds only what grew. */
export function foldPhotos(photos: Photo[]) {
  const brands: Record<string, number> = {};
  let unnamed = 0, quantity = 0;
  for (const photo of photos) {
    const samePile = Object.keys(photo.brands).some((b) => b in brands);
    const named = Object.values(photo.brands).reduce((s, n) => s + n, 0);
    const shownUnnamed = Math.max(0, photo.quantity - named);
    const covered = Object.entries(brands).reduce((s, [b, n]) => s + Math.max(0, n - (photo.brands[b] ?? 0)), 0);
    let newlyNamed = 0;
    for (const [brand, units] of Object.entries(photo.brands)) {
      const extra = samePile ? Math.max(0, units - (brands[brand] ?? 0)) : units;
      brands[brand] = (brands[brand] ?? 0) + extra;
      newlyNamed += extra;
    }
    const grew = samePile ? Math.max(0, newlyNamed + Math.max(0, shownUnnamed - covered) - unnamed) : newlyNamed + shownUnnamed;
    const add = Math.min(grew, photo.quantity);
    unnamed = Math.max(0, unnamed + add - newlyNamed);
    quantity += add;
  }
  return quantity;
}
