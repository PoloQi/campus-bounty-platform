<script setup>
import { computed, createStaticVNode, nextTick, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElButton } from 'element-plus';
import { api, currentUser, sessionToken, storeSession, esc, icon, imageUrl, errorText, routeHref, toast } from './core.js';
import { loginHref, readPage, redirectFor } from './page-route.js';
import { renderHome } from './home.js';
import AuthView from './AuthView.vue';
import SearchView from './SearchView.vue';

const navItems = [['home','首页'],['bounties','悬赏大厅'],['services','技能服务'],['community','社区']];
const accountRoutes = new Set(['personal','profile','wallet','reviews']);
const marketRoutes = new Set(['bounties','services','trade','order','orders','publish-bounty','publish-service']);
const communicationRoutes = new Set(['community','post','publish-post','edit-post','messages','notifications']);
const titles = {home:'首页',bounties:'悬赏大厅',services:'技能服务',community:'社区',trade:'交易详情',order:'订单详情',orders:'我的交易',personal:'个人中心',profile:'个人资料',wallet:'我的钱包',reviews:'同学评价',search:'全站搜索',messages:'私信',notifications:'通知',admin:'管理端',login:'登录',register:'注册', 'publish-bounty':'发布悬赏','publish-service':'挂牌服务','publish-post':'发帖','edit-post':'编辑帖子',post:'帖子详情'};

const router = useRouter();
const route = useRoute();
const view = shallowRef(null);
const context = shallowRef(null);
const main = ref(null);
const userMenu = ref(null);
const headerUser = computed(currentUser);
const unread = ref(0);
const admin = computed(() => view.value?.route === 'admin');
const guestHome = computed(() => !headerUser.value && view.value?.route === 'home');
const displayName = computed(() => headerUser.value?.nickname || headerUser.value?.username || headerUser.value?.displayName || '用户');
const avatarSrc = computed(() => {
  const user = headerUser.value;
  if (!user) return '';
  return imageUrl(Object.hasOwn(user, 'avatar') ? user.avatar : user.avatarUrl);
});
const nativeView = computed(() => ({ search: SearchView, login: AuthView, register: AuthView })[view.value?.route]);
const listeners = new AbortController();

let activeController;
let lastNotificationRequest = 0;
let notificationVersion = 0;
let pageId = 0;
let notificationTimer;

function Glyph({ name, size }) {
  return createStaticVNode(icon(name, size), 1);
}
Glyph.props = ['name', 'size'];

function sectionFor(name) {
  if (navItems.some(([key]) => key === name)) return name;
  if (['post','publish-post','edit-post'].includes(name)) return 'community';
  if (name === 'publish-service') return 'services';
  if (['publish-bounty','trade'].includes(name)) return 'bounties';
  return '';
}

function go(name, ...args) {
  const hash = routeHref(name, ...args);
  if (location.hash === hash || (!location.hash && hash === '#/')) void render();
  else void router.push(location.pathname + location.search + hash);
}

function updateUnreadCount(list) {
  unread.value = Array.isArray(list) ? list.filter(item => !item.read).length : 0;
}

async function refreshNotifications() {
  const token = sessionToken();
  if (!token || currentUser()?.role === 'ADMIN' || Date.now() - lastNotificationRequest < 30000) return;
  const version = ++notificationVersion;
  lastNotificationRequest = Date.now();
  try {
    const list = await api('/api/notify');
    if (version === notificationVersion && token === sessionToken()) {
      updateUnreadCount(list);
      if (Array.isArray(list)) window.dispatchEvent(new CustomEvent('notifications-refresh', {detail:list}));
    }
  } catch { /* The notification page displays its own request status. */ }
}

function setSession(token, user) {
  unread.value = 0;
  lastNotificationRequest = 0;
  notificationVersion++;
  storeSession(token, user);
}

function signOut() {
  setSession('', null);
  if (admin.value) location.replace('/#/');
  else go('home');
}

