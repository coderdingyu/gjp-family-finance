# 管家婆 — 家庭收支管理系统

家庭记账 Web 系统：Spring Boot + Vue 3，含演示数据和一键启动脚本。

完整部署与离线环境说明见 [`部署说明.md`](./部署说明.md)。源码说明见 [`源码/README.md`](./源码/README.md)。

## 最快演示

需要本机已安装并启动 MySQL 8.0+。

```bash
cd 环境 && ./安装环境.sh && source ~/tools/env.sh && cd ..
mysql -u root < 运行包/schema.sql
mysql -u root < 运行包/data.sql
java -jar 运行包/gjp-server-1.0.0.jar
```

浏览器打开 http://localhost:8080 ，使用 `zhangwei` / `123456` 登录。
