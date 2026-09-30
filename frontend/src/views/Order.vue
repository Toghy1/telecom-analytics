<script setup>
/**
 * 套餐办理（《开发文档》§6.4 #5）：套餐列表 + 办理 / 变更 + 办理记录。
 *
 * 三块内容对应功能清单里那三个词，一个不缺。
 *
 * 顶上那张「当前套餐」卡不是装饰——没有它，套餐列表里与当前套餐同 id 的那张卡
 * 就没有参照系，用户不知道自己现在是哪一档，「办理」与「变更」在界面上分不出来。
 *
 * ⚠️ 埋点 `order_package` 是**交互动作类**（选型⑦），在提交成功那一刻手动上报。
 *    绝不能写进路由 meta.track——那是页面浏览类的位置，写进去会变成一进页面
 *    就记一次，把「浏览办理页」记成「办理了套餐」，直接污染行为数据。
 */
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getPackageById, listPackages } from '@/api/package'
import { listOrders, submitOrder } from '@/api/order'
import { getRemaining } from '@/api/usage'
import { reportAction } from '@/utils/tracker'
import { CURRENCY, PACKAGE_UNITS } from '@/utils/constants'
import { quantity, yuan } from '@/utils/format'
import PackageCard from '@/components/PackageCard.vue'

const auth = useAuthStore()

const packages = ref([])
const currentPackage = ref(null)
/** 本月已用。只为降档二次确认里的那句实话，不参与任何计算——减法在 api 层。 */
const usage = ref(null)

const rows = ref([])
const total = ref(0)
const currentPage = ref(1)

const loading = ref(true)
const listLoading = ref(true)
/** 正在办理的那个套餐 id。用它精确 loading，避免点一个按钮全套卡片一起转。 */
const submittingId = ref(null)

/** 每页 5 条。假数据里当前用户有 6 条记录，正好第二页有内容，分页真的翻得动。 */
const PAGE_SIZE = 5

/**
 * 当前套餐 id。
 *
 * 取自登录态而不是 `mockAccount`——办理成功后由页面调 `auth.syncAccount()`
 * 把它一并换掉，于是这张页面、账户总览、余量查询三处读的是同一个值。
 */
const currentId = computed(() => auth.account?.package_id ?? null)

/** 升档 / 降档只看月费。判方向是为了那句二次确认，不是业务规则。 */
function isDowngrade(pkg) {
  return Boolean(currentPackage.value) && pkg.monthly_fee < currentPackage.value.monthly_fee
}

/**
 * `getRemaining()` 的 items 用量纲 key（data / voice / sms）标识维度，
 * 而套餐额度在 `t_package` 上是 `*_quota` 字段，两边名字不同，这里对一次。
 */
const QUOTA_FIELD = { data: 'data_quota', voice: 'voice_quota', sms: 'sms_quota' }

/**
 * 降档后**会超出**新套餐额度的量纲。
 *
 * 这几行是把「点完页面会变红」事先说明白——不写的话，用户看完「办理成功」，
 * 转头打开余量页看到一片红，只会以为页面坏了。
 * 返回空数组是正常的：降档不等于一定超套，小额降档可能仍在额度内。
 */
function overQuotaLines(pkg) {
  return (usage.value?.items || [])
    .filter((item) => item.used > pkg[QUOTA_FIELD[item.key]])
    .map(
      (item) =>
        `${item.label}：本月已用 ${quantity(item.used)} ${item.unit}，` +
        `新套餐仅 ${quantity(pkg[QUOTA_FIELD[item.key]])} ${item.unit}`
    )
}

async function loadOrders() {
  listLoading.value = true
  try {
    const data = await listOrders({ page: currentPage.value, size: PAGE_SIZE })
    rows.value = data.records
    total.value = data.total
    // 回写 current：后端会把越界的页码收敛到有效范围，以它返回的为准。
    currentPage.value = data.current
  } catch (e) {
    ElMessage.error(e.message || '办理记录加载失败')
  } finally {
    listLoading.value = false
  }
}

async function loadAll() {
  loading.value = true
  try {
    // 三个请求互不依赖，并行发起。
    const [pkgs, pkg, remain] = await Promise.all([
      listPackages(),
      getPackageById(auth.account?.package_id),
      getRemaining()
    ])
    packages.value = pkgs
    currentPackage.value = pkg
    usage.value = remain
  } catch (e) {
    ElMessage.error(e.message || '数据加载失败')
  } finally {
    loading.value = false
  }
}

