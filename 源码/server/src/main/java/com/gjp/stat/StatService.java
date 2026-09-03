package com.gjp.stat;

import com.gjp.common.AppTime;
import com.gjp.common.UserContext;
import com.gjp.entity.Member;
import com.gjp.entity.Record;
import com.gjp.record.RecordQuery;
import com.gjp.mapper.MemberMapper;
import com.gjp.mapper.RecordMapper;
import com.gjp.mapper.StatMapper;
import com.gjp.stat.vo.AmountItem;
import com.gjp.stat.vo.BudgetVO;
import com.gjp.stat.vo.MonthAmount;
import com.gjp.stat.vo.OverviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计业务。
 *
 * 职责边界（对应课程要求"统计和分析的功能要区分清楚"）：
 * 本类只做客观数据的汇总 —— 总额、占比、趋势、排行，不下任何结论；
 * "为什么多花了钱"这类判断在 AnalysisService 中完成。
 *
 * 数据范围（需求第 9 条）：每个公开方法都接收一个 requestedMemberId，
 * 统一经 {@link UserContext#resolveMemberId(Long)} 收敛 ——
 * 普通成员无论传什么都会被强制成自己，户主传空看全家、传成员ID看单人。
 * 权限判断只在这一处，新增统计接口时照抄这个模式就不会漏。
 */
@Service
public class StatService {

    private static final int SCALE = 2;

    @Autowired
    private StatMapper statMapper;
    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private RecordMapper recordMapper;

    /** 关键指标卡：收入、支出、结余、笔数、月均、单笔最大支出、人情往来支出 */
    public OverviewVO overview(DateRange range, Long requestedMemberId) {
        Long familyId = UserContext.getFamilyId();
        Long memberId = UserContext.resolveMemberId(requestedMemberId);

        BigDecimal income = statMapper.sumAmount(familyId, 1, range.getStart(), range.getEnd(), memberId);
        BigDecimal expense = statMapper.sumAmount(familyId, 2, range.getStart(), range.getEnd(), memberId);
        int months = range.monthCount();

        OverviewVO vo = new OverviewVO();
        vo.setTotalIncome(income);
        vo.setTotalExpense(expense);
        vo.setBalance(income.subtract(expense));
        vo.setRecordCount(statMapper.countRecords(familyId, range.getStart(), range.getEnd(), memberId));
        vo.setAvgMonthlyIncome(divide(income, BigDecimal.valueOf(months)));
        vo.setAvgMonthlyExpense(divide(expense, BigDecimal.valueOf(months)));
        vo.setMaxExpense(statMapper.maxExpense(familyId, range.getStart(), range.getEnd(), memberId));
        vo.setGiftExpense(statMapper.sumGiftExpense(familyId, range.getStart(), range.getEnd(), memberId));
        return vo;
    }

    /**
     * 收支趋势。数据库只会返回有流水的月份，这里把区间内缺失的月份补成 0，
     * 否则折线图会把 3 月和 6 月画成相邻的两点，视觉上失真。
     *
     * 但"补 0"只补到当前月为止：按年查询当年时区间末是 12 月，
     * 把还没到的月份也补成 0 会在折线图尾部拖出一段假的零值，看起来像收支突然归零。
     */
    public List<MonthAmount> trend(DateRange range, Long requestedMemberId) {
        Long familyId = UserContext.getFamilyId();
        Long memberId = UserContext.resolveMemberId(requestedMemberId);

        List<MonthAmount> rows = statMapper.selectMonthlyTrend(
                familyId, range.getStart(), range.getEnd(), memberId);
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
    public List<AmountItem> categoryStat(Integer type, DateRange range, Long requestedMemberId) {
        return withRatio(statMapper.selectCategoryStat(UserContext.getFamilyId(), type,
                range.getStart(), range.getEnd(), UserContext.resolveMemberId(requestedMemberId)));
    }

    /** 子分类构成（点击饼图某一块后的钻取，支持一级→二级→三级逐层下钻） */
    public List<AmountItem> subCategoryStat(Long parentId, DateRange range, Long requestedMemberId) {
        Long familyId = UserContext.getFamilyId();
        Long memberId = UserContext.resolveMemberId(requestedMemberId);
        List<AmountItem> children = new ArrayList<>(statMapper.selectSubCategoryStat(
                familyId, parentId, range.getStart(), range.getEnd(), memberId));
        List<AmountItem> self = statMapper.selectDirectCategoryStat(
                familyId, parentId, range.getStart(), range.getEnd(), memberId);
        for (AmountItem item : self) {
            if (item.getAmount() != null && item.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                // 不能再拿父分类 ID，否则前端会把「未细分」当成还能继续下钻
                item.setId(null);
                children.add(item);
            }
        }
        children.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));
        return withRatio(children);
    }

    /**
     * 成员收支对比。
     * 普通成员看到的只有自己一根柱子 —— 这是权限收敛的结果，不是 bug；
     * 前端会在这种情况下把图表标题改成"我的支出"以免产生误解。
     */
    public List<AmountItem> memberStat(Integer type, DateRange range, Long requestedMemberId) {
        return withRatio(statMapper.selectMemberStat(UserContext.getFamilyId(), type,
                range.getStart(), range.getEnd(), UserContext.resolveMemberId(requestedMemberId)));
    }

    /** 商家消费排行 */
    public List<AmountItem> merchantRank(DateRange range, int limit, Long requestedMemberId) {
        return withRatio(statMapper.selectMerchantRank(UserContext.getFamilyId(),
                range.getStart(), range.getEnd(), UserContext.resolveMemberId(requestedMemberId), limit));
    }

    /** 消费片区分布 */
    public List<AmountItem> areaStat(DateRange range, Long requestedMemberId) {
        return withRatio(statMapper.selectAreaStat(UserContext.getFamilyId(),
                range.getStart(), range.getEnd(), UserContext.resolveMemberId(requestedMemberId)));
    }

    /** 支付方式构成 */
    public List<AmountItem> payMethodStat(Integer type, DateRange range, Long requestedMemberId) {
        return withRatio(statMapper.selectPayMethodStat(UserContext.getFamilyId(), type,
                range.getStart(), range.getEnd(), UserContext.resolveMemberId(requestedMemberId)));
    }

    /**
     * 成员预算执行情况。预算是"月度"口径，所以这里固定按指定月份统计，
     * 不跟着看板的年度区间走，否则年支出对月预算必然显示超支，预警就没有意义了。
     *
     * 普通成员只会看到自己那一行。
     */
    public List<BudgetVO> budgetStat(YearMonth ym, Long requestedMemberId) {
        Long familyId = UserContext.getFamilyId();
        Long memberId = UserContext.resolveMemberId(requestedMemberId);
        DateRange month = DateRange.ofMonth(ym);

        List<Member> members = memberMapper.selectByFamily(familyId);
        List<BudgetVO> result = new ArrayList<>();
        for (Member member : members) {
            if (memberId != null && !memberId.equals(member.getId())) {
                continue;
            }
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
    public Map<String, Object> dashboard(DateRange range, Long requestedMemberId) {
        Long memberId = UserContext.resolveMemberId(requestedMemberId);

        Map<String, Object> map = new HashMap<>();
        map.put("range", range.toString());
        // 回传实际生效的成员范围，前端据此决定图表标题是"全家"还是某个成员
        map.put("memberId", memberId);
        map.put("memberName", memberId == null ? null : memberName(memberId));
        map.put("scopeLocked", UserContext.scopeMemberId() != null);
        map.put("overview", overview(range, requestedMemberId));
        map.put("trend", trend(range, requestedMemberId));
        map.put("expenseCategory", categoryStat(2, range, requestedMemberId));
        map.put("incomeCategory", categoryStat(1, range, requestedMemberId));
        map.put("memberExpense", memberStat(2, range, requestedMemberId));
        map.put("merchantRank", merchantRank(range, 10, requestedMemberId));
        map.put("areaStat", areaStat(range, requestedMemberId));
        map.put("payMethod", payMethodStat(2, range, requestedMemberId));
        YearMonth budgetYm = budgetMonthOf(range);
        map.put("budget", budgetStat(budgetYm, requestedMemberId));
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


    /**
     * 个人看板。始终按当前登录人自己的 memberId 统计（户主也不看全家）。
     * 日期按 Asia/Shanghai；本周为周一至周日，合计截到今天。
     */
    public Map<String, Object> personal() {
        Long memberId = UserContext.requireOwnMemberId();
        LocalDate today = AppTime.today();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        YearMonth ym = YearMonth.from(today);
        LocalDate monthStart = ym.atDay(1);

        DateRange weekFull = DateRange.of(weekStart, weekEnd, null, null);
        DateRange monthToToday = DateRange.of(monthStart, today, null, null);

        OverviewVO monthOv = overview(monthToToday, memberId);

        YearMonth lastYm = ym.minusMonths(1);
        DateRange lastMonth = DateRange.ofMonth(lastYm);
        OverviewVO lastOv = overview(lastMonth, memberId);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("memberId", memberId);
        map.put("memberName", memberName(memberId));
        map.put("today", today.toString());
        map.put("weekStart", weekStart.toString());
        map.put("weekEnd", weekEnd.toString());
        map.put("weekRule", "周一至周日（Asia/Shanghai），合计截到今天");
        map.put("month", monthToToday.toString());
        map.put("todayExpense", statMapper.sumAmount(UserContext.getFamilyId(), 2, today, today, memberId));
        map.put("todayIncome", statMapper.sumAmount(UserContext.getFamilyId(), 1, today, today, memberId));
        map.put("weekExpense", statMapper.sumAmount(UserContext.getFamilyId(), 2, weekStart, today, memberId));
        map.put("monthIncome", monthOv.getTotalIncome());
        map.put("monthExpense", monthOv.getTotalExpense());
        map.put("monthBalance", monthOv.getBalance());
        map.put("monthCount", monthOv.getRecordCount());
        map.put("overview", monthOv);
        map.put("expenseCategory", categoryStat(2, monthToToday, memberId));
        map.put("lastMonth", lastMonth.toString());
        map.put("lastMonthLabel", lastYm.toString());
        map.put("lastMonthIncome", lastOv.getTotalIncome());
        map.put("lastMonthExpense", lastOv.getTotalExpense());
        map.put("lastMonthBalance", lastOv.getBalance());
        map.put("lastMonthCount", lastOv.getRecordCount());
        if (lastOv.getMaxExpense() != null) {
            map.put("lastMonthMaxExpense", lastOv.getMaxExpense());
        }
        if (lastOv.getGiftExpense() != null) {
            map.put("lastMonthGiftExpense", lastOv.getGiftExpense());
        }
        map.put("lastExpenseCategory", categoryStat(2, lastMonth, memberId));
        BigDecimal thisExpense = monthOv.getTotalExpense() == null ? BigDecimal.ZERO : monthOv.getTotalExpense();
        BigDecimal prevExpense = lastOv.getTotalExpense() == null ? BigDecimal.ZERO : lastOv.getTotalExpense();
        BigDecimal thisBalance = monthOv.getBalance() == null ? BigDecimal.ZERO : monthOv.getBalance();
        BigDecimal prevBalance = lastOv.getBalance() == null ? BigDecimal.ZERO : lastOv.getBalance();
        map.put("monthExpenseChange", thisExpense.subtract(prevExpense));
        map.put("monthBalanceChange", thisBalance.subtract(prevBalance));
        map.put("weekDaily", fillDaily(weekFull, memberId));
        map.put("monthDaily", fillDaily(monthToToday, memberId));
        map.put("budget", budgetStat(ym, memberId));
        map.put("budgetMonth", ym.toString());

        RecordQuery q = new RecordQuery();
        q.setMemberId(memberId);
        q.setPageNum(1);
        q.setPageSize(5);
        q.setOffset(0);
        map.put("recent", recordMapper.selectByQuery(UserContext.getFamilyId(), q));
        return map;
    }

    /** 按日收支，缺日补 0。本周图会补到周日（未到的日子为 0）。 */
    List<MonthAmount> fillDaily(DateRange range, Long requestedMemberId) {
        Long familyId = UserContext.getFamilyId();
        Long memberId = UserContext.resolveMemberId(requestedMemberId);
        List<MonthAmount> rows = statMapper.selectDailyTrend(
                familyId, range.getStart(), range.getEnd(), memberId);
        Map<String, MonthAmount> byDay = new HashMap<>();
        for (MonthAmount m : rows) {
            m.setBalance(m.getIncome().subtract(m.getExpense()));
            byDay.put(m.getYm(), m);
        }
        List<MonthAmount> full = new ArrayList<>();
        LocalDate cursor = range.getStart();
        LocalDate last = range.getEnd();
        if (last.isBefore(cursor)) {
            last = cursor;
        }
        while (!cursor.isAfter(last)) {
            String key = cursor.toString();
            MonthAmount m = byDay.get(key);
            if (m == null) {
                m = new MonthAmount();
                m.setYm(key);
                m.setIncome(BigDecimal.ZERO);
                m.setExpense(BigDecimal.ZERO);
                m.setBalance(BigDecimal.ZERO);
            }
            full.add(m);
            cursor = cursor.plusDays(1);
        }
        return full;
    }

    private String memberName(Long memberId) {
        Member m = memberMapper.selectById(memberId, UserContext.getFamilyId());
        return m == null ? null : m.getMemberName();
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
