package com.gjp.dify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能体返回的结构化结果。Dify 不写库，只负责抽出候选流水。
 */
public class DifyParseResult {

    private boolean relevant = true;
    private String reason;
    private List<Map<String, Object>> records = new ArrayList<>();

    public boolean isRelevant() {
        return relevant;
    }

    public void setRelevant(boolean relevant) {
        this.relevant = relevant;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<Map<String, Object>> getRecords() {
        return records;
    }

    public void setRecords(List<Map<String, Object>> records) {
        this.records = records == null ? new ArrayList<>() : records;
    }
}
