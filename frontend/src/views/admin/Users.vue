<script setup>
/**
 * 用户管理（《开发文档》§6.6）：账号增删、启停、重置密码。
 *
 * ⚠️ **本页没有任何脱敏代码**，这是刻意的、也是红线。列表里的 `login_name` 与
 *    `id_card` 拿到的**本身就是脱敏态**——脱敏在后端 Entity → VO 完成（选型⑥）。
 *    「新增账号」表单收的是明文手机号，但那个明文只在表单里存在一瞬间：
 *    随请求体交给 `backend/business`，入库明文、出参脱敏。
 *    详见 `api/admin.js#createAccount` 与 `AdminAccountVO`。
 *
 * ⚠️ **本页也不产生任何埋点。** 十二个事件全属营业厅，且管理员按角色表
 *    「不产生行为数据」，`tracker.js#send()` 那道闸会把这里的一切拦掉。
 *    所以整页没有一处 `reportAction()` 调用，路由表里也不写 `meta.track`。
 */
import { onMounted, reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import {
  createAccount,
  deleteAccount,
  listAccounts,
  listPackageOptions,
  resetAccountPassword,
  updateAccountStatus
} from '@/api/admin'
import { CURRENCY, ROLE_LABEL, ROLE_TAG } from '@/utils/constants'
import { yuan } from '@/utils/format'

/** 每页 5 条，与营业厅各列表页同一口径。假数据 16 条 → 4 页，翻页真的翻得动。 */
const PAGE_SIZE = 5

const auth = useAuthStore()

const rows = ref([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(true)

/** 行级 loading 的粒度：只转被点的那一行，其余行禁用（与 Order.vue 同一做法）。 */
const submittingId = ref(null)

const filter = reactive({ role: '', status: '', keyword: '' })

const roleOptions = Object.entries(ROLE_LABEL).map(([value, label]) => ({ value, label }))

/** 状态下拉的取值是**数字** 0 / 1，与 `t_account.status` 一致。 */
const statusOptions = [
  { value: 1, label: '正常' },
  { value: 0, label: '停用' }
]

/* ---------- 新增账号弹窗 ---------- */

const dialogVisible = ref(false)
const formRef = ref()
const submitting = ref(false)
const packageOptions = ref([])

const form = reactive({
  login_name: '',
  role: 'USER',
  region: '',
  id_card: '',
  package_id: null,
  balance: 0
})

const rules = {
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  login_name: [
    { required: true, message: '请填写账号', trigger: 'blur' },
    {
      // 电信用户以手机号入网（《开发文档》§5.2 ①）；分析员与管理员用用户名。
      // 校验放在这里而不是只靠 api 层：api 层那道是给「绕过表单的调用」兜底的，
      // 两处都要有，但用户看到的必须是表单这一道（它会把字段标红）。
      validator: (_rule, value, callback) => {
        if (form.role === 'USER' && !/^1\d{10}$/.test(String(value ?? '').trim())) {
          callback(new Error('电信用户请填写 11 位手机号'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  id_card: [
    {
      // 可留空（真实入网也允许暂不补录），填了就必须 18 位——
      // 否则脱敏函数会原样返回，明文落进列表。
      validator: (_rule, value, callback) => {
        const s = String(value ?? '').trim()
        if (s && !/^\d{18}$/.test(s)) {
          callback(new Error('身份证号应为 18 位'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

/* ---------- 数据 ---------- */

async function loadAccounts() {
  loading.value = true
  try {
    const data = await listAccounts({
      page: currentPage.value,
      size: PAGE_SIZE,
      role: filter.role,
      status: filter.status,
      keyword: filter.keyword
    })
    rows.value = data.records
    total.value = data.total
    // 回写 current：后端会把越界页码收敛到有效范围，以它返回的为准。
    currentPage.value = data.current
  } catch (e) {
    ElMessage.error(e.message || '账号列表加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 任一筛选变化都回第 1 页再查。
 *
 * ⚠️ 不回第 1 页的后果很具体：在第 3 页筛出一个只有 1 页的结果，
 *    后端把页码收敛到 1、返回第 1 页的数据，但 `currentPage` 是后端回写的，
 *    所以看着还行——**直到筛选结果有 2 页而你停在第 3 页**，那时才露馅。
 *    统一在第 1 页发起，这个边界就不存在。
 */
function handleFilterChange() {
  currentPage.value = 1
  loadAccounts()
}

function handleReset() {
  filter.role = ''
  filter.status = ''
  filter.keyword = ''
  handleFilterChange()
}

/* ---------- 行操作 ---------- */

/**
 * 当前登录管理员自己那一行要特殊处理。
 *
 * ⚠️ 这里判断用的是 **`auth.account.id`**（登录时从 `api/auth.js` 拿到的当前账号），
 *    而不是去 import `mock/account.js` 里的 `mockAdminAccount`——
 *    **页面绝不直接 import mock**（选型⑤纪律①）。真后端同理由 JWT 给出当前用户，
 *    所以这样写接后端时一行都不用改。
 */
function isSelf(row) {
  return row.id === auth.account?.id
}

async function handleToggleStatus(row) {
  const enabling = row.status !== 1

  try {
    await ElMessageBox.confirm(
      enabling
        ? `确认恢复账号「${row.login_name}」的登录权限？`
        : `确认停用账号「${row.login_name}」？停用后该账号将无法登录。`,
      enabling ? '启用账号' : '停用账号',
      {
        confirmButtonText: enabling ? '确定启用' : '确定停用',
        cancelButtonText: '取消',
        type: enabling ? 'info' : 'warning'
      }
    )
  } catch {
    return // 用户点了取消。ElMessageBox 取消时是 reject，不是异常。
  }

  submittingId.value = row.id
  try {
    await updateAccountStatus({ id: row.id, status: enabling ? 1 : 0 })
    ElMessage.success(enabling ? '账号已启用' : '账号已停用')
    await loadAccounts()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    submittingId.value = null
  }
}

async function handleResetPassword(row) {
  try {
    await ElMessageBox.confirm(
      `确认将「${row.login_name}」的密码重置为初始密码？`,
      '重置密码',
      { confirmButtonText: '确定重置', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  submittingId.value = row.id
  try {
    await resetAccountPassword({ id: row.id })
    // 后端把初始口令 `123456` 的 BCrypt 哈希写回，不会把口令回传给浏览器。
    ElMessage.success('密码已重置为初始密码 123456')
  } catch (e) {
    ElMessage.error(e.message || '重置失败')
  } finally {
    submittingId.value = null
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`删除后不可恢复，确认删除账号「${row.login_name}」？`, '删除账号', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  submittingId.value = row.id
  try {
    await deleteAccount(row.id)
    ElMessage.success('账号已删除')
    // 删掉的可能正是本页最后一条，交给后端收敛页码（回写 data.current）
    await loadAccounts()
  } catch (e) {
    // 「存在业务数据，无法删除」这类由 api 层 reject 带上来。
    // ⚠️ 规则只在 api 层写一份，这里不预判、不重复实现一遍。
    ElMessage.error(e.message || '删除失败')
  } finally {
    submittingId.value = null
  }
}

/* ---------- 新增 ---------- */

function resetForm() {
  form.login_name = ''
  form.role = 'USER'
  form.region = ''
  form.id_card = ''
  form.package_id = null
  form.balance = 0
}

async function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
  // 弹窗里的下拉数据每次打开都拉一次；失败不挡着弹窗（下面的输入框还能填），
  // 只是 package 那一项会是空的。
  try {
    packageOptions.value = await listPackageOptions()
  } catch {
    packageOptions.value = []
  }
}

async function handleCreate() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return // 校验没过。validate() 是 reject，不是抛异常。
  }

  submitting.value = true
  try {
    await createAccount({
      login_name: form.login_name,
      role: form.role,
      region: form.region,
      id_card: form.id_card,
      package_id: form.package_id,
      balance: form.balance
    })
    ElMessage.success('账号已创建')
    dialogVisible.value = false
    // 回第 1 页：新账号 id 最大，而列表按 id 升序 → 它其实在**末页**。
    // 仍然回第 1 页，是为了与另两页的提交后行为一致（用户能预期自己在哪）。
    currentPage.value = 1
    await loadAccounts()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadAccounts)
</script>

<template>
  <div class="users">
    <header class="users__head">
      <h2 class="users__title">用户管理</h2>
      <p class="users__sub">
        账号的增删、启停与密码重置。手机号与身份证为脱敏值——脱敏由后端完成，本页不做任何处理。
      </p>
    </header>

    <section class="users__block">
      <h3 class="users__block-title">
        账号列表
        <span class="users__block-right">
          <span class="users__block-extra">共 {{ total }} 个</span>
          <el-button type="primary" @click="openCreateDialog">新增账号</el-button>
        </span>
      </h3>

      <!-- 筛选栏：项目里第一处「顶部筛选 + 下方列表」的结构，
           沿用白底块与既有配色，不自创视觉语言。 -->
      <el-form :inline="true" class="users__filter" @submit.prevent>
        <el-form-item label="角色">
          <el-select v-model="filter.role" class="users__filter-field" @change="handleFilterChange">
            <el-option label="全部" value="" />
            <el-option
              v-for="item in roleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="filter.status"
            class="users__filter-field"
            @change="handleFilterChange"
          >
            <el-option label="全部" value="" />
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="关键词">
          <el-input
            v-model="filter.keyword"
            class="users__filter-field"
            placeholder="账号 / 归属地"
            clearable
            @keyup.enter="handleFilterChange"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleFilterChange">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无账号">
        <el-table-column prop="login_name" label="账号" min-width="140" />

        <el-table-column label="角色" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="ROLE_TAG[row.role] || 'info'">
              {{ ROLE_LABEL[row.role] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="归属地" min-width="130">
          <template #default="{ row }">{{ row.region || '--' }}</template>
        </el-table-column>

        <el-table-column label="套餐" min-width="170">
          <template #default="{ row }">
            <!-- 分析员与管理员本就没有 package_id（角色表的「最小必要」），
                 这里显示 '--' 而不是空白，免得像「字段没加载出来」。 -->
            {{ row.package_name || '--' }}
          </template>
        </el-table-column>

        <el-table-column prop="id_card" label="身份证" width="180">
          <template #default="{ row }">{{ row.id_card || '--' }}</template>
        </el-table-column>

        <el-table-column label="余额" width="110" align="right">
          <template #default="{ row }">
            <!-- ⚠️ 判的是 `== null`（同时盖住 null 与 undefined），**不是 `!row.balance`**。
                 后者会把余额恰好为 0 的账号（1004 / 1008）显示成 '--'，
                 而「余额 0 元」和「没有余额这个概念」是两句完全不同的话。 -->
            {{ row.balance == null ? '--' : `${yuan(row.balance)} ${CURRENCY}` }}
          </template>
        </el-table-column>

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="create_time" label="入网时间" width="180" />

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <!-- 当前登录的管理员自己那一行不给操作入口。
                 灰掉比「点了再报错」好：规则在 api 层（`updateAccountStatus` /
                 `deleteAccount` 都拒绝 3001），这里只是不把它做成一个能点的陷阱。 -->
            <span v-if="isSelf(row)" class="users__self">当前账号</span>
            <template v-else>
              <el-button
                link
                :type="row.status === 1 ? 'danger' : 'success'"
                :loading="submittingId === row.id"
                :disabled="submittingId !== null && submittingId !== row.id"
                @click="handleToggleStatus(row)"
              >
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button
                link
                type="primary"
                :disabled="submittingId !== null"
                @click="handleResetPassword(row)"
              >
                重置密码
              </el-button>
              <el-button
                link
                type="danger"
                :disabled="submittingId !== null"
                @click="handleDelete(row)"
              >
                删除
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        class="users__pager"
        background
        layout="total, prev, pager, next"
        :page-size="PAGE_SIZE"
        :total="total"
        @current-change="loadAccounts"
      />
    </section>

    <!-- 新增账号 -->
    <el-dialog
      v-model="dialogVisible"
      title="新增账号"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" class="users__dialog-field">
            <el-option
              v-for="item in roleOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item :label="form.role === 'USER' ? '手机号' : '用户名'" prop="login_name">
          <el-input
            v-model="form.login_name"
            :placeholder="form.role === 'USER' ? '11 位手机号，如 13800001234' : '登录用用户名'"
          />
          <p v-if="form.role === 'USER'" class="users__dialog-tip">
            手机号仅用于创建，列表中显示的将是脱敏值。
          </p>
        </el-form-item>

        <el-form-item label="归属地" prop="region">
          <el-input v-model="form.region" placeholder="如 广东省深圳市" />
        </el-form-item>

        <!-- 只有电信用户才有套餐、余额、身份证——分析员与管理员根本没有这三个字段
             （角色表的「最小必要」，见 mock/account.js 的 mockAnalystAccount）。
             与其建完再显示一堆 '--'，不如一开始就不问。 -->
        <template v-if="form.role === 'USER'">
          <el-form-item label="身份证" prop="id_card">
            <el-input v-model="form.id_card" placeholder="18 位，可留空" maxlength="18" />
          </el-form-item>

          <el-form-item label="当前套餐" prop="package_id">
            <el-select v-model="form.package_id" class="users__dialog-field" clearable placeholder="不指定">
              <el-option
                v-for="item in packageOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="余额（元）" prop="balance">
            <el-input-number v-model="form.balance" :min="0" :precision="2" />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">确定创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.users__head {
  margin-bottom: 18px;
}

.users__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.users__sub {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.users__block {
  margin-top: 28px;
  padding: 18px 18px 8px;
  border-radius: var(--tp-radius);
  background: #fff;
}

.users__block:first-of-type {
  margin-top: 0;
}

.users__block-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 16px;
  font-weight: 600;
}

.users__block-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.users__block-extra {
  color: var(--tp-text-sub);
  font-size: 12px;
  font-weight: 400;
}

.users__filter {
  padding-bottom: 2px;
  border-bottom: 1px solid #ebeef5;
}

.users__filter-field {
  width: 160px;
}

.users__self {
  color: var(--tp-text-sub);
  font-size: 12px;
}

.users__dialog-field {
  width: 100%;
}

.users__dialog-tip {
  margin: 4px 0 0;
  color: var(--tp-text-sub);
  font-size: 12px;
  line-height: 1.4;
}

.users__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0;
}
</style>
