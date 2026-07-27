package com.finance.controller;

import com.finance.common.R;
import com.finance.service.LlmClient;
import com.finance.service.LlmConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 板块五:大模型设置面板(全局唯一 AI 配置源,OpenAI 兼容)
 */
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final LlmConfigService svc;
    private final LlmClient client;

    public LlmController(LlmConfigService svc, LlmClient client) {
        this.svc = svc;
        this.client = client;
    }

    @GetMapping("/configs")
    public R<List<Map<String, Object>>> list() {
        return R.ok(svc.list());
    }

    @PostMapping("/configs")
    public R<Long> add(@RequestBody Map<String, Object> cfg) {
        return R.ok(svc.add(cfg));
    }

    @PutMapping("/configs/{id}")
    public R<?> update(@PathVariable Long id, @RequestBody Map<String, Object> cfg) {
        return svc.update(id, cfg) ? R.ok() : R.fail(404, "配置不存在");
    }

    @DeleteMapping("/configs/{id}")
    public R<?> delete(@PathVariable Long id) {
        return svc.delete(id) ? R.ok() : R.fail(404, "配置不存在");
    }

    @PostMapping("/active/{id}")
    public R<?> setActive(@PathVariable Long id) {
        return svc.setActive(id) ? R.ok() : R.fail(404, "配置不存在");
    }

    /** 测试连接:向该模型发一句极短对话,验证 baseUrl/apiKey/model 是否可用 */
    @PostMapping("/test/{id}")
    public R<Map<String, Object>> test(@PathVariable Long id) {
        Map<String, Object> cfg = svc.raw(id);
        if (cfg == null) return R.fail(404, "配置不存在");
        String apiKey = String.valueOf(cfg.getOrDefault("apiKey", "")).trim();
        if (apiKey.isEmpty()) return R.fail(400, "该模型尚未配置 API Key,请先编辑填入");
        long t0 = System.currentTimeMillis();
        try {
            String reply = client.chat(cfg, "请只回复两个字:连接成功");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ok", true);
            data.put("reply", reply.length() > 50 ? reply.substring(0, 50) : reply);
            data.put("latencyMs", System.currentTimeMillis() - t0);
            return R.ok(data);
        } catch (Exception e) {
            return R.fail(500, "连接失败: " + e.getMessage());
        }
    }
}
