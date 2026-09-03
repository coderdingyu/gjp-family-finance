package com.gjp.dedup;

/**
 * 订单号比较：两边都非空时 trim + 忽略大小写视为同一单。
 * 只有一边有订单号时不算强重复，走原来的金额/日期/商家模糊规则。
 */
public final class OrderNos {

    private OrderNos() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    /** 两边都有非空订单号且忽略大小写相同 */
    public static boolean same(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na == null || nb == null) {
            return false;
        }
        return na.equalsIgnoreCase(nb);
    }
}
