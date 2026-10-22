# 应用层容器部署

范围：MySQL / Redis / SFTP / business / realtime / analysis / web。
Kafka 在 `192.168.23.101`、Flink 在 `192.168.23.102`，都是原生安装，不进编排。

**全部服务用 host 网络**：`.133` 上宿主机与容器之间不通（ARP/端口发布全挂），
只有 host 模式能用，详见 `docker-compose.yml` 末尾的说明。因此：

- 没有 `ports` 映射，端口直接是宿主机的：80 / 8080 / 8081 / 8082 / 3306 / 6379 / 2222
- 没有服务名 DNS，容器间地址一律 `127.0.0.1`

## 目录

```
deploy/
├── docker-compose.yml
├── app/Dockerfile          # 三个 Spring 服务共用一个镜像
├── app/jars/               # 构建时放进去：三个 fat jar（不进 git）
├── sftp/Dockerfile         # alpine + openssh，sshd 监听 2222
├── web/nginx.conf          # 四条反代，顺序与 vite proxy 一致
├── web/dist/               # 前端构建产物（不进 git）
└── mysql/init/             # 首次启动自动执行：建库数据 + 远程账号
```

## 部署步骤

### 1. 构建产物（在开发机）

```bash
cd backend && mvn -q -DskipTests package
cd backend/business && mvn -q -DskipTests package
cd backend/realtime && mvn -q -DskipTests package
cd frontend && npm run build
```

### 2. 导出数据库（在开发机，MySQL 在跑）

```bash
mysqldump -h127.0.0.1 -uroot -p123456 --databases demo01 \
  --single-transaction --routines --triggers --set-gtid-purged=OFF \
  > deploy/mysql/init/01-demo01.sql
```

`02-grant.sql` 里给 `.102` 的 Flink 作业建远程账号，**不要删**。

### 3. 上传到 `.133`

```bash
ssh root@192.168.23.133 'mkdir -p /opt/demo01/{app/jars,web/dist,mysql/init,sftp}'
scp deploy/docker-compose.yml root@192.168.23.133:/opt/demo01/
scp deploy/app/Dockerfile    root@192.168.23.133:/opt/demo01/app/
scp deploy/app/jars/*.jar    root@192.168.23.133:/opt/demo01/app/jars/
scp deploy/sftp/Dockerfile   root@192.168.23.133:/opt/demo01/sftp/
scp deploy/web/nginx.conf    root@192.168.23.133:/opt/demo01/web/
scp -r deploy/web/dist/*     root@192.168.23.133:/opt/demo01/web/dist/
scp deploy/mysql/init/*.sql  root@192.168.23.133:/opt/demo01/mysql/init/
```

`web/dist` 有子目录时 `scp -r` 会报 canonicalization 失败，改用打包：

```bash
tar czf - -C deploy/web/dist . | ssh root@192.168.23.133 'tar xzf - -C /opt/demo01/web/dist'
```

### 4. 启动

```bash
cd /opt/demo01
docker compose up -d --build
docker compose ps
```

首次启动会执行 `mysql/init/*.sql`；卷已存在时**不会**重跑，
要重灌数据得先 `docker compose down -v`（会清空所有数据卷）。

### 5. 验证

从开发机（不是 `.133` 上）：

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://192.168.23.133/          # 200
curl -s http://192.168.23.133/api/screen/pipeline/summary | head -c 200   # 离线条数
curl -s -X POST http://192.168.23.133/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"login_name":"13800001234","password":"123456"}'                   # token
```

SFTP 通道（`.101` 造数脚本上传用）：

```bash
sftp -P 2222 demo01@192.168.23.133    # 口令 demo01_sftp，落到 /home/demo01/upload
```

## 与 Kafka / Flink 的关系

- `business` 通过 `DEMO01_KAFKA_BOOTSTRAP=192.168.23.101:9092` 投埋点
- Flink 作业用 `--host 192.168.23.133 --user demo01 --password 123456` 连容器 MySQL
  （`3306` 在 host 网络下直接开在 `.133` 上）
- 容器内 DNS 与外网不通是已知现象，构建期靠 `build.network: host` 绕开
