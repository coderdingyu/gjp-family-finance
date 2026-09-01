package com.gjp.stat.vo;

import java.math.BigDecimal;

/**
 * 统计通用条目：分类占比、成员对比、商家排行、片区排行、支付方式占比都复用这个结构。
 */
public class AmountItem {

    /** 对应的业务ID，如分类ID/成员ID；商家、片区等无ID的维度为空 */
    private Long id;

    /** 维度名称，如“餐饮支出”“张三”“海底捞” */
    private String name;

    /** 金额合计 */
    private BigDecimal amount;

    /** 笔数 */
    private Integer count;

    /** 占比（%），由 Service 计算后回填，保留两位小数 */
    private BigDecimal ratio;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public BigDecimal getRatio() {
        return ratio;
    }

    public void setRatio(BigDecimal ratio) {
        this.ratio = ratio;
    }

}