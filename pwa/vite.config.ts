import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig(({ mode, command }) => {
  const isProd = mode === "production";
  const env = loadEnv(mode, process.cwd(), "VITE_");
  // A production build must never fall back to the staging database in silence.
  if (command === "build" && isProd && !(env.VITE_SUPABASE_URL && env.VITE_SUPABASE_ANON_KEY)) {
    throw new Error("Producción necesita VITE_SUPABASE_URL y VITE_SUPABASE_ANON_KEY en .env.production.local");
  }
  const name = isProd ? "Ruma" : "Ruma Staging";
  return {
    plugins: [
      react(),
      {
        name: "ruma-noindex",
        // Test environments stay out of search results.
        transformIndexHtml: (html: string) => (isProd ? html : html.replace("</head>", '<meta name="robots" content="noindex" /></head>')),
      },
      VitePWA({
        registerType: "autoUpdate",
        manifest: {
          name,
          short_name: name,
          description: "Tu agente de reciclaje inteligente",
          lang: "es-PE",
          start_url: "/",
          display: "standalone",
          orientation: "portrait",
          theme_color: "#F7F5EF",
          background_color: "#F7F5EF",
          icons: [
            { src: "icon-192.png", sizes: "192x192", type: "image/png" },
            { src: "icon-512.png", sizes: "512x512", type: "image/png" },
            { src: "icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
          ],
        },
        workbox: {
          globPatterns: ["**/*.{js,css,html,svg,png,woff2}"],
          navigateFallback: "/index.html",
        },
      }),
    ],
  };
});
