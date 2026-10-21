<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getDemoHint } from '@/api/auth'
import { reportAction } from '@/utils/tracker'
import { isRoleAllowed } from '@/router/access'
// 角色中文名原先在这里有一份局部副本，2026-09-30 提到 constants.js——
// 管理后台的「用户管理」页也要按角色显示中文名与标签色，
// 同一个角色名在登录页与管理后台不能有两种写法。
import { ROLE_LABEL } from '@/utils/constants'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// 假数据阶段的演示账号提示。接入真实后端后连同 getDemoHint 一起删除。
// 三个角色各一行——切角色只换登录标识，口令三个账号共用同一个。
const demoAccounts = getDemoHint()

/**
 * 各角色登录后的落脚页（见 claude.md 决策「分析平台角色守卫与登录分流」）。
 *
 * ⚠️ `ADMIN` 自 2026-09-30 起落**管理后台**（`/admin/users`）。此前暂落营业厅
 *    首页 `/`，是因为管理后台尚未建成、指不过去；建成后不再有理由让它落在
 *    一个与自己职责无关的页面上。
 *
 *    **仍然别顺手把 USER 的落点搬给 ADMIN**：`/user/dashboard` 是用户自助页，
 *    管理员进去看到的是 `mockAccount`（那个电信用户）的余额与账单，
 *    不是他自己的东西；`/user` 的 `roles` 也只列了 `USER`，进去照样会被守卫弹回来。
 *
 * ⚠️ 这里配的地址必须**能通过 `isRoleAllowed()`**——`resolveTarget()` 会把
 *    它交给守卫用的同一个函数再问一次。`/admin/*` 的 roles 含 ADMIN，所以成立；
 *    这一点在改本表时要想一遍：**回落目标自己进不去，登录就会来回弹。**
 */
const ROLE_HOME = {
  USER: '/user/dashboard',
  ANALYST: '/screen/realtime',
  ADMIN: '/admin/users'
}

const form = reactive({
  login_name: '', // 字段名对齐 t_account.login_name——用户填手机号，分析员 / 管理员填用户名
  password: ''
})

const submitting = ref(false)

/**
 * 登录成功后送哪儿。
 *
 * 带了 `redirect` 说明是「访问某个页面被守卫拦下来的」，本应送回原处。
 * 但**不能无条件送**：ANALYST 被 `/user/*` 拦下时 `redirect` 就是 `/user/dashboard`，
 * 送回去必然再被拦一次，来回弹。
 *
 * ⚠️ 所以拿目标地址的 meta **再问一次 `isRoleAllowed()`**——就是守卫用的那个函数，
 *    **不在这里另抄一份「谁能去哪」**（另抄一份的话，往后路由一改，这里不会报错，
 *    只会悄悄把人送到不该去的地方）。这就是「判断只有一份」的用法。
 *
 * ⚠️ `router.resolve()` 对匹配不到的地址会返回兜底路由（`/:pathMatch(.*)*`），
 *    它没有 `requiresAuth`，于是 `isRoleAllowed` 放行——这正是想要的：
 *    一个不存在的地址交给它自己去重定向，不必在这里替它判断。
 */
function resolveTarget(role) {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && isRoleAllowed(router.resolve(redirect).meta, role)) {
    return redirect
  }
  // 没带 redirect，或那个地址该角色去不了 → 落回自己的首页。
  // 不是一律「我的账户」：用户特意登录一趟是想看自己的东西，落到游客也能看的
  // 首页等于白登录；而分析员落到用户自助页更是文不对题。
  return ROLE_HOME[role] || '/'
}

async function handleSubmit() {
  if (!form.login_name.trim() || !form.password) {
    ElMessage.warning('请填写账号和密码')
    return
  }

  submitting.value = true
  try {
    const data = await auth.login({
      login_name: form.login_name.trim(),
      password: form.password
    })

    // 选型⑦：交互动作类事件手动上报。
    // 非电信用户（ANALYST / ADMIN）走到这一行会被拦掉——角色表写明两者
    // 「不产生行为数据」，而 `login` 恰是行为漏斗的**第二段**，放进去会污染漏斗计数。
    // 闸门在 utils/tracker.js 的 `send()`，这里照常调用、不必判断角色。
    reportAction('login')

    ElMessage.success('登录成功')
    router.replace(resolveTarget(data.account.role))
  } catch (e) {
    // 失败分支 mock 阶段会真的走到，不是摆设
    ElMessage.error(e.message || '登录失败')
  } finally {
    submitting.value = false
  }
}

function fillDemo(item) {
  form.login_name = item.login_name
  form.password = item.password
}
</script>

<template>
  <div class="login">
    <div class="login__card">
      <div class="login__brand">
        <span class="login__logo">电信</span>
        <span class="login__title">自助营业厅</span>
      </div>
      <p class="login__sub">电信用户用手机号，分析员 / 管理员用用户名</p>

      <el-form :model="form" label-position="top" @submit.prevent="handleSubmit">
        <el-form-item label="账号">
          <!-- ⚠️ 这里原先有 maxlength="11"，是给手机号用的。现在这个框也要收用户名，
               11 位上限就成了一个会**静默截断**用户名的坑——截断后登录失败，
               报的却是「账号或密码不正确」，排查方向会全错。故去掉。 -->
          <el-input
            v-model="form.login_name"
            placeholder="手机号 / 用户名"
            clearable
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>

        <el-button
          class="login__submit"
          type="primary"
          size="large"
          :loading="submitting"
          @click="handleSubmit"
        >
          登 录
        </el-button>
      </el-form>

      <el-alert class="login__demo" type="info" :closable="false" show-icon>
        <template #title>
          当前为假数据阶段，可用以下演示账号（口令均为 {{ demoAccounts[0]?.password }}）
        </template>
        <ul class="login__demo-list">
          <li v-for="item in demoAccounts" :key="item.login_name">
            <span class="login__demo-role">{{ ROLE_LABEL[item.role] }}</span>
            <code class="login__demo-id">{{ item.login_name }}</code>
            <el-button link type="primary" @click="fillDemo(item)">填入</el-button>
          </li>
        </ul>
      </el-alert>

      <div class="login__back">
        <el-button link @click="router.push({ name: 'home' })">← 返回首页</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 40px 20px;
  background: linear-gradient(135deg, #eef4ff 0%, #f7f9fc 100%);
}

.login__card {
  width: 100%;
  max-width: 400px;
  padding: 32px 32px 24px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(31, 111, 235, 0.1);
}

.login__brand {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8px;
}

.login__logo {
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--tp-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
}

.login__title {
  font-size: 19px;
  font-weight: 600;
  letter-spacing: 1px;
}

.login__sub {
  margin: 10px 0 24px;
  color: var(--tp-text-sub);
  font-size: 13px;
  text-align: center;
}

.login__submit {
  width: 100%;
  margin-top: 4px;
}

.login__demo {
  margin-top: 20px;
}

/* 演示账号列表：一行一个角色，列宽对齐，避免三个「填入」按钮参差 */
.login__demo-list {
  margin: 6px 0 0;
  padding: 0;
  list-style: none;
}

.login__demo-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 26px;
}

.login__demo-role {
  flex: none;
  width: 76px;
  color: var(--tp-text-sub);
}

.login__demo-id {
  flex: 1;
  font-family: Consolas, Monaco, 'Courier New', monospace;
}

.login__back {
  margin-top: 16px;
  text-align: center;
}
</style>
