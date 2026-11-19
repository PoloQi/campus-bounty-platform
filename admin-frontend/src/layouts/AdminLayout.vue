<script setup lang="ts">
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
</script>

<template>
  <div class="admin-shell">
    <AppSidebar />
    <div class="admin-body">
      <AppHeader />
      <main class="admin-main">
        <RouterView v-slot="{ Component }">
          <Transition name="fade" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>
