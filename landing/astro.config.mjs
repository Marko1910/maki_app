import { defineConfig } from "astro/config";

// Sitio independiente y estático: una sola página, sin framework de UI.
// La única pieza interactiva es un script propio, así que no hace falta una isla.
export default defineConfig({
  site: "https://ruma.pe",
  build: { inlineStylesheets: "auto" },
});
