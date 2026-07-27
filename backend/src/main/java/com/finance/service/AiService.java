package com.finance.service;

import org.springframework.stereotype.Service;

import com.finance.entity.DecisionLog;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 统一 AI 服务(全局打通板块一~四)。
 * 各板块只传 scene + 业务数据,这里读当前激活模型 → 组装对应 prompt → 调 OpenAI 兼容接口。
 * 已接真实调用:激活模型配置了 API Key 即走真实大模型(DeepSeek/阿里百炼等);
 * 未配置 Key 或调用失败时回退演示模式并明确标注。
 */
@Service
public class AiService {

    private final LlmConfigService llm;
    private final LlmClient client;
    private final LedgerService ledger;
    private final HoldingService holding;
    private final DecisionLogService decisionLogService;

    public AiService(LlmConfigService llm, LlmClient client, LedgerService ledger, HoldingService holding, DecisionLogService decisionLogService) {
        this.llm = llm;
        this.client = client;
        this.ledger = ledger;
        this.holding = holding;
        this.decisionLogService = decisionLogService;
    }

    public Map<String, Object> analyze(String scene, Map<String, Object> payload) {
        Map<String, Object> active = llm.active();
        String modelName = active == null ? "(未配置模型)" : (active.get("name") + " / " + active.get("model"));

        // decision 场景:①自动注入用户真实个人财务数据;②检索历史相似问答 prepend 红色警示
        List<DecisionLog> history = new ArrayList<>();
        String decisionQuestion = null;
        String decisionScene = null;
        if ("decision".equals(scene)) {
            if (payload == null) payload = new LinkedHashMap<>();
            decisionQuestion = String.valueOf(payload.getOrDefault("decision", ""));
            decisionScene = String.valueOf(payload.getOrDefault("scene", ""));
            Map<String, Object> fin = new LinkedHashMap<>();
            try { fin.put("ledgerSummary", ledger.summary()); } catch (Exception e) { fin.put("ledgerError", e.getMessage()); }
            try { fin.put("holdingSummary", holding.summary()); } catch (Exception e) { fin.put("holdingError", e.getMessage()); }
            payload.put("finance", fin);
            try { history = decisionLogService.findSimilar(decisionQuestion, decisionScene, 3); } catch (Exception ignore) {}
            payload.put("history", history);
        }

        String prompt = buildPrompt(scene, payload);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scene", scene);
        result.put("model", modelName);
        result.put("prompt", prompt);
        result.put("historyHits", history.size());

        String apiKey = active == null ? "" : String.valueOf(active.getOrDefault("apiKey", "")).trim();
        String rawAnalysis = null;
        String mode;
        if (active == null || apiKey.isEmpty()) {
            mode = "mock";
            rawAnalysis = mockAnalysis(scene, payload)
                    + "\n\n💡 当前为演示分析。到「AI 深度分析」面板为 " + (active == null ? "模型" : active.get("name"))
                    + " 填入 API Key 后,这里将自动切换为真实大模型分析。";
        } else {
            try {
                rawAnalysis = client.chat(active, prompt);
                mode = "real";
            } catch (Exception e) {
                mode = "mock";
                rawAnalysis = mockAnalysis(scene, payload)
                        + "\n\n⚠️ 真实模型调用失败,已回退演示分析。原因: " + e.getMessage();
            }
        }
        result.put("mode", mode);

        // decision 场景:若历史上问过类似问题,在内容第一行 prepend 红色警示
        String finalAnalysis = rawAnalysis;
        if ("decision".equals(scene) && !history.isEmpty()) {
            finalAnalysis = buildHistoryWarning(history) + rawAnalysis;
        }
        result.put("analysis", finalAnalysis);

        // decision 场景:落库(失败不影响主流程)
        if ("decision".equals(scene) && decisionQuestion != null && !decisionQuestion.isEmpty()) {
            try {
                DecisionLog log = new DecisionLog();
                log.setScene(decisionScene);
                log.setQuestion(decisionQuestion);
                log.setAnswer(rawAnalysis);
                log.setModel(modelName);
                log.setVerdict(decisionLogService.parseVerdict(rawAnalysis));
                decisionLogService.save(log);
            } catch (Exception ignore) {}
        }

        result.put("disclaimer", "本分析由 AI 基于所提供数据生成,仅供参考,非投资建议,最终决策请自行判断。");
        result.put("createdAt", new Date().toString());
        return result;
    }

