package com.gjp.asset.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TencentQuotesTest {

    static final String STOCK = "v_sh600519=\"1~贵州茅台~600519~1298.89~1297.50~1297.50~12728~5933~6787~1298.81~3~1298.45~1~1298.44~1~1298.43~8~1298.42~2~1298.90~2~1298.93~1~1298.99~6~1299.00~8~1299.09~10~~20260903140132~1.39~0.11~1305.00~1293.02~1298.89/12728/1653074446~12728~165307~0.10~19.94~~1305.00~1293.02~0.92~16237.18~16237.18~6.46~1427.25~1167.75~0.72~-12~1298.72~18.24~19.72~~~0.10~165307.4446~0.0000~0~   A~GP-A~-3.73~0.51~4.01~32.41~27.30~1539.98~1151.01~0.57~-0.74~4.09~1250081601~1250081601~-28.57~-4.93~1250081601~~~-9.08~0.08~~CNY~0~___D__F__N~1299.50~-5~\";";

    static final String FUND = "v_s_jj110022=\"110022~易方达消费行业股票~20260902~2.9260~2.9260~-1.32~-0.0391~0~1.14~926~-21.22~4734~0~萧楠~-4.00~5851~开放~开放~股票型~偏股型基金~稳健成长型~365105.0000~970596.0000~易方达基金管理有限公司~中国农业银行股份有限公司~1.2000~2010-08-20~~~-15.02~0.00~Stock~-10.30~192.60~-24.70~01011001~股票型~食品饮料~~~\";";

    @Test
    void parseStockFixture() {
        QuoteSnapshot q = TencentQuotes.parseStock(STOCK);
        assertNotNull(q);
        assertEquals("贵州茅台", q.getName());
        assertEquals(0, new BigDecimal("1298.89").compareTo(q.getLastPrice()));
        assertEquals("20260903140132", q.getQuoteTime());
        assertEquals("tencent", q.getSource());
    }

    @Test
    void parseFundFixture() {
        QuoteSnapshot q = TencentQuotes.parseFund(FUND);
        assertNotNull(q);
        assertEquals("易方达消费行业股票", q.getName());
        assertEquals(0, new BigDecimal("2.9260").compareTo(q.getLastPrice()));
        assertEquals("20260902", q.getQuoteTime());
    }

    @Test
    void inferMarket() {
        assertEquals("sh600519", TencentQuotes.toStockCode("600519"));
        assertEquals("sz000001", TencentQuotes.toStockCode("000001"));
        assertEquals("sz300750", TencentQuotes.toStockCode("300750"));
        assertEquals("sh600519", TencentQuotes.toStockCode("SH600519"));
        assertEquals("110022", TencentQuotes.toFundCode("110022"));
        assertEquals("001186", TencentQuotes.toFundCode("1186"));
    }

    @Test
    void badInputNull() {
        assertNull(TencentQuotes.parseStock(null));
        assertNull(TencentQuotes.parseStock("v_sh600519=\"1~x~600519~0.00~\";"));
        assertNull(TencentQuotes.parseStock("<html>blocked</html>"));
    }
}
