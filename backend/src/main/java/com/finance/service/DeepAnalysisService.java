package com.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 两阶段筛选的第二阶段:对已通过定量筛选的标的,调用 LLM 按"价值投资+护城河"严格模板做深度分析。
 *
 * 核心要求(已并入提示词):
 *  1. 自动识别标的类型(A股个股 / 公募基金),按分支逻辑分析
 *  2. 身份:资深价值投资者,严格遵循《聪明的投资者》《穷查理宝典》投资理念
 *  3. 规则:先排雷,再谈收益;规避致命风险优先;不预测短期涨跌;侧重3年中长期判断
 *  4. 输出5段:【标的类型识别】→【风险排查总结】→【估值&基本面分析】→【核心风险汇总】→【最终投资评级+操作建议】
 *  5. 同时输出两块 JSON: 精排评分 + 建议持有时间(短期/中期/长期+持有时间+预计收益率+理由)
 *
 * 缓存 60 分钟;精排批量分析后,点「详细分析」直接命中缓存,不再调用模型。
 * 「建议持有时间」在精排阶段一并算出写入 row.advice,前端无需再独立调 advice 接口。
 */
@Service
public class DeepAnalysisService {

    private final LlmClient llm;
    private final LlmConfigService llmCfg;
    private final RealScreenService realScreen;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService pool = Executors.newFixedThreadPool(16);

    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheAt = new ConcurrentHashMap<>();
    private static final long TTL = 60 * 60 * 1000L;

