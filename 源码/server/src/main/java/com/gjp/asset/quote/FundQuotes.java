package com.gjp.asset.quote;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 天天基金 JSONP: jsonpgz({"gsz":"1.23", "name":"...", "gztime":"..."});
 * gs = estimated NAV. Parser is fixture-testable; no network here.
 */
public final class FundQuotes {

    private static final Pattern GSZ = Pattern.compile("\"gsz\"\\s*:\\s*\"?([0-9]+(?:\\.[0-9]+)?)\"");
    private static final Pattern GSZ_BARE = Pattern.compile("\"gsz\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern GZTIME = Pattern.compile("\"gztime\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DWJZ = Pattern.compile("\"dwjz\"\\s*:\\s*\"?([0-9]+(?:\\.[0-9]+)?)\"");

    private FundQuotes() {
    }

    public static QuoteSnapshot parseJsonp(String raw) {
        if (raw == null || raw.isBlank() || raw.contains("页面未找到")) {
            return null;
        }
        BigDecimal gsz = firstDecimal(raw, GSZ);
        if (gsz == null) {
            gsz = firstDecimal(raw, GSZ_BARE);
        }
        if (gsz == null) {
            gsz = firstDecimal(raw, DWJZ);
        }
        if (gsz == null || gsz.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        String name = firstGroup(raw, NAME);
        String time = firstGroup(raw, GZTIME);
        return new QuoteSnapshot(name, gsz, time, "tiantian");
    }

    private static BigDecimal firstDecimal(String raw, Pattern p) {
        Matcher m = p.matcher(raw);
        if (!m.find()) {
            return null;
        }
        return TencentQuotes.parsePrice(m.group(1));
    }

    private static String firstGroup(String raw, Pattern p) {
        Matcher m = p.matcher(raw);
        return m.find() ? m.group(1) : null;
    }
}
