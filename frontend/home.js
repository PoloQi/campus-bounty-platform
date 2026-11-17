import {api, esc, icon, money, dateTime, routeHref} from './core.js';

function listingLink(item, fallback) {
  return item.id == null ? routeHref(fallback) : routeHref(fallback === 'community' ? 'post' : 'trade', item.id);
}

function deadline(item) {
  return item.deadline ? dateTime(item.deadline) + ' 截止' : '';
}

function sectionList(kind, result) {
  const community = kind === 'community';
  const name = community ? '社区' : '技能服务';
  const items = result.items.slice(0,3);
  const entries = items.map(item => {
    const title = community ? item.content : item.title;
    const line = String(title ?? '').trim().split(/\r?\n/,1)[0];
    const meta = community
      ? [item.authorNickname || '校园同学',item.createdAt ? dateTime(item.createdAt) : ''].filter(Boolean).join(' · ')
      : item.category;
    return `<a class="quick-item" href="${listingLink(item,kind)}"><span><b>${esc(line)}</b><small>${esc(meta)}</small></span>${community ? icon('upRight',16) : '<strong>'+money(item.price)+'</strong>'}</a>`;
  }).join('');
  return `<section class="quick-section" aria-labelledby="home-${kind}"><div class="aside-head"><span class="aside-icon">${icon(community ? 'chat' : 'spark',17)}</span><h2 id="home-${kind}">${name}</h2><a class="more-link" aria-label="查看更多${name}" href="${routeHref(kind)}">查看更多${icon('right',14)}</a></div><div>${entries || `<p class="home-empty">${result.failed ? '暂时无法加载，请稍后查看。' : '暂时还没有发布内容。'}</p>`}</div></section>`;
}

function boardRows(items) {
  const tiles = ['parcel','design','calc'];
  return items.slice(0,3).map((item,i) => `<a class="bounty-row" href="${listingLink(item,'bounties')}"><span class="tile tile-${i}">${icon(tiles[i],24)}</span><span class="row-details"><b>${esc(item.title)}</b><small>${esc([item.category,deadline(item)].filter(Boolean).join(' · '))}</small></span><span class="row-money"><strong>${money(item.price)}</strong><small>待接单</small></span>${icon('right',17)}</a>`).join('');
}

function feature(item, failed) {
  if (!item) return `<div class="paper paper-empty"><div class="paper-main"><span class="paper-label">${icon('ticket',16)} 校园悬赏</span><h2>${failed ? '暂时无法加载悬赏' : '暂无待接单悬赏'}</h2><p>${failed ? '可以前往悬赏大厅重试。' : '把你的需求交给有空的同学。'}</p><a class="paper-link" href="#/bounties">进入悬赏大厅 ${icon('right',17)}</a></div><div class="paper-value">${icon('ticket',48)}</div></div>`;
  const reachesSoon = item.deadline && new Date(item.deadline).getTime() <= Date.now()+86400000 && new Date(item.deadline).getTime() >= Date.now();
  return `<div class="paper"><div class="paper-main"><span class="paper-label">${icon('clock',16)} ${reachesSoon ? '即将截止' : '待接单'}</span><h2>${esc(item.title)}</h2><p>${icon('clock',15)}${esc(deadline(item))}</p><a class="paper-link" href="${listingLink(item,'bounties')}">查看悬赏 ${icon('right',17)}</a></div><div class="paper-value"><span>赏金</span><strong class="${money(item.price).length > 5 ? 'long-price' : ''}"><small>¥</small>${esc(money(item.price).slice(1))}</strong><span>已托管</span></div></div>`;
}

