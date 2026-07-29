package com.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 两阶段筛选的第二阶段：对已通过定量筛选的标的，调用 DeepSeek V4 Pro 按严格7段模板做深度分析。
 * 缓存 60 分钟，按 code 缓存（股票和基金分开）。
 */
@Service
public class DeepAnalysisService {

    private final LlmClient llm;
    private final LlmConfigService llmCfg;
    private final RealScreenService realScreen;
    private final ObjectMapper mapper = new ObjectMapper();

    // 缓存: key="stock:600519" / "fund:001480", value={analysis, cachedAt, mode}
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheAt = new ConcurrentHashMap<>();
    private static final long TTL = 60 * 60 * 1000L; // 60分钟

    public DeepAnalysisService(LlmClient llm, LlmConfigService llmCfg, RealScreenService realScreen) {
        this.llm = llm;
        this.llmCfg = llmCfg;
        this.realScreen = realScreen;
    }

    /**
     * 股票深度分析
     */
    public Map<String, Object> analyzeStock(String code) {
        String key = "stock:" + code;
        Map<String, Object> cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cacheAt.getOrDefault(key, 0L) < TTL) {
            return cached;
        }
        // 从筛选列表中找到这只股票
        Map<String, Object> stock = findStock(code);
        if (stock == null) {
            return Map.of("error", "未找到该股票", "mode", "not_found");
        }
        Map<String, Object> result = doAnalyze("stock", stock);
        cache.put(key, result);
        cacheAt.put(key, System.currentTimeMillis());
        return result;
    }

    /**
     * 基金深度分析
     */
    public Map<String, Object> analyzeFund(String code) {
        String key = "fund:" + code;
        Map<String, Object> cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cacheAt.getOrDefault(key, 0L) < TTL) {
            return cached;
        }
        Map<String, Object> fund = findFund(code);
        if (fund == null) {
            return Map.of("error", "未找到该基金", "mode", "not_found");
        }
        Map<String, Object> result = doAnalyze("fund", fund);
        cache.put(key, result);
        cacheAt.put(key, System.currentTimeMillis());
        return result;
    }

    /**
     * 清除某只标的的缓存（用于强制刷新）
     */
    public void invalidate(String scene, String code) {
        String key = scene + ":" + code;
        cache.remove(key);
        cacheAt.remove(key);
    }

    // ================= private =================

    private Map<String, Object> findStock(String code) {
        try {
            return realScreen.stockList().stream()
                    .filter(s -> code.equals(s.get("code")))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> findFund(String code) {
        try {
            return realScreen.fundList("全部").stream()
                    .filter(f -> code.equals(f.get("code")))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> doAnalyze(String scene, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", data.get("name"));
        result.put("code", data.get("code"));

        // 获取激活的模型
        Map<String, Object> active = llmCfg.active();
        if (active == null || isEmpty(active.get("apiKey"))) {
            result.put("mode", "no_model");
            result.put("analysis", "⚠️ 未配置 AI 模型或未填写 API Key，请在「AI 设置」中配置 DeepSeek 或其他模型后再试。");
            return result;
        }

        try {
            String prompt = buildPrompt(scene, data);
            String reply = llm.chat(active, prompt);
            result.put("mode", "real");
            result.put("model", active.get("name") + " / " + active.get("model"));
            result.put("analysis", reply);
        } catch (Exception e) {
            result.put("mode", "error");
            result.put("analysis", "⚠️ AI 分析失败：" + e.getMessage() + "。请检查模型配置和网络连接。");
        }
        return result;
    }

    private String buildPrompt(String scene, Map<String, Object> d) {
        if ("stock".equals(scene)) {
            return buildStockPrompt(d);
        }
        return buildFundPrompt(d);
    }

    // ================= 股票提示词模板 =================

    private String buildStockPrompt(Map<String, Object> d) {
        String name = str(d.get("name"));
        String code = str(d.get("code"));
        String industry = str(d.get("industry"));
        String price = str(d.get("price"));
        String pe = str(d.get("pe"));
        String peQuantile = str(d.get("peQuantile"));
        String roe = str(d.get("roe"));
        String grossMargin = str(d.get("grossMargin"));
        String intrinsicValue = str(d.get("intrinsicValue"));
        String safetyMargin = str(d.get("safetyMargin"));
        String moatScore = str(d.get("moatScore"));
        String moatTags = d.get("moatTags") instanceof List ? String.join("、", (List<String>) d.get("moatTags")) : "";
        String score = str(d.get("score"));
        String rating = str(d.get("rating"));

        return """
        你现在是一名遵循格雷厄姆价值投资+芒格护城河体系的投资分析师，严格按照「先排雷、再定性、最后算估值」的逆向逻辑分析，禁止只谈收益不谈风险，禁止用价值股估值标准硬套成长股。

        请分析标的：%s（%s）

        ### 已有定量数据（由系统采集+格雷厄姆公式计算得出）：
        - 当前价格：%s 元
        - PE-TTM：%s
        - PE历史分位（在A股候选池中）：%s%%
        - ROE（净资产收益率）：%s%%
        - 毛利率：%s%%
        - 格雷厄姆内在价值估算：%s 元
        - 安全边际：%s%%
        - 护城河量化评分（0-100）：%s
        - 护城河标签：%s
        - 综合评分：%s
        - 系统评级：%s
        - 所属行业：%s

        ### 请按以下固定结构分析，只讲核心结论，不用冗余铺垫：

        ## 一、一句话核心结论
        用1句话直接说清：这是什么类型的标的，当前能不能买，适合什么仓位，最大风险是什么。

        ## 二、基础排雷（生死线，过不了直接判高风险）
        1. 有无退市/ST/财务造假风险：有/无，依据是什么
        2. 盈利稳定性：是连续盈利，还是亏损/强周期波动
        3. 现金流与负债：现金流是否覆盖利润，有息负债是否安全

        ## 三、风格与属性定性
        1. 标的类型：宽基指数 / 行业指数 / 消费白马 / 科技成长 / 周期股 / 主动价值基金 / 主动成长基金
        2. 核心赚什么钱：分红收益 / 业绩增长 / 估值修复 / 赛道景气度 / 基金经理选股
        3. 在投资组合中的定位：主力底仓 / 卫星进攻仓 / 纯投机品种

        ## 四、核心价值判断
        1. 护城河与定价权：核心壁垒是什么，有没有松动
        2. 行业需求：底层需求会不会消失，细分赛道是增是减
        3. 盈利增速：基于已有数据推算未来1-2年合理增速
        4. 估值性价比：PE-TTM=%s，分位=%s%%，用PEG（PE÷净利润增速）判断，不准单看PE绝对值

        ## 五、涨跌边界与概率
        1. 正常下跌空间：大概百分比，对应估值/逻辑支撑位
        2. 极端下跌空间：大概百分比，触发条件是什么（黑天鹅级别）
        3. 上涨空间与时间：短期/长期预期收益区间，核心上涨动力是什么
        4. 最可能的走势：震荡慢涨 / 暴涨暴跌 / 横盘磨底

        ## 六、实操建议与纪律
        1. 适配仓位：占总投资资金的建议比例
        2. 买入条件：满足什么情况再入手
        3. 卖出/止损条件：触发什么信号必须离场（逻辑破位+净值跌幅双重标准）
        4. 绝对不能做的事：比如不能定投、不能越跌越补、不能重仓等

        ## 七、5秒决策速查（打勾/打叉）
        □ 无暴雷退市风险
        □ 赛道/生意长期有价值
        □ 当前估值与性价比匹配
        □ 跌幅在可承受范围内
        □ 符合自身能力圈与仓位规划

        ### 强制禁令（必须遵守）
        1. 禁止用消费股、宽基的低PE标准，去判断科技成长股的估值高低
        2. 禁止只说收益不说风险，所有收益预期必须标注前提条件
        3. 禁止推荐将高波动成长标的作为主力定投标的
        4. 禁止用历史涨幅直接线性外推未来收益，必须说明逻辑前提
        """.formatted(name, code,
            price, pe, peQuantile, roe, grossMargin,
            intrinsicValue, safetyMargin, moatScore, moatTags,
            score, rating, industry,
            pe, peQuantile);
    }

    // ================= 基金提示词模板 =================

    private String buildFundPrompt(Map<String, Object> d) {
        String name = str(d.get("name"));
        String code = str(d.get("code"));
        String category = str(d.get("category"));
        String nav = str(d.get("nav"));
        String return1y = str(d.get("return1y"));
        String return3y = str(d.get("return3y"));
        String fee = str(d.get("fee"));
        String score = str(d.get("score"));
        String rating = str(d.get("rating"));

        return """
        你现在是一名遵循格雷厄姆价值投资+芒格护城河体系的投资分析师，严格按照「先排雷、再定性、最后算估值」的逆向逻辑分析，禁止只谈收益不谈风险，禁止用价值股估值标准硬套成长股。

        请分析标的：%s（%s）

        ### 已有定量数据（由系统从天天基金实时排行采集）：
        - 基金类型：%s
        - 单位净值：%s
        - 近1年收益率：%s%%
        - 近3年收益率：%s%%
        - 费率：%s
        - 综合评分：%s
        - 系统评级：%s

        ### 请按以下固定结构分析，只讲核心结论，不用冗余铺垫：

        ## 一、一句话核心结论
        用1句话直接说清：这是什么类型的基金，当前能不能买，适合什么仓位，最大风险是什么。

        ## 二、基础排雷（生死线，过不了直接判高风险）
        1. 基金经理稳定性：从业年限、是否经历完整牛熊、近期有无变更
        2. 规模风险：是否过大导致策略失效，是否过小有清盘风险
        3. 风格漂移风险：持仓是否与其宣称的投资策略一致

        ## 三、风格与属性定性
        1. 基金类型：指数基金 / 主动价值型 / 主动成长型 / 行业主题型 / QDII
        2. 核心赚什么钱：跟踪指数β / 基金经理α选股 / 行业赛道β / 债券票息
        3. 在投资组合中的定位：主力底仓 / 卫星进攻仓 / 防御配置 / 纯投机品种

        ## 四、核心价值判断
        ### 如果是指数基金/宽基：
        - 对应指数PE-TTM、历史分位、合理估值区间
        - 股息率、长期盈利增速、预期年化收益区间

        ### 如果是主动管理基金：
        1. 基金经理水平：从业年限、是否经历完整牛熊、能力圈与持仓是否匹配
        2. 操作风格：持股集中度高/低、换手率高/低、是长期持有还是频繁调仓
        3. 持仓赛道：核心押注什么行业，赛道景气度是上行、见顶还是下行
        4. 历史风控：近3年最大回撤、回撤修复能力

        ## 五、涨跌边界与概率
        1. 正常回撤空间：大概百分比，对应什么市场条件
        2. 极端回撤空间：大概百分比，触发条件是什么（黑天鹅级别）
        3. 上涨空间与时间：短期/长期预期收益区间，核心上涨动力是什么
        4. 最可能的走势：震荡慢涨 / 暴涨暴跌 / 横盘磨底

        ## 六、实操建议与纪律
        1. 适配仓位：占总投资资金的建议比例
        2. 买入条件：满足什么情况再入手（如大盘回调到多少、估值分位到多少）
        3. 卖出/止损条件：触发什么信号必须离场（基金经理变更+净值跌幅双重标准）
        4. 绝对不能做的事：比如不能一把梭、不能越跌越补、不能只看着涨买

        ## 七、5秒决策速查（打勾/打叉）
        □ 基金经理稳定可靠
        □ 策略逻辑长期有效
        □ 当前净值位置有性价比
        □ 回撤在可承受范围内
        □ 符合自身能力圈与仓位规划

        ### 强制禁令（必须遵守）
        1. 禁止用宽基指数的低PE标准，去判断行业主题基金的估值高低
        2. 禁止只说收益不说风险，所有收益预期必须标注前提条件
        3. 禁止推荐将高波动行业基金作为主力定投标的
        4. 禁止用近期收益率直接线性外推未来收益，必须说明逻辑前提
        5. 主动基金必须先分析基金经理，再分析持仓估值
        """.formatted(name, code,
            category, nav, return1y, return3y, fee, score, rating);
    }

    private String str(Object o) { return o == null ? "暂无" : o.toString().trim(); }
    private boolean isEmpty(Object o) { return o == null || o.toString().trim().isEmpty() || "(未配置)".equals(o.toString()); }
}
