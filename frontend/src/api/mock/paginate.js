/**
 * 假后端的分页器。
 *
 * 返回值**刻意长成 MyBatis-Plus `Page<T>` 的样子**——`records` / `total` /
 * `pages` / `current` 四个字段一个不少、名字一致。理由：
 *
 * `claude.md` 决策「统一响应体采用 `Result<T>`」已把分页的载荷位置钉死在
 * `data` 内部（`total` / `pages` / `current` / `records`）。假后端只要照着
 * 这个形状返回，页面组件从一开始就是按真实契约写的；等接后端时，
 * `src/api/` 里那一行 `return Promise.resolve(paginate(...))` 换成
 * `return request.get('/bills', { params })` 就完事，**页面一行都不用改**。
 *
 * 反过来，如果这里图省事直接返回数组，页面就得自己维护「第几页、共几条」，
 * 接后端时这部分逻辑全部要重写——这正是当初决定「分页现在就做」的原因：
 * 分页影响组件结构（要挂 `el-pagination`、要维护 `currentPage` 状态），
 * 不是接线时能顺手补的。
 */
export function paginate(rows, page = 1, size = 10) {
  const total = rows.length
  // 空集时 pages 记 0 而不是 1——与 MyBatis-Plus 的 Page 保持一致。
  // 但 current 仍收敛到 1，否则页面上「第 0 页 / 共 0 页」会显示成负数页码。
  const pages = total === 0 ? 0 : Math.ceil(total / size)
  const current = Math.min(Math.max(1, page), Math.max(1, pages))
  const start = (current - 1) * size

  return { total, pages, current, records: rows.slice(start, start + size) }
}
