import {
  api, ApiError, esc, icon, money, dateTime, imageUrl, uploadImage, toast, errorText, routeHref,
} from './core.js';

const statuses = {
  OPEN: '开放中', LOCKED: '已锁定', CLOSED: '已关闭',
  PENDING: '待接单', IN_PROGRESS: '进行中', CONFIRMING: '待确认',
  DISPUTED: '申诉处理中', COMPLETED: '已完成', CANCELLED: '已取消', EXPIRED: '接单已截止',
};

const fundMessages = {
  PENDING: '赏金已托管，等待同学接单。',
  IN_PROGRESS: '费用已托管，等待卖家交付。',
  CONFIRMING: '卖家已标记交付，等待买家确认后结算。',
  DISPUTED: '订单申诉处理中，费用仍在托管中。',
  COMPLETED: '订单费用已结算给卖家。',
  CANCELLED: '订单已取消，托管费用已退回买家。',
};

const publishCategories = {
  BOUNTY: ['校园跑腿', '设计制作', '物品借用', '学习辅导', '生活互助', '其他'],
  SERVICE: ['学习辅导', '摄影修图', '设计制作', '生活服务', '其他'],
};

const typeName = type => type === 'SERVICE' ? '技能服务' : '悬赏';
const listRoute = type => type === 'SERVICE' ? 'services' : 'bounties';
const publishRoute = type => type === 'SERVICE' ? 'publish-service' : 'publish-bounty';
const href = (route, ...args) => esc(routeHref(route, ...args));
const deadlineTime = value => value ? Date.parse(value + '+08:00') : NaN;
const deadlineFormat = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hour12: false,
});
const pendingTradeRequests = new Map();

function deadlineLabel(value) {
  const instant = deadlineTime(value);
  return Number.isNaN(instant) ? '—' : deadlineFormat.format(instant);
}

function pendingTradeRequest(storageKey) {
  try {
    return sessionStorage.getItem(storageKey) ?? pendingTradeRequests.get(storageKey);
  } catch {
    return pendingTradeRequests.get(storageKey);
  }
}

function rememberTradeRequest(storageKey, value) {
  if (value) pendingTradeRequests.set(storageKey, value);
  else pendingTradeRequests.delete(storageKey);
  try {
    if (value) sessionStorage.setItem(storageKey, value);
    else sessionStorage.removeItem(storageKey);
  } catch { /* The current page can still retry. */ }
}

function newTradeRequestId() {
  if (crypto.randomUUID) return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-');
}

function tradeRequestRejected(error) {
  return error instanceof ApiError && error.code === 40000 && error.status > 0 && error.status < 500;
}

async function buyService(postId, storageKey) {
  const requestId = pendingTradeRequest(storageKey) ?? newTradeRequestId();
  rememberTradeRequest(storageKey, requestId);
  try {
    return await api(`/api/trade/posts/${postId}/buy`, {
      method: 'POST', headers: {'Idempotency-Key': requestId},
    });
  } catch (error) {
    if (tradeRequestRejected(error)) {
      rememberTradeRequest(storageKey, null);
    }
    throw error;
  }
}

function isExpiredBounty(post) {
  return post?.type === 'BOUNTY' && post.status === 'OPEN' && deadlineTime(post.deadline) <= Date.now();
}

function badge(status) {
  return `<span class="badge market-status" data-status="${esc(status)}">${esc(statuses[status] || status)}</span>`;
}

function link(route, label, className, ...args) {
  return `<a class="${className}" href="${href(route, ...args)}">${label}</a>`;
}

function images(post) {
  return String(post.images || '').split(',').map(path => imageUrl(path.trim())).filter(Boolean);
}

function postImage(url, title, className) {
  return `<img class="${className}" src="${esc(url)}" alt="${esc(title)}的附图" loading="lazy">`;
}

function marketIcon(category, fallback) {
  const glyph = /跑腿|快递/.test(category) ? 'parcel' : /设计|制作/.test(category) ? 'design' :
    /借用/.test(category) ? 'calc' : /学习|辅导/.test(category) ? 'book' :
      /摄影|修图/.test(category) ? 'camera' : fallback;
  return icon(glyph, 21);
}

