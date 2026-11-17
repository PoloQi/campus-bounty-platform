import {
  api, dateTime, errorText, esc, icon, imageUrl, money, routeHref,
  safeReturnRoute, toast, uploadImage
} from './core.js';

const accountNav = [
  ['profile', 'users', '个人资料'],
  ['personal', 'grid', '个人中心'],
  ['wallet', 'wallet', '我的钱包'],
  ['reviews', 'star', '收到的评价']
];

const activityNav = [
  ['orders', 'published', 'ticket', '我发布的', '查看我发布的悬赏和技能服务。'],
  ['orders', 'buyer', 'wallet', '我发的订单', '跟进我发布的悬赏和买到的服务。'],
  ['orders', 'seller', 'check', '我接的单', '处理我接取的悬赏和售出的服务。'],
  ['personal', 'posts', 'chat', '我的帖子', '查看我在社区分享过的内容。']
];

const logTypes = {
  RECHARGE: '模拟充值',
  FREEZE: '冻结',
  UNFREEZE: '解冻',
  PAY: '托管结算',
  INCOME: '收入'
};

class StudentValidationError extends Error {}

function accountLayout(current, title, description, content, action = '') {
  return `
    <header class="page-head">
      <div><h1>${title}</h1><p class="muted">${description}</p></div>${action}
    </header>
    <div class="student-layout">
      <nav class="panel student-nav" aria-label="个人中心">
        ${accountNav.map(([page, glyph, label]) => `
          <a href="${routeHref(page)}"${page === current ? ' aria-current="page"' : ''}>
            ${icon(glyph, 18)}<span>${label}</span>
          </a>`).join('')}
      </nav>
      <div class="student-content stack">${content}</div>
    </div>`;
}

function attachForm(form, submit) {
  const button = form.querySelector('[type="submit"]');
  const feedback = form.querySelector('[role="alert"]');
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (button.disabled) return;
    const label = button.textContent;
    feedback.hidden = true;
    button.disabled = true;
    button.textContent = '正在提交…';
    try {
      await submit(Object.fromEntries(new FormData(form)));
    } catch (error) {
      if (!form.isConnected) return;
      feedback.textContent = error instanceof StudentValidationError ? error.message : errorText(error);
      feedback.hidden = false;
      feedback.focus();
    } finally {
      button.disabled = false;
      button.textContent = label;
    }
  });
}

function formError() {
  return '<p class="form-error" role="alert" tabindex="-1" hidden></p>';
}

function renderAuth(ctx, registering) {
  const next = safeReturnRoute(ctx.query.get('next'));
  const suffix = next === '#/' ? '' : '?' + new URLSearchParams({ next });
  const target = routeHref(registering ? 'login' : 'register') + suffix;
  const title = registering ? '注册校园账号' : '登录校园悬赏';

  if (!ctx.show(`
    <section class="student-auth">
      <div class="student-auth-intro">
        <span class="student-auth-mark">${icon('ticket', 34)}</span>
        <p class="student-auth-display">校园里的事，<br>一起解决。</p>
        <p>有需要就发悬赏，有特长就挂牌服务，也可以来社区找搭子。</p>
      </div>
      <div class="panel student-auth-entry">
        <h1>${title}</h1>
        <p class="muted">${registering ? '填写基本信息，创建学生账号。' : '欢迎回来，登录后开始校园互助。'}</p>
        <form class="fields">
          <label class="field">用户名
            <input name="username" required maxlength="20" ${registering ? 'minlength="4"' : ''}
              autocomplete="username" placeholder="${registering ? '输入 4–20 位用户名' : '输入用户名'}">
          </label>
          <label class="field">密码
            <input name="password" type="password" required maxlength="32"
              ${registering ? 'minlength="6" autocomplete="new-password"' : 'autocomplete="current-password"'}
              placeholder="${registering ? '输入 6–32 位密码' : '输入密码'}">
          </label>
          ${registering ? `
            <label class="field">确认密码
              <input name="confirmPassword" type="password" required minlength="6" maxlength="32"
                autocomplete="new-password" placeholder="再次输入密码">
            </label>
            <label class="field">昵称 <span class="muted">选填</span>
              <input name="nickname" maxlength="20" autocomplete="nickname" placeholder="留空时使用用户名">
            </label>` : ''}
          ${formError()}
          <button class="button" type="submit">${registering ? '创建账号' : '登录'}</button>
        </form>
        <p class="student-auth-switch">${registering ? '已有账号？' : '还没有账号？'}
          <a href="${esc(target)}">${registering ? '去登录' : '立即注册'}</a>
        </p>
      </div>
    </section>`)) return;

  const form = ctx.main.querySelector('form');
  attachForm(form, async fields => {
    fields.username = fields.username.trim();
    if (!fields.username) throw new StudentValidationError('请输入用户名。');
    if (registering) {
      if (fields.username.length < 4) throw new StudentValidationError('用户名至少需要 4 位。');
      if (fields.password !== fields.confirmPassword) throw new StudentValidationError('两次输入的密码不一致。');
      fields.nickname = fields.nickname.trim();
      await api('/api/auth/register', { method: 'POST', body: fields, signal: ctx.signal });
      if (!form.isConnected) return;
      toast('账号已创建，请登录。');
      location.hash = target;
      return;
    }
    const session = await api('/api/auth/login', { method: 'POST', body: fields, signal: ctx.signal });
    if (!form.isConnected) return;
    ctx.setSession(session.token, {
      id: session.userId, role: session.role, username: fields.username,
      nickname: session.nickname, displayName: session.nickname || fields.username
    });
    if (session.role === 'ADMIN') {
      location.assign('/admin');
      return;
    }
    location.hash = next;
    toast('登录成功。');
  });
}

