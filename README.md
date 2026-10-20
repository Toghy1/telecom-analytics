# 电信用户行为分析系统 · 前端演示

这是项目的**前端部分**，包含**营业厅、用户自助、实时大屏、管理后台**四块界面。

数据分两段：

- **营业厅、管理后台、大屏实时板块①②、登录**仍是前端假数据（`frontend/src/api/mock/`），只装 Node.js 就能跑；
- **大屏离线板块③④与子页 `/screen/pipeline`** 已接本地后端（MySQL 8 + ETL + Spring Boot）。**不启动后端时，这几块图没有数据**，页面其余部分不受影响。

完整跑通离线数据需要 MySQL 8 + JDK 17 + Maven，步骤见第五节。

---

## 一、你需要准备什么

| 需要 | 说明 |
|---|---|
| **Node.js ≥ 20.19** | 必装。推荐 20 LTS 或 22 以上（用 `node -v` 查看版本） |
| 浏览器 | Chrome / Edge 均可 |
| **MySQL 8 + JDK 17 + Maven** | 可选。只有要看分析平台的**真数据**时才需要，见第五节 |
| Redis / Kafka / Docker | **不需要** |

---

## 二、三步跑起来

在解压出来的目录里，打开终端（Windows 用 PowerShell 或 CMD 都行）：

```bash
cd frontend
npm install
npm run dev
```

终端最后会打印一行 `Local: http://localhost:5173/`，浏览器打开这个地址即可。

> **`npm install` 第一次会比较慢**，要下载约 200MB 依赖，耐心等它跑完（几分钟）。
> 看到 `added xxx packages` 就是成功了。之后再启动都是秒开。

---

## 三、演示账号

登录页上有「填入」按钮，点一下就能自动填好，不用手敲。

| 角色 | 登录标识 | 口令 | 登录后进入 |
|---|---|---|---|
| 电信用户 | `13800001234` | `123456` | 用户自助页 `/user/dashboard` |
| 运营分析员 | `analyst` | `123456` | 实时大屏 `/screen` |
| 系统管理员 | `admin` | `123456` | 管理后台 `/admin/users` |

⚠️ **登录标识按角色不同**：电信用户填**手机号**，分析员和管理员填**用户名**。

---

## 四、都有哪些页面

| 模块 | 地址 | 内容 |
|---|---|---|
| **营业厅** | `/` | 首页、套餐列表、优惠活动（未登录也能看） |
| **用户自助** | `/user/dashboard` | 话费余额、账单、余量、办理、工单、个人信息 |
| **实时大屏** | `/screen` | 用户行为分析大屏；`/screen/pipeline` 是系统运行分析 |
| **管理后台** | `/admin/users` | 用户管理、套餐配置、工单处理 |

---

## 五、可选：接通本地离线数据

只看营业厅与管理后台可以跳过本节。要让分析平台显示真数据，在项目根目录按顺序执行：

**1. 建库建表**（MySQL root 口令按你本机的改）

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS demo01 DEFAULT CHARACTER SET utf8mb4"
mysql -u root -p demo01 < db/schema.sql
```

> PowerShell 不支持 `<` 重定向，这一步请用 **CMD** 或 **Git Bash** 执行。

**2. 灌数据**（读 `mock/out/`；会清空行为表与账号 / 套餐种子表，别指向有真实数据的库）

```bash
cd etl
python etl.py --input ../mock/out --password 123456
cd ..
```

**3. 起后端**（新开一个终端）

```bash
cd backend
mvn -DskipTests package
java -jar target/analysis-service-0.1.0.jar
```

**4. 起前端**（再开一个终端，或沿用第二节已启动的）

```bash
cd frontend
npm run dev
```

Vite 已把 `/api` 代理到 `http://127.0.0.1:8080`，前端不用改地址。

> 当前数据：2026-10-06 ~ 10-19 共 14 天、500 用户、61,109 行（入库 56,220 / 判脏 4,889）。
> 判脏率、漏斗、留存、地域 / 套餐 / 活跃度分布均由 `etl/etl.py` 算出。

