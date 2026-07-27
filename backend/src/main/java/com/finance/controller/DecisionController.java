package com.finance.controller;

import com.finance.common.R;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 板块四:决策思维系统
 * 核心:融入「五阶避错思维框架」——先避免犯错,再追求做对。
 * 适用于人生方方面面的决策(投资/职业/消费/关系/健康...),不局限于理财。
 * 思维模型库 + 五步决策向导 + 决策检查清单 + 3秒速查 + 每周错误复盘模板 + 决策日志。
 */
@RestController
@RequestMapping("/api/decision")
public class DecisionController {

    /** 思维模型库 */
    @GetMapping("/models")
    public R<List<Map<String, Object>>> models() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(model("逆向思维",
                "凡事反过来想:先找『会怎么失败、会亏什么』,避开致命错误,自然容易做对。",
                "重大决策前先列出所有可能失败的原因和最坏结果。"));
        list.add(model("资产/负债判断",
                "所有选择都用『增值/消耗』标尺衡量:会把钱(或时间/精力)放进你口袋的是资产,会拿走的是负债。",
                "行动前问:这笔投入未来是产生回报还是消耗回报?"));
        list.add(model("安全边际",
                "永远留容错空间,不打满、不极端、不赌一把,给自己留退路。",
                "只在把握显著、有退路时行动;不满仓、不梭哈。"));
        list.add(model("能力圈",
                "只在自己真正理解的领域下注,知道边界比扩大边界更重要。",
                "看不懂的领域直接放弃,不勉强。"));
        list.add(model("机会成本",
                "任何决策都要和『最好的备选方案』比较,选就意味着放弃。",
                "决策时列出被放弃的最佳选项,确认当前选择更优。"));
        list.add(model("市场先生",
                "把外部报价/舆论当情绪化的噪声,而非你的老师;别被短期波动牵着走。",
                "恐慌或狂热时问:事情的本质变了吗?没变就别被情绪裹挟。"));
        list.add(model("反偏见/激励机制",
                "警惕确认偏误、损失厌恶、从众;看清『激励』如何扭曲他人和自己的行为。",
                "决策前自查:我是不是只看支持自己观点的信息?谁在从中得利?"));
        list.add(model("决策复盘",
                "记录决策依据,事后对照结果,区分『运气』和『能力』,持续校准。",
                "每笔重大决策写下预期与依据,定期回看。"));
        return R.ok(list);
    }

    /**
     * 五阶避错思维框架。
     * 返回:宗旨、三大支柱、五步流程(每步含自问清单)、检查清单、3秒速查、复盘模板、场景库。
     */
    @GetMapping("/framework")
    public R<Map<String, Object>> framework() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("motto", "先避免犯错,再追求做对");
        data.put("pillars", Arrays.asList(
                Map.of("name", "逆向思维", "book", "先排除失败的路", "point", "反过来想,先排除会失败的路"),
                Map.of("name", "资产负债判断", "book", "增值/消耗标尺", "point", "用增值/消耗标尺定性每个选择"),
                Map.of("name", "安全边际", "book", "永远留退路", "point", "永远留退路和容错空间")
        ));
        // 五步流程
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step(1, "逆向排雷", "逆向思维",
                "先问:这个决策最坏会怎样?哪些是不可承受、不可逆的致命后果?",
                Arrays.asList(
                        "最坏结果是什么?我能承受吗?",
                        "有没有不可逆(赔上健康/信用/本金/关系)的风险?",
                        "如果一年后它失败了,最可能是什么原因?")));
        steps.add(step(2, "价值定性", "资产负债判断",
                "把这个选择归类:它长期是把钱/时间/精力放进口袋(资产),还是拿走(负债)?",
                Arrays.asList(
                        "它未来带来现金流/成长/复利,还是持续消耗?",
                        "是『消费型负债』还是『能增值的投入』?",
                        "三年后回看,它让我更自由还是更被束缚?")));
        steps.add(step(3, "能力圈校验", "能力圈",
                "确认这件事是否在我真正理解的范围内,别在看不懂的地方下重注。",
                Arrays.asList(
                        "我能用自己的话讲清它的原理和风险吗?",
                        "我的判断是基于理解,还是基于别人的说法/情绪?",
                        "如果超出能力圈,能否缩小赌注或先学习?")));
        steps.add(step(4, "替代方案", "机会成本",
                "列出至少一个更好的备选方案做对比,确认当前选择确实最优。",
                Arrays.asList(
                        "如果不做这个,最好的替代选择是什么?",
                        "把同样的钱/时间投到别处,回报会不会更高?",
                        "『什么都不做』是不是更好的选项?")));
        steps.add(step(5, "情绪冷却", "市场先生",
                "决策前先给情绪降温,别在恐慌、贪婪、被催促下拍板。",
                Arrays.asList(
                        "我现在是被 FOMO/恐慌/推销 推着走吗?",
                        "睡一觉/等24小时后,我还会做同样决定吗?",
                        "抛开短期波动,事情的本质变了吗?")));
        data.put("steps", steps);
        // 决策检查清单
        data.put("checklist", Arrays.asList(
                "我已经想过最坏的结果,且能承受",
                "这个选择长期是资产而非负债",
                "它在我的能力圈内",
                "我对比过至少一个更好的备选方案",
                "我不是在情绪(恐慌/贪婪/被催)下做决定",
                "我留了退路和容错空间(安全边际)",
                "我写下了这次决策的依据,方便日后复盘"));
        // 3秒速查
        data.put("quickCheck", Arrays.asList(
                "会死人吗?(不可逆致命后果)→ 有则直接停",
                "是资产还是负债?→ 负债则慎重",
                "懂不懂?→ 不懂就缩小或放弃",
                "有没有更好的选择?→ 有则换",
                "在冲动吗?→ 是则先等一等"));
        // 每周错误复盘模板
        data.put("reviewTemplate", Arrays.asList(
                "本周做的重要决策 / 犯的错:",
                "当时的依据和情绪状态是什么:",
                "结果如何?是能力还是运气:",
                "违反了五步中的哪一步:",
                "下次遇到类似情况,规则是什么:"));
        // 场景库(覆盖生活方方面面)
        data.put("scenes", Arrays.asList(
                Map.of("key", "invest", "name", "投资理财", "example", "要不要现在买入这只低估的消费股?"),
                Map.of("key", "career", "name", "职业发展", "example", "要不要接受这个涨薪但需要频繁出差的 offer?"),
                Map.of("key", "consume", "name", "大额消费", "example", "要不要贷款买这辆车?"),
                Map.of("key", "relation", "name", "人际关系", "example", "要不要和朋友合伙创业?"),
                Map.of("key", "health", "name", "健康生活", "example", "要不要为了赶项目连续熬夜?"),
                Map.of("key", "learn", "name", "学习成长", "example", "要不要花两万报这个培训班?")
        ));
        return R.ok(data);
    }

    /**
     * 决策搜索/分析:按五阶避错框架把用户问题结构化。
     * 步骤③接入大模型后,aiSummary 会由 AI 生成真正的结构化建议。
     * body: { question, scene(可选) }
     */
    @PostMapping("/search")
    public R<Map<String, Object>> search(@RequestBody Map<String, String> body) {
        String q = body.getOrDefault("question", "");
        String scene = body.getOrDefault("scene", "");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("question", q);
        data.put("scene", scene);
        data.put("matchedModels", Arrays.asList("逆向思维", "资产/负债判断", "能力圈", "机会成本", "市场先生"));
        data.put("framework", Arrays.asList(
                "① 逆向排雷:先设想这个决策最坏会怎样失败?有没有不可承受的后果?",
                "② 价值定性:这个选择长期是资产(增值)还是负债(消耗)?",
                "③ 能力圈校验:这件事在我真正理解的范围内吗?",
                "④ 替代方案:相比最好的备选方案(含『不做』),这个选择更优吗?",
                "⑤ 情绪冷却:我是否在恐慌/贪婪/被催促下做决定?"
        ));
        data.put("webRefs", Collections.singletonList(Map.of(
                "title", "(步骤③接入联网搜索后此处展示真实参考资料)",
                "url", "")));
        data.put("aiSummary", "(在 AI 深度分析中,系统会调用你配置的大模型,按五阶避错框架对『" + q + "』给出结构化决策建议)");
        return R.ok(data);
    }

    /** 决策日志 */
    @GetMapping("/logs")
    public R<List<Map<String, Object>>> logs() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(log("是否加仓消费ETF", "安全边际+市场先生", "估值分位22%低估,分批加仓", "2026-06-15", "待复盘"));
        list.add(log("是否买入某新能源题材股", "能力圈+逆向思维", "看不懂技术路线,放弃", "2026-05-20", "正确:该股后续大跌"));
        list.add(log("是否接受频繁出差的 offer", "资产负债判断+机会成本", "涨薪有限但透支健康与家庭时间,判为负债,拒绝", "2026-04-10", "正确:身心状态更好"));
        return R.ok(list);
    }

    private Map<String, Object> model(String name, String desc, String usage) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("desc", desc);
        m.put("usage", usage);
        return m;
    }

    private Map<String, Object> step(int no, String title, String source, String guide, List<String> asks) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("no", no);
        m.put("title", title);
        m.put("source", source);
        m.put("guide", guide);
        m.put("asks", asks);
        return m;
    }

    private Map<String, Object> log(String title, String model, String basis, String date, String review) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("model", model);
        m.put("basis", basis);
        m.put("date", date);
        m.put("review", review);
        return m;
    }
}
