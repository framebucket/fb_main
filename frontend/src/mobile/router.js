import { useEffect, useState } from 'react';

// Hash routes (#/events/12/gallery?folder=abc) so the server only needs to serve /app.
// Android's back button walks the web view history, which these routes live in.

export const TAB_ROOTS = ['/home', '/events', '/enquiries', '/money', '/more'];

export function parseRoute(hash = window.location.hash) {
  const raw = hash.replace(/^#/, '') || '/home';
  const [pathPart, queryPart = ''] = raw.split('?');
  const path = pathPart.startsWith('/') ? pathPart : `/${pathPart}`;
  const parts = path.split('/').filter(Boolean).map((part) => decodeURIComponent(part));
  return { path, parts, query: Object.fromEntries(new URLSearchParams(queryPart)) };
}

// The in-app history, so "back" in the app bar only leaves the current screen, never the app.
const stack = [window.location.hash || '#/home'];
let replacing = false;
let lastDirection = 'none';

window.addEventListener('hashchange', () => {
  const hash = window.location.hash || '#/home';
  if (replacing) {
    stack[stack.length - 1] = hash;
    replacing = false;
    lastDirection = 'none';
  } else if (stack.length > 1 && stack[stack.length - 2] === hash) {
    stack.pop();
    lastDirection = 'back';
  } else {
    stack.push(hash);
    lastDirection = 'forward';
  }
});

export function navDirection() {
  return lastDirection;
}

export function useRoute() {
  const [route, setRoute] = useState(() => parseRoute());
  useEffect(() => {
    const update = () => setRoute(parseRoute());
    window.addEventListener('hashchange', update);
    return () => window.removeEventListener('hashchange', update);
  }, []);
  return route;
}

// Overlays (sheets, viewers) each own one history entry so the back button closes them.
const overlays = [];
let expectedPops = 0;

window.addEventListener('popstate', () => {
  if (expectedPops > 0) {
    expectedPops -= 1;
    return;
  }
  const top = overlays.pop();
  if (top) {
    top.closedByBack = true;
    top.close();
  }
});

export function openOverlay(entry) {
  overlays.push(entry);
  window.history.pushState({ fbOverlay: true }, '', window.location.href);
}

export function closeOverlay(entry) {
  const index = overlays.indexOf(entry);
  if (index >= 0) overlays.splice(index, 1);
  if (!entry.closedByBack) {
    expectedPops += 1;
    window.history.back();
  }
}

export function navigate(path, { replace = false, attempt = 0 } = {}) {
  // Let a closing overlay remove its history entry first, or the route change would be undone.
  if ((expectedPops > 0 || overlays.length > 0) && attempt < 6) {
    window.setTimeout(() => navigate(path, { replace, attempt: attempt + 1 }), 40);
    return;
  }
  const target = `#${path}`;
  if (window.location.hash === target) return;
  if (replace) {
    replacing = true;
    window.location.replace(target);
  } else {
    window.location.hash = path;
  }
}

export function goBack(fallback = '/home') {
  if (expectedPops > 0) {
    window.setTimeout(() => goBack(fallback), 40);
    return;
  }
  if (stack.length > 1) window.history.back();
  else navigate(fallback, { replace: true });
}

export function tabOf(path) {
  return TAB_ROOTS.find((root) => path === root || path.startsWith(`${root}/`))
    || (path.startsWith('/customers') || path.startsWith('/calendar') || path.startsWith('/team') ? '/more' : '/home');
}
