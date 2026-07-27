package com.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.entity.Ledger;
import com.finance.mapper.LedgerMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 随手记式记账服务
 * <p>
 * 用户手动记一笔(金额 / 收支方向 / 描述 / 日期),系统自动按
 * 「资产 / 负债」标尺归类,并汇总成资产负债表 + 现金流象限。
 * 步骤④:已接入 MySQL(MyBatis-Plus),数据持久化;category 落库、advice 读取时现算。
 */
@Service
public class LedgerService {

    /** 分类常量 */
    public static final String C_ASSET_EXP = "消费资产型";
    public static final String C_LIAB_EXP = "消费负债型";
    public static final String C_NEUTRAL = "中性刚需";
    public static final String C_ACTIVE_IN = "主动收入";
    public static final String C_PASSIVE_IN = "被动收入";

    private final LedgerMapper mapper;

    public LedgerService(LedgerMapper mapper) {
        this.mapper = mapper;
    }

    /** 首次启动且表为空时,播种示例流水,便于前端演示与用户替换成真实数据。 */
    @PostConstruct
    public void init() {
        try {
            if (mapper.selectCount(null) == 0) {
                seed("2026-07-01", "income", 28000, "7月工资");
                seed("2026-07-02", "income", 4500, "出租房房租收入");
                seed("2026-07-03", "expense", 6000, "房贷月供");
                seed("2026-07-05", "expense", 1200, "超市买菜生活开销");
                seed("2026-07-08", "expense", 2980, "报名Python数据分析课程");
                seed("2026-07-10", "expense", 800, "和朋友聚餐喝酒");
                seed("2026-07-12", "income", 1500, "基金分红到账");
                seed("2026-07-15", "expense", 3200, "冲动买了名牌包");
                seed("2026-07-18", "expense", 300, "地铁公交交通费");
                seed("2026-07-20", "expense", 1500, "健身房年卡");
                seed("2026-07-22", "income", 800, "银行存款利息");
                seed("2026-07-24", "expense", 600, "水电燃气物业费");
            }
        } catch (Exception e) {
            // 数据库不可用时不影响应用启动,仅在日志提示
            System.err.println("[LedgerService] 初始化播种失败(可忽略,数据库就绪后会正常): " + e.getMessage());
        }
    }

    private void seed(String date, String type, double amount, String desc) {
        add(date, type, amount, desc, null);
    }

    /**
     * 记一笔。category 为 null 时由系统按富爸爸逻辑自动分类;非 null 时用用户手动指定的分类。
     */
    public Map<String, Object> add(String date, String type, double amount, String desc, String category) {
        String cat = (category == null || category.isBlank()) ? classify(type, desc) : category;
        Ledger e = new Ledger();
        e.setDate(date);
        e.setType(type);
        e.setAmount(BigDecimal.valueOf(amount));
        e.setDescription(desc);
        e.setCategory(cat);
        mapper.insert(e);
        return toMap(e);
    }

    public boolean remove(long id) {
        return mapper.deleteById(id) > 0;
    }

    /** 全部流水(按日期倒序) */
    public List<Map<String, Object>> list() {
        List<Ledger> all = mapper.selectList(null);
        List<Map<String, Object>> res = new ArrayList<>();
        for (Ledger e : all) res.add(toMap(e));
        res.sort((a, b) -> String.valueOf(b.get("date")).compareTo(String.valueOf(a.get("date"))));
        return res;
    }

    /**
     * 富爸爸式资产负债表 + 现金流象限汇总。
     */
    public Map<String, Object> summary() {
        List<Map<String, Object>> all = list();
        double activeIncome = 0, passiveIncome = 0;
        double assetExp = 0, liabExp = 0, neutralExp = 0;
        for (Map<String, Object> r : all) {
            double amt = ((Number) r.get("amount")).doubleValue();
            String cat = String.valueOf(r.get("category"));
            switch (cat) {
                case C_ACTIVE_IN -> activeIncome += amt;
                case C_PASSIVE_IN -> passiveIncome += amt;
                case C_ASSET_EXP -> assetExp += amt;
                case C_LIAB_EXP -> liabExp += amt;
                case C_NEUTRAL -> neutralExp += amt;
                default -> { }
            }
        }
        double totalIncome = activeIncome + passiveIncome;
        double totalExpense = assetExp + liabExp + neutralExp;
        double netCashflow = totalIncome - totalExpense;
        double coverage = totalExpense > 0 ? Math.round(passiveIncome / totalExpense * 1000) / 10.0 : 0;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activeIncome", activeIncome);
        data.put("passiveIncome", passiveIncome);
        data.put("totalIncome", totalIncome);
        data.put("assetExpense", assetExp);
        data.put("liabilityExpense", liabExp);
        data.put("neutralExpense", neutralExp);
        data.put("totalExpense", totalExpense);
        data.put("netCashflow", netCashflow);
        data.put("coverage", coverage);
        data.put("assetRatio", totalExpense > 0 ? Math.round(assetExp / totalExpense * 1000) / 10.0 : 0);
        data.put("liabRatio", totalExpense > 0 ? Math.round(liabExp / totalExpense * 1000) / 10.0 : 0);
        data.put("diagnosis", diagnose(passiveIncome, totalExpense, assetExp, liabExp, netCashflow));
        return data;
    }

