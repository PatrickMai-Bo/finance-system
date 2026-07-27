# 理财投资决策系统 v1.0

个人理财投资决策系统 —— 股票/基金智能筛选 + AI 深度分析 + 五阶决策思维 + 个人财务管理。

---

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [快速开始（本地）](#快速开始本地)
- [Docker 部署](#docker-部署)
- [API 文档](#api-文档)
- [环境变量](#环境变量)
- [数据库表结构](#数据库表结构)

---

## 功能概览

| 板块 | 功能 | 说明 |
|---|---|---|
| 首页 | 总览 | 净资产、被动收入覆盖率、基金/股票 TOP3、AI 状态 |
| 个人财务 | 记账 + 资产负债表 | 手工记账自动分类（资产/负债）、被动收入追踪、财务自由度评估 |
| 基金筛选 | 智能排行 + AI 建议 | PE 估值优先、护城河量化、真实数据采集、AI 持有建议（短/中/长期） |
| 股票筛选 | 格雷厄姆估值 + 安全边际 | V=EPS×(8.5+2g)、PE 横截面分位、综合评分前 30 |
| 决策思维 | 五阶避错框架 | 逆向排雷→价值定性→能力圈→替代方案→情绪冷却，自动接入个人财务数据 |
| AI 设置 | 多模型管理 | DeepSeek / 阿里百炼 / Kimi / 智谱，OpenAI 兼容接口统一管理 |

### AI 特色

- **持有建议**：对每只标的并发调 AI，输出短期/中期/长期持有时间 + 预计收益率
- **批量对比**：勾选多只后一键横向对比（护城河/估值/风险/配置建议）
- **决策分析**：自动注入用户真实财务数据（净资产/覆盖率/被动收入），AI 给出量化建议
- **历史警示**：记录决策历史，再次问类似问题时顶部红色大字警告

---

## 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 前端 | Vue 3 + Vite + Element Plus | Vue 3.x |
| 后端 | Spring Boot 3 + Java 17 | Spring Boot 3.x |
| 数据采集 | Python FastAPI | Python 3.13 |
| 数据库 | MySQL | 8.0 |
| ORM | MyBatis-Plus | 3.5.7 |
| AI | OpenAI 兼容接口 | DeepSeek/百炼/Kimi/智谱 |
| 部署 | Docker Compose | MySQL + Backend + Collector + Nginx |

---

## 项目结构

```
finance-system/
├── frontend/                 # Vue3 前端（Vite）
│   ├── src/
│   │   ├── api/index.js      # 所有 API 方法定义
│   │   ├── components/       # AiAnalyze / MarkdownView / WatchlistPanel 等
│   │   ├── layout/           # MainLayout 导航栏
│   │   ├── stores/           # Pinia 状态（auth.js）
│   │   ├── views/            # Home / Finance / FundScreen / StockScreen / Decision / AiPanel / Login
│   │   └── router/index.js   # 路由定义
│   └── vite.config.js        # 含 /api → :8090 代理
├── backend/                  # Spring Boot 3 + Java 17
│   ├── src/main/java/com/finance/
│   │   ├── controller/       # 11 个 REST Controller
│   │   ├── service/          # 业务逻辑层（AI/筛选/记账/自选…）
│   │   ├── entity/           # Ledger / Holding / Watchlist / DecisionLog
│   │   ├── mapper/           # MyBatis-Plus Mapper
│   │   ├── config/           # JWT / CORS
│   │   └── common/           # R（统一响应）/ PageResult / GlobalExceptionHandler
│   └── src/main/resources/application.yml
├── collector/                # Python FastAPI 数据采集服务
│   └── collector.py          # 东财业绩报表 + 腾讯行情 + 天天基金排行
├── data/                     # LLM 配置文件（含 API Key，已 gitignore）
├── dist/                     # 前端构建产物
├── Dockerfile                # 后端镜像（JRE 17）
├── Dockerfile.collector      # 采集服务镜像（Python）
├── docker-compose.yml        # 一键部署编排
├── nginx.conf                # Nginx 反向代理配置
├── init.sql                  # MySQL 建表语句
└── mvn17.sh                  # Maven 包装脚本
```

---

## 快速开始（本地）

### 前提

- Java 17+、Node 22+、Python 3.12+
- MySQL 8.0（运行中，默认 root/root@localhost:3306）
- 数据库 `finance_system` 已建（运行 `init.sql`）

### 1. 启动采集服务（8091）

```bash
cd finance-system/collector
pip install fastapi uvicorn requests
python collector.py
```

### 2. 构建 & 启动后端（8090）

```bash
cd finance-system/backend
bash ../mvn17.sh clean package -DskipTests
java -jar target/finance-system-backend.jar --server.port=8090
```

### 3. 启动前端（5173）

```bash
cd finance-system/frontend
npm install
npx vite --port 5173
```

### 4. 访问

浏览器打开 **http://localhost:5173**

- 默认账号：`admin` / `admin123`

---

## Docker 部署

一键启动 4 个容器（MySQL + 后端 + 采集 + Nginx）：

```bash
# 先确保 backend/target/finance-system-backend.jar 已编译
# 确保 data/llm-configs.json 存在
# 确保前端已构建 dist/
cd finance-system/frontend && npx vite build --outDir ../dist && cd ..

# 拷贝到服务器后：
docker compose up -d --build
```

访问 **http://服务器IP**（Nginx 80 端口自动代理前端 + /api → 后端 8090）

### 腾讯云部署注意事项

如果 Docker Hub 拉取超时（中国访问受限），配置镜像加速：

```bash
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
EOF
sudo systemctl restart docker
```

---

## API 文档

> 所有接口统一响应格式：`{"code":0,"msg":"success","data":...}`  
> 异常格式：`{"code":非0,"msg":"错误信息"}`

### 认证

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/auth/login` | 登录 | body: `{username, password}` |
| GET | `/api/auth/me` | 当前用户信息 | — |

### 首页

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/home/overview` | 首页概览（净资产/基金TOP3/股票TOP3/AI状态） |

### 股票筛选

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/screen/stock` | 股票筛选列表 | query: `page`(默认1), `size`(默认10) |
| POST | `/api/screen/stock/run` | 刷新筛选（清除缓存重跑） | — |
| POST | `/api/screen/stock/advice` | 获取当前页 AI 持有建议 | query: `page`, `size`; `invalidate=true` 清缓存 |
| GET | `/api/screen/detail/{code}` | 单只股票详情 | path: 股票代码 |

### 基金筛选

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/screen/fund` | 基金筛选列表 | query: `category`(全部/股票型/混合型/债券型/指数基金/QDII), `page`, `size` |
| POST | `/api/screen/fund/run` | 刷新筛选 | — |
| POST | `/api/screen/fund/advice` | 获取当前页 AI 持有建议 | query: `page`, `size`, `category`; `invalidate=true` |
| GET | `/api/screen/fund/categories` | 所有基金分类 | — |

### 个人财务

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/finance/balance-sheet` | 资产负债表 | — |
| GET | `/api/finance/cashflow` | 现金流（主动/被动收入、支出结构） | — |
| GET | `/api/finance/freedom` | 财务自由度（被动收入覆盖率） | — |

### 记账（Ledger）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/ledger/add` | 记一笔 | body: `{date, type(income/expense), amount, desc, category?}` |
| DELETE | `/api/ledger/{id}` | 删除一笔 | path: id |
| GET | `/api/ledger/list` | 全部流水（日期倒序） | — |
| GET | `/api/ledger/summary` | 汇总（资产负债表 + 现金流象限 + 诊断） | — |

### 存量资产（Holding）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/holding/add` | 新增资产/负债 | body: `{bigType(资产/负债), name, amount, monthlyCashflow, note}` |
| PUT | `/api/holding/{id}` | 修改 | path: id; body: 同上 |
| DELETE | `/api/holding/{id}` | 删除 | path: id |
| GET | `/api/holding/list` | 全部条目（资产在前、负债在后） | — |
| GET | `/api/holding/summary` | 净资产 + 现金流汇总 | — |

### 自选清单（Watchlist）

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/watchlist/add` | 新增自选 | body: `{type(stock/fund), name, code, category, cost, amount, targetPrice, note}` |
| PUT | `/api/watchlist/{id}` | 修改 | path: id; body: 同上 |
| DELETE | `/api/watchlist/{id}` | 删除 | path: id |
| GET | `/api/watchlist/list` | 按类型列出 | query: `type`(stock/fund) |
| GET | `/api/watchlist/summary` | 组合汇总 | query: `type`(stock/fund) |

### 决策思维

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/decision/models` | 思维模型库（8个模型） | — |
| GET | `/api/decision/framework` | 五阶框架（宗旨/支柱/五步/检查清单/场景库） | — |
| POST | `/api/decision/search` | 按五阶分析决策问题 | body: `{question, scene?}` |
| GET | `/api/decision/logs` | 决策日志列表 | — |

### AI 分析

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| POST | `/api/ai/analyze` | 统一 AI 分析入口 | body: `{scene, payload}`（scene: stock/fund/finance/holding/watchlist/stock-batch/fund-batch/decision） |

### LLM 配置

| 方法 | 路径 | 说明 | 参数 |
|---|---|---|---|
| GET | `/api/llm/configs` | 列出所有模型配置 | — |
| POST | `/api/llm/configs` | 新增模型配置 | body: `{name, baseUrl, model, apiKey, enableSearch?}` |
| PUT | `/api/llm/configs/{id}` | 修改配置 | path: id; body: 同上 |
| DELETE | `/api/llm/configs/{id}` | 删除配置 | path: id |
| POST | `/api/llm/active/{id}` | 设为首选激活模型 | path: id |
| POST | `/api/llm/test/{id}` | 测试模型连接 | path: id |

---

### AI 分析 scene 枚举

| scene | 含义 | payload 结构 |
|---|---|---|
| `stock` | 单只股票分析 | `{name, code, pe, eps, roe, ...}` |
| `fund` | 单只基金分析 | `{name, code, category, nav, ...}` |
| `stock-batch` | 多只股票横向对比 | `{items: [{name,code,...}, ...], count}` |
| `fund-batch` | 多只基金横向对比 | `{items: [{name,code,...}, ...], count}` |
| `finance` | 财务诊断 | ledger + holding 汇总数据 |
| `holding` | 资产负债点评 | holding 列表 |
| `watchlist` | 自选组合点评 | watchlist 列表 |
| `decision` | 五阶决策分析 | `{decision, scene(场景), finance(自动注入)}` |

---

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/finance_system?...` | 数据库连接 |
| `DB_USERNAME` | `root` | 数据库用户 |
| `DB_PASSWORD` | `root` | 数据库密码 |

Docker Compose 已预配好以上三个变量指向 `mysql:3306` 容器，无需手动设置。

---

## 数据库表结构

通过 `init.sql` 自动创建 4 张表：

| 表名 | 用途 | 关键字段 |
|---|---|---|
| `ledger` | 记账流水 | id, date, type, amount, description, category |
| `holding` | 存量资产负债 | id, big_type, name, amount, monthly_cashflow, note |
| `watchlist` | 自选清单 | id, type, name, code, category, cost, amount, target_price, note |
| `decision_log` | 决策历史 | id, scene, question, answer, verdict, model, created_at |

---

## 常见问题

### Q: 前端 Vite 代理没生效？
检查 `frontend/vite.config.js` 中 proxy 指向 `http://localhost:8090`，确保后端已启动。

### Q: 后端启动报数据库连接失败？
确认 MySQL 已启动，数据库 `finance_system` 已建，`init.sql` 已执行。

### Q: AI 分析返回"回退规则估算"？
在「AI 设置」页面为激活的模型填入正确的 API Key。模型仅支持 OpenAI 兼容接口。

### Q: 如何切换大模型？
「AI 设置」→ 点目标模型旁的「设为激活」按钮。支持 DeepSeek / 阿里百炼 / Kimi / 智谱。

---

**License**: MIT | **Author**: PatrickMai-Bo
