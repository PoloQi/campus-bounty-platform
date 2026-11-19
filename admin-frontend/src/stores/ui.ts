import { ref } from 'vue'
import { defineStore } from 'pinia'

const COLLAPSE_KEY = 'campus-bounty-admin-collapsed'

export const useUiStore = defineStore('ui', () => {
  const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')

  function toggleSidebar(): void {
    collapsed.value = !collapsed.value
    localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
  }

  return { collapsed, toggleSidebar }
})