function marketHref(route, category, keyword) {
  const params = new URLSearchParams();
  if (category) params.set('category', category);
  if (keyword) params.set('keyword', keyword);
  return routeHref(route) + (params.size ? '?' + params : '');
}

function listingCard(post) {
  const service = post.type === 'SERVICE';
  const cover = images(post)[0];
  const media = cover
    ? postImage(cover, post.title, 'market-card-image')
    : `<span class="market-card-icon">${marketIcon(post.category, service ? 'spark' : 'ticket')}</span>`;
  return `<a class="market-card" href="${href('trade', post.id)}">
    ${media}
    <span class="market-card-body">
      <span class="market-card-title">${esc(post.title)}</span>
      <span class="market-card-description">${esc(post.description)}</span>
      <span class="market-card-meta">${esc(post.category)}${post.deadline ? ` · ${esc(deadlineLabel(post.deadline))} 截止` : ''}</span>
    </span>
    <span class="market-card-end"><strong class="market-price">${esc(money(post.price))}</strong><small>${service ? '待下单' : '待接单'}</small></span>
    <span class="market-card-arrow">${icon('upRight', 17)}</span>
  </a>`;
}

async function hall(ctx, type) {
  const service = type === 'SERVICE';
  const section = listRoute(type);
  const keyword = ctx.query.get('keyword')?.trim() || '';
  const category = ctx.query.get('category')?.trim() || '';
  const base = new URLSearchParams({type});
  const filters = new URLSearchParams(base);
  if (keyword) filters.set('keyword', keyword);
  if (category) filters.set('category', category);
  const endpoint = '/api/trade/posts?';
  const allPosts = api(endpoint + base, {signal: ctx.signal});
  const [all, shown] = await Promise.all([
    allPosts,
    keyword || category ? api(endpoint + filters, {signal: ctx.signal}) : allPosts,
  ]);
  const options = [...new Set(all.map(post => post.category).filter(Boolean))];
  if (category && !options.includes(category)) options.unshift(category);
  const categories = [['', '全部'], ...options.map(name => [name, name])];
  const publishLabel = service ? '发布服务' : '发布悬赏';
  if (!await ctx.show(`
    <section class="market-page">
      <div class="market-banner page-head">
        <div>
          <h1>${service ? '技能服务' : '悬赏大厅'}</h1>
          <p>${service ? '找同学提供的技能，按需购买服务。' : '浏览同学发来的求助，挑一件你能帮上的忙。'}</p>
        </div>
        ${link(publishRoute(type), icon('plus', 18) + publishLabel, 'button')}
      </div>
      <section class="market-filters" aria-labelledby="market-heading">
        <div class="market-filter-head">
          <h2 id="market-heading">${service ? '在售服务' : '待接单悬赏'} <span>${shown.length} 条</span></h2>
          <form class="market-search" role="search" aria-label="${service ? '搜索服务' : '搜索悬赏'}">
            <label class="market-visually-hidden" for="market-query">搜索标题</label>
            <input id="market-query" name="keyword" type="search" maxlength="80" placeholder="搜索标题" value="${esc(keyword)}">
            <button class="button secondary" type="submit">${icon('search', 18)}搜索</button>
          </form>
        </div>
        <nav class="market-tabs" aria-label="按分类筛选">
          ${categories.map(([key, label]) => `<a href="${esc(marketHref(section, key, keyword))}" ${key === category ? 'aria-current="page"' : ''}>${esc(label)}</a>`).join('')}
          ${keyword ? `<a href="${esc(marketHref(section, category, ''))}" class="market-clear">清除关键词 ${icon('close', 14)}</a>` : ''}
        </nav>
        ${shown.length
          ? `<div class="market-results ${service ? 'market-services' : ''}">${shown.map(listingCard).join('')}</div>`
          : `<div class="empty">${keyword || category ? '暂无符合条件的内容。' : service ? '暂时没有上架的服务。' : '暂时没有待接单的悬赏。'}
            ${keyword || category ? link(section, '查看全部', 'button secondary') : link(publishRoute(type), publishLabel, 'button secondary')}</div>`}
      </section>
    </section>`)) return;
  ctx.main.querySelector('.market-search').addEventListener('submit', event => {
    event.preventDefault();
    const next = new FormData(event.currentTarget).get('keyword').trim();
    location.hash = marketHref(section, category, next);
  });
}

