package com.finance.controller;

import com.finance.common.R;
import com.finance.service.LedgerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 随手记式记账 API
 * 手动记一笔 → 系统自动按资产/负债逻辑分类 → 汇总资产负债表与现金流象限。
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledger;

    public LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    /** 记一笔。body: {date, type(income/expense), amount, desc, category?(可选,留空自动分类)} */
    @PostMapping("/add")
    public R<Map<String, Object>> add(@RequestBody Map<String, Object> body) {
        String date = String.valueOf(body.getOrDefault("date", ""));
        String type = String.valueOf(body.getOrDefault("type", "expense"));
        double amount = body.get("amount") == null ? 0 : ((Number) body.get("amount")).doubleValue();
        String desc = String.valueOf(body.getOrDefault("desc", ""));
        Object cat = body.get("category");
        String category = cat == null ? null : String.valueOf(cat);
        return R.ok(ledger.add(date, type, amount, desc, category));
    }

    /** 删除一笔 */
    @DeleteMapping("/{id}")
    public R<Boolean> remove(@PathVariable long id) {
        return R.ok(ledger.remove(id));
    }

    /** 全部流水(日期倒序) */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(ledger.list());
    }

    /** 富爸爸式汇总:资产负债表 + 现金流象限 + 诊断 */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(ledger.summary());
    }
}
