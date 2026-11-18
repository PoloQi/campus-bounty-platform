import { api, ApiError, esc, icon, dateTime, imageUrl, uploadImage, toast, errorText, routeHref, sessionToken } from './core.js';

const peerNames = new Map();
const peerLabel = id => peerNames.get(id) || '用户 ' + id;

const partnerTags = ['考研', '运动', '组队', '拼车'];
const noticeTypes = {
  ORDER_CREATED: '服务收到新订单', ORDER_ACCEPTED: '悬赏已被接单',
  BOUNTY_EXPIRED: '悬赏已过接单截止时间',
  ORDER_DELIVERED: '订单已交付', ORDER_COMPLETED: '订单已完成', ORDER_CANCELLED: '订单已取消',
  APPEAL_CREATED: '订单发起申诉', APPEAL_WITHDRAWN: '订单申诉已撤回',
  APPEAL_RESOLVED: '申诉已处理', ORDER_ARBITRATED: '订单仲裁结果', REPORT_HANDLED: '举报处理结果',
};
const orderNotices = new Set(Object.keys(noticeTypes).filter(type => type !== 'REPORT_HANDLED'));
const idOf = (value) => /^[1-9]\d*$/.test(String(value)) && Number.isSafeInteger(Number(value)) ? Number(value) : null;
const ownerId = (ctx) => idOf(ctx.user?.id ?? ctx.user?.userId);
const tokens = (value) => (Array.isArray(value) ? value : String(value ?? '').split(/[,，\n]/)).map(item => String(item).trim()).filter(Boolean);
const badgeText = (value) => esc(String(Math.max(0, Number(value) || 0)));
const displayName = (record) => record.authorNickname || '用户 ' + record.authorId;
const dateLabel = (item) => esc(item.createdAt ? dateTime(item.createdAt) : '');
const inputError = message => new ApiError(message,400);
const path = (name, ...args) => esc(routeHref(name, ...args));
const feedLink = (view = '', tag = '') => {
  const query = new URLSearchParams();
  if (view) query.set('view', view);
  if (tag) query.set('tag', tag);
  const suffix = query.toString();
  return esc(routeHref('community') + (suffix ? '?' + suffix : ''));
};

function heading(title, text, action = '') {
  return '<header class="page-head comm-heading"><div><h1>' + title + '</h1>' +
    (text ? '<p>' + text + '</p>' : '') + '</div>' + action + '</header>';
}

function status(message) {
  return '<div class="empty comm-empty" role="status">' + message + '</div>';
}

function failure(element, error) {
  element.textContent = errorText(error);
  element.hidden = false;
}

async function submit(button, action, onError = err => toast(errorText(err))) {
  if (button.disabled) return;
  button.disabled = true;
  try {
    await action();
  } catch (err) {
    if (err?.name !== 'AbortError') onError(err);
  } finally {
    if (button.isConnected) button.disabled = false;
  }
}

function confirmDialog(title, content, label, onSubmit, danger = false) {
  const dialog = document.createElement('dialog');
  dialog.className = 'comm-dialog';
  dialog.setAttribute('aria-label', title);
  dialog.innerHTML = '<form class="fields"><h2>' + title + '</h2>' + content +
    '<p class="form-error" role="alert" hidden></p><div class="comm-actions">' +
    '<button class="button secondary" type="button" data-close>取消</button><button type="submit" class="button' +
    (danger ? ' comm-danger' : '') + '">' + label + '</button></div></form>';
  document.body.append(dialog);
  const form = dialog.querySelector('form');
  dialog.querySelector('[data-close]').onclick = () => dialog.close();
  dialog.addEventListener('close', () => dialog.remove(), {once:true});
  form.onsubmit = event => {
    event.preventDefault();
    submit(form.querySelector('[type="submit"]'), async () => {
      await onSubmit(form);
      dialog.close();
    }, error => failure(form.querySelector('.form-error'), error));
  };
  dialog.showModal();
}

export function openReport(targetType, targetId) {
  targetId = idOf(targetId);
  const typeLabel = { POST: '社区帖子', TRADE_POST: '交易帖子', USER: '用户' }[targetType];
  if (!typeLabel || !targetId) return;
  confirmDialog('举报' + typeLabel,
    '<label class="field">举报原因<textarea name="reason" rows="4" maxlength="200" required placeholder="说明你遇到的问题"></textarea></label>',
    '提交举报', async form => {
      const reason = form.elements.reason.value.trim();
      if (!reason) throw inputError('请输入举报原因。');
      await api('/api/reports', { method:'POST', body:{ targetType,targetId,reason } });
      toast('举报已提交');
    });
}

