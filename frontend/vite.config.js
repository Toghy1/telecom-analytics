import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// Element Plus 按需引入（选型⑤硬性要求：不做全量引入）。
// 关闭 dts 生成——本项目是 JavaScript，不需要 .d.ts。
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // ⚠️ `0.0.0.0` 是给**同一个局域网里的手机 / 另一台电脑**看的，不是给公网看的。
    //    同网段访问用 `http://<本机内网IP>:5173`。
    host: '0.0.0.0',
    port: 5173,
    open: false,

    // 本地最小闭环：前端的 /api 请求代理到本地 Spring Boot 服务（8080）。
    // 页面与 api 层不用改端口，生产环境由 Nginx 做同源反代。
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }

    // 这里原先有一行 `allowedHosts: ['.serveousercontent.com', '.lhr.life', '.trycloudflare.com']`，
    // 那是「公网内网穿透」演示方案的配套——不加的话，Vite 5.4.12 起的 DNS 重绑定防护
    // 会把穿透域名一律 403 掉。
    //
    // 2026-09-30 演示共享**改为分发代码**（Gitee 仓库，组员各自 `git clone` +
    // `npm i && npm run dev`），穿透方案作废，这三条后缀一并删除：不穿透就不会有
    // 请求带这些 Host，留着既没用，又是一处无谓的 Host 放行。
    // 日后真要临时给外人看一次，再加回对应的一行即可，别直接写 `true`（那是放行一切）。
  }
})
