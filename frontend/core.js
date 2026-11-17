const sessionKey = 'campus-bounty-session';
let session;
try {
  const saved = JSON.parse(sessionStorage.getItem(sessionKey));
  if (typeof saved?.token === 'string') {
    const {token, user, ...legacy} = saved;
    session = {token,user:user ?? legacy};
  }
} catch { session = null; }

export function sessionToken() { return session?.token ?? ''; }
export function currentUser() { return session?.user ?? null; }

export function storeSession(token, user) {
  session = token ? {token,user:{...user,id:user?.id ?? user?.userId}} : null;
  try {
    if (session) sessionStorage.setItem(sessionKey, JSON.stringify(session));
    else sessionStorage.removeItem(sessionKey);
  } catch { /* Session stays available for the current page. */ }
}

export function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => ({
    '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;',
  })[ch]);
}

export function routeHref(name, ...args) {
  return '#/' + [name === 'home' ? '' : name, ...args].filter(part => part != null && part !== '').map(part => encodeURIComponent(part)).join('/');
}

export function safeReturnRoute(value) {
  if (typeof value !== 'string' || !/^#\/[a-zA-Z0-9%?&=+._~/#-]*$/.test(value) || /^#\/(?:\/|login(?:[/?#]|$)|register(?:[/?#]|$)|admin(?:[/?#]|$))/.test(value)) return '#/';
  return value;
}

export class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export async function api(path, {body, headers, signal, method = 'GET'} = {}) {
  if (!path.startsWith('/api/')) throw new TypeError('接口地址无效');
  const requestHeaders = new Headers(headers);
  const token = sessionToken();
  if (token) requestHeaders.set('Authorization', 'Bearer ' + token);
  if (body != null && !(body instanceof FormData)) {
    requestHeaders.set('Content-Type', 'application/json');
    body = typeof body === 'string' ? body : JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch(path, {method, headers:requestHeaders, body, signal});
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new ApiError('暂时无法连接服务，请稍后重试。', 0);
  }
  const result = await response.json().catch(() => null);
  if (response.status === 401 && token && token === sessionToken() && !path.startsWith('/api/auth/')) {
    storeSession('', null);
    window.dispatchEvent(new Event('session-expired'));
  }
  if (!response.ok || !result || Number(result.code) !== 0) {
    throw new ApiError(result?.message ?? '请求失败，请稍后重试。', response.status, result?.code);
  }
  return result.data;
}

export function errorText(error) {
  return error instanceof ApiError ? error.message : '操作未完成，请重试。';
}

const currency = new Intl.NumberFormat('zh-CN', {maximumFractionDigits:2});
export function money(value) {
  const amount = Number(value);
  if (value == null || !Number.isFinite(amount)) return '—';
  return (amount < 0 ? '-¥' : '¥') + currency.format(Math.abs(amount));
}

const dateFormat = new Intl.DateTimeFormat('zh-CN', {
  year:'numeric', month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit', hour12:false,
});
export function dateTime(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateFormat.format(date);
}

export function imageUrl(value) {
  return typeof value === 'string' && /^\/uploads\/[a-z0-9._-]+$/i.test(value) ? value : '';
}

export async function uploadImage(file, options = {}) {
  const body = new FormData();
  body.set('file', file);
  const signal = options instanceof AbortSignal ? options : options?.signal;
  const data = await api('/api/community/upload', {method:'POST',body,signal});
  const path = imageUrl(typeof data === 'string' ? data : data?.url);
  if (!path) throw new ApiError('图片上传响应无效。', 502);
  return path;
}

let toastTimer;
export function toast(message) {
  const node = document.querySelector('#toast');
  if (!node) return;
  node.textContent = message;
  node.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { node.hidden = true; }, 4000);
}

