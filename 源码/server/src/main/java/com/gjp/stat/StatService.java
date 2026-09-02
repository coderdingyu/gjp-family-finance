package com.gjp.stat;

import com.gjp.common.UserContext;
import com.gjp.entity.Member;
import com.gjp.mapper.MemberMapper;
import com.gjp.mapper.StatMapper;
import com.gjp.stat.vo.AmountItem;
import com.gjp.stat.vo.BudgetVO;
import com.gjp.stat.vo.MonthAmount;
import com.gjp.stat.vo.OverviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计业务。
 *
 * 职责边界（对应课程要求"统计和分析的功能要区分清楚"）：
 * 本类只做客观数据的汇总 —— 总额、占比、趋势、排行，不下任何结论；
 * "为什么多花了钱"这类判断在 AnalysisService 中完成。
 */
@Service
public class StatService {

    private static final int SCALE = 2;

    @Autowired
    private StatMapper statMapper;
    @Autowired
    private MemberMapper memberMapper;

    /** 关键指标卡：收入、支出、结余、笔数、月均、单笔最大支出、人情往来支出 */
    public OverviewVO overview(DateRange range) {
        Long familyId = UserContext.getFamilyId();
        BigDecimal income = statMapper.sumAmount(familyId, 1, range.getStart(), range.getEnd());
        BigDecimal expense = statMapper.sumAmount(familyId, 2, range.getStart(), range.getEnd());
        int months = range.monthCount();

        OverviewVO vo = new OverviewVO();
        vo.setTotalIncome(income);
        vo.setTotalExpense(expense);
        vo.setBalance(income.subtract(expense));
        vo.setRecordCount(statMapper.countRecords(familyId, range.getStart(), range.getEnd()));
        vo.setAvgMonthlyIncome(divide(income, BigDecimal.valueOf(months)));
        vo.setAvgMonthlyExpense(divide(expense, BigDecimal.valueOf(months)));
        vo.setMaxExpense(statMapper.maxExpense(familyId, range.getStart(), range.getEnd()));
        vo.setGiftExpense(statMapper.sumGiftExpense(familyId, range.getStart(), range.getEnd()));
        return vo;
    }

    /**
     * 收支趋势。数据库只会返回有流水的月份，这里把区间内缺失的月份补成 0，
     * 否则折线图会把 3 月和 6 月画成相邻的两点，视觉上失真。
     *
     * 但"补 0"只补到当前月为止：按年查询当年时区间末是 12 月，
     * 把还没到的月份也补成 0 会在折线图尾部拖出一段假的零值，看起来像收支突然归零。
     */
    public List<MonthAmount> trend(DateRange range) {
        Long familyId = UserContext.getFamilyId();
        List<MonthAmount> rows = statMapper.selectMonthlyTrend(familyId, range.getStart(), range.getEnd());
        Map<String, MonthAmount> byYm = new HashMap<>();
        for (MonthAmount m : rows) {
            m.setBalance(m.getIncome().subtract(m.getExpense()));
            byYm.put(m.getYm(), m);
        }

        List<MonthAmount> full = new ArrayList<>();
        YearMonth cursor = YearMonth.from(range.getStart());
        YearMonth last = YearMonth.from(range.effectiveEnd());
        if (last.isBefore(cursor)) {
            last = cursor;
        }
        while (!cursor.isAfter(last)) {
            String key = String.format("%04d-%02d", cursor.getYear(), cursor.getMonthValue());
            MonthAmount m = byYm.get(key);
            if (m == null) {
                m = new MonthAmount();
                m.setYm(key);
                m.setIncome(BigDecimal.ZERO);
                m.setExpense(BigDecimal.ZERO);
                m.setBalance(BigDecimal.ZERO);
            }
            full.add(m);
            cursor = cursor.plusMonths(1);
        }
        return full;
    }

    /** 一级分类占比 */
    public List<AmountItem> categoryStat(Integer type, DateRange range) {
        List<AmountItem> list = statMapper.selectCategoryStat(
                UserContext.getFamilyId(), type, range.getStart(), range.getEnd());
        return withRatio(list);
    }

    /** 二级分类构成（点击饼图某一块后的钻取） */
    public List<AmountItem> subCategoryStat(Long parentId, DateRange range) {
        List<AmountItem> list = statMapper.selectSubCategoryStat(
                UserContext.getFamilyId(), parentId, range.getStart(), range.getEnd());
        return withRatio(list);
    }

    /** 成员收支对比 */
    public List<AmountItem> memberStat(Integer type, DateRange range) {
        List<AmountItem> list = statMapper.selectMemberStat(
                UserContext.getFamilyId(), type, range.getStart(), range.getEnd());
        return withRatio(list);
    }

