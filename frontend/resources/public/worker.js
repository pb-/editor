const version = 3;

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open('editor-store').then((cache) => cache.addAll([
      '/',
      '/index.html',
      '/diff3.js',
      '/main.js',
      '/main.css',
      '/app.webmanifest',
    ])),
  );
});

self.addEventListener('fetch', (e) => {
  e.respondWith(
    caches.match(e.request).then((response) => response || fetch(e.request)),
  );
});
