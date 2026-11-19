<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import ModulePlaceholder from '@/components/ModulePlaceholder.vue'
</script>

<template>
  <div>
    <PageHeader
      title="用户管理"
      description="按用户名、昵称或编号检索账号，查看注册时间与状态；对违规学生账号执行封禁或解封。"
    />
    <ModulePlaceholder
      module="M5"
      title="用户列表与封禁解封将在 M5 交付"
      summary="管理员账号不可被封禁；封禁后该账号的旧登录凭证会立即失去受保护接口的访问权限。"
      :features="['关键字（用户名 / 昵称 / 编号）检索', '状态筛选', '封禁与解封', '影响说明与二次确认']"
    />
  </div>
</template>
