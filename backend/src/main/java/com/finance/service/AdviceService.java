package com.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * 建议持有时间服务(步骤③增强):
 * 对筛选列表当前页的每只标的,并发调激活大模型,结合价值投资逻辑推算
 * 「短期 / 中期 / 长期」的 持有时间 + 预计收益率区间 + 一句话理由。
 *  - 模型可用且返回合法 JSON → mode=real(AI推算)
 *  - 无模型或调用/解析失败 → 基于安全边际/历史业绩的规则估算,mode=rule(明确标注非AI)
 * 按 (scene:code) 缓存, TTL 30 分钟(与行情缓存一致),避免重复消耗与重复耗时。
 */
@Service
public class AdviceService {

    private final RealScreenService real;
    private final MockDataService mock;
    private final LlmConfigService llmCfg;
    private final LlmClient client;
    private final ObjectMapper om = new ObjectMapper();

    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private static final long TTL = 30 * 60 * 1000L;

    public AdviceService(RealScreenService real, MockDataService mock, LlmConfigService llmCfg, LlmClient client) {
        this.real = real;
        this.mock = mock;
        this.llmCfg = llmCfg;
        this.client = client;
    }

    private static class Cached {
        Map<String, Object> v;
        long at;
        Cached(Map<String, Object> v) { this.v = v; this.at = System.currentTimeMillis(); }
    }

    /** 生成当前页每只标的的建议。invalidate=true 先清空该 scene 全部缓存(用于刷新)。 */
    public List<Map<String, Object>> advice(String scene, String category, int page, int size, boolean invalidate) {
        if (invalidate) {
            cache.keySet().removeIf(k -> k.startsWith(scene + ":"));
        }
        List<Map<String, Object>> rows;
        try {
            rows = "fund".equals(scene) ? real.fundList(category) : real.stockList();
        } catch (Exception e) {
            rows = "fund".equals(scene) ? mock.fundList(category) : mock.stockList();
        }
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(rows.size(), from + size);
        List<Map<String, Object>> pageRows = from >= rows.size() ? Collections.emptyList() : rows.subList(from, to);

        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (Map<String, Object> r : pageRows) {
            String code = str(r.get("code"));
            futures.add(pool.submit(() -> generate(scene, r, code)));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Future<Map<String, Object>> f : futures) {
            try {
                out.add(f.get(120, TimeUnit.SECONDS));
            } catch (Exception e) {
                out.add(errorAdvice());
            }
        }
        return out;
    }

    private Map<String, Object> generate(String scene, Map<String, Object> row, String code) {
        String key = scene + ":" + code;
        long now = System.currentTimeMillis();
        Cached hit = cache.get(key);
        if (hit != null && now - hit.at < TTL) return hit.v;

        Map<String, Object> active = llmCfg.active();
        String apiKey = active == null ? "" : String.valueOf(active.getOrDefault("apiKey", "")).trim();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("name", row.get("name"));

        if (active == null || apiKey.isEmpty()) {
            Map<String, Object> fb = ruleFallback(scene, row);
            result.putAll(fb);
            cache.put(key, new Cached(result));
            return result;
        }
        try {
            String raw = client.chat(active, buildPrompt(scene, row));
            Map<String, Object> parsed = parseAdvice(raw);
            if (parsed == null) {
                result.putAll(ruleFallback(scene, row));
            } else {
                result.put("short", parsed.get("short"));
                result.put("mid", parsed.get("mid"));
                result.put("long", parsed.get("long"));
                result.put("mode", "real");
                result.put("model", active.get("name") + " / " + active.get("model"));
            }
            cache.put(key, new Cached(result));
            return result;
        } catch (Exception e) {
            result.putAll(ruleFallback(scene, row));
            cache.put(key, new Cached(result));
            return result;
        }
    }

    private String buildPrompt(String scene, Map<String, Object> row) {
        boolean isFund = "fund".equals(scene);
        StringBuilder sb = new StringBuilder();
        sb.append("你是价值投资顾问(安全边际、护城河与能力圈、现金流与资产思维)。\n");
        sb.append("请基于下面这只").append(isFund ? "基金" : "股票").append("的真实数据,推算『建议持有时间』与『预计收益率』。\n");
        sb.append("严格按以下 JSON 格式返回(只输出 JSON,不要任何解释文字):\n");
        sb.append("{\n");
        sb.append("  \"short\": {\"horizon\":\"短期持有时间(如 3-6个月)\", \"returnRange\":\"预计收益率区间(如 +8%~+18%)\", \"logic\":\"一句话理由\"},\n");
        sb.append("  \"mid\":   {\"horizon\":\"中期持有时间(如 1-2年)\", \"returnRange\":\"预计收益率区间\", \"logic\":\"一句话理由\"},\n");
        sb.append("  \"long\":  {\"horizon\":\"长期持有时间(如 3年以上)\", \"returnRange\":\"预计收益率区间\", \"logic\":\"一句话理由\"}\n");
        sb.append("}\n");
        sb.append("推算要求:①短期看估值修复与事件催化,中期看业绩兑现与护城河,长期看复利与现金流;")
          .append("②收益率须保守、给区间,不能与安全边际/历史业绩明显矛盾;③理由简洁专业。\n");
        sb.append("数据: ").append(row.toString());
        return sb.toString();
    }

    private Map<String, Object> parseAdvice(String raw) {
        try {
            String s = raw.trim();
            int a = s.indexOf('{');
            int b = s.lastIndexOf('}');
            if (a < 0 || b <= a) return null;
            JsonNode node = om.readTree(s.substring(a, b + 1));
            if (!node.has("short") || !node.has("mid") || !node.has("long")) return null;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("short", toMap(node.get("short")));
            out.put("mid", toMap(node.get("mid")));
            out.put("long", toMap(node.get("long")));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> toMap(JsonNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("horizon", n.path("horizon").asText(""));
        m.put("returnRange", n.path("returnRange").asText(""));
        m.put("logic", n.path("logic").asText(""));
        return m;
    }

    /** 无模型或 AI 失败时的规则兜底:基于安全边际(股票)/历史业绩(基金)给出保守区间,明确标注非AI */
    private Map<String, Object> ruleFallback(String scene, Map<String, Object> row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("code", row.get("code"));
        r.put("name", row.get("name"));
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

    private double num(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }

    private String str(Object o) { return o == null ? "" : o.toString(); }

    private Map<String, Object> errorAdvice() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", "");
        m.put("mode", "error");
        return m;
    }
}
