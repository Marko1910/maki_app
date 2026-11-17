// Push half of the service worker. The generated worker imports this file (vite.config.ts,
// workbox.importScripts); the page half is src/components/Push.tsx and the sender is the
// send-push Edge Function, whose payload is { title, body, tag, url }.

self.addEventListener("push", (event) => {
  let data = {};
  try { data = event.data ? event.data.json() : {}; } catch { data = { body: event.data ? event.data.text() : "" }; }
  // iOS revokes the subscription of a worker that receives a push and shows nothing: always show.
  event.waitUntil(self.registration.showNotification(data.title || "Ruma", {
    body: data.body || "",
    // One notice per pickup: a new step replaces the previous one, and sounds again.
    tag: data.tag,
    renotify: !!data.tag,
    icon: "/icon-192.png",
    badge: "/icon-192.png",
    data: { url: typeof data.url === "string" ? data.url : "/" },
  }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || "/";
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    const open = windows[0];
    if (open) {
      // The app is running: bring it forward and let its router go there (usePush), with no reload.
      await open.focus().catch(() => undefined);
      open.postMessage({ type: "ruma-open", url });
    } else {
      await self.clients.openWindow(url);
    }
  })());
});