function avatar(record) {
  const name = displayName(record);
  const src = imageUrl(record.authorAvatar);
  return src ? '<img class="comm-avatar" src="' + esc(src) + '" alt="" loading="lazy">' :
    '<span class="comm-avatar" aria-hidden="true">' + esc(name.trim().slice(0,1) || '同') + '</span>';
}

function postCard(ctx, post, { detail = false, view = '' } = {}) {
  const id = idOf(post.id);
  const author = idOf(post.authorId);
  if (author && post.authorNickname) peerNames.set(author, post.authorNickname);
  const mine = author === ownerId(ctx);
  const visible = post.status === 'VISIBLE';
  const href = path('post', id);
  const imgs = tokens(post.images).map(imageUrl).filter(Boolean);
  const tags = tokens(post.tags);
  const snippets = tags.map(tag => '<a class="comm-tag" href="' + feedLink(view === 'partners' ? view : '', tag) + '"># ' + esc(tag) + '</a>');
  const originalContent = String(post.content ?? '');
  const content = detail || originalContent.length <= 250 ? originalContent : originalContent.slice(0,250) + '…';
  const body = '<p class="comm-copy">' + esc(content) + '</p>';
  const media = imgs.length ? '<div class="comm-images">' + imgs.map((url, index) =>
    '<a href="' + esc(url) + '" target="_blank" rel="noopener noreferrer" aria-label="查看帖子图片 ' + (index + 1) +
    '"><img src="' + esc(url) + '" alt="帖子图片 ' + (index + 1) + '" loading="lazy"></a>').join('') + '</div>' : '';
  const actions = '<div class="comm-actions comm-post-actions">' +
    '<button class="button quiet" type="button" data-like aria-label="点赞或取消点赞，当前 ' + badgeText(post.likeCount) +
    ' 个赞">' + icon('heart',17) + '<span data-like-count>' + badgeText(post.likeCount) + '</span></button>' +
    (detail ? '<a class="button quiet" href="' + href + '" data-comments-link>' + icon('chat',17) + '<span data-comment-count>' +
     badgeText(post.commentCount) + '</span> 条评论</a>' : '<a class="button quiet" href="' + href + '">' +
     icon('chat',17) + badgeText(post.commentCount) + ' 条评论</a>') +
    (mine ? '<a class="button quiet" href="' + path('edit-post',id) + '">编辑</a><button class="button quiet comm-danger-text" data-delete type="button">删除</button>' :
      (author ? '<a class="button quiet" href="' + path('messages',author) + '">私信</a>' : '') +
      '<button class="button quiet" data-report type="button">举报</button>') + '</div>';
  const authorName = esc(displayName(post));
  const authorLabel = detail && author && !mine ? '<a href="' + path('reviews', author) + '">' + authorName + '</a>' : authorName;
  return '<article class="panel comm-post" data-post-id="' + id + '"><header class="comm-byline">' + avatar(post) +
    '<div><strong>' + authorLabel + '</strong><p class="muted">' + dateLabel(post) + '</p></div>' +
    (visible ? (!detail ? '<a class="comm-detail-link" href="' + href + '">查看详情 ' + icon('upRight',15) + '</a>' : '') :
     '<span class="badge comm-deleted">已删除</span>') + '</header>' + body + media +
    (snippets.length ? '<div class="comm-post-tags">' + snippets.join('') + '</div>' : '') + (visible ? actions : '') + '</article>';
}

function bindPostActions(ctx, region, afterDelete) {
  region.addEventListener('click', event => {
    const button = event.target.closest('button');
    const id = idOf(button?.closest('[data-post-id]')?.dataset.postId);
    if (!id) return;
    if (button.hasAttribute('data-like')) {
      submit(button, async () => {
        const count = await api('/api/community/posts/' + id + '/like', {method:'POST', signal:ctx.signal});
        button.querySelector('[data-like-count]').textContent = String(count);
        button.setAttribute('aria-label', '点赞或取消点赞，当前 ' + count + ' 个赞');
      });
    }
    if (button.hasAttribute('data-report')) openReport('POST',id);
    if (button.hasAttribute('data-delete')) confirmDialog('删除帖子',
      '<p>删除后，其他同学将无法查看这条帖子。</p>', '确认删除', async () => {
        await api('/api/community/posts/' + id, { method:'DELETE' });
        toast('帖子已删除');
        if (!ctx.signal.aborted) afterDelete();
      }, true);
  });
}

