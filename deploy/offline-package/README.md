# 离线演示包（交给老师的版本）

这个目录只放**启动脚本与说明**，`dist/` 是构建产物、不入库。
构建命令与组装步骤见下。

## 它解决什么问题

前端默认是「连真后端」的：Vite 代理把 `/api/**` 转给 8080 / 8081 / 8082，
虚拟机或本机后端没起时，页面会一片 `502`。

离线演示模式把 `src/api/` 的 12 个模块整体替换为 `src/api/offline/`
（2026-10-26 从 git 历史恢复的假实现），页面组件一行未改。
数据全部内置，不连任何后端。

## 重新生成

```bash
cd frontend
npm run build:offline          # 产物在 frontend/dist-offline/
```

然后按下面的结构组装（`dist` 即 `dist-offline` 的副本）：

```
demo01-提交包/
  启动.bat
  serve.ps1
  运行说明.txt
  dist/
  源代码/            # 可选，按需附上仓库副本（排除 node_modules / target / jars）
```

## 为什么需要 `serve.ps1`

构建产物是 ES Module，双击 `index.html` 用 `file://` 打开会被浏览器的
CORS 策略拦掉；路由又是 history 模式，需要「找不到路径回落 index.html」。
`serve.ps1` 用 Windows 自带的 PowerShell 起一个只监听 `localhost` 的静态
服务器，从 8088 起自动找空闲端口，并自动打开浏览器。老师电脑不需要
Node.js / Python / 数据库。

## 两个 npm 脚本的关系

| 命令 | 数据来源 | 用途 |
| --- | --- | --- |
| `npm run dev` | 真接口，后端在 `127.0.0.1` | 本机四进程联调 |
| `npm run dev:vm` | 真接口，后端在 `.env.vm` 的 `192.168.23.133` | 虚拟机开着时的开发 |
| `npm run dev:offline` | 内置假数据 | 离线开发 / 查页面 |
| `npm run build:offline` | 内置假数据 | 生成上面这个演示包 |

## 已知边界

- 数据是 2026-09 的固定快照，不随时间变化
- 实时大屏的数字由 `src/api/offline/realtime.js` 的定时器模拟，
  与 `mock/realtime_feed.py` 无关（后者只服务真链路）
- 管理后台的增删改只改浏览器内存，刷新即还原
- 登录是本地校验，不产生网络请求、不写后端
