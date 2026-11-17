"use client";

import { useCallback, useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import { supabase } from "@/lib/supabase";
import { miCentro, type Centro } from "@/lib/queries";
import { useCallCenter } from "@/lib/ajustes";
import { Cargando, SkeletonKpis, SkeletonBloque, ErrorBox } from "@/components/ui";

type Seccion = {
  href: string;
  label: string;
  icon: string;
  /** Ya implementada. Las que no, se ven en gris y no enlazan. */
  listo: boolean;
  /** Fuera del menú salvo que el operador la encienda en Configuración. */
  opcional?: boolean;
};

/**
 * El Call Center quedó marcado como opcional: el recojo entra por la app, así
 * que sale del menú y se enciende desde Configuración. Ocultarlo no es
 * apagarlo — la ruta sigue existiendo y lo ya registrado se sigue leyendo.
 */
const SECCIONES: Seccion[] = [
  { href: "/dashboard", label: "Dashboard", icon: "▦", listo: true },
  { href: "/call-center", label: "Call Center", icon: "✆", listo: true, opcional: true },
  { href: "/materiales", label: "Materiales y Precios", icon: "◷", listo: true },
  { href: "/mercado", label: "Mercado Vivo", icon: "◈", listo: true },
  { href: "/pagos", label: "Pagos a Riders", icon: "▭", listo: true },
  { href: "/planes", label: "Planes (Yape)", icon: "★", listo: true },
  { href: "/balance", label: "Balance Financiero", icon: "▤", listo: true },
  { href: "/reportes", label: "Reportes", icon: "▥", listo: true },
  { href: "/configuracion", label: "Configuración", icon: "⚙", listo: true },
];

export default function PanelLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const path = usePathname();
  const [centro, setCentro] = useState<Centro | null>(null);
  const [nombre, setNombre] = useState("");
  const [listo, setListo] = useState(false);
  const [fallo, setFallo] = useState<string | null>(null);
  const [callCenter] = useCallCenter();
  const secciones = SECCIONES.filter((s) => !s.opcional || callCenter);

  // Sin catch, un rechazo de miCentro dejaba el panel en "Abriendo el centro"
  // para siempre: los hijos nunca se montaban, asi que su propio reintento
  // tampoco existia. El unico camino era recargar el navegador.
  const abrir = useCallback(async () => {
    setFallo(null);
    try {
      const { data } = await supabase.auth.getSession();
      if (!data.session) {
        router.replace("/login");
        return;
      }
      const { data: p } = await supabase
        .from("profiles").select("display_name").eq("id", data.session.user.id).maybeSingle();
      setNombre(p?.display_name ?? "Operador");
      setCentro(await miCentro());
      setListo(true);
    } catch (e) {
      setFallo(e instanceof Error ? e.message : "No se pudo abrir el centro.");
    }
  }, [router]);

  useEffect(() => {
    void abrir();
  }, [abrir]);

  async function salir() {
    await supabase.auth.signOut();
    router.replace("/login");
  }

  return (
    <div className="flex min-h-screen">
      <aside className="w-60 shrink-0 bg-surface border-r border-border flex flex-col">
        <div className="p-5 flex items-center gap-3 border-b border-border">
          <div className="w-10 h-10 rounded-xl bg-admin/10 grid place-items-center text-admin">▣</div>
          <div className="min-w-0">
            <p className="text-[13px] font-extrabold truncate">{centro?.name ?? "Centro"}</p>
            <p className="text-[11px] text-success font-semibold">● Activo</p>
          </div>
        </div>

        <nav className="p-3 flex flex-col gap-1">
          {secciones.map((s) => {
            const activo = path.startsWith(s.href);
            if (!s.listo)
              return (
                <span key={s.href} className="flex items-center gap-3 px-3.5 py-2.5 rounded-[10px] text-text2/50 text-sm font-semibold cursor-not-allowed">
                  <span className="w-5 text-center">{s.icon}</span>
                  {s.label}
                </span>
              );
            return (
              <Link key={s.href} href={s.href}
                className={`flex items-center gap-3 px-3.5 py-2.5 rounded-[10px] text-sm font-semibold transition
                  ${activo ? "bg-[#FFF3EB] text-admin border-l-4 border-admin font-bold" : "text-text2 hover:bg-bg"}`}>
                <span className="w-5 text-center">{s.icon}</span>
                {s.label}
              </Link>
            );
          })}
        </nav>

        <div className="mt-auto p-4 border-t border-border flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-admin text-white grid place-items-center text-xs font-extrabold">
            {nombre.charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[13px] font-bold truncate">{nombre}</p>
            <p className="text-[11px] text-text2">Operador</p>
          </div>
          <button onClick={salir} title="Cerrar sesión" className="text-text2 hover:text-error px-1">⇥</button>
        </div>
      </aside>

      <div className="flex-1 min-w-0 flex flex-col">
        <header className="h-[72px] shrink-0 bg-surface border-b border-border px-8 flex items-center gap-4">
          <div className="w-11 h-11 rounded-xl bg-admin/10 grid place-items-center text-admin">▣</div>
          <div className="min-w-0">
            <h1 className="text-lg font-extrabold leading-tight truncate">{centro?.name ?? "—"}</h1>
            <p className="text-xs text-text2 truncate">{centro?.address_line ?? ""}</p>
          </div>
        </header>
        <main className="flex-1 p-8 flex flex-col gap-[22px] overflow-auto">
          {/* Antes esto era un "Cargando…" suelto, que es justo el salto en
              seco que los esqueletos de cada pantalla existen para evitar. */}
          {fallo ? (
            <ErrorBox msg={fallo} onRetry={() => void abrir()} />
          ) : listo ? children : (
            <Cargando etiqueta="Abriendo el centro">
              <SkeletonKpis n={3} />
              <SkeletonBloque alto="h-[380px]" />
            </Cargando>
          )}
        </main>
      </div>
    </div>
  );
}
