<script setup>
/**
 * 分析平台布局：暗色全屏 + 固定逻辑尺寸整体缩放。
 *
 * ⚠️ **大屏适配方案在此定稿：CSS `transform: scale()` 整体缩放。**
 *
 *    claude.md 决策「业务功能实现技术选型⑤」把这一条留作「编码时最终确定」，
 *    现在确定，理由如下：
 *
 *    **做法**：内容按固定的 **1920×1080 逻辑像素**排版，外层按
 *    `min(视口宽/1920, 视口高/1080)` 算出一个倍率，整体缩放。
 *
 *    **为什么不是 rem / 响应式栅格**：大屏是**展示场景**，不是多端适配场景。
 *    它永远整屏显示，没有滚动、没有折叠、用户不缩放。用 rem 的话，
 *    每个尺寸（字体、间距、图表容器、边框）都要按基准换算，而且
 *    canvas 图表的尺寸得靠 JS 反算再 `resize()`；混着 px 写就会错位。
 *    `scale` 是**整体几何变换**，一处设定全局生效——布局内部完全按 1920×1080
 *    写死，永远不需要考虑「窄屏怎么办」，因为不存在窄屏。
 *
 *    **代价（已接受的取舍）**：canvas 图表在非 1 倍缩放时会被重采样，
 *    略有一点柔。1080p 显示器上倍率恰好为 1，完全一致；
 *    投影仪分辨率不同时略有柔化，不影响读数。
 *
 * ⚠️ 本布局**一个 Element Plus 组件都不用**（选型⑤：「大屏不引组件库组件，
 *    避开其全局样式对自绘布局的干扰」）。整块分析平台是自绘 CSS，
 *    所以它不会因为 EP 的全局 reset 而错位。
 */

import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

/**
 * 分析平台的三个标签页。
 *
 * ⚠️ **放在布局里而不是各页自己写**：三个页面共用同一条导航，写三份就会漂移
 *    （加一页时漏改一处，表现为某个页面上少一个 tab，且没人会发现）。
 *
 * ⚠️ 名称与归属由 2026-10-21 的对话定稿：热力图留在实时大屏，link 标签不动。
 *    顺序也是定死的——按「实时 → 离线 → 系统」由近及远，与数据的新鲜度一致。
 */
const TABS = [
  { name: 'screenRealtime', label: '实时大屏' },
  { name: 'screenOffline', label: '离线数据分析' },
  { name: 'screenPipeline', label: '系统运行分析' }
]

/** 逻辑尺寸。所有内层布局都按这两个数写死，不再关心真实视口。 */
const LOGIC_WIDTH = 1920
const LOGIC_HEIGHT = 1080

const scale = ref(1)

function fit() {
  // ⚠️ 用 `documentElement.clientWidth` 而不是 `window.innerWidth`：
  //    后者**包含竖直滚动条的宽度**，在恰好出现滚动条时会多出约 15px，
  //    算出来的倍率略大，内容被切掉一点点边缘。本布局虽已 `overflow: hidden`，
  //    但页面一旦有别的元素撑出滚动条（比如调试时插了个长字符串），
  //    innerWidth 就会偏，而用 clientWidth 永远是对的。
  const vw = document.documentElement.clientWidth
  const vh = document.documentElement.clientHeight
  scale.value = Math.min(vw / LOGIC_WIDTH, vh / LOGIC_HEIGHT)
}

onMounted(() => {
  fit()
  window.addEventListener('resize', fit)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', fit)
})

defineProps({
  /** 页面标题，显示在左上角。 */
  title: { type: String, required: true },
  /** 副标题，一句话说明这一页回答什么问题。 */
  subtitle: { type: String, default: '' }
})

/**
 * 大屏内的退出出口。
 *
 * ⚠️ **这是加上 `requiresAuth` 的**前置条件**，不是锦上添花。**
 *    大屏布局与营业厅布局是两套：带「退出登录」的 `AppHeader` 只挂在营业厅，
 *    大屏头部原先只有页面自带的跳转链接（`#actions` 插槽，里面是
 *    「系统运行分析 →」/「← 返回实时大屏」）。角色守卫一生效，`ANALYST`
 *    登录即落在大屏，就成了**进得去出不来**——只能清 localStorage 才能换账号。
 *
 * ⚠️ **这里不报 `logout` 埋点。** 能走到这个按钮的只可能是 ANALYST / ADMIN，
 *    角色表写明两者不产生行为数据。真漏写了也不会出错（`tracker` 的 `send()`
 *    会拦掉），在这里**不写**是为了不让读代码的人以为大屏有埋点。
 *    同理不用 `ElMessage`——大屏不引 Element Plus 组件（选型⑤），
 *    而「跳回登录页」本身就是最清楚的反馈。
 */
