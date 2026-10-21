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

    // 三个后端应用各占一个端口，按前缀分流（决策「工程划分为双应用」）：
    //
    //   /api/track     → 8082 business-service   埋点上报（转投 Kafka）
    //   /api/realtime  → 8081 realtime-service   实时指标 + SSE
    //   /api/screen    → 8080 analysis-service   离线指标（只读，全部接口都在这个前缀下）
    //   /api           → 8082 business-service   营业厅业务接口（登录 / 套餐 / 账单 …）
    //
    // ⚠️ **顺序不能动**：Vite 的 proxy 是按 key 的定义顺序匹配的，
    //    `/api` 放在最前面会把后三条全吃掉——表现是埋点报了 404、
    //    大屏 SSE 连不上、业务接口被转去分析服务，而三个服务本身都好好的。
    //
    // ⚠️ 之所以能让 `/api` 整体指向业务应用：分析服务的所有接口本就在
    //    `/api/screen` 下，此前 `/api → 8080` 是「它当时是唯一的后端」的写法。
    //    现在业务应用接管 `/api` 的其余部分，**前端请求路径一行都不用改**。
    //    生产环境由 Nginx 按同样四条 location 做同源反代。
    proxy: {
      '/api/track': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },
      '/api/realtime': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        // SSE 是长连接，代理不能超时断开（默认无超时，这里写明意图）。
        ws: false
      },
      '/api/screen': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      },
      '/api': {
        target: 'http://127.0.0.1:8082',
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