const paths = {
  home:'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
  ticket:'<path d="M3 8V6h18v2a4 4 0 0 0 0 8v2H3v-2a4 4 0 0 0 0-8Z"/><path d="M15 6v3m0 3v1m0 2v3"/>',
  spark:'<path d="M12 2.5 14.5 9.5 21.5 12 14.5 14.5 12 21.5 9.5 14.5 2.5 12 9.5 9.5Z"/>',
  chat:'<path d="M20.8 11.3c0 4.3-3.9 7.8-8.8 7.8a10 10 0 0 1-3.6-.7L4 20l1.3-3.7a7.2 7.2 0 0 1-2.1-5c0-4.3 3.9-7.8 8.8-7.8s8.8 3.5 8.8 7.8Z"/><path d="M8 11h.01M12 11h.01M16 11h.01"/>',
  search:'<circle cx="10.8" cy="10.8" r="6.3"/><path d="m15.5 15.5 4.3 4.3"/>',
  bell:'<path d="M18 9a6 6 0 0 0-12 0c0 6-2 6-2 8h16c0-2-2-2-2-8m-8 11a2 2 0 0 0 4 0"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  chevron:'<path d="m6 9 6 6 6-6"/>',
  right:'<path d="m9 6 6 6-6 6"/>',
  arrow:'<path d="M4 12h15m-6-6 6 6-6 6"/>',
  upRight:'<path d="M6 18 18 6M8 6h10v10"/>',
  clock:'<circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3 2"/>',
  pin:'<path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 14 0Z"/><circle cx="12" cy="10" r="2.2"/>',
  grid:'<rect x="3.5" y="3.5" width="6.5" height="6.5" rx="1"/><rect x="14" y="3.5" width="6.5" height="6.5" rx="1"/><rect x="3.5" y="14" width="6.5" height="6.5" rx="1"/><rect x="14" y="14" width="6.5" height="6.5" rx="1"/>',
  list:'<path d="M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01"/>',
  filter:'<path d="M4 7h16M7 12h10m-7 5h4"/>',
  heart:'<path d="M12 20 4.9 13a4.3 4.3 0 0 1 6.1-6l1 1 1-1a4.3 4.3 0 0 1 6.1 6Z"/>',
  wallet:'<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M21 10h-6a2 2 0 0 0 0 4h6m-5-2h.01"/>',
  book:'<path d="M12 5.5c-2.8-2-6-2-9-1v14c3-1 6.2-1 9 1 2.8-2 6-2 9-1v-14c-3-1-6.2-1-9 1Zm0 0v14"/>',
  camera:'<path d="M4 7h4l1-2h6l1 2h4a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1Z"/><circle cx="12" cy="13" r="3.2"/>',
  monitor:'<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M12 17v4m-4 0h8"/>',
  parcel:'<path d="m12 3 8.5 4.5v9L12 21l-8.5-4.5v-9L12 3Zm0 9 8.5-4.5M12 12 3.5 7.5M12 12v9m4.2-11.2L7.5 5.4"/>',
  design:'<path d="m5 16-1 4 4-1L20 7l-3-3L5 16Zm9-9 3 3M12 20h8"/>',
  calc:'<rect x="6" y="3" width="12" height="18" rx="2"/><path d="M9 7h6m-6 5h.01m3 0h.01m3 0h.01M9 16h.01m3 0h.01m3 0h.01"/>',
  users:'<circle cx="9" cy="8" r="3"/><path d="M3 20v-2a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v2m1-15a3 3 0 0 1 0 6m5 9v-2a4 4 0 0 0-3-3.9"/>',
  bookmark:'<path d="M6 4h12v17l-6-4-6 4Z"/>',
  calendar:'<rect x="3.5" y="5.5" width="17" height="15" rx="2"/><path d="M7.5 3v5m9-5v5m-13 3h17m-12 4h2m3 0h2"/>',
  check:'<path d="m5 12 4 4 10-10"/>',
  compass:'<circle cx="12" cy="12" r="9"/><path d="m15.7 8.3-2 5.4-5.4 2 2-5.4z"/>',
  menu:'<path d="M4 6h16M4 12h16M4 18h16"/>',
  close:'<path d="m6 6 12 12M6 18 18 6"/>',
  layers:'<path d="m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5m-18 4 9 5 9-5"/>',
  inbox:'<path d="M3 12 6 5h12l3 7v7H3Zm0 0h6l1 3h4l1-3h6"/>',
  star:'<path d="m12 3 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z"/>',
  note:'<path d="M5 3h10l4 4v14H5Zm10 0v5h4m-10 4h6m-6 4h6"/>'
};
export function icon(name,size=20) {
  return '<svg class="ui-icon" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="'+size+'" height="'+size+'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round">'+(paths[name] || paths.right)+'</svg>';
}
