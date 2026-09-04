package com.gjp.asset.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse public listing HTML/JSON for prices. No network.
 * Car prices often appear as 12.8万; house as 380万 or 单价 45000元/平.
 */
public final class ListingEstimate {

    private static final Pattern WAN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*万");
    private static final Pattern IAUTOS_CARD_PRICE = Pattern.compile(
            "<strong class=\"num\">(\\d+(?:\\.\\d+)?)</strong>\\s*<i class=\"unit\">万</i>");
    private static final Pattern IAUTOS_P_PRICE = Pattern.compile(
            "<p class=\"price\"><span>(\\d+(?:\\.\\d+)?)</span>万元");
    private static final Pattern IAUTOS_TEXT_PRICE = Pattern.compile("价格为(\\d+(?:\\.\\d+)?)万元");
    private static final Pattern YUAN_SQM = Pattern.compile("(\\d{4,6})\\s*元\\s*[/／]\\s*平");
    private static final Pattern JSON_PRICE = Pattern.compile(
            "\"(?:price|priceWan|price_wan|list_price|showPrice|unitPrice|unit_price)\"\\s*:\\s*\"?(\\d+(?:\\.\\d+)?)\"");

    private ListingEstimate() {
    }

    public static BigDecimal median(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<BigDecimal> s = new ArrayList<>(values);
        Collections.sort(s);
        int n = s.size();
        if (n % 2 == 1) {
            return s.get(n / 2).setScale(2, RoundingMode.HALF_UP);
        }
        return s.get(n / 2 - 1).add(s.get(n / 2))
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    public static boolean looksBlocked(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        String h = html.length() > 8000 ? html.substring(0, 8000) : html;
        String low = h.toLowerCase();
        if (low.contains("captcha") || h.contains("验证码") || h.contains("antibot")
                || h.contains("访问过于频繁") || h.contains("页面未找到")
                || low.contains("verifycode") || low.contains("defence1")
                || low.contains("verify.init")
                || low.contains("/verify/static") || low.contains("/verify/")
                || low.contains("verify.min")
                || (html.length() < 2500 && low.contains("verify"))
                || low.contains("challenge") && html.length() < 40000) {
            return true;
        }
        // JS WAF / empty SPA shell with no listing numbers
        if (html.length() < 40000 && !html.contains("万") && !low.contains("\"price\"")) {
            if (low.contains("<script") && !low.contains("ershou") && !low.contains("usedcar")) {
                return true;
            }
        }
        return false;
    }

    /** Extract up to max listing totals in 元 from 万 / JSON. */
    public static List<BigDecimal> extractCarYuan(String html, int max) {
        List<BigDecimal> out = new ArrayList<>();
        if (html == null) {
            return out;
        }
        collectWan(html, out, new BigDecimal("5000"), new BigDecimal("5000000"), max);
        if (out.size() < 3) {
            collectJson(html, out, true, max);
        }
        return clip(out, max);
    }


    /**
     * 第一车网 listing cards. Prefers year-matched cards when modelYear is set
     * and at least 3 remain; otherwise all card prices. Skips filter chips like 5-8万.
     */
    public static List<BigDecimal> extractIautosYuan(String html, Integer modelYear, int max) {
        List<BigDecimal> yearHit = new ArrayList<>();
        List<BigDecimal> all = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return all;
        }
        String[] cards = html.split("<li data-id=\"");
        if (cards.length > 1) {
            for (int i = 1; i < cards.length; i++) {
                String card = cards[i];
                BigDecimal yuan = wanToYuan(cardPriceWan(card));
                if (yuan == null) {
                    continue;
                }
                all.add(yuan);
                if (modelYear != null && (card.contains(modelYear + "款") || card.contains(modelYear + "年"))) {
                    yearHit.add(yuan);
                }
            }
        }
        if (yearHit.size() >= 3) {
            return clip(yearHit, max);
        }
        if (all.size() >= 3) {
            return clip(all, max);
        }
        Matcher pPrice = IAUTOS_P_PRICE.matcher(html);
        while (pPrice.find()) {
            BigDecimal yuan = wanToYuan(TencentQuotes.parsePrice(pPrice.group(1)));
            if (yuan != null) {
                all.add(yuan);
            }
        }
        if (all.size() < 3) {
            Matcher t = IAUTOS_TEXT_PRICE.matcher(html);
            while (t.find()) {
                BigDecimal yuan = wanToYuan(TencentQuotes.parsePrice(t.group(1)));
                if (yuan != null) {
                    all.add(yuan);
                }
            }
        }
        return clip(all, max);
    }

