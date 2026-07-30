# 理财投资决策系统 v1.0 — 完整项目手册（含开发会话全记录）

> 个人理财投资决策系统：股票/基金智能筛选 + AI 深度分析（价值投资框架）+ 五阶决策思维 + 个人财务管理 + 云部署。
> 本 README 在原有技术文档基础上，整合了**全部开发会话记录、云端部署流程、AI 提示词模板与排错速记**，便于换机/换人接手后直接复跑。

---

## 目录

- [1. 本地项目位置](#1-本地项目位置)
- [2. 功能概览](#2-功能概览)
- [3. 技术栈](#3-技术栈)
- [4. 项目结构](#4-项目结构)
- [5. 新电脑从零搭建（本地运行）](#5-新电脑从零搭建本地运行)
- [6. 三服务常驻启动顺序](#6-三服务常驻启动顺序)
- [7. 开发会话全记录（时间线）](#7-开发会话全记录时间线)
- [8. 核心 AI 提示词模板（价值投资者）](#8-核心-ai-提示词模板价值投资者)
- [9. 关键约定与安全提醒](#9-关键约定与安全提醒)
- [10. 云端部署（腾讯云 <CLOUD_PUBLIC_IP>）](#10-云端部署腾讯云-10652219144)
- [11. 排错速记 / 踩坑大全](#11-排错速记--踩坑大全)
- [12. API 文档](#12-api-文档)
- [13. 数据库表结构](#13-数据库表结构)

---

## 1. 本地项目位置

当前项目位于本机：

- **Windows 绝对路径**：`C:\Users\Administrator\WorkBuddy\2026-07-26-20-42-56\finance-system`
- **Git Bash 路径**：`/c/Users/Administrator/WorkBuddy/2026-07-26-20-42-56/finance-system`
- **Git 远程**：`git@github.com:PatrickMai-Bo/finance-system.git`（SSH 协议，分支 `master`）
- **微信小程序支线**：`wechat-miniprogram-backup` 分支（与理财主线分叉，已备份，勿合并回 master）

> 换电脑时，直接 `git clone git@github.com:PatrickMai-Bo/finance-system.git` 即可拿到全部源码（注意 `.git` 与 `data/llm-configs.json` 见下方安全提醒）。

---

## 2. 功能概览

| 板块 | 功能 | 说明 |
|---|---|---|
| 首页 | 总览 + 在线人数 | 净资产、被动收入覆盖率、基金/股票 TOP3、AI 状态；右上「在线 N 人」徽标；在线>0 自动后台预热板块数据 |
| 个人财务 | 记账 + 资产负债表 | 富爸爸自动分类（资产/负债）、被动收入追踪、财务自由度评估、家底 CRUD |
| 基金筛选 | 智能排行 + AI 深度分析 | PE 估值优先、护城河量化、真实数据采集、LLM 精排评分 + 持有建议（短/中/长期） |
| 股票筛选 | 格雷厄姆估值 + 安全边际 | V=EPS×(8.5+2g)、PE 横截面分位、综合评分前 30 |
| 决策思维 | 五阶避错框架 | 逆向排雷→价值定性→能力圈→替代方案→情绪冷却，自动接入个人财务数据 |
| AI 设置 | 多模型管理 | DeepSeek / 阿里百炼 / Kimi / 智谱，OpenAI 兼容接口统一管理 |
| 手机版 | `/m/` 独立应用 | 手机 UA 自动跳转，Vant 移动端 UI，含全部模块 |

### AI 特色

- **两阶段筛选 + 深度分析**：先定量筛选（格雷厄姆/护城河），再调 LLM 按价值投资模板做 5 段深度分析，结果入缓存，点「详细分析」秒出。
- **精排评分**：LLM 输出 `refinedScore` / `refinedRating` JSON，前端按评分降序重排，推荐列显示评级标签。
- **持有建议**：精排时 LLM 一次输出短期/中期/长期持有时间 + 预计收益率，直接写入 `row.advice`，前端末列展示。
- **批量对比**：勾选多只后一键横向对比（护城河/估值/风险/配置建议）。
- **决策分析**：自动注入用户真实财务数据（净资产/覆盖率/被动收入），AI 给出量化建议。
- **历史警示**：记录决策历史，再次问类似问题时顶部红色大字警告。

---

## 3. 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 前端(桌面) | Vue 3 + Vite + Element Plus | Vue 3.x |
| 前端(手机) | Vue 3 + Vite + Vant 4 | Vue 3.x |
| 后端 | Spring Boot 3 + Java 17 | Spring Boot 3.x |
| 数据采集 | Python FastAPI | Python 3.13 |
| 数据库 | MySQL | 8.0 |
| ORM | MyBatis-Plus | 3.5.7 |
| AI | OpenAI 兼容接口 | DeepSeek/百炼/Kimi/智谱 |
| 部署 | Docker Compose | MySQL + Backend + Collector + Nginx |

---

## 4. 项目结构

```
finance-system/
├── frontend/                 # Vue3 桌面前端（Vite + Element Plus）
│   ├── src/
│   │   ├── api/index.js      # 所有 API 方法（含 systemApi）
│   │   ├── components/       # AiAnalyze / MarkdownView / WatchlistPanel 等
│   │   ├── layout/           # MainLayout 导航栏
│   │   ├── stores/           # Pinia 状态（auth.js）
│   │   ├── views/            # Home / Finance / FundScreen / StockScreen / Decision / AiPanel / Login
│   │   └── router/index.js   # 路由定义
│   └── vite.config.js        # 含 /api → :8090 代理；build.outDir 指向 ../dist
├── frontend-mobile/          # Vue3 手机前端（Vite + Vant4），base=/m/
├── backend/                  # Spring Boot 3 + Java 17
│   ├── src/main/java/com/finance/
│   │   ├── controller/       # System/Auth/Screen/Decision/Llm/... Controller
│   │   ├── service/          # DeepAnalysisService(核心)/AdviceService/RealScreenService/...
│   │   ├── entity/           # Ledger / Holding / Watchlist / DecisionLog
│   │   ├── mapper/           # MyBatis-Plus Mapper
│   │   ├── config/           # JWT / CORS
│   │   └── common/           # R（统一响应）/ PageResult / GlobalExceptionHandler
│   └── src/main/resources/application.yml   # 读 ${DB_URL}/${DB_USERNAME}/${DB_PASSWORD}
├── collector/                # Python FastAPI 数据采集服务
│   └── collector.py          # 东财业绩报表 + 腾讯行情 qt.gtimg.cn + 天天基金排行
├── data/                     # LLM 配置文件（含 API Key，已 gitignore，勿提交）
│   └── llm-configs.json
├── dist/                     # 桌面前端构建产物（nginx 挂载根，非 frontend/dist）
├── dist-mobile/              # 手机前端构建产物（nginx 挂载 /m/）
├── Dockerfile                # 后端镜像（JRE 17，COPY jar + data/）
├── Dockerfile.collector      # 采集服务镜像（Python）
├── docker-compose.yml        # 一键部署编排（mysql+backend+collector+nginx）
├── nginx.conf                # Nginx 反向代理 + UA 设备识别跳转 /m/
├── init.sql                  # MySQL 建表语句
└── mvn17.sh                  # Maven 包装脚本（规避 Git Bash 路径转换 bug）
```

---

## 5. 新电脑从零搭建（本地运行）

### 5.1 环境准备

- Java 17+（JDK17，本地在 `D:\java\JDK17`）
- Node 22+（前端 dev/build）
- Python 3.12+（采集服务，需 `requests`/`fastapi`/`uvicorn`）
- MySQL 8.0（本地 `service MySQL80`，端口 3306，默认 `root/root`）

### 5.2 获取源码

```bash
git clone git@github.com:PatrickMai-Bo/finance-system.git
cd finance-system
```

### 5.3 初始化数据库

```bash
# 确保 MySQL 已启动，执行建库建表
mysql -uroot -proot < init.sql
# init.sql 会 CREATE DATABASE finance_system 并建 4 张表
```

### 5.4 配置 AI API Key

`data/llm-configs.json` 含模型配置与 Key。**换电脑后需重新填 Key**（见第 9 节）。也可启动后在「AI 设置」页面手动填，会自动回写此文件。
默认预设 4 个模型：DeepSeek(默认激活) + 阿里百炼 qwen-plus(enableSearch) + Kimi + 智谱。

### 5.5 构建后端

```bash
# 用 mvn17.sh 规避 Git Bash 下 mvn 路径转换 bug
bash mvn17.sh clean package -DskipTests
# 产物：backend/target/finance-system-backend.jar (~29MB)
```

### 5.6 构建前端

```bash
cd frontend && npm install && npm run build && cd ..
cd frontend-mobile && npm install && npm run build && cd ..
# 桌面产物 → frontend/dist，需发到项目根 dist/（nginx 挂载根 dist）
# 手机产物 → frontend-mobile/dist，需发到 dist-mobile/
cp -r frontend/dist/. dist/
cp -r frontend-mobile/dist/. dist-mobile/
```

> ⚠️ 若 `npm run build` 触发 WorkBuddy「安全删除」shim 清空 dist 失败，改用
> `cd frontend && node node_modules/vite/bin/vite.js build` 或 `npx vite build --outDir dist-deploy` 后拷贝。

### 5.7 启动三服务

见第 6 节。默认账号 `admin` / `<APP_PASSWORD>`，绑定手机 `<BOUND_PHONE>`。

---

## 6. 三服务常驻启动顺序

| 服务 | 端口 | 启动命令 | 说明 |
|---|---|---|---|
| 采集服务 | 8091 | `python collector/collector.py` | 直连三数据源（`trust_env=False` 绕代理） |
| 后端 | 8090 | `java -jar backend/target/finance-system-backend.jar --server.port=8090` | **cwd 须为 finance-system/ 根目录**，否则 `data/` 路径错位 |
| 前端(dev) | 5173 | `cd frontend && node node_modules/vite/bin/vite.js --host 0.0.0.0 --port 5173` | 代理 `/api` → `localhost:8090` |

> 后端端口必须用 `--server.port=8090` 覆盖（系统环境变量 `SERVER__PORT=54913` 会被 Spring relaxed binding 抢占）。
> 浏览器打开 `http://localhost:5173`。手机模拟访问 UA 含 `mobile` 可测 `/m/` 跳转。

---

## 7. 开发会话全记录（时间线）

> 以下按日期记录每轮需求、改动、验证结果。换机接手时重点看「下一步」与「排错要点」。

### 2026-07-26（后端骨架 + 前端定稿）

- **步骤①后端骨架**：SpringBoot3 + JWT + 全局 AI 中枢（`AiService`/`LlmConfigService`，scene 区分板块），mock 数据服务。
- **步骤②前端定稿**：5 大板块（个人财务/基金筛选/股票筛选/决策思维/AI设置）+ 首页 + 跨板块 `AiAnalyze` 组件。
- **前端三项优化**：① 随手记式记账 + 富爸爸自动分类；② 基金/股票默认前 30 + 每页 10 分页；③ 决策系统融入「五阶避错思维框架」（逆向排雷/价值定性/能力圈/替代方案/情绪冷却）。
- **第二轮增强**：① 存量资产负债表「家底」CRUD + AI；② 股票/基金顶部「我的自选」CRUD + AI（可复用 `WatchlistPanel.vue`）；③ 决策顶部全局 AI 咨询入口。
- **环境坑**：后端必须 `--server.port=8090`；vite proxy `/api`→8090；Maven 用 `mvn17.sh`；登录固定 `admin/admin123`（后改为 `<APP_PASSWORD>`）。

### 2026-07-27（真实模型接入 + 真实数据 + 落库）

- **真实大模型接入（步骤③启动）**：用户拍板——联网搜索用百炼 qwen 原生 `enable_search`（DeepSeek 无联网）；华为云部署放最后，先本地迭代；首批接 DeepSeek + 阿里百炼。新增 `LlmClient.java`（Java HttpClient 调 OpenAI 兼容 `/chat/completions`），有 Key 走 `mode=real`，无 Key 回退 `mode=mock`。
- **步骤③主体（真实数据 + 筛选引擎）**：`CollectorClient.java` 调采集服务 8091；`RealScreenService.java` 格雷厄姆 V=EPS×(8.5+2g) + 护城河量化 + PE 横截面分位 + 安全边际 + 综合前 30（30min TTL 缓存，缺失字段置 null 不编造）；`ScreenController` 优先 real 失败回退 mock 并标 `dataSource`。采集服务用三数据源（东财业绩报表 + 腾讯行情 `qt.gtimg.cn` + 天天基金排行），`requests.Session(trust_env=False)` 强制直连绕开注册表代理 10808。前端表头加「真实数据(绿)/演示数据(灰)」标签。
- **🔑 API Key 持久化坑（重要）**：重启后端会丢内存中的 Key。磁盘无 `llm-configs.json` 时需用 `jmap -dump` 抓堆提取明文 Key 后写入 `data/llm-configs.json`，再删堆文件（含明文 Key！）。提取 DashScope Key 坑：完整 Key 形如 `sk-ws-H.EDELXPP...`（带长前缀），grep `sk-PfMmHeGdi` 会失败，需搜 `enableSearch":true` 的 JSON 上下文。
- **步骤④（MySQL 落库）**：pom 加 MyBatis-Plus 3.5.7 + mysql-connector-j；application.yml 数据源支持 `${DB_URL}/${DB_USERNAME}/${DB_PASSWORD}` 环境变量覆盖；建 `entity`/`mapper`；`LedgerService`/`HoldingService`/`WatchlistService` 从内存改 Mapper；派生字段（category/advice/verdict）读取时现算不落库；表空时播种示例数据。库 `finance_system`，三表 `ledger`/`holding`/`watchlist`。验证：CRUD 真落盘，重启不丢。
- **建议持有时间列**：新增 `AdviceService.java`（8 线程并发调模型，返回 `{short/mid/long:{horizon,returnRange,logic}}`，30min 缓存）；前端末列「建议持有时间(AI)」+ 头部「刷新全部(行情+AI建议)」按钮。
- **三处 UI 优化**：① 新增 `MarkdownView.vue` 手写 markdown 渲染（重点加粗红字 `#c0392b`+浅黄底，### 蓝色左边框，✅❌💡⚠️🎯 span，行高 1.85）；② 批量对比（勾选 → `stock-batch`/`fund-batch`）；③ 决策五阶改 AI 自动分析（`Decision.vue` 去自查子问题）。
- **弹框加宽 + 决策接入财务数据**：AI 弹框宽 640px → 80%；清空全部「三书」文字引用；决策历史红色警示放大（`!!!` 行解析，28px/900 红字）；决策五阶分析自动注入 ledger/holding 真实数据。

### 2026-07-28（登录认证 + 手机端 + 真实数据修复）

- **登录认证**：账号 `admin`/`<APP_PASSWORD>`/绑定手机 `<BOUND_PHONE>`；登录页加「忘记密码」（填手机号，仅匹配 `<BOUND_PHONE>` 才改密码）。根因：本地 Vite 没启动（须 `node node_modules/vite/bin/vite.js`）；服务器 nginx bind mount 失效（替换 dist 用 `mv` 原目录→Docker 绑旧 inode），修复 = 删除重建 nginx 容器。
- **独立手机前端 + 设备识别**：新建 `frontend-mobile/`（Vant4），nginx `map $http_user_agent $is_mobile` 检测 → 302 跳 `/m/`；docker-compose 挂载 `./dist-mobile`。覆盖 8 个模块（Login/Home/Finance/Stock/Fund/Decision/Ai/Profile）。
- **手机端 5 项 UI 修复**：① Markdown CSS 不匹配（输出真实 `<h1>/<h2>/<h3>`）；② Decision 日志字段对齐；③ MobileLayout 底部被 tabbar 遮挡；④ AiDialog 不能滚动；⑤ 模型列表缺字段。
- **真实数据修复（Docker 网络隔离）**：容器内 `localhost` 指向 backend 自身而非 collector → 回退 mock。修复：docker-compose backend 加 `COLLECTOR_URL: http://finance-collector:8091`，重建容器。验证：股票 5000 扫 30 过、基金 20000 扫 30 过，均 `source=real`。

### 2026-07-29（两阶段筛选 + LLM 深度分析 + 11 条规则 + 缓存）

- **两阶段筛选 + LLM 深度分析**：新建 `DeepAnalysisService`，对筛选通过的每只调 LLM（默认 DeepSeek），缓存 60min；新增 `POST /api/screen/{stock,fund}/analyze/{code}`；桌面「详细分析」80% dialog，手机全屏 popup。
- **5 条优化规则 + 精排评分**：规模时效 / 持有人结构 / 成长赛道分层止损 / 客观平衡 / 禁止绝对化；LLM 输出 `{"refinedScore":85,"refinedRating":"强烈推荐","confidence":"高"}` 正则提取；新增 `POST /api/screen/{stock,fund}/refined` 8 线程并发精排重排。
- **累计 11 条规则**：+ 止损双轨并行 / 最大回撤用完整熊市极值 / 持仓以最新季报为准 / 长期预期带前提 / 概率仅推演 / 区分从业年限与管理年限。
- **缓存架构修复**：精排结果自动 `cache.put(key, result)`，点「详细分析」命中缓存秒出（精排 30 只 59s → 详细分析 1s）。
- **新分析模板（分支逻辑）**：自动识别 A股个股 / 公募基金；个股=行业定性→护城河→盈利质量→估值→逆向风险；基金=底层赛道→经理分析→估值→费率→逆向风险；统一收尾 3 类结论（持续观望/极小仓位试错/分批建仓）。`openDeep(row)` 改读 `row.deepAnalysis` 秒出。

### 2026-07-30（在线人数 + 后台预热 + 列宽 + 持有时间 + 持续建议修复 + 深度分析模板）

- **首页在线人数 + 后台预热**：新增 `SystemController`——`GET /api/system/online`（2 分钟心窗口，内存 `ConcurrentHashMap`）、`POST /api/system/ping`（30s 心跳）、`POST /api/system/warmup`（后台异步预热 stock/fund 深度分析进 60min 缓存，5min 节流 + 并发锁）。`Home.vue` 加「在线 N 人」徽标（>1 变绿）+ 30s 心跳；在线>0 自动触发 warmup；板块卡显示「⚡ 板块已就绪」绿标。
- **列宽 + 持有时间空态**：「推荐」列 100/110 → 135，精排 chip 容器 `width:100%`；持有时间空态「—」→「AI 推算中…」。
- **🔧 持续建议为空根因（重要）**：fund advice 接口 `POST /screen/fund/advice?category=中文` 被 Tomcat 拒 400（Invalid character），前端 `loadAdvice` catch 静默吞错 → adviceMap 恒空 → 显示「—」。**修复**：合并 advice 进精排，LLM 一次输出 5 段分析 + 精排评分 JSON + 持续建议 JSON，直接写入 `row.advice`（`{code, short/mid/long:{horizon,returnRange,logic}, mode, model}`），前端读 `row.advice`，去掉独立 advice 接口。
- **深度分析提示词模板优化（最新，见第 8 节）**：身份=资深价值投资者（遵循《聪明的投资者》《穷查理宝典》）；先排雷再谈收益、不预测短期、锁 3 年中长期；股票 5 步 / 基金 4 步；5 段固定输出格式。
- **云端部署上线**：见第 10 节。3 个需求全量上线 <CLOUD_PUBLIC_IP>，验证 `/api/system/online`→online:1、warmup→started、/api/screen/fund→total:30 dataSource:real deepAnalysis 完整。

---

## 8. 核心 AI 提示词模板（价值投资者）

> 位置：`backend/.../service/DeepAnalysisService.java` 的 `buildStockPrompt` / `buildFundPrompt`。
> 这是用户 2026-07-30 指定的最终模板，LLM 同时输出 5 段分析 + 精排评分 JSON + 持续建议 JSON。

**身份**：资深价值投资者，严格遵循《聪明的投资者》、《穷查理宝典》投资理念。

**规则**：
- 自动识别标的类型：A股个股 / 混合型·指数公募基金。
- 核心准则：先排雷，再谈收益；规避致命风险优先；不预测短期涨跌；侧重 3 年维度中长期判断。

**分支逻辑**：
- 情况 A（A股股票）：① 基础排雷 → ② 估值分析 → ③ 基本面三层检验 → ④ 风险清单 → ⑤ 结论分级。
- 情况 B（公募基金）：① 基础排雷 → ② 持仓分析 → ③ 收益与风险 → ④ 风险清单 → ⑤ 结论分级。

**硬性要求**：
1. 禁止单纯乐观唱多，须同时列明利空与风险。
2. 不预测短期，锁定 3 年中长期维度。
3. 输出结构清晰，标题分段。
4. 最终输出 5 段固定顺序：

```
【标的类型识别】
【风险排查总结】
【估值&基本面分析】
【核心风险汇总】
【最终投资评级+操作建议】
```

**精排评分 JSON**（从 LLM 回复正则提取）：
```json
{"refinedScore": 85, "refinedRating": "强烈推荐", "confidence": "高"}
```

**持续建议 JSON**（写入 `row.advice`）：
```json
{
  "short":  {"horizon": "3-6个月", "returnRange": "+8%~+18%", "logic": "..."},
  "mid":    {"horizon": "1-2年",   "returnRange": "+15%~+30%", "logic": "..."},
  "long":   {"horizon": "3年以上",  "returnRange": "+50%~+80%", "logic": "..."},
  "mode": "real", "model": "DeepSeek"
}
```

---

## 9. 关键约定与安全提醒

- **⚠️ API Key 安全**：`data/llm-configs.json` 含明文 Key，**已 gitignore，切勿提交到 Git 或上传分享**。换电脑后需重新填 Key（或安全拷贝此文件）。重启后端若需保留刚填的 Key，可用 `jmap -dump` 抓堆提取后写入，但**堆转储文件含明文 Key，提取后必须删除**。
- **API Key 持久化**：运行时在「AI 设置」改 Key，经 `PUT /api/llm/configs/{id}` 自动回写 `data/llm-configs.json`，无需重启。
- **真实数据标记**：后端响应每项带 `dataSource=real|mock`；前端表头显示「真实数据/演示数据」。
- **真实筛选逻辑**：`RealScreenService` 格雷厄姆 V=EPS×(8.5+2g)、护城河量化、PE 横截面分位、安全边际、综合前 30；缺失字段置 null 不编造。
- **深度分析已合并 advice**：`DeepAnalysisService` 精排时 LLM 一次输出 5 段 + 评分 JSON + 持续建议 JSON，写入 `row.advice`；前端读 `row.advice` 直接展示，不再独立调 advice 接口（避免中文 category URL 被 Tomcat 400 拒）。
- **AI 弹框排版**：`MarkdownView.vue` 手写 markdown 渲染；重点加粗红字 `#c0392b`+浅黄底；### 蓝色左边框；✅❌💡⚠️🎯 单独 span；行高 1.85、字号 14px；弹框 `width="80%"`。
- **涨红跌绿（中国习惯）**：前端收益率/涨跌用红涨绿跌。
- **数据库切换**：`application.yml` 经 `${DB_URL}/${DB_USERNAME}/${DB_PASSWORD}` 读环境变量，Docker/云部署只需注入这 3 个变量即可切换，无需改码。

---

## 10. 云端部署（腾讯云 <CLOUD_PUBLIC_IP>）

### 10.1 服务器信息

| 项 | 值 |
|---|---|
| 公网 IP | **<CLOUD_PUBLIC_IP>** |
| SSH 用户 | **ubuntu**（非 root） |
| 密码 | **<APP_PASSWORD>** |
| SSH 密钥 | 本机 `C:\Users\Administrator\.ssh\id_ed25519.pub` 已加进 `ubuntu` 的 `~/.ssh/authorized_keys`，可 `ssh -i id_ed25519 ubuntu@<CLOUD_PUBLIC_IP>` 直连 |
| 项目目录 | `/home/ubuntu/finance-system`（**不是 git 仓库**，主机无 java/maven） |
| 访问 | `http://<CLOUD_PUBLIC_IP>`（http）或 `https://<CLOUD_PUBLIC_IP>`（自签证书）；手机 UA 自动跳 `/m/` |
| 登录 | `admin` / `<APP_PASSWORD>` |

> ⚠️ 域名 `<YOUR_DOMAIN>` 子域 HTTPS 被其代理层拦截（Connection reset），需腾讯云备案才能用域名；暂用 IP 访问。

### 10.2 云端架构

- `docker-compose.yml`：4 容器 `mysql`(3306) + `backend`(8090, build 根 Dockerfile) + `collector`(8091) + `nginx`(80/443)。
- 后端镜像 `Dockerfile`：`FROM eclipse-temurin:17-jre` + `COPY backend/target/finance-system-backend.jar` + `COPY data/`（含 llm-configs.json）。**重建 backend 镜像会自带云端 data/（Key 不丢）；切勿 SCP 本地 data/ 覆盖云端，否则丢 Key。**
- nginx 挂载：`./dist`（项目根，非 frontend/dist）→ 桌面版；`./dist-mobile` → `/m/` 手机版；`./nginx.conf`；自签证书 `selfsigned.crt/key`。
- backend 容器环境变量：`COLLECTOR_URL=http://finance-collector:8091`（修复 Docker 网络隔离导致真实数据回退 mock）。

### 10.3 标准部署流程（本机 → 云端）

```bash
# 1. 本机构建
cd finance-system
bash mvn17.sh -DskipTests package                 # → backend/target/finance-system-backend.jar
cd frontend && npm run build && cd ..            # → frontend/dist（避开 WorkBuddy 安全删除 shim）
cp -r frontend/dist/. dist/                       # 发到根 dist（nginx 挂载点）
# 手机端同理 → dist-mobile/

# 2. SCP 到云端
scp -i ~/.ssh/id_ed25519 frontend/dist/. ubuntu@<CLOUD_PUBLIC_IP>:/home/ubuntu/finance-system/dist/
scp -i ~/.ssh/id_ed25519 backend/target/finance-system-backend.jar ubuntu@<CLOUD_PUBLIC_IP>:/home/ubuntu/finance-system/backend/target/

# 3. 云端重建容器
ssh ubuntu@<CLOUD_PUBLIC_IP> 'cd /home/ubuntu/finance-system && docker compose up -d --build backend && docker compose restart nginx'
```

> 若只改前端：`scp dist/.` 后 `docker compose restart nginx` 即可（nginx 静态挂载）。
> 若只改后端：SCP jar 后 `docker compose up -d --build backend`。

### 10.4 Git 推送通道（本机 → GitHub）

- GitHub 直连 `github.com:443` 超时；`gh-proxy.com` 只读不可推送；`cnb.cool` 需 token。
- **唯一可用推送协议：SSH `git@github.com`**。deploy key 已加（仓库级，id 158806792，read_only:false），remote 已设为 `git@github.com:PatrickMai-Bo/finance-system.git`。
- 推送前 master 曾与微信小程序支线分叉：已把远古 `74e7387` 备份到 `wechat-miniprogram-backup` 分支，`git push origin master --force-with-lease` 对齐理财线 `e664476`。

---

## 11. 排错速记 / 踩坑大全

1. **东财 push2 行情接口本机双路不可达**（代理 10808 ProxyError + 直连被断）→ 已弃用，改三数据源（东财业绩报表 + 腾讯行情 + 天天基金排行）。
2. **采集服务 `requests` 缺失**：managed venv `C:/Users/Administrator/.workbuddy/binaries/python/envs/default` 才有 requests；用其 `Scripts/python.exe collector.py`（cwd=finance-system/collector）。系统 `D:/python` 无 requests。
3. **前端 `npm run build` 被 WorkBuddy「安全删除」shim 拦截**：vite 清空 dist 时 Trash 失败。改用 `node node_modules/vite/bin/vite.js build` 或 `vite build --outDir <全新目录>` 后拷贝。
4. **nginx bind mount 失效**：替换 dist 用 `mv dist dist.old && mkdir dist` → Docker 绑旧 inode，新 dist 没挂进容器。修复：清空原目录内容（`rm -rf dist/* && tar -xzf ... -C dist`）或 `docker rm -f finance-nginx && docker compose up -d nginx` 重建容器。
5. **Vite dev 启动方式**：用 `node node_modules/vite/bin/vite.js`，别用 `node .bin/vite`（那是 bash 脚本，Git Bash 下报错）。
6. **后端端口被抢占**：系统环境变量 `SERVER__PORT=54913` 被 Spring relaxed binding 抢占 → 必须 `--server.port=8090` 覆盖。
7. **Maven 路径转换 bug**：Git Bash 下 `mvn` 脚本路径转换坏 → 用 `mvn17.sh`（直接 java 调 classworlds）。
8. **中文 category URL 被 Tomcat 400**：fund advice 接口 `?category=中文` 被拒 → 已合并 advice 进精排，不再独立调 advice 接口。
9. **Docker 网络隔离导致真实数据回退 mock**：容器内 `localhost` 指向 backend 自身 → 加 `COLLECTOR_URL: http://finance-collector:8091`。
10. **重启后端丢 API Key**：见第 9 节，需 `data/llm-configs.json` 或 jmap 提取。
11. **云端不是 git 仓库**：`/home/ubuntu/finance-system` 无 .git，无 java/maven，部署靠 SCP 构建产物 + compose 重建，不是 git pull。

---

## 12. API 文档

> 所有接口统一响应格式：`{"code":0,"msg":"success","data":...}`；异常：`{"code":非0,"msg":"错误信息"}`
> 前端接口封装见 `frontend/src/api/index.js`。

### 认证

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/auth/login` | 登录 | body: `{username, password}` |
| POST | `/api/auth/forgot-reset` | 忘记密码（手机校验） | body: `{phone, newPassword}`（仅 `<BOUND_PHONE>` 可改）|
| GET | `/api/auth/me` | 当前用户信息 | — |

### 系统（在线人数 + 预热）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/system/online` | 在线人数（2min 心窗口） | — |
| POST | `/api/system/ping` | 30s 心跳 | — |
| POST | `/api/system/warmup` | 后台异步预热 stock/fund 深度分析 | — |

### 首页

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/home/overview` | 首页概览（净资产/基金TOP3/股票TOP3/AI状态） |

### 股票筛选

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/screen/stock` | 股票筛选列表 | query: `page`(默认1), `size`(默认10) |
| POST | `/api/screen/stock/run` | 刷新筛选（清除缓存重跑） | — |
| POST | `/api/screen/stock/analyze/{code}` | 单只深度分析（命中缓存秒出） | path: 代码 |
| POST | `/api/screen/stock/refined` | 精排分析（并发30只→重排） | query: `force=true` 清缓存 |
| GET | `/api/screen/detail/{code}` | 单只股票详情 | path: 代码 |

### 基金筛选

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/screen/fund` | 基金筛选列表 | query: `category`(全部/股票型/混合型/债券型/指数基金/QDII), `page`, `size` |
| POST | `/api/screen/fund/run` | 刷新筛选 | — |
| POST | `/api/screen/fund/analyze/{code}` | 单只深度分析 | path: 代码 |
| POST | `/api/screen/fund/refined` | 精排分析 | query: `force=true` |
| GET | `/api/screen/fund/categories` | 所有基金分类 | — |

### 个人财务 / 记账 / 家底 / 自选

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/finance/balance-sheet` | 资产负债表 |
| GET | `/api/finance/cashflow` | 现金流（主动/被动收入、支出结构） |
| GET | `/api/finance/freedom` | 财务自由度（被动收入覆盖率） |
| POST | `/api/ledger/add` | 记一笔 |
| DELETE | `/api/ledger/{id}` | 删除一笔 |
| GET | `/api/ledger/list` | 全部流水 |
| GET | `/api/ledger/summary` | 汇总（资产负债表+现金流象限+诊断） |
| POST | `/api/holding/add` | 新增资产/负债 |
| PUT/DELETE | `/api/holding/{id}` | 修改/删除 |
| GET | `/api/holding/list` | 全部条目 |
| GET | `/api/holding/summary` | 净资产+现金流汇总 |
| POST | `/api/watchlist/add` | 新增自选 |
| PUT/DELETE | `/api/watchlist/{id}` | 修改/删除 |
| GET | `/api/watchlist/list` | 按类型列出（type=stock/fund） |
| GET | `/api/watchlist/summary` | 组合汇总 |

### 决策思维

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/decision/models` | 思维模型库（8个） |
| GET | `/api/decision/framework` | 五阶框架 |
| POST | `/api/decision/search` | 按五阶分析决策问题（body: `{question, scene?}`） |
| GET | `/api/decision/logs` | 决策日志列表 |

### AI 分析 / LLM 配置

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ai/analyze` | 统一 AI 入口（scene: stock/fund/finance/holding/watchlist/stock-batch/fund-batch/decision） |
| GET | `/api/llm/configs` | 列出模型配置 |
| POST | `/api/llm/configs` | 新增模型 |
| PUT/DELETE | `/api/llm/configs/{id}` | 修改/删除 |
| POST | `/api/llm/active/{id}` | 设为激活模型 |
| POST | `/api/llm/test/{id}` | 测试连接 |

### AI 分析 scene 枚举

| scene | 含义 | payload |
|---|---|---|
| `stock` | 单只股票分析 | `{name, code, pe, eps, roe, ...}` |
| `fund` | 单只基金分析 | `{name, code, category, nav, ...}` |
| `stock-batch` / `fund-batch` | 多只横向对比 | `{items:[...], count}` |
| `finance` | 财务诊断 | ledger+holding 汇总 |
| `holding` | 资产负债点评 | holding 列表 |
| `watchlist` | 自选组合点评 | watchlist 列表 |
| `decision` | 五阶决策分析 | `{decision, scene, finance(自动注入)}` |

---

## 13. 数据库表结构

通过 `init.sql` 自动创建 4 张表（库 `finance_system`，MySQL 8.0）：

| 表名 | 用途 | 关键字段 |
|---|---|---|
| `ledger` | 记账流水 | id, date, type, amount, description, category |
| `holding` | 存量资产负债 | id, big_type, name, amount, monthly_cashflow, note |
| `watchlist` | 自选清单 | id, type, name, code, category, cost, amount, target_price, note |
| `decision_log` | 决策历史 | id, scene, question, answer, verdict, model, created_at |

---

## 常见问题

- **Vite 代理没生效？** 检查 `frontend/vite.config.js` proxy 指向 `http://localhost:8090`，后端已启动。
- **后端报数据库连接失败？** 确认 MySQL 运行、库 `finance_system` 已建、`init.sql` 已执行。
- **AI 返回「回退规则估算」？** 在「AI 设置」填入正确 API Key（OpenAI 兼容接口）。
- **显示「演示数据」而非真实？** 检查采集服务 8091 是否运行；Docker 环境确认 backend 有 `COLLECTOR_URL=http://finance-collector:8091`。
- **换电脑后 Key 丢了？** 在「AI 设置」重新填，自动回写 `data/llm-configs.json`。

---

**License**: MIT | **Author**: PatrickMai-Bo | **最后更新**: 2026-07-30
