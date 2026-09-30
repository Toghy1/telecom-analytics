import * as echarts from 'echarts/core'
import { MAP_NAME } from './screenCharts'

/**
 * 中国地图 geoJSON 的加载与注册。
 *
 * ⚠️ **ECharts 5 起不再内置地图数据**（4.x 时代的 `echarts/map/js/china.js`
 *    已移除）。要用 `type: 'map'` 必须先自己 `registerMap()`，否则运行时
 *    报 `Map china not exists`。这是接 ECharts 地图时第一个会撞上的坑。
 *
 * 数据放在 `public/map/china.json`（582 KB，省级边界），**不打进 JS 包**：
 *   · 它是大屏板块④**一块图**才需要的资源，塞进主包会让整个营业厅的首屏
 *     都多背着 582 KB；
 *   · 放 `public/` 下由浏览器按需拉取并缓存，与真实部署时静态资源走
 *     Nginx 的形态一致。
 *
 * 路径用 `import.meta.env.BASE_URL` 拼接，不写死 `/map/...`：
 * 部署到子路径（如 `http://host/demo01/`）时，写死的绝对路径会 404，
 * 而那是一个**只有部署后才暴露、本地开发永远看不到**的问题。
 */

let pending = null

/**
 * 确保地图已注册。重复调用只发一次请求。
 *
 * @returns {Promise<boolean>}
 */
export function ensureChinaMap() {
  if (!pending) {
    pending = fetch(`${import.meta.env.BASE_URL}map/china.json`)
      .then((res) => {
        if (!res.ok) {
          throw new Error(`地图数据加载失败（HTTP ${res.status}）`)
        }
        return res.json()
      })
      .then((geo) => {
        echarts.registerMap(MAP_NAME, geo)
        return true
      })
      .catch((err) => {
        // 失败了要把缓存清掉，否则一次网络抖动会让地图**永久**加载不出来
        // ——后续调用都直接返回那个失败的 Promise，重试也没用。
        pending = null
        throw err
      })
  }
  return pending
}
