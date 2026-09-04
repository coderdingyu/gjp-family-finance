package com.gjp.asset.quote;

import java.math.BigDecimal;

/** One successful market quote (stock or fund). */
public class QuoteSnapshot {

    private String name;
    private BigDecimal lastPrice;
    private String quoteTime;
    private String source;

    public QuoteSnapshot() {
    }

    public QuoteSnapshot(String name, BigDecimal lastPrice, String quoteTime, String source) {
        this.name = name;
        this.lastPrice = lastPrice;
        this.quoteTime = quoteTime;
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
