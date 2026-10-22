# SFTP 上传通道。
#
# 为什么不用 atmoz/sftp：Docker Hub 直连不通，而本机配的三个镜像站
# 只代理 library/ 下的官方镜像，atmoz/ 这个第三方命名空间拉不动
# （2026-10-22 实测，pull 挂起十几分钟无进展）。
# 自建的成本很低：openssh 在 alpine 的 main 源里就有。
#
# ⚠️ 构建必须用宿主机网络（compose 里已写 build.network: host）：
#    容器默认 DNS 在本机不通，apk 拉不到索引。

FROM alpine:3.20

# 只留 main 源：community 索引在镜像站上反复超时，而 sshd 在 main 里。
RUN echo "https://mirrors.aliyun.com/alpine/v3.20/main" > /etc/apk/repositories \
 && apk add --no-cache openssh

# 演示账号（弱口令，仅内网演示用）：demo01 / demo01_sftp
RUN adduser -D -s /bin/sh demo01 \
 && echo "demo01:demo01_sftp" | chpasswd \
 && mkdir -p /home/demo01/upload \
 && chown -R demo01:demo01 /home/demo01

# 允许口令登录（演示用；生产应改为密钥）
# 监听 2222：容器用 host 网络，宿主机 22 被系统 sshd 占着
RUN echo "PasswordAuthentication yes" >> /etc/ssh/sshd_config \
 && echo "Port 2222" >> /etc/ssh/sshd_config \
 && ssh-keygen -A

EXPOSE 2222
CMD ["/usr/sbin/sshd", "-D", "-e"]