function avatarMarkup(avatar, name) {
  const image = imageUrl(avatar);
  return image
    ? `<img class="student-avatar" width="68" height="68" src="${esc(image)}" alt="头像预览">`
    : `<span class="student-avatar" aria-label="默认头像">${esc(Array.from(name || '同')[0])}</span>`;
}

async function renderProfile(ctx) {
  const profile = await api('/api/users/me', { signal: ctx.signal });
  const name = profile.nickname || profile.username;
  const content = `
    <section class="panel student-profile">
      <div class="student-identity">
        <div data-profile-avatar>${avatarMarkup(profile.avatar, name)}</div>
        <div><h2>${esc(name)}</h2><p class="muted">@${esc(profile.username)} · ${profile.role === 'ADMIN' ? '管理员' : '学生'}</p></div>
      </div>
      <form class="fields">
        <div class="field">
          <label for="student-avatar-file">头像图片</label>
          <div class="student-avatar-controls">
            <input id="student-avatar-file" type="file" accept=".jpg,.jpeg,.png,.gif,.webp"
              aria-describedby="student-avatar-hint">
            <button type="button" class="button quiet" data-avatar-remove>移除头像</button>
          </div>
          <span class="muted" id="student-avatar-hint">支持 JPG、PNG、GIF 和 WebP，单张最大 5 MB。</span>
        </div>
        <div class="student-form-pair">
          <label class="field">昵称
            <input name="nickname" maxlength="30" value="${esc(profile.nickname ?? '')}" autocomplete="nickname">
          </label>
          <label class="field">联系方式
            <input name="contact" maxlength="60" value="${esc(profile.contact ?? '')}" placeholder="选填">
          </label>
        </div>
        <label class="field">个人简介
          <textarea name="bio" maxlength="200" rows="4" placeholder="介绍一下自己">${esc(profile.bio ?? '')}</textarea>
        </label>
        ${formError()}
        <div class="student-form-actions">
          <button class="button" type="submit">保存资料</button>
          <button class="button quiet" type="button" data-student-signout>退出登录</button>
        </div>
      </form>
    </section>`;
  if (!ctx.show(accountLayout('profile', '个人资料', '编辑头像、昵称、简介和联系方式。', content))) return;

  const form = ctx.main.querySelector('form');
  const file = form.querySelector('#student-avatar-file');
  const preview = ctx.main.querySelector('[data-profile-avatar]');
  let avatar = profile.avatar || '';
  let objectUrl = '';
  function releasePreview() {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = '';
  }
  ctx.signal.addEventListener('abort', releasePreview, { once: true });
  file.addEventListener('change', () => {
    releasePreview();
    const selected = file.files[0];
    const invalid = selected && (!/\.(jpe?g|png|gif|webp)$/i.test(selected.name) ||
      !selected.size || selected.size > 5 * 1024 * 1024);
    file.setCustomValidity(invalid ? '请选择不超过 5 MB 的 JPG、PNG、GIF 或 WebP 图片。' : '');
    if (selected && !invalid) {
      objectUrl = URL.createObjectURL(selected);
      preview.innerHTML = `<img class="student-avatar" width="68" height="68" src="${esc(objectUrl)}" alt="待保存的头像">`;
      return;
    }
    preview.innerHTML = avatarMarkup(avatar, name);
    if (invalid) file.reportValidity();
  });
  form.querySelector('[data-avatar-remove]').addEventListener('click', () => {
    releasePreview();
    avatar = '';
    file.value = '';
    file.setCustomValidity('');
    preview.innerHTML = avatarMarkup('', name);
  });
  form.querySelector('[data-student-signout]').addEventListener('click', ctx.signOut);
  attachForm(form, async fields => {
    const selected = file.files[0];
    file.disabled = true;
    const remove = form.querySelector('[data-avatar-remove]');
    remove.disabled = true;
    try {
      if (selected) {
        avatar = await uploadImage(selected, ctx.signal);
        file.value = '';
        releasePreview();
        preview.innerHTML = avatarMarkup(avatar, name);
      }
      const saved = await api('/api/users/me', {
        method: 'PUT', signal: ctx.signal,
        body: { nickname: fields.nickname.trim(), contact: fields.contact.trim(), bio: fields.bio.trim(), avatar }
      });
      if (!form.isConnected) return;
      ctx.updateUser({
        ...ctx.user, ...saved, avatarUrl: saved.avatar, displayName: saved.nickname || saved.username
      });
      toast('个人资料已保存。');
      ctx.refresh();
    } finally {
      file.disabled = false;
      remove.disabled = false;
    }
  });
}

