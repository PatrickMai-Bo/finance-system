package com.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.entity.Holding;
import com.finance.mapper.HoldingMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 存量资产负债表服务(资产/负债标尺)。
 * <p>
 * 与 LedgerService(流水/现金流)互补:LedgerService 记「每一笔进出」,
 * 本服务记「你现在拥有的资产 / 背负的负债」这份「存量家底」。
 * 步骤④:已接入 MySQL(MyBatis-Plus),verdict 读取时现算不落库。
 */
@Service
public class HoldingService {

    /** 大类 */
    public static final String T_ASSET = "资产";
    public static final String T_LIABILITY = "负债";

    /** 富爸爸判定 */
    public static final String V_REAL_ASSET = "真资产";
    public static final String V_FAKE_ASSET = "伪资产";
    public static final String V_GOOD_DEBT = "投资性负债";
    public static final String V_BAD_DEBT = "消费性负债";

    private final HoldingMapper mapper;

    public HoldingService(HoldingMapper mapper) {
        this.mapper = mapper;
    }

    /** 首次启动且表为空时,播种示例家底。 */
    @PostConstruct
    public void init() {
        try {
            if (mapper.selectCount(null) == 0) {
                seed(T_ASSET, "自住房产", 1500000, -6000, "自住,每月还房贷,富爸爸视为伪资产");
                seed(T_ASSET, "出租房产", 800000, 2500, "月租3500-物业维护,净现金流为正");
                seed(T_ASSET, "股票/基金", 120000, 500, "分红+定投,长期生息资产");
                seed(T_ASSET, "银行存款", 80000, 130, "货币/定期,现金蓄水池");
                seed(T_LIABILITY, "自住房房贷", 500000, 0, "剩余本金,对应自住房");
                seed(T_LIABILITY, "信用卡欠款", 20000, 0, "消费性负债,利息高应尽快还清");
            }
        } catch (Exception e) {
            System.err.println("[HoldingService] 初始化播种失败(可忽略): " + e.getMessage());
        }
    }

    private void seed(String bigType, String name, double amount, double monthlyCashflow, String note) {
        add(bigType, name, amount, monthlyCashflow, note);
    }

    /** 新增一项。verdict(富爸爸判定)由系统自动计算,不落库。 */
    public Map<String, Object> add(String bigType, String name, double amount, double monthlyCashflow, String note) {
        Holding e = new Holding();
        e.setBigType(bigType);
        e.setName(name);
        e.setAmount(BigDecimal.valueOf(amount));
        e.setMonthlyCashflow(BigDecimal.valueOf(monthlyCashflow));
        e.setNote(note);
        mapper.insert(e);
        return toMap(e);
    }

    /** 修改一项。 */
    public Map<String, Object> update(long id, String bigType, String name, double amount, double monthlyCashflow, String note) {
        Holding e = mapper.selectById(id);
        if (e == null) return null;
        e.setBigType(bigType);
        e.setName(name);
        e.setAmount(BigDecimal.valueOf(amount));
        e.setMonthlyCashflow(BigDecimal.valueOf(monthlyCashflow));
        e.setNote(note);
        mapper.updateById(e);
        return toMap(e);
    }

    public boolean remove(long id) {
        return mapper.deleteById(id) > 0;
    }

    /** 全部条目(资产在前、负债在后,金额降序) */
    public List<Map<String, Object>> list() {
        List<Holding> all = mapper.selectList(null);
        List<Map<String, Object>> res = new ArrayList<>();
        for (Holding e : all) res.add(toMap(e));
        res.sort((a, b) -> {
            int ta = T_ASSET.equals(a.get("bigType")) ? 0 : 1;
            int tb = T_ASSET.equals(b.get("bigType")) ? 0 : 1;
            if (ta != tb) return Integer.compare(ta, tb);
            return Double.compare(((Number) b.get("amount")).doubleValue(), ((Number) a.get("amount")).doubleValue());
        });
        return res;
    }