    /** 构建红色警示块(顶部 prepend),MarkdownView 渲染时重点加粗会高亮红色 */
    private String buildHistoryWarning(List<DecisionLog> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("!!! 🔴 **警告:你曾问过类似问题,要记得避免重复犯错!!!**\n\n");
        sb.append("---\n\n");
        sb.append("**📌 历史相似问答(供参考,记得避开重复犯错)**\n\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (DecisionLog h : history) {
            String q = h.getQuestion() != null && h.getQuestion().length() > 50
                    ? h.getQuestion().substring(0, 50) + "..."
                    : h.getQuestion();
            sb.append("- 📅 ").append(sdf.format(h.getCreatedAt()))
              .append(" · 问题:").append(q == null ? "" : q)
              .append(" · 当时建议:**").append(h.getVerdict() != null ? h.getVerdict() : "见详情").append("**\n");
        }
        sb.append("\n---\n\n");
        sb.append("**⬇️ 以下是本次 AI 五阶分析**\n\n");
        return sb.toString();
    }

    private String buildPrompt(String scene, Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder();
        switch (scene) {
            case "stock":
            case "fund":
                sb.append("你是价值投资分析师,请基于以下真实数据,从护城河可持续性、财务健康度(现金流/负债/利润)、")
                  .append("DCF估值参数保守性、安全边际是否充足四个维度分析该标的,并给出保守的买入区间建议。数据:");
                break;
            case "stock-batch":
                sb.append("你是价值投资顾问。用户从筛选结果中**勾选了多只股票**,希望你来一次**横向深度对比分析**。\n")
                  .append("请基于下面这几只股票的真实指标,结构化输出(用 Markdown,前端会渲染 ### 标题 / - 列表 / **重点加粗**):\n")
                  .append("- 每只的核心特征(护城河/估值/财务,各三句话点评)\n")
                  .append("- **相对优劣对比**(谁更便宜、谁更稳、谁成长更强)\n")
                  .append("- **组合配置建议**(仓位如何分配,理由)\n")
                  .append("- **共同的风险点**与**对比的风险差异**\n")
                  .append("- 一句话最终建议(优先买哪只 / 分散哪几只 / 哪只应剔除及理由)。\n")
                  .append("数据(数组):");
                break;
            case "fund-batch":
                sb.append("你是基金研究顾问。用户从筛选结果中**勾选了多只基金**,希望你来一次**横向深度对比分析**。\n")
                  .append("请基于下面这几只基金的真实指标,结构化输出(用 Markdown,前端会渲染 ### 标题 / - 列表 / **重点加粗**):\n")
                  .append("- 每只的核心特征(类型/历史业绩/费率/规模,各三句话点评)\n")
                  .append("- **相对优劣对比**(谁收益更强、谁回撤更小、谁费率更低)\n")
                  .append("- **组合配置建议**(核心仓 vs 卫星,仓位如何分配)\n")
                  .append("- **共同的风险点**与**对比的风险差异**\n")
                  .append("- 一句话最终建议(优先选哪只 / 分散哪几只 / 哪只应剔除及理由)。\n")
                  .append("数据(数组):");
                break;
            case "finance":
                sb.append("你是家庭财务规划师,请基于资产/负债、被动收入理念,")
                  .append("点评以下资产负债结构与现金流健康度,给出财务自由路径建议。数据:");
                break;
            case "holding":
                sb.append("你是家庭财务规划师。以下是用户自行填写的『存量资产负债表』(真资产=能生钱、伪资产=自用消耗、")
                  .append("投资性负债=借钱买生钱资产、消费性负债=为消费背债)。请按资产/负债口径:")
                  .append("①判断净资产与真资产占比是否健康;②指出哪些伪资产/消费性负债在拖累现金流;")
                  .append("③给出把伪资产结余转化为生钱资产、优先偿还坏负债的具体路径。存量数据:");
                break;
            case "watchlist":
                sb.append("你是价值投资顾问。以下是用户的自选清单(自选股或自选基及其成本、持仓、目标价)。请基于")
                  .append("能力圈与逆向思维、安全边际与分散原则,对这份组合做整体点评:")
                  .append("①集中度与分散是否合理;②各标的相对成本/目标价的安全边际;③加减仓与再平衡建议。自选数据:");
                break;
            case "decision":
                sb.append("你是一位融合价值投资、逆向思维与财务智慧的决策顾问。\n")
                  .append("用户已经写下了他要做的决策,请严格按『五阶避错思维框架』,**由你来分析回答**(而不是继续追问用户)。\n")
                  .append("**关键**:系统已注入『该用户的真实个人财务数据』(本月主动/被动收入、月支出、净资产、真伪资产构成、总负债、财务自由度覆盖率等)。\n")
                  .append("请在每一阶分析中**主动引用这些具体数字**,让建议贴合用户实际承受能力(如『你净资产仅 X 万,占可承受风险比例...』『当前被动收入仅覆盖支出 X%,额外月供会拉低覆盖率到...%』)。\n")
                  .append("请用清晰的 Markdown 结构输出(前端会用 ### 标题 / - 列表 / **重点加粗** 等样式渲染):\n\n")
                  .append("### ① 逆向排雷 — 最坏会怎样?有无不可逆致命后果?\n")
                  .append("从最坏情况出发,指出这件事如果失败,可能踩到的坑和不可承受的代价(**结合用户的财务承受能力**)。\n\n")
                  .append("### ② 价值定性 — 这是资产还是负债?\n")
                  .append("用增值/消耗的标尺,定性这个选择长期是往口袋里装钱还是从口袋里拿钱(**对比用户的真资产占比与现金流**)。\n\n")
                  .append("### ③ 能力圈校验 — 这是不是真懂?\n")
                  .append("判断用户是否在自己真正理解的范围内下注(『能不能用自己的话讲清它的原理和风险』)。\n\n")
                  .append("### ④ 替代方案 — 有没有更好的选择(含『不做』)?\n")
                  .append("列出至少一个更好的备选方案(如『把 X 万首付转为高股息资产可产生 Y 元/月被动收入,相当于覆盖你 Z% 的缺口』),或说明『不做』为什么更优。\n\n")
                  .append("### ⑤ 情绪冷却 — 是否在冲动下做决定?\n")
                  .append("指出情绪裹挟的迹象(FOMO/恐慌/推销/从众等),以及冷静后再看是否会做同样决定。\n\n")
                  .append("### 🎯 最终建议\n")
                  .append("明确给出 **『做 / 不做 / 再等等』** 的结论,并用 2-3 句话**讲清楚为什么这么建议**(引用上述五阶的关键发现,**结合用户的真实财务数据**)。\n\n")
                  .append("用户的决策内容与场景,以及系统注入的真实个人财务数据如下:");
                break;
            default:
                sb.append("请分析以下内容:");
        }
        sb.append(payload == null ? "{}" : payload.toString());
        return sb.toString();
    }

