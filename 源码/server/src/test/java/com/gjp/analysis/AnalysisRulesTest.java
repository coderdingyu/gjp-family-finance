package com.gjp.analysis;

import com.gjp.entity.Member;
import com.gjp.stat.DateRange;
import com.gjp.stat.vo.MonthAmount;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisRulesTest {
    static final LocalDate TODAY = LocalDate.of(2026,9,3);
    static BigDecimal n(String s) { return new BigDecimal(s); }
    static MonthAmount month(String ym, String expense, int records) {
        MonthAmount m = new MonthAmount(); m.setYm(ym); m.setExpense(n(expense));
        m.setIncome(records > 0 ? n("1000") : BigDecimal.ZERO); m.setRecordCount(records);
        m.setExpenseCount(records); m.setBalance(m.getIncome().subtract(m.getExpense())); return m;
    }
    static AnalysisData data(String start, String end, MonthAmount... months) {
        AnalysisData d = new AnalysisData(); d.range=DateRange.of(LocalDate.parse(start),LocalDate.parse(end),null,null);
        d.trend=List.of(months); d.members=new ArrayList<>(); d.categories=List.of(); d.merchants=List.of();
        d.areas=List.of(); d.categoryMonths=new ArrayList<>(); d.memberMonths=new ArrayList<>(); d.gift=BigDecimal.ZERO;
        d.quality=new AnalysisData.Quality(); d.quality.incomeCount=months.length; return d;
    }
    static AnalysisData.CategoryMonth category(String ym, long id, String amount, int count) {
        var c = new AnalysisData.CategoryMonth(); c.ym=ym; c.categoryId=id; c.name="餐饮"; c.amount=n(amount); c.count=count; return c;
    }
    static Member member(long id, String name) {
        Member m = new Member(); m.setId(id); m.setMemberName(name); m.setMonthlyBudget(n("100")); return m;
    }
    static AnalysisData.MemberMonth budgetMonth(long id, String ym, String amount) {
        var b=new AnalysisData.MemberMonth(); b.memberId=id; b.ym=ym; b.amount=n(amount); return b;
    }
    static AnalysisItem finding(AnalysisReport r, String code) { return r.items.stream().filter(i->code.equals(i.getCode())).findFirst().orElseThrow(); }

    @Test void absentMiddleMonthIsNotMonthOnMonth() {
        var d=data("2026-06-01","2026-08-31",month("2026-06","100",1),month("2026-07","0",0),month("2026-08","300",1));
        var r=AnalysisRules.evaluate(d,TODAY);
        assertTrue(r.items.stream().noneMatch(i->i.getCode().equals("A2")));
        assertTrue(r.notices.stream().anyMatch(s->s.contains("不跨过缺失月")));
    }
    @Test void partialHistoricalMonthIsNotComplete() {
        var range=DateRange.of(LocalDate.of(2026,7,15),LocalDate.of(2026,8,20),null,null);
        assertFalse(AnalysisRules.fullMonth(YearMonth.of(2026,7),range,TODAY));
        assertFalse(AnalysisRules.fullMonth(YearMonth.of(2026,8),range,TODAY));
        var d=data("2026-07-15","2026-08-20",month("2026-07","100",1),month("2026-08","300",1));
        assertTrue(AnalysisRules.evaluate(d,TODAY).items.stream().noneMatch(i->i.getCode().equals("A2")));
    }
    @Test void zeroBaselineHasNoPercentageOrDivisionError() {
        var d=data("2026-07-01","2026-08-31",month("2026-07","0",1),month("2026-08","100",1));
        var i=finding(AnalysisRules.evaluate(d,TODAY),"A2");
        assertFalse(i.getMetrics().containsKey("rate")); assertTrue(i.getBasis().contains("不计算百分比"));
    }
    @Test void abnormalAttributionExcludesCurrentMonthFromBothNumeratorAndDenominator() {
        var d=data("2026-06-01","2026-09-03",month("2026-06","100",1),month("2026-07","100",1),month("2026-08","200",1),month("2026-09","9999",1));
        d.categoryMonths.addAll(List.of(category("2026-06",1,"100",1),category("2026-07",1,"100",1),category("2026-08",1,"200",1),category("2026-09",1,"9999",1)));
        var i=finding(AnalysisRules.evaluate(d,TODAY),"A1");
        assertEquals(0,n("100").compareTo((BigDecimal)i.getMetrics().get("topCategoryDelta")));
        assertFalse(i.getBasis().contains("9999")); assertFalse(i.getBasis().contains("后续仍会发生"));
    }
    @Test void budgetIdsRemainDistinctEvenForSameName() {
        var d=data("2026-08-01","2026-08-31",month("2026-08","400",2));
        d.members.addAll(List.of(member(1,"同名"),member(2,"同名")));
        d.memberMonths.addAll(List.of(budgetMonth(1,"2026-08","150"),budgetMonth(2,"2026-08","250")));
        var items=AnalysisRules.evaluate(d,TODAY).items.stream().filter(i->i.getCode().equals("A4")).toList();
        assertEquals(2,items.size()); assertNotEquals(items.get(0).getFindingId(),items.get(1).getFindingId());
        assertNotEquals(items.get(0).getRecordFilters().get("memberId"),items.get(1).getRecordFilters().get("memberId"));
    }
    @Test void frequencyAndAverageEffectsAddUpToCategoryChange() {
        var d=data("2026-07-01","2026-08-31",month("2026-07","100",2),month("2026-08","300",3));
        d.categoryMonths.addAll(List.of(category("2026-07",1,"100",2),category("2026-08",1,"300",3)));
        var metrics=finding(AnalysisRules.evaluate(d,TODAY),"A2").getMetrics();
        assertEquals(n("200.00"),((BigDecimal)metrics.get("frequencyEffect")).add((BigDecimal)metrics.get("averageEffect")));
    }
    @Test void incompleteCurrentBudgetCanWarnOnlyAfterActuallyExceedingBudget() {
        var d=data("2026-09-01","2026-09-03",month("2026-09","80",1));
        d.members.add(member(1,"甲")); d.memberMonths.add(budgetMonth(1,"2026-09","80"));
        assertTrue(AnalysisRules.evaluate(d,TODAY).items.stream().noneMatch(i->i.getCode().equals("A4")));
        d.memberMonths.get(0).amount=n("101");
        assertEquals("danger",finding(AnalysisRules.evaluate(d,TODAY),"A4").getLevel());
    }
}
