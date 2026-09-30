<script setup>
/**
 * 大屏板块外框：标题 + 链路标签 +（可选）右上角操作区 + 内容槽。
 *
 * ⚠️ **`link` 这个链路标签不是装饰，是「指标分工原则」在界面上的落点。**
 *
 *    claude.md 决策「指标分工原则」要求：大屏上每一项指标只能由实时链路
 *    或离线链路**其中之一**产出，严禁两条链路各算一遍，且
 *    「大屏上每一块图表都必须能回答『这块数据来自哪条链路』」。
 *
 *    所以这个标签做成外框的**固定部件**而不是可选项——想让某个板块不显示
 *    链路，得显式传 `link: null`，是个要动手的动作，不是忘了写就没有。
 *    答辩时指着任一块说「这是实时的」或「这是离线的」，标签就在那儿。
 */
defineProps({
  title: { type: String, required: true },
  /**
   * 数据链路。`'REALTIME'`（实时链路 / Kafka）或 `'OFFLINE'`（离线链路 / Spark）。
   * 传 `null` 表示这一块是两类混合的，不标链路。
   */
  link: { type: String, default: null }
})

const LINK_META = {
  REALTIME: { label: '实时链路', cls: 'is-realtime' },
  OFFLINE: { label: '离线链路', cls: 'is-offline' }
}
</script>

<template>
  <section class="panel">
    <header class="panel__head">
      <span class="panel__bar"></span>
      <h3 class="panel__title">{{ title }}</h3>
      <span
        v-if="link && LINK_META[link]"
        class="panel__link"
        :class="LINK_META[link].cls"
      >
        {{ LINK_META[link].label }}
      </span>
      <div class="panel__extra">
        <slot name="extra" />
      </div>
    </header>

    <div class="panel__body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border: 1px solid rgba(90, 150, 230, 0.18);
  border-radius: 6px;
  background: linear-gradient(
    180deg,
    rgba(20, 46, 88, 0.55) 0%,
    rgba(11, 27, 54, 0.75) 100%
  );
}

.panel__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
  padding: 8px 12px 7px;
  border-bottom: 1px solid rgba(90, 150, 230, 0.14);
}

/* 标题左侧的一小段亮条，用来在深色背景里把标题「钉」住 */
.panel__bar {
  width: 3px;
  height: 13px;
  flex: none;
  border-radius: 2px;
  background: #3d8bff;
}

.panel__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #dbe7fb;
}

.panel__link {
  flex: none;
  padding: 1px 7px;
  border-radius: 9px;
  font-size: 10px;
  line-height: 16px;
  letter-spacing: 0.5px;
}

.panel__link.is-realtime {
  color: #4fd1e0;
  border: 1px solid rgba(79, 209, 224, 0.4);
  background: rgba(79, 209, 224, 0.1);
}

.panel__link.is-offline {
  color: #f5a524;
  border: 1px solid rgba(245, 165, 36, 0.4);
  background: rgba(245, 165, 36, 0.1);
}

.panel__extra {
  margin-left: auto;
}

.panel__body {
  flex: 1;
  min-height: 0;
  padding: 6px 8px 8px;
}
</style>
