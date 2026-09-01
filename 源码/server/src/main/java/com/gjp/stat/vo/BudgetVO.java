package com.gjp.stat.vo;

import java.math.BigDecimal;

/**
 * 成员预算执行情况：预算 vs 实际支出，超支预警的数据基础。
 */
public class BudgetVO {

    /** 成员ID */
    private Long memberId;

    /** 成员姓名 */
    private String memberName;

    /** 月度预算 */
    private BigDecimal budget;

    /** 当月实际支出 */
    private BigDecimal expense;

    /** 预算使用率（%） */
    private BigDecimal usedRate;

    /** 状态：正常/接近上限/已超支/未设预算 */
    private String status;

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public BigDecimal getExpense() {
        return expense;
    }

    public void setExpense(BigDecimal expense) {
        this.expense = expense;
    }

    public BigDecimal getUsedRate() {
        return usedRate;
    }

    public void setUsedRate(BigDecimal usedRate) {
        this.usedRate = usedRate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}