    private static BigDecimal cardPriceWan(String card) {
        Matcher m = IAUTOS_CARD_PRICE.matcher(card);
        if (m.find()) {
            return TencentQuotes.parsePrice(m.group(1));
        }
        return null;
    }

    private static BigDecimal wanToYuan(BigDecimal wan) {
        if (wan == null) {
            return null;
        }
        BigDecimal yuan = wan.multiply(new BigDecimal("10000")).setScale(2, RoundingMode.HALF_UP);
        if (yuan.compareTo(new BigDecimal("5000")) >= 0 && yuan.compareTo(new BigDecimal("5000000")) <= 0) {
            return yuan;
        }
        return null;
    }

    /** House listing totals in 元 (万). */
    public static List<BigDecimal> extractHouseTotalYuan(String html, int max) {
        List<BigDecimal> out = new ArrayList<>();
        if (html == null) {
            return out;
        }
        collectWan(html, out, new BigDecimal("150000"), new BigDecimal("100000000"), max);
        if (out.size() < 3) {
            collectJson(html, out, false, max);
        }
        return clip(out, max);
    }

    /** 单价 元/㎡ */
    public static List<BigDecimal> extractUnitYuan(String html, int max) {
        List<BigDecimal> out = new ArrayList<>();
        if (html == null) {
            return out;
        }
        Matcher m = YUAN_SQM.matcher(html);
        while (m.find() && out.size() < max) {
            BigDecimal v = TencentQuotes.parsePrice(m.group(1));
            if (v != null && v.compareTo(new BigDecimal("2000")) >= 0
                    && v.compareTo(new BigDecimal("500000")) <= 0) {
                out.add(v.setScale(2, RoundingMode.HALF_UP));
            }
        }
        Matcher j = Pattern.compile("\"(?:unitPrice|unit_price|pricePerSqm)\"\\s*:\\s*\"?(\\d+(?:\\.\\d+)?)\"").matcher(html);
        while (j.find() && out.size() < max) {
            BigDecimal v = TencentQuotes.parsePrice(j.group(1));
            if (v != null && v.compareTo(new BigDecimal("2000")) >= 0
                    && v.compareTo(new BigDecimal("500000")) <= 0) {
                out.add(v.setScale(2, RoundingMode.HALF_UP));
            }
        }
        return clip(out, max);
    }

    private static void collectWan(String html, List<BigDecimal> out, BigDecimal minYuan, BigDecimal maxYuan, int max) {
        Matcher m = WAN.matcher(html);
        while (m.find() && out.size() < max) {
            BigDecimal wan = TencentQuotes.parsePrice(m.group(1));
            if (wan == null) {
                continue;
            }
            BigDecimal yuan = wan.multiply(new BigDecimal("10000")).setScale(2, RoundingMode.HALF_UP);
            if (yuan.compareTo(minYuan) >= 0 && yuan.compareTo(maxYuan) <= 0) {
                out.add(yuan);
            }
        }
    }

    private static void collectJson(String html, List<BigDecimal> out, boolean car, int max) {
        Matcher m = JSON_PRICE.matcher(html);
        while (m.find() && out.size() < max) {
            BigDecimal v = TencentQuotes.parsePrice(m.group(1));
            if (v == null) {
                continue;
            }
            BigDecimal yuan;
            if (v.compareTo(new BigDecimal("1000")) < 0) {
                yuan = v.multiply(new BigDecimal("10000"));
            } else {
                yuan = v;
            }
            yuan = yuan.setScale(2, RoundingMode.HALF_UP);
            if (car) {
                if (yuan.compareTo(new BigDecimal("5000")) >= 0 && yuan.compareTo(new BigDecimal("5000000")) <= 0) {
                    out.add(yuan);
                }
            } else if (yuan.compareTo(new BigDecimal("150000")) >= 0 && yuan.compareTo(new BigDecimal("100000000")) <= 0) {
                out.add(yuan);
            }
        }
    }

    private static List<BigDecimal> clip(List<BigDecimal> out, int max) {
        if (out.size() <= max) {
            return out;
        }
        return new ArrayList<>(out.subList(0, max));
    }

    public static final class Listing {
        public final BigDecimal estimate;
        public final int sampleCount;
        public final String source;
        public final String note;
        public final String reason;
        public final BigDecimal unitPrice;

        public Listing(BigDecimal estimate, int sampleCount, String source, String note, String reason,
                       BigDecimal unitPrice) {
            this.estimate = estimate;
            this.sampleCount = sampleCount;
            this.source = source;
            this.note = note;
            this.reason = reason;
            this.unitPrice = unitPrice;
        }

        public static Listing fail(String reason) {
            return new Listing(null, 0, null, null, reason, null);
        }
    }
}
