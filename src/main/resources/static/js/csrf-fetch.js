/**
 * csrf-fetch.js - attaches the CSRF token to same-origin, state-changing fetch() calls.
 *
 * SecurityConfig now issues a CSRF token via a JS-readable cookie (XSRF-TOKEN) instead of
 * leaving CSRF disabled app-wide. Spring Security expects that token back as the
 * X-XSRF-TOKEN header on POST/PUT/PATCH/DELETE requests. Rather than editing every fetch()
 * call across the codebase to add the header by hand, this wraps window.fetch once so every
 * existing call site keeps working unchanged. Must load before any script that calls fetch().
 */
(function () {
  function readCookie(name) {
    const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
    return match ? decodeURIComponent(match[1]) : null;
  }

  const originalFetch = window.fetch.bind(window);
  const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

  window.fetch = function (input, init) {
    const opts = init ? Object.assign({}, init) : {};
    const method = (opts.method || (input && input.method) || 'GET').toUpperCase();

    if (MUTATING_METHODS.has(method)) {
      const token = readCookie('XSRF-TOKEN');
      if (token) {
        const headers = new Headers(opts.headers || (input && input.headers) || {});
        if (!headers.has('X-XSRF-TOKEN')) {
          headers.set('X-XSRF-TOKEN', token);
        }
        opts.headers = headers;
      }
    }

    return originalFetch(input, opts);
  };
})();
