<script setup>
/**
 * 套餐配置（《开发文档》§6.6）：套餐配置增删改查。
 *
 * ⚠️ **本页走 `listAdminPackages`，不是营业厅的 `listPackages`。** 两者口径相反：
 *    后者过滤 `status === 1`（回答「现在能办哪些」），前者不过滤（回答「系统里有哪些」）。
 *    用错的话，已停售的「学生特惠 29 元套餐」会从这里**凭空消失且不报错**——
 *    而它恰恰是管理端最该看见的那一条：要下架一个套餐，得先找得到它。
 *
 * ⚠️ 「删除」在有引用时会被 api 层**拒绝**，正确的动作是「停售」。
 *    本页**不预先判断**能不能删，也不把按钮灰掉——规则只在 api 层写一份
 *    （`deletePackage` 的引用守卫），页面对所有行给出同样的按钮，
 *    点下去由 api 用 reject 的文案说明原因。理由：UI 再实现一遍等于
 *    把同一条业务规则写两处，改一处漏一处时，界面会给出与实际不符的暗示。
 *
 * 埋点：同用户管理页，本页不产生任何行为事件。
 */
import { onMounted, reactive, ref } from 'vue'
import {
  createPackage,
  deletePackage,
  listAdminPackages,
  updatePackage
} from '@/api/admin'
import { CURRENCY, PACKAGE_UNITS } from '@/utils/constants'
import { quantity, yuan } from '@/utils/format'

const PAGE_SIZE = 5

const rows = ref([])
const total = ref(0)
const currentPage = ref(1)
const loading = ref(true)

/** 行级 loading：只转被点的那一行（与 Order.vue / Users.vue 同一做法）。 */
const submittingId = ref(null)

const filter = reactive({ status: '', keyword: '' })

const statusOptions = [
  { value: 1, label: '在售' },
  { value: 0, label: '停售' }
]

/* ---------- 新增 / 编辑弹窗 ---------- */

const dialogVisible = ref(false)
/** `'create'` 或 `'edit'`。标题、提交走向都由它决定——一个弹窗两用，不建两个。 */
const dialogMode = ref('create')
const formRef = ref()
const submitting = ref(false)

const form = reactive({
  id: null,
  name: '',
  monthly_fee: 0,
  data_quota: 0,
  voice_quota: 0,
  sms_quota: 0,
  description: '',
  status: 1
})

/**
 * 数字项的必填校验。
 *
 * ⚠️ 不能只写 `{ required: true }`：`el-input-number` 清空后给的是 `null`，
 *    而 Element 的 `required` 对 `null` 的判断在 `type: 'number'` 下并不总如预期；
 *    更要紧的是 **`Number('') === 0`**——「月费没填」会被当成「0 元套餐」放过去。
 *    所以显式判 `null` / `undefined` / `''` 三种空。
 */
function numberRule(label) {
  return {
    validator: (_rule, value, callback) => {
      if (value === null || value === undefined || value === '') {
        callback(new Error(`请填写${label}`))
        return
      }
      if (Number(value) < 0) {
        callback(new Error(`${label}不能为负数`))
        return
      }
      callback()
    },
    trigger: 'blur'
  }
}

const rules = {
  name: [{ required: true, message: '请填写套餐名称', trigger: 'blur' }],
  monthly_fee: [numberRule('月费')],
  data_quota: [numberRule('流量')],
  voice_quota: [numberRule('通话')],
  sms_quota: [numberRule('短信')]
}

/* ---------- 数据 ---------- */

