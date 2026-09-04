package com.gjp.asset.quote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public used-car / second-hand house listings. Tries several hosts until one
 * returns numeric prices. Captcha / WAF / empty SPA → next source, then fail.
 */
@Component
public class ListingClient {

    private static final Logger log = LoggerFactory.getLogger(ListingClient.class);
    private static final int MAX_ADS = 40;
    private static final String FAIL = "二手平台暂无法访问";

    private static final Map<String, String> CITY_SLUG = new LinkedHashMap<>();
    private static final Map<String, String> CITY_PINYIN = new LinkedHashMap<>();

    static {
        putCity("北京", "bj", "beijing");
        putCity("上海", "sh", "shanghai");
        putCity("广州", "gz", "guangzhou");
        putCity("深圳", "sz", "shenzhen");
        putCity("杭州", "hz", "hangzhou");
        putCity("南京", "nj", "nanjing");
        putCity("成都", "cd", "chengdu");
        putCity("武汉", "wh", "wuhan");
        putCity("西安", "xa", "xian");
        putCity("重庆", "cq", "chongqing");
        putCity("天津", "tj", "tianjin");
        putCity("苏州", "su", "suzhou");
        putCity("长沙", "cs", "changsha");
        putCity("郑州", "zz", "zhengzhou");
        putCity("青岛", "qd", "qingdao");
        putCity("合肥", "hf", "hefei");
        putCity("宁波", "nb", "ningbo");
        putCity("东莞", "dg", "dongguan");
        putCity("佛山", "fs", "foshan");
        putCity("厦门", "xm", "xiamen");
        putCity("济南", "jn", "jinan");
        putCity("福州", "fz", "fuzhou");
        putCity("无锡", "wx", "wuxi");
        putCity("沈阳", "sy", "shenyang");
        putCity("昆明", "km", "kunming");
        putCity("大连", "dl", "dalian");
    }

    private static void putCity(String name, String slug, String pinyin) {
        CITY_SLUG.put(name, slug);
        CITY_PINYIN.put(name, pinyin);
    }

    public ListingEstimate.Listing estimateCar(String carModel, String city, Integer modelYear, Integer mileageKm) {
        if (!StringUtils.hasText(carModel)) {
            return ListingEstimate.Listing.fail("请填写车型");
        }
        ListingEstimate.Listing iautos = IautosListings.estimate(carModel, city, modelYear, MAX_ADS);
        if (iautos.estimate != null) {
            return iautos;
        }
        log.debug("car listing skip 第一车网, fallback age");
        return ListingEstimate.Listing.fail(FAIL);
    }

    public ListingEstimate.Listing estimateHouse(String city, String community, BigDecimal areaSqm) {
        if (!StringUtils.hasText(city) || !StringUtils.hasText(community)) {
            return ListingEstimate.Listing.fail("请填写城市和小区");
        }
        String comm = community.trim();
        String slug = slug(city);
        String py = pinyin(city);
        String rs = enc(comm);
        String[] urls = new String[] {
                "https://" + slug + ".ke.com/ershoufang/rs" + rs + "/",
                "https://" + slug + ".lianjia.com/ershoufang/rs" + rs + "/",
                "https://" + py + ".anjuke.com/sale/?kw=" + rs,
                "https://esf.fang.com/house/n31/?keyword=" + rs,
                "https://" + slug + ".58.com/ershoufang/?key=" + rs
        };
        String[] names = {"贝壳", "链家", "安居客", "房天下", "58同城"};
        for (int i = 0; i < urls.length; i++) {
            String html = QuoteHttp.getUtf8(urls[i]);
            if (html == null || ListingEstimate.looksBlocked(html)) {
                log.debug("house listing skip {}", names[i]);
                continue;
            }
            List<BigDecimal> units = ListingEstimate.extractUnitYuan(html, MAX_ADS);
            BigDecimal unitMed = ListingEstimate.median(units);
            if (unitMed != null && areaSqm != null && areaSqm.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal est = unitMed.multiply(areaSqm).setScale(2, RoundingMode.HALF_UP);
                String note = "挂牌估值，仅供参考（" + names[i] + " 单价中位 "
                        + unitMed.toPlainString() + " 元/㎡ × " + areaSqm.stripTrailingZeros().toPlainString()
                        + "㎡，" + units.size() + " 条）";
                return new ListingEstimate.Listing(est, units.size(), names[i], note, null, unitMed);
            }
            List<BigDecimal> totals = ListingEstimate.extractHouseTotalYuan(html, MAX_ADS);
            BigDecimal med = ListingEstimate.median(totals);
            if (med != null) {
                String note = "挂牌估值，仅供参考（" + names[i] + " 总价中位 " + totals.size() + " 条）";
                return new ListingEstimate.Listing(med, totals.size(), names[i], note, null, unitMed);
            }
        }
        return ListingEstimate.Listing.fail(FAIL);
    }

    private static String slug(String city) {
        if (!StringUtils.hasText(city)) {
            return "bj";
        }
        String t = city.trim();
        String s = CITY_SLUG.get(t);
        if (s != null) {
            return s;
        }
        if (t.length() <= 3 && t.chars().allMatch(c -> c < 128)) {
            return t.toLowerCase();
        }
        return "bj";
    }

    private static String pinyin(String city) {
        if (!StringUtils.hasText(city)) {
            return "beijing";
        }
        String t = city.trim();
        String s = CITY_PINYIN.get(t);
        return s != null ? s : t.toLowerCase();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
