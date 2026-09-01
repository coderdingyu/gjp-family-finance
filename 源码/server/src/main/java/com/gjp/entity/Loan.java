package com.gjp.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 贷款表 t_loan 实体（拓展功能）
 */
public class Loan {

    /** 贷款ID */
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 贷款名称 */
    private String loanName;

    /** 贷款类型：房贷/车贷/消费贷 */
    private String loanType;

    /** 贷款总额 */
    private BigDecimal totalAmount;

    /** 每月还款额 */
    private BigDecimal monthlyPayment;

    /** 总期数（月） */
    private Integer totalMonths;

    /** 已还期数 */
    private Integer paidMonths;

    /** 起始还款日 */
    private LocalDate startDate;

    /** 创建时间 */
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public String getLoanName() {
        return loanName;
    }

    public void setLoanName(String loanName) {
        this.loanName = loanName;
    }

    public String getLoanType() {
        return loanType;
    }

    public void setLoanType(String loanType) {
        this.loanType = loanType;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getMonthlyPayment() {
        return monthlyPayment;
    }

    public void setMonthlyPayment(BigDecimal monthlyPayment) {
        this.monthlyPayment = monthlyPayment;
    }

    public Integer getTotalMonths() {
        return totalMonths;
    }

    public void setTotalMonths(Integer totalMonths) {
        this.totalMonths = totalMonths;
    }

    public Integer getPaidMonths() {
        return paidMonths;
    }

    public void setPaidMonths(Integer paidMonths) {
        this.paidMonths = paidMonths;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}