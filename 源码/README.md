# 管家婆 — 家庭收支管理系统

《软件开发实践2》课程实践作品。前后端分离，以家庭为整体、成员为最小数据单位，
完成家庭收入与支出的录入、编辑、查询、统计与分析，并拓展了资产与贷款管理。

## 一、目录结构

```
软件开发/
├── server/                     后端（Java 17 + Spring Boot 3 + MyBatis）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/gjp/
│       │   ├── GjpApplication.java     启动类
│       │   ├── common/                 统一返回体、全局异常、登录拦截、JSON 配置
│       │   ├── entity/                 7 张表对应的实体
│       │   ├── mapper/                 数据访问层（MyBatis 注解式）
│       │   ├── auth/                   登录注册（甲）
│       │   ├── member/                 家庭成员（乙）
│       │   ├── category/               收支分类（乙）
│       │   ├── record/                 收支流水（丙）★核心
│       │   ├── stat/                   统计（丁）
│       │   ├── analysis/               智能分析（丁）★评分点
│       │   └── asset/                  资产与贷款（戊，拓展）
│       └── resources/
│           ├── application.yml
│           └── db/
│               ├── schema.sql          建库建表脚本
│               ├── gen_demo_data.py    演示数据生成脚本
│               └── data.sql            演示数据（由上面脚本生成，962 笔流水）
├── web/                        前端（Vue 3 + Element Plus + ECharts + Vite）
│   └── src/
│       ├── api/                接口封装，一个模块一个文件
│       ├── components/         EChart 图表包装、指标卡
│       ├── layout/             侧边栏 + 顶栏骨架
│       ├── router/             路由与登录守卫
│       ├── utils/              axios 实例、金额/日期格式化
│       └── views/              各功能页面
└── docs/                       项目文档
```

## 二、环境要求

| 组件  | 版本      | 说明                          |
| ----- | --------- | ----------------------------- |
| JDK   | 17        | Temurin / OpenJDK 均可        |
| Maven | 3.9+      | 用于编译和启动后端            |
| MySQL | 8.0 及以上 | 需支持 utf8mb4                |
| Node  | 18+       | 用于前端开发服务器和打包      |

本机已装好的路径（macOS，供组员参考）：

```bash
export JAVA_HOME="$HOME/tools/jdk-17.0.20.1+1/Contents/Home"
export PATH="$JAVA_HOME/bin:$HOME/tools/apache-maven-3.9.16/bin:/usr/local/opt/mysql/bin:$PATH"
```

上面三行也写在 `~/tools/env.sh`，每次开新终端执行 `source ~/tools/env.sh` 即可。

## 三、启动步骤

### 1. 数据库

```bash
# 启动 MySQL（Homebrew 安装的写法）
mysqld_safe --datadir=/usr/local/var/mysql &

# 建库建表 + 灌演示数据（两个脚本都自带 SET NAMES utf8mb4，直接执行不会乱码）
mysql -u root < server/src/main/resources/db/schema.sql
mysql -u root < server/src/main/resources/db/data.sql
```

数据库连接信息在 `server/src/main/resources/application.yml`，默认 `root` 空密码。
本机 root 有密码的组员改这一处即可。

### 2. 后端

```bash
cd server
mvn spring-boot:run          # 监听 8080，接口前缀 /api
```

### 3. 前端

```bash
cd web
npm install                  # 首次执行
npm run dev                  # 监听 5173
```

浏览器打开 <http://localhost:5173>。

> npm 11 默认不跑依赖的安装脚本，`vite` 依赖的 `esbuild` 需要它来下载二进制。
> `package.json` 里的 `allowScripts` 字段已经放开这一项，正常 `npm install` 即可；
> 若仍提示 install-scripts，执行一次 `npm install-scripts approve esbuild`。

### 4. 演示账号

| 账号      | 密码   | 家庭 | 数据量                                     |
| --------- | ------ | ---- | ------------------------------------------ |
| zhangwei  | 123456 | 张家 | 4 名成员、52 项分类、962 笔流水（20 个月） |

也可以在登录页「注册新家庭」自行注册，系统会自动为新家庭初始化 12 类、
40 余项常用收支分类，注册人自动成为第一位家庭成员。

## 四、演示数据说明

`data.sql` 由 `gen_demo_data.py` 生成，固定随机种子，因此每次生成结果完全一致，
测试用例可以把预期值写死。造数时刻意做了两件事：

1. **按真实家庭的消费规律造数** —— 工资月发、年终奖只在 1 月、暑假与国庆才有旅游支出、
   人情往来集中在春节与婚庆季、居住地「城东」占比最高。这样统计图表讲出来的是一个
   合理可解释的故事，而不是一堆随机数。
2. **埋了一个异常月份（2026-05）** —— 卫生间厨房翻新 5.1 万 + 五一云南全家游 1.68 万，
   当月支出 9.4 万，是其他月份平均值的 4.6 倍。用来验证智能分析能否正确定位异常月份、
   把超支归因到「居住支出」，并判断这是偶发性而非持续性支出。

需要重新生成：

```bash
cd server/src/main/resources/db
python3 gen_demo_data.py
mysql -u root < data.sql
```

## 五、功能与分工

| 模块                | 责任人 | 主要内容                                                     |
| ------------------- | ------ | ------------------------------------------------------------ |
| 架构、登录、集成    | 甲     | 工程骨架、统一返回体、全局异常、登录拦截、库表脚本            |
| 成员管理、分类管理  | 乙     | 成员增删改查与月度预算；两级分类、预置与自定义                |
| 收支流水            | 丙     | 录入/编辑/删除、多条件分页查询、商家与片区等多维字段          |
| 统计、智能分析      | 丁     | 汇总/占比/趋势/排行；8 条分析规则                             |
| 资产贷款、文档测试  | 戊     | 资产与贷款管理、净资产测算；造数脚本、文档汇编                |

## 六、设计上值得说明的几点

- **统计与分析严格分开**。`StatService` 只做客观汇总，不下任何结论；
  `AnalysisService` 负责判断，每条结论都输出「结论 + 数据依据 + 处理建议」三段，
  阈值集中定义为常量，评审时可以直接讨论阈值是否合理。
- **数据按家庭隔离**。`familyId` 从服务端 session 取（`UserContext`），
  不接受前端传参，所有 SQL 都带 `family_id` 条件。
- **录入层严格校验**。分类的收入/支出类型必须与流水类型一致、金额上限、日期不得超过今天等，
  统计结果的准确性完全依赖这一层，因此校验放在 Service 而不是只靠前端。
- **多维字段不是凑数**。`merchant` / `area` / `is_gift` 三个字段支撑了商家排行、
  片区分布、人情往来专项分析，对应课程要求「录入时应考虑多种因素」。
