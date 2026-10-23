#!/usr/bin/env bash
# deploy/verify.sh —— 部署后 / 演示前的一次性全链自检
#
# 在开发机 Windows 的 Git Bash 里跑，**不需要登录任何虚拟机**：
#   bash deploy/verify.sh            # 默认只读，不改任何数据
#   bash deploy/verify.sh --track    # 额外走一遍「埋点 → Kafka → Flink → MySQL」
#
# 选项：
#   --host  <ip>   应用机，默认 192.168.23.133
#   --kafka <ip>   Kafka，  默认 192.168.23.101
#   --flink <ip>   Flink，  默认 192.168.23.102
#   --track        发一条埋点并等 today_pv +1（会往库里写一行，默认不做）
#
# 退出码：0 = 全通过（SKIP 不算失败），1 = 至少一项 FAIL。
#
# 覆盖范围：清单 3.4 / 3.5 / 3.7 / 3.8 的「从外部可见」部分。MySQL 行数与
# Redis 键值这类必须在容器内 `docker exec` 才能看到的检查不在这里——那些仍然
# 归 `docker compose ps` 与清单的手工步骤管。

set -u

HOST=192.168.23.133
KAFKA=192.168.23.101
FLINK=192.168.23.102
DO_TRACK=0

while [ $# -gt 0 ]; do
    case "$1" in
        --host)  HOST=$2;  shift 2 ;;
        --kafka) KAFKA=$2; shift 2 ;;
        --flink) FLINK=$2; shift 2 ;;
        --track) DO_TRACK=1; shift ;;
        -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
        *) echo "未知选项：$1（--help 看用法）" >&2; exit 2 ;;
    esac
done

PASS=0; FAIL=0; SKIP=0
FAILED=()