function tabs(view, activeTag) {
  const entries = [
    ['', '最新动态'],
    ['partners', '找搭子'],
    ['mine', '我的帖子'],
  ];
  return '<nav aria-label="社区动态" class="comm-tabs">' + entries.map(([key,label]) =>
    '<a href="' + feedLink(key) + '"' + (key === view && !activeTag ? ' aria-current="page"' : '') + '>' + label + '</a>').join('') + '</nav>';
}

async function community(ctx) {
  const view = ['partners','mine'].includes(ctx.query.get('view')) ? ctx.query.get('view') : '';
  const tag = (ctx.query.get('tag') ?? '').slice(0,200).trim();
  const mine = view === 'mine';
  const partner = view === 'partners';
  const endpoint = '/api/community/posts' + (mine ? '/mine' : tag ? '?tag=' + encodeURIComponent(tag) : '');
  const title = mine ? '我的帖子' : partner ? '找搭子' : '最新动态';
  const filters = partner ? '<nav class="comm-topics comm-topic-bar" aria-label="搭子类型"><a href="' + feedLink('partners') +
    '"' + (!tag ? ' aria-current="page"' : '') + '>全部</a>' +
    partnerTags.map(label => '<a href="' + feedLink('partners',label) + '"' + (tag===label ? ' aria-current="page"' : '') +
      '>' + label + '</a>').join('') + '</nav>' : '';
  const composeURL = path('publish-post') + (partner ? '?tag=' + encodeURIComponent(tag || '组队') : '');
  const sidebar = '<aside class="comm-sidebar">' + (partner ? '' : '<section class="panel comm-topic-panel">' +
    '<span class="comm-section-icon">' + icon('users',22) + '</span><h2>找搭子</h2>' +
    '<p class="muted">按兴趣筛选，找到可以一起行动的同学。</p><div class="comm-topics">' +
    partnerTags.map(label => '<a href="' + feedLink('partners',label) + '"' + (partner && tag===label ? ' aria-current="page"' : '') +
      '># ' + label + '</a>').join('') + '</div></section>') +
    '<a class="panel comm-inbox-link" href="' + path('messages') + '">' + icon('chat',20) +
    '<span>一对一私信</span>' + icon('right',15) + '</a></aside>';
  if (!await ctx.show(heading('校园社区','分享校园日常，认识一起学习和活动的同学。',
    '<a class="button" href="' + composeURL + '">' + icon('plus',18) + '发布帖子</a>') +
    tabs(view, tag) + '<div class="comm-layout"><section class="comm-feed" aria-labelledby="feed-title">' +
    '<div class="comm-section"><h2 id="feed-title">' + title + '</h2>' +
    (tag && !partner ? '<a class="comm-tag" href="' + feedLink(view) + '"># ' + esc(tag) + ' · 清除筛选</a>' : '') +
    '</div>' + filters + '<div class="comm-card-list" data-feed>' + status('正在加载帖子…') + '</div></section>' + sidebar + '</div>')) return;
  const region = ctx.main.querySelector('[data-feed]');
  const raw = await api(endpoint, { signal:ctx.signal });
  if (ctx.signal.aborted) return;
  const posts = raw.filter(post => {
    const labels = tokens(post.tags);
    return tag ? labels.includes(tag) : !partner || labels.some(label => partnerTags.includes(label));
  });
  region.innerHTML = posts.length ? posts.map(post => postCard(ctx,post,{ view })).join('') :
    status(mine ? '你还没有发布帖子。' : partner || tag ? '暂时没有符合筛选条件的帖子。' : '还没有社区动态，欢迎发布第一条。');
  bindPostActions(ctx,region,() => ctx.refresh());
}

