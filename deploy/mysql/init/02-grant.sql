-- 给 .102 上的 Flink 作业建远程账号。
--
-- 作业跑在 Flink 集群里，不是本机进程，所以连不上 root@localhost；
-- 口令与 Flink 提交参数里的 --user / --password 必须一致。
-- 只授 demo01 一个库，不给全局权限。

CREATE USER IF NOT EXISTS 'demo01'@'192.168.23.%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON demo01.* TO 'demo01'@'192.168.23.%';
FLUSH PRIVILEGES;
