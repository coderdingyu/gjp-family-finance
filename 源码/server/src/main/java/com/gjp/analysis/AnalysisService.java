package com.gjp.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gjp.common.AppTime;
import com.gjp.common.BizException;
import com.gjp.common.UserContext;
import com.gjp.dify.DifyProperties;
import com.gjp.dify.DifyWorkflowClient;
import com.gjp.stat.DateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class AnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private static final long TTL_MILLIS = 180_000;
    private final AnalysisDataService dataService;
    private final DifyProperties config;
    private final DifyWorkflowClient workflow;
    private final ObjectMapper json;
    // 最多 64 份、三分钟。每次本地分析重新取数，数据变化即产生新指纹。
    private final Map<String, Entry> reports = new LinkedHashMap<>();
    public AnalysisService(AnalysisDataService dataService, DifyProperties config, DifyWorkflowClient workflow, ObjectMapper json) {
        this.dataService = dataService; this.config = config; this.workflow = workflow; this.json = json;
    }
    private record Scope(Long user, Long family, int role, Long boundMember, Integer sessionVersion, Long member) {
        static Scope current(Long requestedMember) {
            Long effective = UserContext.resolveMemberId(requestedMember);
            return new Scope(UserContext.getUserId(), UserContext.getFamilyId(), UserContext.getRole(),
                    UserContext.get().getMemberId(), UserContext.get().getSessionVersion(), effective);
        }
    }
    private record Enhancement(String status, String message, List<AnalysisItem> insights) {}
    private static class Entry {
        final AnalysisReport base;
        final Scope scope;
        final String key, snapshot;
        final long expires;
        volatile Enhancement enhancement;
        Entry(AnalysisReport base, Scope scope, String key, String snapshot) {
            this.base = base; this.scope = scope; this.key = key; this.snapshot = snapshot;
            expires = System.currentTimeMillis() + TTL_MILLIS;
        }
    }
    public boolean agentConfigured() { return config.analysisConfigured(); }
    public AnalysisReport report(DateRange range, Long requestedMemberId, boolean refresh) {
        AnalysisData d = dataService.load(range, requestedMemberId);
        AnalysisReport result = AnalysisRules.evaluate(d, AppTime.today());
        result.aiConfigured = agentConfigured();
        if (!result.aiConfigured) result.aiMessage = "未配置 AI，当前为本地规则报告";
        Scope scope = Scope.current(d.memberId);
        try {
            String snapshot = json.writeValueAsString(Map.of("ruleVersion", result.ruleVersion,
                    "range", d.range.toString(), "scope", d.memberId == null ? "family" : "member",
                    "findings", result.items, "quality", result.quality));
            String key = digest(json.writeValueAsString(scope) + json.writeValueAsString(result)
                    + config.analysisBaseUrlOrDefault() + config.getAnalysisApiKey() + AnalysisItems.RULE_TABLE);
            synchronized (reports) {
                reports.values().removeIf(e -> e.expires <= System.currentTimeMillis());
                if (refresh) reports.values().removeIf(e -> e.key.equals(key));
                if (!refresh) for (Entry entry : reports.values()) if (entry.key.equals(key)) return copy(entry);
                while (reports.size() >= 64) reports.remove(reports.keySet().iterator().next());
                result.reportId = UUID.randomUUID().toString(); result.generatedAt = Instant.now().toString();
                Entry entry = new Entry(result, scope, key, snapshot);
                reports.put(result.reportId, entry); return copy(entry);
            }
        } catch (Exception ex) { throw new BizException("生成分析快照失败，请重试"); }
    }
    public AnalysisReport enhance(String reportId) {
        Entry entry;
        synchronized (reports) { entry = reports.get(reportId); }
        if (entry == null || entry.expires <= System.currentTimeMillis()) throw new BizException("报告已过期，请重新分析");
        if (!entry.scope.equals(Scope.current(entry.scope.member()))) throw new BizException(403, "无权访问该分析快照");
        if (!agentConfigured() || entry.base.items.stream().anyMatch(i -> "A0".equals(i.getCode()))) return copy(entry);
        // 同快照请求合并，不持有全局锁，不在数据库事务中等待网络。
        synchronized (entry) {
            if (entry.enhancement == null) {
                try {
                    String raw = workflow.run(config.analysisBaseUrlOrDefault(), config.getAnalysisApiKey(),
                            "gjp-u" + UserContext.getUserId(), config.getAnalysisTimeoutSeconds(),
                            Map.of("snapshot", entry.snapshot, "rule_table", AnalysisItems.RULE_TABLE));
                    List<AnalysisItem> merged = AnalysisItems.merge(entry.base.items, raw);
                    entry.enhancement = new Enhancement("enhanced", "AI 已生成重点与下一步；事实由本机渲染",
                            merged.stream().filter(i -> AnalysisItems.isExtraCode(i.getCode())).toList());
                } catch (Exception ex) {
                    log.warn("分析增强失败，保留本地事实（{}）", ex.getClass().getSimpleName());
                    entry.enhancement = new Enhancement("fallback", "AI 暂不可用或协议不匹配，已保留完整本地报告", List.of());
                }
            }
            return copy(entry);
        }
    }
    private static String digest(String input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    }
    private static AnalysisReport copy(Entry entry) {
        AnalysisReport source = entry.base, r = new AnalysisReport();
        r.reportId = source.reportId; r.generatedAt = source.generatedAt; r.ruleVersion = source.ruleVersion;
        r.startDate = source.startDate; r.endDate = source.endDate; r.memberId = source.memberId;
        r.scopeLabel = source.scopeLabel; r.totals = source.totals; r.quality = source.quality;
        r.notices = source.notices; r.items = source.items; r.aiConfigured = source.aiConfigured;
        Enhancement enhanced = entry.enhancement;
        r.aiStatus = enhanced == null ? source.aiStatus : enhanced.status();
        r.aiMessage = enhanced == null ? source.aiMessage : enhanced.message();
        r.aiInsights = enhanced == null ? List.of() : enhanced.insights();
        return r;
    }
}