async function postDetail(ctx) {
  const id = idOf(ctx.args[0]);
  const back = '<a class="button secondary" href="' + feedLink() + '">返回社区</a>';
  if (!await ctx.show('<div class="comm-reader">' + heading('帖子详情','',back) + '<div class="comm-detail" data-detail>' + status('正在加载帖子…') + '</div></div>')) return;
  const region = ctx.main.querySelector('[data-detail]');
  if (!id) {
    region.innerHTML = status('帖子编号无效。');
    return;
  }
  // The community API exposes a feed and comments, but has no individual GET post endpoint.
  const posts = await api('/api/community/posts', { signal:ctx.signal });
  if (ctx.signal.aborted) return;
  const post = posts.find(item => idOf(item.id) === id);
  if (!post) {
    region.innerHTML = status('这条帖子已删除或不可查看。');
    return;
  }
  region.innerHTML = postCard(ctx, post, { detail:true }) +
    '<section class="panel comm-comment-section" aria-labelledby="comments-title" tabindex="-1"><h2 id="comments-title">全部评论</h2><div data-comments>' +
    status('正在加载评论…') + '</div></section>';
  bindPostActions(ctx,region,() => ctx.go('community'));
  const commentsSection = region.querySelector('.comm-comment-section');
  region.querySelector('[data-comments-link]').onclick = event => {
    event.preventDefault();
    commentsSection.focus();
    commentsSection.scrollIntoView({block:'start'});
  };
  const commentRegion = commentsSection.querySelector('[data-comments]');
  const load = async () => {
    const list = await api('/api/community/posts/' + id + '/comments', { signal:ctx.signal });
    if (ctx.signal.aborted) return;
    renderComments(ctx,commentRegion,id,list,load);
    region.querySelector('[data-comment-count]').textContent = String(list.length);
  };
  try { await load(); } catch (err) {
    if (!ctx.signal.aborted) commentRegion.innerHTML = status(esc(errorText(err)));
  }
}

function renderComments(ctx, region, id, comments, reload) {
  const commentMap = new Map(comments.map(item => [idOf(item.id), item]));
  region.innerHTML = '<div class="comm-comments">' + (comments.length ? comments.map(comment => {
    const parent = commentMap.get(idOf(comment.replyToId));
    return '<article class="comm-comment"><header class="comm-byline">' + avatar(comment) +
      '<strong>' + esc(displayName(comment)) + '</strong><span class="muted">' + dateLabel(comment) + '</span></header>' +
      '<p class="comm-copy">' + (parent ? '<strong class="comm-reply-byline">回复 ' + esc(displayName(parent)) + ' </strong>' : '') +
      esc(comment.content) + '</p><button class="button quiet" type="button" data-reply="' + idOf(comment.id) + '">回复</button></article>';
  }).join('') : '<p class="muted">还没有评论。</p>') + '</div><form class="fields comm-comment-form">' +
    '<label class="field"><span data-comment-label>发表评论</span><textarea name="content" maxlength="500" rows="3" required></textarea></label>' +
    '<p class="form-error" role="alert" hidden></p><div class="comm-actions"><button type="submit" class="button">发送评论</button>' +
    '<button type="button" class="button secondary" data-reset-reply hidden>取消回复</button></div></form>';
  const form = region.querySelector('form');
  const label = form.querySelector('[data-comment-label]');
  const cancel = form.querySelector('[data-reset-reply]');
  const error = form.querySelector('.form-error');
  let replyToId = null;
  cancel.onclick = () => {
    replyToId = null;
    label.textContent = '发表评论';
    cancel.hidden = true;
  };
  region.querySelectorAll('[data-reply]').forEach(button => {
    button.onclick = () => {
      replyToId = idOf(button.dataset.reply);
      label.textContent = '回复 ' + displayName(commentMap.get(replyToId));
      cancel.hidden = false;
      form.elements.content.focus();
    };
  });
  form.onsubmit = event => {
    event.preventDefault();
    error.hidden = true;
    submit(form.querySelector('[type="submit"]'), async () => {
      const content = form.elements.content.value.trim();
      if (!content) throw inputError('请输入评论内容。');
      await api('/api/community/posts/' + id + '/comments', {method:'POST',body:{content,replyToId},signal:ctx.signal});
      form.reset();
      cancel.click();
      toast('评论已发布');
      await reload();
    }, err => failure(error,err));
  };
}

