package com.gjp.dedup;

import com.gjp.entity.Record;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一组疑似重复的流水。
 *
 * 刻意**不提供**"自动删除"能力：金额时间相同的两笔完全可能都是真实消费
 * （同一天在同一家店买了两杯一样的咖啡）。系统只负责把可疑的挑出来并说明理由，
 * 删哪条由用户决定 —— 这也是需求里"把删除权交给用户"的意思。
 */
public class DuplicateGroup {

    /** 组内相同的金额 */
    private BigDecimal amount;
    /** 组内流水条数 */
    private int count;
    /** 组内最大日期差（天）。0 表示完全同日 */
    private int maxDayDiff;
    /** 匹配类型：完全一致 / 高度相似 */
    private String matchType;
    /** 判定理由，前端直接显示给用户 */
    private String reason;
    /** 建议保留的流水ID（组内最早录入的那条），前端默认帮用户勾上其余的 */
    private Long suggestKeepId;
    /** 组内全部流水明细 */
    private List<Record> records;

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getMaxDayDiff() {
        return maxDayDiff;
    }

    public void setMaxDayDiff(int maxDayDiff) {
        this.maxDayDiff = maxDayDiff;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getSuggestKeepId() {
        return suggestKeepId;
    }

    public void setSuggestKeepId(Long suggestKeepId) {
        this.suggestKeepId = suggestKeepId;
    }

    public List<Record> getRecords() {
        return records;
    }

    public void setRecords(List<Record> records) {
        this.records = records;
    }
}
