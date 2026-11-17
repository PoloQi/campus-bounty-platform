import {api, currentUser, sessionToken, storeSession, esc, icon, imageUrl, errorText, routeHref, toast} from './core.js';
import {renderHome} from './home.js';

const app = document.querySelector('#app');
const navItems = [['home','首页'],['bounties','悬赏大厅'],['services','技能服务'],['community','社区']];
const publicRoutes = new Set(['home','login','register']);
const accountRoutes = new Set(['login','register','personal','profile','wallet','reviews']);
const marketRoutes = new Set(['bounties','services','trade','order','orders','publish-bounty','publish-service']);
const communicationRoutes = new Set(['community','post','publish-post','edit-post','messages','notifications']);
const titles = {home:'首页',bounties:'悬赏大厅',services:'技能服务',community:'社区',trade:'交易详情',order:'订单详情',orders:'我的交易',personal:'个人中心',profile:'个人资料',wallet:'我的钱包',reviews:'同学评价',search:'全站搜索',messages:'私信',notifications:'通知',admin:'管理端',login:'登录',register:'注册','publish-bounty':'发布悬赏','publish-service':'挂牌服务','publish-post':'发帖','edit-post':'编辑帖子',post:'帖子详情'};
let activeController;
let unread = 0;
let lastNotificationRequest = 0;
let notificationVersion = 0;

function readRoute() {
  const admin = location.pathname === '/admin' || location.pathname === '/admin/';
  const [path, ...query] = (location.hash.startsWith('#/') ? location.hash.slice(2) : '').split('?');
  const parts = path.split('/').filter(Boolean).map(segment => {
    try { return decodeURIComponent(segment); } catch { return segment; }
  });
  const [route = 'home', ...args] = parts;
  if (admin) return {route:'admin',args:[route],query:new URLSearchParams(query.join('?'))};
  return {route,args,query:new URLSearchParams(query.join('?'))};
}

function go(name, ...args) {
  const hash = routeHref(name,...args);
  if (location.hash === hash || (!location.hash && hash === '#/')) render();
  else location.hash = hash;
}

function loginHref(next) {
  return '#/login?next=' + encodeURIComponent(next || '#/');
}

function sectionFor(route) {
  if (['home','bounties','services','community'].includes(route)) return route;
  if (['post','publish-post','edit-post'].includes(route)) return 'community';
  if (route === 'publish-service') return 'services';
  if (['publish-bounty','trade'].includes(route)) return 'bounties';
  return '';
}

function avatar(user) {
  const name = user.nickname || user.displayName || user.username || '用户';
  const src = imageUrl(user.avatarUrl || user.avatar);
  return '<span class="avatar" aria-hidden="true">' + (src ? '<img alt="" src="'+src+'">' : esc(Array.from(name)[0])) + '</span>';
}

function accountMarkup(user, route) {
  if (!user) return `<a class="account-login" href="${loginHref(location.hash)}">登录</a>`;
  const name = user.nickname || user.displayName || user.username || '用户';
  if (route === 'admin') return `${avatar(user)}<button class="button secondary" data-signout type="button">退出登录</button>`;
  return `<details class="user-menu"><summary aria-label="打开用户菜单">${avatar(user)}<span class="user-name">${esc(name)}</span>${icon('chevron',15)}</summary><div class="user-dropdown">
    <div class="menu-identity"><strong>${esc(name)}</strong><small>学生账户</small></div>
    <a href="#/personal">${icon('home',17)}个人中心</a>
    <a href="#/profile">${icon('users',17)}个人资料</a>
    <a href="#/wallet">${icon('wallet',17)}我的钱包</a>
    <a href="#/orders/buyer">${icon('ticket',17)}我的交易</a>
    <a href="#/messages">${icon('chat',17)}私信</a>
    <button type="button" data-signout>${icon('arrow',17)}退出登录</button>
  </div></details>`;
}

function renderShell(route, user) {
  const admin = route === 'admin';
  const guestHome = !user && route === 'home';
  const visibleNav = guestHome ? navItems.slice(0,1) : navItems;
  app.innerHTML = `<header class="site-header${guestHome ? ' is-guest-home' : ''}"><div class="frame top-inner">
    <a class="brand" href="${admin ? '/admin' : '#/'}"><span class="brand-mark">${icon('ticket',24)}</span>校园悬赏${admin ? '<small>管理端</small>' : ''}</a>
    ${admin ? '' : `<nav class="main-nav" aria-label="主导航">${visibleNav.map(([key,label]) => `<a data-nav="${key}" href="${routeHref(key)}"${sectionFor(route)===key ? ' aria-current="page"' : ''}>${label}</a>`).join('')}</nav>`}
    ${guestHome ? '' : `<div class="header-actions">
      ${admin ? '' : `<a class="icon-button" href="#/search" aria-label="全站搜索"${route === 'search' ? ' aria-current="page"' : ''}>${icon('search',21)}</a>
      <a class="icon-button" data-notification-link href="#/notifications" aria-label="通知"${route === 'notifications' ? ' aria-current="page"' : ''}>${icon('bell',21)}<span class="unread-indicator" aria-hidden="true" hidden></span></a>`}
      <div class="account-slot">${accountMarkup(user, route)}</div>
    </div>`}
  </div></header><main class="frame page" id="main" tabindex="-1" aria-busy="true"><p class="empty" role="status">加载中…</p></main>`;
  paintNotificationCount();
}

function markSection(section) {
  app.querySelectorAll('[data-nav]').forEach(item => {
    if (item.dataset.nav === section) item.setAttribute('aria-current','page');
    else item.removeAttribute('aria-current');
  });
}

