/**
 * 子页 · 系统运行分析的假数据。
 *
 * 子页回答的是「系统本身跑得怎么样」，与主页的「用户在系统里做了什么」
 * 是两回事（决策「分析平台采用『主页 + 子页』结构」）。四块内容：
 *   ① 今日管道量  ② 脏数据占比趋势  ③ 规则命中分布  ④ 死信明细
 *
 * ⚠️ 本文件里有多处**跨板块的算术校验**，它们不是巧合，是刻意设计的证伪物：
 *
 *     采集 10420 − 判脏 834 = 入库 9586      （①内部）
 *     834 / 10420 = 8.0%                     （① ↔ ②今值）
 *     七条规则命中数合计 = 834                 （① ↔ ③）
 *     今日死信条数（按 source 分组合计）          （① ↔ ④）
 *
 *   任何一处口径写漏，这组等式就会破。之所以要这样设计，是因为子页没有
 *   「过滤条件」可证伪——它全是聚合数，写错了页面照样渲染，只是数字悄悄不对。
 *   让几处数字互相咬住，是这一页唯一的自查手段。
 *
 * 注入比例 8.0% 不是随手取的，是 claude.md 决策「脏数据规则清单与注入方案」
 * 定死的：七条规则 2.0 + 1.5 + 1.0 + 1.0 + 1.0 + 1.0 + 0.5 = 8.0%。
 * 假数据按这个比例生成，接后端时真脚本也按这个比例注入，两处才对得上。
 */

/** 统计日，与 `mock/screen.js` 的 `STAT_DATE` 同一天。 */
export const PIPELINE_DATE = '2026-09-30'

/**
 * ① 今日管道量。
 *
 * ⚠️ 三个数的口径必须说清楚，否则「清洗」与「入库」很容易被写成同一个数：
 *
 *     采集 collected —— Kafka 与 SFTP 两条通道**收到**的原始条数
 *     判脏 dirty     —— 被七条规则拦下、落 `dwd_behavior_dirty` 的条数
 *     入库 stored    —— 通过校验、写进 `dwd_behavior_log` 的条数
 *
 *   `stored = collected − dirty`，这是恒等式，不是巧合。
 *   若哪天把「判脏」写成「清洗后剩余」，等式两边会同时变成 9586，
 *   三个数字里有两个一样——那正是这处注释想拦住的写法。
 */
export const pipelineToday = {
  date: PIPELINE_DATE,
  collected: 10420,
  dirty: 834,
  stored: 9586
}

/**
 * ② 脏数据占比趋势（%）。近 7 天与近 30 天。
 *
 * ⚠️ **最后一个点（今天）必须等于 ① 算出来的 8.0%**（834 / 10420 = 8.0038%）。
 *    趋势线与数字卡讲的应该是同一件事——趋势图最后一个点与上方数字卡
 *    对不上，是最容易被追问、也最难解释的破绽。
 */
export const dirtyRatioTrend7d = [
  { stat_date: '2026-09-24', metric_value: 7.4 },
  { stat_date: '2026-09-25', metric_value: 8.1 },
  { stat_date: '2026-09-26', metric_value: 7.8 },
  { stat_date: '2026-09-27', metric_value: 8.6 },
  { stat_date: '2026-09-28', metric_value: 8.2 },
  { stat_date: '2026-09-29', metric_value: 7.9 },
  { stat_date: '2026-09-30', metric_value: 8.0 }
]