    private Map<String, Object> toMap(Ledger e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("date", e.getDate());
        m.put("type", e.getType());
        m.put("amount", e.getAmount() == null ? 0 : e.getAmount().doubleValue());
        m.put("desc", e.getDescription());
        m.put("category", e.getCategory());
        m.put("advice", advice(e.getCategory()));
        return m;
    }

    // ============ 富爸爸自动分类引擎(关键词规则,步骤⑤可升级为大模型辅助分类) ============

    private static final String[] ASSET_KW = {
            "课程", "学习", "培训", "书", "读书", "考证", "证书", "技能", "网课",
            "健身", "运动", "体检", "医疗保健", "营养",
            "投资", "理财", "定投", "基金", "股票", "本金", "存款", "买入",
            "工具", "电脑", "设备", "软件", "会员", "效率"
    };
    private static final String[] LIAB_KW = {
            "名牌", "奢侈", "包包", "名牌包", "限量", "潮鞋", "球鞋", "撑面子", "面子",
            "游戏", "皮肤", "抽卡", "氪金", "烟", "酒", "喝酒", "赌", "彩票",
            "冲动", "直播间", "打赏", "奶茶", "网红", "跟风"
    };
    private static final String[] PASSIVE_KW = {
            "房租", "租金", "分红", "利息", "股息", "版税", "收益", "返利", "被动"
    };
    private static final String[] NEUTRAL_KW = {
            "房贷", "月供", "车贷", "房租支出", "水电", "燃气", "物业", "话费", "宽带",
            "餐饮", "买菜", "吃饭", "生活", "日用", "交通", "地铁", "公交", "打车", "加油",
            "医疗", "看病", "药", "保险", "教育支出", "学费"
    };

    private String classify(String type, String desc) {
        String d = desc == null ? "" : desc;
        if ("income".equals(type)) {
            if (containsAny(d, PASSIVE_KW)) return C_PASSIVE_IN;
            return C_ACTIVE_IN;
        }
        if (containsAny(d, LIAB_KW)) return C_LIAB_EXP;
        if (containsAny(d, ASSET_KW)) return C_ASSET_EXP;
        if (containsAny(d, NEUTRAL_KW)) return C_NEUTRAL;
        return C_NEUTRAL;
    }

    private boolean containsAny(String text, String[] kws) {
        for (String k : kws) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private String advice(String cat) {
        return switch (cat) {
            case C_ASSET_EXP -> "资产型支出:钱花在能增值/带来未来现金流的地方,富爸爸鼓励多花。";
            case C_LIAB_EXP -> "负债型支出:为面子/冲动买单,持续消耗财富,建议压缩。";
            case C_NEUTRAL -> "中性刚需:维持生活必需,控制在合理占比即可。";
            case C_ACTIVE_IN -> "主动收入:靠时间劳动换取,应把结余转化为资产。";
            case C_PASSIVE_IN -> "被动收入:资产自动产生的钱,这是通往财务自由的关键,越多越好。";
            default -> "";
        };
    }

    private String diagnose(double passive, double expense, double assetExp, double liabExp, double net) {
        StringBuilder sb = new StringBuilder();
        double cov = expense > 0 ? passive / expense * 100 : 0;
        sb.append("被动收入覆盖支出的 ").append(Math.round(cov)).append("%");
        if (cov >= 100) sb.append(",已达到财务自由(被动收入 ≥ 支出)!");
        else sb.append(",距财务自由还需每月增加被动现金流 ").append((int) Math.max(0, expense - passive)).append(" 元。");

        if (net < 0) sb.append(" ⚠️ 本期现金流为负,支出大于收入,需先止血。");
        if (liabExp > assetExp && liabExp > 0) {
            sb.append(" 负债型消费(").append((int) liabExp).append("元)已超过资产型消费(")
              .append((int) assetExp).append("元),富爸爸提醒:先买资产,再买负债。");
        } else if (assetExp > 0) {
            sb.append(" 资产型消费占比健康,继续保持把钱花在能增值的地方。");
        }
        return sb.toString();
    }
}
