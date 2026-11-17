/* Prioritize pending decisions and transaction evidence. Put filters ahead of records, then history.
   Use the site's ink, accent, surface and line colors, spaced on its 8 / 16 / 24 px scale. */
import {api,esc,icon,money,dateTime,errorText,toast} from './core.js';

const tabs = [
  ['dashboard','仪表盘','grid'], ['orders','订单管理','ticket'], ['appeals','申诉仲裁','book'],
  ['reports','举报处理','bell'], ['users','用户管理','users'], ['funds','资金监管','wallet'], ['audit','审计日志','list'],
];
const orders = {
  PENDING:'待接单', IN_PROGRESS:'进行中', CONFIRMING:'待确认', COMPLETED:'已完成', CANCELLED:'已取消', DISPUTED:'纠纷中',
};
const kinds = {BOUNTY:'需求悬赏',SERVICE:'技能服务'};
const userStatus = {ACTIVE:'正常',BANNED:'已封禁'};
const reportStatus = {PENDING:'待处理',HANDLED:'已处理'};
const reportResult = {VALID:'成立',REJECTED:'驳回'};
const subjects = {POST:'社区帖子',TRADE_POST:'交易帖',USER:'用户'};
const ledgerTypes = {RECHARGE:'模拟充值',FREEZE:'冻结',UNFREEZE:'解冻退款',PAY:'托管结算',INCOME:'订单收入'};
const actions = {
  HANDLE_REPORT:'处理举报', DELETE_POST:'删除帖子', CLOSE_TRADE_POST:'下架交易帖',
  BAN_USER:'封禁用户', UNBAN_USER:'解封用户', ARBITRATE_APPEAL:'申诉仲裁',
};
const timelineLabels = {
  ...ledgerTypes, BOUNTY_PUBLISHED:'发布悬赏', ORDER_CREATED:'购买服务', ORDER_ACCEPTED:'接单', BOUNTY_EXPIRED:'接单截止',
  ORDER_DELIVERED:'标记交付', ORDER_COMPLETED:'确认完成', ORDER_CANCELLED:'取消订单',
  APPEAL_CREATED:'发起申诉', APPEAL_WITHDRAWN:'撤回申诉', APPEAL_RESOLVED:'平台裁决',
};
const moderation = {
  POST:{label:actions.DELETE_POST,path:id=>`/posts/${id}/delete`},
  TRADE_POST:{label:actions.CLOSE_TRADE_POST,path:id=>`/services/${id}/close`},
  USER:{label:actions.BAN_USER,path:id=>`/users/${id}/ban`},
};
const adminApi = (ctx,path,options = {}) => api('/api/admin' + path,{...options,signal:ctx.signal});
const idValid = value => typeof value === 'string' && /^[1-9][0-9]*$/.test(value);
const label = (dictionary,key) => esc(dictionary[key] ?? key ?? '—');
const number = new Intl.NumberFormat('zh-CN');

export function adminHref(tab, params = {}) {
  const query = new URLSearchParams(params);
  return '/admin#/' + tab + (query.size ? '?' + query : '');
}

function navLink(tab,text,params) {
  return `<a href="${esc(adminHref(tab,params))}">${esc(text)}</a>`;
}

function queryString(ctx) {
  return ctx.query.size ? '?' + ctx.query : '';
}

const refresh = '<button class="button secondary" type="button" data-admin-refresh>刷新</button>';
const subhead = title => `<h2>${esc(title)}</h2>`;
const badge = (dictionary,key) => `<span class="badge">${label(dictionary,key)}</span>`;
const userLink = id => id != null ? navLink('users',`#${id}`,{keyword:id}) : '—';
const orderLink = id => id != null ? navLink('orders',`#${id}`,{id}) : '—';
const filtersStart = '<form class="admin-filters panel" data-admin-filters>';
const filtersEnd = '<div class="admin-filter-actions"><button type="submit" class="button">查询</button></div></form>';

function choice(name,text,options,current = '',emptyLabel = '全部') {
  return `<label class="field">${esc(text)}<select name="${name}"><option value="">${esc(emptyLabel)}</option>
    ${Object.entries(options).map(([value,caption]) => `<option value="${value}"${value === current ? ' selected' : ''}>${esc(caption)}</option>`).join('')}
  </select></label>`;
}

