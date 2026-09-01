package com.gjp.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 家庭成员表 t_member 实体
 */
public class Member {

    /** 成员ID */
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 成员姓名 */
    private String memberName;

    /** 家庭关系：本人/配偶/子女/父母/其他 */
    private String relation;

    /** 月度预算金额，用于超支预警 */
    private BigDecimal monthlyBudget;

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

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getRelation() {
        return relation;
    }

    public void setRelation(String relation) {
        this.relation = relation;
    }

    public BigDecimal getMonthlyBudget() {
        return monthlyBudget;
    }

    public void setMonthlyBudget(BigDecimal monthlyBudget) {
        this.monthlyBudget = monthlyBudget;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}