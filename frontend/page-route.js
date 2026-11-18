const publicRoutes = new Set(['home', 'login', 'register']);

export function readPage(route) {
  const hashIndex = route.fullPath.indexOf('#');
  const hash = hashIndex === -1 ? '' : route.fullPath.slice(hashIndex);
  const entry = hashIndex === -1 ? route.fullPath : route.fullPath.slice(0, hashIndex);
  const [path, ...queryParts] = (hash.startsWith('#/') ? hash.slice(2) : '').split('?');
  const parts = path.split('/').filter(Boolean).map(segment => {
    try { return decodeURIComponent(segment); } catch { return segment; }
  });
  const [name = 'home', ...args] = parts;
  const query = new URLSearchParams(queryParts.join('?'));
  if (route.path === '/admin' || route.path === '/admin/') {
    return { route: 'admin', args: [name], query, hash, entry };
  }
  return { route: name, args, query, hash, entry };
}

export function loginHref(next) {
  return '#/login?next=' + encodeURIComponent(next || '#/');
}

export function redirectFor(page, user) {
  if (page.route === 'admin' && user?.role !== 'ADMIN') return user ? '/#/' : '/#/login';
  if (page.route !== 'admin' && user?.role === 'ADMIN') return '/admin';
  if (!user && !publicRoutes.has(page.route)) return page.entry + loginHref(page.hash);
  return null;
}