async function loadPackages() {
  loading.value = true
  try {
    const data = await listAdminPackages({
      page: currentPage.value,
      size: PAGE_SIZE,
      status: filter.status,
      keyword: filter.keyword
    })
    rows.value = data.records
    total.value = data.total
    currentPage.value = data.current
  } catch (e) {
    ElMessage.error(e.message || '套餐列表加载失败')
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  currentPage.value = 1
  loadPackages()
}

function handleReset() {
  filter.status = ''
  filter.keyword = ''
  handleFilterChange()
}

/* ---------- 弹窗 ---------- */

function openCreateDialog() {
  dialogMode.value = 'create'
  Object.assign(form, {
    id: null,
    name: '',
    monthly_fee: 0,
    data_quota: 0,
    voice_quota: 0,
    sms_quota: 0,
    description: '',
    status: 1
  })
  dialogVisible.value = true
}

function openEditDialog(row) {
  dialogMode.value = 'edit'
  // ⚠️ `{ ...row }` 拷一份再填，**不能把 row 直接给 form**。
  //    直接给的话，用户在弹窗里改一个数字，列表那一行会同步跟着变——
  //    他没点「保存」，数据却"脏"了；点了取消也回不去。
  //    `id` 要带上（提交时用它定位），但 `create_time` 不带：它不随编辑改动。
  Object.assign(form, {
    id: row.id,
    name: row.name,
    monthly_fee: row.monthly_fee,
    data_quota: row.data_quota,
    voice_quota: row.voice_quota,
    sms_quota: row.sms_quota,
    description: row.description,
    status: row.status
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return // 校验没过。validate() 是 reject，不是抛异常。
  }

  const payload = {
    name: form.name,
    monthly_fee: form.monthly_fee,
    data_quota: form.data_quota,
    voice_quota: form.voice_quota,
    sms_quota: form.sms_quota,
    description: form.description,
    status: form.status
  }

  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createPackage(payload)
      ElMessage.success('套餐已新增')
      currentPage.value = 1
    } else {
      await updatePackage({ id: form.id, ...payload })
      ElMessage.success('套餐已保存')
    }
    dialogVisible.value = false
    await loadPackages()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

/* ---------- 行操作 ---------- */

/** 在售 / 停售。与用户管理的启停同一形态，但**不弹二次确认**——
 *  下架套餐是可逆的（再点一次就上架），也没有「用户无法登录」那种后果。 */
async function handleToggleStatus(row) {
  const enabling = row.status !== 1

  submittingId.value = row.id
  try {
    await updatePackage({
      id: row.id,
      name: row.name,
      monthly_fee: row.monthly_fee,
      data_quota: row.data_quota,
      voice_quota: row.voice_quota,
      sms_quota: row.sms_quota,
      description: row.description,
      status: enabling ? 1 : 0
    })
    ElMessage.success(enabling ? '套餐已上架' : '套餐已停售')
    await loadPackages()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    submittingId.value = null
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `删除后不可恢复，确认删除套餐「${row.name}」？若该套餐仍被使用，系统会拒绝删除。`,
      '删除套餐',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  submittingId.value = row.id
  try {
    await deletePackage(row.id)
    ElMessage.success('套餐已删除')
    await loadPackages()
  } catch (e) {
    // 「该套餐已被使用（关联 N 条记录）…」由 api 层 reject 带上来。
    ElMessage.error(e.message || '删除失败')
  } finally {
    submittingId.value = null
  }
}

onMounted(loadPackages)
</script>

<template>
  <div class="pkgs">
    <header class="pkgs__head">
      <h2 class="pkgs__title">套餐配置</h2>
      <p class="pkgs__sub">
        套餐的增删改查。停售只让套餐从营业厅消失，历史记录仍可解析；已被使用的套餐不能删除。
      </p>
    </header>

    <section class="pkgs__block">
      <h3 class="pkgs__block-title">
        套餐列表
        <span class="pkgs__block-right">
          <span class="pkgs__block-extra">共 {{ total }} 个</span>
          <el-button type="primary" @click="openCreateDialog">新增套餐</el-button>
        </span>
      </h3>

      <el-form :inline="true" class="pkgs__filter" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="filter.status" class="pkgs__filter-field" @change="handleFilterChange">
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
            class="pkgs__filter-field"
            placeholder="套餐名称"
            clearable
            @keyup.enter="handleFilterChange"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleFilterChange">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe empty-text="暂无套餐">
        <el-table-column prop="name" label="套餐名称" min-width="170" />

        <el-table-column label="月费" width="110" align="right">
          <template #default="{ row }">{{ yuan(row.monthly_fee) }} {{ CURRENCY }}</template>
        </el-table-column>

        <el-table-column :label="`流量（${PACKAGE_UNITS.data_quota}）`" width="110" align="right">
          <template #default="{ row }">{{ quantity(row.data_quota) }}</template>
        </el-table-column>

        <el-table-column :label="`通话（${PACKAGE_UNITS.voice_quota}）`" width="120" align="right">
          <template #default="{ row }">{{ quantity(row.voice_quota) }}</template>
        </el-table-column>

        <el-table-column :label="`短信（${PACKAGE_UNITS.sms_quota}）`" width="110" align="right">
          <template #default="{ row }">{{ quantity(row.sms_quota) }}</template>
        </el-table-column>

        <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />

        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '在售' : '停售' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="使用数" width="90" align="right">
          <template #default="{ row }">
            <!-- 这一列是给「为什么删不掉」做自解释的：点删除时会报
                 「已被使用（关联 N 条记录）」，N 就是这个数。
                 没有它，管理员会以为是系统不让删。 -->
            {{ row.user_count }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="submittingId !== null"
              @click="openEditDialog(row)"
            >
              编辑
            </el-button>
            <el-button
              link
              :type="row.status === 1 ? 'warning' : 'success'"
              :loading="submittingId === row.id"
              :disabled="submittingId !== null && submittingId !== row.id"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '下架' : '上架' }}
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
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        class="pkgs__pager"
        background
        layout="total, prev, pager, next"
        :page-size="PAGE_SIZE"
        :total="total"
        @current-change="loadPackages"
      />
    </section>

    <!-- 新增与编辑共用一个弹窗，靠 `dialogMode` 分流 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增套餐' : '编辑套餐'"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="套餐名称" prop="name">
          <el-input v-model="form.name" placeholder="如 畅享 5G 99 元套餐" maxlength="30" />
        </el-form-item>

        <el-form-item :label="`月费（${CURRENCY}）`" prop="monthly_fee">
          <el-input-number v-model="form.monthly_fee" :min="0" :precision="2" />
        </el-form-item>

        <!-- 单位取自 PACKAGE_UNITS，不在模板里写 'GB' / '分钟' / '条' 字面量——
             这三个单位由 claude.md 决策「套餐量纲单位定稿」定死，与 t_usage 共用。 -->
        <el-form-item :label="`流量（${PACKAGE_UNITS.data_quota}）`" prop="data_quota">
          <el-input-number v-model="form.data_quota" :min="0" :precision="0" />
        </el-form-item>

        <el-form-item :label="`通话（${PACKAGE_UNITS.voice_quota}）`" prop="voice_quota">
          <el-input-number v-model="form.voice_quota" :min="0" :precision="0" />
        </el-form-item>

        <el-form-item :label="`短信（${PACKAGE_UNITS.sms_quota}）`" prop="sms_quota">
          <el-input-number v-model="form.sms_quota" :min="0" :precision="0" />
        </el-form-item>

        <el-form-item label="说明" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="面向用户的一句话介绍"
          />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">在售</el-radio>
            <el-radio :value="0">停售</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ dialogMode === 'create' ? '确定新增' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pkgs__head {
  margin-bottom: 18px;
}

.pkgs__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.pkgs__sub {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.pkgs__block {
  margin-top: 28px;
  padding: 18px 18px 8px;
  border-radius: var(--tp-radius);
  background: #fff;
}

.pkgs__block:first-of-type {
  margin-top: 0;
}

.pkgs__block-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 16px;
  font-weight: 600;
}

.pkgs__block-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pkgs__block-extra {
  color: var(--tp-text-sub);
  font-size: 12px;
  font-weight: 400;
}

.pkgs__filter {
  padding-bottom: 2px;
  border-bottom: 1px solid #ebeef5;
}

.pkgs__filter-field {
  width: 160px;
}

.pkgs__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0;
}
</style>
