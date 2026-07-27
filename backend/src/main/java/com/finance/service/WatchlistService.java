package com.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.entity.Watchlist;
import com.finance.mapper.WatchlistMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 自选清单服务(自选股 / 自选基)。
 * <p>
 * 用户把关注/持有的股票、基金加入自选,自行维护成本价、持仓金额、目标价与备注,
 * 支持增删改查。之后可整体丢给 AI 做组合点评。
 * type: stock(自选股) / fund(自选基)。
 * 步骤④:已接入 MySQL(MyBatis-Plus),数据持久化。
 */
@Service
public class WatchlistService {

    private final WatchlistMapper mapper;

    public WatchlistService(WatchlistMapper mapper) {
        this.mapper = mapper;
    }

    /** 首次启动且表为空时,播种示例自选。 */
    @PostConstruct
    public void init() {
        try {
            if (mapper.selectCount(null) == 0) {
                seed("stock", "贵州茅台", "600519", "白酒", 1650, 33000, 1500, "护城河极强,回调分批");
                seed("stock", "招商银行", "600036", "银行", 34, 17000, 40, "高股息,收息为主");
                seed("fund", "沪深300ETF", "510300", "指数基金", 3.8, 20000, 4.5, "核心宽基定投");
                seed("fund", "中证红利ETF", "515080", "指数基金", 2.6, 15600, 3.2, "高股息防御");
            }
        } catch (Exception e) {
            System.err.println("[WatchlistService] 初始化播种失败(可忽略): " + e.getMessage());
        }
    }

    private void seed(String type, String name, String code, String category,
                      double cost, double amount, double targetPrice, String note) {
        add(type, name, code, category, cost, amount, targetPrice, note);
    }

    public Map<String, Object> add(String type, String name, String code, String category,
                                   double cost, double amount, double targetPrice, String note) {
        Watchlist e = new Watchlist();
        e.setType(type);
        e.setName(name);
        e.setCode(code);
        e.setCategory(category);
        e.setCost(BigDecimal.valueOf(cost));
        e.setAmount(BigDecimal.valueOf(amount));
        e.setTargetPrice(BigDecimal.valueOf(targetPrice));
        e.setNote(note);
        mapper.insert(e);
        return toMap(e);
    }

    public Map<String, Object> update(long id, String type, String name, String code, String category,
                                      double cost, double amount, double targetPrice, String note) {
        Watchlist e = mapper.selectById(id);
        if (e == null) return null;
        e.setType(type);
        e.setName(name);
        e.setCode(code);
        e.setCategory(category);
        e.setCost(BigDecimal.valueOf(cost));
        e.setAmount(BigDecimal.valueOf(amount));
        e.setTargetPrice(BigDecimal.valueOf(targetPrice));
        e.setNote(note);
        mapper.updateById(e);
        return toMap(e);
    }

    public boolean remove(long id) {
        return mapper.deleteById(id) > 0;
    }

    /** 按类型列出自选(stock / fund);type 为空返回全部。 */
    public List<Map<String, Object>> list(String type) {
        LambdaQueryWrapper<Watchlist> w = new LambdaQueryWrapper<>();
        if (type != null && !type.isBlank()) {
            w.eq(Watchlist::getType, type);
        }
        List<Watchlist> all = mapper.selectList(w);
        List<Map<String, Object>> res = new ArrayList<>();
        for (Watchlist e : all) res.add(toMap(e));
        return res;
    }

    /** 组合汇总:总持仓金额、条目数(供 AI 组合点评)。 */
    public Map<String, Object> summary(String type) {
        List<Map<String, Object>> list = list(type);
        double total = 0;
        for (Map<String, Object> r : list) {
            total += ((Number) r.get("amount")).doubleValue();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("count", list.size());
        data.put("totalAmount", total);
        data.put("items", list);
        return data;
    }

    private Map<String, Object> toMap(Watchlist e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("type", e.getType());
        m.put("name", e.getName());
        m.put("code", e.getCode());
        m.put("category", e.getCategory());
        m.put("cost", e.getCost() == null ? 0 : e.getCost().doubleValue());
        m.put("amount", e.getAmount() == null ? 0 : e.getAmount().doubleValue());
        m.put("targetPrice", e.getTargetPrice() == null ? 0 : e.getTargetPrice().doubleValue());
        m.put("note", e.getNote());
        return m;
    }
}
