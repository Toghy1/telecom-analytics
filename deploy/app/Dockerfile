# 三个 Spring Boot 服务共用同一个镜像：jar 由 compose 的 command 指定。
#
# 为什么共用：三个服务的运行时完全一样（JRE 17 + fat jar），
# 分开 build 只会让镜像多存两份 180 MB 的 JRE（vfs 驱动下还要翻倍）。
#
# 构建前置：把三个 jar 放进 ./jars/（见 deploy/README.md）。

FROM eclipse-temurin:17-jre-alpine

# 容器默认 UTC，与本机差 8 小时——会让 LocalDateTime.now()、
# 日志时间、JWT 过期判断全部偏 8 小时，且不报错。
ENV TZ=Asia/Shanghai

WORKDIR /app
COPY jars/ /app/jars/

# 默认命令只用来占位，真实入口由 compose 的 command 覆盖。
CMD ["java", "-version"]
