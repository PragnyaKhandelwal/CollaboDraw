/**
 * sw.js - minimal service worker.
 *
 * CollaboDraw is a live, WebSocket-backed collaborative app, so offline editing wouldn't
 * actually let you do anything useful - there's no cached copy of a "board" to work from
 * disconnected. This worker exists only to satisfy browsers' installability requirement
 * (a registered service worker with a fetch handler), so the app can be added to the
 * home screen / installed as a standalone window. It intentionally does no caching and
 * just passes every request straight through to the network.
 */
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', (event) => {
  event.respondWith(fetch(event.request));
});
