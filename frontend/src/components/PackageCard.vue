<script setup>
/**
 * 套餐卡片。
 *
 * 从 `Home.vue` 抽出来的——首页（节选）与套餐资费浏览页（全量）用的是同一张卡。
 * 抽之前这份模板在首页里内联，再复制一份到浏览页就是「同一份模板维护两处」：
 * 日后改一处列宽、加一个字段，另一处必然漏掉，而漏掉的那一处平时没人看，
 * 等发现时已经和另一边长得不一样了。
 *
 * 单位一律走 `PACKAGE_UNITS` 常量，不在模板里写字面量——三个量纲的单位
 * 属全链路统一项（claude.md 决策「套餐量纲单位定稿」），写死会让日后调整散落各处。
 *
 * 卡片开了两个**可选插槽**，办理页用它们挂「当前套餐」角标与「办理」按钮：
 *   `badge`  —— 标题行右侧；`action` —— 卡片底部。
 *   两处默认都不渲染任何东西，首页与套餐资费浏览页不传，外观与开槽之前完全一致。
 *   之所以用插槽而不是再写一张「带按钮的套餐卡」：那就成了同一份模板维护两处，
 *   日后改一处列宽、加一个字段，另一处必然漏掉（见本文件开头那段话）。
 */
import { CURRENCY, PACKAGE_UNITS } from '@/utils/constants'
import { yuan } from '@/utils/format'

defineProps({
  /** 套餐对象，字段对齐 `t_package`。⚠️ 不能叫 `package`（保留字），故用 pkg。 */
  pkg: { type: Object, required: true }
})
</script>

<template>
  <article class="package-card">
    <div class="package-card__head">
      <h4 class="package-card__name">{{ pkg.name }}</h4>
      <slot name="badge" />
    </div>

    <p class="package-card__price">
      <span class="package-card__amount">{{ yuan(pkg.monthly_fee) }}</span>
      <span class="package-card__unit">{{ CURRENCY }} / 月</span>
    </p>

    <ul class="package-card__quota">
      <li>流量 {{ pkg.data_quota }} {{ PACKAGE_UNITS.data_quota }}</li>
      <li>通话 {{ pkg.voice_quota }} {{ PACKAGE_UNITS.voice_quota }}</li>
      <li>短信 {{ pkg.sms_quota }} {{ PACKAGE_UNITS.sms_quota }}</li>
    </ul>

    <p class="package-card__desc">{{ pkg.description }}</p>

    <slot name="action" />
  </article>
</template>

<style scoped>
.package-card {
  display: flex;
  flex-direction: column;
  padding: 20px;
  border: 1px solid #ebeef5;
  border-radius: var(--tp-radius);
  background: #fff;
  transition: box-shadow 0.2s, transform 0.2s;
}

.package-card:hover {
  box-shadow: 0 6px 18px rgba(31, 111, 235, 0.12);
  transform: translateY(-2px);
}

/* 标题行。角标槽为空时这一行只有一个子元素，间距与加槽之前一模一样。 */
.package-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.package-card__name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.package-card__price {
  margin: 0 0 14px;
  color: var(--tp-primary);
}

.package-card__amount {
  font-size: 28px;
  font-weight: 700;
}

.package-card__unit {
  margin-left: 4px;
  color: var(--tp-text-sub);
  font-size: 13px;
}

.package-card__quota {
  margin: 0 0 14px;
  padding: 12px 0;
  border-top: 1px dashed #ebeef5;
  border-bottom: 1px dashed #ebeef5;
  list-style: none;
}

.package-card__quota li {
  color: #606266;
  font-size: 13px;
  line-height: 2;
}

.package-card__desc {
  /* flex:1 让同一行里的卡片说明区等高，卡片底部对齐 */
  flex: 1;
  margin: 0;
  color: var(--tp-text-sub);
  font-size: 12px;
  line-height: 1.7;
}
</style>
