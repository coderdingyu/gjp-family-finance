#!/bin/bash
# 停止《管家婆》后端（MySQL 保持运行，如需停止：mysqladmin -u root shutdown）
pkill -f "gjp-server-1.0.0.jar" && echo "后端已停止" || echo "后端未在运行"
pkill -f "vite" 2>/dev/null && echo "前端开发服务器已停止"
exit 0