async function render({ focus = true } = {}) {
  activeController?.abort();
  const controller = new AbortController();
  activeController = controller;
  const { signal } = controller;
  const destination = readPage(route);
  const { route: name, args, query, hash } = destination;
  const user = currentUser();
  const token = sessionToken();
  const redirect = redirectFor(destination, user);
  if (redirect) {
    void router.replace(redirect);
    return;
  }

  const page = reactive({
    id: ++pageId, route: name, hash, section: sectionFor(name), busy: true,
    html: '<p class="empty" role="status">加载中…</p>',
  });
  const ctx = {
    route: name, args, query, user, signal, go,
    ready() { if (!signal.aborted) page.busy = false; },
    async show(html) {
      if (signal.aborted) return false;
      page.html = html;
      page.busy = false;
      await nextTick();
      return !signal.aborted;
    },
    refresh: () => render({focus:false}),
    setSession, signOut,
    syncNotifications(list) {
      if (signal.aborted || !token || token !== sessionToken()) return;
      // The notification page has fresh data, so ignore any poll still in flight.
      notificationVersion++;
      lastNotificationRequest = Date.now();
      updateUnreadCount(list);
    },
    setSection(section) { if (!signal.aborted) page.section = section; },
    updateUser(profile) {
      if (signal.aborted || !token || token !== sessionToken()) return;
      storeSession(token, {...currentUser(), ...profile});
    },
  };

  document.title = (titles[name] || '页面') + ' · 校园悬赏';
  if (userMenu.value?.open) userMenu.value.open = false;
  context.value = ctx;
  view.value = page;
  await nextTick();
  if (signal.aborted) return;
  ctx.main = main.value;
  if (focus) ctx.main.focus({preventScroll:true});
  if (user && name !== 'notifications') void refreshNotifications();
  if (nativeView.value) return;

  try {
    if (name === 'home') await renderHome(ctx);
    else if (name === 'admin') await (await import('./admin.js')).renderAdmin(ctx);
    else if (accountRoutes.has(name)) await (await import('./student.js')).renderStudent(ctx);
    else if (marketRoutes.has(name)) await (await import('./marketplace.js')).renderMarketplace(ctx);
    else if (communicationRoutes.has(name)) await (await import('./communication.js')).renderCommunication(ctx);
    else await ctx.show('<div class="empty"><h1>找不到这个页面</h1><a class="button" href="#/">回到首页</a></div>');
  } catch (error) {
    if (signal.aborted || error.name === 'AbortError') return;
    if (!error.status) console.error(error);
    if (await ctx.show(`<div class="empty"><h1>暂时无法打开页面</h1><p>${esc(errorText(error))}</p><button class="button secondary" type="button" data-retry>重试</button></div>`)) {
      ctx.main.querySelector('[data-retry]')?.addEventListener('click', ctx.refresh);
    }
  }
}

watch(() => route.fullPath, () => {
  window.scrollTo(0, 0);
  void render();
});

watch(sessionToken, (token, _, onCleanup) => {
  const user = currentUser();
  if (!token || user?.role === 'ADMIN') return;
  const request = new AbortController();
  onCleanup(() => request.abort());
  api('/api/users/me', {signal: request.signal}).then(profile => {
    // A profile save replaces the session user while this request can still be in flight.
    if (!profile || request.signal.aborted || token !== sessionToken() || user !== currentUser()) return;
    storeSession(token, {...user, ...profile});
  }).catch(() => { /* The profile page handles errors when it loads. */ });
}, {immediate:true});

onMounted(() => {
  const { signal } = listeners;
  document.querySelector('.skip').addEventListener('click', event => {
    event.preventDefault();
    main.value?.focus();
  }, {signal});
  window.addEventListener('session-expired', () => {
    unread.value = 0;
    void render({focus: !['home','login','register'].includes(readPage(route).route)});
    toast('登录已失效，请重新登录。');
  }, {signal});
  window.addEventListener('focus', refreshNotifications, {signal});
  notificationTimer = window.setInterval(() => {
    if (!document.hidden) refreshNotifications();
  }, 45000);
  document.addEventListener('click', event => {
    const menu = userMenu.value;
    if (menu?.open && (!menu.contains(event.target) || event.target.closest('a'))) menu.open = false;
  }, {signal});
  document.addEventListener('keydown', event => {
    const menu = userMenu.value;
    if (event.key === 'Escape' && menu?.open && menu.contains(document.activeElement)) {
      menu.open = false;
      menu.querySelector('summary').focus();
    }
  }, {signal});

  void render({focus:false});
});