function input(ctx,name,text,type = 'text') {
  const numeric = type === 'id' ? ' inputmode="numeric" pattern="[1-9][0-9]*" maxlength="19"' : '';
  return `<label class="field">${esc(text)}<input name="${name}" type="${type === 'date' ? 'date' : 'text'}"${numeric} value="${esc(ctx.query.get(name))}"></label>`;
}

const dates = ctx => input(ctx,'from','开始日期','date') + input(ctx,'through','结束日期','date');

function table(title,columns,rows,empty) {
  if (!rows.length) return `<p class="panel empty">${esc(empty)}</p>`;
  return `<div class="panel admin-table" role="region" tabindex="0" aria-label="${esc(title)}"><table aria-label="${esc(title)}">
    <thead><tr>${columns.map(col => `<th scope="col">${esc(col)}</th>`).join('')}</tr></thead>
    <tbody>${rows.map(row => `<tr>${row.map(cell => `<td>${cell}</td>`).join('')}</tr>`).join('')}</tbody>
  </table></div>`;
}

function pagination(ctx,tab,page) {
  if (!page.page && !page.hasNext) return '';
  const url = number => {
    const params = new URLSearchParams(ctx.query);
    params.set('page',number);
    return esc(adminHref(tab,params));
  };
  return `<nav class="admin-pages" aria-label="翻页">
    ${page.page > 0 ? `<a class="button secondary" href="${url(page.page-1)}">上一页</a>` : ''}
    <span>第 ${number.format(page.page+1)} 页</span>
    ${page.hasNext ? `<a class="button secondary" href="${url(page.page+1)}">下一页</a>` : ''}
  </nav>`;
}

function bindFilters(ctx,body,tab) {
  const form = body.querySelector('[data-admin-filters]');
  if (!form) return;
  form.addEventListener('submit',event => {
    event.preventDefault();
    const params = new URLSearchParams();
    for (const [key,value] of new FormData(form)) {
      if (value.trim()) params.set(key,value.trim());
    }
    navigate(ctx,tab,params);
  });
}

function navigate(ctx,tab,params) {
  const hash = adminHref(tab,params).slice('/admin'.length);
  if (location.hash === hash) ctx.refresh();
  else location.hash = hash;
}

function metrics(items) {
  return `<dl class="panel admin-kpis">${items.map(([heading,value,detail]) => `<div><dt>${esc(heading)}</dt>
    <dd>${value}</dd>${detail ? `<dd class="admin-kpi-detail">${detail}</dd>` : ''}</div>`).join('')}</dl>`;
}

async function dashboard(ctx,body) {
  const {totals,distribution,trend} = await adminApi(ctx,'/dashboard');
  if (ctx.signal.aborted) return;
  const colors = ['#806347','#a54a31','#bb7c2c','#42684f','#6f707b','#7f507d'];
  let start = 0;
  const stops = distribution.map(({total},i) => {
    const end = start + total / (totals.orders || 1) * 100;
    const segment = `${colors[i]} ${start}% ${end}%`;
    start = end;
    return segment;
  }).join(',');
  body.innerHTML = `${metrics([
    ['学生用户',number.format(totals.students)+' 人'],['订单总量',number.format(totals.orders)+' 单'],
    ['已完成交易额',money(totals.gmv)],['冻结总额',money(totals.frozen)],
    ['待办',number.format(totals.openAppeals+totals.pendingReports)+' 项',
      navLink('appeals',`申诉 ${totals.openAppeals}`) + ' · ' + navLink('reports',`举报 ${totals.pendingReports}`,{status:'PENDING'})],
  ])}
    <div class="admin-charts">
      <section class="panel"><h2>订单状态分布</h2>
        ${totals.orders ? `<div class="admin-distribution"><div class="admin-pie" aria-hidden="true" style="--slices:${stops}"></div>
          <ul>${distribution.map(({status,total},i) => `<li><span class="admin-swatch" style="--swatch:${colors[i]}"></span>
            <span>${label(orders,status)}</span><strong>${number.format(total)} 单</strong></li>`).join('')}</ul></div>`
          : '<p class="empty">暂无订单。</p>'}
      </section>
      <section class="panel"><h2>近 7 天订单趋势</h2>
        ${trend.some(day => day.total) ? '<canvas class="admin-trend" width="560" height="210" aria-hidden="true"></canvas>'
          : '<p class="empty">近 7 天暂无新订单。</p>'}
        <ol class="admin-days">${trend.map(({orderDay,total}) => `<li><time datetime="${esc(orderDay)}">${esc(orderDay.slice(5).replace('-','/'))}</time>
          <strong>${number.format(total)} 单</strong></li>`).join('')}</ol>
      </section>
    </div>`;
  const canvas = body.querySelector('.admin-trend');
  if (canvas) drawOrderTrend(canvas,trend);
}

