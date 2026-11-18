<script setup>
import {createStaticVNode, nextTick, onMounted, ref, shallowRef} from 'vue';
import {api, errorText, icon, money, routeHref} from './core.js';

const {context} = defineProps({context: {type: Object, required: true}});
const keyword = (context.query.get('q') ?? '').trim().slice(0, 80);
const draft = ref(keyword);
const input = ref(null);
const groups = shallowRef(null);
const failure = shallowRef(null);

function SearchIcon({name, size}) {
  return createStaticVNode(icon(name, size), 1);
}
SearchIcon.props = ['name', 'size'];

function submitSearch(event) {
  const q = String(new FormData(event.currentTarget).get('q') ?? '').trim();
  if (!q) {
    input.value.focus();
    return;
  }

  const target = '#/search?' + new URLSearchParams({q});
  if (location.hash === target) context.refresh();
  else location.hash = target;
}

function editKeyword() {
  input.value.focus();
  input.value.select();
}

onMounted(async () => {
  context.ready();
  if (!keyword) {
    await nextTick();
    if (!context.signal.aborted) input.value?.focus({preventScroll: true});
    return;
  }

  try {
    const query = new URLSearchParams({keyword});
    const result = await api('/api/search?' + query, {signal: context.signal});
    if (context.signal.aborted) return;

    const tradePosts = Array.isArray(result?.tradePosts) ? result.tradePosts : [];
    groups.value = [
      {label: '悬赏大厅', destination: 'bounties', glyph: 'ticket', detail: 'trade', entries: tradePosts.filter(row => row.type === 'BOUNTY')},
      {label: '技能服务', destination: 'services', glyph: 'spark', detail: 'trade', entries: tradePosts.filter(row => row.type === 'SERVICE')},
      {label: '社区', destination: 'community', glyph: 'chat', detail: 'post', entries: Array.isArray(result?.posts) ? result.posts : []},
    ].filter(group => group.entries.length);
  } catch (error) {
    if (context.signal.aborted || error.name === 'AbortError') return;
    if (!error.status) console.error(error);
    failure.value = error;
  }
});
</script>

<template>
  <div v-if="failure" class="empty">
    <h1>暂时无法打开页面</h1>
    <p>{{ errorText(failure) }}</p>
    <button class="button secondary" type="button" data-retry @click="context.refresh()">重试</button>
  </div>
  <template v-else>
    <header class="page-head"><div><h1>全站搜索</h1><p>查找悬赏、技能服务和社区帖子。</p></div></header>
    <form class="panel toolbar global-search" role="search" @submit.prevent="submitSearch">
      <label class="field" for="search-keyword">关键词<input
        id="search-keyword" ref="input" v-model="draft" name="q" maxlength="80" required
        placeholder="例如：快递、证件照、自习"
      ></label>
      <button class="button" type="submit"><SearchIcon name="search" :size="18" />搜索</button>
    </form>
    <div id="search-results" class="search-groups" aria-live="polite">
      <p v-if="groups === null" class="empty" role="status">{{ keyword ? '搜索中…' : '输入关键词查看结果。' }}</p>
      <section v-else-if="!groups.length" class="panel empty">
        <h2>未找到「{{ keyword }}」</h2>
        <p>可以缩短关键词，或试试「快递」「自习」这类常用词。</p>
        <button class="button secondary" type="button" @click="editKeyword">修改关键词</button>
      </section>
      <template v-else>
        <section v-for="group in groups" :key="group.destination" class="panel">
          <div class="toolbar">
            <h2><SearchIcon :name="group.glyph" :size="19" /> {{ group.label }}</h2>
            <span class="badge">{{ group.entries.length }} 条</span>
          </div>
          <a v-for="item in group.entries" :key="item.id" class="list-row" :href="routeHref(group.detail, item.id)">
            <span><strong>{{ group.detail === 'post' ? item.content : item.title }}</strong><small
              v-if="group.detail === 'trade'" class="muted"
            >{{ item.category }} · {{ money(item.price) }}</small></span><SearchIcon name="right" :size="16" />
          </a>
          <a class="button quiet" :href="routeHref(group.destination)">浏览{{ group.label }}<SearchIcon name="right" :size="15" /></a>
        </section>
      </template>
    </div>
  </template>
</template>
