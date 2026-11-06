const CACHE = "transcriber-v3";
const SHELL = ["./", "./index.html", "./manifest.webmanifest", "./icon-192.png", "./icon-512.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)));
  self.skipWaiting();
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
  );
  self.clients.claim();
});

function store(req, res) {
  if (res.ok) {
    const copy = res.clone();
    caches.open(CACHE).then((c) => c.put(req, copy));
  }
  return res;
}

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);

  // Файлы приложения: сначала сеть (чтобы обновления доходили), без сети — из кэша
  if (url.origin === location.origin) {
    e.respondWith(fetch(req).then((res) => store(req, res)).catch(() => caches.match(req)));
    return;
  }
  // Библиотеки с jsdelivr (Transformers.js, ffmpeg): из кэша, один раз скачали — дальше офлайн
  if (url.hostname === "cdn.jsdelivr.net") {
    e.respondWith(caches.match(req).then((hit) => hit || fetch(req).then((res) => store(req, res))));
  }
});