function tradePostDetails(post) {
  const gallery = images(post);
  return `<article class="panel market-detail-card">
    <div class="market-post-caption">
      <span class="market-card-icon">${marketIcon(post.category, post.type === 'SERVICE' ? 'spark' : 'ticket')}</span>
      <div><p class="market-card-meta">${typeName(post.type)} · ${esc(post.category)}</p><h2>${esc(post.title)}</h2></div>
    </div>
    <p class="market-description">${esc(post.description)}</p>
    ${gallery.length ? `<div class="market-gallery">${gallery.map(url => `<a href="${esc(url)}" target="_blank" rel="noopener noreferrer">${postImage(url, post.title, 'market-detail-image')}</a>`).join('')}</div>` : ''}
    <div class="market-post-facts">
      <span>${icon('users', 17)}发布者 用户 ${esc(post.publisherId)}</span>
      <span>${icon('calendar', 17)}发布于 ${esc(dateTime(post.createdAt))}</span>
      ${post.deadline ? `<span>${icon('clock', 17)}接单截止 ${esc(deadlineLabel(post.deadline))}</span>` : ''}
    </div>
  </article>`;
}

function bindActionButtons(root, actions) {
  for (const button of root.querySelectorAll('[data-market-action]')) {
    const action = actions[button.dataset.marketAction];
    button.addEventListener('click', action);
  }
}

function actionButton(action, label, style = 'secondary') {
  return `<button class="button ${style}" type="button" data-market-action="${action}">${label}</button>`;
}

function actionDialog(ctx, options) {
  const dialog = document.createElement('dialog');
  dialog.className = 'market-dialog';
  dialog.setAttribute('aria-labelledby', 'market-dialog-title');
  dialog.innerHTML = `<form class="stack">
    <h2 id="market-dialog-title">${esc(options.title)}</h2>
    ${options.note ? `<p class="muted">${esc(options.note)}</p>` : ''}
    ${options.deadline ? '<label class="field"><span>新的接单截止时间（北京时间）</span><input name="deadline" type="datetime-local" required aria-describedby="market-dialog-error"></label>' :
      options.field ? `<label class="field"><span>${esc(options.field)}</span><textarea name="content" required maxlength="500" rows="4" aria-describedby="market-dialog-error"></textarea></label>` : ''}
    <p class="form-error" id="market-dialog-error" role="alert" hidden></p>
    <div class="market-actions">
      <button class="button secondary" type="button" data-close>返回</button>
      <button class="button" type="submit">${esc(options.submit || '确认')}</button>
    </div>
  </form>`;
  ctx.main.append(dialog);
  const form = dialog.querySelector('form');
  const submit = form.querySelector('[type="submit"]');
  const back = form.querySelector('[data-close]');
  const error = form.querySelector('[role="alert"]');
  let needsRefresh = false;
  back.addEventListener('click', () => dialog.close());
  dialog.addEventListener('close', () => {
    dialog.remove();
    if (needsRefresh && !ctx.signal.aborted) ctx.refresh();
  }, {once: true});
  dialog.addEventListener('cancel', event => {
    if (back.disabled) event.preventDefault();
  });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (submit.disabled) return;
    const field = form.elements.deadline ?? form.elements.content;
    const value = field?.value.trim();
    field?.removeAttribute('aria-invalid');
    if (field && (!value || (options.deadline && !(deadlineTime(value) > Date.now())))) {
      error.textContent = options.deadline ? '新的接单截止时间必须晚于当前时间。' : '请填写内容。';
      error.hidden = false;
      field.setAttribute('aria-invalid', 'true');
      field.focus();
      return;
    }
    error.hidden = true;
    submit.disabled = true;
    back.disabled = true;
    let result;
    try {
      result = await options.save(value);
    } catch (failure) {
      needsRefresh = failure.status === 409 || /订单状态不允许|不可下单|已下架|已关闭|不存在|不在处理中|接单已截止|已(?:过|超过)接单截止/.test(failure.message);
      error.textContent = errorText(failure);
      error.hidden = false;
      if (needsRefresh) {
        back.textContent = '返回并刷新';
      }
      return;
    } finally {
      submit.disabled = needsRefresh;
      back.disabled = false;
      if (needsRefresh) back.focus();
    }
    if (ctx.signal.aborted || !ctx.main.contains(dialog)) return;
    dialog.close();
    toast(options.success);
    options.done(result);
  });
  dialog.showModal();
}