    // 后台预热:避免冷缓存时列表接口同步跑 30 次 LLM 导致前端 30s 超时
    private final Set<String> warmScenes = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> warming = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "llm-warmup"); t.setDaemon(true); return t; });

    // 精排评分 JSON: {"refinedScore":85,"refinedRating":"强烈推荐","confidence":"高"}
    private static final Pattern SCORE_PAT = Pattern.compile(
        "\\{\\s*\"refinedScore\"\\s*:\\s*(\\d+)\\s*,\\s*\"refinedRating\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"confidence\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");

    // 建议持有时间 JSON: {"short":{"horizon":"...","returnRange":"...","logic":"..."},"mid":{...},"long":{...}}
    private static final Pattern ADVICE_PAT = Pattern.compile(
        "\\{\\s*\"short\"\\s*:\\s*\\{[^}]*\\}\\s*,\\s*\"mid\"\\s*:\\s*\\{[^}]*\\}\\s*,\\s*\"long\"\\s*:\\s*\\{[^}]*\\}\\s*\\}");

    public DeepAnalysisService(LlmClient llm, LlmConfigService llmCfg, RealScreenService realScreen) {
        this.llm = llm;
        this.llmCfg = llmCfg;
        this.realScreen = realScreen;
    }

    /** 股票深度分析(优先走缓存,无缓存则调LLM) */
    public Map<String, Object> analyzeStock(String code) {
        return analyze("stock", code, () -> findStock(code));
    }

    /** 基金深度分析(优先走缓存) */
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
     * 全量精排:并发深度分析所有标的 → 提取精排评分 + 建议持有时间 → 重排。
     * force=true 清缓存全量重跑。分析结果自动入缓存,后续点「详细分析」秒出。
     * 返回的 list 中每项包含:row原数据 + deepAnalysis + advice + refinedScore + refinedRating
     */
    public List<Map<String, Object>> refinedStockList(boolean force) {
        try { return refinedList("stock", realScreen.stockList(), force); } catch (Exception e) { return List.of(); }
    }

    public List<Map<String, Object>> refinedFundList(String category, boolean force) {
        try { return refinedList("fund", realScreen.fundList(category), force); } catch (Exception e) { return List.of(); }
    }

    /**
     * 全量精排核心。
     * - force=true:清缓存 + 同步全量重跑(手动「精排分析」按钮用,前端已设长超时)。
     * - force=false:若热缓存命中 → 直接合并排序(毫秒级);若冷缓存 → 立即返回定量结果(秒级),
     *   并在后台触发 LLM 精排预热,预热完成后下一次加载即为完整 AI 精排结果。
     * 这样列表接口永远不会因同步跑 30 次 LLM 而阻塞前端。
     */
    private List<Map<String, Object>> refinedList(String scene, List<Map<String, Object>> raw, boolean force) {
        if (force) {
            invalidateAll();
            return computeAndSort(raw, scene);
        }
        if (isWarm(raw, scene)) return mergeAndSort(raw, scene);
        // 冷缓存:先返回定量结果,后台预热
        triggerWarm(scene, raw);
        return quantOnly(raw, scene);
    }

    /** 启动即后台预热股票/基金精排,并每 50 分钟(早于 60 分钟 TTL)自动续热,确保列表几乎始终命中热缓存 */
    @PostConstruct
    public void init() {
        try { triggerWarm("stock", realScreen.stockList()); } catch (Exception ignore) {}
        try { triggerWarm("fund", realScreen.fundList("全部")); } catch (Exception ignore) {}
        scheduler.scheduleWithFixedDelay(() -> {
            try { triggerWarm("stock", realScreen.stockList()); } catch (Exception ignore) {}
            try { triggerWarm("fund", realScreen.fundList("全部")); } catch (Exception ignore) {}
        }, 50, 50, TimeUnit.MINUTES);
    }

    private boolean isWarm(List<Map<String, Object>> raw, String scene) {
        if (warmScenes.contains(scene)) {
            // 复核首条是否仍新鲜,防止 60min TTL 过期后仍误判为热
            if (!raw.isEmpty()) {
                String k0 = scene + ":" + raw.get(0).get("code");
                if (!(cache.containsKey(k0) && System.currentTimeMillis() - cacheAt.getOrDefault(k0, 0L) < TTL)) {
                    warmScenes.remove(scene);
                }
            }
        }
        if (warmScenes.contains(scene)) return true;
        if (raw.isEmpty()) return false;
        boolean all = raw.stream().allMatch(m -> {
            String k = scene + ":" + m.get("code");
            return cache.containsKey(k) && System.currentTimeMillis() - cacheAt.getOrDefault(k, 0L) < TTL;
        });
        if (all) warmScenes.add(scene);
        return all;
    }

    /** 后台触发精排预热(若已在预热则跳过);逐只调 LLM 并写入缓存,完成后标记 scene 为热 */
    private void triggerWarm(String scene, List<Map<String, Object>> raw) {
        if (warming.putIfAbsent(scene, true) != null) return;
        pool.submit(() -> {
            try {
                for (Map<String, Object> item : raw) {
                    String code = String.valueOf(item.get("code"));
                    String key = scene + ":" + code;
                    if (cache.containsKey(key) && System.currentTimeMillis() - cacheAt.getOrDefault(key, 0L) < TTL) continue;
                    try {
                        Map<String, Object> r = doAnalyze(scene, item, true);
                        cache.put(key, r); cacheAt.put(key, System.currentTimeMillis());
                    } catch (Exception ignore) { /* 单只失败不影响整体 */ }
                }
                warmScenes.add(scene);
            } finally { warming.remove(scene); }
        });
    }

    /** 热缓存命中:合并缓存里的 AI 分析 + 按 refinedScore 重排 */
    private List<Map<String, Object>> mergeAndSort(List<Map<String, Object>> raw, String scene) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            String k = scene + ":" + item.get("code");
            Map<String, Object> a = cache.get(k);
            Map<String, Object> m = new LinkedHashMap<>(item);
            if (a != null) {
                m.put("deepAnalysis", a.get("analysis"));
                m.put("deepMode", a.get("mode"));
                m.put("deepModel", a.get("model"));
                m.put("refinedScore", a.getOrDefault("refinedScore", item.getOrDefault("score", 0)));
                m.put("refinedRating", a.getOrDefault("refinedRating", item.getOrDefault("rating", "观察")));
                m.put("confidence", a.getOrDefault("confidence", ""));
                m.put("advice", a.getOrDefault("advice", buildRuleAdviceOnly(scene, item)));
            } else {
                m.put("refinedScore", item.getOrDefault("score", 0));
                m.put("refinedRating", item.getOrDefault("rating", "观察"));
                m.put("advice", buildRuleAdviceOnly(scene, item));
            }
            result.add(m);
        }
        result.sort((a, b) -> Integer.compare(toInt(b.get("refinedScore")), toInt(a.get("refinedScore"))));
        for (int i = 0; i < result.size(); i++) result.get(i).put("refinedRank", i + 1);
        return result;
    }

    /** 冷缓存:立即返回定量结果(按安全边际+护城河排序),标记 aiPending,等后台预热完成 */
    private List<Map<String, Object>> quantOnly(List<Map<String, Object>> raw, String scene) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            Map<String, Object> m = new LinkedHashMap<>(item);
            m.put("refinedScore", item.getOrDefault("score", 0));
            m.put("refinedRating", item.getOrDefault("rating", "观察"));
            m.put("advice", buildRuleAdviceOnly(scene, item));
            m.put("deepAnalysis", null);
            m.put("aiPending", true);
            result.add(m);
        }
        result.sort((a, b) -> Double.compare(
                num(b.get("safetyMargin")) + num(b.get("moatScore")),
                num(a.get("safetyMargin")) + num(a.get("moatScore"))));
        for (int i = 0; i < result.size(); i++) result.get(i).put("refinedRank", i + 1);
        return result;
    }

    /** force=true 时同步全量重跑(手动按钮用) */
    private List<Map<String, Object>> computeAndSort(List<Map<String, Object>> raw, String scene) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            String code = String.valueOf(item.get("code"));
            String key = scene + ":" + code;
            futures.add(pool.submit(() -> {
                cache.remove(key); cacheAt.remove(key);
                Map<String, Object> r = doAnalyze(scene, item, true);
                cache.put(key, r); cacheAt.put(key, System.currentTimeMillis());
                return r;
            }));
        }
        for (int i = 0; i < futures.size(); i++) {
            Map<String, Object> a;
            try {
                a = futures.get(i).get(12, TimeUnit.SECONDS);
            } catch (Exception e) {
                a = Map.of("analysis", "⚠️ 分析超时或失败", "mode", "error",
                        "refinedScore", raw.get(i).getOrDefault("score", 0),
                        "refinedRating", raw.get(i).getOrDefault("rating", "观察"),
                        "advice", buildRuleAdviceOnly(scene, raw.get(i)));
            }
            Map<String, Object> m = new LinkedHashMap<>(raw.get(i));
            m.put("deepAnalysis", a.get("analysis"));
            m.put("deepMode", a.get("mode"));
            m.put("deepModel", a.get("model"));
            m.put("refinedScore", a.getOrDefault("refinedScore", raw.get(i).getOrDefault("score", 0)));
            m.put("refinedRating", a.getOrDefault("refinedRating", raw.get(i).getOrDefault("rating", "观察")));
            m.put("confidence", a.getOrDefault("confidence", ""));
            m.put("advice", a.getOrDefault("advice", buildRuleAdviceOnly(scene, raw.get(i))));
            result.add(m);
        }
        warmScenes.add(scene);
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
        return doAnalyze(scene, data, false);
    }

    /**
     * 单只 AI 分析。
     * lite=true:列表/预热专用,只要求模型输出「精排评分+建议持有时间」两个 JSON(不生成 5 段长文),
     *   响应极短,单只 5-10s,30 只并发预热可在 30s 内完成(否则全量长文单只 40s,预热要 1-2 分钟)。
     * lite=false:详细分析专用,生成完整 5 段长文+两个 JSON(点「详细分析/强制刷新」时按需调用,结果缓存)。
     */
    private Map<String, Object> doAnalyze(String scene, Map<String, Object> data, boolean lite) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", data.get("name"));
        result.put("code", data.get("code"));
        Map<String, Object> active = llmCfg.active();
        if (active == null || isEmpty(active.get("apiKey"))) {
            result.put("mode", "no_model");
            result.put("analysis", "⚠️ 未配置 AI 模型或未填写 API Key");
            result.put("refinedScore", data.getOrDefault("score", 0));
            result.put("refinedRating", data.getOrDefault("rating", "观察"));
            result.put("advice", buildRuleAdviceOnly(scene, data));
            return result;
        }
        try {
            String prompt = lite ? buildLitePrompt(scene, data) : buildPrompt(scene, data);
            String reply = llm.chat(active, prompt);
            result.put("mode", "real");
            result.put("model", active.get("name") + " / " + active.get("model"));
            result.put("analysis", lite ? "" : cleanAnalysis(reply));
            result.put("lite", lite);
            extractScore(reply, result, data);
            extractAdvice(reply, result, scene, data);
        } catch (Exception e) {
            result.put("mode", "error");
            result.put("analysis", "⚠️ AI 分析失败:" + e.getMessage());
            result.put("refinedScore", data.getOrDefault("score", 0));
            result.put("refinedRating", data.getOrDefault("rating", "观察"));
            result.put("advice", buildRuleAdviceOnly(scene, data));
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

    /** 从 LLM 回复中提取「建议持有时间」JSON;解析失败回退到规则估算,保证列不空 */
    private void extractAdvice(String reply, Map<String, Object> result, String scene, Map<String, Object> data) {
        Matcher m = ADVICE_PAT.matcher(reply);
        if (m.find()) {
            try {
                JsonNode n = mapper.readTree(m.group());
                Map<String, Object> adv = new LinkedHashMap<>();
                adv.put("code", data.get("code"));
                adv.put("short", segFromJson(n.path("short")));
                adv.put("mid", segFromJson(n.path("mid")));
                adv.put("long", segFromJson(n.path("long")));
                adv.put("mode", "real");
                adv.put("model", result.get("model"));
                result.put("advice", adv);
                return;
            } catch (Exception ignore) {}
        }
        result.put("advice", buildRuleAdviceOnly(scene, data));
    }

    private Map<String, Object> segFromJson(JsonNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("horizon", n.path("horizon").asText(""));
        m.put("returnRange", n.path("returnRange").asText(""));
        m.put("logic", n.path("logic").asText(""));
        return m;
    }

    private String cleanAnalysis(String reply) {
        // 把两块 JSON 都剥离掉,只留 5 段分析正文
        String s = reply;
        Matcher s1 = SCORE_PAT.matcher(s);
        if (s1.find()) s = s.substring(0, s1.start()) + s.substring(s1.end());
        Matcher s2 = ADVICE_PAT.matcher(s);
        if (s2.find()) s = s.substring(0, s2.start()) + s.substring(s2.end());
        return s.trim();
    }

    // ================= 提示词模板(用户最新要求版) =================

    private String buildPrompt(String scene, Map<String, Object> d) {
        return "stock".equals(scene) ? buildStockPrompt(d) : buildFundPrompt(d);
    }

    /** 列表/预热专用精简 prompt:只要求输出两个 JSON,不生成 5 段长文,响应极短、速度快 */
    private String buildLitePrompt(String scene, Map<String, Object> d) {
        String codeName = str(d.get("code")) + "(" + str(d.get("name")) + ")";
        String metrics = "stock".equals(scene)
                ? String.format("价格:%s PE-TTM:%s ROE:%s%% 毛利率:%s%% 安全边际:%s%% 护城河:%s 系统评分:%s·%s 行业:%s",
                    str(d.get("price")), str(d.get("pe")), str(d.get("roe")), str(d.get("grossMargin")),
                    str(d.get("safetyMargin")), str(d.get("moatScore")), str(d.get("score")), str(d.get("rating")), str(d.get("industry")))
                : String.format("类型:%s 近1年:%s%% 近3年:%s%% 费率:%s 规模:%s 系统评分:%s·%s",
                    str(d.get("category")), str(d.get("return1y")), str(d.get("return3y")), str(d.get("fee")), str(d.get("scale")), str(d.get("score")), str(d.get("rating")));
        return """
        身份:资深价值投资者。标的:%s
        已有定量数据:%s
        请严格只输出以下两个 JSON 块,不要输出任何分析正文:
        1. 精排评分: {"refinedScore":85,"refinedRating":"强烈推荐","confidence":"高"}
           评分=排雷(0-25)+护城河(0-25)+估值性价比(0-20)+涨跌比(0-20)+实操适合度(0-10)满分100;
           refinedRating:强烈推荐(≥80) 推荐(60-79) 观察(40-59) 回避(<40)
        2. 建议持有时间: {"short":{"horizon":"3-6个月","returnRange":"+5%~+15%","logic":"一句话理由"},"mid":{"horizon":"1-2年","returnRange":"+10%~+25%","logic":"一句话理由"},"long":{"horizon":"3年以上","returnRange":"+20%~+40%","logic":"一句话理由"}}
        收益区间必须保守、给区间,不得与上方已知数据矛盾。
        """.formatted(codeName, metrics);
    }

    /**
     * 共享前缀:身份 + 规则 + 输入说明 + 输出框架 + 硬性要求 + 末尾 JSON
     */
    private String sharedHead(String codeName) {
        return """
        身份:资深价值投资者,严格遵循《聪明的投资者》、《穷查理宝典》投资理念。
        规则:首先自动识别标的类型:A股个股 / 混合型/指数公募基金。
        核心准则:先排雷,再谈收益;规避致命风险优先,不预测短期涨跌,侧重3年维度中长期判断。

        硬性要求:
        1. 禁止单纯乐观唱多,必须同时客观列出利空;
        2. 不预测短期几天、几个月涨跌;判断周期锁定3年中长期;
        3. 输出结构清晰,使用标题分段;
        4. 最终输出格式:先【标的类型识别】→【风险排查总结】→【估值&基本面分析】→【核心风险汇总】→【最终投资评级+操作建议】

        输入标的:%s
        """.formatted(codeName);
    }

    private String sharedTail() {
        return """

        ---
        ### 分析完成后,末尾必须单独输出两个 JSON 块(不放进 markdown 文档里,仅末尾追加):

        第一个:精排评分
        ```json
        {"refinedScore":85,"refinedRating":"强烈推荐","confidence":"高"}
        ```
        评分:排雷(0-25)+护城河/经理(0-25)+估值性价比(0-20)+涨跌比(0-20)+实操适合度(0-10),满分100。
        refinedRating:强烈推荐(≥80)、推荐(60-79)、观察(40-59)、回避(<40)。

        第二个:建议持有时间(短期/中期/长期)
        ```json
        {"short":{"horizon":"3-6个月","returnRange":"+5%~+15%","logic":"一句话理由"},"mid":{"horizon":"1-2年","returnRange":"+10%~+25%","logic":"一句话理由"},"long":{"horizon":"3年以上","returnRange":"+20%~+40%","logic":"一句话理由"}}
        ```
        收益区间必须保守、给区间;不得与安全边际/历史业绩明显矛盾。
        """;
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
        String codeName = code + "(" + name + ")";

        String body = """
        ### 已有定量数据(由系统从东财业绩报表+腾讯实时行情采集,经格雷厄姆公式计算):
        - 当前价格:%s 元 | PE-TTM:%s | PE 分位:%s%% | ROE:%s%%
        - 毛利率:%s%% | 内在价值:%s 元 | 安全边际:%s%%
        - 护城河评分:%s | 护城河标签:%s | 系统评分:%s · %s | 行业:%s

        ### 情况A:标的为 A 股个股
        #### 1. 基础排雷(最先检查,出现重大风险直接降低评级)
        ①是否 ST/*ST、有无退市风险;
        ②近三年是否财务暴雷、造假丑闻;
        ③连续净利润亏损情况;
        ④行业政策利空风险。

        #### 2. 估值分析(核心指标 PE-TTM、PEG、PB)
        对比近 5/10 年历史估值分位;区分成长股、价值股估值标准;客观说明估值高低,不单一依靠估值下定论。

        #### 3. 基本面三层检验
        ①行业:长期需求空间、行业景气度、潜在替代品风险;
        ②公司护城河:护城河强弱、是否逐年弱化;竞争格局;
        ③盈利:ROE 稳定性、净利润增速、现金流健康度。

        #### 4. 风险清单
        列出所有可见利空、潜在隐患。

        #### 5. 结论分级
        【优先建仓 / 观望等待估值回落 / 规避不考虑】
        附带建议:适合小仓位试错 or 不适合入场,参考安全边际。
        """.formatted(price, pe, peQuantile, roe, grossMargin,
                intrinsicValue, safetyMargin, moatScore, moatTags,
                score, rating, industry);

        return sharedHead(codeName) + body + sharedTail();
    }

    // ==================== 基金提示词 ====================

    private String buildFundPrompt(Map<String, Object> d) {
        String name = str(d.get("name")), code = str(d.get("code"));
        String category = str(d.get("category")), nav = str(d.get("nav"));
        String return1y = str(d.get("return1y")), return3y = str(d.get("return3y"));
        String fee = str(d.get("fee")), score = str(d.get("score")), rating = str(d.get("rating"));
        String codeName = code + "(" + name + ")";

        String body = """
        ### 已有定量数据(由系统从天天基金实时排行采集):
        - 基金类型:%s | 净值:%s | 近1年:%s%% | 近3年:%s%% | 费率:%s
        - 系统评分:%s · %s

        ### 情况B:标的为公募基金(主动/指数基金)
        #### 1. 基础排雷
        ①基金规模异常(过小清盘风险);
        ②基金经理任职年限(必须区分证券从业年限 和 基金管理年限,禁止混淆);
        ③有无频繁更换基金经理;
        ④历史重大回撤(以完整熊市历史极值为准,禁止截取局部区间缩小);
        ⑤持仓是否高度集中。

        #### 2. 持仓分析
        ①前十大重仓风格、重仓行业;
        ②整体持仓估值水平(重仓个股 PE、PEG 综合水平);
        ③主动基金额外重点:基金经理投资风格是否长期稳定、是否追热点、换手率特征;
        ④指数基金额外重点:对应指数历史估值分位。

        #### 3. 收益与风险
        ①中长期业绩;
        ②最大回撤(完整熊市极值);
        ③波动特征。

        #### 4. 风险清单

        #### 5. 结论分级
        【优先建仓 / 观望等待估值回落 / 规避不考虑】
        附带定投 / 一次性小仓建议。
        """.formatted(category, nav, return1y, return3y, fee, score, rating);

        return sharedHead(codeName) + body + sharedTail();
    }

    // ================= 规则兜底(无 LLM 或 LLM 失败时,保证「建议持有时间」列不空) =================

    private Map<String, Object> buildRuleAdviceOnly(String scene, Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", row.get("code"));
        double margin = num(row.get("safetyMargin"));
        double y3 = num(row.get("return3y"));
        double shortR, midR, longR;
        if ("fund".equals(scene) && y3 > 0) {
            shortR = Math.min(30, y3 * 0.2);
            midR = Math.min(60, y3 * 0.5);
            longR = Math.min(120, y3);
        } else {
            shortR = margin * 0.5;
            midR = margin;
            longR = margin * 1.5;
        }
        r.put("short", seg("3-6个月", pct(shortR), "规则估算(非AI)"));
        r.put("mid", seg("1-2年", pct(midR), "规则估算(非AI)"));
        r.put("long", seg("3年以上", pct(longR), "规则外的简单外推"));
        r.put("mode", "rule");
        return r;
    }

    private Map<String, Object> seg(String h, String rr, String logic) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("horizon", h);
        m.put("returnRange", rr);
        m.put("logic", logic);
        return m;
    }

    private String pct(double v) {
        String sign = v >= 0 ? "+" : "";
        return sign + Math.round(v) + "%";
    }

    // ================= util =================

    private String str(Object o) { return o == null ? "暂无" : o.toString().trim(); }
    private boolean isEmpty(Object o) { return o == null || o.toString().trim().isEmpty() || "(未配置)".equals(o.toString()); }
    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString().trim()); } catch (Exception e) { return 0; }
    }
    private double num(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }
}