export async function renderHome(ctx) {
  if (!ctx.user) return renderGuestHome(ctx);
  const datasets = await Promise.all([
    '/api/trade/posts?type=BOUNTY','/api/trade/posts?type=SERVICE','/api/community/posts',
  ].map(async path => {
    try {
      const items = await api(path, {signal:ctx.signal});
      return {items:Array.isArray(items) ? items : []};
    } catch (error) {
      if (error.name === 'AbortError' || error.status === 401) throw error;
      return {items:[],failed:true};
    }
  }));
  const [bounties,services,posts] = datasets;
  const categories = Array.from(new Set(bounties.items.map(item => item.category).filter(Boolean))).slice(0,5);
  const boardEmpty = bounties.failed ? '暂时无法加载悬赏，请稍后查看。' : '暂无待接单悬赏。';
  const first = bounties.items[0];
  if (!ctx.show(`<div class="home">
    <section class="intro" aria-labelledby="intro-title">
      <div class="pitch"><h1 id="intro-title">有事需要帮忙？<br>来发个悬赏。</h1><p>把需求和赏金说清楚，等同学来接单。</p><a class="cta" href="#/publish-bounty">${icon('plus',20)}发布悬赏</a></div>
      ${feature(first,bounties.failed)}
    </section>
    <div class="home-grid">
      <section aria-labelledby="market-title">
        <div class="section-heading"><h2 id="market-title">悬赏大厅</h2><div class="section-actions">${bounties.failed ? '' : `<span>${bounties.items.length} 条待接单</span>`}<a class="more-link" aria-label="查看更多悬赏大厅" href="#/bounties">查看更多${icon('right',14)}</a></div></div>
        <div class="filter-row" aria-label="筛选悬赏分类"><button type="button" data-category="" aria-pressed="true">全部</button>${categories.map(category=>`<button type="button" data-category="${esc(category)}" aria-pressed="false">${esc(category)}</button>`).join('')}</div>
        <div class="bounty-list">${boardRows(bounties.items) || `<p class="home-empty">${boardEmpty}</p>`}</div>
      </section>
      <aside class="nearby" aria-label="技能与社区">${sectionList('services',services)}${sectionList('community',posts)}</aside>
    </div></div>`)) return;
  ctx.main.querySelector('.filter-row').addEventListener('click', event => {
    const button = event.target.closest('[data-category]');
    if (!button) return;
    ctx.main.querySelectorAll('[data-category]').forEach(filter => filter.setAttribute('aria-pressed',filter===button));
    const category = button.dataset.category;
    ctx.main.querySelector('.bounty-list').innerHTML = boardRows(bounties.items.filter(item => !category || item.category === category)) || `<p class="home-empty">${boardEmpty}</p>`;
  });
}

