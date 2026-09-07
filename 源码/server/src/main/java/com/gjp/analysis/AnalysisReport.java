package com.gjp.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 返回本次执行状态，而不是把“配置过 Key”冒充“AI 调用成功”。 */
public class AnalysisReport {
    public String reportId;
    public String generatedAt;
    public String ruleVersion = "analysis-v2";
    public String startDate;
    public String endDate;
    public Long memberId;
    public String scopeLabel;
    public Map<String, Object> totals = new LinkedHashMap<>();
    public Map<String, Object> quality = new LinkedHashMap<>();
    public List<String> notices = new ArrayList<>();
    public List<AnalysisItem> items = new ArrayList<>();
    public boolean aiConfigured;
    public String aiStatus = "rules";
    public String aiMessage = "本地规则分析已完成";
    public List<AnalysisItem> aiInsights = new ArrayList<>();
}
