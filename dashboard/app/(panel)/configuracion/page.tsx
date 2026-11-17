"use client";

import { useEffect, useState } from "react";
import { kilos } from "@/lib/supabase";
import { miCentro, type Centro } from "@/lib/queries";
import { useCallCenter } from "@/lib/ajustes";
import { Card, Chip, ErrorBox, Cargando, SkeletonBloque } from "@/components/ui";

/**
 * Interruptor accesible: un `button` con `role="switch"`, no un div que
 * escucha clics. Así lo alcanza el tabulador y un lector de pantalla puede
 * decir si está encendido.
 */
function Interruptor({ activo, onChange, etiqueta }: {
  activo: boolean;
  onChange: (activo: boolean) => void;
  etiqueta: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={activo}
      aria-label={etiqueta}
      onClick={() => onChange(!activo)}
      className={`ruma-press relative w-11 h-6 shrink-0 rounded-full transition-colors
        ${activo ? "bg-admin" : "bg-border"}`}
    >
      <span
        className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-surface shadow
          transition-transform ${activo ? "translate-x-5" : "translate-x-0"}`}
      />
    </button>
  );
}

function Dato({ k, v }: { k: string; v: string }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] uppercase tracking-wide text-text2 font-bold">{k}</dt>
      <dd className="text-[14px] font-semibold mt-0.5 break-words">{v}</dd>
    </div>
  );
}

export default function Configuracion() {
  const [centro, setCentro] = useState<Centro | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [primera, setPrimera] = useState(true);
  const [callCenter, setCallCenter] = useCallCenter();

  useEffect(() => {
    (async () => {
      try {
        setCentro(await miCentro());
        setError(null);
      } catch (e) {
        setError(e instanceof Error ? e.message : "No pudimos leer los datos del centro.");
      } finally {
        setPrimera(false);
      }
    })();
  }, []);

  if (primera) {
    return (
      <Cargando etiqueta="Abriendo la configuración">
        <SkeletonBloque alto="h-40" />
        <SkeletonBloque alto="h-40" />
      </Cargando>
    );
  }

  return (
    <>
      {/* Los módulos no dependen del centro: se muestran aun si la cuenta
          todavía no tiene uno asignado, que es justo cuando hace falta
          ordenar el panel. */}
      <section className="flex flex-col gap-3 ruma-in">
        <h2 className="text-[15px] font-extrabold">Módulos del panel</h2>
        <Card>
          <div className="p-5 flex items-start gap-4">
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2 flex-wrap">
                <p className="text-[14px] font-bold">Call Center</p>
                {callCenter
                  ? <Chip color="success">en el menú</Chip>
                  : <Chip color="text2">oculto</Chip>}
              </div>
              <p className="text-[13px] text-text2 mt-1.5 leading-snug">
                Registro de llamadas: recojos pedidos por teléfono, consultas de saldo
                y reclamos. Está fuera del menú mientras el recojo entra por la app.
                Nada de lo ya registrado se borra al ocultarlo.
              </p>
            </div>
            <Interruptor
              activo={callCenter}
              onChange={setCallCenter}
              etiqueta="Mostrar el Call Center en el menú"
            />
          </div>
        </Card>
        <p className="text-[12px] text-text2">
          Esta preferencia se guarda en este navegador, no en tu cuenta: si abres el
          panel en otra computadora tendrás que volver a activarlo.
        </p>
      </section>

      <section className="flex flex-col gap-3 ruma-in" style={{ "--i": 1 } as React.CSSProperties}>
        <h2 className="text-[15px] font-extrabold">Centro</h2>
        {error ? (
          <ErrorBox msg={error} />
        ) : !centro ? (
          <Card>
            <p className="p-5 text-[13px] text-text2">
              Tu cuenta todavía no tiene un centro asignado, así que el resto del panel
              aparecerá vacío. Escríbenos para que lo vinculemos.
            </p>
          </Card>
        ) : (
          <Card>
            <dl className="p-5 grid sm:grid-cols-2 gap-x-6 gap-y-4">
              <Dato k="Nombre" v={centro.name} />
              <Dato k="Dirección" v={centro.address_line ?? "sin dirección registrada"} />
              <Dato
                k="Capacidad"
                v={centro.capacity_kg != null ? kilos(centro.capacity_kg) : "sin dato"}
              />
              <Dato k="Carga actual" v={kilos(centro.current_load_kg)} />
            </dl>
          </Card>
        )}
        <p className="text-[12px] text-text2">
          Estos datos los administra Ruma y aquí solo se leen. Si algo está mal,
          escríbenos y lo corregimos.
        </p>
      </section>
    </>
  );
}
