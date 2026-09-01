package com.gjp.record;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 流水多条件查询参数。对应课程要求"多条件组合查询与分页"，
 * 所有字段都可以为空，为空即表示该条件不参与过滤。
 */
public class RecordQuery {

    /** 类型：1=收入 2=支出，null=全部 */
    private Integer type;
    /** 成员ID */
    private Long memberId;
    /** 分类ID：传一级分类时会自动把其下所有二级分类一起查出来 */
    private Long categoryId;
    /** 起始日期（含） */
    private LocalDate startDate;
    /** 结束日期（含） */
    private LocalDate endDate;
    /** 关键字：模糊匹配商家名称与备注 */
    private String keyword;
    /** 支付方式 */
    private String payMethod;
    /** 消费片区 */
    private String area;
    /** 是否人情往来：1=是 0=否 null=不限 */
    private Integer isGift;
    /** 金额下限 */
    private BigDecimal minAmount;
    /** 金额上限 */
    private BigDecimal maxAmount;

    /** 页码，从 1 开始 */
    private Integer pageNum = 1;
    /** 每页条数 */
    private Integer pageSize = 10;

    /** 供 SQL 使用的偏移量，由 Service 计算后回填 */
    private Integer offset;

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
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

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(String payMethod) {
        this.payMethod = payMethod;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public Integer getIsGift() {
        return isGift;
    }

    public void setIsGift(Integer isGift) {
        this.isGift = isGift;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }
}