async function renderPersonal(ctx) {
  if (ctx.args[0] === 'posts') {
    const posts = await api('/api/community/posts/mine', { signal: ctx.signal });
    const rows = posts.map(post => `
      <li class="student-item">
        <div><p class="student-post-copy">${esc(post.content)}</p>
          <p class="muted">${esc(dateTime(post.createdAt))} · ${esc(post.likeCount)} 赞 · ${esc(post.commentCount)} 条评论</p>
        </div>
        ${post.status === 'VISIBLE'
          ? `<a class="button quiet" href="${routeHref('post', post.id)}">查看帖子</a>`
          : '<span class="badge">已删除</span>'}
      </li>`).join('');
    const content = `<section class="panel student-list">
      ${rows ? `<ul>${rows}</ul>` : '<p class="empty">你还没有发布过社区帖子。</p>'}
    </section>`;
    ctx.show(accountLayout('personal', '我的帖子', '查看自己发布的社区帖子及状态。', content,
      `<a class="button secondary" href="${routeHref('personal')}">返回个人中心</a>`));
    return;
  }
  const content = `<div class="student-activity">${activityNav.map(([page, tab, glyph, label, description]) => `
    <a class="panel student-activity-link" href="${routeHref(page, tab)}">
      <span class="student-glyph">${icon(glyph, 23)}</span>
      <span><strong>${label}</strong><span class="muted">${description}</span></span>
      ${icon('arrow', 19)}
    </a>`).join('')}</div>`;
  ctx.show(accountLayout('personal', '个人中心', '查看交易进度、管理发布内容和个人资料。', content));
}

function ledgerAmount(log) {
  return (Number(log.amount) > 0 ? '+' : '') + esc(money(log.amount));
}

function ledgerOrder(log) {
  return log.orderId == null ? '—' : `<a href="${routeHref('order', log.orderId)}">#${esc(log.orderId)}</a>`;
}

function renderLedger(logs) {
  if (!logs.length) return '<p class="empty">暂无资金流水。充值或交易后，明细会显示在这里。</p>';
  return `<div class="student-table-wrap" tabindex="0" role="region" aria-label="资金流水明细">
    <table class="student-table">
      <thead><tr><th scope="col">类型</th><th scope="col">可用余额变动</th><th scope="col">变动后余额</th>
        <th scope="col">备注</th><th scope="col">关联订单</th><th scope="col">时间</th></tr></thead>
      <tbody>${logs.map(log => `
        <tr>
          <td><span class="badge">${esc(logTypes[log.type] || log.type)}</span></td>
          <td class="${Number(log.amount) > 0 ? 'student-credit' : ''}">${ledgerAmount(log)}</td>
          <td>${esc(money(log.balanceAfter))}</td>
          <td>${esc(log.remark || '—')}</td>
          <td>${ledgerOrder(log)}</td>
          <td>${esc(dateTime(log.createdAt))}</td>
        </tr>`).join('')}</tbody>
    </table></div>
    <ul class="student-ledger-list">${logs.map(log => `
      <li>
        <div class="student-ledger-line"><span class="badge">${esc(logTypes[log.type] || log.type)}</span><strong class="${Number(log.amount) > 0 ? 'student-credit' : ''}">${ledgerAmount(log)}</strong></div>
        <p class="muted">${esc(dateTime(log.createdAt))}</p>
        <dl>
          <div><dt>变动后余额</dt><dd>${esc(money(log.balanceAfter))}</dd></div>
          <div><dt>关联订单</dt><dd>${ledgerOrder(log)}</dd></div>
        </dl>
        ${log.remark ? `<p class="muted">${esc(log.remark)}</p>` : ''}
      </li>`).join('')}</ul>`;
}

