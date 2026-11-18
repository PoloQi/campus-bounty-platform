import { createApp } from 'vue';
import { createRouter, createWebHistory, RouterView } from 'vue-router';
import App from './App.vue';
import { currentUser } from './core.js';
import { readPage, redirectFor } from './page-route.js';
import { pinia } from './session.js';

const router = createRouter({
  // Hashes identify screens in the existing student and admin entry points.
  history: createWebHistory(),
  routes: [{ path: '/', alias: ['/admin', '/index.html'], component: App }],
});

router.beforeEach(to => redirectFor(readPage(to), currentUser()) || true);

const app = createApp(RouterView).use(pinia).use(router);
router.isReady().then(() => app.mount('#app'));