function tradeConfirmation(ctx, options) {
  return () => actionDialog(ctx, options);
}

async function tradeDetail(ctx, id) {
  const {post, order} = await api('/api/trade/posts/' + encodeURIComponent(id), {signal: ctx.signal});
  const userId = Number(ctx.user.id);
  const own = userId === post.publisherId;
  const involved = order && [order.buyerId, order.sellerId].includes(userId);
  const service = post.type === 'SERVICE';
  const purchaseStorageKey = `campus:pending-purchase:${userId}:${post.id}`;
  const recoverPurchase = service && !own && post.status !== 'OPEN' && Boolean(pendingTradeRequest(purchaseStorageKey));
  const mayBuy = service && !own && post.status === 'OPEN';
  const expired = order?.status === 'PENDING' && isExpiredBounty(post);
  const mayAccept = !service && !own && order?.status === 'PENDING' && post.status === 'OPEN' && !expired;
  if (!await ctx.show(`
    <section class="market-page">
      ${link(listRoute(post.type), '← 返回' + (service ? '技能服务' : '悬赏大厅'), 'market-back')}
      <div class="page-head market-trade-heading"><div><h1>${service ? '服务详情' : '悬赏详情'}</h1><p>${esc(post.title)}</p></div>${badge(expired ? 'EXPIRED' : order?.status || post.status)}</div>
      <div class="market-detail market-trade-detail">
        <aside class="panel market-summary">
          <div>
            <p class="market-card-meta">${service ? '服务价格' : '悬赏金额'}</p>
            <strong class="market-price">${esc(money(post.price))}</strong>
          </div>
          ${mayBuy ? actionButton('buy', '购买服务', '') : ''}
      ${recoverPurchase ? actionButton('buy', '查询下单结果', '') : ''}
          ${mayAccept ? actionButton('accept', '接下悬赏', '') : ''}
          ${expired && !own ? '<p class="muted">已过接单截止时间，等待发布者处理。</p>' : ''}
          ${involved ? link('order', expired ? '处理悬赏' : '查看关联订单', 'button', order.id) : ''}
          ${own ? '<p class="muted">这是你发布的内容。</p>' : link('messages', '联系发布者', 'button secondary', post.publisherId)}
          ${link('reviews', '查看发布者评价', 'button quiet', post.publisherId)}
          ${!own ? actionButton('report', '举报交易帖', 'quiet') : ''}
        </aside>
        ${tradePostDetails(post)}
      </div>
    </section>`)) return;
  ctx.setSection?.(listRoute(post.type));
  const newOrder = result => ctx.go('order', result.id);
  bindActionButtons(ctx.main, {
    buy: tradeConfirmation(ctx, {
      title: recoverPurchase ? '查询下单结果' : '购买服务',
      note: recoverPurchase ? '查询上一次下单请求的结果。' : `下单后将从可用余额托管 ${money(post.price)}。`,
      submit: recoverPurchase ? '查询' : '确认下单',
      success: recoverPurchase ? '已找到订单' : '已创建订单',
      save: () => buyService(post.id, purchaseStorageKey),
      done: result => {
        rememberTradeRequest(purchaseStorageKey, null);
        newOrder(result);
      },
    }),
    accept: tradeConfirmation(ctx, {
      title: '接下悬赏', note: '确认你可以按发布者的要求完成这项悬赏。', submit: '确认接单', success: '已接下悬赏',
      save: () => api(`/api/trade/orders/${order?.id}/accept`, {method: 'POST'}), done: newOrder,
    }),
    report: async () => {
      try {
        const {openReport} = await import('./communication.js');
        if (!ctx.signal.aborted) openReport('TRADE_POST', post.id);
      } catch (failure) {
        toast(errorText(failure));
      }
    },
  });
}