function drawOrderTrend(canvas,trend) {
  const drawing = canvas.getContext('2d');
  if (!drawing) return;
  const {width,height} = canvas;
  const max = Math.max(...trend.map(({total}) => total),1);
  const coordinates = trend.map(({total},i) => [
    (width / trend.length) * (i + .5), height - 16 - (height - 32) * total / max,
  ]);
  drawing.strokeStyle = '#e8ded1';
  for (const y of [16,height / 2,height - 16]) {
    drawing.beginPath();
    drawing.moveTo(0,y);
    drawing.lineTo(width,y);
    drawing.stroke();
  }
  drawing.beginPath();
  drawing.strokeStyle = '#9f3216';
  drawing.lineWidth = 3;
  coordinates.forEach(([x,y],i) => i ? drawing.lineTo(x,y) : drawing.moveTo(x,y));
  drawing.stroke();
  drawing.fillStyle = '#9f3216';
  for (const [x,y] of coordinates) {
    drawing.beginPath();
    drawing.arc(x,y,4,0,Math.PI*2);
    drawing.fill();
  }
}

export function orderSummary(detail) {
  const {order,post,buyer,seller} = detail;
  const person = (user,id) => user ? `${esc(user.nickname || user.username)} ${userLink(user.id)}` : userLink(id);
  return `<section class="panel stack"><h2>订单 #${esc(order.id)} · ${esc(post.title)}</h2>
    <dl class="admin-record"><div><dt>交易类型</dt><dd>${label(kinds,post.type)}</dd></div>
      <div><dt>当前状态</dt><dd>${badge(orders,order.status)}</dd></div>
      <div><dt>订单金额</dt><dd>${money(order.amount)}</dd></div>
      <div><dt>出资方</dt><dd>${person(buyer,order.buyerId)}</dd></div>
      <div><dt>接单方</dt><dd>${person(seller,order.sellerId)}</dd></div>
      <div><dt>创建时间</dt><dd>${dateTime(order.createdAt)}</dd></div></dl>
    ${detail.appeals?.length ? `<div class="toolbar">${detail.appeals.map(appeal => navLink('appeals',`申诉 #${appeal.id}`,{id:appeal.id})).join('')}</div>` : ''}
  </section>`;
}

export function orderTimeline(detail) {
  return `<section class="panel stack"><h2>订单时间线</h2><ol class="admin-timeline">
    ${detail.timeline.map(event => `<li><div><strong>${label(timelineLabels,event.type)}</strong>
      <time>${dateTime(event.createdAt)}</time></div><p>${esc(event.details)}${event.actorId != null ? ' · ' + userLink(event.actorId) : ''}</p></li>`).join('')}
  </ol></section>${subhead('关联资金流水')}${ledgerTable(detail.logs)}`;
}

function ledgerTable(entries) {
  return table('资金流水',['流水号','用户','类型','可用余额变动','变动后余额','订单','时间','摘要'],entries.map(row =>
    [`#${esc(row.id)}`,userLink(row.userId),label(ledgerTypes,row.type),money(row.amount),money(row.balanceAfter),
      orderLink(row.orderId),dateTime(row.createdAt),esc(row.remark)]),'暂无匹配流水。');
}

async function orderPage(ctx,body) {
  const id = ctx.query.get('id');
  if (idValid(id)) {
    const detail = await adminApi(ctx,`/orders/${id}`);
    if (!ctx.signal.aborted) body.innerHTML = `${navLink('orders','返回订单列表')}${orderSummary(detail)}${orderTimeline(detail)}`;
    return;
  }
  const page = await adminApi(ctx,'/orders'+queryString(ctx));
  if (ctx.signal.aborted) return;
  body.innerHTML = `${filtersStart}
    ${choice('status','订单状态',orders,ctx.query.get('status'))}${choice('type','交易类型',kinds,ctx.query.get('type'))}
    ${input(ctx,'buyerId','出资方编号','id')}${input(ctx,'sellerId','接单方编号','id')}${dates(ctx)}${filtersEnd}
    ${table('订单列表',['订单','交易帖','类型','出资方','接单方','金额','状态','创建时间'],page.items.map(row => [
      orderLink(row.id),esc(row.title),label(kinds,row.type),userLink(row.buyerId),userLink(row.sellerId),
      money(row.amount),badge(orders,row.status),dateTime(row.createdAt),
    ]),'暂无匹配订单。')}${pagination(ctx,'orders',page)}`;
}

