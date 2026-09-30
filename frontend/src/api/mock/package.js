/**
 * 套餐假数据。字段对齐《开发文档》§5.2 ② `t_package`：
 * id / name / monthly_fee / data_quota / voice_quota / sms_quota /
 * description / status / create_time
 *
 * ⚠️ 三个量纲的单位已于 2026-09-30 定稿并被 `t_usage` 沿用：
 *    `data_quota` = GB、`voice_quota` = 分钟、`sms_quota` = 条
 *    （claude.md 决策「套餐量纲单位定稿」）。前端展示一律取
 *    `src/utils/constants.js` 的 `PACKAGE_UNITS`，不在模板里写字面量。
 *    本文件只负责「额度是多少」，不负责「单位是什么」。
 *
 * 最后一条 status=0（停用），用来验证「列表只显示在售套餐」这条过滤确实生效。
 *
 * ⚠️ `create_time` 不是装饰，它要能**罩住 `mock/order.js` 里那条办理时间线**：
 *    套餐 1/2/3 是主推款，上架时间定在 2024-01-05；三条「畅享 5G」若还写着
 *    2026-01-05，而办理记录里 2024-09 就办了 79 元套餐——上架晚于办理，
 *    同一份假数据里两个数对不上。孝心卡（2026-02）与学生特惠（2026-03）
 *    是后上的产品，各自的时间也都与办理记录里用到的日期咬合。
 */
export const mockPackages = [
  {
    id: 1,
    name: '畅享 5G 39 元套餐',
    monthly_fee: 39.0,
    data_quota: 30,
    voice_quota: 300,
    sms_quota: 100,
    description: '30GB 国内流量，300 分钟国内通话，超出后 0.29 元/分钟。',
    status: 1,
    create_time: '2024-01-05 09:00:00'
  },
  {
    id: 2,
    name: '畅享 5G 79 元套餐',
    monthly_fee: 79.0,
    data_quota: 60,
    voice_quota: 600,
    sms_quota: 200,
    description: '60GB 国内流量，600 分钟国内通话，赠送 5G 极速服务。',
    status: 1,
    create_time: '2024-01-05 09:00:00'
  },
  {
    id: 3,
    name: '畅享 5G 129 元套餐',
    monthly_fee: 129.0,
    data_quota: 100,
    voice_quota: 1000,
    sms_quota: 300,
    description: '100GB 国内流量，1000 分钟国内通话，附赠 1 张副卡。',
    status: 1,
    create_time: '2024-01-05 09:00:00'
  },
  {
    id: 4,
    name: '孝心卡 19 元套餐',
    monthly_fee: 19.0,
    data_quota: 5,
    voice_quota: 200,
    sms_quota: 50,
    description: '面向老年用户，5GB 流量 + 200 分钟通话，支持设置亲情号码。',
    status: 1,
    create_time: '2026-02-11 14:30:00'
  },
  {
    id: 5,
    name: '学生特惠 29 元套餐',
    monthly_fee: 29.0,
    data_quota: 40,
    voice_quota: 100,
    sms_quota: 100,
    description: '校园专项，40GB 定向流量，需通过学生身份认证。',
    status: 0,
    create_time: '2026-03-02 10:00:00'
  }
]