async function postEditor(ctx) {
  const editing = ctx.route === 'edit-post';
  const id = idOf(ctx.args[0]);
  const back = '<a href="' + (editing && id ? path('post',id) : feedLink()) + '" class="button secondary">取消</a>';
  const title = editing ? '编辑帖子' : '发布帖子';
  if (!await ctx.show(heading(title,'',back) + '<section class="panel comm-editor" data-editor>' + status('正在加载…') + '</section>')) return;
  const editor = ctx.main.querySelector('[data-editor]');
  let post;
  if (editing) {
    const mine = id ? await api('/api/community/posts/mine',{signal:ctx.signal}) : [];
    if (ctx.signal.aborted) return;
    post = mine.find(item => idOf(item.id) === id && item.status === 'VISIBLE');
    if (!post) {
      editor.innerHTML = status('这条帖子不可编辑。');
      return;
    }
  }
  const tags = post?.tags ?? ctx.query.get('tag')?.slice(0,200) ?? '';
  editor.innerHTML = '<form class="fields comm-editor-form">' +
    '<label class="field">帖子内容<textarea name="content" rows="7" maxlength="2000" required placeholder="分享校园日常，或邀请同学一起行动">' +
    esc(post?.content ?? '') + '</textarea></label>' +
    '<label class="field">标签<input name="tags" maxlength="200" placeholder="例如：运动，组队" value="' + esc(tags) + '"></label>' +
    '<div class="comm-topics" aria-label="添加常用标签">' +
    partnerTags.map(label => '<button type="button" data-add-tag="' + label + '"># ' + label + '</button>').join('') + '</div>' +
    '<label class="field">上传图片<input type="file" name="files" accept=".jpg,.jpeg,.png,.gif,.webp" multiple>' +
    '<small class="muted">每张最多 5 MB，支持 JPG、PNG、GIF、WebP。</small></label>' +
    (post?.images ? '<label class="comm-check"><input type="checkbox" name="keepImages" checked>保留已有图片</label>' : '') +
    '<p class="form-error" role="alert" hidden></p><div class="comm-actions">' +
    '<button class="button" type="submit">' + (editing ? '保存修改' : '发布帖子') + '</button></div></form>';
  const form = editor.querySelector('form');
  const error = form.querySelector('.form-error');
  const fileInput = form.elements.files;
  const uploaded = new Map();
  fileInput.onchange = () => uploaded.clear();
  form.querySelectorAll('[data-add-tag]').forEach(button => {
    button.onclick = () => {
      const input = form.elements.tags;
      const next = [...new Set([...tokens(input.value),button.dataset.addTag])].join(',');
      if (next.length <= input.maxLength) input.value = next;
    };
  });
  form.onsubmit = event => {
    event.preventDefault();
    const button = form.querySelector('[type="submit"]');
    error.hidden = true;
    submit(button, async () => {
      const content = form.elements.content.value.trim();
      if (!content) throw inputError('请输入帖子内容。');
      const files = Array.from(fileInput.files);
      if (files.some(file => file.size === 0 || file.size > 5*1024*1024 || !/\.(jpe?g|png|gif|webp)$/i.test(file.name))) {
        throw inputError('请选择非空且不超过 5 MB 的 JPG、PNG、GIF 或 WebP 图片。');
      }
      const images = form.elements.keepImages?.checked ? tokens(post.images) : [];
      fileInput.disabled = true;
      try {
        for (const [index,file] of files.entries()) {
          if (!uploaded.has(index)) uploaded.set(index,await uploadImage(file,ctx.signal));
          images.push(uploaded.get(index));
        }
      } finally {
        fileInput.disabled = false;
      }
      if (images.join(',').length > 1000) throw inputError('图片太多，请减少图片数量。');
      const saved = await api('/api/community/posts' + (editing ? '/' + id : ''), {
        method:editing ? 'PUT' : 'POST', body:{content,images:images.join(','),tags:tokens(form.elements.tags.value).join(',')},
        signal:ctx.signal,
      });
      if (ctx.signal.aborted) return;
      toast(editing ? '帖子已更新' : '帖子已发布');
      ctx.go('post',idOf(saved.id));
    }, err => failure(error,err));
  };
}

function notificationCard(n) {
  const type = Object.hasOwn(noticeTypes,n.type) ? noticeTypes[n.type] : '站内通知';
  const target = orderNotices.has(n.type) && idOf(n.refId) ? path('order',idOf(n.refId)) : '';
  const actionLabel = n.type === 'BOUNTY_EXPIRED' ? '处理悬赏' : '查看订单';
  return '<article class="panel comm-notification' + (n.read ? '' : ' comm-unread') + '" data-notice-id="' + idOf(n.id) + '">' +
    '<div class="comm-section-icon">' + icon('bell',20) + '</div><div class="comm-notice-copy"><div class="comm-notice-meta"><h2>' + type +
    '</h2><span class="badge ' + (n.read ? 'comm-read-badge' : 'comm-unread-badge') + '">' + (n.read ? '已读' : '未读') +
    '</span><span class="muted">' + dateLabel(n) + '</span></div><p>' + esc(n.content) + '</p>' +
    (target ? '<a href="' + target + '" data-notification-target>' + actionLabel + ' ' + icon('arrow',16) + '</a>' : '') + '</div>' +
    (n.read ? '' : '<button type="button" class="button quiet" data-mark-read>标记已读</button>') + '</article>';
}

