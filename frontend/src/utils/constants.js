/**
 * 全站常量。
 *
 * 为什么套餐单位要单独放这里，而不是写在页面模板里：
 *   `data_quota` / `voice_quota` / `sms_quota` 三个字段的单位若直接写死在模板里，
 *   一旦要调整就得挨个页面改（账户总览、余量查询、套餐资费浏览……）。
 *   集中在这里，改动只发生在一个地方。
 */

/**
 * 套餐三个量纲的单位。
 * 2026-09-30 定稿（claude.md 决策「套餐量纲单位定稿」）。
 *
 * ⚠️ **必须与后端 `t_package` / `t_usage` 保持同一套**——余量由
 *    `t_package.data_quota − t_usage.used_data` 相减得出，两侧单位若不一致，
 *    相减不报错、不抛异常，只是结果错，页面上会显示成一个看起来完全正常的数字。
 */
export const PACKAGE_UNITS = {
  data_quota: 'GB',
  voice_quota: '分钟',
  sms_quota: '条'
}

/** 金额单位 */
export const CURRENCY = '元'

/**
 * 角色（`t_account.role`）的中文名与标签色。
 *
 * `ROLE_LABEL` 原先只在 `Login.vue` 里有一份局部副本（用于演示账号提示区）。
 * 管理后台的「用户管理」页也要按角色显示中文名与标签色，于是把它提到这里
 * ——**同一个角色名在登录页与管理后台不能有两种写法**。
 *
 * `ROLE_TAG` 是 `el-tag` 的 type，取值与 `TICKET_STATUS[x].tag` 同一套路。
 * 色彩只是为了让三种角色在一列里一眼分开，没有语义（`danger` 不表示
 * 「管理员是危险的」，只是红色与另两色区分度最大）。
 */
export const ROLE_LABEL = {
  USER: '电信用户',
  ANALYST: '运营分析员',
  ADMIN: '系统管理员'
}

export const ROLE_TAG = {
  USER: 'info',
  ANALYST: 'warning',
  ADMIN: 'danger'
}

/** 账单流水方向。⚠️ 金额一律存正数，方向由 type 区分（见 claude.md 业务数据模型决策）。 */
export const BILL_TYPE = {
  BILL: { label: '消费', sign: '-' },
  PAY: { label: '缴费', sign: '+' }
}

/**
 * 工单类型（`t_ticket.type`）。2026-09-30 定稿为四类。
 *
 * 编码用英文大写、中文标签放这里，与 `BILL_TYPE` 同一套写法。之所以不把中文
 * 直接存库：日后要加英文界面或改称呼，改的是这张表，而不是已经写进库里的数据。
 */
export const TICKET_TYPE = {
  NETWORK: { label: '网络故障' },
  BILLING: { label: '资费问题' },
  CONSULT: { label: '业务咨询' },
  COMPLAINT: { label: '投诉建议' }
}

/**
 * 工单状态（`t_ticket.status`）。三值与《开发文档》§5.1.2 一致。
 *
 * `tag` 是 el-tag 的色值类型，放在这里而不是页面模板里——管理后台的工单列表
 * 要显示同一套状态，颜色必须和用户端一致，否则同一个「处理中」在两处长得不一样。
 *
 * ⚠️ 状态流转（待处理 → 处理中 → 已完成）由**管理后台**驱动，
 *    用户端只读。这里是三个状态的定义，不是状态机。
 */
export const TICKET_STATUS = {
  PENDING: { label: '待处理', tag: 'warning' },
  PROCESSING: { label: '处理中', tag: 'primary' },
  DONE: { label: '已完成', tag: 'success' }
}

/**
 * 工单描述的字数上下限。
 *
 * ⚠️ 表单校验（`Ticket.vue`）与 api 层（`api/ticket.js`）都要用这两个数，
 *    所以必须只有一份。分头写两个 200，改的时候漏一处，就会出现
 *    「表单放行、接口拒收」——用户看着表单校验通过，点提交却报错。
 *    上限 200 的取法：这段文字要能在「我的工单」列表里读完整，
 *    更长的内容在表格里展示不了。真需要长篇描述那是客服工单系统的范畴，
 *    本项目已定「工单流转从简」。
 */
export const TICKET_CONTENT_LIMIT = { min: 5, max: 200 }

