package com.gjp.asset.quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Fetch live stock / fund quotes. Tiantian JSONP first for funds, then Tencent.
 */
@Component
public class MarketClient {

    private static final Logger log = LoggerFactory.getLogger(MarketClient.class);

    public QuoteSnapshot fetchStock(String symbol) {
        String code = TencentQuotes.toStockCode(symbol);
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String raw = QuoteHttp.getGbk("http://qt.gtimg.cn/q=" + code);
        QuoteSnapshot s = TencentQuotes.parseStock(raw);
        if (s != null) {
            return s;
        }
        raw = QuoteHttp.getGbk("https://qt.gtimg.cn/q=" + code);
        return TencentQuotes.parseStock(raw);
    }

    public QuoteSnapshot fetchFund(String symbol) {
        String code = TencentQuotes.toFundCode(symbol);
        if (!StringUtils.hasText(code)) {
            return null;
        }
        String jsonp = QuoteHttp.getUtf8("http://fundgz.1234567.com.cn/js/" + code + ".js");
        QuoteSnapshot s = FundQuotes.parseJsonp(jsonp);
        if (s != null) {
            return s;
        }
        jsonp = QuoteHttp.getUtf8("https://fundgz.1234567.com.cn/js/" + code + ".js");
        s = FundQuotes.parseJsonp(jsonp);
        if (s != null) {
            return s;
        }
        String raw = QuoteHttp.getGbk("http://qt.gtimg.cn/q=s_jj" + code);
        s = TencentQuotes.parseFund(raw);
        if (s != null) {
            return s;
        }
        raw = QuoteHttp.getGbk("http://qt.gtimg.cn/q=jj" + code);
        s = TencentQuotes.parseFund(raw);
        if (s == null) {
            log.debug("fund quote miss {}", code);
        }
        return s;
    }
}
