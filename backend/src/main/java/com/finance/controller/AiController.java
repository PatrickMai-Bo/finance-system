package com.finance.controller;

import com.finance.common.R;
import com.finance.service.AiService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 板块五:统一 AI 分析入口。
 * 各板块(财务/基金/股票/决策)统一调此接口,scene 区分来源板块。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService ai;

    public AiController(AiService ai) {
        this.ai = ai;
    }

    /**
     * body: { "scene": "stock|fund|finance|decision", "payload": {...业务数据...} }
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/analyze")
    public R<Map<String, Object>> analyze(@RequestBody Map<String, Object> body) {
        String scene = body.getOrDefault("scene", "").toString();
        Object p = body.get("payload");
        Map<String, Object> payload = p instanceof Map ? (Map<String, Object>) p : null;
        return R.ok(ai.analyze(scene, payload));
    }
}