ok()   { PASS=$((PASS+1)); printf '  \033[32m[ OK ]\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); FAILED+=("$1"); printf '  \033[31m[FAIL]\033[0m %s\n' "$1"; }
skip() { SKIP=$((SKIP+1)); printf '  \033[33m[SKIP]\033[0m %s\n' "$1"; }
title(){ printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

# ---------------------------------------------------------------- 基础工具
command -v curl >/dev/null 2>&1 || { echo "缺少 curl，无法继续" >&2; exit 2; }

# 端口是否可连。Git Bash 的 bash 支持 /dev/tcp；连不上或超时都算失败。
tcp_open() { timeout 3 bash -c "exec 3<>/dev/tcp/$1/$2" >/dev/null 2>&1; }

tcp_check() { # host port 说明
    if tcp_open "$1" "$2"; then ok "$3（$1:$2）"; else bad "$3（$1:$2 连不上）"; fi
}

# 取 HTTP 状态码
http_code() { curl -s -o /dev/null -m 5 -w '%{http_code}' "$@" 2>/dev/null; }

# 取响应体里第一个 "code":N —— 三个服务都返回 Result<T>，200 为成功
json_code() { printf '%s' "$1" | sed -n 's/.*"code":\([0-9]*\).*/\1/p' | head -1; }

# 取字符串字段 "key":"value"
json_str() { printf '%s' "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p" | head -1; }

# 取数字字段 "key":N
json_num() { printf '%s' "$1" | sed -n "s/.*\"$2\":\([0-9][0-9]*\).*/\1/p" | head -1; }

# 取带小数的数字字段（入库率是 92.8 这种，json_num 只能取到 92）
json_dec() { printf '%s' "$1" | sed -n "s/.*\"$2\":\([0-9][0-9.]*\).*/\1/p" | head -1; }

# 报错文案用：空响应显示成「无响应」，否则报错行看起来像漏了字
show_code()   { local c; c=$(json_code "$1"); printf '%s' "${c:-无响应}"; }
show_error()  { local m; m=$(json_str "$1" message); printf '%s' "${m:-无响应}"; }

# 从 /api/realtime/snapshot 里取某个指标的值。
# 不用 sed 直接扫全文：五个指标对象挨在一起，靠 "metric_code" 定位更稳，
# 也不依赖字段顺序（metric_code / metric_value / stat_time 只是当前声明顺序）。
metric_value() { # 响应体 metric_code
    printf '%s' "$1" | tr '}' '\n' \
        | grep -F "\"metric_code\":\"$2\"" \
        | sed -n 's/.*"metric_value":\([0-9]*\).*/\1/p' | head -1
}

# ---------------------------------------------------------------- 1. 端口
title "1. 端口与进程存活"

tcp_check "$HOST" 80   "nginx（前端）"
tcp_check "$HOST" 8082 "business"
tcp_check "$HOST" 8081 "realtime"
tcp_check "$HOST" 8080 "analysis"
tcp_check "$HOST" 3306 "MySQL"
tcp_check "$HOST" 6379 "Redis"
tcp_check "$HOST" 2222 "SFTP"
tcp_check "$KAFKA" 9092 "Kafka"
tcp_check "$FLINK" 8081 "Flink 集群"

# ---------------------------------------------------------------- 2. 前端
title "2. 前端与静态资源"

code=$(http_code "http://$HOST/")
[ "$code" = "200" ] && ok "首页 200" || bad "首页返回 $code（期望 200）"

code=$(http_code "http://$HOST/screen")
[ "$code" = "200" ] && ok "/screen 子路由回落 200" || bad "/screen 返回 $code（期望 200）"

# ---------------------------------------------------------------- 3. 离线接口
title "3. 离线指标（analysis，8080）"

body=$(curl -s -m 5 "http://$HOST/api/screen/pipeline/summary")
if [ "$(json_code "$body")" = "200" ]; then
    day=$(json_str "$body" date)
    stored=$(json_num "$body" stored)
    rate=$(json_dec "$body" passRate)
    if [ -n "$day" ] && [ "${stored:-0}" -gt 0 ] 2>/dev/null; then
        ok "pipeline/summary 有数据（批次日 $day，入库 $stored，入库率 ${rate:-?}）"
    else
        bad "pipeline/summary 数据为空（批次日 '${day}'，入库 '${stored}'）"
    fi
else
    bad "pipeline/summary 返回 $(show_code "$body")（期望 code 200）"
fi

body=$(curl -s -m 5 "http://$HOST/api/screen/offline-metrics")
if [ "$(json_code "$body")" = "200" ]; then
    n=$(printf '%s' "$body" | grep -o '"metric_code"' | wc -l | tr -d ' ')
    [ "${n:-0}" -gt 0 ] && ok "offline-metrics 返回 $n 项指标" || bad "offline-metrics 返回空数组"
else
    bad "offline-metrics 返回 $(show_code "$body")（期望 code 200）"
fi

body=$(curl -s -m 5 "http://$HOST/api/screen/dead-letters?page=1&size=5")
if [ "$(json_code "$body")" = "200" ]; then
    total=$(json_num "$body" total)
    [ -n "$total" ] && ok "dead-letters 分页正常（共 ${total} 条）" || bad "dead-letters 缺 total 字段"
else
    bad "dead-letters 返回 $(show_code "$body")（期望 code 200）"
fi

# ---------------------------------------------------------------- 4. 登录与白名单
title "4. 登录 / Token 白名单（business，8082）"

login_body=$(curl -s -m 5 -X POST "http://$HOST/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"login_name":"13800001234","password":"123456"}')

TOKEN=$(json_str "$login_body" token)
if [ -z "$TOKEN" ]; then
    bad "登录失败：$(show_error "$login_body")"
else
    ok "登录成功，拿到 token"
    name=$(json_str "$login_body" login_name)
    case "$name" in
        *'****'*) ok "账号已脱敏（$name）" ;;
        *)        bad "账号未脱敏（$name）——脱敏应在后端 VO 转换里做" ;;
    esac

    code=$(http_code -H "Authorization: Bearer $TOKEN" "http://$HOST/api/account")
    [ "$code" = "200" ] && ok "带 token 访问 /api/account 200" || bad "/api/account 返回 $code（期望 200）"

    # 登出后旧 token 立刻失效 —— 这是「白名单生效」的唯一外部证据。
    # 内存版与 Redis 版都会过这一条，区别只在重启后是否仍然失效。
    curl -s -m 5 -o /dev/null -X POST -H "Authorization: Bearer $TOKEN" \
        "http://$HOST/api/auth/logout"

    code=$(http_code -H "Authorization: Bearer $TOKEN" "http://$HOST/api/account")
    [ "$code" = "401" ] && ok "登出后旧 token 返回 401（白名单已生效）" \
                        || bad "登出后旧 token 返回 $code（期望 401，白名单可能没生效）"
fi

# ---------------------------------------------------------------- 5. 实时链路
title "5. 实时链路（Flink $FLINK + realtime 8081）"

overview=$(curl -s -m 5 "http://$FLINK:8081/jobs/overview")
if [ -z "$overview" ]; then
    bad "Flink REST 无响应，作业状态未知"
else
    running=$(printf '%s' "$overview" | grep -o '"state":"RUNNING"' | wc -l | tr -d ' ')
    jid=$(printf '%s' "$overview" | sed -n 's/.*"jid":"\([0-9a-f]*\)".*/\1/p' | head -1)
    if [ "${running:-0}" -ge 1 ]; then
        ok "Flink 有 $running 个作业 RUNNING（$jid）"
    else
        bad "Flink 没有 RUNNING 的作业——大屏实时数字会一直不动"
    fi
    # 同 group 两个消费者会让指标「少一半」，这条专门咬那个坑
    [ "${running:-0}" -le 1 ] || bad "同时有 $running 个作业在跑，同一 Kafka group 应只有一个"
fi

snap=$(curl -s -m 5 "http://$HOST/api/realtime/snapshot")
if [ "$(json_code "$snap")" = "200" ]; then
    n=$(printf '%s' "$snap" | grep -o '"metric_code"' | wc -l | tr -d ' ')
    [ "${n:-0}" -eq 5 ] && ok "snapshot 返回五个指标" || bad "snapshot 返回 $n 个指标（期望 5）"
else
    bad "snapshot 返回 $(show_code "$snap")（期望 code 200）"
fi

sse=$(curl -s -m 3 -H 'Accept: text/event-stream' "http://$HOST/api/realtime/metrics")
lines=$(printf '%s' "$sse" | grep -c '^data:')
[ "${lines:-0}" -ge 1 ] && ok "SSE 3 秒内收到 $lines 条推送" \
                        || bad "SSE 3 秒内没有收到任何 data: 行（大屏会空白且不报错）"

# ---------------------------------------------------------------- 6. 埋点链路（可选）
title "6. 埋点链路（--track 才跑）"

if [ "$DO_TRACK" -eq 0 ]; then
    skip "未加 --track，跳过「埋点 → Kafka → Flink → MySQL」（该检查会往库里写一行）"
else
    pv_before=$(metric_value "$(curl -s -m 5 "http://$HOST/api/realtime/snapshot")" today_pv)
    if [ -z "$pv_before" ]; then
        bad "取不到 today_pv，无法比对"
    else
        # UUID 必须每次不同，否则重跑会撞 uk_event 被判 DUPLICATE，pv 不涨
        ev="00000000-0000-4000-8000-$(printf '%06d%06d' "$RANDOM" "$RANDOM")"
        # event_time 往前推 15 秒：判脏规则 4 拿 .102 的时钟比「未来时间」，
        # 本机与 .102 有几秒时钟差时，发「此刻」会被判 TIME_ILLEGAL 而全链误判失败。
        # 这与 realtime_feed.py 的 --skew 是同一个兜底。
        ets=$(date -u -d '-15 seconds' +%Y-%m-%dT%H:%M:%S.000Z)
        curl -s -m 5 -o /dev/null -X POST "http://$HOST/api/track" \
            -H 'Content-Type: application/json' \
            -d "{\"event_id\":\"$ev\",\"event_code\":\"view_home\",\"event_time\":\"$ets\",\"session_id\":\"55555555-6666-7777-8888-999999999999\",\"page\":\"/\",\"region\":\"shanghai\",\"device\":\"PC\",\"user_id\":null}"

        printf '       已发送，等 8 秒让 Kafka → Flink → MySQL 走完…\n'
        sleep 8
        pv_after=$(metric_value "$(curl -s -m 5 "http://$HOST/api/realtime/snapshot")" today_pv)
        if [ -n "$pv_after" ] && [ "$pv_after" -gt "$pv_before" ] 2>/dev/null; then
            ok "today_pv $pv_before → $pv_after，全链贯通"
        else
            bad "today_pv 未增长（$pv_before → ${pv_after:-?}）——埋点接口回 200 不代表链路通，见开启步骤 5.5"
        fi
    fi
fi

# ---------------------------------------------------------------- 汇总
printf '\n\033[1m== 汇总 ==\033[0m\n'
printf '  OK %d / FAIL %d / SKIP %d\n' "$PASS" "$FAIL" "$SKIP"
if [ "$FAIL" -gt 0 ]; then
    printf '\n未通过项：\n'
    for f in "${FAILED[@]}"; do printf '  - %s\n' "$f"; done
    printf '\n对照排查见 init/开启步骤.md 第 9 节「故障对照」。\n'
    exit 1
fi
printf '全部通过。\n'
exit 0