/** 近 30 天。前期略低（规则未上齐），中段稳定在 8% 附近。 */
export const dirtyRatioTrend30d = [
  { stat_date: '2026-09-01', metric_value: 6.2 },
  { stat_date: '2026-09-02', metric_value: 6.8 },
  { stat_date: '2026-09-03', metric_value: 7.1 },
  { stat_date: '2026-09-04', metric_value: 7.5 },
  { stat_date: '2026-09-05', metric_value: 7.3 },
  { stat_date: '2026-09-06', metric_value: 7.9 },
  { stat_date: '2026-09-07', metric_value: 8.3 },
  { stat_date: '2026-09-08', metric_value: 8.0 },
  { stat_date: '2026-09-09', metric_value: 7.7 },
  { stat_date: '2026-09-10', metric_value: 8.4 },
  { stat_date: '2026-09-11', metric_value: 8.1 },
  { stat_date: '2026-09-12', metric_value: 7.6 },
  { stat_date: '2026-09-13', metric_value: 8.8 },
  { stat_date: '2026-09-14', metric_value: 8.5 },
  { stat_date: '2026-09-15', metric_value: 7.9 },
  { stat_date: '2026-09-16', metric_value: 8.2 },
  { stat_date: '2026-09-17', metric_value: 8.7 },
  { stat_date: '2026-09-18', metric_value: 8.3 },
  { stat_date: '2026-09-19', metric_value: 7.8 },
  { stat_date: '2026-09-20', metric_value: 8.9 },
  { stat_date: '2026-09-21', metric_value: 8.4 },
  { stat_date: '2026-09-22', metric_value: 8.0 },
  { stat_date: '2026-09-23', metric_value: 7.5 },
  { stat_date: '2026-09-24', metric_value: 7.4 },
  { stat_date: '2026-09-25', metric_value: 8.1 },
  { stat_date: '2026-09-26', metric_value: 7.8 },
  { stat_date: '2026-09-27', metric_value: 8.6 },
  { stat_date: '2026-09-28', metric_value: 8.2 },
  { stat_date: '2026-09-29', metric_value: 7.9 },
  { stat_date: '2026-09-30', metric_value: 8.0 }
]

/**
 * ④ 死信明细（`dwd_behavior_dirty` 形状）。
 * 字段：id / source / raw_content / fail_reason / fail_time。
 *
 * ⚠️ 刻意做了两件事：
 *
 *   1. **两种 `source` 都有**（REALTIME 8 条 / OFFLINE 4 条）。若只放实时链路
 *      的死信，那后端的 `source` 字段写没写、写对没写对，页面上看不出来——
 *      全是 REALTIME 和全是 OFFLINE 长得一模一样。这正对应决策里
 *      「双通道注入」要落地的东西。
 *
 *   2. **`fail_reason` 覆盖全部七条规则**。规则 7「字段超长 / 乱码」只占 0.5%，
 *      是命中数最少的一条，最容易在实现时被漏掉；让它在这 12 条里出现一次，
 *      漏掉它就会立刻少一条。
 *
 * `raw_content` 存的是**原始上报内容**（决策明确「保留原始行，不做静默丢弃」），
 * 这里按 120 字符截断——死信表要能装下畸形的超长内容，页面上不能撑破表格。
 */
