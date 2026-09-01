package com.gjp.stat;

import com.gjp.common.BizException;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 统计区间。所有统计接口都通过它把前端传来的参数收敛成一个闭区间 [start, end]，
 * 这样 SQL 里只需要写一种 BETWEEN 条件，统计口径不会因为接口不同而漂移。
 */
public class DateRange {

    private final LocalDate start;
    private final LocalDate end;

    private DateRange(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
    }

    /**
     * 解析统计区间，优先级：显式的起止日期 &gt; 年月 &gt; 年份 &gt; 默认当年。
     */
    public static DateRange of(LocalDate start, LocalDate end, Integer year, Integer month) {
        if (start != null && end != null) {
            if (start.isAfter(end)) {
                throw new BizException("起始日期不能晚于结束日期");
            }
            return new DateRange(start, end);
        }
        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            return new DateRange(ym.atDay(1), ym.atEndOfMonth());
        }
        int y = year != null ? year : LocalDate.now().getYear();
        return new DateRange(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
    }

    /** 某个月的区间 */
    public static DateRange ofMonth(YearMonth ym) {
        return new DateRange(ym.atDay(1), ym.atEndOfMonth());
    }

    public LocalDate getStart() {
        return start;
    }

    public LocalDate getEnd() {
        return end;
    }

    /** 区间跨越的月份数，用于算月均，至少为 1，避免除零 */
    public int monthCount() {
        int months = (end.getYear() - start.getYear()) * 12 + (end.getMonthValue() - start.getMonthValue()) + 1;
        return Math.max(months, 1);
    }

    @Override
    public String toString() {
        return start + " ~ " + end;
    }
}
