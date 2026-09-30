<script setup>
/**
 * 工单管理（《开发文档》§6.6）：查看、处理工单。
 *
 * ⚠️ **本页是本项目两处真实业务逻辑之二**（另一处是套餐办理），
 *    文档对它有明确警告：
 *
 *      「工单状态流转…**编码时不可从简到只剩一个下拉框改值**——
 *        状态迁移的合法性（如 `DONE` 不可回退到 `PENDING`）要在**后端**校验。」
 *
 *    「从简成一个下拉框」的问题不在于少写了几行代码，而在于**它把非法状态
 *    也做成了可选项**：下拉框一展开，`PENDING` / `PROCESSING` / `DONE` 三条
 *    平铺在管理员面前，「已完成的工单改回待处理」看起来与另两个选项毫无区别。
 *    状态机的意义正是「有些迁移不存在」，一个平铺的下拉框把这个意义抹掉了。
 *
 *    所以本页的做法是：**只渲染当前状态的唯一合法后继**（见 `TICKET_ACTION`）。
 *    `PENDING` 行只有一个「开始处理」按钮，`PROCESSING` 行只有一个「标记完成」，
 *    `DONE` 行**没有任何状态按钮**——非法迁移在界面上根本不可达，不是被拦住，
 *    而是压根没有那个入口。
 *
 *    另一半在 `api/admin.js#advanceTicket`：那里扮演后端再校验一次。
 *    两层互补而非重复——UI 这层挡「点错」，api 那层挡「绕过 UI 的调用」
 *    与「两个标签页同时操作同一张工单」。
 *
 * ⚠️ **本页走 `listAllTickets`，不是用户端的 `listTickets`。** 后者硬过滤了
 *    `CURRENT_USER_ID`；复用的话列表里只剩 1001 的工单，而 `mock/ticket.js` 里
 *    属 1002 的 7901 的 `create_time` 是全局最新、本该排第一行——少掉它一眼可见。
 *
 * 埋点：同另两个管理页，本页不产生任何行为事件。工单状态流转**不是**十二个
 * 埋点事件之一，别顺手补一个 `handle_ticket`——那张表是定稿的。
 */
import { onMounted, reactive, ref } from 'vue'
import { advanceTicket, listAllTickets } from '@/api/admin'
import { TICKET_STATUS, TICKET_TYPE } from '@/utils/constants'

const PAGE_SIZE = 5

