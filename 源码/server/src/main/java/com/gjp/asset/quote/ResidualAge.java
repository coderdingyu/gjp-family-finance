package com.gjp.asset.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 二手挂牌不可用时，用取得成本和车龄做透明估算，避免只报「暂无法访问」。
 * 首年约保留 82%，之后每年再乘 0.90；里程高于 1.5 万公里/年再略扣。下限 18%。
 */
public final class ResidualAge {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal FLOOR = new BigDecimal("0.18");

    private ResidualAge() {
    }

    public static ListingEstimate.Listing estimateCar(BigDecimal cost, Integer modelYear,
                                                      LocalDate buyDate, Integer mileageKm) {
        return estimateCar(cost, modelYear, buyDate, mileageKm, LocalDate.now(SHANGHAI));
    }

    public static ListingEstimate.Listing estimateCar(BigDecimal cost, Integer modelYear,
                                                      LocalDate buyDate, Integer mileageKm, LocalDate today) {
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            return ListingEstimate.Listing.fail("二手平台暂无法访问，且未填取得成本，无法估算");
        }
        double years = yearsHeld(modelYear, buyDate, today);
        double retain = retainWithMileage(years, mileageKm);
        BigDecimal est = cost.multiply(BigDecimal.valueOf(retain)).setScale(2, RoundingMode.HALF_UP);
        String note = String.format("按车龄约 %.1f 年估算残值（二手挂牌暂不可用，仅供参考）", years);
        return new ListingEstimate.Listing(est, 0, "车龄估算", note, null, null);
    }

    /** 挂牌不可用且用户未填成本时：用新车指导价当成本再折车龄。 */
    public static ListingEstimate.Listing fromGuidePrice(BigDecimal guideYuan, Integer modelYear,
                                                         LocalDate buyDate, Integer mileageKm) {
        return fromGuidePrice(guideYuan, modelYear, buyDate, mileageKm, LocalDate.now(SHANGHAI));
    }

    static ListingEstimate.Listing fromGuidePrice(BigDecimal guideYuan, Integer modelYear,
                                                  LocalDate buyDate, Integer mileageKm, LocalDate today) {
        ListingEstimate.Listing base = estimateCar(guideYuan, modelYear, buyDate, mileageKm, today);
        if (base.estimate == null) {
            return base;
        }
        double years = yearsHeld(modelYear, buyDate, today);
        String guide = guideYuan.stripTrailingZeros().toPlainString();
        String note = String.format("按车300新车指导价 %s 元、车龄约 %.1f 年估算（二手挂牌暂不可用，仅供参考）",
                guide, years);
        return new ListingEstimate.Listing(base.estimate, 0, "指导价估算", note, null, null);
    }

    static double yearsHeld(Integer modelYear, LocalDate buyDate, LocalDate today) {
        if (buyDate != null) {
            long days = ChronoUnit.DAYS.between(buyDate, today);
            return Math.max(0, days / 365.0);
        }
        if (modelYear != null && modelYear >= 1980) {
            LocalDate start = LocalDate.of(modelYear, 7, 1);
            long days = ChronoUnit.DAYS.between(start, today);
            return Math.max(0, days / 365.0);
        }
        return 0;
    }

    static double retainWithMileage(double years, Integer mileageKm) {
        double retain = retainRatio(years);
        if (mileageKm != null && mileageKm > 0 && years > 0.05) {
            double expected = 15000 * years;
            if (mileageKm > expected) {
                retain *= Math.max(0.5, 1 - 0.008 * (mileageKm - expected) / 10000.0);
            }
        }
        return Math.max(FLOOR.doubleValue(), Math.min(0.98, retain));
    }

    static double retainRatio(double years) {
        if (years <= 0) {
            return 0.98;
        }
        if (years <= 1) {
            return 1 - 0.18 * years;
        }
        return 0.82 * Math.pow(0.90, years - 1);
    }
}