onBeforeUnmount(() => {
  activeController?.abort();
  listeners.abort();
  window.clearInterval(notificationTimer);
});
</script>

<template>
  <template v-if="view">
    <header class="site-header" :class="{'is-guest-home': guestHome}">
      <div class="frame top-inner">
        <a class="brand" :href="admin ? '/admin' : '#/'"><span class="brand-mark"><Glyph name="ticket" :size="24" /></span>校园悬赏<small v-if="admin">管理端</small></a>
        <nav v-if="!admin" class="main-nav" aria-label="主导航">
          <a v-for="[key, label] in (guestHome ? navItems.slice(0, 1) : navItems)" :key="key"
            :data-nav="key" :href="routeHref(key)" :aria-current="view.section === key ? 'page' : null">{{ label }}</a>
        </nav>
        <div v-if="!guestHome" class="header-actions">
          <a v-if="!admin" class="icon-button" href="#/search" aria-label="全站搜索" :aria-current="view.route === 'search' ? 'page' : null"><Glyph name="search" :size="21" /></a>
          <a v-if="!admin" class="icon-button" data-notification-link href="#/notifications"
            :aria-label="unread ? `通知，${unread} 条未读` : '通知'" :aria-current="view.route === 'notifications' ? 'page' : null"><Glyph name="bell" :size="21" /><span class="unread-indicator" aria-hidden="true" :hidden="unread === 0"></span></a>
          <div class="account-slot">
            <a v-if="!headerUser" class="account-login" :href="loginHref(view.hash)">登录</a>
            <template v-else-if="admin">
              <span class="avatar" aria-hidden="true"><img v-if="avatarSrc" alt="" :src="avatarSrc"><template v-else>{{ Array.from(displayName)[0] }}</template></span>
              <ElButton class="button secondary" data-signout native-type="button" @click="signOut">退出登录</ElButton>
            </template>
            <details v-else ref="userMenu" class="user-menu">
              <summary aria-label="打开用户菜单"><span class="avatar" aria-hidden="true"><img v-if="avatarSrc" alt="" :src="avatarSrc"><template v-else>{{ Array.from(displayName)[0] }}</template></span><span class="user-name">{{ displayName }}</span><Glyph name="chevron" :size="15" /></summary>
              <div class="user-dropdown">
                <div class="menu-identity"><strong>{{ displayName }}</strong><small>学生账户</small></div>
                <a href="#/personal"><Glyph name="home" :size="17" />个人中心</a>
                <a href="#/profile"><Glyph name="users" :size="17" />个人资料</a>
                <a href="#/wallet"><Glyph name="wallet" :size="17" />我的钱包</a>
                <a href="#/orders/buyer"><Glyph name="ticket" :size="17" />我的交易</a>
                <a href="#/messages"><Glyph name="chat" :size="17" />私信</a>
                <button type="button" data-signout @click="signOut"><Glyph name="arrow" :size="17" />退出登录</button>
              </div>
            </details>
          </div>
        </div>
      </div>
    </header>

    <main v-if="nativeView" :key="view.id" ref="main" class="frame page" id="main" tabindex="-1" :aria-busy="view.busy">
      <component :is="nativeView" :context="context" />
    </main>
    <!-- Presenters retain the existing page markup and bind their controls after Vue renders it. -->
    <main v-else :key="-view.id" ref="main" class="frame page" id="main" tabindex="-1" :aria-busy="view.busy" v-html="view.html"></main>
  </template>
</template>
