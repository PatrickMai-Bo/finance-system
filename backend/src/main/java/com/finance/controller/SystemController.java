package com.finance.controller;

import com.finance.common.R;
import com.finance.service.DeepAnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 系统级辅助接口:
 *  - GET  /api/system/online      返回当前在线人数(最近 2 分钟内有心跳的 session 数)
 *  - POST /api/system/ping        前端每 30s 调用,刷新自身 session 的最后活跃时间
 *  - POST /api/system/warmup      一键后台预热基金/股票深度分析(写入缓存),首次访问显著加速板块页加载
 *
 * 缓存命中策略由 DeepAnalysisService 自身控制(60 分钟 TTL),
 * 本端点对已热缓存基本 0 成本,只对冷启动触发真实分析。
 */
@RestController
@RequestMapping("/api/system")
public class SystemController {

    /** sessionId -> 最后心跳时间戳(毫秒) */
    private static final ConcurrentHashMap<String, Long> ACTIVE = new ConcurrentHashMap<>();
    /** 判定"在线"的时间窗:2 分钟.心跳间隔 30s,丢包容忍度高 */
    private static final long ONLINE_WINDOW_MS = 2 * 60 * 1000L;

    private static final ScheduledExecutorService CLEANER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "online-cleaner");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean WARMING = new AtomicBoolean(false);
    private static volatile long LAST_WARM_AT = 0L;
    /** 预热节流:5 分钟内不重复触发 */
    private static final long WARM_THROTTLE_MS = 5 * 60 * 1000L;

    @Autowired
    private DeepAnalysisService deep;

    static {
        CLEANER.scheduleAtFixedRate(SystemController::evictStale, 60, 60, TimeUnit.SECONDS);
    }

    /** 获取在线人数 + 是否我自己在线 */
    @GetMapping("/online")
    public R<Map<String, Object>> online(HttpServletRequest req) {
        long now = System.currentTimeMillis();
        String sid = req.getSession(true).getId();
        ACTIVE.put(sid, now);
        int count = countOnline(now);
        return R.ok(Map.of(
                "online", count,
                "selfOnline", true,
                "ts", now
        ));
    }

    /** 前端 30s 心跳:刷新自身时间戳 */
    @PostMapping("/ping")
    public R<Map<String, Object>> ping(HttpServletRequest req) {
        long now = System.currentTimeMillis();
        String sid = req.getSession(true).getId();
        ACTIVE.put(sid, now);
        return R.ok(Map.of(
                "online", countOnline(now),
                "selfOnline", true,
                "ts", now
        ));
    }

    /**
     * 后台预热基金+股票深度分析:
     *  - 命中 DeepAnalysisService 自身缓存(60min TTL) → 基本 0 成本
     *  - 冷启动才真跑 ~60s,启动单线程后台任务,接口立即返回
     *  - 5 分钟节流,防止重复触发
     */
    @PostMapping("/warmup")
    public R<Map<String, Object>> warmup() {
        long now = System.currentTimeMillis();
        long since = now - LAST_WARM_AT;
        boolean alreadyRunning = WARMING.get();
        if (alreadyRunning) {
            return R.ok(Map.of(
                    "status", "running",
                    "msg", "后台预热已在进行中"
            ));
        }
        if (since < WARM_THROTTLE_MS) {
            return R.ok(Map.of(
                    "status", "throttled",
                    "msg", "最近已预热过(剩 " + (WARM_THROTTLE_MS - since) / 1000 + "s 冷却)"
            ));
        }
        WARMING.set(true);
        LAST_WARM_AT = now;
        Thread t = new Thread(() -> {
            try {
                deep.refinedStockList(false);
                deep.refinedFundList("全部", false);
            } catch (Exception ignored) {
                // 后台预热失败不抛,日志由 service 自行处理
            } finally {
                WARMING.set(false);
            }
        }, "system-warmup");
        t.setDaemon(true);
        t.start();
        return R.ok(Map.of(
                "status", "started",
                "msg", "已启动后台预热任务"
        ));
    }

    private static int countOnline(long now) {
        // 一并清理过期项
        ACTIVE.entrySet().removeIf(e -> now - e.getValue() > ONLINE_WINDOW_MS);
        return ACTIVE.size();
    }

    private static void evictStale() {
        long now = System.currentTimeMillis();
        ACTIVE.entrySet().removeIf(e -> now - e.getValue() > ONLINE_WINDOW_MS);
    }
}
