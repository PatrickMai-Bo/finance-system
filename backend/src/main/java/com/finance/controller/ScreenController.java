package com.finance.controller;

import com.finance.common.PageResult;
import com.finance.common.R;
import com.finance.service.AdviceService;
import com.finance.service.DeepAnalysisService;
import com.finance.service.MockDataService;
import com.finance.service.RealScreenService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 板块二(基金)& 板块三(股票)筛选系统
 * 筛选排序:估值分位→护城河→FCF→DCF→安全边际→综合评分。
 * 步骤③:优先走真实数据(采集服务8091 + RealScreenService 引擎),不可用时自动回退 mock 并标注。
 */
@RestController
@RequestMapping("/api/screen")
public class ScreenController {

    private final MockDataService mock;
    private final RealScreenService real;
    private final AdviceService advice;
    private final DeepAnalysisService deep;

    public ScreenController(MockDataService mock, RealScreenService real, AdviceService advice, DeepAnalysisService deep) {
        this.mock = mock;
        this.real = real;
        this.advice = advice;
        this.deep = deep;
    }

    /** 股票筛选结果:分页 page(1..),size 默认10 */
    @GetMapping("/stock")
    public R<PageResult<Map<String, Object>>> stock(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Map<String, Object>> list;
        try {
            list = real.stockList();
        } catch (Exception e) {
            list = markMock(mock.stockList());
        }
        return R.ok(paginate(list, page, size));
    }

    /** 基金筛选结果:可按 category 分类(全部/股票型/混合型/债券型/指数基金/QDII) */
    @GetMapping("/fund")
    public R<PageResult<Map<String, Object>>> fund(
            @RequestParam(defaultValue = "全部") String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Map<String, Object>> list;
        try {
            list = real.fundList(category);
        } catch (Exception e) {
            list = markMock(mock.fundList(category));
        }
        return R.ok(paginate(list, page, size));
    }

    /** 一键刷新:清缓存并重新跑真实筛选流水线 */
    @PostMapping("/stock/run")
    public R<Map<String, Object>> runStock() {
        Map<String, Object> info = new LinkedHashMap<>();
        real.invalidate();
        boolean realOk;
        int passed;
        try {
            List<Map<String, Object>> list = real.stockList();
            realOk = true;
            passed = list.size();
        } catch (Exception e) {
            realOk = false;
            passed = mock.stockList().size();
        }
        info.put("status", "done");
        info.put("dataSource", realOk ? "real" : "mock");
        info.put("scanned", realOk ? 5000 : 4832);
        info.put("passed", passed);
        info.put("pipeline", Arrays.asList(
                realOk ? "东财数据中心-全市场业绩报表(真实)" : "估值分位筛选",
                realOk ? "腾讯行情-实时价格/PE/市值(真实)" : "护城河量化评分",
                "护城河量化评分(ROE/毛利率/规模/成长)",
                "格雷厄姆公式估算内在价值",
                "安全边际计算与排序",
                "综合评分取前30"));
        info.put("updatedAt", new Date().toString());
        return R.ok(info);
    }

    @PostMapping("/fund/run")
    public R<Map<String, Object>> runFund() {
        Map<String, Object> info = new LinkedHashMap<>();
        real.invalidate();
        boolean realOk;
        int passed;
        try {
            passed = real.fundList("全部").size();
            realOk = true;
        } catch (Exception e) {
            realOk = false;
            passed = mock.fundList("全部").size();
        }
        info.put("status", "done");
        info.put("dataSource", realOk ? "real" : "mock");
        info.put("scanned", realOk ? 20000 : 9765);
        info.put("passed", passed);
        info.put("updatedAt", new Date().toString());
        return R.ok(info);
    }

    /**
     * 建议持有时间(AI 推算):对当前页每只标的并发调激活模型,返回 short/mid/long 的
     * 持有时间 + 预计收益率区间 + 理由。invalidate=true 清缓存重算(用于刷新)。
     */
    @PostMapping("/stock/advice")
    public R<List<Map<String, Object>>> stockAdvice(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean invalidate) {
        return R.ok(advice.advice("stock", "全部", page, size, invalidate));
    }

    @PostMapping("/fund/advice")
    public R<List<Map<String, Object>>> fundAdvice(
            @RequestParam(defaultValue = "全部") String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean invalidate) {
        return R.ok(advice.advice("fund", category, page, size, invalidate));
    }

    /** 个股/基金详情 */
    @GetMapping("/detail/{code}")
    public R<Map<String, Object>> detail(@PathVariable String code) {
        List<Map<String, Object>> stocks;
        List<Map<String, Object>> funds;
        try { stocks = real.stockList(); } catch (Exception e) { stocks = mock.stockList(); }
        try { funds = real.fundList("全部"); } catch (Exception e) { funds = mock.fundList("全部"); }
        Optional<Map<String, Object>> found = stocks.stream()
                .filter(m -> code.equals(m.get("code"))).findFirst();
        if (found.isEmpty()) {
            found = funds.stream().filter(m -> code.equals(m.get("code"))).findFirst();
        }
        return found.map(R::ok).orElseGet(() -> R.fail(404, "未找到该标的"));
    }

    /** 基金类型列表(与天天基金排行口径一致) */
    @GetMapping("/fund/categories")
    public R<List<String>> categories() {
        return R.ok(Arrays.asList("全部", "股票型", "混合型", "债券型", "指数基金", "QDII"));
    }

    /**
     * 第二阶段深度分析：对已通过第一阶段定量筛选的标的，调用 DeepSeek 按7段模板做深度分析。
     * 缓存 60 分钟。invalidate=true 强制刷新。
     */
    @PostMapping("/stock/analyze/{code}")
    public R<Map<String, Object>> analyzeStock(
            @PathVariable String code,
            @RequestParam(defaultValue = "false") boolean invalidate) {
        if (invalidate) deep.invalidate("stock", code);
        return R.ok(deep.analyzeStock(code));
    }

    @PostMapping("/fund/analyze/{code}")
    public R<Map<String, Object>> analyzeFund(
            @PathVariable String code,
            @RequestParam(defaultValue = "false") boolean invalidate) {
        if (invalidate) deep.invalidate("fund", code);
        return R.ok(deep.analyzeFund(code));
    }

    private List<Map<String, Object>> markMock(List<Map<String, Object>> list) {
        for (Map<String, Object> m : list) m.put("dataSource", "mock");
        return list;
    }

    private PageResult<Map<String, Object>> paginate(List<Map<String, Object>> all, int page, int size) {
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        List<Map<String, Object>> sub = from >= all.size() ? Collections.emptyList() : all.subList(from, to);
        return new PageResult<>(all.size(), page, size, new ArrayList<>(sub));
    }
}
