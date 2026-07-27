package com.finance.controller;

import com.finance.common.R;
import com.finance.service.HoldingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 存量资产负债表 API。
 * 用户自行填入现在拥有的资产 / 背负的负债 → 增删改查 → 净资产与现金流汇总。
 */
@RestController
@RequestMapping("/api/holding")
public class HoldingController {

    private final HoldingService holding;

    public HoldingController(HoldingService holding) {
        this.holding = holding;
    }

    /** 新增。body: {bigType(资产/负债), name, amount, monthlyCashflow, note} */
    @PostMapping("/add")
    public R<Map<String, Object>> add(@RequestBody Map<String, Object> body) {
        return R.ok(holding.add(
                str(body.get("bigType"), "资产"),
                str(body.get("name"), ""),
                num(body.get("amount")),
                num(body.get("monthlyCashflow")),
                str(body.get("note"), "")
        ));
    }

    /** 修改。 */
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> r = holding.update(id,
                str(body.get("bigType"), "资产"),
                str(body.get("name"), ""),
                num(body.get("amount")),
                num(body.get("monthlyCashflow")),
                str(body.get("note"), ""));
        return r == null ? R.fail("条目不存在") : R.ok(r);
    }

    /** 删除。 */
    @DeleteMapping("/{id}")
    public R<Boolean> remove(@PathVariable long id) {
        return R.ok(holding.remove(id));
    }

    /** 全部条目(资产在前、负债在后)。 */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list() {
        return R.ok(holding.list());
    }

    /** 富爸爸式净资产 + 现金流汇总。 */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(holding.summary());
    }

    private String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }
}
