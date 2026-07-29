package com.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 两阶段筛选的第二阶段：对已通过定量筛选的标的，调用 DeepSeek V4 Pro 按严格7段模板做深度分析。
 * 含5条优化规则（规模/持有人/成长赛道止损/客观平衡/禁止绝对化）+ LLM精排评分JSON。
 * 缓存 60 分钟，按 code 缓存。
 */
@Service
public class DeepAnalysisService {

    private final LlmClient llm;
    private final LlmConfigService llmCfg;
    private final RealScreenService realScreen;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newFixedThreadPool(8); // 精排并发

    // 缓存: key="stock:600519" / "fund:001480"
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheAt = new ConcurrentHashMap<>();
    private static final long TTL = 60 * 60 * 1000L;

    // 正则: 提取 {"refinedScore":85,"refinedRating":"强烈推荐","confidence":"高"}
    private static final Pattern SCORE_PAT = Pattern.compile(
        "\\{\\s*\"refinedScore\"\\s*:\\s*(\\d+)\\s*,\\s*\"refinedRating\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"confidence\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");

    public DeepAnalysisService(LlmClient llm, LlmConfigService llmCfg, RealScreenService realScreen) {
        this.llm = llm;
        this.llmCfg = llmCfg;
        this.realScreen = realScreen;
    }

    /** 股票深度分析 */
    public Map<String, Object> analyzeStock(String code) {
        String key = "stock:" + code;
        Map<String, Object> cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cacheAt.getOrDefault(key, 0L) < TTL) return cached;
        Map<String, Object> stock = findStock(code);
        if (stock == null) return Map.of("error", "未找到该股票", "mode", "not_found");
        Map<String, Object> result = doAnalyze("stock", stock);
        cache.put(key, result); cacheAt.put(key, System.currentTimeMillis());
        return result;
    }

