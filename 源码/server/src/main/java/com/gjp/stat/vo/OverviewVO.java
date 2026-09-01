package com.gjp.stat.vo;

import java.math.BigDecimal;

/**
 * 统计总览：看板顶部的几个关键指标卡。
 */
public class OverviewVO {

    /** 区间内收入合计 */
    private BigDecimal totalIncome;

    /** 区间内支出合计 */
    private BigDecimal totalExpense;

    /** 区间内结余（家庭收益） */
    private BigDecimal balance;

    /** 流水笔数 */
    private Integer recordCount;

    /** 月均支出 */
    private BigDecimal avgMonthlyExpense;

    /** 月均收入 */
    private BigDecimal avgMonthlyIncome;

    /** 单笔最大支出 */
    private BigDecimal maxExpense;

    /** 人情往来支出合计 */
    private BigDecimal giftExpense;

    public BigDecimal getTotalIncome() {
        return totalIncome;
    }

    public void setTotalIncome(BigDecimal totalIncome) {
        this.totalIncome = totalIncome;
    }

    public BigDecimal getTotalExpense() {
        return totalExpense;
    }

    public void setTotalExpense(BigDecimal totalExpense) {
        this.totalExpense = totalExpense;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public BigDecimal getAvgMonthlyExpense() {
        return avgMonthlyExpense;
    }

    public void setAvgMonthlyExpense(BigDecimal avgMonthlyExpense) {
        this.avgMonthlyExpense = avgMonthlyExpense;
    }

    public BigDecimal getAvgMonthlyIncome() {
        return avgMonthlyIncome;
    }

    public void setAvgMonthlyIncome(BigDecimal avgMonthlyIncome) {
        this.avgMonthlyIncome = avgMonthlyIncome;
    }

    public BigDecimal getMaxExpense() {
        return maxExpense;
    }

    public void setMaxExpense(BigDecimal maxExpense) {
        this.maxExpense = maxExpense;
    }

    public BigDecimal getGiftExpense() {
        return giftExpense;
    }

    public void setGiftExpense(BigDecimal giftExpense) {
        this.giftExpense = giftExpense;
    }

}