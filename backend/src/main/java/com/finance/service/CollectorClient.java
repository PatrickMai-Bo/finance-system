package com.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Python 采集服务客户端(collector,默认 http://localhost:8091)。
 * 数据源:东财数据中心业绩报表 + 腾讯行情 + 天天基金排行(公开接口)。
 */
@Component
public class CollectorClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    public CollectorClient() {
        this.baseUrl = System.getenv().getOrDefault("COLLECTOR_URL", "http://localhost:8091");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean healthy() {
        try {
            Map<String, Object> r = get("/health", Duration.ofSeconds(3));
            return Boolean.TRUE.equals(r.get("ok"));
        } catch (Exception e) {
            return false;
        }
    }

    /** 优质股候选池(真实业绩+行情) */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> stockQuality(int limit) throws Exception {
        Map<String, Object> r = get("/stocks/quality?limit=" + limit, Duration.ofSeconds(240));
        if (!Boolean.TRUE.equals(r.get("ok"))) throw new IllegalStateException("collector error: " + r.get("error"));
        return (List<Map<String, Object>>) r.get("list");
    }

    /** 基金排行(真实净值/收益) */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fundRank(String ftype, int limit) throws Exception {
        String q = URLEncoder.encode(ftype, StandardCharsets.UTF_8);
        Map<String, Object> r = get("/funds/rank?ftype=" + q + "&limit=" + limit, Duration.ofSeconds(120));
        if (!Boolean.TRUE.equals(r.get("ok"))) throw new IllegalStateException("collector error: " + r.get("error"));
        return (List<Map<String, Object>>) r.get("list");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path, Duration timeout) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(timeout)
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return om.readValue(resp.body(), Map.class);
    }
}