async function notifications(ctx) {
  const unreadOnly = ctx.query.get('view') === 'unread';
  const base = path('notifications');
  if (!await ctx.show(heading('站内通知','查看交易进度和平台消息。') +
    '<div class="comm-detail"><nav class="comm-tabs" aria-label="通知筛选"><a href="' + base + '"' +
    (unreadOnly ? '' : ' aria-current="page"') + '>全部通知</a><a href="' + base + '?view=unread"' +
    (unreadOnly ? ' aria-current="page"' : '') + '>未读</a></nav><div class="comm-card-list" data-notifications>' +
    status('正在加载通知…') + '</div></div>')) return;
  const list = ctx.main.querySelector('[data-notifications]');
  let items = await api('/api/notify',{signal:ctx.signal});
  if (ctx.signal.aborted) return;
  ctx.syncNotifications(items);
  const marking = new Map();
  const render = () => {
    const filtered = unreadOnly ? items.filter(item => !item.read) : items;
    list.innerHTML = filtered.length ? filtered.map(notificationCard).join('') :
      status(unreadOnly ? '没有未读通知。' : '暂时没有通知。');
  };
  window.addEventListener('notifications-refresh', event => {
    const next = event.detail;
    const newReadStates = new Map(next.map(item => [item.id,item.read]));
    const changed = items.some(item => newReadStates.get(item.id) !== item.read);
    const knownIds = new Set(items.map(item => item.id));
    const arrivals = next.filter(item => !knownIds.has(item.id));
    items = next;
    if (changed) {
      render();
      return;
    }
    const visible = unreadOnly ? arrivals.filter(item => !item.read) : arrivals;
    if (!visible.length) return;
    list.querySelector('.comm-empty')?.remove();
    list.insertAdjacentHTML('afterbegin', visible.map(notificationCard).join(''));
  }, {signal:ctx.signal});
  async function markRead(item) {
    if (item.read) return;
    if (!marking.has(item.id)) {
      marking.set(item.id,api('/api/notify/' + item.id + '/read', { method:'POST',signal:ctx.signal })
        .then(() => {
          const current = items.find(notice => notice.id === item.id);
          if (current) current.read = true;
          ctx.syncNotifications(items);
        })
        .finally(() => marking.delete(item.id)));
    }
    await marking.get(item.id);
  }
  list.onclick = event => {
    const target = event.target.closest('[data-notification-target]');
    const button = event.target.closest('[data-mark-read]');
    const item = items.find(n => idOf(n.id) === idOf((target || button)?.closest('[data-notice-id]')?.dataset.noticeId));
    if (!item) return;
    if (button) {
      submit(button, async () => {
        await markRead(item);
        if (!ctx.signal.aborted) render();
      });
      return;
    }
    if (!target || item.read || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
    event.preventDefault();
    markRead(item).catch(error => {
      if (!ctx.signal.aborted) toast(errorText(error));
    }).finally(() => {
      if (!ctx.signal.aborted) ctx.go('order', idOf(item.refId));
    });
  };
  render();
}

function chatPanel(peer) {
  if (!peer) return '<section class="panel comm-chat-empty">' + icon('chat',28) +
    '<h2>从帖子或交易发起私信</h2><p class="muted">在帖子或交易详情中找到对方，就可以开始聊天。</p>' +
    '<div class="toolbar"><a class="button" href="' + path('community') + '">去社区</a>' +
    '<a class="button secondary" href="' + path('orders','buyer') + '">查看我的交易</a></div></section>';
  return '<section class="panel comm-chat-window"><header class="comm-chat-head"><div class="comm-byline"><span class="comm-avatar" aria-hidden="true">同</span>' +
    '<div><h2>' + esc(peerLabel(peer)) + '</h2><span class="muted" data-chat-status role="status">正在连接…</span></div></div>' +
    '<button type="button" class="button quiet" data-chat-refresh title="更新聊天记录">' + icon('clock',16) + '刷新记录</button></header>' +
    '<div class="comm-chat-history" data-history role="log" aria-label="聊天记录">' + status('正在加载历史消息…') + '</div>' +
    '<form class="fields comm-chat-send"><label class="field">消息 <textarea name="message" rows="2" maxlength="1000" required placeholder="输入消息"></textarea></label>' +
    '<p class="form-error" role="alert" hidden></p><div class="comm-actions"><button type="submit" class="button" disabled>发送</button>' +
    '<button type="button" class="button secondary" data-reconnect hidden>重新连接</button></div></form></section>';
}

async function messages(ctx) {
  const self = ownerId(ctx);
  const requested = idOf(ctx.args[0]);
  const peer = requested !== self ? requested : null;
  const recent = new Map();
  let conversationStatus = '正在加载最近会话…';
  let conversationsRequested = false;
  if (!await ctx.show(heading('一对一私信','') +
    '<div class="comm-layout comm-chat"><aside class="panel comm-chat-sidebar">' +
    '<nav aria-label="最近会话"><h2>最近会话</h2><div data-peers></div></nav>' +
    '<details class="comm-peer-picker"><summary>按用户编号打开会话</summary>' +
    '<form class="fields" data-peer-picker><label class="field">对方用户编号<input type="text" name="peer" inputmode="numeric" pattern="[0-9]+" required></label>' +
    '<button class="button secondary" type="submit">打开会话</button></form></details>' +
    '</aside>' + chatPanel(peer) + '</div>')) return;
  const peerLinks = ctx.main.querySelector('[data-peers]');
  function renderPeers() {
    const known = [...recent.keys()].sort((a,b) => recent.get(b) - recent.get(a));
    const visible = peer && !known.includes(peer) ? [peer,...known] : known;
    peerLinks.innerHTML = visible.length ? visible.map(id => '<a href="' + path('messages',id) + '"' +
      (id === peer ? ' aria-current="page"' : '') + '>' + esc(peerLabel(id)) + icon('right',16) + '</a>').join('') :
      '<p class="muted">' + conversationStatus + '</p>';
  }
  function rememberPeer(id, lastMessageId) {
    if (!id || id === self || !lastMessageId || lastMessageId <= (recent.get(id) ?? 0)) return false;
    recent.set(id,lastMessageId);
    return true;
  }
  async function loadConversations() {
    conversationsRequested = true;
    try {
      const conversations = await api('/api/chat/conversations',{signal:ctx.signal});
      if (!live()) return;
      for (const conversation of conversations) {
        rememberPeer(idOf(conversation.peerId),idOf(conversation.lastMessageId));
      }
      conversationStatus = '还没有最近会话。';
    } catch {
      if (!live()) return;
      conversationStatus = '最近会话加载失败，请重新打开私信。';
    }
    renderPeers();
  }
  renderPeers();
  ctx.main.querySelector('[data-peer-picker]').onsubmit = event => {
    event.preventDefault();
    const id = idOf(event.currentTarget.elements.peer.value.trim());
    if (!id || id === self) {
      toast('请输入其他用户的有效编号。');
      return;
    }
    ctx.go('messages',id);
  };

  const form = ctx.main.querySelector('.comm-chat-send');
  const send = form?.querySelector('[type="submit"]');
  const reconnect = form?.querySelector('[data-reconnect]');
  const error = form?.querySelector('.form-error');
  const statusText = ctx.main.querySelector('[data-chat-status]');
  const history = ctx.main.querySelector('[data-history]');
  const refresh = ctx.main.querySelector('[data-chat-refresh]');
  const savedMessages = new Map();
  let socket;
  let pending;
  let requestSequence = 0;
  let historyLoaded = false;
  const live = () => !ctx.signal.aborted;
  const connected = () => socket?.readyState === WebSocket.OPEN;
  function setState(label, offerReconnect = false) {
    if (!form || !live()) return;
    statusText.textContent = label;
    send.disabled = Boolean(pending) || !connected();
    reconnect.hidden = !offerReconnect;
  }
  function showHistory() {
    historyLoaded = true;
    const list = [...savedMessages.values()].sort((a,b) => a.id - b.id);
    history.innerHTML = list.length ? list.map(item => {
      const own = idOf(item.fromId) === self;
      return '<article class="comm-message' + (own ? ' comm-message-own' : '') + '"><span class="muted">' +
        (own ? '你' : esc(peerLabel(idOf(item.fromId)))) + ' · ' + dateLabel(item) + '</span><p class="comm-copy">' +
        esc(item.content) + '</p></article>';
    }).join('') : status('这段会话还没有消息。');
    history.scrollTop = history.scrollHeight;
  }
  async function loadHistory() {
    const list = await api('/api/chat/history?withUserId=' + peer,{signal:ctx.signal});
    if (!live()) return;
    if (rememberPeer(peer,idOf(list.at(-1)?.id))) renderPeers();
    for (const item of list) savedMessages.set(idOf(item.id),item);
    showHistory();
  }
  function showSavedMessage(frame) {
    const messageId = idOf(frame.id);
    if (!messageId || savedMessages.has(messageId)) return;
    savedMessages.set(messageId,frame);
    showHistory();
  }
  function onFailure(err) {
    if (!live() || err?.name === 'AbortError') return;
    if (error) failure(error,err);
  }
  function showHistoryError(err) {
    onFailure(err);
    if (live() && !historyLoaded) history.innerHTML = status('聊天记录加载失败，请点击刷新记录重试。');
  }
  function clearPending() {
    if (!pending) return;
    clearTimeout(pending.timer);
    pending = null;
  }
  function finishSend(err) {
    clearPending();
    setState(connected() ? '实时连接已建立' : '连接已断开',!connected());
    if (err) onFailure(err);
  }
  function connect() {
    if (!live() || socket?.readyState === WebSocket.CONNECTING || connected()) return;
    const token = sessionToken();
    if (!token) {
      void loadConversations();
      if (peer) loadHistory().catch(showHistoryError);
      return;
    }
    const url = new URL('/ws/chat',location.origin);
    url.protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    url.searchParams.set('token',token);
    setState('正在连接…');
    try {
      socket = new WebSocket(url);
    } catch (err) {
      setState('连接失败',true);
      onFailure(err);
      if (!conversationsRequested) void loadConversations();
      if (peer && !historyLoaded) loadHistory().catch(showHistoryError);
      return;
    }
    socket.onopen = () => {
      setState('实时连接已建立');
      void loadConversations();
      if (peer) loadHistory().catch(showHistoryError);
    };
    socket.onclose = () => {
      const uncertain = pending && inputError('连接已断开。请刷新记录确认消息是否已保存。');
      finishSend(uncertain);
      if (!conversationsRequested) void loadConversations();
      if (peer && !historyLoaded) loadHistory().catch(showHistoryError);
    };
    socket.onmessage = event => {
      if (!live()) return;
      let frame;
      try { frame = JSON.parse(event.data); } catch { return; }
      if (!frame || typeof frame !== 'object') return;
      if (frame.action === 'ACK') {
        const messageId = idOf(frame.id);
        if (idOf(frame.fromId) !== self || idOf(frame.toId) !== peer || !messageId) return;
        if (rememberPeer(peer,messageId)) renderPeers();
        showSavedMessage(frame);
        if (pending?.requestId === frame.requestId) {
          if (form.elements.message.value.trim() === pending.text) form.reset();
          finishSend();
        }
        return;
      }
      if (frame.error) {
        if (pending && pending.requestId === frame.requestId) finishSend(inputError(String(frame.error)));
        return;
      }
      const sender = idOf(frame.fromId);
      const receiver = idOf(frame.toId);
      const other = sender === self ? receiver : receiver === self ? sender : null;
      const messageId = idOf(frame.id);
      if (!other || other === self || !messageId) return;
      if (rememberPeer(other,messageId)) renderPeers();
      if (other === peer) showSavedMessage(frame);
      else if (sender !== self) toast('收到' + peerLabel(sender) + '的私信');
    };
  }
  ctx.signal.addEventListener('abort',() => {
    clearPending();
    if (!socket) return;
    socket.onopen = socket.onclose = socket.onmessage = null;
    socket.close();
  },{once:true});
  if (form) {
    refresh.onclick = () => {
      error.hidden = true;
      submit(refresh,loadHistory,showHistoryError);
    };
    reconnect.onclick = connect;
    form.onsubmit = event => {
      event.preventDefault();
      if (pending || !connected()) return;
      const text = form.elements.message.value.trim();
      if (!text) {
        onFailure(inputError('请输入消息内容。'));
        return;
      }
      const requestId = String(++requestSequence);
      pending = {
        requestId,text,
        timer:setTimeout(() => finishSend(inputError('暂未确认消息已保存。请先刷新聊天记录再重试。')),10000),
      };
      error.hidden = true;
      setState('正在发送…');
      try {
        socket.send(JSON.stringify({toId:peer,content:text,requestId}));
      } catch (err) {
        finishSend(err);
      }
    };
  }
  connect();
}

const pages = {
  community, post:postDetail, 'publish-post':postEditor, 'edit-post':postEditor, messages, notifications,
};

export async function renderCommunication(ctx) {
  await pages[ctx.route]?.(ctx);
}
