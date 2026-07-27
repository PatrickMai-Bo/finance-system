package com.finance.controller;

import com.finance.common.R;
import com.finance.service.MockDataService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页:卡片式展示 5 大板块核心内容
 */
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final MockDataService mock;

    public HomeController(MockDataService mock) {
        this.mock = mock;
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();

        // 板块一 财务概览
        Map<String, Object> finance = new LinkedHashMap<>();
        finance.put("netWorth", 2605000);
        finance.put("passiveIncome", 6800);
        finance.put("freedomCoverage", 36.8);
        data.put("finance", finance);

        // 板块二 基金 TOP3
        data.put("topFunds", mock.fundList("全部").stream().limit(3).collect(Collectors.toList()));
        // 板块三 股票 TOP3
        data.put("topStocks", mock.stockList().stream().limit(3).collect(Collectors.toList()));

        // 板块四 决策入口
        data.put("mentalModelCount", 8);
        // 板块五 AI 状态
        data.put("aiReady", true);

        data.put("marketNote", "策略:估值优先、留足安全边际、扩大被动现金流。");
        return R.ok(data);
    }
}