export const mockDeadLetters = [
  {
    id: 9001,
    source: 'REALTIME',
    raw_content:
      '{"event_id":"c1f0a2e8-4b71-4a2c-9f31-8d2e5a7c0b44","event_code":"query_bill","event_time":"2026-09-30 15:02:11","user_id":1001,"session_id":"7b2e...","page":"/user/bills","region":"上海市","device":"PC"}',
    fail_reason: 'MISSING_REQUIRED',
    fail_time: '2026-09-30 15:02:12'
  },
  {
    id: 9002,
    source: 'REALTIME',
    raw_content:
      '{"event_id":"8a3d1c07-2e55-4f8b-b0a9-1c7e93d4f260","event_code":"view_dashbord","event_time":"2026-09-30 15:02:19","user_id":1002,"session_id":"3f9a...","page":"/user/dashboard","region":"上海市","device":"MOBILE"}',
    fail_reason: 'ENUM_OUT_OF_RANGE',
    fail_time: '2026-09-30 15:02:20'
  },
  {
    id: 9003,
    source: 'OFFLINE',
    raw_content:
      '{"event_id":"5e9b7d31-6a04-4c82-9d17-2f8a4e6b1c93","event_code":"view_home","event_time":"2026-09-30 14:41:03","user_id":"1003","session_id":"b41c...","page":"/","region":"广东省深圳市","device":"PC"}',
    fail_reason: 'TYPE_ERROR',
    fail_time: '2026-09-30 14:50:00'
  },
  {
    id: 9004,
    source: 'REALTIME',
    raw_content:
      '{"event_id":"d7c2f4a9-1b38-4e60-8a25-9c3f7b1d5e08","event_code":"query_usage","event_time":"2027-03-11 09:14:52","user_id":1001,"session_id":"a05d...","page":"/user/usage","region":"上海市","device":"PC"}',
    fail_reason: 'TIME_ILLEGAL',
    fail_time: '2026-09-30 14:33:41'
  },
  {
    id: 9005,
    source: 'OFFLINE',
    raw_content:
      '{"event_id":"NOT-A-UUID","event_code":"view_promo","event_time":"2026-09-30 13:58:20","user_id":null,"session_id":"e7b3...","page":"/promos","region":"上海市","device":"PC"}',
    fail_reason: 'ID_FORMAT_ERROR',
    fail_time: '2026-09-30 14:20:00'
  },
  {
    id: 9006,
    source: 'REALTIME',
    raw_content:
      '{"event_id":"a1f8c3d5-9e27-4b06-7c94-3d1a8f2e6b57","event_code":"login","event_time":"2026-09-30 13:47:11","user_id":1002,"session_id":"c92e...","page":"/login","region":"上海市","device":"PC"}',
    fail_reason: 'DUPLICATE',
    fail_time: '2026-09-30 13:47:11'
  },
  {
    id: 9007,
    source: 'REALTIME',
    raw_content:
      '{"event_id":"b4e6a802-7c19-4d53-8f20-5a9b3e7c1d46","event_code":"view_package_list","event_time":"2026-09-30 13:22:07","user_id":1001,"session_id":"1d7f...","page":"/packages?from=home&utm=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","region":"上海市","device":"PC"}',
    fail_reason: 'OVERLONG_GARBLED',
    fail_time: '2026-09-30 13:22:08'
  },
  {
    id: 9008,
    source: 'OFFLINE',
    raw_content:
      '{"event_id":"3c7a9e14-5b62-4d08-a371-8e6f2c9b4d05","event_code":"logout","event_time":"2026-09-30 12:55:33","user_id":1003,"session_id":"9b6c...","page":"/user/dashboard","region":"上海市","device":"OTHER"}',
    fail_reason: 'MISSING_REQUIRED',
    fail_time: '2026-09-30 13:10:00'
  },
  {
    id: 9009,
    source: 'REALTIME',
    raw_content:
      '{"event_id":"f0d5b9 37-2e81-4a6c-b05d-7c3e9a1f4826","event_code":"query_balance","event_time":"2026-09-30 12:31:45","user_id":1001,"session_id":"5e8a...","page":"/user/balance","region":"上海市","device":"PC"}',
    fail_reason: 'ID_FORMAT_ERROR',
    fail_time: '2026-09-30 12:31:46'
  },
  {
    id: 9010,
    source: 'OFFLINE',
    raw_content:
      '{"event_id":"2b8e4c61-0f59-4a73-9e28-6d1b5f7a3c90","event_code":"submit_ticket","event_time":"2026-09-30 11:08:14","user_id":1004,"session_id":"d30b...","page":"/user/ticket","region":"广东省深圳市","device":"TABLET"}',
    fail_reason: 'ENUM_OUT_OF_RANGE',
    fail_time: '2026-09-30 11:20:00'
  },
  {
    id: 9011,
    source: 'REALTIME',
    raw_content:
      '{"event_id":"6a1c8f72-3d94-4e05-b7c2-9f4a1e6d8b30","event_code":"view_profile","event_time":"2026-09-30 10:44:29","user_id":1002,"session_id":"7c4d...","page":"/user/profile","region":"上海市","device":"MOBILE"}',
    fail_reason: 'DUPLICATE',
    fail_time: '2026-09-30 10:44:30'
  },
  {
    id: 9012,
    source: 'REALTIME',
    raw_content:
      '{"event_id":"9d3f5a28-6c71-4b09-8e46-1a7c2d5f9b83","event_code":"order_package","event_time":"2026-09-30 09:58:02","user_id":1001,"session_id":"f18e...","page":"/user/order","region":"上海市","device":"PC","ext":{"package_id":2}}',
    fail_reason: 'TIME_ILLEGAL',
    fail_time: '2026-09-30 09:58:03'
  }
]
