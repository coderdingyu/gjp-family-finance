# 管家婆 — 家庭收支管理系统

家庭记账 Web 系统：Spring Boot + Vue 3，含演示数据和一键启动脚本。

完整部署与离线环境说明见 [`部署说明.md`](./部署说明.md)。源码说明见 [`源码/README.md`](./源码/README.md)。

文件导入、Dify 工作流搭建、云主机更新见：

- [`运行包/dify/导入说明.md`](./运行包/dify/导入说明.md)
- [`运行包/dify/云主机迁移.md`](./运行包/dify/云主机迁移.md)
- 可导入的工作流：[`运行包/dify/管家婆账单导入.yml`](./运行包/dify/管家婆账单导入.yml)

## 最快演示

需要本机已安装并启动 MySQL 8.0+。

```bash
cd 环境 && ./安装环境.sh && source ~/tools/env.sh && cd ..
mysql -u root < 运行包/schema.sql
mysql -u root < 运行包/data.sql
java -jar 运行包/gjp-server-1.0.0.jar
```

浏览器打开 http://localhost:8080 。密码统一 `123456`：

| 账号 | 角色 | 说明 |
| ---- | ---- | ---- |
| `zhangwei` | 户主 | 看全家、可管理成员/分类/资产 |
| `lijuan` | 普通成员 | 只能看自己的账单 |
| `admin` | 系统管理员 | 网站维护与跨家庭日志，看不到账单金额 |
