import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// Element Plus 按需引入（选型⑤硬性要求：不做全量引入）。
// 关闭 dts 生成——本项目是 JavaScript，不需要 .d.ts。

/**
 * 离线演示模式（`--mode offline`）下整模块替换的 api 文件。
 *
 * 页面一律写 `import … from '@/api/package'` 这种不带扩展名的路径，
 * 所以这里可以按模块精确映射到 `src/api/offline/<name>.js`；
 * 离线实现内部对 `@/api/mock/*` 的引用不受影响（它不匹配下面任何一条）。
 *
 * 只列这 12 个：它们就是页面与 `utils/tracker.js` 全部的数据入口。
 * 新增 api 文件时若也想离线可用，必须同时加进来，否则会落到真实现上。
 */
const OFFLINE_API_MODULES = [
  'admin',
  'auth',
  'bill',
  'order',
  'package',
  'pipeline',
  'promo',
  'realtime',
  'screen',
  'ticket',
  'track',
  'usage'
]

export default defineConfig(({ mode }) => {
  // 两种模式：
  //   offline           —— 纯前端假数据，不连任何后端（交给老师的演示包用它构建）
  //   其余（development / vm）—— 真接口，代理目标由 VITE_BACKEND_HOST 决定
  const offline = mode === 'offline'
  const env = loadEnv(mode, process.cwd(), '')
  const backendHost = env.VITE_BACKEND_HOST || '127.0.0.1'

  const offlineAliases = OFFLINE_API_MODULES.map((name) => ({
    find: `@/api/${name}`,
    replacement: fileURLToPath(new URL(`./src/api/offline/${name}.js`, import.meta.url))
  }))

  return {
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
      // ⚠️ 顺序不能反：`/^@\/api\/(admin|auth|…)$/` 这些精确别名必须排在 `@` 前面，
      //    否则 `@` 会先把它们吃掉，离线模式静默失效（页面照常打开，但请求打到真后端）。
      alias: [
        ...(offline ? offlineAliases : []),
        {
          find: '@',
          replacement: fileURLToPath(new URL('./src', import.meta.url))
        }
      ]
    },
    build: {
      // 离线产物单独放一个目录：`dist/` 留给真接口构建，两者互不覆盖。
      // 交给老师的包只取 `dist-offline/`。
      outDir: offline ? 'dist-offline' : 'dist',
      emptyOutDir: true
    },
    server: {
      // ⚠️ `0.0.0.0` 是给**同一个局域网里的手机 / 另一台电脑**看的，不是给公网看的。
      //    同网段访问用 `http://<本机内网IP>:5173`。
      host: '0.0.0.0',
      port: 5173,
      open: false,

      // 离线模式没有后端，不注册任何代理——请求根本不会发出去（api 已被替换）。
      ...(offline
        ? {}
        : {
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
            // ⚠️ 目标主机由 `VITE_BACKEND_HOST` 决定：
            //      · 本机四进程联调（默认）→ 127.0.0.1
            //      · 后端跑在容器 / 虚拟机上 → `npm run dev:vm`，读 `.env.vm` 里的 192.168.23.133
            //    生产环境由 Nginx 按同样四条 location 做同源反代。
            proxy: {
              '/api/track': {
                target: `http://${backendHost}:8082`,
                changeOrigin: true
              },
              '/api/realtime': {
                target: `http://${backendHost}:8081`,
                changeOrigin: true,
                // SSE 是长连接，代理不能超时断开（默认无超时，这里写明意图）。
                ws: false
              },
              '/api/screen': {
                target: `http://${backendHost}:8080`,
                changeOrigin: true
              },
              '/api': {
                target: `http://${backendHost}:8082`,
                changeOrigin: true
              }
            }
          })
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
