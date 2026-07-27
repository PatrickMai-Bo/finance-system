package com.finance.controller;

import com.finance.common.R;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 板块一:个人财务系统
 * 资产负债表(真资产/真负债)、现金流(主动/被动)、资产产生收入能力、财务自由目标。
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    /** 资产负债表:区分真资产(把钱放进口袋)/真负债 */
    @GetMapping("/balance-sheet")
    public R<Map<String, Object>> balanceSheet() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> assets = new ArrayList<>();
        assets.add(item("指数基金定投", "真资产", 180000, 5.2));
        assets.add(item("股票组合", "真资产", 220000, 8.0));
        assets.add(item("出租房产", "真资产", 1200000, 3.5));
        assets.add(item("货币基金", "真资产", 80000, 2.1));
        assets.add(item("自住房(自用)", "中性", 1800000, 0));

        List<Map<String, Object>> liabilities = new ArrayList<>();
        liabilities.add(item("房贷", "真负债", 900000, -4.1));
        liabilities.add(item("车贷", "真负债", 60000, -5.0));
        liabilities.add(item("信用卡", "真负债", 15000, -18.0));

        double totalAsset = assets.stream().mapToDouble(a -> ((Number) a.get("amount")).doubleValue()).sum();
        double totalLiab = liabilities.stream().mapToDouble(a -> ((Number) a.get("amount")).doubleValue()).sum();

        data.put("assets", assets);
        data.put("liabilities", liabilities);
        data.put("totalAsset", totalAsset);
        data.put("totalLiability", totalLiab);
        data.put("netWorth", totalAsset - totalLiab);
        data.put("netWorthTrend", Arrays.asList(2100000, 2180000, 2260000, 2350000, 2480000, 2605000));
        data.put("trendMonths", Arrays.asList("2月", "3月", "4月", "5月", "6月", "7月"));
        return R.ok(data);
    }

    /** 现金流:主动收入 vs 被动收入,支出结构 */
    @GetMapping("/cashflow")
    public R<Map<String, Object>> cashflow() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activeIncome", 28000);   // 工资等主动收入
        data.put("passiveIncome", 6800);   // 房租/分红/利息等被动收入
        data.put("totalExpense", 18500);
        List<Map<String, Object>> incomeItems = new ArrayList<>();
        incomeItems.add(item("工资", "主动", 28000, 0));
        incomeItems.add(item("房租收入", "被动", 4500, 0));
        incomeItems.add(item("基金分红", "被动", 1500, 0));
        incomeItems.add(item("利息", "被动", 800, 0));
        List<Map<String, Object>> expenseItems = new ArrayList<>();
        expenseItems.add(item("房贷月供", "刚性", 6000, 0));
        expenseItems.add(item("生活开销", "刚性", 7000, 0));
        expenseItems.add(item("教育", "投资型", 3000, 0));
        expenseItems.add(item("娱乐", "弹性", 2500, 0));
        data.put("incomeItems", incomeItems);
        data.put("expenseItems", expenseItems);
        return R.ok(data);
    }

    /** 财务自由目标:被动收入 vs 总支出覆盖率 */
    @GetMapping("/freedom")
    public R<Map<String, Object>> freedom() {
        Map<String, Object> data = new LinkedHashMap<>();
        double passive = 6800, expense = 18500;
        data.put("passiveIncome", passive);
        data.put("totalExpense", expense);
        data.put("coverage", Math.round(passive / expense * 1000) / 10.0); // 覆盖率%
        data.put("targetPassive", expense);
        data.put("gap", expense - passive);
        data.put("advice", "当前被动收入覆盖支出的 " + Math.round(passive / expense * 100) + "%,距财务自由还需每月增加被动现金流 " + (int) (expense - passive) + " 元。可优先扩大高股息资产与出租类真资产配置。");
        return R.ok(data);
    }

    private Map<String, Object> item(String name, String type, double amount, double yield) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("amount", amount);
        m.put("yield", yield); // 年化收益率%,负债为负
        return m;
    }
}