async function publish(ctx, type) {
  const service = type === 'SERVICE';
  const publishLabel = service ? '发布服务' : '发布悬赏';
  if (!await ctx.show(`
    <section class="market-page">
      ${link(listRoute(type), '← 返回' + (service ? '技能服务' : '悬赏大厅'), 'market-back')}
      <div class="page-head"><div><h1>${publishLabel}</h1><p class="muted">${service ? '说明你可以提供的服务和交付内容。' : '说清楚你需要的帮助、赏金和接单截止时间。'}</p></div>
        ${link(service ? 'publish-bounty' : 'publish-service', service ? '发布悬赏' : '发布服务', 'button secondary')}
      </div>
      <form class="panel market-form stack">
        <label class="field"><span>标题</span><input name="title" maxlength="80" required></label>
        <div class="fields market-form-fields">
          <label class="field"><span>分类</span><input name="category" list="market-categories" maxlength="30" required></label>
          <label class="field"><span>${service ? '价格' : '赏金'}（元）</span><input name="price" type="number" min="0.01" max="99999999.99" step="0.01" inputmode="decimal" required></label>
        </div>
        <datalist id="market-categories">${publishCategories[type].map(category => `<option value="${esc(category)}"></option>`).join('')}</datalist>
        ${service ? '' : '<label class="field"><span>接单截止时间（北京时间）</span><input name="deadline" type="datetime-local" required></label>'}
        <label class="field"><span>${service ? '服务描述' : '需求描述'}</span><textarea name="description" maxlength="1000" rows="6" required></textarea></label>
        <label class="field"><span>图片（选填）</span><input name="images" type="file" accept=".jpg,.jpeg,.png,.gif,.webp" multiple aria-describedby="market-image-help"></label>
        <p id="market-image-help" class="muted">JPG、PNG、GIF 或 WebP，每张不超过 5 MB。</p>
        ${service ? '' : `<p class="market-fund-note">${icon('wallet', 17)}发布后将托管对应赏金。 ${link('wallet', '查看钱包', 'market-inline')}</p>`}
        ${service ? '' : '<p class="muted" data-publish-pending role="status" hidden>上次发布结果待确认。重试会沿用原请求，只冻结一次赏金。</p>'}
        <p class="form-error" role="alert" hidden></p>
        <div class="market-actions"><button class="button" type="submit">${publishLabel}</button></div>
      </form>
    </section>`)) return;
  const form = ctx.main.querySelector('.market-form');
  const input = form.elements.images;
  const submit = form.querySelector('[type="submit"]');
  const error = form.querySelector('[role="alert"]');
  const uploads = new Map();
  const storageKey = `campus:pending-bounty:${ctx.user.id}`;
  const pendingNote = form.querySelector('[data-publish-pending]');
  let pending = null;
  if (!service) {
    try {
      pending = JSON.parse(pendingTradeRequest(storageKey) ?? 'null');
    } catch { /* A missing or unreadable request leaves an empty form. */ }
    if (pending) {
      for (const name of ['title', 'category', 'price', 'deadline', 'description']) {
        form.elements[name].value = pending.payload[name];
      }
    }
  }

  function showPending() {
    if (service) return;
    pendingNote.hidden = !pending;
    submit.textContent = pending ? '重试发布' : publishLabel;
    for (const field of form.elements) {
      if (field !== submit) field.disabled = Boolean(pending);
    }
  }

  showPending();
  input.addEventListener('change', () => uploads.clear());

  async function preparePayload() {
    const payload = Object.fromEntries(new FormData(form));
    const files = [...input.files];
    input.disabled = true;
    payload.title = payload.title.trim();
    payload.category = payload.category.trim();
    payload.description = payload.description.trim();
    if (![payload.title, payload.category, payload.description].every(Boolean)) {
      throw new ApiError('请填写标题、分类和描述。');
    }
    if (!service && !(deadlineTime(payload.deadline) > Date.now())) {
      form.elements.deadline.focus();
      throw new ApiError('接单截止时间必须晚于当前时间。');
    }
    if (files.some(file => !file.size || file.size > 5 * 1024 * 1024 || !/\.(jpg|jpeg|png|gif|webp)$/i.test(file.name))) {
      throw new ApiError('请上传 JPG、PNG、GIF 或 WebP 图片，每张不超过 5 MB。');
    }
    const paths = [];
    for (const [index, file] of files.entries()) {
      if (ctx.signal.aborted) return;
      if (!uploads.has(index)) uploads.set(index, await uploadImage(file));
      paths.push(uploads.get(index));
    }
    payload.images = paths.join(',');
    if (payload.images.length > 500) throw new ApiError('图片地址总长度超限，请减少图片。');
    payload.price = Number(payload.price);
    if (!service && payload.deadline.length === 16) payload.deadline += ':00';
    return payload;
  }

  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (submit.disabled) return;
    submit.disabled = true;
    error.hidden = true;
    try {
      const payload = pending?.payload ?? await preparePayload();
      if (ctx.signal.aborted) return;
      if (!service && !pending) {
        pending = {requestId: newTradeRequestId(), payload};
        rememberTradeRequest(storageKey, JSON.stringify(pending));
        showPending();
      }
      const headers = pending ? {'Idempotency-Key': pending.requestId} : undefined;
      const id = await api(service ? '/api/trade/services' : '/api/trade/posts', {
        method: 'POST', body: payload, headers,
      });
      if (ctx.signal.aborted) return;
      if (pending) rememberTradeRequest(storageKey, null);
      toast('发布成功');
      ctx.go('trade', id);
    } catch (failure) {
      if (ctx.signal.aborted) return;
      if (pending && tradeRequestRejected(failure)) {
        rememberTradeRequest(storageKey, null);
        pending = null;
        showPending();
      }
      error.textContent = errorText(failure);
      error.hidden = false;
    } finally {
      submit.disabled = false;
      input.disabled = Boolean(pending);
    }
  });
}