    private String mockAnalysis(String scene, Map<String, Object> payload) {
        String name = payload != null && payload.get("name") != null ? payload.get("name").toString() : "该标的";
        switch (scene) {
            case "stock":
                return "【护城河】" + name + "具备较强品牌与规模壁垒,毛利率长期稳定,短期内竞争格局难以被颠覆。\n"
                     + "【财务健康】经营现金流充沛,负债率处于健康区间,利润质量高。\n"
                     + "【DCF参数】当前采用 WACC 10%、永续增长 2.5% 的保守假设合理,若下调增速仍有安全边际。\n"
                     + "【安全边际】现价低于内在价值,具备一定容错空间,可分批建仓,勿一次打满。\n"
                     + "(接入真实模型后,将结合最新年报原文做护城河可持续性与财务排雷的深度校验)";
            case "fund":
                return "该基金估值处于历史低位,底层资产分散,适合作为长期定投核心仓;注意跟踪误差与费率。";
            case "finance":
                return "你的真资产占比健康,但被动收入覆盖支出仅约 37%,建议逐步提升高股息与出租类真资产,压降高息负债(信用卡)。";
            case "holding":
                return "【净资产】家底结构基本健康,但自住房等伪资产占比偏高,真正生钱的资产比例仍有提升空间。\n"
                     + "【现金流】出租房、分红基金等真资产已贡献稳定被动流入,但尚不足以完全覆盖房贷等持有性支出。\n"
                     + "【负债】信用卡属高息消费性负债,应优先偿还;自住房房贷为低息,可从容处理。\n"
                     + "【路径】把每月结余持续转入高股息/出租类真资产,目标让被动流入逐步覆盖全部持有性支出。\n"
                     + "(接入真实模型后,将结合你填写的每一项做更精细的资产结构与偿债优先级测算)";
            case "watchlist":
                return "【集中度】自选整体偏向消费与宽基,方向稳健;单一个股占比若过高需留意波动。\n"
                     + "【安全边际】部分标的现价已接近目标价,安全边际收窄,不宜追高;可等回调分批。\n"
                     + "【再平衡】建议维持核心宽基+高股息的哑铃结构,单只个股仓位设上限,定期再平衡。\n"
                     + "(接入真实模型后,将结合实时行情与你的成本价给出更精确的加减仓区间)";
            case "decision":
                return "先做逆向推演最坏情形并确认可承受;用资产/负债标尺判断长期增值性;确保留有安全边际;对比机会成本后再行动。";
            default:
                return "已收到数据,接入真实模型后给出深度分析。";
        }
    }
}
