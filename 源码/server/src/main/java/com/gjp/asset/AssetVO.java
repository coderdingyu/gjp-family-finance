package com.gjp.asset;

import com.gjp.entity.Asset;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;

/**
 * Asset plus live valuation fields for list/summary. Does not persist extras.
 */
public class AssetVO extends Asset {

    /** 库里登记的当前价值，列表不会回写 */
    private BigDecimal storedAmount;

    /** stored | quote | interest | listing */
    private String valueSource;

    private BigDecimal pnl;

    private String quoteName;

    private BigDecimal lastPrice;

    private String quoteTime;

    private Integer remainDays;

    private String remainLabel;

    private BigDecimal profit;

    private String interestMethodLabel;

    /** 表格「说明」：行情 18.2×200股 / 利润+120 还有86天 / 估值来自瓜子 n 条 */
    private String estimateNote;

    private Integer sampleCount;

    private String estimateSource;

    /** 失败时短原因，不阻断列表 */
    private String estimateReason;

    public static AssetVO from(Asset asset) {
        AssetVO vo = new AssetVO();
        if (asset != null) {
            BeanUtils.copyProperties(asset, vo);
            vo.setStoredAmount(asset.getAmount());
            vo.setAmount(asset.getAmount());
            vo.setValueSource("stored");
        }
        return vo;
    }

    public BigDecimal getStoredAmount() {
        return storedAmount;
    }

    public void setStoredAmount(BigDecimal storedAmount) {
        this.storedAmount = storedAmount;
    }

    public String getValueSource() {
        return valueSource;
    }

    public void setValueSource(String valueSource) {
        this.valueSource = valueSource;
    }

    public BigDecimal getPnl() {
        return pnl;
    }

    public void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }

    public String getQuoteName() {
        return quoteName;
    }

    public void setQuoteName(String quoteName) {
        this.quoteName = quoteName;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public String getQuoteTime() {
        return quoteTime;
    }

    public void setQuoteTime(String quoteTime) {
        this.quoteTime = quoteTime;
    }

    public Integer getRemainDays() {
        return remainDays;
    }

    public void setRemainDays(Integer remainDays) {
        this.remainDays = remainDays;
    }

    public String getRemainLabel() {
        return remainLabel;
    }

    public void setRemainLabel(String remainLabel) {
        this.remainLabel = remainLabel;
    }

    public BigDecimal getProfit() {
        return profit;
    }

    public void setProfit(BigDecimal profit) {
        this.profit = profit;
    }

    public String getInterestMethodLabel() {
        return interestMethodLabel;
    }

    public void setInterestMethodLabel(String interestMethodLabel) {
        this.interestMethodLabel = interestMethodLabel;
    }

    public String getEstimateNote() {
        return estimateNote;
    }

    public void setEstimateNote(String estimateNote) {
        this.estimateNote = estimateNote;
    }

    public Integer getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Integer sampleCount) {
        this.sampleCount = sampleCount;
    }

    public String getEstimateSource() {
        return estimateSource;
    }

    public void setEstimateSource(String estimateSource) {
        this.estimateSource = estimateSource;
    }

    public String getEstimateReason() {
        return estimateReason;
    }

    public void setEstimateReason(String estimateReason) {
        this.estimateReason = estimateReason;
    }
}
