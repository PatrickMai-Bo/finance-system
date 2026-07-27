package com.finance.controller;

import com.finance.common.R;
import com.finance.service.WatchlistService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 自选清单 API(自选股 / 自选基)。
 * 顶部增删改查模块用:用户自行维护关注/持有的标的,可整体交给 AI 做组合点评。
 */
@RestController
@RequestMapping("/api/watchlist")
public class WatchlistController {

    private final WatchlistService watchlist;

    public WatchlistController(WatchlistService watchlist) {
        this.watchlist = watchlist;
    }

    /** 新增。body: {type(stock/fund), name, code, category, cost, amount, targetPrice, note} */
    @PostMapping("/add")
    public R<Map<String, Object>> add(@RequestBody Map<String, Object> b) {
        return R.ok(watchlist.add(
                str(b.get("type"), "stock"), str(b.get("name"), ""), str(b.get("code"), ""),
                str(b.get("category"), ""), num(b.get("cost")), num(b.get("amount")),
                num(b.get("targetPrice")), str(b.get("note"), "")));
    }

    /** 修改。 */
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> b) {
        Map<String, Object> r = watchlist.update(id,
                str(b.get("type"), "stock"), str(b.get("name"), ""), str(b.get("code"), ""),
                str(b.get("category"), ""), num(b.get("cost")), num(b.get("amount")),
                num(b.get("targetPrice")), str(b.get("note"), ""));
        return r == null ? R.fail("条目不存在") : R.ok(r);
    }

    /** 删除。 */
    @DeleteMapping("/{id}")
    public R<Boolean> remove(@PathVariable long id) {
        return R.ok(watchlist.remove(id));
    }

    /** 按类型列出。/list?type=stock|fund */
    @GetMapping("/list")
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) String type) {
        return R.ok(watchlist.list(type));
    }

    /** 组合汇总(供 AI 组合点评)。/summary?type=stock|fund */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary(@RequestParam(required = false) String type) {
        return R.ok(watchlist.summary(type));
    }

    private String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }
}