async function renderWallet(ctx) {
  const options = { signal: ctx.signal };
  const [wallet, logs] = await Promise.all([api('/api/wallet', options), api('/api/wallet/logs', options)]);
  const content = `
    <div class="student-balances">
      <div class="panel student-balance student-balance-available">
        <span>${icon('wallet', 21)} 可用余额</span><strong data-student-balance>${esc(money(wallet.balance))}</strong>
      </div>
      <div class="panel student-balance">
        <span>${icon('clock', 21)} 冻结金额</span><strong data-student-frozen>${esc(money(wallet.frozen))}</strong>
      </div>
    </div>
    <section class="panel student-recharge">
      <h2>模拟充值</h2>
      <p class="muted">充值金额用于平台内演示，无需实际付款。</p>
      <form class="fields">
        <label class="field">充值金额（元）
          <input name="amount" type="number" min="0.01" step="0.01" max="99999999.99"
            inputmode="decimal" placeholder="0.00" required>
        </label>
        ${formError()}
        <button class="button" type="submit">确认充值</button>
      </form>
    </section>
    <section class="panel student-ledger">
      <h2>资金流水</h2><div data-student-ledger>${renderLedger(logs)}</div>
    </section>`;
  if (!ctx.show(accountLayout('wallet', '我的钱包', '查看余额、交易冻结金额及资金流水。', content))) return;
  const form = ctx.main.querySelector('form');
  const balance = ctx.main.querySelector('[data-student-balance]');
  const frozen = ctx.main.querySelector('[data-student-frozen]');
  const ledger = ctx.main.querySelector('[data-student-ledger]');
  attachForm(form, async ({ amount }) => {
    if (!/^(\d{1,8}(\.\d{1,2})?|\.\d{1,2})$/.test(amount) || Number(amount) <= 0) {
      throw new StudentValidationError('请输入大于 0 且最多两位小数的金额。');
    }
    const updated = await api('/api/wallet/recharge', { method: 'POST', body: { amount: Number(amount) }, signal: ctx.signal });
    if (!form.isConnected) return;
    balance.textContent = money(updated.balance);
    frozen.textContent = money(updated.frozen);
    form.reset();
    toast('模拟充值成功。');
    try {
      const updatedLogs = await api('/api/wallet/logs', options);
      if (ledger.isConnected) ledger.innerHTML = renderLedger(updatedLogs);
    } catch {
      if (ledger.isConnected) ledger.innerHTML = '<p class="empty">充值已到账，资金流水暂时无法刷新。请重新打开钱包查看。</p>';
    }
  });
}

async function renderReviews(ctx) {
  const id = ctx.args[0] || ctx.user.id;
  if (!/^[1-9]\d*$/.test(String(id))) throw new Error('用户不存在。');
  const reviews = await api('/api/reviews/user/' + encodeURIComponent(id), { signal: ctx.signal });
  const mine = String(id) === String(ctx.user.id);
  const rows = reviews.map(review => `
    <li class="student-item"><div><p class="student-post-copy">${esc(review.content)}</p>
      <p class="muted">同学 #${esc(review.fromUserId)} · ${esc(dateTime(review.createdAt))}</p>
    </div>${mine ? `<a class="button quiet" href="${routeHref('order', review.orderId)}">查看订单</a>` : ''}
    </li>`).join('');
  const content = `<section class="panel student-list">
    ${rows ? `<ul>${rows}</ul>` : '<p class="empty">暂无已完成订单的互评记录。</p>'}
  </section>`;
  if (mine) {
    ctx.show(accountLayout('reviews', '收到的评价', '来自已完成订单的交易评价。', content));
    return;
  }
  ctx.show(`
    <header class="page-head"><div><h1>同学 #${esc(id)} 的评价</h1><p class="muted">来自已完成订单的交易评价。</p></div></header>
    ${content}`);
}

export async function renderStudent(ctx) {
  switch (ctx.route) {
    case 'login': return renderAuth(ctx, false);
    case 'register': return renderAuth(ctx, true);
    case 'profile': return renderProfile(ctx);
    case 'personal': return renderPersonal(ctx);
    case 'wallet': return renderWallet(ctx);
    case 'reviews': return renderReviews(ctx);
  }
}
