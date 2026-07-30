package com.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大模型配置(全局唯一 AI 配置源)。
 * OpenAI 兼容:每个模型配 base_url + api_key + model 名,可随时新增/切换。
 * 持久化:每次变更自动写入 data/llm-configs.json,重启不丢 Key;步骤④可再落库。
 */
@Service
public class LlmConfigService {

    private final Map<Long, Map<String, Object>> store = new LinkedHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);
    private Long activeId;

    private final ObjectMapper om = new ObjectMapper();
    private final File file;

    public LlmConfigService() {
        String path = System.getenv().getOrDefault("LLM_CONFIG_FILE",
                System.getProperty("user.dir") + File.separator + "data" + File.separator + "llm-configs.json");
        this.file = new File(path);
        if (!loadFromFile()) {
            // 首批优先:DeepSeek + 阿里百炼(api_key 留空,用户在面板自填)
            add(preset("DeepSeek", "https://api.deepseek.com", "deepseek-chat", "", false));
            add(preset("阿里百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "", true));
            add(preset("Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "", false));
            add(preset("智谱GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-plus", "", false));
            activeId = 1L;
            saveToFile();
        }
    }

    private Map<String, Object> preset(String name, String baseUrl, String model, String key, boolean enableSearch) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("baseUrl", baseUrl);
        m.put("model", model);
        m.put("apiKey", key);
        m.put("enableSearch", enableSearch); // 百炼 qwen 系列支持原生联网搜索
        return m;
    }

    // ================= 持久化 =================

    @SuppressWarnings("unchecked")
    private synchronized boolean loadFromFile() {
        try {
            if (!file.exists()) return false;
            Map<String, Object> root = om.readValue(file, Map.class);
            List<Map<String, Object>> items = (List<Map<String, Object>>) root.getOrDefault("items", List.of());
            if (items.isEmpty()) return false;
            long maxId = 0;
            for (Map<String, Object> it : items) {
                long id = ((Number) it.get("id")).longValue();
                Map<String, Object> cfg = new LinkedHashMap<>(it);
                cfg.remove("id");
                store.put(id, cfg);
                maxId = Math.max(maxId, id);
            }
            seq.set(maxId + 1);
            Object act = root.get("activeId");
            activeId = act == null ? store.keySet().stream().findFirst().orElse(null) : ((Number) act).longValue();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized void saveToFile() {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map.Entry<Long, Map<String, Object>> e : store.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<>(e.getValue());
                m.put("id", e.getKey());
                items.add(m);
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("activeId", activeId);
            root.put("items", items);
            om.writerWithDefaultPrettyPrinter().writeValue(file, root);
            System.out.println("[LlmConfigService] 配置已持久化 → " + file.getAbsolutePath() + " (items=" + items.size() + ")");
        } catch (Exception e) {
            // 以前吞错导致内存/磁盘脱钩(改了Key但文件没更新)。现在记日志,方便排查;内存依然保留。
            System.err.println("[LlmConfigService] 持久化失败(内存仍是最新的): " + file.getAbsolutePath() + " → " + e.getMessage());
        }
    }

    // ================= CRUD =================

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> res = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Object>> e : store.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>(e.getValue());
            m.put("id", e.getKey());
            m.put("active", e.getKey().equals(activeId));
            // 脱敏返回 key
            Object k = m.get("apiKey");
            m.put("apiKeyMasked", maskKey(k == null ? "" : k.toString()));
            m.remove("apiKey");
            res.add(m);
        }
        return res;
    }

    public Long add(Map<String, Object> cfg) {
        Long id = seq.getAndIncrement();
        store.put(id, new LinkedHashMap<>(cfg));
        if (activeId == null) activeId = id;
        saveToFile();
        return id;
    }

    public boolean update(Long id, Map<String, Object> cfg) {
        if (!store.containsKey(id)) return false;
        Map<String, Object> old = store.get(id);
        cfg.forEach((k, v) -> { if (v != null && !"".equals(v)) old.put(k, v); });
        saveToFile();
        return true;
    }

    public boolean delete(Long id) {
        boolean removed = store.remove(id) != null;
        if (removed && id.equals(activeId)) {
            activeId = store.keySet().stream().findFirst().orElse(null);
        }
        if (removed) saveToFile();
        return removed;
    }

    public boolean setActive(Long id) {
        if (!store.containsKey(id)) return false;
        activeId = id;
        saveToFile();
        return true;
    }

    /**
     * 从磁盘重新加载配置到内存(覆盖当前 store 与 activeId)。
     * 用于:①外部直接编辑了 data/llm-configs.json;②云端 volume 更新;
     * ③PUT 后怀疑写盘失败的兜底。失败时保留旧内存配置。
     */
    public synchronized RefreshResult refresh() {
        Map<Long, Map<String, Object>> oldStore = new LinkedHashMap<>(store);
        Long oldActiveId = activeId;
        long oldSeq = seq.get();
        store.clear();
        seq.set(1);
        activeId = null;
        if (!loadFromFile()) {
            // 文件不存在或解析失败,回滚内存
            store.clear();
            store.putAll(oldStore);
            activeId = oldActiveId;
            seq.set(oldSeq);
            return RefreshResult.fail("data/llm-configs.json 加载失败(文件不存在或格式错误),已保留原配置");
        }
        return RefreshResult.ok(store.size(), activeId);
    }

    public static class RefreshResult {
        public final boolean ok;
        public final String msg;
        public final int count;
        public final Long activeId;
        private RefreshResult(boolean ok, String msg, int count, Long activeId) {
            this.ok = ok; this.msg = msg; this.count = count; this.activeId = activeId;
        }
        static RefreshResult ok(int count, Long activeId) { return new RefreshResult(true, "刷新成功", count, activeId); }
        static RefreshResult fail(String msg) { return new RefreshResult(false, msg, 0, null); }
    }

    public Map<String, Object> active() {
        if (activeId == null) return null;
        Map<String, Object> m = new LinkedHashMap<>(store.get(activeId));
        m.put("id", activeId);
        return m;
    }

    /** 按 id 取完整配置(含 apiKey,仅供服务内部调用模型/测试连接使用,不对外返回) */
    public Map<String, Object> raw(Long id) {
        Map<String, Object> m = store.get(id);
        if (m == null) return null;
        Map<String, Object> copy = new LinkedHashMap<>(m);
        copy.put("id", id);
        return copy;
    }

    private String maskKey(String k) {
        if (k == null || k.length() < 6) return k == null || k.isEmpty() ? "(未配置)" : "***";
        return k.substring(0, 3) + "****" + k.substring(k.length() - 3);
    }
}
