import {api,esc,icon,money,routeHref} from './core.js';

const searchGroups = [['悬赏大厅','bounties','ticket'],['技能服务','services','spark'],['社区','community','chat']];

export async function renderSearch(ctx) {
  const keyword = (ctx.query.get('q') ?? '').trim().slice(0,80);
  if (!ctx.show(`<header class="page-head"><div><h1>全站搜索</h1><p>查找悬赏、技能服务和社区帖子。</p></div></header>
    <form class="panel toolbar global-search" role="search"><label class="field" for="search-keyword">关键词<input id="search-keyword" name="q" value="${esc(keyword)}" maxlength="80" required placeholder="例如：快递、证件照、自习"></label><button class="button" type="submit">${icon('search',18)}搜索</button></form>
    <div id="search-results" class="search-groups" aria-live="polite"><p class="empty" role="status">${keyword ? '搜索中…' : '输入关键词查看结果。'}</p></div>`)) return;
  const form = ctx.main.querySelector('form');
  form.addEventListener('submit',event => {
    event.preventDefault();
    const q = String(new FormData(form).get('q') ?? '').trim();
    if (q) {
      const target = '#/search?' + new URLSearchParams({q});
      if (location.hash === target) ctx.refresh();
      else location.hash = target;
    } else form.elements.q.focus();
  });
  if (!keyword) {
    form.elements.q.focus({preventScroll:true});
    return;
  }
  const result = await api('/api/search?' + new URLSearchParams({keyword}),{signal:ctx.signal});
  if (ctx.signal.aborted) return;
  const tradePosts = Array.isArray(result?.tradePosts) ? result.tradePosts : [];
  const groups = [tradePosts.filter(row => row.type==='BOUNTY'),tradePosts.filter(row => row.type==='SERVICE'),Array.isArray(result?.posts) ? result.posts : []];
  const results = ctx.main.querySelector('#search-results');
  if (groups.every(group => !group.length)) {
    results.innerHTML = `<section class="panel empty"><h2>未找到「${esc(keyword)}」</h2>
      <p>可以缩短关键词，或试试「快递」「自习」这类常用词。</p>
      <button class="button secondary" type="button">修改关键词</button></section>`;
    results.querySelector('button').addEventListener('click', () => {
      form.elements.q.focus();
      form.elements.q.select();
    });
    return;
  }

  results.innerHTML = searchGroups.map(([label,destination,glyph],index) => {
    const entries = groups[index];
    if (!entries.length) return '';
    return `<section class="panel"><div class="toolbar"><h2>${icon(glyph,19)} ${label}</h2><span class="badge">${entries.length} 条</span></div>
    ${entries.map(item => `<a class="list-row" href="${routeHref(index===2 ? 'post' : 'trade',item.id)}"><span><strong>${esc(index===2 ? item.content : item.title)}</strong>${index===2 ? '' : `<small class="muted">${esc(item.category)} · ${money(item.price)}</small>`}</span>${icon('right',16)}</a>`).join('')}
    <a class="button quiet" href="${routeHref(destination)}">浏览${label}${icon('right',15)}</a></section>`;
  }).join('');
}
