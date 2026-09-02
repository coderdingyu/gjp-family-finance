#!/bin/bash
# 停止《管家婆》的后端与前端进程
# MySQL 保持运行，如需停止：mysqladmin -u root shutdown
STOPPED=0

# 打包后用 jar 启动的后端
pkill -f "gjp-server-1.0.0.jar" 2>/dev/null && { echo "已停止：后端（jar）"; STOPPED=1; }

# 开发期用 mvn spring-boot:run 启动的后端。
# 漏掉这一条会导致 8080 一直被占着，./启动.sh 提示"端口被占用"却找不到进程。
pkill -f "spring-boot:run" 2>/dev/null && { echo "已停止：后端（mvn spring-boot:run）"; STOPPED=1; }
pkill -f "com.gjp.GjpApplication" 2>/dev/null && { echo "已停止：后端（GjpApplication）"; STOPPED=1; }

# 前端开发服务器
pkill -f "vite" 2>/dev/null && { echo "已停止：前端开发服务器"; STOPPED=1; }

[ $STOPPED -eq 0 ] && echo "没有正在运行的进程"
exit 0
