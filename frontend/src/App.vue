<script setup>
/**
 * Element Plus 的语言包。
 *
 * ⚠️ 不配这个，**组件自带的文字全是英文**——Element Plus 的默认 locale 是 en，
 *    而「按需引入」又不会连带引入语言包。表现是很隐蔽的：页面主体都是我们自己写的
 *    中文，只有组件内建的那几个字露出来，比如分页控件显示成 `Total 7`、
 *    表格无数据时显示 `No Data`、日期选择器弹出 `January`。
 *    自己写的文案越多，这几处英文越像「忘了翻译」，而不是「没配 locale」。
 *
 * 用 `el-config-provider` 而不是 `app.use(ElementPlus, { locale })`：
 * 后者会把整个 Element Plus 全量引入，与选型⑤「按需引入」的硬性要求相冲。
 * `ElConfigProvider` 会被 unplugin-vue-components 自动按需引入，不增加体积。
 */
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
