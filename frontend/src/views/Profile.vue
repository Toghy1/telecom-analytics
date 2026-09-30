<script setup>
/**
 * 个人信息（《开发文档》§6.4 #7）：资料查看。
 *
 * ⚠️ 这一页最容易犯的错是「顺手脱敏」。**前端一行脱敏代码都不能有。**
 *
 *    手机号与身份证由后端在 Entity→VO 转换时脱敏后才下发，这里拿到的
 *    `login_name` / `id_card` 本身就已经是 `138****1234` 这种形态，
 *    原样显示即可，不需要也不允许再加工。
 *
 *    前端脱敏是**假脱敏**：页面上数字是遮住了，但 F12 打开 Network 面板、
 *    或者 Postman 直接打接口，拿到的仍是全量数据——遮住的只是视觉，
 *    数据早就落到浏览器里了。真脱敏必须发生在数据出库之前。
 *
 *    可验证的判据：本页显示的字符串必须与 store 里的 `account.login_name`
 *    **逐字相同**。一旦有人加了前端脱敏（哪怕只是再截一次），显示值就会
 *    和 store 里的对不上——那正是这条注释想拦住的事。
 *
 * ⚠️ 与 order / ticket 两页相反：埋点 `view_profile` 是**页面浏览类**（选型⑦），
 *    写在路由 `meta.track` 里由守卫自动上报，本页一行上报代码都不用写。
 *    判据是「打开页面就会发生」对「用户要做个动作才会发生」——翻到这一页
 *    本身就是要记的行为，没有对应的动作可绑。
 *
 * 本页**只读**。功能清单第 7 条写的是「资料查看」，没有修改入口：
 * 手机号与身份证属敏感个人信息，本来也不允许用户自助改。
 */
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { getPackageById } from '@/api/package'
import { dateOnly } from '@/utils/format'

const auth = useAuthStore()

/** 从登录态取——与账户总览、套餐办理读的是同一份数据，三处不会打架。 */
const account = computed(() => auth.account || {})

const currentPackage = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    // 整页只多要一个套餐名，其余字段登录态里都有，不必再拉一次账号信息。
    currentPackage.value = await getPackageById(account.value.package_id)
  } catch (e) {
    ElMessage.error(e.message || '数据加载失败')
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="profile">
    <header class="profile__head">
      <h2 class="profile__title">个人信息</h2>
      <p class="profile__sub">手机号与身份证号已脱敏，本页仅作展示</p>
    </header>

    <el-skeleton v-if="loading" :rows="5" animated />

    <template v-else>
      <section class="profile__block">
        <el-descriptions :column="2" border>
          <!-- login_name / id_card 直接绑定，不做任何加工——脱敏是后端的事 -->
          <el-descriptions-item label="手机号码">
            {{ account.login_name || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="身份证号">
            {{ account.id_card || '--' }}
          </el-descriptions-item>

          <el-descriptions-item label="归属地">
            {{ account.region || '--' }}
          </el-descriptions-item>
          <el-descriptions-item label="账户状态">
            <el-tag
              size="small"
              effect="plain"
              :type="account.status === 1 ? 'success' : 'danger'"
            >
              {{ account.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </el-descriptions-item>

          <el-descriptions-item label="当前套餐">
            {{ currentPackage?.name || '未订购' }}
          </el-descriptions-item>
          <el-descriptions-item label="入网时间">
            {{ dateOnly(account.create_time) }}
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <p class="profile__footnote">
        手机号与身份证号属敏感个人信息。系统按「最小必要」原则处理：这两个字段
        在脱敏后才下发给页面，运营分析员在任何页面都看不到用户个人明细。
      </p>
    </template>
  </div>
</template>

<style scoped>
.profile__head {
  margin-bottom: 18px;
}

.profile__title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
}

.profile__sub {
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.profile__block {
  padding: 18px;
  border-radius: var(--tp-radius);
  background: #fff;
}

.profile__footnote {
  margin: 18px 0 0;
  color: var(--tp-text-sub);
  font-size: 12px;
  line-height: 1.8;
}
</style>
