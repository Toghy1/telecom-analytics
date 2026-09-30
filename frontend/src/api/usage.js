import { mockUsage } from './mock/usage'
import { mockAccount } from './mock/account'
import { getPackageById } from './package'
import { DEMO_MONTH } from './mock/period'
import { PACKAGE_UNITS } from '@/utils/constants'

/**
 * 余量查询。《开发文档》§6.4 #1（账户总览）与 #4（余量查询）共用这一个函数。
 *
 * ---------------------------------------------------------------------------
 * **减法在这里做，不在页面里做。** 这是本文件最要紧的一条约定。
 *
 * 余量 = 套餐额度 − 本月已用，看着只有一行减法，但它牵着一个真实的坑：
 * `data_quota` 与 `used_data` 必须同单位（GB 对 GB）。单位若不一致——
 * 比如一侧是 GB、一侧是 MB——相减**不报错、不抛异常，结果只是错**，
 * 错成一个看起来很正常的数字（页面显示「剩余 59.8GB」而实际应为「47.3GB」）。
 * 这类错误不崩不炸，验收前几乎发现不了。
 *
 * 如果每个用到的页面各减一次，这个坑就有了 N 个入口，且单位是否一致
 * 取决于每个人当时记得多少。收口到这一个函数，就是收口成一个入口。
 *
 * 真后端的形态也一样：这个减法属于服务端，前端只负责显示。所以等接线时，
 * 整个文件只剩一个 `return request.get('/account/usage/remaining', ...)`。
 * ---------------------------------------------------------------------------
 */

/** 三个量纲的展示名。单位不写死在这里，取自 `PACKAGE_UNITS` 那唯一一份。 */
const DIMENSIONS = [
  { key: 'data', label: '国内流量', unit: PACKAGE_UNITS.data_quota, quotaField: 'data_quota', usedField: 'used_data' },
  { key: 'voice', label: '国内通话', unit: PACKAGE_UNITS.voice_quota, quotaField: 'voice_quota', usedField: 'used_voice' },
  { key: 'sms', label: '国内短信', unit: PACKAGE_UNITS.sms_quota, quotaField: 'sms_quota', usedField: 'used_sms' }
]

/**
 * 查某账期的余量。
 *
 * ⚠️ 签名里**没有 userId / packageId**——这不是漏了。真后端从 JWT 里取当前用户，
 *    再顺着 `t_account.package_id` 找套餐，前端无从也无需参与。这里用
 *    `mockAccount` 顶替「从 token 读出来的当前用户」，就是在假装那件事。
 *
 * 后端就绪后换成：
 *     return request.get('/account/usage/remaining', { params: { month } })
 */
export async function getRemaining(month = DEMO_MONTH) {
  const account = mockAccount
  const pkg = await getPackageById(account.package_id)

  if (!pkg) {
    // 没有套餐就没有额度，余量无从谈起。真后端会返回业务错误码（1001 起）。
    return Promise.reject(new Error('当前账号未订购套餐，无法计算余量'))
  }

  // ⚠️ 两个条件都要带。只按月份查会取到**别人的**用量——`t_usage` 是全体用户
  //    共用的一张表，`user_id` 条件掉了不会有任何报错，页面会安静地显示
  //    另一个人的用量。`mock/usage.js` 里放了一条 1002 的 2026-09 记录，
  //    就是为了让这个错误能被测出来而不是靠人记得。
  const usage = mockUsage.find((u) => u.user_id === account.id && u.usage_month === month)

  const items = DIMENSIONS.map(({ key, label, unit, quotaField, usedField }) => {
    const quota = pkg[quotaField]
    // 查不到用量行时按「已用 0」处理，即额度全剩。
    // 这里有个真实的判断：**「没有记录」不等于「没用过」**。之所以敢这么取，
    // 是因为 `t_usage` 的唯一键是 `uk_user_month`，契约上「一人一月一行」——
    // 造数脚本应在每月初为所有在网用户落一行 0 用量，于是「查不到行」只可能是
    // 数据出了问题，而不是「这个月没用」。真后端接入时这条约定要写进造数脚本，
    // 否则新入网用户首月会显示成「一点没用」。
    const usedValue = usage ? usage[usedField] : 0
    const remain = quota - usedValue

    return {
      key,
      label,
      unit,
      quota,
      used: usedValue,
      remain,
      // 超套时进度条封顶 100%，但 `remain` **照实为负**——进度条可以画满，
      // 数字不能撒谎。页面据此把超套的条染成红色（见 Usage.vue）。
      percent: quota > 0 ? Math.min(100, Math.round((usedValue / quota) * 100)) : 0
    }
  })

  return {
    usage_month: month,
    package_id: pkg.id,
    package_name: pkg.name,
    has_usage: Boolean(usage),
    items
  }
}