const orderTabs = [
  ['buyer', '我发的订单', 'asBuyer'],
  ['seller', '我接的单', 'asSeller'],
  ['published', '我发布的', 'published'],
];

async function myOrders(ctx) {
  const data = await api('/api/trade/orders/mine', {signal: ctx.signal});
  const current = orderTabs.find(([key]) => key === ctx.args[0]) || orderTabs[0];
  const published = current[0] === 'published';
  const items = data[current[2]];
  const pageSize = 12;
  const pages = Math.max(1, Math.ceil(items.length / pageSize));
  const page = Math.min(pages, Math.max(1, Math.floor(Number(ctx.query.get('page'))) || 1));
  const visible = items.slice((page - 1) * pageSize, page * pageSize);
  const posts = new Map(data.published.map(post => [post.id, post]));
  if (!published) {
    const missing = [...new Set(visible.map(item => item.postId))].filter(id => !posts.has(id));
    const details = await Promise.allSettled(missing.map(id => api(`/api/trade/posts/${id}`, {signal: ctx.signal})));
    for (const result of details) {
      if (result.status === 'fulfilled') posts.set(result.value.post.id, result.value.post);
    }
  }
  if (ctx.signal.aborted) return;
  const pageLink = number => esc(routeHref('orders', current[0]) + '?page=' + number);
  await ctx.show(`
    <section class="market-page">
      <div class="page-head"><div><h1>我的交易</h1><p class="muted">查看发布记录和订单进度。</p></div>${link('wallet', '我的钱包', 'button secondary')}</div>
      <nav class="market-tabs market-order-tabs" aria-label="交易视图">
        ${orderTabs.map(([key, label, field]) => `<a href="${href('orders', key)}" ${key === current[0] ? 'aria-current="page"' : ''}>${label} <small>${data[field].length}</small></a>`).join('')}
      </nav>
      ${visible.length ? `<div class="market-results">${visible.map(item => {
        const post = published ? item : posts.get(item.postId);
        const title = post?.title || `订单 #${item.id}`;
        const expired = (published || item.status === 'PENDING') && isExpiredBounty(post);
        return `<a class="market-card market-order-row" href="${href(published ? 'trade' : 'order', item.id)}">
          <span class="market-card-icon">${marketIcon(post?.category || '', 'ticket')}</span>
          <span class="market-card-body"><strong class="market-card-title">${esc(title)}</strong><small class="market-card-meta">${post ? typeName(post.type) + ' · ' : ''}${esc(dateTime(item.createdAt))}</small></span>
          <strong class="market-price">${esc(money(published ? item.price : item.amount))}</strong>
          ${badge(expired ? 'EXPIRED' : item.status)}
          <span class="market-card-arrow">${icon('right', 17)}</span>
        </a>`;
      }).join('')}</div>` : `<div class="empty">这里还没有记录。${link('bounties', '去悬赏大厅', 'button secondary')}</div>`}
      ${pages > 1 ? `<nav class="market-pagination" aria-label="订单分页">
        ${page > 1 ? `<a class="button secondary" href="${pageLink(page - 1)}">上一页</a>` : ''}
        <span>第 ${page} / ${pages} 页</span>
        ${page < pages ? `<a class="button secondary" href="${pageLink(page + 1)}">下一页</a>` : ''}
      </nav>` : ''}
    </section>`);
}

