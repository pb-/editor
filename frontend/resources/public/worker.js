const version = 4;

self.addEventListener('install', (e) => {
  e.waitUntil(
    caches.open('editor-store').then((cache) => cache.addAll([
      '/',
      '/index.html',
      '/diff3.js',
      '/main.js',
      '/main.css',
      '/app.webmanifest',
      '/cloud.svg',
      '/download-cloud.svg',
      '/upload-cloud.svg',
    ])),
  );
});

self.addEventListener('fetch', (e) => {
  e.respondWith(
    caches.match(e.request).then((response) => response || fetch(e.request)),
  );
});