const rows = ref([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(true)

/** 行级 loading：只转被点的那一行（与 Order.vue 同一做法）。 */
const submittingId = ref(null)

const filter = reactive({ type: '', status: '', keyword: '' })

const typeOptions = Object.entries(TICKET_TYPE).map(([value, item]) => ({
  value,
  label: item.label
}))

const statusOptions = Object.entries(TICKET_STATUS).map(([value, item]) => ({
  value,
  label: item.label
}))

/**
 * 状态机的**动作映射**——每个状态最多一个后继，`DONE` 没有。
 *
 * ⚠️ 这是**展示层**，不是状态机的定义处。真相在 `api/admin.js` 的
 *    `TICKET_TRANSITION`；这里多出来的只有「按钮叫什么、确认语怎么说」。
 *    两处必须一起改：`TICKET_TRANSITION` 加了新迁移而这里没加，
 *    表现为「后端允许但界面上点不到」——比反过来好，但仍是 bug。
 */
const TICKET_ACTION = {
  PENDING: {
    status: 'PROCESSING',
    label: '开始处理',
    type: 'primary',
    confirm: '确认开始处理该工单？处理人将记为当前登录管理员。'
  },
  PROCESSING: {
    status: 'DONE',
    label: '标记完成',
    type: 'success',
    confirm: '确认该工单已处理完成？完成后不可回退。'
  },
  // `DONE` 刻意**不在这里**：`TICKET_ACTION['DONE']` 是 undefined，
  // 模板里 `v-if="TICKET_ACTION[row.status]"` 就是「已办结」的判断，
  // 不需要额外写一次 `row.status !== 'DONE'`。
  DONE: null
}

/* ---------- 详情弹窗 ---------- */

const detailVisible = ref(false)
const detailRow = ref(null)

/* ---------- 数据 ---------- */

async function loadTickets() {
  loading.value = true
  try {
    const data = await listAllTickets({
      page: currentPage.value,
      size: PAGE_SIZE,
      type: filter.type,
      status: filter.status,
      keyword: filter.keyword
    })
    rows.value = data.records
    total.value = data.total
    currentPage.value = data.current
  } catch (e) {
    ElMessage.error(e.message || '工单列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  currentPage.value = 1
  loadTickets()
}

function handleReset() {
  filter.type = ''
  filter.status = ''
  filter.keyword = ''
  handleFilterChange()
}

/* ---------- 处理 ---------- */

function openDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}

async function handleAdvance(row) {
  const action = TICKET_ACTION[row.status]
  if (!action) return // 已办结，界面上本就不该有这个入口；防御性返回

  try {
    await ElMessageBox.confirm(action.confirm, '工单处理', {
      confirmButtonText: action.label,
      cancelButtonText: '取消',
      type: row.status === 'PENDING' ? 'info' : 'warning'
    })
  } catch {
    return // 用户点了取消。ElMessageBox 取消时是 reject，不是异常。
  }

  submittingId.value = row.id
  try {
    await advanceTicket({ id: row.id, status: action.status })
    ElMessage.success(`工单 #${row.id} 已${action.label}`)

    // 详情弹窗里触发的处理：处理完就关掉。不关的话弹窗里显示的还是处理**前**
    // 的状态（`detailRow` 是处理前抓的那一份），会出现「列表已经变了、
    // 弹窗还写着待处理」的自相矛盾。
    detailVisible.value = false

    // 状态筛选中（例如只看「待处理」）时，处理完这一条它就不再符合筛选条件，
    // 重拉之后会自动从当前页消失，条数也减一。这正是想要的。
    await loadTickets()
  } catch (e) {
    // 非法迁移（例如另一个标签页已经改过这张工单）由 api 层 reject 带上来。
    // 提示之后**重拉一次**，让界面回到与数据一致的状态——
    // 否则管理员会对着一张状态已经变了的卡片反复点。
    ElMessage.error(e.message || '操作失败')
    detailVisible.value = false
    await loadTickets()
  } finally {
    submittingId.value = null
  }
}

onMounted(loadTickets)
</script>

<template>
  <div class="tk">
    <header class="tk__head">
      <h2 class="tk__title">工单管理</h2>
      <p class="tk__sub">
        查看并处理用户提交的工单。状态按「待处理 → 处理中 → 已完成」单向流转，已完成不可回退。
      </p>
    </header>

    <section class="tk__block">
      <h3 class="tk__block-title">
        工单列表
        <span class="tk__block-extra">共 {{ total }} 条</span>
      </h3>

      <el-form :inline="true" class="tk__filter" @submit.prevent>
        <el-form-item label="类型">
          <el-select v-model="filter.type" class="tk__filter-field" @change="handleFilterChange">
            <el-option label="全部" value="" />
            <el-option
              v-for="item in typeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="filter.status" class="tk__filter-field" @change="handleFilterChange">
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
            class="tk__filter-field"
            placeholder="工单号 / 描述"
            clearable
            @keyup.enter="handleFilterChange"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleFilterChange">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无工单">
        <el-table-column prop="id" label="工单号" width="90" />

        <el-table-column label="提交人" min-width="130">
          <template #default="{ row }">{{ row.user_login_name }}</template>
        </el-table-column>

        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            {{ TICKET_TYPE[row.type]?.label || '未知' }}
          </template>
        </el-table-column>

        <el-table-column
          prop="content"
          label="问题描述"
          min-width="220"
          show-overflow-tooltip
        />

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <!-- 与用户端「我的工单」共用 TICKET_STATUS 的中文与色值——
                 同一个「处理中」在两处不能长得不一样。 -->
            <el-tag size="small" effect="plain" :type="TICKET_STATUS[row.status]?.tag || 'info'">
              {{ TICKET_STATUS[row.status]?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="处理人" width="120">
          <template #default="{ row }">{{ row.handler_name || '--' }}</template>
        </el-table-column>

        <el-table-column prop="create_time" label="提交时间" width="180" />

        <el-table-column label="办结时间" width="180">
          <template #default="{ row }">{{ row.handle_time || '--' }}</template>
        </el-table-column>

        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <!-- 状态按钮：**只有当前状态的唯一合法后继**。
                 `DONE` 行的 TICKET_ACTION[row.status] 是 null，整块 v-if 不渲染，
                 这一行只剩「详情」——「已完成不可回退」是在这里表达出来的，
                 不是靠一句提示文字。 -->
            <el-button
              v-if="TICKET_ACTION[row.status]"
              link
              :type="TICKET_ACTION[row.status].type"
              :loading="submittingId === row.id"
              :disabled="submittingId !== null && submittingId !== row.id"
              @click="handleAdvance(row)"
            >
              {{ TICKET_ACTION[row.status].label }}
            </el-button>

            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        class="tk__pager"
        background
        layout="total, prev, pager, next"
        :page-size="PAGE_SIZE"
        :total="total"
        @current-change="loadTickets"
      />
    </section>

    <!-- 工单详情。
         列表的「问题描述」列只能 show-overflow-tooltip 看一行，而正文上限 200 字
         （TICKET_CONTENT_LIMIT.max）——管理员读不到全文就没法决定怎么处理，
         所以这个弹窗是必需的，不是装饰。 -->
    <el-dialog v-model="detailVisible" :title="`工单 #${detailRow?.id ?? ''}`" width="640px">
      <template v-if="detailRow">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="类型">
            {{ TICKET_TYPE[detailRow.type]?.label || '未知' }}
          </el-descriptions-item>

          <el-descriptions-item label="状态">
            <el-tag
              size="small"
              effect="plain"
              :type="TICKET_STATUS[detailRow.status]?.tag || 'info'"
            >
              {{ TICKET_STATUS[detailRow.status]?.label || '未知' }}
            </el-tag>
          </el-descriptions-item>

          <el-descriptions-item label="提交人">
            {{ detailRow.user_login_name }}
          </el-descriptions-item>

          <el-descriptions-item label="提交时间">
            {{ detailRow.create_time }}
          </el-descriptions-item>

          <el-descriptions-item label="处理人">
            {{ detailRow.handler_name || '--' }}
          </el-descriptions-item>

          <el-descriptions-item label="办结时间">
            {{ detailRow.handle_time || '--' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="tk__detail-body">
          <h4 class="tk__detail-label">问题描述</h4>
          <!-- `white-space: pre-wrap` 让 200 字正文按用户输入的样子换行显示，
               不折成一整坨。 -->
          <p class="tk__detail-text">{{ detailRow.content }}</p>
        </div>
      </template>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <!-- 底部同样只给合法动作，与列表那一列是同一份映射 -->
        <el-button
          v-if="detailRow && TICKET_ACTION[detailRow.status]"
          :type="TICKET_ACTION[detailRow.status].type"
          :loading="submittingId === detailRow.id"
          @click="handleAdvance(detailRow)"
        >
          {{ TICKET_ACTION[detailRow.status].label }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tk__head {
  margin-bottom: 18px;
}

.tk__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.tk__sub {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.tk__block {
  margin-top: 28px;
  padding: 18px 18px 8px;
  border-radius: var(--tp-radius);
  background: #fff;
}

.tk__block:first-of-type {
  margin-top: 0;
}

.tk__block-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 16px;
  font-weight: 600;
}

.tk__block-extra {
  color: var(--tp-text-sub);
  font-size: 12px;
  font-weight: 400;
}

.tk__filter {
  padding-bottom: 2px;
  border-bottom: 1px solid #ebeef5;
}

.tk__filter-field {
  width: 160px;
}

.tk__detail-body {
  margin-top: 16px;
}

.tk__detail-label {
  margin: 0 0 6px;
  color: var(--tp-text-sub);
  font-size: 13px;
  font-weight: 400;
}

.tk__detail-text {
  margin: 0;
  padding: 12px;
  border-radius: 6px;
  background: var(--tp-bg);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.tk__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0;
}
</style>
