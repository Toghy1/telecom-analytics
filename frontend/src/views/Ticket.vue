<script setup>
/**
 * 在线报障（《开发文档》§6.4 #6）：提交工单 + 我的工单。
 *
 * ⚠️ 埋点 `submit_ticket` 是**交互动作类**（选型⑦），在提交成功那一刻手动上报，
 *    所以 `router/index.js` 里这一条**没有 `meta.track`**。写进去会变成一进页面
 *    就记一次——用户只是来看看列表，行为数据里却多了一张工单，而大屏板块①的
 *    「今日新增工单」数字卡取的正是这个数。
 *
 * ⚠️ 状态流转（待处理 → 处理中 → 已完成）**不在这里做**，用户端只读。
 *    那是管理后台的工单处理功能（`t_ticket.status` 状态机，本项目两处真实业务
 *    逻辑之一）。这里放开就等于把状态机入口敞开。
 */
import { onMounted, reactive, ref } from 'vue'
import { listTickets, submitTicket } from '@/api/ticket'
import { reportAction } from '@/utils/tracker'
import { TICKET_CONTENT_LIMIT, TICKET_STATUS, TICKET_TYPE } from '@/utils/constants'

/** 每页 5 条。假数据里当前用户有 6 条，正好第二页有内容，分页真的翻得动。 */
const PAGE_SIZE = 5

const rows = ref([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(true)
const submitting = ref(false)

const formRef = ref()
const form = reactive({ type: '', content: '' })

/**
 * 下拉选项由 `TICKET_TYPE` 常量表生成，不在模板里手写四个 `<el-option>`。
 * 加一类工单时只改常量表一处，表单和管理后台的筛选同时跟上。
 */
const typeOptions = Object.entries(TICKET_TYPE).map(([value, item]) => ({
  value,
  label: item.label
}))

const rules = {
  type: [{ required: true, message: '请选择报障类型', trigger: 'change' }],
  content: [
    { required: true, message: '请填写问题描述', trigger: 'blur' },
    {
      type: 'string',
      min: TICKET_CONTENT_LIMIT.min,
      max: TICKET_CONTENT_LIMIT.max,
      message: `问题描述需 ${TICKET_CONTENT_LIMIT.min}–${TICKET_CONTENT_LIMIT.max} 个字`,
      trigger: 'blur'
    }
  ]
}

async function loadTickets() {
  loading.value = true
  try {
    const data = await listTickets({ page: currentPage.value, size: PAGE_SIZE })
    rows.value = data.records
    total.value = data.total
    // 回写 current：后端会把越界的页码收敛到有效范围，以它返回的为准。
    currentPage.value = data.current
  } catch (e) {
    ElMessage.error(e.message || '工单列表加载失败')
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return // 校验没过。validate() 是 reject，不是抛异常。
  }

  submitting.value = true
  try {
    const data = await submitTicket({ type: form.type, content: form.content })

    // 埋点在提交**成功之后**上报。放在前面会记下根本没提交成功的工单。
    // ext 带 ticket_id，与《开发文档》§5.1.2「ext 可带工单 ID」一致。
    reportAction('submit_ticket', { ticket_id: data.id })

    ElMessage.success('工单已提交，我们会尽快处理')
    formRef.value.resetFields()

    // 回第 1 页——新工单的 create_time 最新，倒序必然排在第一页第一条。
    currentPage.value = 1
    await loadTickets()
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadTickets)
</script>

<template>
  <div class="ticket">
    <header class="ticket__head">
      <h2 class="ticket__title">在线报障</h2>
      <p class="ticket__sub">提交后由管理员处理，进度可在下方「我的工单」查看</p>
    </header>

    <!-- 提交工单 -->
    <section class="ticket__block">
      <h3 class="ticket__block-title">提交工单</h3>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        class="ticket__form"
      >
        <el-form-item label="报障类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择" class="ticket__type">
            <el-option
              v-for="item in typeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="问题描述" prop="content">
          <!-- maxlength 取常量而不是写 200：表单放行、接口拒收是最难查的一类问题。 -->
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            :maxlength="TICKET_CONTENT_LIMIT.max"
            show-word-limit
            placeholder="请描述遇到的问题，例如故障现象、发生时间、影响范围"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            提交工单
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 我的工单 -->
    <section class="ticket__block">
      <h3 class="ticket__block-title">
        我的工单
        <span class="ticket__block-extra">共 {{ total }} 条</span>
      </h3>

      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无工单">
        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">{{ row.create_time }}</template>
        </el-table-column>

        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            {{ TICKET_TYPE[row.type]?.label || '未知' }}
          </template>
        </el-table-column>

        <el-table-column
          prop="content"
          label="问题描述"
          min-width="240"
          show-overflow-tooltip
        />

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <!-- 状态只读。三个中文标签与色值都取自 TICKET_STATUS，
                 与将来管理后台的工单列表是同一份定义。 -->
            <el-tag
              size="small"
              effect="plain"
              :type="TICKET_STATUS[row.status]?.tag || 'info'"
            >
              {{ TICKET_STATUS[row.status]?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="处理完成时间" width="180">
          <template #default="{ row }">
            <!-- 未办结的工单没有处理完成时间，显示 '--' 而不是空白，
                 免得看着像「字段没加载出来」。 -->
            {{ row.handle_time || '--' }}
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        class="ticket__pager"
        background
        layout="total, prev, pager, next"
        :page-size="PAGE_SIZE"
        :total="total"
        @current-change="loadTickets"
      />
    </section>
  </div>
</template>

<style scoped>
.ticket__head {
  margin-bottom: 18px;
}

.ticket__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.ticket__sub {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.ticket__block {
  margin-top: 28px;
  padding: 18px 18px 8px;
  border-radius: var(--tp-radius);
  background: #fff;
}

.ticket__block:first-of-type {
  margin-top: 0;
}

.ticket__block-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 16px;
  font-weight: 600;
}

.ticket__block-extra {
  color: var(--tp-text-sub);
  font-size: 12px;
  font-weight: 400;
}

/* 表单不要铺满整行——textarea 拉到 1100px 宽的话，一行字能写半篇文章，
   反而看不出自己写了多少。限个宽度更好读。 */
.ticket__form {
  max-width: 620px;
}

.ticket__type {
  width: 100%;
}

.ticket__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0;
}
</style>
