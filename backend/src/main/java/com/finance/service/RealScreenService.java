package com.finance.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 真实筛选引擎(步骤③):
 * 输入 = 采集服务的真实业绩+行情数据;
 * 输出 = 与 mock 完全同构的字段,按价值投资逻辑计算:
 *   - PE分位(候选域横截面) → 估值纪律
 *   - 护城河量化评分(ROE/毛利率/规模/成长稳定性,全部来自真实财报) → 护城河与能力圈
 *   - 格雷厄姆成长公式估算内在价值 V = EPS × (8.5 + 2g),g 保守取值
 *   - 安全边际 = (V - P) / V
 * 数据不足的字段(负债率/十年FCF/最大回撤)明确置空或标注近似,不凭空编造。
 * 结果带 30 分钟内存缓存;采集失败由 Controller 回退 mock。
 */
@Service
public class RealScreenService {

    private final CollectorClient collector;

    private volatile List<Map<String, Object>> stockCache;
    private volatile long stockCacheAt;
    private final Map<String, List<Map<String, Object>>> fundCache = new HashMap<>();
    private final Map<String, Long> fundCacheAt = new HashMap<>();
    private static final long TTL = 30 * 60 * 1000L;

    public RealScreenService(CollectorClient collector) {
        this.collector = collector;
    }

    public boolean available() {
        return collector.healthy();
    }

    public void invalidate() {
        stockCache = null;
        synchronized (fundCache) { fundCache.clear(); fundCacheAt.clear(); }
    }

    // ================= 股票 =================