function confirmAdmin(ctx,body,{title,summary,submit,request,success}) {
  const dialog = document.createElement('dialog');
  dialog.innerHTML = `<form method="dialog" class="fields">
    <h2 id="admin-confirm-heading">${esc(title)}</h2><p class="admin-confirm-summary">${esc(summary)}</p>
    <p class="form-error" role="alert"></p><div class="toolbar"><button class="button secondary" value="cancel">返回</button>
    <button class="button danger" value="confirm">${esc(submit)}</button></div></form>`;
  dialog.setAttribute('aria-labelledby','admin-confirm-heading');
  body.append(dialog);
  const confirm = dialog.querySelector('[value=confirm]');
  const cancel = dialog.querySelector('[value=cancel]');
  dialog.addEventListener('close',() => dialog.remove(),{once:true});
  dialog.addEventListener('cancel',event => { if (confirm.disabled) event.preventDefault(); });
  dialog.querySelector('form').addEventListener('submit',async event => {
    if (event.submitter !== confirm) return;
    event.preventDefault();
    if (confirm.disabled) return;
    confirm.disabled = cancel.disabled = true;
    dialog.querySelector('[role=alert]').textContent = '';
    try {
      await request();
      if (ctx.signal.aborted) return;
      dialog.close();
      toast(success);
      ctx.refresh();
    } catch (error) {
      if (!ctx.signal.aborted) dialog.querySelector('[role=alert]').textContent = errorText(error);
    } finally {
      confirm.disabled = cancel.disabled = false;
    }
  });
  dialog.showModal();
  cancel.focus();
}

async function reportDetail(ctx,body,id) {
  const report = await adminApi(ctx,`/reports/${id}`);
  const target = await adminApi(ctx,`/moderation/${report.targetType}/${report.targetId}`).catch(error => {
    if (error.code === 40000) return null;
    throw error;
  });
  if (ctx.signal.aborted) return;
  const action = moderation[report.targetType];
  body.innerHTML = `${navLink('reports','返回举报列表')}
  <section class="panel stack"><h2>举报 #${esc(report.id)}</h2>
    <dl class="admin-record"><div><dt>举报状态</dt><dd>${badge(reportStatus,report.status)}</dd></div>
      <div><dt>举报人</dt><dd>${userLink(report.reporterId)}</dd></div>
      <div><dt>对象</dt><dd>${label(subjects,report.targetType)} #${esc(report.targetId)}</dd></div>
      <div><dt>提交时间</dt><dd>${dateTime(report.createdAt)}</dd></div>
      <div><dt>举报原因</dt><dd>${esc(report.reason)}</dd></div>
      <div><dt>对象摘要</dt><dd>${esc(target?.summary ?? '该对象已不存在')}</dd></div></dl>
  </section>
  ${report.status === 'HANDLED' ? `<section class="panel stack"><h2>处理结果</h2>
    <dl class="admin-record"><div><dt>结论</dt><dd>${badge(reportResult,report.result)}</dd></div>
      <div><dt>处理意见</dt><dd>${esc(report.comment)}</dd></div>
      <div><dt>处理人</dt><dd>${userLink(report.adminId)}</dd></div>
      <div><dt>处理时间</dt><dd>${dateTime(report.handledAt)}</dd></div>
      ${report.moderationAction ? `<div><dt>联动动作</dt><dd>${label(actions,report.moderationAction)}</dd></div>` : ''}</dl></section>`
    : `<section class="panel stack"><h2>处理举报</h2><form class="fields" data-resolve-report>
      ${choice('result','处理结果',reportResult,'','请选择')}
      <label class="field">处理意见<textarea name="comment" maxlength="160" rows="4" required></textarea></label>
      ${action && target ? `<label class="admin-checkbox"><input type="checkbox" name="moderate" disabled> 同时${action.label}</label>` : ''}
      <div><button class="button" type="submit">核对并提交</button></div>
    </form></section>`}`;
  const form = body.querySelector('[data-resolve-report]');
  if (!form) return;
  const result = form.elements.result;
  const moderate = form.elements.moderate;
  result.required = true;
  result.addEventListener('change',() => {
    if (moderate) {
      moderate.disabled = result.value !== 'VALID';
      if (moderate.disabled) moderate.checked = false;
    }
  });
  form.addEventListener('submit',event => {
    event.preventDefault();
    const comment = form.elements.comment.value.trim();
    if (!comment) return form.elements.comment.focus();
    const request = {result:result.value,comment,moderate:moderate?.checked ?? false};
    confirmAdmin(ctx,body,{title:'确认结案',submit:'确认处理',
      summary:`举报 #${id} 将标为${reportResult[request.result]}。${request.moderate ? `同时${action.label} #${report.targetId}。` : ''}举报人将收到处理结果和意见。`,
      request:() => adminApi(ctx,`/reports/${id}/handle`,{method:'POST',body:request}),success:'举报已处理。',
    });
  });
}

