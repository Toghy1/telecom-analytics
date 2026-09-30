<script setup>
/**
 * 大屏数字卡（板块①）。
 *
 * ⚠️ `value` 为 `null` / `undefined` 时显示 `--`，**不显示 0**。
 *    初值来自 `getRealtimeSnapshot()`，在它返回之前卡片是空的；
 *    若用 `|| 0` 兜底，大屏打开的头一瞬间会显示「当前在线 0 人」——
 *    一个煞有介事、且很吓人的读数。见 `utils/format.js#thousands`。
 *
 * ⚠️ 卡片**不做数字滚动动画**，直接换值。滚动动画在每 2 秒一次的实时更新下
 *    会一直在滚，永远停不下来——大屏上一排数字同时在动，看着像故障。
 *    滚动动画适合「加载一次、之后不动」的场景，不适合实时刷新。
 */
import { thousands } from '@/utils/format'

defineProps({
  label: { type: String, required: true },
  // ⚠️ `type: Number` 而不是 `[Number, null]`——`null` 不是合法的类型构造器，
  //    写进数组会让 Vue 的类型校验拿它当一种「类型」去比对，开发期告警
  //    「Invalid prop: type check failed」。而 `null` 值本身是允许的：
  //    Vue 对可空 prop 会跳过类型校验，所以不需要把 null 写进 type。
  value: { type: Number, default: null },
  unit: { type: String, default: '' },
  /** 强调色，用于区分「在线人数」这类瞬时量与其他累计量 */
  accent: { type: String, default: '#3d8bff' }
})
</script>

<template>
  <div class="stat">
    <div class="stat__label">
      <span class="stat__dot" :style="{ background: accent }"></span>
      {{ label }}
    </div>
    <div class="stat__value">
      <span class="stat__num" :style="{ color: accent }">{{ thousands(value) }}</span>
      <span v-if="unit" class="stat__unit">{{ unit }}</span>
    </div>
  </div>
</template>

<style scoped>
.stat {
  display: flex;
  flex-direction: column;
  justify-content: center;
  height: 100%;
  padding: 8px 16px;
  border: 1px solid rgba(90, 150, 230, 0.2);
  border-radius: 6px;
  background: linear-gradient(
    180deg,
    rgba(22, 50, 96, 0.6) 0%,
    rgba(11, 27, 54, 0.8) 100%
  );
}

.stat__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8fa6cc;
  letter-spacing: 0.5px;
}

.stat__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  box-shadow: 0 0 6px currentColor;
}

.stat__value {
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-top: 4px;
}

.stat__num {
  font-family: 'DIN Alternate', 'Bahnschrift', 'Arial Narrow', sans-serif;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: 1px;
}

.stat__unit {
  font-size: 12px;
  color: #8fa6cc;
}
</style>
