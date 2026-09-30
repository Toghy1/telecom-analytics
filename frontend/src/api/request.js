import axios from 'axios'

/**
 * 全站唯一的 axios 实例。
 * 选型⑤纪律①：**所有请求集中封装于 src/api/，页面组件不直接调 axios**。
 * 选型⑤纪律②：响应拦截器统一解包 `Result<T>` 与处理 401。
 *
 * `Result<T>` 结构已于 2026-09-30 定稿（见 claude.md 决策「统一响应体采用 Result<T>」）：
 *
 *     { "code": 200, "message": "操作成功", "data": { … } }
 *
 * **失败分两层**，本文件两层都管，但只管各自该管的那一层：
 *
 *   - HTTP 状态码 —— 程序层面（401 未登录 / 403 无权限 / 500 服务端异常）。
 *     走下方**错误分支**，401 在拦截器里一次处理完，全站二十个接口都不用再判。
 *   - `code` —— 业务层面（200 成功；1001 起为业务错误，如余额不足）。
 *     走下方**成功分支**里的 `!== 200` 判断。`message` 按契约就是"可直接展示给
 *     用户的文案"，所以这里默认弹一次提示再 reject；页面若还要做别的（如跳转、
 *     刷新列表）就在 catch 里补，不必再弹第二遍。
 */
const SUCCESS_CODE = 200

const TOKEN_KEY = 'demo01_token'
const USER_KEY = 'demo01_user'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：统一带上 Authorization 头
request.interceptors.request.use((config) => {
  // 直接读 localStorage 而不是 Pinia：拦截器可能在 app 初始化前就被调用
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const body = response.data

    // 后端返回统一响应体 → 解包出 data，调用方拿到的就是业务数据本身
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === SUCCESS_CODE) {
        return body.data
      }
      // 业务错误（1001 起）。把 code 一起挂到 Error 上，
      // 页面若想按具体错误码分支处理（如"余额不足"时高亮充值按钮）拿得到。
      const err = new Error(body.message || '请求失败')
      err.code = body.code
      ElMessage.error(err.message)
      return Promise.reject(err)
    }

    // 不是统一响应体的（例如文件流），原样返回
    return body
  },
  (error) => {
    const status = error.response?.status

    if (status === 401) {
      // token 失效：清干净本地登录态，回登录页并把当前地址带上
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      ElMessage.error('登录已过期，请重新登录')
      if (window.location.pathname !== '/login') {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.href = `/login?redirect=${redirect}`
      }
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络异常')
    }

    return Promise.reject(error)
  }
)

export default request