    /** 富爸爸式净资产 + 现金流象限汇总。 */
    public Map<String, Object> summary() {
        List<Map<String, Object>> all = list();
        double totalAsset = 0, totalLiability = 0;
        double realAsset = 0, fakeAsset = 0;
        double monthlyInflow = 0, monthlyOutflow = 0;
        for (Map<String, Object> r : all) {
            double amt = ((Number) r.get("amount")).doubleValue();
            double cf = ((Number) r.get("monthlyCashflow")).doubleValue();
            String bt = String.valueOf(r.get("bigType"));
            String v = String.valueOf(r.get("verdict"));
            if (T_ASSET.equals(bt)) {
                totalAsset += amt;
                if (V_REAL_ASSET.equals(v)) realAsset += amt; else fakeAsset += amt;
            } else {
                totalLiability += amt;
            }
            if (cf >= 0) monthlyInflow += cf; else monthlyOutflow += -cf;
        }
        double netWorth = totalAsset - totalLiability;
        double monthlyPassive = monthlyInflow;
        double monthlyNet = monthlyInflow - monthlyOutflow;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalAsset", totalAsset);
        data.put("totalLiability", totalLiability);
        data.put("netWorth", netWorth);
        data.put("realAsset", realAsset);
        data.put("fakeAsset", fakeAsset);
        data.put("realAssetRatio", totalAsset > 0 ? Math.round(realAsset / totalAsset * 1000) / 10.0 : 0);
        data.put("debtRatio", totalAsset > 0 ? Math.round(totalLiability / totalAsset * 1000) / 10.0 : 0);
        data.put("monthlyPassive", monthlyPassive);
        data.put("monthlyOutflow", monthlyOutflow);
        data.put("monthlyNet", monthlyNet);
        data.put("diagnosis", diagnose(realAsset, fakeAsset, totalLiability, netWorth, monthlyPassive, monthlyOutflow));
        return data;
    }

    private Map<String, Object> toMap(Holding e) {
        double cf = e.getMonthlyCashflow() == null ? 0 : e.getMonthlyCashflow().doubleValue();
        String verdict = judge(e.getBigType(), cf);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("bigType", e.getBigType());
        m.put("name", e.getName());
        m.put("amount", e.getAmount() == null ? 0 : e.getAmount().doubleValue());
        m.put("monthlyCashflow", cf);
        m.put("note", e.getNote());
        m.put("verdict", verdict);
        m.put("advice", verdictAdvice(verdict));
        return m;
    }

    // ============ 富爸爸判定引擎 ============

    private String judge(String bigType, double monthlyCashflow) {
        if (T_ASSET.equals(bigType)) {
            return monthlyCashflow > 0 ? V_REAL_ASSET : V_FAKE_ASSET;
        }
        return monthlyCashflow > 0 ? V_GOOD_DEBT : V_BAD_DEBT;
    }

    private String verdictAdvice(String v) {
        return switch (v) {
            case V_REAL_ASSET -> "真资产:能把钱放进你口袋,富爸爸鼓励持续增持。";
            case V_FAKE_ASSET -> "伪资产:名义资产但持续要你掏钱,注意别当成财富来源。";
            case V_GOOD_DEBT -> "投资性负债:借钱买生钱资产,只要现金流为正即可用杠杆放大。";
            case V_BAD_DEBT -> "消费性负债:为消费背债、持续吃利息,建议优先偿还。";
            default -> "";
        };
    }

    private String diagnose(double realAsset, double fakeAsset, double liability, double netWorth,
                            double monthlyPassive, double monthlyOutflow) {
        StringBuilder sb = new StringBuilder();
        sb.append("净资产 ").append((long) netWorth).append(" 元");
        double totalAsset = realAsset + fakeAsset;
        double realRatio = totalAsset > 0 ? realAsset / totalAsset * 100 : 0;
        sb.append(",其中真资产(生钱)占比 ").append(Math.round(realRatio)).append("%。");
        if (realRatio < 40) {
            sb.append(" ⚠️ 真资产占比偏低,大量家底是自用型伪资产,建议逐步把结余转化为出租/分红/存息等生钱资产。");
        } else {
            sb.append(" 真资产结构健康,继续用富爸爸思路让资产替你赚钱。");
        }
        if (monthlyPassive >= monthlyOutflow && monthlyPassive > 0) {
            sb.append(" 资产每月被动流入(").append((long) monthlyPassive)
              .append("元)已覆盖持有性支出,现金流方向正确。");
        } else {
            sb.append(" 目前资产每月被动流入 ").append((long) monthlyPassive)
              .append("元,尚不足以覆盖持有性支出 ").append((long) monthlyOutflow).append("元,需继续做大被动收入。");
        }
        if (liability > realAsset && liability > 0) {
            sb.append(" 负债已超过真资产,杠杆偏高,优先偿还消费性负债降低风险。");
        }
        return sb.toString();
    }
}
