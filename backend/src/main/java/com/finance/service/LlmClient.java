package com.finance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容大模型 HTTP 客户端。
 * 支持 DeepSeek / 阿里百炼(DashScope compatible-mode) / Kimi / 智谱 等所有 OpenAI 兼容接口。
 * 百炼 qwen 系列支持 enable_search=true 原生联网搜索(无需额外搜索接口)。
 */
@Component
public class LlmClient {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final String SYSTEM_PROMPT =
            "你是一位融合价值投资与财务智慧的" +
            "个人理财与决策顾问。回答要求:①严格基于用户提供的数据分析,不编造数据;②结构化输出(分点/分维度);" +
            "③结论明确、保守务实;④中文回答;⑤末尾不需要重复免责声明。";

    /**
     * 调用 chat/completions,返回模型回复文本。失败抛出带可读信息的异常。
     */
    public String chat(Map<String, Object> cfg, String userPrompt) throws Exception {
        String baseUrl = str(cfg.get("baseUrl"));
        String apiKey = str(cfg.get("apiKey"));
        String model = str(cfg.get("model"));
        boolean enableSearch = Boolean.parseBoolean(String.valueOf(cfg.getOrDefault("enableSearch", "false")));

        if (baseUrl.isEmpty() || apiKey.isEmpty() || model.isEmpty()) {
            throw new IllegalStateException("模型配置不完整(baseUrl/apiKey/model)");
        }

        // 组装请求体
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userPrompt));
        body.put("messages", messages);
        body.put("temperature", 0.6);
        body.put("stream", false);
        // 阿里百炼(DashScope)原生联网搜索
        if (enableSearch && baseUrl.contains("dashscope")) {
            body.put("enable_search", true);
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint(baseUrl)))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("模型接口返回 " + resp.statusCode() + ": " + trim(resp.body(), 300));
        }
        JsonNode root = mapper.readTree(resp.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isEmpty()) {
            throw new RuntimeException("模型返回内容为空: " + trim(resp.body(), 300));
        }
        return content.asText();
    }

    /** baseUrl 规范化为完整 chat/completions 端点 */
    private String endpoint(String baseUrl) {
        String u = baseUrl.trim();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith("/chat/completions")) return u;
        return u + "/chat/completions";
    }

    private String str(Object o) { return o == null ? "" : o.toString().trim(); }

    private String trim(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
