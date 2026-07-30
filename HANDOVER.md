# 理财投资决策系统 · 接手速通 Checklist

> 面向「换电脑 / 换人」接手本项目的分步核对清单。按从上到下的顺序执行，全部打勾即可本地跑起来并上线云端。
> 配套文档：`README.md`（完整手册）、`api-doc.html`（交互式接口测试）、`deploy-cloud.sh`（一键部署）、`README.sanitized.md`（脱敏版）。

---

## 0. 前置认知（先读后做）
- [ ] 项目是什么：股票/基金智能筛选 + AI 价值投资深度分析 + 个人财务 + 五阶决策 + 云端部署
- [ ] 技术栈：Vue3+Vite+Element Plus(桌面)/Vant4(手机) · Spring Boot3+Java17 · Python FastAPI(采集) · MySQL8 · MyBatis-Plus · Docker
- [ ] 三服务：采集(8091) → 后端(8090) → 前端(5173 dev / Nginx 80·443 生产)
- [ ] Git 远程：`git@github.com:PatrickMai-Bo/finance-system.git`（SSH 协议，仅 SSH 可推送）
- [ ] 微信小程序支线在 `wechat-miniprogram-backup` 分支，勿合并回 master

---

## 1. 环境准备（新电脑）
- [ ] Java 17+（JDK17，本地例 `D:\java\JDK17`）
- [ ] Node 22+（前端 dev/build）
- [ ] Python 3.12+（采集服务，需 `requests`/`fastapi`/`uvicorn`；优先用 managed venv 以免缺包）
- [ ] MySQL 8.0（本地例 `service MySQL80`，端口 3306，默认 `root/root`）
- [ ] Docker + Docker Compose（仅云端/容器化部署需要）
- [ ] Git + SSH key（已加入 GitHub deploy key 才能推送）

## 2. 获取源码
- [ ] `git clone git@github.com:PatrickMai-Bo/finance-system.git`
- [ ] `cd finance-system`

## 3. 初始化数据库
- [ ] 启动 MySQL
- [ ] `mysql -uroot -proot < init.sql`（建库 `finance_system` + 4 张表：`ledger`/`holding`/`watchlist`/`decision_log`）
- [ ] 表空时会自动播种示例数据，无需手填

## 4. 配置 AI API Key（关键）
- [ ] `data/llm-configs.json` 含明文 Key，**已 gitignore，换电脑需重新填**
- [ ] 方式A：启动后在「AI 设置」页面手动填（自动回写文件，无需重启）
- [ ] 方式B：安全拷贝旧机的 `data/llm-configs.json`
- [ ] ⚠️ 重启后端会丢内存中的 Key；磁盘无该文件时需用 `jmap -dump` 提取后写入，**堆转储含明文 Key，提取后必须删除**
- [ ] 默认预设 4 模型：DeepSeek(默认激活) + 阿里百炼 qwen-plus(enableSearch) + Kimi + 智谱

## 5. 构建后端
- [ ] 用 `bash mvn17.sh clean package -DskipTests`（**必须**用 mvn17.sh，规避 Git Bash 路径转换 bug）
- [ ] 产物校验：`backend/target/finance-system-backend.jar`（约 29MB）
- [ ] 后端必须用 `--server.port=8090` 启动（系统变量 `SERVER__PORT=54913` 会被抢占）

## 6. 构建前端
- [ ] `cd frontend && npm install && node node_modules/vite/bin/vite.js build && cd ..`
- [ ] `cd frontend-mobile && npm install && node node_modules/vite/bin/vite.js build && cd ..`
- [ ] 拷贝：`cp -r frontend/dist/. dist/` 与 `cp -r frontend-mobile/dist/. dist-mobile/`
- [ ] ⚠️ 若 `npm run build` 被 WorkBuddy「安全删除」shim 拦截，改用 `node node_modules/vite/bin/vite.js build` 或 `vite build --outDir <新目录>` 后拷贝

## 7. 启动三服务（常驻）
- [ ] 采集服务(8091)：`python collector/collector.py`（managed venv 的 python，cwd=finance-system/collector；`trust_env=False` 绕代理）
- [ ] 后端(8090)：`java -jar backend/target/finance-system-backend.jar --server.port=8090`（**cwd 须为项目根目录**，否则 `data/` 路径错位）
- [ ] 前端 dev(5173)：`cd frontend && node node_modules/vite/bin/vite.js --host 0.0.0.0 --port 5173`（代理 `/api`→8090）
- [ ] 浏览器开 `http://localhost:5173`；手机模拟访问把 UA 加 `mobile` 测 `/m/` 跳转
- [ ] 默认账号 `admin` / `<APP_PASSWORD>`，绑定手机 `<BOUND_PHONE>`

## 8. 本地验证清单
- [ ] `/api/system/online` 返回 online≥1
- [ ] `/api/screen/stock` 与 `/api/screen/fund` 返回 `dataSource:real`（确认采集服务 8091 在跑）
- [ ] 点「详细分析」能命中缓存秒出（DeepAnalysisService 60min 缓存）
- [ ] 首页「在线 N 人」徽标显示、板块卡「⚡ 板块已就绪」
- [ ] 记账/家底/自选 CRUD 重启后端不丢（落库验证）

## 9. 云端部署（已有服务器）
- [ ] 参考 `deploy-cloud.sh`：`export CLOUD_HOST=<CLOUD_PUBLIC_IP>; export SSH_KEY=~/.ssh/id_ed25519; bash deploy-cloud.sh`
- [ ] 云端目录 `/home/ubuntu/finance-system`（非 git 仓库，无 java/maven，靠 SCP 产物 + compose 重建）
- [ ] backend 容器须注入 `COLLECTOR_URL=http://finance-collector:8091`（修复 Docker 网络隔离导致真实数据回退 mock）
- [ ] 重建 backend 镜像会自带云端 `data/`（Key 不丢）；**切勿 SCP 本地 data/ 覆盖云端**，否则丢 Key
- [ ] 验证：`/api/system/online`、`/api/system/warmup`、`/api/screen/fund`(total:30, dataSource:real)

## 10. 排错速记（高频坑，Top 8）
- [ ] 显示「演示数据」非真实 → 检查采集服务 8091；Docker 确认 `COLLECTOR_URL` 已注入
- [ ] 后端连库失败 → MySQL 在跑 + 库 `finance_system` 已建 + `init.sql` 已执行
- [ ] AI 回退规则估算 → 「AI 设置」填正确 Key（OpenAI 兼容接口）
- [ ] 后端端口被抢 → 必须 `--server.port=8090`
- [ ] Maven 路径转换 bug → 用 `mvn17.sh`
- [ ] 前端 build 被安全删除 shim 拦截 → 用 `node .../vite.js build`
- [ ] nginx 静态不更新 → 清空原目录内容或 `docker rm -f finance-nginx && docker compose up -d nginx`
- [ ] 中文 category URL 400 → 已合并 advice 进精排，勿再独立调 advice 接口

## 11. 安全红线（务必遵守）
- [ ] ⚠️ `data/llm-configs.json`（明文 Key）**绝不提交 git / 上传分享**
- [ ] ⚠️ 公网 IP、登录密码、绑定手机号等敏感信息**不要写进会被提交的文档**（见 `README.sanitized.md`）
- [ ] ⚠️ `jmap` 堆转储含明文 Key，提取后必须删除
- [ ] 涨红跌绿（中国习惯）：前端收益率/涨跌用红涨绿跌

---

**提示**：所有接口清单与在线测试见 `api-doc.html`；完整手册见 `README.md`。