    /** 商家消费排行 */
    public List<AmountItem> merchantRank(DateRange range, int limit) {
        List<AmountItem> list = statMapper.selectMerchantRank(
                UserContext.getFamilyId(), range.getStart(), range.getEnd(), limit);
        return withRatio(list);
    }

    /** 消费片区分布 */
    public List<AmountItem> areaStat(DateRange range) {
        List<AmountItem> list = statMapper.selectAreaStat(
                UserContext.getFamilyId(), range.getStart(), range.getEnd());
        return withRatio(list);
    }

    /** 支付方式构成 */
    public List<AmountItem> payMethodStat(Integer type, DateRange range) {
        List<AmountItem> list = statMapper.selectPayMethodStat(
                UserContext.getFamilyId(), type, range.getStart(), range.getEnd());
        return withRatio(list);
    }

    /**
     * 成员预算执行情况。预算是"月度"口径，所以这里固定按指定月份统计，
     * 不跟着看板的年度区间走，否则年支出对月预算必然显示超支，预警就没有意义了。
     */
    public List<BudgetVO> budgetStat(YearMonth ym) {
        Long familyId = UserContext.getFamilyId();
        DateRange month = DateRange.ofMonth(ym);
        List<BudgetVO> result = new ArrayList<>();
        for (Member member : memberMapper.selectByFamily(familyId)) {
            BigDecimal expense = statMapper.sumMemberExpense(
                    familyId, member.getId(), month.getStart(), month.getEnd());
            BigDecimal budget = member.getMonthlyBudget() == null ? BigDecimal.ZERO : member.getMonthlyBudget();

            BudgetVO vo = new BudgetVO();
            vo.setMemberId(member.getId());
            vo.setMemberName(member.getMemberName());
            vo.setBudget(budget);
            vo.setExpense(expense);
            if (budget.compareTo(BigDecimal.ZERO) <= 0) {
                vo.setUsedRate(BigDecimal.ZERO);
                vo.setStatus("未设预算");
            } else {
                BigDecimal rate = divide(expense.multiply(BigDecimal.valueOf(100)), budget);
                vo.setUsedRate(rate);
                if (rate.compareTo(BigDecimal.valueOf(100)) > 0) {
                    vo.setStatus("已超支");
                } else if (rate.compareTo(BigDecimal.valueOf(80)) >= 0) {
                    vo.setStatus("接近上限");
                } else {
                    vo.setStatus("正常");
                }
            }
            result.add(vo);
        }
        return result;
    }

    /**
     * 看板一次性数据。前端首页要画 5 张图，逐个接口请求会产生 5 次往返，
     * 这里合并成一个接口返回，首屏明显更快。
     */
    public Map<String, Object> dashboard(DateRange range) {
        Map<String, Object> map = new HashMap<>();
        map.put("range", range.toString());
        map.put("overview", overview(range));
        map.put("trend", trend(range));
        map.put("expenseCategory", categoryStat(2, range));
        map.put("incomeCategory", categoryStat(1, range));
        map.put("memberExpense", memberStat(2, range));
        map.put("merchantRank", merchantRank(range, 10));
        map.put("areaStat", areaStat(range));
        map.put("payMethod", payMethodStat(2, range));
        YearMonth budgetYm = budgetMonthOf(range);
        map.put("budget", budgetStat(budgetYm));
        map.put("budgetMonth", budgetYm.toString());
        return map;
    }

    /**
     * 看板预算始终落在「今天所在月」（若该月落在所选区间内）。
     * 按年查看时区间末日是 12-31，若跟着区间走会把 12 月空账当成当月预算。
     */
    YearMonth budgetMonthOf(DateRange range) {
        YearMonth now = YearMonth.now();
        YearMonth start = YearMonth.from(range.getStart());
        YearMonth end = YearMonth.from(range.effectiveEnd());
        if (!now.isBefore(start) && !now.isAfter(end)) {
            return now;
        }
        return now.isAfter(end) ? end : start;
    }

    /** 统一计算占比，前端直接拿百分数用 */
    private List<AmountItem> withRatio(List<AmountItem> list) {
        BigDecimal total = BigDecimal.ZERO;
        for (AmountItem item : list) {
            total = total.add(item.getAmount());
        }
        for (AmountItem item : list) {
            item.setRatio(total.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : divide(item.getAmount().multiply(BigDecimal.valueOf(100)), total));
        }
        return list;
    }

    /** 除法统一保留两位小数，除数为 0 时返回 0 而不是抛异常 */
    private BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b == null || b.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return a.divide(b, SCALE, RoundingMode.HALF_UP);
    }
}