    /** 基金深度分析 */
    public Map<String, Object> analyzeFund(String code) {
        String key = "fund:" + code;
        Map<String, Object> cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cacheAt.getOrDefault(key, 0L) < TTL) return cached;
        Map<String, Object> fund = findFund(code);
        if (fund == null) return Map.of("error", "未找到该基金", "mode", "not_found");
        Map<String, Object> result = doAnalyze("fund", fund);
        cache.put(key, result); cacheAt.put(key, System.currentTimeMillis());
        return result;
    }

    /** 清除缓存 */
    public void invalidate(String scene, String code) { String k = scene + ":" + code; cache.remove(k); cacheAt.remove(k); }
    public void invalidateAll() { cache.clear(); cacheAt.clear(); }

    /**
     * 全量精排：对所有筛选结果跑深度分析，解析精排评分后重新排序。
     * force=true 清除所有缓存重跑。
     */
    public List<Map<String, Object>> refinedStockList(boolean force) {
        List<Map<String, Object>> raw;
        try { raw = realScreen.stockList(); } catch (Exception e) { return List.of(); }
        return refinedList("stock", raw, force);
    }

    public List<Map<String, Object>> refinedFundList(String category, boolean force) {
        List<Map<String, Object>> raw;
        try { raw = realScreen.fundList(category); } catch (Exception e) { return List.of(); }
        return refinedList("fund", raw, force);
    }

    private List<Map<String, Object>> refinedList(String scene, List<Map<String, Object>> raw, boolean force) {
        if (force) invalidateAll();

        // 并发深度分析
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            String code = String.valueOf(item.get("code"));
            futures.add(pool.submit(() -> {
                if (force) { cache.remove(scene + ":" + code); cacheAt.remove(scene + ":" + code); }
                return doAnalyze(scene, item);
            }));
        }

        // 收集结果 + 提取精排评分
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                Map<String, Object> analysis = futures.get(i).get(180, TimeUnit.SECONDS);
                Map<String, Object> item = new LinkedHashMap<>(raw.get(i));
                item.put("deepAnalysis", analysis.get("analysis"));
                item.put("deepMode", analysis.get("mode"));
                item.put("deepModel", analysis.get("model"));
                item.put("refinedScore", analysis.getOrDefault("refinedScore", raw.get(i).getOrDefault("score", 0)));
                item.put("refinedRating", analysis.getOrDefault("refinedRating", raw.get(i).getOrDefault("rating", "观察")));
                item.put("confidence", analysis.getOrDefault("confidence", ""));
                result.add(item);
            } catch (Exception e) {
                Map<String, Object> item = new LinkedHashMap<>(raw.get(i));
                item.put("deepAnalysis", "⚠️ 分析超时或失败: " + e.getMessage());
                item.put("deepMode", "error");
                item.put("refinedScore", raw.get(i).getOrDefault("score", 0));
                item.put("refinedRating", raw.get(i).getOrDefault("rating", "观察"));
                result.add(item);
            }
        }

        // 按精排评分降序
        result.sort((a, b) -> {
            int sa = toInt(b.get("refinedScore"));
            int sb = toInt(a.get("refinedScore"));
            return Integer.compare(sa, sb);
        });
        // 重新编排名
        for (int i = 0; i < result.size(); i++) result.get(i).put("refinedRank", i + 1);
        return result;
    }

    // ================= private =================

    private Map<String, Object> findStock(String code) {
        try { return realScreen.stockList().stream().filter(s -> code.equals(s.get("code"))).findFirst().orElse(null); }
        catch (Exception e) { return null; }
    }
    private Map<String, Object> findFund(String code) {
        try { return realScreen.fundList("全部").stream().filter(f -> code.equals(f.get("code"))).findFirst().orElse(null); }
        catch (Exception e) { return null; }
    }

    private Map<String, Object> doAnalyze(String scene, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", data.get("name"));
        result.put("code", data.get("code"));

        Map<String, Object> active = llmCfg.active();
        if (active == null || isEmpty(active.get("apiKey"))) {
            result.put("mode", "no_model");
            result.put("analysis", "⚠️ 未配置 AI 模型或未填写 API Key");
            result.put("refinedScore", data.getOrDefault("score", 0));
            result.put("refinedRating", data.getOrDefault("rating", "观察"));
            return result;
        }

        try {
            String prompt = buildPrompt(scene, data);
            String reply = llm.chat(active, prompt);
            result.put("mode", "real");
            result.put("model", active.get("name") + " / " + active.get("model"));
            result.put("analysis", cleanAnalysis(reply));
            // 提取精排评分JSON; 失败则用原始评分
            extractScore(reply, result, data);
        } catch (Exception e) {
            result.put("mode", "error");
            result.put("analysis", "⚠️ AI 分析失败：" + e.getMessage());
            result.put("refinedScore", data.getOrDefault("score", 0));
            result.put("refinedRating", data.getOrDefault("rating", "观察"));
        }
        return result;
    }

    /** 从LLM回复中提取精排评分JSON */
    private void extractScore(String reply, Map<String, Object> result, Map<String, Object> data) {
        Matcher m = SCORE_PAT.matcher(reply);
        if (m.find()) {
            try {
                result.put("refinedScore", Integer.parseInt(m.group(1)));
                result.put("refinedRating", m.group(2));
                result.put("confidence", m.group(3));
                return;
            } catch (Exception ignore) {}
        }
        result.put("refinedScore", data.getOrDefault("score", 0));
        result.put("refinedRating", data.getOrDefault("rating", "观察"));
        result.put("confidence", "");
    }

    /** 移除分析文本末尾的评分JSON块（前端只展示纯分析内容） */
    private String cleanAnalysis(String reply) {
        Matcher m = SCORE_PAT.matcher(reply);
        if (m.find()) {
            return reply.substring(0, m.start()).trim();
        }
        return reply;
    }

    // ================= 提示词模板 =================

    private String buildPrompt(String scene, Map<String, Object> d) {
        return "stock".equals(scene) ? buildStockPrompt(d) : buildFundPrompt(d);
    }

    // ---------- 股票 ----------

    private String buildStockPrompt(Map<String, Object> d) {
        String name = str(d.get("name")); String code = str(d.get("code"));
        String industry = str(d.get("industry")); String price = str(d.get("price"));
        String pe = str(d.get("pe")); String peQuantile = str(d.get("peQuantile"));
        String roe = str(d.get("roe")); String grossMargin = str(d.get("grossMargin"));
        String intrinsicValue = str(d.get("intrinsicValue")); String safetyMargin = str(d.get("safetyMargin"));
        String moatScore = str(d.get("moatScore"));
        String moatTags = d.get("moatTags") instanceof List ? String.join("、", (List<String>) d.get("moatTags")) : "";
        String score = str(d.get("score")); String rating = str(d.get("rating"));

        return """
你现在是一名遵循格雷厄姆价值投资+芒格护城河体系的投资分析师，严格按照「先排雷、再定性、最后算估值」的逆向逻辑分析。

请分析标的：%s（%s）

### 已有定量数据（由系统从东财业绩报表+腾讯实时行情采集，经格雷厄姆公式计算）：
- 当前价格：%s 元
- PE-TTM：%s
- PE历史分位（在A股候选池中）：%s%%
- ROE：%s%%
- 毛利率：%s%%
- 格雷厄姆内在价值估算：%s 元
- 安全边际：%s%%
- 护城河评分（0-100）：%s
- 护城河标签：%s
- 系统综合评分：%s · 系统评级：%s
- 所属行业：%s

### 请按以下7段固定结构分析，只讲核心结论：

## 一、一句话核心结论
类型、能不能买、适合仓位、最大风险。

## 二、基础排雷（生死线）
1. 退市/ST/财务造假风险：有/无，依据
2. 盈利稳定性：连续盈利/强周期波动/亏损
3. 现金流与负债：现金流是否覆盖利润，有息负债是否安全

## 三、风格与属性定性
1. 标的类型（消费白马/科技成长/周期股等）
2. 核心赚什么钱（分红/业绩增长/估值修复/赛道景气度）
3. 组合定位（主力底仓/卫星进攻/纯投机）

## 四、核心价值判断
1. 护城河与定价权：核心壁垒，有无松动
2. 行业需求：底层需求趋势，赛道增减
3. 盈利增速：未来1-2年合理增速
4. 估值性价比：PE-TTM=%s，分位=%s%%，用PEG判断

## 五、涨跌边界与概率
1. 正常下跌空间：预估百分比，对应支撑位
2. 极端下跌空间：预估百分比，触发条件
3. 上涨空间与时间：短期/长期预期，上涨动力
4. 最可能走势：震荡慢涨/暴涨暴跌/横盘磨底

## 六、实操建议与纪律
1. 适配仓位：占总投资资金建议比例
2. 买入条件：满足什么再入手
3. 卖出/止损条件：（成长股禁止15%%一刀切！需结合历史波动设置分层预警+清仓线）
4. 绝对不能做的事

## 七、5秒决策速查
□ 无暴雷退市风险 □ 赛道长期有价值 □ 估值性价比匹配 □ 跌幅可承受 □ 符合能力圈

---

### 严格优化规则（必须纳入分析，不可遗漏）：

**规则1·规模时效与冲击**：优先使用最新季度规模数据，严禁用过时数据。若规模短期增幅超80%%，必须单独分析规模急速扩张对策略的冲击（策略容量、风格偏离、申赎冲击）。

**规则2·持有人结构**：必须分析持有人结构（散户占比/机构占比），评估不同持有人行为对净值波动的影响（散户占比高→情绪化申赎波动大；机构占比高→集中赎回风险）。

**规则3·成长赛道分层止损**：若为高波动成长股/科技股，禁止15%%一刀切止损。必须结合该股近3年历史最大回撤，设置分层预警线（如回撤20%%减半仓）+清仓线（如回撤35%%全清），每层说明触发条件。

**规则4·客观平衡**：既要充分揭示下行风险，也要客观写明该股对应行业/赛道/基本面的上行优势。结论不能单向悲观，必须同时给出「风险侧」和「机会侧」的均衡判断。

**规则5·禁止绝对化推断**：区分"该企业历史上没有跨行业扩张"与"该企业无法靠跨行业创造超额收益"两种不同表述。所有判断标注置信度，避免绝对化词汇（如"必然""绝对""永远"）。

---

### 精排评分（分析完成后，必须在末尾单独输出以下JSON块，不要放在markdown里）：
```json
{"refinedScore":85,"refinedRating":"强烈推荐","confidence":"高"}
```
评分规则：综合排雷(0-25分)+护城河(0-25分)+估值性价比(0-20分)+涨跌比(0-20分)+实操适合度(0-10分)，满分100。
refinedRating四选一：强烈推荐(≥80)、推荐(60-79)、观察(40-59)、回避(<40)。
confidence三选一：高/中/低。
""".formatted(name, code,
            price, pe, peQuantile, roe, grossMargin,
            intrinsicValue, safetyMargin, moatScore, moatTags,
            score, rating, industry, pe, peQuantile);
    }

    // ---------- 基金 ----------

    private String buildFundPrompt(Map<String, Object> d) {
        String name = str(d.get("name")); String code = str(d.get("code"));
        String category = str(d.get("category")); String nav = str(d.get("nav"));
        String return1y = str(d.get("return1y")); String return3y = str(d.get("return3y"));
        String fee = str(d.get("fee")); String score = str(d.get("score")); String rating = str(d.get("rating"));

        return """
你现在是一名遵循格雷厄姆价值投资+芒格护城河体系的投资分析师，严格按照「先排雷、再定性、最后算估值」的逆向逻辑分析。

请分析标的：%s（%s）

### 已有定量数据（由系统从天天基金实时排行采集）：
- 基金类型：%s
- 单位净值：%s
- 近1年收益率：%s%%
- 近3年收益率：%s%%
- 费率：%s
- 系统综合评分：%s · 系统评级：%s

### 请按以下7段固定结构分析：

## 一、一句话核心结论
类型、能不能买、适合仓位、最大风险。

## 二、基础排雷（生死线）
1. 基金经理稳定性：从业年限、完整牛熊经历、近期有无变更
2. 规模风险：【必须使用最新季度规模！】是否过大导致策略失效/过小有清盘风险；短期规模增幅超80%%必须单独分析冲击
3. 风格漂移风险：持仓是否与宣称策略一致
4. **持有人结构**：散户/机构占比，各自对净值波动的影响

## 三、风格与属性定性
1. 基金类型（指数/主动价值/主动成长/行业主题/QDII）
2. 核心赚什么钱（指数β/经理α/赛道β/债券票息）
3. 组合定位（主力底仓/卫星进攻/防御配置/纯投机）

## 四、核心价值判断
### 如果是指数基金/宽基：
- 对应指数PE-TTM、历史分位、合理估值区间、股息率、预期年化收益

### 如果是主动管理基金：
1. 基金经理水平：从业年限（务必核实！）、牛熊完整度、能力圈与持仓匹配度
2. 操作风格：持股集中度、换手率、长期持有还是频繁调仓
3. 持仓赛道：核心押注行业，赛道景气度（上行/见顶/下行）
4. 历史风控：近3年最大回撤、回撤修复能力
5. 行情匹配：该基金经理擅长什么行情（牛市进攻/熊市防御/震荡选股），客观写出优劣势

## 五、涨跌边界与概率
1. 正常回撤空间：预估百分比，对应市场条件
2. 极端回撤空间：预估百分比，触发条件
3. 上涨空间与时间：短期/长期预期，上涨动力
4. 最可能走势：震荡慢涨/暴涨暴跌/横盘磨底

## 六、实操建议与纪律
1. 适配仓位：占总投资资金建议比例
2. 买入条件
3. 卖出/止损条件：（若是成长赛道基金，禁止15%%一刀切！必须结合历史最大回撤设分层预警+清仓线）
4. 绝对不能做的事

## 七、5秒决策速查
□ 基金经理稳定可靠 □ 策略长期有效 □ 净值位置有性价比 □ 回撤可承受 □ 符合能力圈

---

### 严格优化规则（必须纳入分析）：

**规则1·规模时效与冲击**：必须调取最新季度规模数据，严禁用过时规模。规模短期增幅超80%%，须单独分析对基金策略的冲击。

**规则2·持有人结构**：分析散户/机构占比，评估申赎行为对净值波动的影响。

**规则3·成长赛道分层止损**：若为成长风格/行业主题基金，禁止15%%一刀切。结合历史最大回撤设分层预警+清仓线。

**规则4·客观平衡**：既要揭示下行风险，也要客观写明该基金经理擅长行情对应的优势。结论不能单向悲观。

**规则5·禁止绝对化**：区分"经理未跨行业投资"与"无法跨行业持续创造超额收益"两种表述。禁止"必然""绝对""永远"等词汇，所有判断标注置信度。

---

### 精排评分（分析完成后必须单独输出JSON，不放markdown内）：
```json
{"refinedScore":85,"refinedRating":"强烈推荐","confidence":"高"}
```
评分规则：基金经理(0-25分)+策略与风控(0-25分)+当前性价比(0-20分)+涨跌比(0-20分)+实操适合度(0-10分)，满分100。
refinedRating四选一：强烈推荐(≥80)、推荐(60-79)、观察(40-59)、回避(<40)。
confidence三选一：高/中/低。
""".formatted(name, code, category, nav, return1y, return3y, fee, score, rating);
    }

    // ================= util =================

    private String str(Object o) { return o == null ? "暂无" : o.toString().trim(); }
    private boolean isEmpty(Object o) { return o == null || o.toString().trim().isEmpty() || "(未配置)".equals(o.toString()); }
    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString().trim()); } catch (Exception e) { return 0; }
    }
}
