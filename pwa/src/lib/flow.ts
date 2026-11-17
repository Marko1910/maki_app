import { useLayoutEffect, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { useLocation, useNavigationType, type Location, type NavigateFunction } from "react-router-dom";

/* ---------- Screen transitions ---------- */

type VTDocument = Document & { startViewTransition?: (update: () => void) => unknown };
const reducedMotion = () => !!window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

/**
 * The location `<Routes>` should draw. It lags the real one by a beat so the View Transitions API
 * can photograph the old screen first, then the new one is committed inside the transition
 * (`::view-transition-*` in styles.css). Without the API, or with reduced motion, it swaps at once
 * and the CSS fallback (a short fade-in of the new `main`) does the rest.
 * `data-nav` on <html> says forward or back, so the slide goes the right way.
 */
export function useTransitionLocation(): Location {
  const location = useLocation();
  const type = useNavigationType();
  const [shown, setShown] = useState(location);
  const latest = useRef(location);
  latest.current = location;
  useLayoutEffect(() => {
    if (location === shown) return;
    document.documentElement.dataset.nav = type === "POP" ? "back" : "forward";
    const doc = document as VTDocument;
    // Same screen with new state (a cleared banner, a reload): no transition.
    const sameScreen = location.pathname === shown.pathname && location.search === shown.search;
    if (sameScreen || !doc.startViewTransition || reducedMotion()) { setShown(location); return; }
    // The latest location wins even if a quick second tap skips this transition.
    doc.startViewTransition(() => flushSync(() => setShown(latest.current)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location]);
  return shown;
}

/* ---------- The scan → pickup flow ---------- */

const historyIdx = (): number | null => {
  const idx = (window.history.state as { idx?: number } | null)?.idx;
  return typeof idx === "number" ? idx : null;
};

// ponytail: one module-level mark; a second flow overwrites it, which is what the camera wants.
let cameraIdx: number | null = null;
/** The camera calls this on open. "Otra foto" replaces the same history entry, so the mark holds. */
export const markFlowStart = () => { cameraIdx = historyIdx(); };

/**
 * After a pickup is asked for: back out of camera → result → form to where the scan started,
 * then land on Home with [state]. "Atrás" from there never reopens the form or the camera.
 */
export function returnHome(nav: NavigateFunction, state?: unknown) {
  const now = historyIdx();
  const delta = cameraIdx != null && now != null ? cameraIdx - 1 - now : 0;
  cameraIdx = null;
  const land = () => nav("/f/inicio", { replace: true, state });
  if (delta >= 0) return land();
  window.addEventListener("popstate", () => setTimeout(land, 0), { once: true });
  nav(delta);
}

/**
 * "Volver" on a screen opened with nothing behind it (a tapped notification, a shared link, a
 * reload of an installed PWA): go to the role's home instead of nowhere.
 */
export function goBack(nav: NavigateFunction) {
  if ((historyIdx() ?? 0) > 0) return nav(-1);
  const path = window.location.pathname;
  nav(path.startsWith("/r/") ? "/r/inicio" : path.startsWith("/f/") ? "/f/inicio" : "/", { replace: true });
}

/** A short buzz on phones that have one (Android Chrome); iOS ignores it. */
export const tick = () => { try { navigator.vibrate?.(12); } catch { /* not supported */ } };
