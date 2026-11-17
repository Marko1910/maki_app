"use client";

import { useEffect, useState } from "react";

/**
 * Preferencias del operador sobre qué módulos aparecen en el panel.
 *
 * Vive en el navegador a propósito: es una preferencia de pantalla, no un dato
 * del negocio. Darle una tabla y una migración a "¿muestro este menú?" sería
 * pagar un esquema por algo que no tiene consecuencias contables.
 *
 * El precio de esa decisión, y hay que decirlo en la pantalla: la preferencia
 * no viaja entre computadoras.
 */
const CLAVE = "ruma.modulos.callcenter";
const EVENTO = "ruma:ajustes";

/** Nunca lanza: en una ventana privada el acceso a localStorage tira excepción. */
export function leerCallCenter(): boolean {
  try {
    return localStorage.getItem(CLAVE) === "1";
  } catch {
    return false;
  }
}

export function guardarCallCenter(activo: boolean) {
  try {
    localStorage.setItem(CLAVE, activo ? "1" : "0");
  } catch {
    // Sin almacenamiento la preferencia no persiste, pero el panel sigue usable.
  }
  // El menú y la pantalla de ajustes son componentes distintos y hermanos: sin
  // este aviso el menú no se enteraba del cambio hasta recargar la página.
  window.dispatchEvent(new Event(EVENTO));
}

/**
 * Devuelve `false` en el primer render y se corrige al montar. Leer
 * localStorage durante el render rompe la hidratación: el servidor no tiene
 * ese dato, así que pintaría un menú distinto del que el cliente calcula.
 */
export function useCallCenter(): [boolean, (activo: boolean) => void] {
  const [activo, setActivo] = useState(false);

  useEffect(() => {
    const sincronizar = () => setActivo(leerCallCenter());
    sincronizar();
    window.addEventListener(EVENTO, sincronizar);
    // `storage` llega cuando el operador lo cambia en OTRA pestaña del panel.
    window.addEventListener("storage", sincronizar);
    return () => {
      window.removeEventListener(EVENTO, sincronizar);
      window.removeEventListener("storage", sincronizar);
    };
  }, []);

  return [activo, guardarCallCenter];
}
