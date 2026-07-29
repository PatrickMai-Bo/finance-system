package com.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 两阶段筛选的第二阶段：对已通过定量筛选的标的，调用 LLM 按严格7段模板做深度分析。
 * 含 11 条约束规则 + LLM 精排评分 JSON。
 * 缓存 60 分钟；精排批量分析后，点「详细分析」直接命中缓存，不再调用模型。
 */
@Service
public class DeepAnalysisService {

    private final LlmClient llm;
    private final LlmConfigService llmCfg;
    private final RealScreenService realScreen;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheAt = new ConcurrentHashMap<>();
    private static final long TTL = 60 * 60 * 1000L;

    private static final Pattern SCORE_PAT = Pattern.compile(
        "\\{\\s*\"refinedScore\"\\s*:\\s*(\\d+)\\s*,\\s*\"refinedRating\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"confidence\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");

    public DeepAnalysisService(LlmClient llm, LlmConfigService llmCfg, RealScreenService realScreen) {
        this.llm = llm;
        this.llmCfg = llmCfg;
        this.realScreen = realScreen;
    }

    /** 股票深度分析（优先走缓存，无缓存则调LLM） */
    public Map<String, Object> analyzeStock(String code) {
        return analyze("stock", code, () -> findStock(code));
    }

    /** 基金深度分析（优先走缓存） */
    public Map<String, Object> analyzeFund(String code) {
        return analyze("fund", code, () -> findFund(code));
    }

    private Map<String, Object> analyze(String scene, String code, java.util.function.Supplier<Map<String, Object>> finder) {
        String key = scene + ":" + code;
        Map<String, Object> cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cacheAt.getOrDefault(key, 0L) < TTL) return cached;
        Map<String, Object> data = finder.get();
        if (data == null) return Map.of("error", "未找到该标的", "mode", "not_found");
        Map<String, Object> result = doAnalyze(scene, data);
        cache.put(key, result); cacheAt.put(key, System.currentTimeMillis());
        return result;
    }

    public void invalidate(String scene, String code) { String k = scene + ":" + code; cache.remove(k); cacheAt.remove(k); }
    public void invalidateAll() { cache.clear(); cacheAt.clear(); }

    /**
     * 全量精排：并发深度分析所有标的 → 提取精排评分 → 重排。
     * force=true 清缓存全量重跑。分析结果自动入缓存，后续点「详细分析」秒出。
     */
    public List<Map<String, Object>> refinedStockList(boolean force) {
        try { return refinedList("stock", realScreen.stockList(), force); } catch (Exception e) { return List.of(); }
    }

    public List<Map<String, Object>> refinedFundList(String category, boolean force) {
        try { return refinedList("fund", realScreen.fundList(category), force); } catch (Exception e) { return List.of(); }
    }

    private List<Map<String, Object>> refinedList(String scene, List<Map<String, Object>> raw, boolean force) {
        if (force) invalidateAll();

        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            String code = String.valueOf(item.get("code"));
            String key = scene + ":" + code;
            futures.add(pool.submit(() -> {
                if (force) { cache.remove(key); cacheAt.remove(key); }
                // ★ 缓存命中：直接用缓存结果（精排分析过的标的不再调LLM）
                Map<String, Object> hit = cache.get(key);
                if (hit != null && System.currentTimeMillis() - cacheAt.getOrDefault(key, 0L) < TTL) return hit;
                // 未命中：调 LLM 并自动入缓存
                Map<String, Object> result = doAnalyze(scene, item);
                cache.put(key, result); cacheAt.put(key, System.currentTimeMillis());
                return result;
            }));
        }

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

