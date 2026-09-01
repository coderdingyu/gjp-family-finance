package com.gjp.stat.vo;

import java.math.BigDecimal;

/**
 * 按月汇总：收支趋势折线图的数据源。
 */
public class MonthAmount {

    /** 年月，格式 yyyy-MM */
    private String ym;

    /** 当月收入合计 */
    private BigDecimal income;

    /** 当月支出合计 */
    private BigDecimal expense;

    /** 当月结余 = 收入 - 支出，由 Service 计算 */
    private BigDecimal balance;

    public String getYm() {
        return ym;
    }

    public void setYm(String ym) {
        this.ym = ym;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public void setIncome(BigDecimal income) {
        this.income = income;
    }

    public BigDecimal getExpense() {
        return expense;
    }

    public void setExpense(BigDecimal expense) {
        this.expense = expense;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

}