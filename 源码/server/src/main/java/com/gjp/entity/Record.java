package com.gjp.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收支流水表 t_record 实体（核心表）
 */
public class Record {

    /** 流水ID */
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 所属成员ID */
    private Long memberId;

    /** 分类ID */
    private Long categoryId;

    /** 类型：1=收入 2=支出 */
    private Integer type;

    /** 金额 */
    private BigDecimal amount;

    /** 发生日期 */
    private LocalDate recordDate;

    /** 商家名称，如“海底捞” */
    private String merchant;

    /** 消费片区，如“城东” */
    private String area;

    /** 支付方式：现金/微信/支付宝/银行卡 */
    private String payMethod;

    /** 是否人情往来：1=是 0=否 */
    private Integer isGift;

    /** 备注 */
    private String remark;

    /** 订单号/商单号/交易单号，可空；同家庭相同订单号视为强重复 */
    private String orderNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 成员姓名，联表查询用，非数据库字段 */
    private String memberName;

    /** 分类名称，联表查询用，非数据库字段 */
    private String categoryName;

    /** 直接父分类名称，联表查询用，非数据库字段 */
    private String parentCategoryName;

    /** 所属一级分类名称，联表查询用，非数据库字段 */
    private String rootCategoryName;

    /** 分类层级 1/2/3，前端据此拼分类路径，非数据库字段 */
    private Integer categoryLevel;

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

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(String payMethod) {
        this.payMethod = payMethod;
    }

    public Integer getIsGift() {
        return isGift;
    }

    public void setIsGift(Integer isGift) {
        this.isGift = isGift;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getParentCategoryName() {
        return parentCategoryName;
    }

    public void setParentCategoryName(String parentCategoryName) {
        this.parentCategoryName = parentCategoryName;
    }

    public String getRootCategoryName() {
        return rootCategoryName;
    }

    public void setRootCategoryName(String rootCategoryName) {
        this.rootCategoryName = rootCategoryName;
    }

    public Integer getCategoryLevel() {
        return categoryLevel;
    }

    public void setCategoryLevel(Integer categoryLevel) {
        this.categoryLevel = categoryLevel;
    }

}