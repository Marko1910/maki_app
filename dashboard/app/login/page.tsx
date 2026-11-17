"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { supabase } from "@/lib/supabase";

/** Lo que el centro de acopio hace cada día, en el orden en que lo hace. */
const FLUJO = [
  { n: "01", t: "Recibe", d: "El Eco-Rider llega con el lote de varias familias." },
  { n: "02", t: "Pesa y califica", d: "Las unidades se vuelven kilos. Aquí entra el dinero." },
  { n: "03", t: "Liquida", d: "Cada familia cobra sus puntos y el rider su tarifa." },
];

export default function Login() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [pass, setPass] = useState("");
  const [ver, setVer] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function entrar(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const { data, error } = await supabase.auth.signInWithPassword({ email, password: pass });
    if (error) {
      setError(
        error.message === "Invalid login credentials"
          ? "Correo o contraseña incorrectos."
          : error.message,
      );
      setBusy(false);
      return;
    }
    const { data: perfil } = await supabase
      .from("profiles").select("role,display_name").eq("id", data.user.id).maybeSingle();
    if (perfil?.role !== "operador") {
      await supabase.auth.signOut();
      setError("Esta cuenta no es de un centro de acopio. Entra desde la app Ruma.");
      setBusy(false);
      return;
    }
    router.push("/dashboard");
  }

  return (
    <main className="min-h-screen grid lg:grid-cols-[1.1fr_1fr]">
      {/* Lado marca: lo primero que ve quien abre el panel. */}
      <section className="relative hidden lg:flex flex-col justify-between p-14 overflow-hidden bg-gen">
        <div
          aria-hidden
          className="absolute inset-0 opacity-30"
          style={{ background: "radial-gradient(120% 90% at 85% 10%, #1E8E4F 0%, transparent 55%)" }}
        />
        <div className="relative">
          <div className="flex items-center gap-3">
            <div className="w-11 h-11 rounded-2xl bg-white/15 grid place-items-center text-white text-xl backdrop-blur">
              ▣
            </div>
            <div className="text-white">
              <p className="text-xl font-extrabold leading-none tracking-tight">Ruma</p>
              <p className="text-[11px] text-white/70 mt-1">Centro de Acopio</p>
            </div>
          </div>

          <h2 className="mt-16 text-[40px] leading-[1.1] font-extrabold text-white max-w-md tracking-tight">
            El reciclaje del barrio,
            <br />
            con cuentas claras.
          </h2>
          <p className="mt-5 text-white/75 text-[15px] leading-relaxed max-w-md">
            Cada kilo que entra por tu puerta queda registrado: qué familia lo juntó, qué rider lo
            trajo y cuánto vale. Sin cuadernos, sin discusiones al pesar.
          </p>
        </div>

        <div className="relative flex flex-col gap-5">
          {FLUJO.map((f) => (
            <div key={f.n} className="flex gap-4 items-start">
              <span className="text-white/35 text-[13px] font-extrabold tabular-nums pt-0.5">{f.n}</span>
              <div>
                <p className="text-white font-bold text-[15px] leading-tight">{f.t}</p>
                <p className="text-white/60 text-[13px] leading-snug mt-0.5">{f.d}</p>
              </div>
            </div>
          ))}
        </div>

        <p className="relative text-white/40 text-[11px]">
          1.000 Eco-Puntos = S/ 1.00 · La familia recibe el 40 % del valor del material
        </p>
      </section>

      {/* Lado formulario. */}
      <section className="flex items-center justify-center p-6 sm:p-12">
        <form onSubmit={entrar} className="w-full max-w-[380px] flex flex-col gap-6">
          <div className="lg:hidden flex items-center gap-3 mb-2">
            <div className="w-10 h-10 rounded-xl bg-gen grid place-items-center text-white">▣</div>
            <p className="text-lg font-extrabold">Ruma</p>
          </div>

          <div>
            <h1 className="text-[26px] font-extrabold tracking-tight leading-tight">Entra a tu centro</h1>
            <p className="text-text2 text-sm mt-1.5">
              Panel del operador. Las familias y los Eco-Riders usan la app del celular.
            </p>
          </div>

          <div className="flex flex-col gap-4">
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-text2">Correo</span>
              <input
                type="email" required autoComplete="email" value={email}
                onChange={(e) => setEmail(e.target.value)} placeholder="tucentro@ruma.pe"
                className="h-12 border border-border rounded-xl px-4 text-sm outline-none bg-surface
                           focus:border-gen focus:ring-4 focus:ring-gen/10 transition"
              />
            </label>

            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-bold text-text2">Contraseña</span>
              <div className="relative">
                <input
                  type={ver ? "text" : "password"} required autoComplete="current-password"
                  value={pass} onChange={(e) => setPass(e.target.value)} placeholder="••••••••"
                  className="h-12 w-full border border-border rounded-xl px-4 pr-16 text-sm outline-none bg-surface
                             focus:border-gen focus:ring-4 focus:ring-gen/10 transition"
                />
                <button
                  type="button" onClick={() => setVer(!ver)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-[11px] font-bold text-text2 hover:text-gen px-1"
                >
                  {ver ? "ocultar" : "ver"}
                </button>
              </div>
            </label>
          </div>

          {error && (
            <p role="alert" className="text-[13px] text-error bg-error/10 rounded-xl px-4 py-3 font-semibold">
              {error}
            </p>
          )}

          <button
            disabled={busy}
            className="h-12 bg-gen text-white rounded-xl text-[15px] font-bold
                       hover:brightness-110 active:brightness-95 disabled:opacity-60 transition"
          >
            {busy ? "Entrando…" : "Entrar al panel"}
          </button>

          <div className="rounded-xl border border-border bg-surface p-4 flex flex-col gap-2">
            <p className="text-[11px] font-bold text-text2 uppercase tracking-wide">Cuenta de demostración</p>
            <button
              type="button"
              onClick={() => { setEmail("operador@ruma.test"); setPass("Maki12345!"); }}
              className="text-left text-[13px] leading-snug hover:text-gen transition"
            >
              <span className="font-bold">operador@ruma.test</span>
              <span className="text-text2"> · Maki12345!</span>
              <span className="block text-[11px] text-gen font-semibold mt-0.5">tocar para rellenar</span>
            </button>
          </div>

          <p className="text-[11px] text-text2 leading-relaxed">
            Los datos que ves están protegidos por políticas de la base: un operador solo alcanza su
            propio centro, aunque pida otra cosa.
          </p>
        </form>
      </section>
    </main>
  );
}
