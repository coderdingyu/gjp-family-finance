package com.gjp.analysis;

import com.gjp.entity.Member;
import com.gjp.stat.DateRange;
import com.gjp.stat.vo.AmountItem;
import com.gjp.stat.vo.MonthAmount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/** 纯规则计算：无网络、无数据库，时间显式传入以支持边界回归测试。 */
public final class AnalysisRules {
    private AnalysisRules() {}
    static BigDecimal div(BigDecimal a, BigDecimal b) {
        return b.signum() == 0 ? BigDecimal.ZERO : a.divide(b, 2, RoundingMode.HALF_UP);
    }
    static BigDecimal percent(BigDecimal a, BigDecimal b) { return div(a.multiply(BigDecimal.valueOf(100)), b); }
    static BigDecimal sum(List<MonthAmount> rows, boolean income) {
        return rows.stream().map(m -> income ? m.getIncome() : m.getExpense()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public static boolean fullMonth(YearMonth ym, DateRange range, LocalDate today) {
        return !ym.atDay(1).isBefore(range.getStart()) && !ym.atEndOfMonth().isAfter(range.getEnd())
                && !ym.atEndOfMonth().isAfter(today);
    }

    public static AnalysisReport evaluate(AnalysisData d, LocalDate today) {
        AnalysisReport r = new AnalysisReport();
        r.startDate = d.range.getStart().toString(); r.endDate = d.range.getEnd().toString();
        r.memberId = d.memberId;
        r.scopeLabel = d.memberId == null ? "全家" : d.members.get(0).getMemberName();
        BigDecimal income = sum(d.trend, true), expense = sum(d.trend, false);
        r.totals.put("income", income); r.totals.put("expense", expense);
        r.totals.put("balance", income.subtract(expense));
        List<MonthAmount> complete = d.trend.stream()
                .filter(m -> fullMonth(YearMonth.parse(m.getYm()), d.range, today)).toList();
        List<MonthAmount> comparable = complete.stream().filter(m -> m.getExpense().signum() > 0).toList();
        BigDecimal coverage = percent(BigDecimal.valueOf(d.quality.merchantFilledCount), BigDecimal.valueOf(d.quality.expenseCount));
        r.quality.put("recordCount", d.trend.stream().mapToInt(MonthAmount::getRecordCount).sum());
        r.quality.put("expenseCount", d.quality.expenseCount);
        r.quality.put("merchantCoverage", coverage);
        r.quality.put("comparableMonths", comparable.size());
        r.quality.put("months", d.trend.stream().map(m -> Map.of(
                "ym", m.getYm(), "recordCount", m.getRecordCount(),
                "status", !fullMonth(YearMonth.parse(m.getYm()), d.range, today) ? "未完整覆盖"
                        : m.getRecordCount() == 0 ? "无记录，不能确认零支出"
                        : m.getExpense().signum() == 0 ? "已记支出为零，完整性待确认" : "有支出记录"
        )).toList());
        r.notices.add("分析只描述已录入账目，不能据此确认收支记录完整；异常不等于浪费。");
        if (d.quality.incomeCount == 0) r.notices.add("没有收入记录，暂不能评价结余健康度。");
        if (comparable.size() < 3) r.notices.add("完整且有支出的月份不足三个，暂不进行异常月份归因。");
        if (complete.stream().anyMatch(m -> m.getRecordCount() == 0)) r.notices.add("存在整月无记录的情况，不补作真实零支出，也不跨过缺失月计算环比。");
        if (coverage.compareTo(BigDecimal.valueOf(80)) < 0 && d.quality.expenseCount > 0)
            r.notices.add("商家信息覆盖率较低，商家榜仅代表已填写部分。覆盖率按支出笔数计算，与榜单金额占比不同。");

        if (d.trend.stream().mapToInt(MonthAmount::getRecordCount).sum() == 0) {
            add(r, "A0", "info", "所选区间没有收支记录", "尚无可用于计算的流水。", "请先检查日期范围或补充账单。");
            return r;
        }
        abnormal(r, d, comparable);
        monthOnMonth(r, d, complete);
        balance(r, income, expense);
        budgets(r, d, today);
        if (!d.categories.isEmpty() && expense.signum() > 0) {
            AmountItem top = d.categories.get(0);
            AnalysisItem item = add(r, "A5", "info", "支出最多的分类是「" + top.getName() + "」",
                    "已记支出 " + top.getAmount() + " 元，占总支出 " + top.getRatio() + "%（" + top.getCount() + " 笔）。",
                    "占比高不等于异常。请先区分固定支出、可调整支出和一次性事件，再决定是否调整预算。");
            item.getMetrics().putAll(Map.of("amount", top.getAmount(), "ratio", top.getRatio(), "count", top.getCount()));
            item.getRecordFilters().put("categoryId", top.getId().toString());
        }
        if (!d.merchants.isEmpty()) {
            AmountItem top = d.merchants.get(0);
            AnalysisItem item = add(r, "A6", "info", "商家消费流向：「" + top.getName() + "」占 " + top.getRatio() + "%",
                    "该商家支出 " + top.getAmount() + " 元。分母为所有已填商家且排除房租房贷分类的支出，不是前几名小计。",
                    "集中消费不必然意味着浪费；先核对消费次数、用途和是否包含一次性支出，再考虑替代方案。");
            item.getMetrics().putAll(Map.of("amount", top.getAmount(), "ratio", top.getRatio(), "merchantCoverage", coverage));
            // keyword 同时匹配备注，不能冒充商家精确筛选；入口明确为整个区间流水。
        }
        if (!d.areas.isEmpty()) {
            AmountItem top = d.areas.get(0);
            AnalysisItem item = add(r, "A7", "info", "消费片区参考：「" + top.getName() + "」",
                    "已填写片区的支出中，该片区占 " + top.getRatio() + "%。", "片区为可选信息，只作参考，不作为风险判断。");
            item.getRecordFilters().put("area", top.getName());
        }
        if (d.gift.signum() > 0) {
            AnalysisItem item = add(r, "A8", "info", "人情往来支出参考",
                    "已标记人情往来的支出 " + d.gift + " 元，占总支出 " + percent(d.gift, expense) + "%。",
                    "请结合节日、婚庆等事件核对用途；占比本身不代表不合理。");
            item.getMetrics().putAll(Map.of("amount", d.gift, "ratio", percent(d.gift, expense)));
            item.getRecordFilters().put("isGift", "1");
        }
        AnalysisItems.sort(r.items);
        return r;
    }

    private static AnalysisItem add(AnalysisReport r, String code, String level, String title, String basis, String suggestion) {
        AnalysisItem item = new AnalysisItem(code, level, title, basis, suggestion);
        item.setFindingId(code);
        item.getRecordFilters().put("startDate", r.startDate);
        item.getRecordFilters().put("endDate", r.endDate);
        if (!"A3".equals(code) && !"A0".equals(code)) item.getRecordFilters().put("type", "2");
        if (r.memberId != null) item.getRecordFilters().put("memberId", r.memberId.toString());
        r.items.add(item);
        return item;
    }

    private static void abnormal(AnalysisReport r, AnalysisData d, List<MonthAmount> comparable) {
        if (comparable.size() < 3) return;
        MonthAmount peak = comparable.stream().max(Comparator.comparing(MonthAmount::getExpense)).orElseThrow();
        BigDecimal average = div(sum(comparable, false).subtract(peak.getExpense()), BigDecimal.valueOf(comparable.size() - 1));
        if (average.signum() == 0 || peak.getExpense().compareTo(average.multiply(new BigDecimal("1.30"))) < 0) return;
        Set<String> samples = comparable.stream().map(MonthAmount::getYm).collect(Collectors.toSet());
        Map<Long, BigDecimal> otherTotals = new HashMap<>();
        d.categoryMonths.stream().filter(c -> samples.contains(c.ym) && !peak.getYm().equals(c.ym))
                .forEach(c -> otherTotals.merge(c.categoryId, c.amount, BigDecimal::add));
        AnalysisData.CategoryMonth top = null;
        BigDecimal topGap = BigDecimal.ZERO, topAverage = BigDecimal.ZERO;
        for (AnalysisData.CategoryMonth c : d.categoryMonths) {
            if (!peak.getYm().equals(c.ym)) continue;
            BigDecimal avg = div(otherTotals.getOrDefault(c.categoryId, BigDecimal.ZERO), BigDecimal.valueOf(samples.size() - 1));
            BigDecimal gap = c.amount.subtract(avg);
            if (gap.compareTo(topGap) > 0) { top = c; topGap = gap; topAverage = avg; }
        }
        String basis = peak.getYm() + " 支出 " + peak.getExpense() + " 元；同一可比样本中其余月份平均 " + average + " 元。";
        if (top != null) basis += "差额贡献最大的分类为「" + top.name + "」，当月 " + top.amount + " 元，对比均值 " + topAverage + " 元，多出 " + topGap + " 元。";
        AnalysisItem item = add(r, "A1", "warning", peak.getYm() + " 已记支出高于可比月均 " + percent(peak.getExpense().subtract(average), average) + "%",
                basis, "先核对大额账单及重复录入。仅凭历史均值无法确认一次性或持续性支出，请结合实际事件判断。");
        item.getMetrics().putAll(Map.of("amount", peak.getExpense(), "baseline", average, "delta", peak.getExpense().subtract(average), "sampleMonths", new TreeSet<>(samples)));
        item.getRecordFilters().put("startDate", YearMonth.parse(peak.getYm()).atDay(1).toString());
        item.getRecordFilters().put("endDate", YearMonth.parse(peak.getYm()).atEndOfMonth().toString());
        if (top != null) item.getMetrics().put("topCategoryDelta", topGap);
    }

    private static void monthOnMonth(AnalysisReport r, AnalysisData d, List<MonthAmount> complete) {
        if (complete.size() < 2) return;
        MonthAmount curr = complete.get(complete.size() - 1), prev = complete.get(complete.size() - 2);
        if (!YearMonth.parse(prev.getYm()).plusMonths(1).equals(YearMonth.parse(curr.getYm()))
                || curr.getRecordCount() == 0 || prev.getRecordCount() == 0) return;
        BigDecimal delta = curr.getExpense().subtract(prev.getExpense());
        BigDecimal rate = percent(delta, prev.getExpense());
        if (prev.getExpense().signum() > 0 && rate.abs().compareTo(BigDecimal.valueOf(20)) < 0) return;
        String title = curr.getYm() + " 与上月已记支出对比";
        String basis = curr.getYm() + " 支出 " + curr.getExpense() + " 元；" + prev.getYm() + " 支出 " + prev.getExpense() + " 元；差额 " + delta + " 元。";
        basis += prev.getExpense().signum() == 0 ? "上月已记支出为零，不计算百分比。" : "环比变化 " + rate + "% 。";
        AnalysisItem item = add(r, "A2", delta.signum() > 0 ? "warning" : "info", title, basis,
                "先确认两个月记录完整。支出下降也可能来自漏记，不能直接认定消费控制改善。");
        item.getMetrics().putAll(Map.of("previousMonth", prev.getYm(), "currentMonth", curr.getYm(), "previousAmount", prev.getExpense(), "amount", curr.getExpense(), "delta", delta));
        if (prev.getExpense().signum() > 0) item.getMetrics().put("rate", rate);
        item.getRecordFilters().put("startDate", YearMonth.parse(prev.getYm()).atDay(1).toString());
        item.getRecordFilters().put("endDate", YearMonth.parse(curr.getYm()).atEndOfMonth().toString());
        Map<Long, AnalysisData.CategoryMonth> before = new HashMap<>();
        d.categoryMonths.stream().filter(c -> prev.getYm().equals(c.ym)).forEach(c -> before.put(c.categoryId, c));
        AnalysisData.CategoryMonth top = null;
        BigDecimal largest = BigDecimal.ZERO;
        for (AnalysisData.CategoryMonth c : d.categoryMonths) {
            if (!curr.getYm().equals(c.ym)) continue;
            BigDecimal gap = c.amount.subtract(before.containsKey(c.categoryId) ? before.get(c.categoryId).amount : BigDecimal.ZERO);
            if (gap.compareTo(largest) > 0) { largest = gap; top = c; }
        }
        if (delta.signum() > 0 && top != null) {
            AnalysisData.CategoryMonth old = before.get(top.categoryId);
            String detail = "其中「" + top.name + "」增加 " + largest + " 元，本月 " + top.count + " 笔。";
            if (old != null && old.count > 0 && top.count > 0) {
                BigDecimal priorAverage = old.amount.divide(BigDecimal.valueOf(old.count), 8, RoundingMode.HALF_UP);
                BigDecimal frequency = priorAverage.multiply(BigDecimal.valueOf(top.count - old.count)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal ticket = largest.subtract(frequency);
                detail += "上月 " + old.count + " 笔；按上月单笔均额分解，次数变化对应 " + frequency + " 元，单笔均额变化对应 " + ticket + " 元（算术分解，不代表价格或因果判断）。";
                item.getMetrics().putAll(Map.of("frequencyEffect", frequency, "averageEffect", ticket, "previousCount", old.count, "currentCount", top.count));
            } else detail += "上月该分类无支出记录，不能据此判断新增消费习惯。";
            item.setBasis(item.getBasis() + detail);
            item.getMetrics().put("topCategoryId", top.categoryId);
        }
    }

    private static void balance(AnalysisReport r, BigDecimal income, BigDecimal expense) {
        if (income.signum() == 0) {
            add(r, "A3", "info", "收入记录不足，无法评价结余率", "已记收入为零，已记支出 " + expense + " 元。", "请先确认收入是否漏记。");
            return;
        }
        BigDecimal balance = income.subtract(expense), rate = percent(balance, income);
        String level = balance.signum() < 0 ? "danger" : rate.compareTo(BigDecimal.TEN) < 0 ? "warning" : "good";
        AnalysisItem item = add(r, "A3", level, "账面结余率 " + rate + "%",
                "收入 " + income + " 元，支出 " + expense + " 元，结余 " + balance + " 元。",
                "此结论以收支录入完整为前提；结合一次性事件和家庭目标核对，不直接把分类占比高视为可削减支出。");
        item.getMetrics().putAll(Map.of("income", income, "expense", expense, "balance", balance, "rate", rate));
    }

    private static void budgets(AnalysisReport r, AnalysisData d, LocalDate today) {
        Map<Long, Member> members = d.members.stream().collect(Collectors.toMap(Member::getId, m -> m));
        boolean hasBudget = false;
        for (AnalysisData.MemberMonth row : d.memberMonths) {
            Member member = members.get(row.memberId);
            if (member == null || member.getMonthlyBudget() == null || member.getMonthlyBudget().signum() <= 0) continue;
            YearMonth ym = YearMonth.parse(row.ym);
            boolean complete = fullMonth(ym, d.range, today);
            boolean currentMtd = ym.equals(YearMonth.from(today)) && !ym.atDay(1).isBefore(d.range.getStart()) && d.range.getEnd().equals(today);
            if (!complete && !currentMtd) continue;
            hasBudget = true;
            BigDecimal rate = percent(row.amount, member.getMonthlyBudget());
            boolean over = row.amount.compareTo(member.getMonthlyBudget()) > 0;
            if (!over && (!complete || rate.compareTo(BigDecimal.valueOf(80)) < 0)) continue;
            AnalysisItem item = add(r, "A4", over ? "danger" : "warning",
                    member.getMemberName() + " · " + row.ym + (over ? " 支出超过当前月预算参考值" : " 已记支出接近当前月预算参考值"),
                    "当前月预算 " + member.getMonthlyBudget() + " 元，" + (complete ? "该月" : "本月至今") + "已记支出 " + row.amount + " 元，使用率 " + rate + "% 。",
                    "核对账单和预算是否适合实际需求。历史月份按当前预算回看，并非当时预算的正式执行结果。");
            item.setFindingId("A4:" + row.memberId + ":" + row.ym);
            item.getMetrics().putAll(Map.of("memberId", row.memberId, "month", row.ym, "budget", member.getMonthlyBudget(), "amount", row.amount, "usedRate", rate));
            item.getRecordFilters().put("memberId", row.memberId.toString());
            item.getRecordFilters().put("startDate", ym.atDay(1).toString());
            item.getRecordFilters().put("endDate", (complete ? ym.atEndOfMonth() : today).toString());
        }
        if (hasBudget) r.notices.add("预算尚无历史版本，历史月份统一使用当前成员预算作参考；不完整的历史月份不作预算比较。");
        r.notices.add("疑似重复账单会影响汇总，可先前往账单查重核对；本报告未自动剔除任何记录。");
    }
}
