package com.gjp.asset.quote;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingEstimateTest {

    @Test
    void medianEvenAndOdd() {
        assertEquals(new BigDecimal("20.00"),
                ListingEstimate.median(List.of(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"))));
        assertEquals(new BigDecimal("15.00"),
                ListingEstimate.median(List.of(new BigDecimal("10"), new BigDecimal("20"))));
        assertNull(ListingEstimate.median(List.of()));
    }

    @Test
    void parseCarWanFromFixtureHtml() {
        String html = "<div>途观L 2019款 12.8万</div><div>13.2万</div><div>11.9万</div>";
        var prices = ListingEstimate.extractCarYuan(html, 20);
        assertEquals(3, prices.size());
        assertEquals(0, new BigDecimal("128000.00").compareTo(ListingEstimate.median(prices)));
    }

    @Test
    void parseHouseUnitAndTotal() {
        String html = "<span>45000元/平</span><span>46000元/平</span><span>总价 380万</span>";
        var units = ListingEstimate.extractUnitYuan(html, 20);
        assertEquals(2, units.size());
        var totals = ListingEstimate.extractHouseTotalYuan(html, 20);
        assertTrue(totals.size() >= 1);
        assertEquals(0, new BigDecimal("3800000.00").compareTo(ListingEstimate.median(totals)));
    }

    @Test
    void captchaIsBlocked() {
        assertTrue(ListingEstimate.looksBlocked("<title>CAPTCHA</title>"));
        assertTrue(ListingEstimate.looksBlocked("请输入验证码"));
        assertTrue(ListingEstimate.looksBlocked("<script>verify.init({ctx: \"/verify\"</script>"));
        assertTrue(ListingEstimate.looksBlocked("<script src=\"/verify/static/verify.min.js\"></script>"));
        assertTrue(ListingEstimate.looksBlocked("<link href=\"/verify/challenge.css\">短页"));
    }

    /** ~900-byte WAF verify shell (no listing content) must count as blocked. */
    @Test
    void verifyShellShortPageIsBlocked() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title></title>");
        sb.append("<script src=\"/verify/static/js/verify.min.js\"></script>");
        sb.append("<script>verify.init({ctx:\"/verify/\",appId:\"iautos\"});</script>");
        sb.append("</head><body><div id=\"verify\"></div>");
        while (sb.length() < 900) {
            sb.append("<!-- pad -->");
        }
        String html = sb.substring(0, 900);
        assertEquals(900, html.length());
        assertTrue(ListingEstimate.looksBlocked(html));
        assertTrue(html.toLowerCase().contains("verify.init")
                || html.toLowerCase().contains("/verify/"));
    }

    @Test
    void parseIautosCardsPrefersYear() {
        String html = ""
                + "<ul class=\"car-box-list\">"
                + "<li data-id=\"1\"><h6 class=\"name\">2021款 后驱</h6>"
                + "<strong class=\"num\">9.90</strong><i class=\"unit\">万</i></li>"
                + "<li data-id=\"2\"><h6 class=\"name\">2021款 长续航</h6>"
                + "<strong class=\"num\">11.58</strong><i class=\"unit\">万</i></li>"
                + "<li data-id=\"3\"><h6 class=\"name\">2021款 高性能</h6>"
                + "<strong class=\"num\">15.30</strong><i class=\"unit\">万</i></li>"
                + "<li data-id=\"4\"><h6 class=\"name\">2023款 后驱</h6>"
                + "<strong class=\"num\">17.80</strong><i class=\"unit\">万</i></li>"
                + "</ul>"
                + "<a href=\"#\">5-8万</a><a href=\"#\">8-12万</a>";
        var year = ListingEstimate.extractIautosYuan(html, 2021, 20);
        assertEquals(3, year.size());
        assertEquals(0, new BigDecimal("115800.00").compareTo(ListingEstimate.median(year)));
        var all = ListingEstimate.extractIautosYuan(html, 2018, 20);
        assertEquals(4, all.size());
    }

    @Test
    void parseIautosBrandPagePrice() {
        String html = "<p class=\"price\"><span>19.8</span>万元</p>"
                + "<p class=\"price\"><span>20.8</span>万元</p>"
                + "<p class=\"price\"><span>22.8</span>万元</p>";
        var prices = ListingEstimate.extractIautosYuan(html, null, 20);
        assertEquals(3, prices.size());
        assertEquals(0, new BigDecimal("208000.00").compareTo(ListingEstimate.median(prices)));
    }

    @Test
    void iautosParseBrandSeries() {
        var p = IautosListings.parse("特斯拉 / Model 3 / 2021款");
        assertEquals("特斯拉", p.brand);
        assertEquals("Model 3", p.series);
        assertEquals("tesilamodel3", IautosListings.guessSeriesSlug("tesila", "Model 3"));
        IautosListings.loadBrands("<a href=\"https://www.iautos.cn/2scbrand-tesila/\" target=\"_blank\">二手特斯拉</a>"
                + "<a href=\"https://www.iautos.cn/2scbrand-anshantesila/\">鞍山二手特斯拉</a>");
        assertEquals("tesila", IautosListings.brandSlug("特斯拉"));
        assertEquals("quanguo", IautosListings.regionOf(null));
        assertEquals("beijing", IautosListings.regionOf("北京市"));
    }

    @Test
    void iautosSeriesFromBrandPage() {
        String html = "<a href=\"https://so.iautos.cn/quanguo/dazhong-pasate/\">帕萨特</a>"
                + "<a href=\"https://so.iautos.cn/quanguo/dazhong-maiteng/\">迈腾</a>"
                + "<a href=\"https://so.iautos.cn/quanguo/biyadi-hanev/\">汉EV</a>"
                + "<a href=\"https://so.iautos.cn/quanguo/biyadi-handm/\">汉DM</a>"
                + "<a href=\"https://so.iautos.cn/quanguo/baoma-baoma3xi/\">宝马3系</a>";
        assertEquals("pasate", IautosListings.seriesSlugFromHtml(html, "dazhong", "帕萨特"));
        assertEquals("hanev", IautosListings.seriesSlugFromHtml(html, "biyadi", "汉"));
        assertEquals("baoma3xi", IautosListings.seriesSlugFromHtml(html, "baoma", "3系"));
        assertEquals("baoma3xi", IautosListings.seriesSlugFromHtml(html, "baoma", "宝马3系"));
    }
}
