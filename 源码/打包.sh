#!/bin/bash
# ============================================================
# 《管家婆》一键打包脚本
# 依次做三件事：编译前端 -> 前端产物并入后端 -> 打出可执行 jar
# 产物：server/target/gjp-server-1.0.0.jar（单个 jar 同时提供前端页面与后端接口）
# ============================================================
set -e
cd "$(dirname "$0")"

# 工具链环境（JDK / Maven 装在 ~/tools，不在系统 PATH 里）
if [ -f "$HOME/tools/env.sh" ]; then
  source "$HOME/tools/env.sh"
fi

echo "==> 1/3 检查工具链"
command -v java >/dev/null || { echo "找不到 java，请先 source ~/tools/env.sh"; exit 1; }
command -v mvn  >/dev/null || { echo "找不到 mvn，请先 source ~/tools/env.sh"; exit 1; }
command -v node >/dev/null || { echo "找不到 node"; exit 1; }
java -version 2>&1 | head -1
mvn -v | head -1
echo "node $(node -v)"

echo "==> 2/3 编译前端（web/dist）"
cd web
[ -d node_modules ] || npm install
npm run build
cd ..

echo "==> 3/3 打包后端（会自动把 web/dist 复制进 jar 的 static 目录）"
cd server
mvn -q -B clean package -DskipTests
cd ..

echo
echo "打包完成：server/target/gjp-server-1.0.0.jar"
echo "启动：./启动.sh    然后浏览器打开 http://localhost:8080"
