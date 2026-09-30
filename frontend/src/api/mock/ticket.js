/**
 * 工单假数据。字段对齐《开发文档》§5.2 ⑥ `t_ticket`：
 * id / user_id / type / content / status / handler_id / create_time / handle_time
 *
 * `type` 取 `TICKET_TYPE` 的四个编码，`status` 取 `TICKET_STATUS` 的三个编码
 * ——英文大写存库、中文标签放前端常量表，与 `t_bill.type` 同一套纪律。
 *
 * ⚠️ 三条不变量，假数据必须自洽：
 *
 *   ① **`handler_id` / `handle_time` 跟着 `status` 走**，三者不能各说各话：
 *        PENDING    → handler_id = null, handle_time = null（还没人接手）
 *        PROCESSING → handler_id 有值,   handle_time = null（已接手，尚未办结）
 *        DONE       → handler_id 有值,   handle_time 有值
 *      `handle_time` 定为「**处理完成**时间」而不是「开始处理时间」。
 *      没有这条，就会出现「已完成的工单没有处理时间」这种没人会注意到的数据错误。
 *
 *   ② **`handle_time` 不早于 `create_time`**——先有工单才有处理。
 *
 *   ③ **混了两条用户 1002 的记录，其中一条 `create_time` 取全局最新。**
 *      `api/ticket.js` 必须按 user_id 过滤；这道过滤一旦失效，1002 那条会直接
 *      落在「我的工单」第一页第一行。放末页就白放了——那时候要翻两页才看得见，
 *      而「翻页才看见」等于看不见。
 *
 * 三个状态在用户 1001 的记录里**都出现了**，四个类型也都出现了：
 * 状态列与类型列的中文翻译一旦写错，页面上立刻能比对出来。
 */
export const mockTickets = [
  {
    id: 7001,
    user_id: 1001,
    type: 'NETWORK',
    content: '家中宽带每天 20 点后频繁掉线，重启光猫只能恢复十来分钟。',
    status: 'PENDING',
    handler_id: null,
    create_time: '2026-09-28 17:55:02',
    handle_time: null
  },
  {
    id: 7002,
    user_id: 1001,
    type: 'BILLING',
    content: '9 月账单里有一笔 30 元的增值业务费，我没有订购过，想确认来源。',
    status: 'PENDING',
    handler_id: null,
    create_time: '2026-09-24 20:15:33',
    handle_time: null
  },
  {
    id: 7003,
    user_id: 1001,
    type: 'COMPLAINT',
    content: '营业厅自助机前排队时间过长，工作日上午人也很多，希望能增加窗口。',
    status: 'PROCESSING',
    handler_id: 2,
    create_time: '2026-09-18 09:27:44',
    handle_time: null
  },
  {
    id: 7004,
    user_id: 1001,
    type: 'CONSULT',
    content: '咨询副卡如何办理，需要本人到营业厅吗？月费是多少？',
    status: 'DONE',
    handler_id: 2,
    create_time: '2026-08-12 10:03:21',
    handle_time: '2026-08-13 14:20:05'
  },
  {
    id: 7005,
    user_id: 1001,
    type: 'NETWORK',
    content: '手机在郊区老家经常没有信号，同行的其他运营商手机可以正常使用。',
    status: 'DONE',
    handler_id: 2,
    create_time: '2026-07-05 15:40:09',
    handle_time: '2026-07-06 10:12:30'
  },
  {
    id: 7006,
    user_id: 1001,
    type: 'BILLING',
    content: '6 月账单显示的套餐月费与我办理时看到的宣传不一致，想核实一下。',
    status: 'DONE',
    handler_id: 2,
    create_time: '2026-06-20 11:12:00',
    handle_time: '2026-06-21 09:30:44'
  },

  /* ---- 以下两条属于用户 1002，用于证伪 user_id 过滤（见上文不变量 ③）---- */
  {
    id: 7901,
    user_id: 1002,
    type: 'NETWORK',
    content: '宽带续费后网速比之前慢了很多，测速只有 20Mbps。',
    status: 'PENDING',
    handler_id: null,
    create_time: '2026-09-29 21:40:11',
    handle_time: null
  },
  {
    id: 7902,
    user_id: 1002,
    type: 'BILLING',
    content: '短信验证码收不到，换了一台手机还是一样。',
    status: 'DONE',
    handler_id: 2,
    create_time: '2026-05-08 13:20:00',
    handle_time: '2026-05-09 10:00:00'
  }
]