        result.sort((a, b) -> Integer.compare(toInt(b.get("refinedScore")), toInt(a.get("refinedScore"))));
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
            extractScore(reply, result, data);
        } catch (Exception e) {
            result.put("mode", "error");
            result.put("analysis", "⚠️ AI 分析失败：" + e.getMessage());
            result.put("refinedScore", data.getOrDefault("score", 0));
            result.put("refinedRating", data.getOrDefault("rating", "观察"));
        }
        return result;
    }

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

    private String cleanAnalysis(String reply) {
        Matcher m = SCORE_PAT.matcher(reply);
        return m.find() ? reply.substring(0, m.start()).trim() : reply;
    }

    // ================= 提示词模板 =================

    private String buildPrompt(String scene, Map<String, Object> d) {
        return "stock".equals(scene) ? buildStockPrompt(d) : buildFundPrompt(d);
    }

    // ==================== 股票提示词 ====================

    private String buildStockPrompt(Map<String, Object> d) {
        String name = str(d.get("name")), code = str(d.get("code"));
        String industry = str(d.get("industry")), price = str(d.get("price"));
        String pe = str(d.get("pe")), peQuantile = str(d.get("peQuantile"));
        String roe = str(d.get("roe")), grossMargin = str(d.get("grossMargin"));
        String intrinsicValue = str(d.get("intrinsicValue")), safetyMargin = str(d.get("safetyMargin"));
        String moatScore = str(d.get("moatScore"));
        String moatTags = d.get("moatTags") instanceof List ? String.join("、", (List<String>) d.get("moatTags")) : "";
        String score = str(d.get("score")), rating = str(d.get("rating"));

        return """
你现在是一名遵循格雷厄姆价值投资+芒格护城河体系的投资分析师，严格按照「先排雷、再定性、最后算估值」的逆向逻辑分析。

请分析标的：%s（%s）

### 已有定量数据（由系统从东财业绩报表+腾讯实时行情采集，经格雷厄姆公式计算）：
- 当前价格：%s 元 | PE-TTM：%s | PE分位：%s%% | ROE：%s%%
- 毛利率：%s%% | 内在价值：%s 元 | 安全边际：%s%%
- 护城河评分：%s | 护城河标签：%s | 系统评分：%s · %s | 行业：%s

### 请按以下固定结构分析，只讲核心结论：

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
1. 正常下跌空间：预估百分比，支撑位（**必须用完整熊市历史极值，禁止截取局部区间**）
2. 极端下跌空间：预估百分比，触发条件
3. 上涨空间：短期/长期预期 + 核心上涨动力
   **所有长期收益预期必须写明前提假设，注明"如果发生XX风险，收益预期会下调"**
4. 最可能走势：震荡慢涨/暴涨暴跌/横盘磨底
   **以下涨跌情景概率仅为模型推演估算，不代表市场确定性预测**

## 六、实操建议与纪律
1. 适配仓位：占总投资资金建议比例
2. 买入条件：满足什么再入手
3. **止损体系必须双轨并行**：
   a) 净值分层价格止损（结合历史最大回撤设分层预警线+清仓线，成长股禁止15%%一刀切）
   b) 逻辑止损清单（至少包含：基本面恶化/行业景气度拐点/护城河松动）
4. 绝对不能做的事

## 七、5秒决策速查
□ 无暴雷退市风险 □ 赛道长期有价值 □ 估值性价比匹配 □ 跌幅可承受 □ 符合能力圈

---

### 严格约束规则（共11条，必须全部纳入分析）：

**规则1·规模时效**：优先最新季度规模数据，严禁过时数据。规模短期增幅超80%%须单独分析扩张冲击。

**规则2·持有人结构**：分析散户/机构占比对净值波动影响。

**规则3·分层止损**：成长股/高波动标的禁止15%%一刀切。结合完整熊市历史最大回撤，设分层预警+清仓线。

**规则4·客观平衡**：既要揭示下行风险，也要客观写明上行优势。结论不能单向悲观。

**规则5·禁止绝对化**：区分"未跨行业扩张"与"无法创造超额收益"。禁止"必然""绝对""永远"，标注置信度。

**规则6·止损双轨并行**：必须同时包含【净值分层价格止损】+【逻辑止损清单】。逻辑止损至少含：基本面恶化/赛道景气度拐点/护城河松动。

**规则7·最大回撤用完整熊市极值**：禁止截取局部区间缩小回撤数据、低估下行风险。

**规则8·持仓以最新季报为准**：判断当前赛道以最新季报重仓股/主营业务为准，禁止套用多年前历史标签。

**规则9·长期预期带前提**：所有长期收益预期必须写明前提假设，并注明"如果发生XX风险，收益预期会下调"。

**规则10·概率仅推演**：所有涨跌情景概率仅为模型推演估算，必须备注"不代表市场确定性预测"。

**规则11·区分从业年限与管理年限**：证券从业年限≠基金管理年限，不要混淆。

---

### 精排评分（分析完成后，末尾单独输出以下JSON块，不要放markdown里）：
```json
{"refinedScore":85,"refinedRating":"强烈推荐","confidence":"高"}
```
评分：排雷(0-25)+护城河(0-25)+估值性价比(0-20)+涨跌比(0-20)+实操适合度(0-10)，满分100。
refinedRating：强烈推荐(≥80)、推荐(60-79)、观察(40-59)、回避(<40)。
""".formatted(name, code,
            price, pe, peQuantile, roe, grossMargin,
            intrinsicValue, safetyMargin, moatScore, moatTags,
            score, rating, industry, pe, peQuantile);
    }

    // ==================== 基金提示词 ====================

    private String buildFundPrompt(Map<String, Object> d) {
        String name = str(d.get("name")), code = str(d.get("code"));
        String category = str(d.get("category")), nav = str(d.get("nav"));
        String return1y = str(d.get("return1y")), return3y = str(d.get("return3y"));
        String fee = str(d.get("fee")), score = str(d.get("score")), rating = str(d.get("rating"));

        return """
你现在是一名遵循格雷厄姆价值投资+芒格护城河体系的投资分析师，严格按照「先排雷、再定性、最后算估值」的逆向逻辑分析。

请分析标的：%s（%s）

### 已有定量数据（由系统从天天基金实时排行采集）：
- 基金类型：%s | 净值：%s | 近1年：%s%% | 近3年：%s%% | 费率：%s
- 系统评分：%s · %s

### 请按以下固定结构分析：

## 一、一句话核心结论
类型、能不能买、适合仓位、最大风险。

## 二、基础排雷（生死线）
1. 基金经理稳定性：【必须区分证券从业年限和管理基金年限！】从业几年？管基金几年？完整牛熊经历？近期有无变更？
2. 规模风险：最新季度规模（严禁用过期数据）。过大→策略失效？过小→清盘？增幅超80%%须单独分析冲击
3. 风格漂移：持仓以最新季报重仓股为准，禁止套用多年前标签
4. 持有人结构：散户/机构占比，各自对净值波动影响

## 三、风格与属性定性
1. 基金类型（指数/主动价值/主动成长/行业主题/QDII）
2. 核心赚什么钱（指数β/经理α/赛道β/债券票息）
3. 组合定位（主力底仓/卫星进攻/防御配置）

## 四、核心价值判断
### 指数基金/宽基：
- 对应指数PE-TTM、历史分位、合理估值区间、股息率、预期年化

### 主动管理基金：
1. 基金经理：证券从业年限___年，管理基金年限___年（两者必须区分写清！），牛熊完整度，能力圈与持仓匹配度
2. 操作风格：持股集中度、换手率、持有周期
3. 持仓赛道：【以最新季报重仓股为准】核心行业，景气度（上行/见顶/下行）
4. 历史风控：**完整熊市历史最大回撤极值（禁止截取局部区间）**、回撤修复能力
5. 行情匹配：擅长牛市进攻/熊市防御/震荡选股？客观写优劣势

## 五、涨跌边界与概率
1. 正常回撤空间：预估%%，对应市场条件（用完整熊市历史极值）
2. 极端回撤空间：预估%%，触发条件
3. 上涨空间：短期/长期预期 + 核心动力
   **所有长期收益预期必须写明前提假设，注明"如果发生XX风险，收益预期会下调"**
4. 最可能走势
   **以下涨跌情景概率仅为模型推演估算，不代表市场确定性预测**

## 六、实操建议与纪律
1. 适配仓位：建议比例
2. 买入条件
3. **止损体系必须双轨并行**：
   a) 净值分层价格止损（结合历史最大回撤设分层预警线+清仓线，成长风格禁止15%%一刀切）
   b) 逻辑止损清单（至少含：基金经理变更、赛道景气度拐点、规模短期暴涨策略失效）
4. 绝对不能做的事

## 七、5秒决策速查
□ 基金经理稳定 □ 策略长期有效 □ 净值有性价比 □ 回撤可承受 □ 符合能力圈

---

### 严格约束规则（共11条，必须全部纳入分析）：

**规则1·规模时效**：最新季度规模，严禁过期。短期增幅>80%%须单独分析策略冲击。

**规则2·持有人结构**：散户/机构占比，对净值波动的影响评估。

**规则3·分层止损**：成长风格/行业主题禁止15%%一刀切。结合完整熊市历史最大回撤设分层预警+清仓线。

**规则4·客观平衡**：风险侧和机会侧均衡，不能单向悲观。

**规则5·禁止绝对化**：区分"未跨行业投资"与"无法跨行业创造超额"。禁止"必然""绝对""永远"。

**规则6·止损双轨并行**：净值分层价格止损 + 逻辑止损。逻辑止损必含：基金经理变更、赛道景气度拐点、规模短期暴涨策略失效。

**规则7·最大回撤用完整熊市极值**：禁止截取局部区间缩小回撤数据。

**规则8·持仓以最新季报为准**：判断当前赛道以最新季报重仓股为准，禁止套用多年前标签。

**规则9·长期预期带前提**：所有长期收益预期必须写明前提假设，注明"如果发生XX风险，收益预期会下调"。

**规则10·概率仅推演**：所有涨跌情景概率仅为模型推演估算，备注"不代表市场确定性预测"。

**规则11·区分从业年限与管理年限**：证券从业年限 和 基金管理年限，两者必须分别写明，禁止混淆。

---

### 精排评分（分析完成后末尾单独输出JSON，不放markdown）：
```json
{"refinedScore":85,"refinedRating":"强烈推荐","confidence":"高"}
```
评分：经理(0-25)+策略风控(0-25)+性价比(0-20)+涨跌比(0-20)+实操适合度(0-10)，满分100。
refinedRating：强烈推荐(≥80)、推荐(60-79)、观察(40-59)、回避(<40)。
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
