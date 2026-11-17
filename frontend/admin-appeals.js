import {api, dateTime, errorText, esc, money, toast} from './core.js';
import {adminHref, orderSummary, orderTimeline} from './admin.js';

const statuses = {OPEN: '待裁决', RESOLVED: '已裁决', WITHDRAWN: '已撤回'};
const fromStatuses = {IN_PROGRESS: '进行中', CONFIRMING: '待确认'};
const outcomes = {RELEASE_TO_SELLER: '打款给卖家', REFUND_TO_BUYER: '退款给买家'};

export async function renderAppeals(ctx, body) {
  const id = ctx.query.get('id');
  if (id) {
    return renderAppealDetail(ctx, body, id);
  }

  const status = ctx.query.get('status') || '';
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  params.set('page', ctx.query.get('page') || '0');
  const results = await api('/api/admin/appeals?' + params, {signal: ctx.signal});
  if (ctx.signal.aborted) return;
  body.innerHTML = `<section class="panel stack">
    <div class="toolbar"><h2>订单申诉</h2>
      <label class="field">申诉状态<select data-appeal-filter>
        <option value="">全部</option>
        ${Object.entries(statuses).map(([key, label]) =>
          `<option value="${key}" ${key === status ? 'selected' : ''}>${label}</option>`).join('')}
      </select></label>
    </div>
    ${results.items.length ? results.items.map(appeal => `
      <a class="list-row" href="${adminHref('appeals', {id: appeal.id})}">
        <div class="stack">
          <strong>申诉 #${esc(appeal.id)} · 订单 #${esc(appeal.orderId)}</strong>
          <span class="market-description">${esc(appeal.reason)}</span>
          <small class="muted">申诉人 #${esc(appeal.appellantId)} · ${esc(fromStatuses[appeal.fromStatus])} · ${esc(dateTime(appeal.createdAt))}</small>
        </div>
        <span class="badge">${esc(statuses[appeal.status])}</span>
      </a>`).join('') : '<p class="empty">没有符合条件的申诉。</p>'}
    ${results.totalPages > 1 ? `<nav class="toolbar" aria-label="申诉分页">
      ${results.page > 0 ? `<a class="button secondary" href="${esc(adminHref('appeals', {status, page: results.page - 1}))}">上一页</a>` : ''}
      <span>第 ${results.page + 1} / ${results.totalPages} 页</span>
      ${results.page + 1 < results.totalPages ? `<a class="button secondary" href="${esc(adminHref('appeals', {status, page: results.page + 1}))}">下一页</a>` : ''}
    </nav>` : ''}
  </section>`;
  body.querySelector('[data-appeal-filter]').addEventListener('change', event => {
    window.location.href = adminHref('appeals', {status: event.currentTarget.value});
  });
}

async function renderAppealDetail(ctx, body, id) {
  const endpoint = '/api/admin/appeals/' + encodeURIComponent(id);
  const appeal = await api(endpoint, {signal: ctx.signal});
  const detail = await api('/api/admin/orders/' + encodeURIComponent(appeal.orderId), {signal: ctx.signal});
  if (ctx.signal.aborted) return;
  const canArbitrate = appeal.status === 'OPEN' && detail.order.status === 'DISPUTED';
  body.innerHTML = `<div class="stack">
    <div><a class="button secondary" href="${adminHref('appeals')}">返回申诉列表</a></div>
    <section class="panel stack">
      <div class="toolbar"><h2>申诉 #${esc(id)}</h2><span class="badge">${esc(statuses[appeal.status])}</span></div>
      <p class="muted">申诉人 #${esc(appeal.appellantId)} · 发起于 ${esc(dateTime(appeal.createdAt))} · 申诉前状态 ${esc(fromStatuses[appeal.fromStatus])}</p>
      <p class="market-description">${esc(appeal.reason)}</p>
      ${appeal.status === 'RESOLVED' ? `<div class="market-appeal is-resolved">
        <h3>裁决结果</h3><strong>${esc(outcomes[appeal.outcome])}</strong>
        <p class="market-description">${esc(appeal.adminComment)}</p>
        <small>管理员 #${esc(appeal.adminId)} · ${esc(dateTime(appeal.handledAt))}</small>
      </div>` : ''}
    </section>
    ${orderSummary(detail)}
    ${orderTimeline(detail)}
    ${canArbitrate ? arbitrationForm() : ''}
  </div>`;
  if (canArbitrate) bindArbitration(ctx, body, endpoint, detail.order);
}

function arbitrationForm() {
  return `<section class="panel stack">
    <h2>裁决申诉</h2>
    <form class="fields" data-arbitration>
      <label class="field">裁决结果<select name="outcome" required>
        <option value="">选择处理方式</option>
        ${Object.entries(outcomes).map(([value, label]) => `<option value="${value}">${label}</option>`).join('')}
      </select></label>
      <label class="field">处理意见<textarea name="comment" maxlength="500" rows="4" required></textarea></label>
      <div><button class="button" type="submit">核对裁决</button></div>
    </form>
    <dialog data-confirm-arbitration aria-labelledby="confirm-arbitration-title">
      <form method="dialog" class="fields">
        <h2 id="confirm-arbitration-title">确认裁决</h2>
        <p data-arbitration-summary></p>
        <p class="form-error" role="alert"></p>
        <div class="toolbar">
          <button type="submit" value="cancel" class="button secondary">返回修改</button>
          <button type="submit" value="confirm" class="button danger">确认裁决</button>
        </div>
      </form>
    </dialog>
  </section>`;
}

function bindArbitration(ctx, body, endpoint, order) {
  const form = body.querySelector('[data-arbitration]');
  const dialog = body.querySelector('[data-confirm-arbitration]');
  const confirm = dialog.querySelector('[value=confirm]');
  const cancel = dialog.querySelector('[value=cancel]');
  const error = dialog.querySelector('.form-error');
  let payload;
  form.addEventListener('submit', event => {
    event.preventDefault();
    const comment = form.elements.comment.value.trim();
    if (!comment) {
      form.elements.comment.setCustomValidity('请填写处理意见');
      form.elements.comment.reportValidity();
      return;
    }
    payload = {outcome: form.elements.outcome.value, comment};
    dialog.querySelector('[data-arbitration-summary]').textContent =
      `订单 #${order.id} · ${outcomes[payload.outcome]} · ${money(order.amount)}。裁决会立即处理托管款并通知双方。`;
    error.textContent = '';
    dialog.showModal();
    cancel.focus();
  });
  form.elements.comment.addEventListener('input', () => form.elements.comment.setCustomValidity(''));
  dialog.addEventListener('cancel', event => { if (confirm.disabled) event.preventDefault(); });
  dialog.querySelector('form').addEventListener('submit', async event => {
    if (event.submitter !== confirm) return;
    event.preventDefault();
    if (!payload || confirm.disabled) return;
    confirm.disabled = cancel.disabled = true;
    error.textContent = '';
    try {
      await api(endpoint + '/arbitrate', {method: 'POST', body: payload, signal: ctx.signal});
      if (ctx.signal.aborted) return;
      dialog.close();
      toast('裁决已完成，双方已收到通知。');
      ctx.refresh();
    } catch (cause) {
      if (!ctx.signal.aborted) error.textContent = errorText(cause);
    } finally {
      confirm.disabled = cancel.disabled = false;
    }
  });
}