export async function renderGuestHome(ctx) {
  if (!ctx.show(`<div class="guest-home">
<section class="hero" aria-labelledby="hero-title">
<div class="pitch">
<h1 id="hero-title">在校园，<br>找同学搭把手。</h1>
<p>发悬赏找人帮忙，分享你的技能，也能在社区找搭子。</p>
<div class="hero-actions">
<a class="action primary" href="/#/login">登录进入平台<svg aria-hidden="true" width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12h15m-6-6 6 6-6 6"/></svg></a>
<a class="action secondary" href="/#/register">注册账号</a>
</div></div>
<aside class="ticket" aria-labelledby="ticket-title">
<div class="steps"><h2 id="ticket-title">悬赏怎么用</h2>
<ol><li><span class="step-number" aria-hidden="true">1</span>说明需求和赏金</li><li><span class="step-number" aria-hidden="true">2</span>同学接单并交付</li><li><span class="step-number" aria-hidden="true">3</span>验收后确认完成</li></ol></div>
<div class="stub"><svg width="29" height="29" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.55" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M21 10h-6a2 2 0 0 0 0 4h6m-5-2h.01"/></svg><strong>赏金托管</strong><span>确认后结算</span></div>
</aside>
</section>
<section class="features" aria-labelledby="features-title">
<h2 id="features-title">在这里可以做什么？</h2>
<div class="grid">
<article class="card"><span class="tile"><svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3 8V6h18v2a4 4 0 0 0 0 8v2H3v-2a4 4 0 0 0 0-8ZM15 6v3m0 3v1m0 2v3"/></svg></span><div><h3>发悬赏</h3><p>把需要帮忙的事发出来，等有空的同学接单。</p></div></article>
<article class="card"><span class="tile"><svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" aria-hidden="true"><path d="M12 2.5 14.5 9.5 21.5 12 14.5 14.5 12 21.5 9.5 14.5 2.5 12 9.5 9.5Z"/></svg></span><div><h3>找技能</h3><p>找会辅导、摄影或设计的同学，也可以挂牌自己的技能。</p></div></article>
<article class="card"><span class="tile"><svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20.8 11.3c0 4.3-3.9 7.8-8.8 7.8a10 10 0 0 1-3.6-.7L4 20l1.3-3.7a7.2 7.2 0 0 1-2.1-5c0-4.3 3.9-7.8 8.8-7.8s8.8 3.5 8.8 7.8Zm-13 0h.01m4.1 0h.01m4 0h.01"/></svg></span><div><h3>逛社区</h3><p>分享校园日常，找一起运动或自习的搭子。</p></div></article>
</div>
</section>
<section class="bounty-strip" aria-labelledby="strip-title">
  <div class="strip-head"><h2 id="strip-title">待接单悬赏</h2>
    <button type="button" class="strip-pause" aria-controls="bounty-title-lane" hidden>暂停滚动</button>
  </div>
  <p class="strip-status" role="status">正在读取悬赏大厅…</p>
  <div class="strip-lane" id="bounty-title-lane" hidden>
    <div class="strip-track"><ul class="strip-group"></ul><ul class="strip-group strip-copy" aria-hidden="true"></ul></div>
  </div>
</section>
</div>`)) return;

  const strip = ctx.main.querySelector('.bounty-strip');
  const status = strip.querySelector('.strip-status');
  const lane = strip.querySelector('.strip-lane');
  const track = strip.querySelector('.strip-track');
  const [primary, duplicate] = strip.querySelectorAll('.strip-group');
  const control = strip.querySelector('.strip-pause');
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  let paused = false;

  function updateMotion() {
    const still = paused || motion.matches;
    control.hidden = lane.hidden || motion.matches;
    control.textContent = paused ? '继续滚动' : '暂停滚动';
    lane.classList.toggle('is-still',still);
    if (still) lane.tabIndex = 0;
    else lane.removeAttribute('tabindex');
  }

  try {
    const data = await api('/api/public/bounty-titles', {signal:ctx.signal});
    if (ctx.signal.aborted) return;
    if (!Array.isArray(data)) throw new Error('悬赏标题格式无效');
    const titles = data.filter(title => typeof title === 'string' && title.trim());
    if (!titles.length) {
      status.textContent = '暂时没有可展示的待接单悬赏。';
      return;
    }

    for (const title of titles) {
      const item = document.createElement('li');
      item.className = 'strip-title';
      item.textContent = title;
      item.title = title;
      primary.append(item);
      duplicate.append(item.cloneNode(true));
    }
    status.hidden = true;
    lane.hidden = false;
    const resize = new ResizeObserver(() => {
      track.style.setProperty('--strip-width', lane.clientWidth + 'px');
      const stride = primary.getBoundingClientRect().width;
      track.style.setProperty('--strip-stride', stride + 'px');
      track.style.setProperty('--strip-duration', Math.max(17, stride / 62) + 's');
    });
    resize.observe(lane);
    ctx.signal.addEventListener('abort', () => resize.disconnect(), {once:true});
    motion.addEventListener('change', updateMotion, {signal:ctx.signal});
    control.addEventListener('click', () => {
      paused = !paused;
      if (!paused) lane.scrollLeft = 0;
      updateMotion();
    }, {signal:ctx.signal});
    updateMotion();
  } catch {
    if (ctx.signal.aborted) return;
    status.textContent = '暂时无法加载悬赏。';
    const retry = document.createElement('button');
    retry.className = 'strip-pause';
    retry.type = 'button';
    retry.textContent = '重试';
    retry.addEventListener('click', ctx.refresh, {signal:ctx.signal});
    status.after(retry);
  }
}
