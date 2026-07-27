package com.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.finance.entity.DecisionLog;
import com.finance.mapper.DecisionLogMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 决策日志服务:
 *  - 每次决策问答落库(MyBatis-Plus)
 *  - 简单关键词相似度检索:提取 question 中的中文 2-6 字词组 / 英文单词,
 *    对历史做 LIKE 匹配,**同一问题里命中关键词数 >= 2** 视为相似,
 *    用于避免重复犯错提醒。
 */
@Service
public class DecisionLogService {

    private final DecisionLogMapper mapper;

    public DecisionLogService(DecisionLogMapper mapper) {
        this.mapper = mapper;
    }

    public void save(DecisionLog log) {
        if (log.getCreatedAt() == null) log.setCreatedAt(new Date());
        mapper.insert(log);
    }

    /** 检索与当前 question 相似的历史问答(命中关键词数 >= 2),按时间倒序取 limit 条 */
    public List<DecisionLog> findSimilar(String question, String scene, int limit) {
        if (question == null || question.trim().isEmpty()) return Collections.emptyList();
        List<String> kws = extractKeywords(question);
        if (kws.isEmpty()) return Collections.emptyList();

        QueryWrapper<DecisionLog> qw = new QueryWrapper<>();
        // 走自定义 mapper.findSimilar,直接拼 SQL,绕开 Wrapper 嵌套 OR 的坑
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kws.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append("question LIKE '%").append(kws.get(i).replace("'", "''")).append("%'");
        }
        String sceneParam = (scene == null || "全部".equals(scene)) ? "" : scene;
        List<DecisionLog> candidates = mapper.findSimilar(sceneParam, sb.toString(), Math.max(limit * 4, 20));

        // 二次过滤:必须命中 >= 2 个关键词,且排除 id 接近的(防止刚刚问的同一条再被召回)
        return candidates.stream()
                .filter(log -> countHit(log.getQuestion(), kws) >= 2)
                .sorted(Comparator.comparing(DecisionLog::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** 提取关键词(中文 2-6 字 / 英文单词,去除停用词) */
    private List<String> extractKeywords(String text) {
        Set<String> set = new LinkedHashSet<>();
        // 1) 单字(保证"换"/"车"等单字关键词不被多字 match 覆盖)
        Matcher m1 = Pattern.compile("[\\u4e00-\\u9fa5]").matcher(text);
        while (m1.find()) set.add(m1.group());
        // 2) 2-6 字词组
        Matcher m2 = Pattern.compile("[\\u4e00-\\u9fa5]{2,6}").matcher(text);
        while (m2.find()) set.add(m2.group());
        // 3) 英文/数字
        Matcher m3 = Pattern.compile("[A-Za-z0-9]+").matcher(text);
        while (m3.find()) {
            String w = m3.group();
            if (w.length() >= 2) set.add(w);
        }
        Set<String> stop = new HashSet<>(Arrays.asList(
                // 双字以上
                "现在", "请问", "一下", "什么", "怎么", "如何", "这个", "那个",
                "所以", "因为", "如果", "然后", "目前", "打算", "决定",
                "已经", "可以", "能够", "应该", "可能", "就是", "我们", "他们",
                "你们", "她们", "这样", "那样", "一点", "一直", "一定",
                "一种", "一样", "一些", "时候", "东西", "问题", "情况",
                "方面", "考虑",
                // 单字停用(高频)
                "我", "你", "他", "她", "它", "们", "了", "的", "吗", "呢", "啊", "吧",
                "是", "在", "和", "与", "或", "就", "也", "都", "已", "不", "有", "没",
                "要", "能", "会", "把", "给", "让", "被", "从", "到", "为", "于", "对",
                "跟", "比", "按", "因", "所", "而", "且", "并", "但", "则", "其", "之",
                "以", "可", "应",
                "上", "下", "里", "外", "前", "后", "中", "大", "小", "多", "少",
                "好", "坏", "做", "看", "说", "问", "买", "卖",
                "花", "年", "月", "日", "点", "次", "个", "些", "人", "事", "时"
        ));
        set.removeAll(stop);
        return new ArrayList<>(set);
    }

    private int countHit(String text, List<String> keywords) {
        if (text == null) return 0;
        int n = 0;
        for (String kw : keywords) if (text.contains(kw)) n++;
        return n;
    }

    /**
     * 从 AI 回复中解析最终建议(做 / 不做 / 再等等)。
     * 优先看 🎯 最终建议 段;兼容"建议:做"/"建议不做"等模式。
     */
    public String parseVerdict(String answer) {
        if (answer == null) return null;
        int idx = answer.indexOf("🎯");
        String focus = idx >= 0 ? answer.substring(idx) : answer;
        // 顺序:不做 > 再等等 > 做(避免"做"被"不做"误吞)
        if (focus.contains("不做")) return "不做";
        if (focus.contains("再等等") || focus.contains("再等") || focus.contains("等一等")) return "再等等";
        if (focus.contains("做") && !focus.contains("不做") && !focus.contains("难做")) return "做";
        return null;
    }
}