package com.gjp.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产表 t_asset 实体（拓展功能）。
 * V4 起含行情/计息/估值用的可空字段；老数据这些列为 null，列表仍按原 amount 展示。
 */
public class Asset {

    /** 资产ID */
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 资产名称 */
    private String assetName;

    /** 资产类型：房产/车辆/存款/股票/基金/其他 */
    private String assetType;

    /** 当前价值（元）。列表接口可能被替换为实时估值，库内值见 storedAmount */
    private BigDecimal amount;

    /** 取得成本（元） */
    private BigDecimal cost;

    /** 取得日期 */
    private LocalDate buyDate;

    /** 备注 */
    private String remark;

    /** 股票/基金代码 */
    private String symbol;

    /** 持仓数量 */
    private BigDecimal shares;

    /** 年利率%（存款/理财） */
    private BigDecimal annualRate;

    /** 存期月 */
    private Integer termMonths;

    /** simple / compound_year / compound_month */
    private String interestMethod;

    /** 车型 */
    private String carModel;

    /** 城市 */
    private String city;

    /** 小区 */
    private String community;

    /** 面积㎡ */
    private BigDecimal areaSqm;

    /** 里程 km */
    private Integer mileageKm;

    /** 车份（年款） */
    private Integer modelYear;

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

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public LocalDate getBuyDate() {
        return buyDate;
    }

    public void setBuyDate(LocalDate buyDate) {
        this.buyDate = buyDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getShares() {
        return shares;
    }

    public void setShares(BigDecimal shares) {
        this.shares = shares;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public void setAnnualRate(BigDecimal annualRate) {
        this.annualRate = annualRate;
    }

    public Integer getTermMonths() {
        return termMonths;
    }

    public void setTermMonths(Integer termMonths) {
        this.termMonths = termMonths;
    }

    public String getInterestMethod() {
        return interestMethod;
    }

    public void setInterestMethod(String interestMethod) {
        this.interestMethod = interestMethod;
    }

    public String getCarModel() {
        return carModel;
    }

    public void setCarModel(String carModel) {
        this.carModel = carModel;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCommunity() {
        return community;
    }

    public void setCommunity(String community) {
        this.community = community;
    }

    public BigDecimal getAreaSqm() {
        return areaSqm;
    }

    public void setAreaSqm(BigDecimal areaSqm) {
        this.areaSqm = areaSqm;
    }

    public Integer getMileageKm() {
        return mileageKm;
    }

    public void setMileageKm(Integer mileageKm) {
        this.mileageKm = mileageKm;
    }

    public Integer getModelYear() {
        return modelYear;
    }

    public void setModelYear(Integer modelYear) {
        this.modelYear = modelYear;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