---

## 六、关于「假数据」

- **仍走假数据的**：营业厅业务页、管理后台、大屏实时板块①②、登录与权限。
- 假数据实现在 `frontend/src/api/mock/` 目录下，例如登录就是拿 `mock/account.js` 里的账号直接比对口令；
  手机号、身份证的脱敏也在这一层做掉，页面里没有任何脱敏代码。
- **已接真数据的**：`api/screen.js` 的离线指标（漏斗 / 留存 / 地域 / 套餐 / 活跃度）与
  `api/pipeline.js` 的四块（今日管道量 / 脏数据趋势 / 规则命中 / 死信）。
- 后端就绪后，整个 `api/mock/` 目录会被删掉，`api/` 下的函数改成发真实请求。

**所以：不要把这个目录里的假数据当成真实需求的依据，改数据也不会影响真实业务。**

---

## 七、常见问题

**① 页面白屏，或者终端报 504 / 依赖相关错误**

多半是 `node_modules` 里残留了旧版本缓存。按顺序执行：

```bash
# 1. 停掉正在跑的 dev（在终端按 Ctrl+C）
# 2. 删掉依赖和缓存
cd frontend
rm -rf node_modules .vite
# Windows 上如果 rm 不好用，就在资源管理器里手动删这两个目录
# 3. 重新装
npm install
npm run dev
```

> 注意：改依赖版本后，**光重启是不够的**，必须真的把 5173 端口上的进程杀掉，
> 否则新代码跑在旧缓存上，症状就是白屏。

**② 装依赖特别慢 / 卡住**

换国内镜像（任选其一）：

```bash
npm config set registry https://registry.npmmirror.com
```

**③ 想让同一局域网里的手机或另一台电脑看**

开发服务器已经开了 `0.0.0.0`，所以直接用手机浏览器打开：

```
http://<你这台电脑的内网IP>:5173
```

内网 IP 用 `ipconfig` 查（找 `IPv4 地址`，通常是 `192.168.x.x`）。
Windows 防火墙可能弹窗询问，选「允许」。

**④ 5173 端口被占用**

Vite 会自动换到 5174 或下一个可用端口，**看终端实际打印的地址**即可，
不要死记 5173。

---

## 八、目录结构

```
demo01/
├── README.md              本文件
├── frontend/              前端工程（就是它跑起来）
│   ├── index.html
│   ├── package.json       依赖清单与启动脚本
│   ├── package-lock.json  锁定依赖版本，别删
│   ├── vite.config.js     构建配置（端口 5173 + /api 代理）
│   ├── public/map/        大屏用的地图数据
│   └── src/
│       ├── api/           所有接口封装（集中在这里，页面不直接调 axios）
│       │   └── mock/      假数据实现（接后端时整体删除）
│       ├── components/    通用组件
│       ├── layouts/       四种布局：营业厅 / 用户 / 大屏 / 管理后台
│       ├── router/        路由与权限守卫
│       ├── stores/        Pinia 状态（登录态）
│       ├── utils/         工具函数
│       └── views/         页面
├── db/
│   └── schema.sql         11 张表的建表 DDL（业务 7 + 行为 4）
├── etl/
│   └── etl.py             离线 ETL 替身：mock/out → MySQL（仅依赖 pymysql）
├── backend/               Spring Boot 3.5 / JDK 17 本地只读服务
│   ├── pom.xml
│   └── src/main/...       /api/screen/* 三个只读接口
├── mock/
│   ├── simulate_access.py 造数脚本（含 8% 脏数据注入）
│   └── out/               造数产物：users.json + behavior_YYYY-MM-DD.jsonl
└── init/
    ├── 开发文档.md        需求、功能清单与设计说明
    └── claude.md          决策记录：开发过程中每个取舍的原因
```

---

## 九、构建生产版本

```bash
cd frontend
npm run build      # 产物输出到 frontend/dist/
npm run preview    # 本地预览构建产物
```