function orderProgress(status, type) {
  const sequence = ['IN_PROGRESS', 'CONFIRMING', 'COMPLETED'];
  if (type === 'BOUNTY') sequence.unshift('PENDING');
  const active = sequence.indexOf(status);
  if (active < 0) return '';
  return `<ol class="market-progress" aria-label="订单进度">${sequence.map((key, index) =>
    `<li ${key === status ? 'aria-current="step"' : ''} class="${index <= active ? 'is-reached' : ''}"><span>${index + 1}</span>${statuses[key]}</li>`
  ).join('')}</ol>`;
}

function appealSummary(appeal, order) {
  if (appeal?.status === 'OPEN') {
    return `<div class="market-appeal"><h3>申诉理由</h3><p class="market-description">${esc(appeal.reason)}</p><small>发起于 ${esc(dateTime(appeal.createdAt))}</small></div>`;
  }
  if (appeal?.status !== 'RESOLVED') return '';
  const result = appeal.outcome === 'RELEASE_TO_SELLER'
    ? '托管款已结算给卖家' : '托管款已退回买家';
  return `<div class="market-appeal is-resolved">
    <h3>平台仲裁结果</h3>
    <strong>${result} · ${esc(money(order.amount))}</strong>
    <p class="market-description">${esc(appeal.adminComment)}</p>
    <small>裁决于 ${esc(dateTime(appeal.handledAt))}</small>
  </div>`;
}

function orderReviews(reviews, order) {
  if (!reviews.length) return '';
  return `<section class="panel market-detail-card">
    <h2>交易评价</h2>
    <dl class="stack">${reviews.map(review => `<div>
      <dt><strong>${review.fromUserId === order.buyerId ? '买家评价' : '卖家评价'}</strong></dt>
      <dd class="market-description">${esc(review.content)}</dd>
    </div>`).join('')}</dl>
  </section>`;
}