async function handleLogout() {
  await auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="screen">
    <!-- 背景装饰：径向光晕 + 网格，纯 CSS，不含交互 -->
    <div class="screen__glow"></div>
    <div class="screen__grid"></div>

    <div
      class="screen__stage"
      :style="{ transform: `scale(${scale})`, width: `${1920}px`, height: `${1080}px` }"
    >
      <header class="screen__head">
        <div class="screen__brand">
          <span class="screen__mark"></span>
          <div>
            <h1 class="screen__title">{{ title }}</h1>
            <p v-if="subtitle" class="screen__sub">{{ subtitle }}</p>
          </div>
        </div>
        <!-- 三个标签页的导航。自绘（选型⑤：分析平台不引 Element Plus） -->
        <nav class="screen__tabs">
          <router-link
            v-for="tab in TABS"
            :key="tab.name"
            :to="{ name: tab.name }"
            class="screen__tab"
            :class="{ 'is-active': route.name === tab.name }"
          >
            {{ tab.label }}
          </router-link>
        </nav>

        <div class="screen__actions">
          <slot name="actions" />
          <button class="screen__quit" type="button" @click="handleLogout">退出</button>
        </div>
      </header>

      <main class="screen__body">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.screen {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: radial-gradient(circle at 50% 0%, #0d2247 0%, #061226 55%, #03090f 100%);
  /* 用 :root 的营业厅字体栈做兜底，但大屏整体是另一套视觉域 */
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  color: #c8d6f0;
}

.screen__glow {
  position: absolute;
  top: -18%;
  left: 50%;
  width: 70%;
  height: 48%;
  transform: translateX(-50%);
  background: radial-gradient(ellipse at center, rgba(61, 139, 255, 0.16) 0%, transparent 70%);
  pointer-events: none;
}

.screen__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(90, 150, 230, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(90, 150, 230, 0.05) 1px, transparent 1px);
  background-size: 40px 40px;
  pointer-events: none;
}

/*
 * 舞台固定 1920×1080，靠 flex 居中。
 * 视口比它小时，flex 会把超出的部分**两侧均分地溢出去**，
 * 而 `transform-origin` 默认为元素中心——于是缩放后仍然精确居中。
 * 外层 `overflow: hidden` 收掉溢出部分。
 */
.screen__stage {
  position: relative;
  flex: none;
  transform-origin: center center;
  display: flex;
  flex-direction: column;
  padding: 14px 20px 18px;
}

.screen__head {
  display: flex;
  align-items: center;
  flex: none;
  /* 66px 是跟着字号一起定的：标题 28 + 副标题 14 + 间距，62 会紧到贴边。
     舞台总高固定 1080，头部多占的 4px 从下面的图表行里出，不影响布局 */
  height: 66px;
  padding: 0 4px 0 0;
}

.screen__brand {
  /* 左右两翼各占一份、标签条不伸缩——三个 tab 才会**严格居中**。
     若只给 actions 加 `margin-left: auto`，auto 外边距会与标签条的
     `margin: auto` 三分剩余空间，标签条就偏向左边，三页切换时位置还会漂。 */
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.screen__mark {
  width: 5px;
  height: 34px;
  border-radius: 3px;
  background: linear-gradient(180deg, #5aa4ff 0%, #1f6feb 100%);
  box-shadow: 0 0 12px rgba(90, 164, 255, 0.6);
}

.screen__title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #eaf2ff;
  text-shadow: 0 0 18px rgba(90, 164, 255, 0.45);
}

.screen__sub {
  margin: 3px 0 0;
  font-size: 14px;
  letter-spacing: 1px;
  color: #7b8db5;
}

/*
 * 标签页导航。
 *
 * ⚠️ 用 `margin: 0 auto` 居中、而不是紧贴标题：标题有一定长度，
 *    紧贴会让三个 tab 的位置随标题字数浮动，三页之间切换时眼睛要重新找。
 *
 * ⚠️ 激活态用**底色填充 + 亮字**，不用下划线：大屏是深色背景 + 光晕，
 *    下划线在投屏时被光晕吃掉一半，反而看不出选中了哪个。
 */
.screen__tabs {
  flex: none;
  display: flex;
  gap: 6px;
  padding: 3px;
  border: 1px solid rgba(90, 150, 230, 0.18);
  border-radius: 18px;
  background: rgba(10, 26, 52, 0.55);
}

.screen__tab {
  padding: 6px 20px;
  border-radius: 15px;
  color: #7b8db5;
  font-size: 15px;
  letter-spacing: 1px;
  text-decoration: none;
  transition: all 0.2s;
  white-space: nowrap;
}

.screen__tab:hover {
  color: #c8d6f0;
}

.screen__tab.is-active {
  background: linear-gradient(180deg, rgba(61, 139, 255, 0.55) 0%, rgba(31, 111, 235, 0.45) 100%);
  box-shadow: 0 0 12px rgba(90, 164, 255, 0.35) inset;
  color: #eaf2ff;
  font-weight: 600;
}

.screen__actions {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

/*
 * 大屏内的退出出口。
 *
 * ⚠️ **故意做得很淡**：大屏是要投屏演示的，一个显眼的「退出登录」摆在右上角
 *    既不专业、也容易被误点。默认几乎融进背景，鼠标扫过才浮出来。
 *    `:focus-visible` 一并给上，键盘走到时同样可见——否则键盘用户看不到焦点在哪。
 */
.screen__quit {
  padding: 4px 10px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: rgba(200, 214, 240, 0.22);
  font-family: inherit;
  font-size: 13px;
  letter-spacing: 1px;
  cursor: pointer;
  transition: all 0.2s;
}

.screen__quit:hover,
.screen__quit:focus-visible {
  background: rgba(61, 139, 255, 0.16);
  color: #9ecbff;
}

.screen__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