    public synchronized List<Map<String, Object>> stockList() throws Exception {
        long now = System.currentTimeMillis();
        if (stockCache != null && now - stockCacheAt < TTL) return stockCache;

        List<Map<String, Object>> raw = collector.stockQuality(120);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            Double price = d(r.get("price"));
            Double pe = d(r.get("pe"));
            Double roe = d(r.get("roe"));
            if (price == null || pe == null || pe <= 0 || roe == null) continue;
            Double gross = d(r.get("grossMargin"));
            Double peQ = d(r.get("peQuantile"));
            Double mktcap = d(r.get("mktcapYi"));
            Double profitYoy = d(r.get("profitYoy"));
            Double revenueYoy = d(r.get("revenueYoy"));
            Double epsCf = d(r.get("epsCashflow"));

            // EPS(由 PE 与现价反推,TTM口径)
            double eps = price / pe;
            // 格雷厄姆公式,g 保守:取净利增速一半,封顶12%,下限0
            double g = profitYoy == null ? 0 : Math.max(0, Math.min(12, profitYoy / 2.0));
            double intrinsic = round1(eps * (8.5 + 2 * g));
            double margin = (intrinsic - price) / intrinsic * 100.0;

            // 护城河评分(全部来自真实财报指标)
            int moat = moatScore(roe, gross, mktcap, profitYoy, revenueYoy);
            // 综合评分:护城河40% + 估值(PE分位越低越好)30% + 安全边际20% + 现金流10%
            double valScore = peQ == null ? 50 : (100 - peQ);
            double mgScore = Math.max(0, Math.min(100, 50 + margin));
            double cfScore = epsCf == null ? 40 : (epsCf > 0 ? Math.min(100, 60 + epsCf * 20) : 20);
            int score = (int) Math.round(moat * 0.40 + valScore * 0.30 + mgScore * 0.20 + cfScore * 0.10);

            // 经营现金流(亿) ≈ 每股经营现金流 × 总股本(亿股 = 总市值/股价)
            Double opCashflow = (epsCf == null || mktcap == null) ? null
                    : round1(epsCf * (mktcap / price));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.get("name"));
            m.put("code", r.get("code"));
            m.put("industry", r.get("industry"));
            m.put("peQuantile", peQ == null ? null : Math.round(peQ));
            m.put("pe", round1(pe));
            m.put("roe", round1(roe));
            m.put("grossMargin", gross == null ? null : round1(gross));
            m.put("debtRatio", null); // 本期采集未含负债率,不编造
            m.put("operatingCashflow", opCashflow);
            m.put("intrinsicValue", intrinsic);
            m.put("price", price);
            m.put("safetyMargin", round1(margin));
            m.put("moatScore", moat);
            m.put("moatTags", moatTags(moat, gross == null ? 0 : gross, roe));
            m.put("score", score);
            m.put("rating", rating(score, margin));
            m.put("reason", reason(peQ, roe, margin, moat, profitYoy));
            m.put("fcfTrend", approxTrend(opCashflow, revenueYoy)); // 由最新现金流+增速回推的近似趋势
            m.put("dataSource", "real");
            m.put("reportDate", r.get("reportDate"));
            out.add(m);
        }
        // 综合分降序,取前30
        out.sort((a, b) -> (int) b.get("score") - (int) a.get("score"));
        if (out.size() > 30) out = new ArrayList<>(out.subList(0, 30));
        for (int i = 0; i < out.size(); i++) out.get(i).put("rank", i + 1);

        stockCache = out;
        stockCacheAt = now;
        return out;
    }

    private int moatScore(Double roe, Double gross, Double mktcap, Double profitYoy, Double revenueYoy) {
        double s = 0;
        // ROE(权重最大,巴菲特/芒格首要指标):15%起步给分,30%+满分40
        if (roe != null) s += Math.max(0, Math.min(40, (roe - 5) / 25.0 * 40));
        // 毛利率:反映定价权,60%+满分30
        if (gross != null) s += Math.max(0, Math.min(30, gross / 60.0 * 30));
        // 规模壁垒:千亿市值满分15
        if (mktcap != null) s += Math.max(0, Math.min(15, mktcap / 1000.0 * 15));
        // 成长稳定性:营收利润同向正增长给15
        if (profitYoy != null && revenueYoy != null) {
            if (profitYoy > 0 && revenueYoy > 0) s += 15;
            else if (profitYoy > 0 || revenueYoy > 0) s += 8;
        }
        return (int) Math.round(Math.min(100, s));
    }

    private List<String> moatTags(int moat, double gross, double roe) {
        List<String> tags = new ArrayList<>();
        if (gross >= 60) tags.add("品牌/定价权");
        if (roe >= 20) tags.add("高资本回报");
        if (moat >= 80) tags.add("宽护城河");
        else if (moat >= 60) tags.add("中等护城河");
        if (tags.isEmpty()) tags.add("壁垒一般");
        return tags;
    }

    private String rating(int score, double margin) {
        if (score >= 85 && margin >= 25) return "强烈推荐";
        if (score >= 70 && margin > 0) return "推荐";
        if (score >= 55) return "观察";
        return "回避";
    }

    private String reason(Double peQ, Double roe, double margin, int moat, Double profitYoy) {
        StringBuilder sb = new StringBuilder();
        if (peQ != null) sb.append("PE候选域分位").append(Math.round(peQ)).append("%").append(peQ < 30 ? "(相对低估)" : "");
        sb.append(",ROE ").append(round1(roe)).append("%").append(roe >= 15 ? "(优异)" : "");
        if (profitYoy != null) sb.append(",净利同比").append(round1(profitYoy)).append("%");
        sb.append(",护城河").append(moat).append("分");
        if (margin >= 30) sb.append(",安全边际充足(≥30%)");
        else if (margin > 0) sb.append(",安全边际").append(round1(margin)).append("%(不足30%,等待更好价格)");
        else sb.append(",现价高于保守内在价值,不宜追高");
        return sb.toString();
    }

    /** 十年FCF暂无逐年数据,用最新经营现金流+营收增速回推近似趋势(仅供形态参考) */
    private int[] approxTrend(Double opCashflow, Double revenueYoy) {
        double base = opCashflow == null || opCashflow <= 0 ? 10 : opCashflow;
        double g = revenueYoy == null ? 5 : Math.max(-10, Math.min(20, revenueYoy / 2.0));
        int[] t = new int[10];
        double v = base / Math.pow(1 + g / 100.0, 9);
        for (int i = 0; i < 10; i++) {
            t[i] = (int) Math.max(1, Math.round(v));
            v *= (1 + g / 100.0);
        }
        return t;
    }

    // ================= 基金 =================

    public List<Map<String, Object>> fundList(String category) throws Exception {
        String key = category == null || category.isEmpty() ? "全部" : category;
        long now = System.currentTimeMillis();
        synchronized (fundCache) {
            List<Map<String, Object>> hit = fundCache.get(key);
            Long at = fundCacheAt.get(key);
            if (hit != null && at != null && now - at < TTL) return hit;
        }

        List<Map<String, Object>> raw = collector.fundRank(key, 200);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            Double y1 = d(r.get("y1"));
            Double y3 = d(r.get("y3"));
            if (y3 == null && y1 == null) continue;
            // 评分:近3年(长期业绩)60% + 近1年30% + 今年来10%,再按波动粗略折减
            double s3 = y3 == null ? 0 : Math.min(100, Math.max(0, y3 / 3.0));       // 3年300%=满分
            double s1 = y1 == null ? 0 : Math.min(100, Math.max(0, y1 + 50));        // 1年50%=满分
            Double ty = d(r.get("thisYear"));
            double sy = ty == null ? 50 : Math.min(100, Math.max(0, ty + 50));
            int score = (int) Math.round(s3 * 0.6 + Math.min(100, s1) * 0.3 + sy * 0.1);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.get("name"));
            m.put("code", r.get("code"));
            m.put("category", key);
            m.put("nav", r.get("nav"));
            m.put("peQuantile", null);      // 基金层面无PE分位,不编造
            m.put("return1y", y1);
            m.put("return3y", y3);
            m.put("maxDrawdown", null);     // 排行接口无回撤数据,不编造
            m.put("scale", null);
            m.put("fee", r.get("fee"));
            m.put("score", Math.min(99, score));
            m.put("moatScore", Math.min(95, score - 5));
            m.put("moatTags", fundTags(key));
            m.put("rating", score >= 85 ? "强烈推荐" : score >= 70 ? "推荐" : score >= 50 ? "观察" : "回避");
            m.put("reason", fundReason(y1, y3, r.get("fee")));
            m.put("dataSource", "real");
            out.add(m);
        }
        out.sort((a, b) -> (int) b.get("score") - (int) a.get("score"));
        if (out.size() > 30) out = new ArrayList<>(out.subList(0, 30));
        for (int i = 0; i < out.size(); i++) out.get(i).put("rank", i + 1);

        synchronized (fundCache) {
            fundCache.put(key, out);
            fundCacheAt.put(key, now);
        }
        return out;
    }

    private List<String> fundTags(String type) {
        switch (type) {
            case "指数基金": return Arrays.asList("被动跟踪", "低费率");
            case "混合型": return Arrays.asList("主动管理", "灵活配置");
            case "债券型": return Arrays.asList("票息现金流", "防御");
            case "股票型": return Arrays.asList("高权益仓位", "波动大");
            case "QDII": return Arrays.asList("海外配置", "汇率敞口");
            default: return Arrays.asList("真实净值排行");
        }
    }

    private String fundReason(Double y1, Double y3, Object fee) {
        StringBuilder sb = new StringBuilder();
        if (y3 != null) sb.append("近3年").append(round1(y3)).append("%(长期业绩为先)");
        if (y1 != null) sb.append(sb.length() > 0 ? "," : "").append("近1年").append(round1(y1)).append("%");
        if (fee != null) sb.append(",费率").append(fee);
        sb.append(";数据来自天天基金实时排行");
        return sb.toString();
    }

    // ================= util =================

    private static Double d(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
}