async function reportPage(ctx,body) {
  const id = ctx.query.get('id');
  if (idValid(id)) return reportDetail(ctx,body,id);
  const page = await adminApi(ctx,'/reports/query'+queryString(ctx));
  if (ctx.signal.aborted) return;
  body.innerHTML = `${filtersStart}
    ${choice('status','举报状态',reportStatus,ctx.query.get('status'))}${choice('targetType','对象类型',subjects,ctx.query.get('targetType'))}${filtersEnd}
    ${table('举报列表',['举报','对象','举报人','原因','状态','提交时间'],page.items.map(row => [
      navLink('reports',`#${row.id}`,{id:row.id}),`${label(subjects,row.targetType)} #${esc(row.targetId)}`,
      userLink(row.reporterId),esc(row.reason),badge(reportStatus,row.status),dateTime(row.createdAt),
    ]),'暂无匹配举报。')}${pagination(ctx,'reports',page)}
  <details class="panel admin-direct"><summary>按编号治理</summary><form class="admin-filters" data-direct-moderation>
    ${choice('type','对象类型',subjects,'','请选择')}
    <label class="field">对象编号<input name="id" pattern="[1-9][0-9]*" maxlength="19" inputmode="numeric" required></label>
    <div class="admin-filter-actions"><button class="button secondary" type="submit">查看对象</button></div>
    <p class="form-error" role="alert"></p>
  </form></details>`;
  const direct = body.querySelector('[data-direct-moderation]');
  direct.elements.type.required = true;
  direct.addEventListener('submit',async event => {
    event.preventDefault();
    const type = direct.elements.type.value;
    const id = direct.elements.id.value;
    const action = moderation[type];
    const button = direct.querySelector('button');
    if (button.disabled || !idValid(id) || !action) return;
    const message = direct.querySelector('.form-error');
    button.disabled = true;
    message.textContent = '';
    try {
      const target = await adminApi(ctx,`/moderation/${type}/${id}`);
      if (ctx.signal.aborted) return;
      const impact = type === 'USER' ? '封禁后该账号无法登录或使用受保护功能。'
        : '该内容将从公共列表隐藏，已有交易照常处理。';
      confirmAdmin(ctx,body,{title:action.label,summary:`#${id} ${target.summary}。 ${impact}`,submit:action.label,
        request:() => adminApi(ctx,action.path(id),{method:'POST'}),success:'治理操作已执行。',
      });
    } catch (error) {
      if (!ctx.signal.aborted) message.textContent = errorText(error);
    } finally {
      button.disabled = false;
    }
  });
}

