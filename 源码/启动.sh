#!/bin/bash
# ============================================================
# 《管家婆》一键启动脚本（验收演示用）
# 启动 MySQL（若未启动）+ 后端 jar，前端页面由 jar 内置提供
# 浏览器打开 http://localhost:8080 即可
# ============================================================
cd "$(dirname "$0")"

if [ -f "$HOME/tools/env.sh" ]; then
  source "$HOME/tools/env.sh"
fi

JAR=server/target/gjp-server-1.0.0.jar
if [ ! -f "$JAR" ]; then
  echo "找不到 $JAR，请先执行 ./打包.sh"
  exit 1
fi

# ---- MySQL ----
if ! mysqladmin ping >/dev/null 2>&1; then
  echo "==> 启动 MySQL"
  nohup mysqld_safe --datadir=/usr/local/var/mysql > /tmp/gjp-mysql.log 2>&1 &
  for i in $(seq 1 30); do
    mysqladmin ping >/dev/null 2>&1 && break
    sleep 1
  done
fi
mysqladmin ping >/dev/null 2>&1 && echo "MySQL 已就绪" || { echo "MySQL 启动失败，见 /tmp/gjp-mysql.log"; exit 1; }

# ---- 首次运行自动建库灌数据 ----
if ! mysql -u root -e "USE gjp; SELECT 1 FROM t_record LIMIT 1;" >/dev/null 2>&1; then
  echo "==> 首次运行：建库建表 + 导入演示数据"
  mysql -u root < server/src/main/resources/db/schema.sql
  mysql -u root < server/src/main/resources/db/data.sql
fi
echo "流水笔数：$(mysql -u root -N -e 'USE gjp; SELECT COUNT(*) FROM t_record;')"

# ---- 后端 ----
if lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "8080 已被占用，先执行 ./停止.sh"
  exit 1
fi
echo "==> 启动后端"
nohup java -jar "$JAR" > /tmp/gjp-server.log 2>&1 &
for i in $(seq 1 60); do
  grep -q "Started GjpApplication" /tmp/gjp-server.log 2>/dev/null && break
  grep -q "APPLICATION FAILED" /tmp/gjp-server.log 2>/dev/null && { echo "启动失败，见 /tmp/gjp-server.log"; exit 1; }
  sleep 1
done

echo
echo "============================================"
echo " 启动完成"
echo " 访问地址：http://localhost:8080"
echo " 演示账号：zhangwei / 123456"
echo " 日志：/tmp/gjp-server.log"
echo "============================================"
command -v open >/dev/null && open http://localhost:8080
