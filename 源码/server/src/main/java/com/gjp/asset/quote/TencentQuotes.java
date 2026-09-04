package com.gjp.asset.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tencent qt.gtimg.cn quotes. Stock line is GBK, fields split by ~.
 * v_sh600519="1~贵州茅台~600519~<price>~...~<datetime>"
 * Fund: v_s_jj110022="110022~name~date~<nav>~..."
 */
public final class TencentQuotes {

    private static final Pattern INNER = Pattern.compile("=\"([^\"]*)\"");

    private TencentQuotes() {
    }

    /**
     * Infer market prefix: 6xxxxx → sh, 0/3xxxxx → sz.
     * Users may type sh600519 / sz000001 / 600519.
     */
    public static String toStockCode(String symbol) {
        if (symbol == null) {
            return null;
        }
        String s = symbol.trim().toLowerCase().replaceAll("\\s+", "");
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("sh") || s.startsWith("sz") || s.startsWith("bj")) {
            return s;
        }
        String digits = s.replaceAll("\\D", "");
        if (digits.length() < 6) {
            return s;
        }
        digits = digits.substring(digits.length() - 6);
        char c = digits.charAt(0);
        if (c == '6' || c == '9') {
            return "sh" + digits;
        }
        if (c == '0' || c == '3') {
            return "sz" + digits;
        }
        if (c == '4' || c == '8') {
            return "bj" + digits;
        }
        return "sh" + digits;
    }

    public static String toFundCode(String symbol) {
        if (symbol == null) {
            return null;
        }
        String digits = symbol.trim().replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        if (digits.length() > 6) {
            digits = digits.substring(digits.length() - 6);
        }
        while (digits.length() < 6) {
            digits = "0" + digits;
        }
        return digits;
    }

    public static QuoteSnapshot parseStock(String raw) {
        String[] p = fields(raw);
        if (p == null || p.length < 4) {
            return null;
        }
        BigDecimal price = parsePrice(p[3]);
        if (price == null) {
            return null;
        }
        String name = p.length > 1 ? p[1] : null;
        String time = p.length > 30 ? p[30] : null;
        return new QuoteSnapshot(emptyToNull(name), price, emptyToNull(time), "tencent");
    }

    public static QuoteSnapshot parseFund(String raw) {
        String[] p = fields(raw);
        if (p == null || p.length < 4) {
            return null;
        }
        BigDecimal nav = parsePrice(p[3]);
        if (nav == null) {
            return null;
        }
        String name = p.length > 1 ? p[1] : null;
        String time = p.length > 2 ? p[2] : null;
        return new QuoteSnapshot(emptyToNull(name), nav, emptyToNull(time), "tencent");
    }

    static String[] fields(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = INNER.matcher(raw);
        String inner;
        if (m.find()) {
            inner = m.group(1);
        } else {
            int a = raw.indexOf('"');
            int b = raw.lastIndexOf('"');
            if (a < 0 || b <= a) {
                return null;
            }
            inner = raw.substring(a + 1, b);
        }
        return inner.split("~", -1);
    }

    static BigDecimal parsePrice(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty() || "-".equals(t) || "0".equals(t) || "0.00".equals(t) || "0.000".equals(t)) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(t);
            if (v.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return v.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String emptyToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
