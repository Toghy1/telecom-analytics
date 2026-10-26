/**
 * 埋点上报 —— **离线演示模式下为空实现**。
 *
 * 真实现在 `src/api/track.js`：POST `/api/track`，由 business-service
 * 投 Kafka，再经 Flink 洗净入库。离线模式没有后端，这条链路一个环节都不在；
 * 若沿用真实现，每次路由切换都会在控制台留下一条失败的 fetch。
 *
 * 刻意保留同名同签名的函数，是因为 `utils/tracker.js` 与页面都只认
 * `postTrack(payload)`——换模式时调用方一行不用改。
 *
 * ⚠️ 离线模式的大屏实时数字由 `offline/realtime.js` 的定时器模拟，
 *    **不来自这里**。两者没有关系。
 */
export function postTrack() {
  // 即发即忘，离线时无事可做。
}
