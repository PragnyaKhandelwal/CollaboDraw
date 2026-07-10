/**
 * csrf-fetch.js - attaches the CSRF token to same-origin, state-changing fetch() calls.
 *
 * SecurityConfig issues a CSRF token via a JS-readable cookie (XSRF-TOKEN), but that cookie is
 * NOT reliably present on every page: Spring Security's CsrfAuthenticationStrategy deletes it
 * on every login (to rotate the token), and it's only reissued when a page happens to render
 * something that touches the token (e.g. a Thymeleaf form with a csrf hidden field) - a page
 * like the dashboard, which has no such form, would leave a logged-in user with NO CSRF cookie
 * at all until they happened to visit one that did, so their very first fetch() write would
 * fail with a silent 403 (confirmed by hand while testing this).
 *
 * The fix is the standard Spring Security + Thymeleaf pattern for JS-driven requests: every
 * template embeds the token in a <meta name="_csrf"> tag (see each template's <head>), which
 * Thymeleaf resolves fresh on every single render regardless of page content. This wrapper
 * reads that meta tag first, falling back to the cookie only if it's missing (e.g. a
 * non-Thymeleaf-rendered context). Must load before any script that calls fetch().
 */
(function () {
  function readCookie(name) {
    const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
    return match ? decodeURIComponent(match[1]) : null;
  }

  function readMeta(name) {
    const el = document.querySelector(`meta[name="${name}"]`);
    return el ? el.getAttribute('content') : null;
  }

  const originalFetch = window.fetch.bind(window);
  const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

  window.fetch = function (input, init) {
    const opts = init ? Object.assign({}, init) : {};
    const method = (opts.method || (input && input.method) || 'GET').toUpperCase();

    if (MUTATING_METHODS.has(method)) {
      const token = readMeta('_csrf') || readCookie('XSRF-TOKEN');
      const headerName = readMeta('_csrf_header') || 'X-XSRF-TOKEN';
      if (token) {
        const headers = new Headers(opts.headers || (input && input.headers) || {});
        if (!headers.has(headerName)) {
          headers.set(headerName, token);
        }
        opts.headers = headers;
      }
    }

    return originalFetch(input, opts);
  };
})();
