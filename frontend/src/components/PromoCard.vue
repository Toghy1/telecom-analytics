<script setup>
/**
 * 促销活动卡片。与 `PackageCard.vue` 同理，从 `Home.vue` 抽出，首页与浏览页共用。
 *
 * `packageName` 是**可选的**，这正是首页与浏览页的区别所在：
 *   首页     —— 不放「适用套餐」，卡片保持三行，一屏能扫完；
 *   浏览页   —— 放上「适用套餐」，因为活动与套餐的关联（`t_promo.package_id`）
 *               是这张表唯一还没露面的信息，浏览页不放它，就成了首页的复制品。
 */
defineProps({
  /** 活动对象，字段对齐 `t_promo` */
  promo: { type: Object, required: true },
  /** 关联套餐名称。由 api 层把 `package_id` 解析好后传入；不传则不显示这一行。 */
  packageName: { type: String, default: '' }
})
</script>

<template>
  <article class="promo-card">
    <h4 class="promo-card__title">{{ promo.title }}</h4>
    <p class="promo-card__content">{{ promo.content }}</p>

    <p v-if="packageName" class="promo-card__pkg">
      适用套餐：<b>{{ packageName }}</b>
    </p>

    <span class="promo-card__time">
      {{ promo.start_time.slice(0, 10) }} ~ {{ promo.end_time.slice(0, 10) }}
    </span>
  </article>
</template>

<style scoped>
.promo-card {
  display: flex;
  flex-direction: column;
  padding: 18px 20px;
  border: 1px solid #ebeef5;
  border-left: 3px solid #e6a23c;
  border-radius: var(--tp-radius);
  background: #fff;
}

.promo-card__title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
}

.promo-card__content {
  margin: 0 0 10px;
  color: #606266;
  font-size: 13px;
  line-height: 1.7;
}

.promo-card__pkg {
  margin: 0 0 10px;
  color: #606266;
  font-size: 12px;
}

.promo-card__pkg b {
  color: var(--tp-primary);
  font-weight: 600;
}

.promo-card__time {
  /* 与 PackageCard 同样的用意：把日期压到卡片底部，多张卡并排时日期对齐 */
  margin-top: auto;
  color: var(--tp-text-sub);
  font-size: 12px;
}
</style>
