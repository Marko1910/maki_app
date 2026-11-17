// Web Push for the PWA (iPhone and browsers). The database decides what is sent and when
// (supabase/sql/2026-11-12_push_reglas.sql); this file only subscribes this device and opens what a tapped
// notice points at. The service worker half is public/push-sw.js.
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { humanize, registerPushToken } from "../lib/api";
import { VAPID_PUBLIC_KEY } from "../lib/config";
import { Icon, useToast } from "./ui";

/** on: this device receives. off: it can, nobody asked yet. ios-install: an iPhone that must install the PWA first. */
export type PushState = "on" | "off" | "denied" | "ios-install" | "unsupported";

const isIos = () => /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
const installed = () => window.matchMedia("(display-mode: standalone)").matches || (navigator as { standalone?: boolean }).standalone === true;
const supported = () => "serviceWorker" in navigator && "PushManager" in window && "Notification" in window && !!VAPID_PUBLIC_KEY;

const keyBytes = () => Uint8Array.from(atob(VAPID_PUBLIC_KEY.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));
const sameKey = (a: ArrayBuffer | null | undefined, b: Uint8Array) => !!a && a.byteLength === b.length && new Uint8Array(a).every((v, i) => v === b[i]);

/** This device's subscription, made with the current server key. One made with an older key can never be used again. */
async function subscription(create: boolean): Promise<PushSubscription | null> {
  const reg = await navigator.serviceWorker.getRegistration();
  if (!reg) {
    // `npm run dev` has no service worker; only a built PWA does.
    if (create) throw new Error("Los avisos funcionan en la app publicada, no en el modo de desarrollo.");
    return null;
  }
  const key = keyBytes();
  let sub = await reg.pushManager.getSubscription();
  if (sub && !sameKey(sub.options.applicationServerKey, key)) { await sub.unsubscribe(); sub = null; }
  if (!sub && (create || Notification.permission === "granted")) sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
  return sub;
}

async function save(sub: PushSubscription) {
  const j = sub.toJSON();
  if (j.endpoint && j.keys?.p256dh && j.keys?.auth) await registerPushToken(j.endpoint, j.keys.p256dh, j.keys.auth);
}

export async function pushState(): Promise<PushState> {
  // iOS exposes PushManager only inside the installed PWA (iOS 16.4+): in Safari's tab it does not exist.
  if (!supported()) return isIos() && !installed() ? "ios-install" : "unsupported";
  if (Notification.permission === "denied") return "denied";
  if (Notification.permission !== "granted") return "off";
  const reg = await navigator.serviceWorker.getRegistration();
  return (await reg?.pushManager.getSubscription()) ? "on" : "off";
}

/** Must be called from a tap: iOS shows the permission dialog only inside a user gesture. */
export async function enablePush(): Promise<PushState> {
  const permission = await Notification.requestPermission();
  if (permission !== "granted") return permission === "denied" ? "denied" : "off";
  const sub = await subscription(true);
  if (sub) await save(sub);
  return sub ? "on" : "off";
}

/** In App: re-registers this device for whoever is signed in, and follows a tapped notice to its screen. */
export function usePush(signedIn: boolean) {
  const nav = useNavigate();
  useEffect(() => {
    // Only a device that already said yes: no dialog can appear from here.
    if (!signedIn || !supported() || Notification.permission !== "granted") return;
    void subscription(false).then((sub) => sub && save(sub)).catch(() => undefined);
  }, [signedIn]);
  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;
    const onMessage = (e: MessageEvent) => {
      const url = e.data?.type === "ruma-open" ? e.data.url : null;
      if (typeof url === "string" && url.startsWith("/") && !url.startsWith("//")) nav(url);
    };
    navigator.serviceWorker.addEventListener("message", onMessage);
    return () => navigator.serviceWorker.removeEventListener("message", onMessage);
  }, [nav]);
}

/**
 * The invitation to turn notices on. `quiet` is for screens with something else to say (the pickup being
 * waited for, the rider's home): it shows only while there is something the person can do about it.
 */
export function PushCard({ quiet = false }: { quiet?: boolean }) {
  const toast = useToast();
  const [state, setState] = useState<PushState | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { void pushState().then(setState).catch(() => setState("unsupported")); }, []);
  if (!state || (quiet && state !== "off" && state !== "ios-install")) return null;

  const enable = async () => {
    setBusy(true);
    try {
      const next = await enablePush();
      setState(next);
      toast(next === "on" ? "Listo: te avisaremos en este teléfono." : next === "denied" ? "Los avisos quedaron bloqueados." : "No se activaron los avisos.");
    } catch (e) { toast(humanize(e)); } finally { setBusy(false); }
  };

  const text = {
    on: "Avisos activados en este teléfono.",
    off: "Te avisamos de tus recojos y tus puntos aunque Ruma esté cerrada.",
    denied: "Los avisos están bloqueados. Permítelos para este sitio en los ajustes del navegador.",
    "ios-install": "En iPhone los avisos llegan solo con Ruma instalada: en Safari toca Compartir, luego «Añadir a pantalla de inicio», y abre Ruma desde ese ícono. Necesita iOS 16.4 o posterior.",
    unsupported: isIos() ? "Para recibir avisos actualiza tu iPhone a iOS 16.4 o posterior." : "Este navegador no permite avisos.",
  }[state];

  if (state === "on") return <p className="muted t-small row" style={{ gap: 8 }}><Icon name="check" size={16} />{text}</p>;
  return (
    <section className="glass pad stack">
      <div className="row" style={{ alignItems: "flex-start" }}>
        <span className="accent"><Icon name="bell" /></span>
        <div className="min0"><p className="t-row">{state === "off" ? "Entérate sin abrir la app" : "Avisos en este teléfono"}</p><p className="muted t-small">{text}</p></div>
      </div>
      {state === "off" && <button className="btn btn-secondary" disabled={busy} onClick={() => void enable()}>{busy ? "Activando…" : "Activar avisos"}</button>}
    </section>
  );
}
