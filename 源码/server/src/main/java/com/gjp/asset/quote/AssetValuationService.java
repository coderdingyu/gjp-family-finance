package com.gjp.asset.quote;

import com.gjp.asset.AssetVO;
import com.gjp.entity.Asset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enrich assets with live quotes / interest / listing median.
 * Failures never fail list/summary: keep stored amount and a short reason.
 */
@Service
public class AssetValuationService {

    private static final Logger log = LoggerFactory.getLogger(AssetValuationService.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private QuoteCache cache;
    @Autowired
    private MarketClient marketClient;
    @Autowired
    private ListingClient listingClient;

    private final ExecutorService pool = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "asset-quote");
        t.setDaemon(true);
        return t;
    });

    public List<AssetVO> enrichAll(List<Asset> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<CompletableFuture<AssetVO>> futs = new ArrayList<>(rows.size());
        for (Asset a : rows) {
            futs.add(CompletableFuture.supplyAsync(() -> enrich(a), pool));
        }
        List<AssetVO> out = new ArrayList<>(rows.size());
        for (int i = 0; i < futs.size(); i++) {
            try {
                out.add(futs.get(i).join());
            } catch (Exception e) {
                log.debug("enrich join: {}", e.toString());
                AssetVO vo = AssetVO.from(rows.get(i));
                fillStoredPnl(vo);
                vo.setEstimateReason("估值失败");
                out.add(vo);
            }
        }
        return out;
    }

    public AssetVO enrich(Asset asset) {
        AssetVO vo = AssetVO.from(asset);
        fillStoredPnl(vo);
        try {
            String type = asset.getAssetType();
            if ("股票".equals(type)) {
                applyQuote(vo, asset, false);
            } else if ("基金".equals(type)) {
                applyQuote(vo, asset, true);
            } else if ("存款".equals(type)) {
                applyInterest(vo, asset);
            } else if ("车辆".equals(type)) {
                applyListing(vo, asset, true);
            } else if ("房产".equals(type)) {
                applyListing(vo, asset, false);
            }
        } catch (Exception e) {
            log.debug("enrich {}: {}", asset.getId(), e.toString());
            vo.setEstimateReason("估值失败");
        }
        return vo;
    }

    public Map<String, Object> quotePreview(String type, String symbol, BigDecimal shares) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean fund = "基金".equals(type);
        QuoteSnapshot q = fund ? cachedFund(symbol) : cachedStock(symbol);
        m.put("type", type);
        m.put("symbol", symbol);
        if (q == null) {
            m.put("ok", false);
            m.put("reason", fund ? "基金行情暂不可用" : "股票行情暂不可用");
            return m;
        }
        m.put("ok", true);
        m.put("name", q.getName());
        m.put("lastPrice", q.getLastPrice());
        m.put("quoteTime", q.getQuoteTime());
        m.put("source", q.getSource());
        m.put("valueSource", "quote");
        if (shares != null && shares.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal mv = q.getLastPrice().multiply(shares).setScale(2, RoundingMode.HALF_UP);
            m.put("marketValue", mv);
            m.put("amount", mv);
            m.put("note", noteQuote(q, shares));
        } else {
            m.put("note", (q.getName() == null ? "" : q.getName() + " ") + "最新价 " + q.getLastPrice());
        }
        return m;
    }

    public Map<String, Object> estimatePreview(String type, String carModel, String city, String community,
                                               BigDecimal areaSqm, Integer modelYear, Integer mileageKm,
                                               BigDecimal cost, LocalDate buyDate) {
        Map<String, Object> m = new LinkedHashMap<>();
        ListingEstimate.Listing listing;
        if ("车辆".equals(type)) {
            listing = resolveCar(carModel, city, modelYear, mileageKm, cost, buyDate);
        } else {
            listing = cachedHouse(city, community, areaSqm);
        }
        m.put("type", type);
        m.put("valueSource", listing.estimate != null
                ? (isAgeSource(listing.source) ? "age" : "listing") : "stored");
        m.put("ok", listing.estimate != null);
        m.put("estimate", listing.estimate);
        m.put("amount", listing.estimate);
        m.put("sampleCount", listing.sampleCount);
        m.put("source", listing.source);
        m.put("note", listing.note);
        m.put("reason", listing.reason);
        m.put("unitPrice", listing.unitPrice);
        m.put("estimateNote", listing.estimate != null ? listing.note : listing.reason);
        return m;
    }


    /**
     * 管家婆自研二手车残值查询：挂牌中位（若可达）否则 新车指导价/取得成本 × 车龄里程折价。
     */
    public Map<String, Object> usedCarPrice(String carModel, String city, Integer modelYear,
                                            Integer mileageKm, BigDecimal cost, LocalDate buyDate) {
        Map<String, Object> m = new LinkedHashMap<>();
        IautosListings.ParsedCar parsed = IautosListings.parse(carModel);
        LocalDate today = LocalDate.now(SHANGHAI);
        double years = ResidualAge.yearsHeld(modelYear, buyDate, today);
        double retain = ResidualAge.retainWithMileage(years, mileageKm);
        BigDecimal guide = null;
        if (StringUtils.hasText(carModel)) {
            guide = cache.get("msrp:" + n(carModel) + "|" + modelYear,
                    () -> Che300Catalog.msrpYuan(carModel, modelYear));
        }
        ListingEstimate.Listing hangpai = StringUtils.hasText(carModel)
                ? cachedCar(carModel, city, modelYear, mileageKm)
                : ListingEstimate.Listing.fail("请填写车型");
        BigDecimal hangpaiPrice = hangpai.estimate;
        BigDecimal residual;
        String method;
        String source;
        String note;
        if (hangpaiPrice != null) {
            residual = hangpaiPrice;
            method = "hangpai";
            source = hangpai.source;
            note = hangpai.note;
        } else if (cost != null && cost.compareTo(BigDecimal.ZERO) > 0) {
            ListingEstimate.Listing age = ResidualAge.estimateCar(cost, modelYear, buyDate, mileageKm, today);
            residual = age.estimate;
            method = "cost+age";
            source = age.source;
            note = age.note;
        } else if (guide != null) {
            ListingEstimate.Listing age = ResidualAge.fromGuidePrice(guide, modelYear, buyDate, mileageKm, today);
            residual = age.estimate;
            method = "guide+age";
            source = age.source;
            note = age.note;
        } else {
            m.put("ok", false);
            m.put("brand", parsed.brand);
            m.put("series", parsed.series);
            m.put("year", modelYear);
            m.put("reason", hangpai.reason != null ? hangpai.reason
                    : "无法估价：挂牌不可用，且没有指导价和取得成本");
            return m;
        }
        m.put("ok", true);
        m.put("brand", parsed.brand);
        m.put("series", parsed.series);
        m.put("year", modelYear);
        m.put("mileageKm", mileageKm);
        m.put("yearsHeld", BigDecimal.valueOf(years).setScale(1, RoundingMode.HALF_UP));
        m.put("retainRatio", BigDecimal.valueOf(retain).setScale(4, RoundingMode.HALF_UP));
        m.put("guidePrice", guide);
        m.put("hangpaiPrice", hangpaiPrice);
        m.put("residual", residual);
        m.put("estimate", residual);
        m.put("amount", residual);
        m.put("method", method);
        m.put("source", source);
        m.put("note", note);
        m.put("sampleCount", hangpai.sampleCount);
        return m;
    }

    public Map<String, Object> interestPreview(BigDecimal principal, BigDecimal annualRate,
                                               Integer termMonths, String method, LocalDate buyDate) {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(SHANGHAI);
        DepositInterest.Result r = DepositInterest.compute(principal, annualRate, termMonths, method, buyDate, today);
        if (r == null) {
            m.put("ok", false);
            m.put("reason", "缺少本金、利率、计息方式或起息日");
            return m;
        }
        m.put("ok", true);
        m.put("valueSource", "interest");
        m.put("profit", r.profit);
        m.put("amount", r.valueNow);
        m.put("valueNow", r.valueNow);
        m.put("daysHeld", r.daysHeld);
        m.put("remainDays", r.remainDays);
        m.put("remainLabel", r.remainLabel);
        m.put("interestMethodLabel", r.interestMethodLabel);
        m.put("note", noteInterest(r));
        return m;
    }

    private void applyQuote(AssetVO vo, Asset asset, boolean fund) {
        if (!StringUtils.hasText(asset.getSymbol()) || asset.getShares() == null
                || asset.getShares().compareTo(BigDecimal.ZERO) <= 0) {
            vo.setEstimateNote("未填代码/持仓，使用登记价值");
            return;
        }
        QuoteSnapshot q = fund ? cachedFund(asset.getSymbol()) : cachedStock(asset.getSymbol());
        if (q == null) {
            vo.setEstimateReason(fund ? "基金行情暂不可用" : "股票行情暂不可用");
            vo.setEstimateNote(vo.getEstimateReason());
            return;
        }
        BigDecimal live = q.getLastPrice().multiply(asset.getShares()).setScale(2, RoundingMode.HALF_UP);
        vo.setAmount(live);
        vo.setValueSource("quote");
        vo.setQuoteName(q.getName());
        vo.setLastPrice(q.getLastPrice());
        vo.setQuoteTime(q.getQuoteTime());
        vo.setEstimateNote(noteQuote(q, asset.getShares()));
        fillLivePnl(vo);
    }

    private void applyInterest(AssetVO vo, Asset asset) {
        LocalDate today = LocalDate.now(SHANGHAI);
        DepositInterest.Result r = DepositInterest.compute(
                asset.getAmount(), asset.getAnnualRate(), asset.getTermMonths(),
                asset.getInterestMethod(), asset.getBuyDate(), today);
        if (r == null) {
            vo.setEstimateNote("未填利率/计息方式，使用本金");
            return;
        }
        vo.setAmount(r.valueNow);
        vo.setValueSource("interest");
        vo.setProfit(r.profit);
        vo.setRemainDays(r.remainDays);
        vo.setRemainLabel(r.remainLabel);
        vo.setInterestMethodLabel(r.interestMethodLabel);
        vo.setEstimateNote(noteInterest(r));
        if (asset.getCost() != null) {
            vo.setPnl(r.valueNow.subtract(asset.getCost()).setScale(2, RoundingMode.HALF_UP));
        } else {
            vo.setPnl(r.profit);
        }
    }

    private void applyListing(AssetVO vo, Asset asset, boolean car) {
        ListingEstimate.Listing listing;
        if (car) {
            if (!StringUtils.hasText(asset.getCarModel())) {
                vo.setEstimateNote("未填车型，使用登记价值");
                return;
            }
            listing = resolveCar(asset.getCarModel(), asset.getCity(), asset.getModelYear(),
                    asset.getMileageKm(), asset.getCost(), asset.getBuyDate());
        } else {
            if (!StringUtils.hasText(asset.getCity()) || !StringUtils.hasText(asset.getCommunity())) {
                vo.setEstimateNote("未填城市/小区，使用登记价值");
                return;
            }
            listing = cachedHouse(asset.getCity(), asset.getCommunity(), asset.getAreaSqm());
        }
        if (listing.estimate == null) {
            vo.setEstimateReason(listing.reason != null ? listing.reason : "二手平台暂无法访问");
            vo.setEstimateNote(vo.getEstimateReason());
            return;
        }
        vo.setAmount(listing.estimate);
        vo.setValueSource(isAgeSource(listing.source) ? "age" : "listing");
        vo.setSampleCount(listing.sampleCount);
        vo.setEstimateSource(listing.source);
        vo.setEstimateNote(listing.note);
        fillLivePnl(vo);
    }


    private ListingEstimate.Listing resolveCar(String model, String city, Integer year, Integer km,
                                               BigDecimal cost, LocalDate buyDate) {
        ListingEstimate.Listing listing = cachedCar(model, city, year, km);
        if (listing.estimate != null) {
            return listing;
        }
        if (cost != null && cost.compareTo(BigDecimal.ZERO) > 0) {
            return ResidualAge.estimateCar(cost, year, buyDate, km);
        }
        BigDecimal guide = cache.get("msrp:" + n(model) + "|" + year, () -> Che300Catalog.msrpYuan(model, year));
        if (guide != null) {
            return ResidualAge.fromGuidePrice(guide, year, buyDate, km);
        }
        return ResidualAge.estimateCar(cost, year, buyDate, km);
    }

    private static boolean isAgeSource(String source) {
        return "车龄估算".equals(source) || "指导价估算".equals(source);
    }

    private QuoteSnapshot cachedStock(String symbol) {
        String code = TencentQuotes.toStockCode(symbol);
        if (code == null) {
            return null;
        }
        return cache.get("stock:" + code, () -> marketClient.fetchStock(symbol));
    }

    private QuoteSnapshot cachedFund(String symbol) {
        String code = TencentQuotes.toFundCode(symbol);
        if (code == null) {
            return null;
        }
        return cache.get("fund:" + code, () -> marketClient.fetchFund(symbol));
    }

    private ListingEstimate.Listing cachedCar(String model, String city, Integer year, Integer km) {
        String key = "car:" + n(model) + "|" + n(city) + "|" + year + "|" + km;
        return cache.get(key, () -> listingClient.estimateCar(model, city, year, km), QuoteCache::listingTtl);
    }

    private ListingEstimate.Listing cachedHouse(String city, String community, BigDecimal area) {
        String key = "house:" + n(city) + "|" + n(community) + "|" + (area == null ? "" : area.toPlainString());
        return cache.get(key, () -> listingClient.estimateHouse(city, community, area), QuoteCache::listingTtl);
    }

    private static String n(String s) {
        return s == null ? "" : s.trim();
    }

    private static void fillStoredPnl(AssetVO vo) {
        if (vo.getAmount() != null && vo.getCost() != null) {
            vo.setPnl(vo.getAmount().subtract(vo.getCost()).setScale(2, RoundingMode.HALF_UP));
        }
    }

    private static void fillLivePnl(AssetVO vo) {
        if (vo.getAmount() != null && vo.getCost() != null) {
            vo.setPnl(vo.getAmount().subtract(vo.getCost()).setScale(2, RoundingMode.HALF_UP));
        }
    }

    private static String noteQuote(QuoteSnapshot q, BigDecimal shares) {
        String name = q.getName() == null ? "" : q.getName() + " ";
        String sh = shares.stripTrailingZeros().toPlainString();
        return name + "行情 " + q.getLastPrice().stripTrailingZeros().toPlainString()
                + " × " + sh + (shares.compareTo(BigDecimal.ONE) == 0 ? "股" : "份");
    }

    private static String noteInterest(DepositInterest.Result r) {
        String p = "利润 " + (r.profit.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                + r.profit.toPlainString();
        if (r.remainLabel != null) {
            return p + "，" + r.remainLabel;
        }
        return p + "（持有 " + r.daysHeld + " 天，" + r.interestMethodLabel + "）";
    }
}
