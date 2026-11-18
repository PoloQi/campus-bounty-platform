import { createPinia, defineStore } from 'pinia';

const sessionKey = 'campus-bounty-session';

function restoreSession() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(sessionKey));
    if (typeof saved?.token === 'string') {
      const { token, user, ...legacy } = saved;
      return { token, user: user ?? legacy };
    }
  } catch { /* The stored session is optional. */ }
  return null;
}

export const pinia = createPinia();

export const useSessionStore = defineStore('session', {
  state: () => ({ session: restoreSession() }),
  getters: {
    token: state => state.session?.token ?? '',
    user: state => state.session?.user ?? null,
  },
  actions: {
    save(token, user) {
      this.session = token ? { token, user: { ...user, id: user?.id ?? user?.userId } } : null;
      try {
        if (this.session) sessionStorage.setItem(sessionKey, JSON.stringify(this.session));
        else sessionStorage.removeItem(sessionKey);
      } catch { /* Session stays available for the current page. */ }
    },
  },
});