async function orderDetail(ctx, id) {
  const endpoint = '/api/trade/orders/' + encodeURIComponent(id);
  const {order, post} = await api(endpoint, {signal: ctx.signal});
  const userId = Number(ctx.user.id);
  const buyer = userId === order.buyerId;
  const seller = userId === order.sellerId;
  const counterparty = buyer ? order.sellerId : order.buyerId;
  const status = order.status;
  const expired = status === 'PENDING' && isExpiredBounty(post);
  const mayExtend = buyer && expired;
  const cancelLabel = mayExtend ? '取消悬赏' : '取消订单';
  const canAppeal = (buyer || seller) && ['IN_PROGRESS', 'CONFIRMING'].includes(status);
  const mayReview = status === 'COMPLETED' && Boolean(counterparty);
  const [appeal, reviews] = await Promise.all([
    ['DISPUTED', 'COMPLETED', 'CANCELLED'].includes(status) ? api(endpoint + '/appeal', {signal: ctx.signal}) : null,
    mayReview ? api('/api/reviews/order/' + encodeURIComponent(order.id), {signal: ctx.signal}) : [],
  ]);
  const hasReviewed = reviews.some(item => item.fromUserId === userId);
  if (!await ctx.show(`
    <section class="market-page">
      ${link('orders', '← 返回我的交易', 'market-back')}
      <div class="page-head"><div><h1>订单详情</h1><p class="muted">订单 #${esc(order.id)}</p></div>${badge(expired ? 'EXPIRED' : status)}</div>
      <div class="market-detail">
        <div class="stack">
          <section class="panel market-detail-card">
            <h2>订单进度</h2>
            ${expired ? '' : orderProgress(status, post.type)}
            <p class="market-fund-note">${icon('wallet', 17)}${expired ? '悬赏已过接单截止时间，赏金仍在托管。延长截止时间可恢复接单，取消悬赏可退回赏金。' : esc(fundMessages[status] || statuses[status] || status)}</p>
            ${mayExtend ? `<div class="market-expiry-actions">
              ${actionButton('extend', '延长截止时间', '')}
              ${actionButton('cancel', cancelLabel, 'secondary')}
            </div>` : ''}
            ${appealSummary(appeal, order)}
          </section>
          ${tradePostDetails(post)}
          ${orderReviews(reviews, order)}
        </div>
        <aside class="panel market-summary">
          <div><p class="market-card-meta">订单金额</p><strong class="market-price">${esc(money(order.amount))}</strong></div>
          <dl class="market-summary-facts">
            <div><dt>买家</dt><dd>${buyer ? '我' : '用户 ' + esc(order.buyerId)}</dd></div>
            <div><dt>卖家</dt><dd>${seller ? '我' : order.sellerId ? '用户 ' + esc(order.sellerId) : expired ? '无人接单' : '待接单'}</dd></div>
            <div><dt>最近更新</dt><dd>${esc(dateTime(order.updatedAt || order.createdAt))}</dd></div>
          </dl>
          ${seller && status === 'IN_PROGRESS' ? actionButton('deliver', '标记交付', '') : ''}
          ${buyer && status === 'CONFIRMING' ? actionButton('confirm', '确认完成', '') : ''}
          ${canAppeal ? actionButton('appeal', '发起申诉') : ''}
          ${appeal?.status === 'OPEN' && appeal.appellantId === userId ? actionButton('withdraw', '撤回申诉') : ''}
          ${buyer && !mayExtend && ['PENDING', 'IN_PROGRESS'].includes(status) ? actionButton('cancel', cancelLabel, 'quiet') : ''}
          ${mayReview && !hasReviewed ? actionButton('review', '评价对方', '') : ''}
          ${counterparty ? link('messages', '联系对方', 'button secondary', counterparty) : ''}
          ${link('trade', '查看原交易帖', 'button quiet', post.id)}
        </aside>
      </div>
    </section>`)) return;
  const refresh = () => ctx.refresh();
  const postAction = (url, body) => api(url, {method: 'POST', body});
  const confirmation = (title, note, action) => tradeConfirmation(ctx, {
    title, note, submit: title, success: title + '成功', save: () => postAction(endpoint + '/' + action), done: refresh,
  });
  bindActionButtons(ctx.main, {
    deliver: confirmation('标记交付', '确认你已按约定交付，买家随后将收到通知。', 'deliver'),
    confirm: confirmation('确认完成', '确认后，订单费用会结算给卖家。', 'confirm'),
    cancel: confirmation(cancelLabel, mayExtend ? '取消后，托管赏金将退回你的余额。' : '取消后，托管费用会退回买家。', 'cancel'),
    extend: tradeConfirmation(ctx, {
      title: '延长接单截止时间', note: '设置新的截止时间后，悬赏将重新开放接单，赏金继续托管。',
      deadline: true, submit: '确认延长', success: '截止时间已延长',
      save: value => postAction(endpoint + '/extend', {deadline: value.length === 16 ? value + ':00' : value}),
      done: refresh,
    }),
    withdraw: tradeConfirmation(ctx, {
      title: '撤回申诉', note: '撤回后，订单会恢复到申诉前的状态。', submit: '撤回申诉',
      success: '已撤回申诉', save: () => postAction(`/api/trade/appeals/${appeal?.id}/withdraw`), done: refresh,
    }),
    appeal: tradeConfirmation(ctx, {
      title: '发起申诉', note: '申诉期间，订单操作暂停，资金保持托管。', field: '申诉理由', submit: '提交申诉',
      success: '申诉已提交', save: reason => postAction(endpoint + '/appeal', {reason}), done: refresh,
    }),
    review: tradeConfirmation(ctx, {
      title: '评价对方', field: '评价内容', submit: '提交评价',
      success: '评价已发布', save: content => postAction('/api/reviews', {orderId: order.id, content}), done: refresh,
    }),
  });
}

export async function renderMarketplace(ctx) {
  const [id] = ctx.args;
  switch (ctx.route) {
    case 'bounties': return hall(ctx, 'BOUNTY');
    case 'services': return hall(ctx, 'SERVICE');
    case 'publish-bounty': return publish(ctx, 'BOUNTY');
    case 'publish-service': return publish(ctx, 'SERVICE');
    case 'orders': return myOrders(ctx);
    case 'order': return orderDetail(ctx, id);
    case 'trade': return tradeDetail(ctx, id);
  }
}