function paintNotificationCount() {
  const link = app.querySelector('[data-notification-link]');
  if (!link) return;
  const badge = link.querySelector('.unread-indicator');
  badge.hidden = unread === 0;
  link.setAttribute('aria-label', unread ? `通知，${unread} 条未读` : '通知');
}

async function refreshNotifications(force = true) {
  const token = sessionToken();
  if (!token || currentUser()?.role === 'ADMIN' || (!force && Date.now()-lastNotificationRequest < 30000)) return;
  const version = ++notificationVersion;
  lastNotificationRequest = Date.now();
  try {
    const list = await api('/api/notify');
    if (version === notificationVersion && token === sessionToken()) {
      unread = Array.isArray(list) ? list.filter(item => !item.read).length : 0;
      paintNotificationCount();
      if (Array.isArray(list)) window.dispatchEvent(new CustomEvent('notifications-refresh', {detail:list}));
    }
  } catch { /* The notification page displays its own request status. */ }
}

function setSession(token, user) {
  unread = 0;
  lastNotificationRequest = 0;
  notificationVersion++;
  storeSession(token,user);
}

function signOut() {
  setSession('',null);
  if (location.pathname.startsWith('/admin')) location.replace('/#/');
  else go('home');
}

async function render({focus = true} = {}) {
  activeController?.abort();
  const controller = new AbortController();
  activeController = controller;
  const {signal} = controller;
  const {route,args,query} = readRoute();
  const user = currentUser();
  if (route === 'admin' && user?.role !== 'ADMIN') {
    location.replace(user ? '/#/' : '/#/login');
    return;
  }
  if (route !== 'admin' && user?.role === 'ADMIN') {
    location.replace('/admin');
    return;
  }
  if (!user && !publicRoutes.has(route)) {
    location.replace(loginHref(location.hash));
    return;
  }
  document.title = (titles[route] || '页面') + ' · 校园悬赏';
  renderShell(route,user);
  const main = app.querySelector('main');
  if (focus) main.focus({preventScroll:true});
  const ctx = {
    route,args,query,main,user,signal,go,
    show(html) {
      if (signal.aborted) return false;
      main.innerHTML = html;
      main.setAttribute('aria-busy','false');
      return true;
    },
    refresh: () => render({focus:false}),
    setSession, signOut, refreshNotifications, setSection:markSection,
    updateUser(profile) {
      if (signal.aborted || !sessionToken()) return;
      storeSession(sessionToken(), {...currentUser(),...profile});
      app.querySelector('.account-slot').innerHTML = accountMarkup(currentUser(),route);
    },
  };
  if (user) void refreshNotifications(false);
  try {
    if (route === 'home') await renderHome(ctx);
    else if (route === 'search') await (await import('./discovery.js')).renderSearch(ctx);
    else if (route === 'admin') await (await import('./admin.js')).renderAdmin(ctx);
    else if (accountRoutes.has(route)) await (await import('./student.js')).renderStudent(ctx);
    else if (marketRoutes.has(route)) await (await import('./marketplace.js')).renderMarketplace(ctx);
    else if (communicationRoutes.has(route)) await (await import('./communication.js')).renderCommunication(ctx);
    else ctx.show('<div class="empty"><h1>找不到这个页面</h1><a class="button" href="#/">回到首页</a></div>');
  } catch (error) {
    if (signal.aborted || error.name === 'AbortError') return;
    if (!error.status) console.error(error);
    ctx.show(`<div class="empty"><h1>暂时无法打开页面</h1><p>${esc(errorText(error))}</p><button class="button secondary" type="button" data-retry>重试</button></div>`);
    main.querySelector('[data-retry]')?.addEventListener('click',ctx.refresh);
  }
}

document.querySelector('.skip').addEventListener('click',event => {
  event.preventDefault();
  app.querySelector('main')?.focus();
});
window.addEventListener('hashchange', () => {
  window.scrollTo(0,0);
  render();
});
window.addEventListener('session-expired', () => {
  unread = 0;
  paintNotificationCount();
  if (!publicRoutes.has(readRoute().route)) render();
  else render({focus:false});
  toast('登录已失效，请重新登录。');
});
window.addEventListener('focus', () => refreshNotifications(false));
window.setInterval(() => {
  if (!document.hidden) refreshNotifications(false);
}, 45000);
app.addEventListener('click', event => {
  const menu = app.querySelector('.user-menu[open]');
  if (event.target.closest('[data-signout]')) signOut();
  else if (menu && (!menu.contains(event.target) || event.target.closest('a'))) menu.open = false;
});
document.addEventListener('click', event => {
  const menu = app.querySelector('.user-menu[open]');
  if (menu && !menu.contains(event.target)) menu.open = false;
});
document.addEventListener('keydown',event => {
  const menu = app.querySelector('.user-menu[open]');
  if (event.key === 'Escape' && menu && menu.contains(document.activeElement)) {
    menu.open = false;
    menu.querySelector('summary').focus();
  }
});
render({focus:false});
const initialToken = sessionToken();
if (initialToken && currentUser()?.role !== 'ADMIN') {
  api('/api/users/me').then(profile => {
    if (!profile || initialToken !== sessionToken()) return;
    storeSession(initialToken,{...currentUser(),...profile});
    const slot = app.querySelector('.account-slot');
    if (slot && !slot.querySelector('[open]') && !slot.contains(document.activeElement)) {
      slot.innerHTML = accountMarkup(currentUser(),readRoute().route);
    }
  }).catch(() => {});
}