async function handleOrder(pkg) {
  if (pkg.id === currentId.value) return

  const downgrade = isDowngrade(pkg)
  const lines = downgrade ? overQuotaLines(pkg) : []

  const html = downgrade
    ? `<p>新套餐额度低于本月已用量，办理后余量将显示为超出：</p>` +
      (lines.length ? `<ul>${lines.map((l) => `<li>${l}</li>`).join('')}</ul>` : '') +
      `<p>超出部分按超套计费（次月出账）；本月不另收月费。</p>`
    : `<p>变更即时生效，本月不另收取月费。</p>`

  try {
    await ElMessageBox.confirm(html, `变更为「${pkg.name}」`, {
      // ⚠️ 只有这里的字符串是我们自己拼的，不含任何用户输入，用 HTML 渲染是安全的。
      //    若日后把套餐名之类的**外部数据**拼进来，这里必须改成纯文本。
      dangerouslyUseHTMLString: true,
      confirmButtonText: '确认办理',
      cancelButtonText: '取消',
      type: downgrade ? 'warning' : 'info'
    })
  } catch {
    return // 用户点了取消。ElMessageBox 取消时是 reject，不是异常。
  }

  submittingId.value = pkg.id
  try {
    await submitOrder({ package_id: pkg.id })

    // 埋点在提交**成功之后**上报。放在前面会记下根本没发生的办理。
    reportAction('order_package', { package_id: pkg.id })

    // 把登录态里的 package_id 一并换掉，账户总览的「当前套餐」卡才会跟着变。
    // 真后端形态是办理后重新拉一次账号信息，这里用打补丁顶替那次往返。
    auth.syncAccount({ package_id: pkg.id })

    ElMessage.success(`已变更为「${pkg.name}」，即时生效`)

    // 回到第 1 页——新办的这条时间最新，按倒序必然排在第一页第一条。
    currentPage.value = 1
    await Promise.all([loadAll(), loadOrders()])
  } catch (e) {
    ElMessage.error(e.message || '办理失败')
  } finally {
    submittingId.value = null
  }
}

onMounted(() => {
  loadAll()
  loadOrders()
})
</script>

<template>
  <div class="order">
    <header class="order__head">
      <h2 class="order__title">套餐办理</h2>
      <p class="order__sub">变更即时生效、本月不另收月费；一个账号同时只持有一个套餐</p>
    </header>

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else>
      <!-- 当前套餐 -->
      <el-card shadow="never" class="order__current">
        <p class="order__current-label">当前套餐</p>
        <p class="order__current-name">{{ currentPackage?.name || '未订购' }}</p>
        <p v-if="currentPackage" class="order__current-note">
          月费 {{ yuan(currentPackage.monthly_fee) }} {{ CURRENCY }} ·
          流量 {{ currentPackage.data_quota }} {{ PACKAGE_UNITS.data_quota }} ·
          通话 {{ currentPackage.voice_quota }} {{ PACKAGE_UNITS.voice_quota }} ·
          短信 {{ currentPackage.sms_quota }} {{ PACKAGE_UNITS.sms_quota }}
        </p>
      </el-card>

      <!-- 套餐列表。`listPackages()` 只给在售的（`status = 1`），
           已下架的学生特惠 29 元套餐不会出现在这里——想办也办不了。 -->
      <section class="order__block">
        <h3 class="order__block-title">
          在售套餐
          <span class="order__block-extra">共 {{ packages.length }} 款</span>
        </h3>

        <div class="card-grid">
          <PackageCard v-for="item in packages" :key="item.id" :pkg="item">
            <template v-if="item.id === currentId" #badge>
              <el-tag size="small" effect="dark" type="success">当前</el-tag>
            </template>

            <template #action>
              <el-button
                v-if="item.id === currentId"
                class="order__act"
                disabled
                plain
              >
                当前套餐
              </el-button>
              <el-button
                v-else
                class="order__act"
                :type="isDowngrade(item) ? 'warning' : 'primary'"
                plain
                :loading="submittingId === item.id"
                :disabled="submittingId !== null && submittingId !== item.id"
                @click="handleOrder(item)"
              >
                {{ isDowngrade(item) ? '降档办理' : '办理' }}
              </el-button>
            </template>
          </PackageCard>
        </div>
      </section>

      <!-- 办理记录 -->
      <section class="order__block">
        <h3 class="order__block-title">
          办理记录
          <span class="order__block-extra">共 {{ total }} 条</span>
        </h3>

        <el-table v-loading="listLoading" :data="rows" stripe empty-text="暂无办理记录">
          <el-table-column label="办理时间" width="180">
            <template #default="{ row }">{{ row.order_time }}</template>
          </el-table-column>
          <el-table-column prop="package_name" label="办理套餐" min-width="200" />
          <el-table-column label="月费" width="120" align="right">
            <template #default="{ row }">{{ yuan(row.monthly_fee) }} {{ CURRENCY }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <!-- status 恒为 1（已生效）——办理即时生效，没有待生效这个中间态。 -->
              <el-tag size="small" effect="plain" :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '已生效' : '未知' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="currentPage"
          class="order__pager"
          background
          layout="total, prev, pager, next"
          :page-size="PAGE_SIZE"
          :total="total"
          @current-change="loadOrders"
        />
      </section>
    </template>
  </div>
</template>

<style scoped>
.order__head {
  margin-bottom: 18px;
}

.order__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.order__sub {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.order__current {
  border: none;
  border-radius: var(--tp-radius);
}

.order__current-label {
  margin: 0 0 8px;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.order__current-name {
  margin: 0 0 8px;
  color: var(--tp-primary);
  font-size: 22px;
  font-weight: 700;
}

.order__current-note {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 12px;
}

.order__block {
  margin-top: 28px;
  padding: 18px 18px 8px;
  border-radius: var(--tp-radius);
  background: #fff;
}

.order__block-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 0 0 14px;
  padding-left: 10px;
  border-left: 3px solid var(--tp-primary);
  font-size: 16px;
  font-weight: 600;
}

.order__block-extra {
  color: var(--tp-text-sub);
  font-size: 12px;
  font-weight: 400;
}

/* 卡片由 .card-grid 排布，这里只把栅格和卡片的背景分开：
   卡片自己有白底与描边，栅格容器放在白色区块里也还是白底，靠间距区分即可。 */
.order__act {
  width: 100%;
  margin-top: 14px;
}

.order__pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 0;
}
</style>
