/**
 * pwa-register.js - registers sw.js so the browser treats CollaboDraw as installable.
 * Safe to include on every page; no-ops in browsers without service worker support.
 */
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // Installability is a progressive enhancement - a failed registration shouldn't
      // block or alarm anyone using the app normally.
    });
  });
}
