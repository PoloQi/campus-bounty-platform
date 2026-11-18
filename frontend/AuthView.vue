<script setup>
import {nextTick, onMounted, ref} from 'vue';
import {ElButton} from 'element-plus';
import {api, errorText, icon, routeHref, safeReturnRoute, toast} from './core.js';

const {context} = defineProps({context: {type: Object, required: true}});
const registering = context.route === 'register';
const next = safeReturnRoute(context.query.get('next'));
const suffix = next === '#/' ? '' : '?' + new URLSearchParams({next});
const target = routeHref(registering ? 'login' : 'register') + suffix;
const submitting = ref(false);
const feedback = ref('');
const feedbackElement = ref(null);

function validate(fields) {
  if (!fields.username) return '请输入用户名。';
  if (!registering) return '';
  if (fields.username.length < 4) return '用户名至少需要 4 位。';
  if (fields.password !== fields.confirmPassword) return '两次输入的密码不一致。';
  if (new TextEncoder().encode(fields.password).length > 72) return '密码不能超过 72 个 UTF-8 字节。';
  return '';
}

async function completeAuth(form, fields) {
  if (registering) {
    fields.nickname = fields.nickname.trim();
    await api('/api/auth/register', {method: 'POST', body: fields, signal: context.signal});
    if (!form.isConnected) return;
    toast('账号已创建，请登录。');
    location.hash = target;
    return;
  }

  const session = await api('/api/auth/login', {method: 'POST', body: fields, signal: context.signal});
  if (!form.isConnected) return;
  context.setSession(session.token, {
    id: session.userId, role: session.role, username: fields.username,
    nickname: session.nickname, displayName: session.nickname || fields.username,
  });
  if (session.role === 'ADMIN') {
    location.assign('/admin');
    return;
  }
  location.hash = next;
  toast('登录成功。');
}

async function submit(event) {
  if (submitting.value) return;
  const form = event.currentTarget;
  feedback.value = '';
  submitting.value = true;
  try {
    const fields = Object.fromEntries(new FormData(form));
    fields.username = fields.username.trim();
    feedback.value = validate(fields);
    if (!feedback.value) await completeAuth(form, fields);
  } catch (error) {
    if (form.isConnected) feedback.value = errorText(error);
  } finally {
    if (feedback.value) {
      await nextTick();
      feedbackElement.value?.focus();
    }
    submitting.value = false;
  }
}

onMounted(() => context.ready());
</script>

<template>
  <section class="student-auth">
    <div class="student-auth-intro">
      <span class="student-auth-mark" v-html="icon('ticket', 34)"></span>
      <p class="student-auth-display">校园里的事，<br>一起解决。</p>
      <p>有需要就发悬赏，有特长就挂牌服务，也可以来社区找搭子。</p>
    </div>
    <div class="panel student-auth-entry">
      <h1>{{ registering ? '注册校园账号' : '登录校园悬赏' }}</h1>
      <p class="muted">{{ registering ? '填写基本信息，创建学生账号。' : '欢迎回来，登录后开始校园互助。' }}</p>
      <form class="fields" @submit.prevent="submit">
        <label class="field">用户名
          <input name="username" required maxlength="20" :minlength="registering ? 4 : undefined"
            autocomplete="username" :placeholder="registering ? '输入 4–20 位用户名' : '输入用户名'">
        </label>
        <label class="field">密码
          <input name="password" type="password" required maxlength="32" :minlength="registering ? 6 : undefined"
            :autocomplete="registering ? 'new-password' : 'current-password'"
            :placeholder="registering ? '输入 6–32 位密码' : '输入密码'">
        </label>
        <template v-if="registering">
          <label class="field">确认密码
            <input name="confirmPassword" type="password" required minlength="6" maxlength="32"
              autocomplete="new-password" placeholder="再次输入密码">
          </label>
          <label class="field">昵称 <span class="muted">选填</span>
            <input name="nickname" maxlength="20" autocomplete="nickname" placeholder="留空时使用用户名">
          </label>
        </template>
        <p ref="feedbackElement" class="form-error" role="alert" tabindex="-1" :hidden="!feedback">{{ feedback }}</p>
        <ElButton class="button" native-type="submit" :disabled="submitting">
          {{ submitting ? '正在提交…' : registering ? '创建账号' : '登录' }}
        </ElButton>
      </form>
      <p class="student-auth-switch">{{ registering ? '已有账号？' : '还没有账号？' }}
        <a :href="target">{{ registering ? '去登录' : '立即注册' }}</a>
      </p>
    </div>
  </section>
</template>
