<!--
  ⚠️ 这里原先挂过一个**临时**的「分析平台 · 实时大屏 →」入口，已于 2026-09-30 删除
     （claude.md 决策「分析平台角色守卫与登录分流」）。

     删它的理由：大屏归运营分析员，与营业厅是**两个互不相干的入口**
     （《开发文档》角色表把「营业厅」与「分析平台」列为两个面）。
     消费端的页脚挂一个内部分析平台的链接，从产品角度就是不对的。

     当初放它是因为没有角色登录、`/screen` 也没挂守卫，演示时只能手敲 URL 进大屏，
     而大屏是本项目优先级最高的交付物，够不着它比页脚多一行字严重得多。两害相权先放着。

     现在入口在**登录页按角色分流**：ANALYST 登录即落 `/screen`，
     见 `views/Login.vue` 的 `ROLE_HOME` 与 `resolveTarget()`。
-->
<template>
  <footer class="ftr">
    <span>电信用户行为分析系统 · P1 课程设计</span>
    <span class="ftr__mock-note">当前为「纯前端 + 假数据」阶段，页面数据非真实</span>
  </footer>
</template>

<style scoped>
.ftr {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 20px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
  color: var(--tp-text-sub);
  font-size: 12px;
  text-align: center;
}

.ftr__mock-note {
  color: #e6a23c;
}
</style>
