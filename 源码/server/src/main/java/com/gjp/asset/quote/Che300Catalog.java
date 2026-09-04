package com.gjp.asset.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 车300 unauthenticated catalog JSON: brand → series → model 新车指导价 (万元).
 * Used when 二手挂牌 is blocked and the user did not fill 取得成本.
 */
final class Che300Catalog {

    private static final Logger log = LoggerFactory.getLogger(Che300Catalog.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern BRAND_P = Pattern.compile(
            "pinpailist[^>]*\\sid=['\"](\\d+)['\"][^>]*>([^<]+)</p>", Pattern.CASE_INSENSITIVE);
    private static final ConcurrentHashMap<String, Integer> BRAND_ID = new ConcurrentHashMap<>();
    private static volatile boolean homeTried;

    static {
        BRAND_ID.put("特斯拉", 120);
    }

    private Che300Catalog() {
    }

    static BigDecimal msrpYuan(String carModel, Integer modelYear) {
        IautosListings.ParsedCar parsed = IautosListings.parse(carModel);
        if (parsed.brand.isEmpty() || parsed.series.isEmpty()) {
            return null;
        }
        Integer brandId = brandId(parsed.brand);
        if (brandId == null) {
            log.debug("che300 no brand id for {}", parsed.brand);
            return null;
        }
        String seriesJson = QuoteHttp.getUtf8("https://www.che300.com/api/series/list_all?brand_id=" + brandId);
        String seriesId = pickSeriesId(seriesJson, parsed.series);
        if (seriesId == null) {
            log.debug("che300 no series for {} {}", parsed.brand, parsed.series);
            return null;
        }
        String modelsJson = QuoteHttp.getUtf8("https://www.che300.com/api/model/list_all?series_id=" + seriesId);
        return medianGuideYuan(modelsJson, modelYear);
    }

    static Integer brandId(String brand) {
        if (!StringUtils.hasText(brand)) {
            return null;
        }
        Integer id = BRAND_ID.get(brand.trim());
        if (id != null) {
            return id;
        }
        loadHomeOnce();
        return BRAND_ID.get(brand.trim());
    }

    static void loadHomeOnce() {
        if (homeTried) {
            return;
        }
        synchronized (Che300Catalog.class) {
            if (homeTried) {
                return;
            }
            String html = QuoteHttp.getUtf8("https://www.che300.com/");
            loadBrandsFromHome(html);
            homeTried = true;
        }
    }

    static void loadBrandsFromHome(String html) {
        if (html == null) {
            return;
        }
        Matcher m = BRAND_P.matcher(html);
        while (m.find()) {
            String name = m.group(2).trim();
            if (name.isEmpty() || name.length() > 20) {
                continue;
            }
            try {
                BRAND_ID.putIfAbsent(name, Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
    }

    static String pickSeriesId(String json, String seriesName) {
        JsonNode list = dataList(json);
        if (list == null || !StringUtils.hasText(seriesName)) {
            return null;
        }
        String want = compact(seriesName);
        String best = null;
        int bestScore = -1;
        for (JsonNode n : list) {
            String name = n.path("series_name").asText("");
            String id = n.path("series_id").asText("");
            if (id.isEmpty()) {
                continue;
            }
            String c = compact(name);
            int score = -1;
            if (c.equals(want)) {
                score = 100;
            } else if (c.contains(want) || want.contains(c)) {
                score = 50;
            }
            if (score < 0) {
                continue;
            }
            String group = n.path("series_group_name").asText("");
            if (group.contains("进口")) {
                score -= 10;
            }
            if (group.contains("中国")) {
                score += 5;
            }
            if (score > bestScore) {
                bestScore = score;
                best = id;
            }
        }
        return best;
    }

    static BigDecimal medianGuideYuan(String json, Integer modelYear) {
        JsonNode list = dataList(json);
        if (list == null) {
            return null;
        }
        List<Row> rows = new ArrayList<>();
        for (JsonNode n : list) {
            BigDecimal wan = TencentQuotes.parsePrice(n.path("model_price").asText(null));
            int year = n.path("model_year").asInt(0);
            if (wan == null || wan.compareTo(BigDecimal.ZERO) <= 0 || year < 1980) {
                continue;
            }
            rows.add(new Row(year, wan));
        }
        if (rows.isEmpty()) {
            return null;
        }
        List<BigDecimal> prices = new ArrayList<>();
        if (modelYear != null) {
            for (Row r : rows) {
                if (r.year == modelYear) {
                    prices.add(r.wan);
                }
            }
            if (prices.isEmpty()) {
                int best = Integer.MIN_VALUE;
                for (Row r : rows) {
                    if (r.year <= modelYear && r.year > best) {
                        best = r.year;
                    }
                }
                if (best >= 1980) {
                    for (Row r : rows) {
                        if (r.year == best) {
                            prices.add(r.wan);
                        }
                    }
                }
            }
        }
        if (prices.isEmpty()) {
            int latest = 0;
            for (Row r : rows) {
                if (r.year > latest) {
                    latest = r.year;
                }
            }
            for (Row r : rows) {
                if (r.year == latest) {
                    prices.add(r.wan);
                }
            }
        }
        List<BigDecimal> yuan = new ArrayList<>();
        for (BigDecimal wan : prices) {
            yuan.add(wan.multiply(new BigDecimal("10000")).setScale(2, RoundingMode.HALF_UP));
        }
        return ListingEstimate.median(yuan);
    }

    private static JsonNode dataList(String json) {
        if (json == null || json.isBlank() || json.charAt(0) != '{') {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root.path("code").asInt() != 2000) {
                return null;
            }
            JsonNode list = root.path("data").path("list");
            return list.isArray() ? list : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String compact(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replace("进口", "").replaceAll("[\\s\\-_/()]+", "");
    }

    private static final class Row {
        final int year;
        final BigDecimal wan;

        Row(int year, BigDecimal wan) {
            this.year = year;
            this.wan = wan;
        }
    }
}