/**
 * 埋点事件 → 行为大类（大屏板块②「行为类型分布」环形图用）。
 *
 * ⚠️ 键必须**完整覆盖**《开发文档》§5.1.1 的十二个事件，一个不多一个不少。
 *    这里是那 12 个事件的第二处清单——第一处是 `utils/tracker.js` 的注释与
 *    路由表的 `meta.track`。若日后新增事件，三处要一起改；漏了这张表，
 *    新事件不会报错，只会**从环形图里消失**（按 event_code 分组时它根本没有
 *    对应的标签，画出来是个没有名字的扇区，或者干脆被聚合丢掉）。
 */
export const EVENT_CATEGORY = {
  view_home: 'BROWSE',
  view_package_list: 'BROWSE',
  view_promo: 'BROWSE',

  login: 'AUTH',
  logout: 'AUTH',

  query_balance: 'QUERY',
  query_bill: 'QUERY',
  query_usage: 'QUERY',

  view_dashboard: 'ACCOUNT',
  view_profile: 'ACCOUNT',

  order_package: 'ORDER',
  submit_ticket: 'SERVICE'
}

/**
 * 行为大类的中文标签与配色。
 *
 * `color` 给 ECharts 用。放在这里而不是图表配置里，是因为**行为类型分布
 * （板块②）与页面热度（同板块）在同一个视觉区域**，两条色序若各写一份，
 * 调色时会顾此失彼。
 */
export const EVENT_CATEGORY_LABEL = {
  BROWSE: { label: '页面浏览', color: '#3d8bff' },
  AUTH: { label: '登录登出', color: '#22c1a4' },
  QUERY: { label: '业务查询', color: '#f5a524' },
  ACCOUNT: { label: '账户信息', color: '#a06cff' },
  ORDER: { label: '套餐办理', color: '#ff5c8a' },
  SERVICE: { label: '在线报障', color: '#4fd1e0' }
}

/**
 * 判脏规则清单（7 条，《开发文档》§5.3 / claude.md 决策「脏数据规则清单与注入方案」）。
 *
 * ⚠️ **子页「规则命中分布」图的横轴取自这张表，而不是取自接口返回的数据。**
 *    这不是写法偏好，是必须的：
 *
 *      - 横轴来自数据 → 某条规则本期一次都没命中，它**从图上消失**，
 *        图表看起来完全正常（七条变六条，谁也不会数）。而「这条规则实现了吗、
 *        跑到了吗」恰恰是验收时要看的东西。
 *      - 横轴来自常量 → 没命中的规则画成 0，一眼可见。
 *
 *    同理，`code` 与后端存进 `dwd_behavior_dirty.fail_reason` 的取值必须一致。
 */
export const DIRTY_RULE = {
  MISSING_REQUIRED: { label: '必填字段缺失' },
  ENUM_OUT_OF_RANGE: { label: '枚举越界' },
  TYPE_ERROR: { label: '字段类型错误' },
  TIME_ILLEGAL: { label: '时间戳非法或越界' },
  ID_FORMAT_ERROR: { label: '标识格式非法' },
  DUPLICATE: { label: '重复上报' },
  OVERLONG_GARBLED: { label: '字段超长 / 乱码' }
}

/** 死信来源（`dwd_behavior_dirty.source`）。 */
export const DIRTY_SOURCE = {
  REALTIME: { label: '实时链路' },
  OFFLINE: { label: '离线链路' }
}

/**
 * 行为漏斗的四段（大屏板块③）。
 *
 * 四段取自《开发文档》§5.1 与 claude.md 决策「统一日志规范」：
 * 「漏斗 `view_home → login → query_* → order_package` 四段全部取自上表，
 * 无需额外埋点」。
 *
 * ⚠️ **顺序即漏斗顺序**，不能靠 `sort` 或数据到达顺序决定。
 *    翻页顺序定了漏斗的箭头方向，写反了图会倒过来画，
 *    而倒过来的漏斗看起来仍然「像个漏斗」——递减方向由数值决定，
 *    标签顺序写反了只会让箭头指向错误的对象。
 *    故这里用数组而不是对象，顺序是数据的一部分。
 */
export const FUNNEL_STEP = [
  { code: 'view_home', label: '访问' },
  { code: 'login', label: '登录' },
  { code: 'query', label: '查询' },
  { code: 'order_package', label: '办理' }
]