async function userPage(ctx,body) {
  const page = await adminApi(ctx,'/users'+queryString(ctx));
  if (ctx.signal.aborted) return;
  body.innerHTML = `${filtersStart}${input(ctx,'keyword','用户名、昵称或编号')}
    ${choice('status','账号状态',userStatus,ctx.query.get('status'))}${filtersEnd}
    ${table('用户列表',['编号','用户名','昵称','角色','状态','注册时间','操作'],page.items.map(row => [
      `#${esc(row.id)}`,esc(row.username),esc(row.nickname),label({ADMIN:'管理员',STUDENT:'学生'},row.role),
      badge(userStatus,row.status),dateTime(row.createdAt),row.role === 'STUDENT' ?
        `<button class="button secondary" type="button" data-user-status="${esc(row.id)}">${row.status === 'BANNED' ? '解封' : '封禁'}</button>` : '—',
    ]),'暂无匹配用户。')}${pagination(ctx,'users',page)}`;
  body.querySelectorAll('[data-user-status]').forEach(button => {
    const user = page.items.find(row => String(row.id) === button.dataset.userStatus);
    const unban = user.status === 'BANNED';
    const action = unban ? '解封用户' : '封禁用户';
    button.addEventListener('click',() => confirmAdmin(ctx,body,{
      title:action,submit:action,summary:`账号 ${user.username}（#${user.id}）。${unban ?
        '解封后可重新登录。' : '封禁后无法登录，已有登录凭证将失去受保护接口访问权限。'}`,
      request:() => adminApi(ctx,`/users/${user.id}/${unban ? 'unban' : 'ban'}`,{method:'POST'}),success:action+'成功。',
    }));
  });
}

async function fundsPage(ctx,body) {
  const id = ctx.query.get('userId');
  const [totals,page,wallet] = await Promise.all([
    adminApi(ctx,'/funds'),adminApi(ctx,'/funds/ledger'+queryString(ctx)),
    idValid(id) ? adminApi(ctx,`/funds/wallets/${id}`) : null,
  ]);
  if (ctx.signal.aborted) return;
  body.innerHTML = `${metrics([
    ['平台冻结总额',money(totals.frozen)],['在途订单应冻金额',money(totals.expected)],
    ['对账差额',money(totals.difference),Number(totals.difference) === 0 ? '核对一致' : '<strong class="admin-discrepancy">资金核对异常</strong>'],
  ])}
    ${filtersStart}${input(ctx,'userId','用户编号','id')}${choice('type','流水类型',ledgerTypes,ctx.query.get('type'))}
    ${input(ctx,'orderId','订单编号','id')}${dates(ctx)}${filtersEnd}
    ${wallet ? metrics([[`用户 #${wallet.userId} 可用余额`,money(wallet.balance)],['冻结金额',money(wallet.frozen)]])
      : idValid(id) ? `<p class="panel empty">用户 #${esc(id)} 暂无钱包记录。</p>` : ''}
    ${ledgerTable(page.items)}${pagination(ctx,'funds',page)}`;
}

async function auditPage(ctx,body) {
  const page = await adminApi(ctx,'/audit'+queryString(ctx));
  if (ctx.signal.aborted) return;
  body.innerHTML = `${filtersStart}${input(ctx,'adminId','管理员编号','id')}${choice('action','动作',actions,ctx.query.get('action'))}
    ${dates(ctx)}${filtersEnd}
    ${table('审计日志',['日志','管理员','动作','目标','详情','时间'],page.items.map(row => [
      `#${esc(row.id)}`,userLink(row.adminId),label(actions,row.action),
      row.targetType === 'APPEAL' ? navLink('appeals',`申诉 #${row.targetId}`,{id:row.targetId})
        : row.targetType === 'REPORT' ? navLink('reports',`举报 #${row.targetId}`,{id:row.targetId})
          : `${label(subjects,row.targetType)} #${esc(row.targetId)}`,
      esc(row.details),dateTime(row.createdAt),
    ]),'暂无匹配日志。')}${pagination(ctx,'audit',page)}`;
}

const pages = {
  dashboard, orders:orderPage, reports:reportPage, users:userPage, funds:fundsPage, audit:auditPage,
  appeals:async (ctx,body) => (await import('./admin-appeals.js')).renderAppeals(ctx,body),
};

export async function renderAdmin(ctx) {
  const [tab,title] = tabs.find(([key]) => key === ctx.args[0]) ?? tabs[0];
  if (!ctx.show(`<nav class="admin-nav" aria-label="管理端导航">${tabs.map(([key,text,glyph]) =>
    `<a href="${adminHref(key)}"${key === tab ? ' aria-current="page"' : ''}>${icon(glyph,18)}<span>${text}</span></a>`).join('')}</nav>
    <header class="page-head"><h1>${title}</h1>${refresh}</header>
    <div class="admin-content stack" aria-busy="true"><p class="empty" role="status">加载中…</p></div>`)) return;
  document.title = title + ' · 校园悬赏管理端';
  ctx.main.querySelector('[data-admin-refresh]').addEventListener('click',ctx.refresh);
  const body = ctx.main.querySelector('.admin-content');
  try {
    await pages[tab](ctx,body);
    if (!ctx.signal.aborted) bindFilters(ctx,body,tab);
  } catch (error) {
    if (!ctx.signal.aborted) {
      body.innerHTML = `<p class="panel empty" role="alert">${esc(errorText(error))}</p>`;
      if (!error.status) console.error(error);
    }
  } finally {
    body.setAttribute('aria-busy','false');
  }
}
