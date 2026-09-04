package com.gjp.asset.quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 第一车网 (so.iautos.cn) used-car hangpai. Reachable from this host; no login.
 * Keyword search hits captcha — only brand/series path URLs.
 */
final class IautosListings {

    static final String SOURCE = "第一车网";

    private static final Logger log = LoggerFactory.getLogger(IautosListings.class);
    private static final String INDEX = "https://www.iautos.cn/2scbrand/";
    private static final Pattern BRAND_A = Pattern.compile(
            "2scbrand-([a-z0-9]+)/\"[^>]*>([^<]{1,40})</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SERIES_A = Pattern.compile(
            "so\\.iautos\\.cn/[^/]+/([a-z0-9]+)-([a-z0-9]+)/\"[^>]*>([^<]{1,40})<",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> CITY_PY = new LinkedHashMap<>();
    private static final ConcurrentHashMap<String, String> BRAND_SLUG = new ConcurrentHashMap<>();
    private static volatile boolean brandLoaded;

    static {
        seed("特斯拉", "tesila");
        seed("比亚迪", "biyadi");
        seed("大众", "dazhong");
        seed("丰田", "fengtian");
        seed("本田", "bentian");
        seed("奔驰", "benchi");
        seed("宝马", "baoma");
        seed("奥迪", "aodi");
        seed("理想", "lixiang");
        seed("蔚来", "weilai");
        seed("小鹏", "xiaopeng");
        seed("吉利", "jili");
        seed("长安", "changan");
        seed("长城", "changcheng");
        seed("五菱", "wuling");
        seed("别克", "bieke");
        seed("日产", "richan");
        seed("福特", "fute");
        seed("路虎", "luhu");
        seed("雷克萨斯", "leikesasi");
        seed("沃尔沃", "woerwo");
        seed("奇瑞", "qirui");
        seed("保时捷", "baoshijie");
        seed("凯迪拉克", "kaidilake");
        seed("荣威", "rongwei");
        seed("宝骏", "baojun");
        seed("现代", "xiandai");
        seed("雪佛兰", "xuefolan");
        seed("马自达", "mazida");
        seed("起亚", "qiya");
        seed("标致", "biaozhi");
        seed("斯柯达", "sikeda");
        CITY_PY.put("北京", "beijing");
        CITY_PY.put("上海", "shanghai");
        CITY_PY.put("广州", "guangzhou");
        CITY_PY.put("深圳", "shenzhen");
        CITY_PY.put("杭州", "hangzhou");
        CITY_PY.put("南京", "nanjing");
        CITY_PY.put("成都", "chengdu");
        CITY_PY.put("武汉", "wuhan");
        CITY_PY.put("西安", "xian");
        CITY_PY.put("重庆", "chongqing");
        CITY_PY.put("天津", "tianjin");
        CITY_PY.put("苏州", "suzhou");
        CITY_PY.put("长沙", "changsha");
        CITY_PY.put("郑州", "zhengzhou");
        CITY_PY.put("青岛", "qingdao");
        CITY_PY.put("合肥", "hefei");
        CITY_PY.put("宁波", "ningbo");
        CITY_PY.put("东莞", "dongguan");
        CITY_PY.put("佛山", "foshan");
        CITY_PY.put("厦门", "xiamen");
        CITY_PY.put("济南", "jinan");
        CITY_PY.put("福州", "fuzhou");
        CITY_PY.put("无锡", "wuxi");
        CITY_PY.put("沈阳", "shenyang");
        CITY_PY.put("昆明", "kunming");
        CITY_PY.put("大连", "dalian");
    }

    private IautosListings() {
    }

    static ListingEstimate.Listing estimate(String carModel, String city, Integer modelYear, int maxAds) {
        ParsedCar parsed = parse(carModel);
        if (parsed.brand.isEmpty()) {
            return ListingEstimate.Listing.fail("请填写车型");
        }
        ensureBrands();
        String brandSlug = brandSlug(parsed.brand);
        if (brandSlug == null) {
            log.debug("iautos no brand slug for {}", parsed.brand);
            return ListingEstimate.Listing.fail(
                    "第一车网暂无「" + parsed.brand + "」挂牌，可填取得成本用车龄估算");
        }
        String brandUrl = "https://so.iautos.cn/quanguo/" + brandSlug + "/";
        String brandHtml = QuoteHttp.getUtf8(brandUrl);
        if (brandHtml == null) {
            return ListingEstimate.Listing.fail("第一车网请求超时，请稍后重试");
        }
        if (ListingEstimate.looksBlocked(brandHtml)) {
            return ListingEstimate.Listing.fail("第一车网风控拦截（验证码），请过几分钟再试");
        }
        String seriesSlug = seriesSlugFromHtml(brandHtml, brandSlug, parsed.series);
        if (seriesSlug == null) {
            seriesSlug = guessSeriesSlug(brandSlug, parsed.series);
        }
        List<String> urls = new ArrayList<>();
        if (seriesSlug != null && StringUtils.hasText(parsed.series)) {
            urls.add("https://so.iautos.cn/quanguo/" + brandSlug + "-" + seriesSlug + "/");
        } else {
            urls.add(brandUrl);
        }
        boolean sawTimeout = false;
        boolean sawBlocked = false;
        for (String url : urls) {
            String html = url.equals(brandUrl) ? brandHtml : QuoteHttp.getUtf8(url);
            if (html == null) {
                sawTimeout = true;
                continue;
            }
            if (ListingEstimate.looksBlocked(html)) {
                sawBlocked = true;
                continue;
            }
            List<BigDecimal> prices = ListingEstimate.extractIautosYuan(html, modelYear, maxAds);
            BigDecimal med = ListingEstimate.median(prices);
            if (med != null && prices.size() >= 1) {
                String extra = "";
                if (modelYear != null) {
                    extra += " " + modelYear + "款";
                }
                String note = "二手挂牌中位数，仅供参考（" + SOURCE + " " + prices.size() + " 条"
                        + extra + "）";
                if (prices.size() < 3) {
                    note += "，样本偏少";
                }
                return new ListingEstimate.Listing(med, prices.size(), SOURCE, note, null, null);
            }
        }
        if (sawBlocked) {
            return ListingEstimate.Listing.fail("第一车网风控拦截（验证码），请过几分钟再试");
        }
        if (sawTimeout) {
            return ListingEstimate.Listing.fail("第一车网请求超时，请稍后重试");
        }
        return ListingEstimate.Listing.fail("第一车网未解析到报价，可填取得成本用车龄估算");
    }

    static ParsedCar parse(String carModel) {
        ParsedCar p = new ParsedCar();
        if (!StringUtils.hasText(carModel)) {
            return p;
        }
        String raw = carModel.trim();
        String[] parts = raw.split("\\s*/\\s*");
        if (parts.length >= 2) {
            p.brand = parts[0].trim();
            p.series = parts[1].trim();
            return p;
        }
        String[] sp = raw.split("\\s+", 2);
        p.brand = sp[0].trim();
        p.series = sp.length > 1 ? sp[1].trim() : "";
        return p;
    }

    static String compact(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replace("进口", "").replaceAll("[\\s\\-_/]+", "");
    }

    static String guessSeriesSlug(String brandSlug, String series) {
        String c = compact(series);
        if (c.isEmpty()) {
            return null;
        }
        return brandSlug + c;
    }

    static String seriesSlugFromHtml(String html, String brandSlug, String series) {
        if (html == null || series == null || series.isBlank()) {
            return null;
        }
        String want = compact(series);
        String best = null;
        int bestScore = -1;
        Matcher m = SERIES_A.matcher(html);
        while (m.find()) {
            if (!brandSlug.equalsIgnoreCase(m.group(1))) {
                continue;
            }
            String slug = m.group(2).toLowerCase(Locale.ROOT);
            String text = compact(m.group(3));
            int score = -1;
            if (!want.isEmpty() && text.equals(want)) {
                score = 100;
            } else if (!want.isEmpty() && (text.startsWith(want) || text.contains(want))) {
                score = 80;
            } else if (!want.isEmpty() && (slug.contains(want) || want.contains(slug))) {
                score = 40;
            }
            if (score < 0) {
                continue;
            }
            if (text.contains("进口")) {
                score -= 15;
            }
            if (best == null || score > bestScore || (score == bestScore && slug.length() < best.length())) {
                bestScore = score;
                best = slug;
            }
        }
        return best;
    }

    static String regionOf(String city) {
        if (!StringUtils.hasText(city)) {
            return "quanguo";
        }
        String t = city.trim().replace("市", "");
        String py = CITY_PY.get(t);
        return py != null ? py : "quanguo";
    }

    static String brandSlug(String brand) {
        if (!StringUtils.hasText(brand)) {
            return null;
        }
        String hit = BRAND_SLUG.get(brand.trim());
        if (hit != null) {
            return hit;
        }
        String c = compact(brand);
        for (Map.Entry<String, String> e : BRAND_SLUG.entrySet()) {
            if (compact(e.getKey()).equals(c)) {
                return e.getValue();
            }
        }
        return null;
    }

    static void ensureBrands() {
        if (brandLoaded && !BRAND_SLUG.isEmpty()) {
            return;
        }
        synchronized (IautosListings.class) {
            if (brandLoaded && !BRAND_SLUG.isEmpty()) {
                return;
            }
            String html = QuoteHttp.getUtf8(INDEX);
            // Do not lock on blocked/empty index — retry next call when WAF cools down.
            if (html == null || html.isBlank() || ListingEstimate.looksBlocked(html)) {
                return;
            }
            int found = loadBrands(html);
            if (found > 0) {
                brandLoaded = true;
            }
        }
    }

    /** Visible for tests. First exact 「二手品牌」 wins (national list is first on the page). */
    private static void seed(String brand, String slug) {
        BRAND_SLUG.putIfAbsent(brand, slug);
    }

    /** @return number of brand links parsed from HTML (0 if empty/blocked shell) */
    static int loadBrands(String html) {
        if (html == null) {
            return 0;
        }
        int found = 0;
        Matcher m = BRAND_A.matcher(html);
        while (m.find()) {
            String slug = m.group(1).toLowerCase(Locale.ROOT);
            String text = m.group(2).replace("二手", "").trim();
            if (text.isEmpty() || slug.length() < 2) {
                continue;
            }
            BRAND_SLUG.putIfAbsent(text, slug);
            found++;
        }
        return found;
    }

    static final class ParsedCar {
        String brand = "";
        String series = "";
    }
}
