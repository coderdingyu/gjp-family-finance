package com.gjp.analysis;

import com.gjp.common.UserContext;
import com.gjp.mapper.StatMapper;
import com.gjp.stat.DateRange;
import com.gjp.stat.StatService;
import com.gjp.stat.vo.AmountItem;
import com.gjp.stat.vo.BudgetVO;
import com.gjp.stat.vo.MonthAmount;
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
 * 智能分析业务。
 *
 * 这是本系统与【只会算总数的记账工具】的区别所在：统计负责给出客观数字，
 * 本类负责在数字之上做判断 —— 哪个月异常、异常由哪个分类造成、是偶发还是会持续、
 * 钱主要流向了哪些商家和片区、人情往来占了多少。
 *
 * 每条规则都遵循同一套写法：先取数 → 再比阈值 → 命中则输出"结论 + 数据依据 + 建议"。
 * 阈值集中定义在下面的常量里，评审时可以直接讨论阈值是否合理。
 */
@Service
public class AnalysisService {

    /** 某月支出超过月均这个倍数即判定为异常月份 */
    private static final BigDecimal ABNORMAL_MONTH_RATIO = new BigDecimal("1.30");
    /** 单一商家消费占比超过该百分比即提示消费集中 */
    private static final BigDecimal MERCHANT_CONCENTRATION = new BigDecimal("20");
    /** 单一片区消费占比超过该百分比即提示区域集中 */
    private static final BigDecimal AREA_CONCENTRATION = new BigDecimal("40");
    /** 人情往来支出占总支出超过该百分比即提示偏高 */
    private static final BigDecimal GIFT_RATIO_WARN = new BigDecimal("10");
    /** 结余率低于该百分比即提示储蓄能力不足 */
    private static final BigDecimal BALANCE_RATE_WARN = new BigDecimal("10");
    /** 环比涨幅超过该百分比即提示支出抬头 */
    private static final BigDecimal MOM_WARN = new BigDecimal("20");

    @Autowired
    private StatMapper statMapper;
    @Autowired
    private StatService statService;

    /**
     * 生成分析报告。返回列表按严重程度排序：danger 在前，good 在后，
     * 前端直接按顺序渲染即可。
     */
    public List<AnalysisItem> report(DateRange range) {
        List<AnalysisItem> items = new ArrayList<>();

        List<MonthAmount> trend = statService.trend(range);
        BigDecimal totalIncome = statMapper.sumAmount(UserContext.getFamilyId(), 1, range.getStart(), range.getEnd());
        BigDecimal totalExpense = statMapper.sumAmount(UserContext.getFamilyId(), 2, range.getStart(), range.getEnd());

        if (totalIncome.compareTo(BigDecimal.ZERO) == 0 && totalExpense.compareTo(BigDecimal.ZERO) == 0) {
            items.add(new AnalysisItem("A0", "info", "所选区间内没有收支记录",
                    "区间 " + range + " 的收入与支出均为 0。",
                    "请先在【收支录入】中登记流水，或把统计区间调整到有数据的年份。"));
            return items;
        }

        analyzeAbnormalMonth(items, trend, range);
        analyzeMonthOnMonth(items, trend);
        analyzeBalanceRate(items, totalIncome, totalExpense);
        analyzeBudget(items, range);
        analyzeCategoryConcentration(items, range, totalExpense);
        analyzeMerchant(items, range);
        analyzeArea(items, range);
        analyzeGift(items, range, totalExpense);

        items.sort((a, b) -> weight(a.getLevel()) - weight(b.getLevel()));
        return items;
    }

    private int weight(String level) {
        switch (level == null ? "" : level) {
            case "danger":
                return 0;
            case "warning":
                return 1;
            case "info":
                return 2;
            default:
                return 3;
        }
    }

    /**
     * A1 异常月份归因。
     * 先找出支出最高的月份，与【其余月份的平均支出】比较（不含本月，避免自己把均值拉高）；
     * 命中阈值后再钻取到分类层，找出这个月比平常多花的钱主要花在哪一类上，
     * 并根据该分类在其他月份是否也高频出现，判断这笔支出是偶发还是会持续。
     */
    private void analyzeAbnormalMonth(List<AnalysisItem> items, List<MonthAmount> trend, DateRange range) {
        List<MonthAmount> withData = new ArrayList<>();
        for (MonthAmount m : trend) {
            if (m.getExpense().compareTo(BigDecimal.ZERO) > 0) {
                withData.add(m);
            }
        }
        if (withData.size() < 2) {
            return;
        }

        MonthAmount peak = withData.get(0);
        for (MonthAmount m : withData) {
            if (m.getExpense().compareTo(peak.getExpense()) > 0) {
                peak = m;
            }
        }

        BigDecimal othersSum = BigDecimal.ZERO;
        for (MonthAmount m : withData) {
            if (!m.getYm().equals(peak.getYm())) {
                othersSum = othersSum.add(m.getExpense());
            }
        }
        BigDecimal othersAvg = divide(othersSum, BigDecimal.valueOf(withData.size() - 1));
        if (othersAvg.compareTo(BigDecimal.ZERO) == 0
                || peak.getExpense().compareTo(othersAvg.multiply(ABNORMAL_MONTH_RATIO)) < 0) {
            return;
        }

        BigDecimal overRate = divide(peak.getExpense().subtract(othersAvg).multiply(BigDecimal.valueOf(100)), othersAvg);

        // 钻取：该月各一级分类支出 与 其他月份该分类的平均支出 对比，找出超支贡献最大的分类
        YearMonth peakMonth = YearMonth.parse(peak.getYm());
        Map<String, BigDecimal> peakByCat = toMap(statMapper.selectExpenseByCategory(
                UserContext.getFamilyId(), DateRange.ofMonth(peakMonth).getStart(),
                DateRange.ofMonth(peakMonth).getEnd()));
        Map<String, BigDecimal> allByCat = toMap(statMapper.selectExpenseByCategory(
                UserContext.getFamilyId(), range.getStart(), range.getEnd()));

        String topCat = null;
        BigDecimal topGap = BigDecimal.ZERO;
        BigDecimal topPeakAmount = BigDecimal.ZERO;
        BigDecimal topOtherAvg = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : peakByCat.entrySet()) {
            BigDecimal all = allByCat.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal otherAvgOfCat = divide(all.subtract(e.getValue()), BigDecimal.valueOf(withData.size() - 1));
            BigDecimal gap = e.getValue().subtract(otherAvgOfCat);
            if (gap.compareTo(topGap) > 0) {
                topGap = gap;
                topCat = e.getKey();
                topPeakAmount = e.getValue();
                topOtherAvg = otherAvgOfCat;
            }
        }

        StringBuilder basis = new StringBuilder();
        basis.append(peak.getYm()).append(" 支出 ").append(peak.getExpense())
                .append(" 元，其余 ").append(withData.size() - 1).append(" 个有数据月份的平均支出为 ")
                .append(othersAvg).append(" 元，高出 ").append(overRate).append("%。");

        String suggestion;
        if (topCat != null) {
            basis.append("按一级分类钻取，超出最多的是【").append(topCat).append("】：当月 ")
                    .append(topPeakAmount).append(" 元，其他月份平均 ").append(topOtherAvg)
                    .append(" 元，多支出 ").append(topGap).append(" 元，占本月超支额的 ")
                    .append(divide(topGap.multiply(BigDecimal.valueOf(100)),
                            peak.getExpense().subtract(othersAvg))).append("%。");
            // 判断偶发还是持续：该分类在其他月份的均值本身很低，说明是这个月单独发生的
            boolean occasional = topOtherAvg.compareTo(topPeakAmount.multiply(new BigDecimal("0.3"))) < 0;
            basis.append(occasional
                    ? "该分类在其他月份的平均支出很低，判断为偶发性支出。"
                    : "该分类在其他月份也有稳定支出，判断为持续性支出，后续仍会发生。");
            suggestion = occasional
                    ? "属于一次性大额支出，无需调整长期预算，但建议在备注中写明事由，便于年度复盘时区分。"
                    : "属于经常性支出且本月明显抬头，建议为【" + topCat + "】设置月度上限，并核对是否有可优化的固定支出。";
        } else {
            suggestion = "建议逐笔核对该月流水，确认是否存在重复录入或金额录错。";
        }

        items.add(new AnalysisItem("A1", "danger",
                peak.getYm() + " 支出异常偏高，超出其他月份平均水平 " + overRate + "%",
                basis.toString(), suggestion));
    }

    /**
     * A2 环比分析。取区间内最后两个有数据的月份做对比，回答"最近是不是花多了"。
     */
    private void analyzeMonthOnMonth(List<AnalysisItem> items, List<MonthAmount> trend) {
        List<MonthAmount> withData = new ArrayList<>();
        for (MonthAmount m : trend) {
            if (m.getExpense().compareTo(BigDecimal.ZERO) > 0) {
                withData.add(m);
            }
        }
        if (withData.size() < 2) {
            return;
        }
        MonthAmount curr = withData.get(withData.size() - 1);
        MonthAmount prev = withData.get(withData.size() - 2);
        BigDecimal diff = curr.getExpense().subtract(prev.getExpense());
        BigDecimal rate = divide(diff.multiply(BigDecimal.valueOf(100)), prev.getExpense());

        if (rate.compareTo(MOM_WARN) >= 0) {
            items.add(new AnalysisItem("A2", "warning",
                    curr.getYm() + " 支出环比上升 " + rate + "%",
                    curr.getYm() + " 支出 " + curr.getExpense() + " 元，" + prev.getYm() + " 为 "
                            + prev.getExpense() + " 元，增加 " + diff + " 元。",
                    "建议对比两个月的分类明细，确认涨幅来自哪一类支出，必要时收紧下月预算。"));
        } else if (rate.compareTo(MOM_WARN.negate()) <= 0) {
            items.add(new AnalysisItem("A2", "good",
                    curr.getYm() + " 支出环比下降 " + rate.abs() + "%",
                    curr.getYm() + " 支出 " + curr.getExpense() + " 元，" + prev.getYm() + " 为 "
                            + prev.getExpense() + " 元，减少 " + diff.abs() + " 元。",
                    "支出控制有效果，可以保持当前的消费节奏。"));
        }
    }

    /** A3 结余率（家庭收益健康度）。 */
    private void analyzeBalanceRate(List<AnalysisItem> items, BigDecimal income, BigDecimal expense) {
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            items.add(new AnalysisItem("A3", "warning", "区间内没有登记任何收入",
                    "收入合计为 0，支出合计 " + expense + " 元，无法计算结余率。",
                    "建议把工资、奖金、投资收益等收入也录入系统，否则家庭收益情况无法评估。"));
            return;
        }
        BigDecimal balance = income.subtract(expense);
        BigDecimal rate = divide(balance.multiply(BigDecimal.valueOf(100)), income);

        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            items.add(new AnalysisItem("A3", "danger", "区间内入不敷出，家庭收益为负",
                    "收入 " + income + " 元，支出 " + expense + " 元，结余 " + balance + " 元，结余率 " + rate + "%。",
                    "建议优先压缩占比最高的支出分类，并检查是否有大额一次性支出可以分期或延后。"));
        } else if (rate.compareTo(BALANCE_RATE_WARN) < 0) {
            items.add(new AnalysisItem("A3", "warning", "结余率偏低，仅 " + rate + "%",
                    "收入 " + income + " 元，支出 " + expense + " 元，结余 " + balance + " 元。",
                    "结余率低于 10% 意味着几乎没有储蓄空间，建议先设定每月固定储蓄额，再安排消费。"));
        } else {
            items.add(new AnalysisItem("A3", "good", "结余情况良好，结余率 " + rate + "%",
                    "收入 " + income + " 元，支出 " + expense + " 元，结余 " + balance + " 元。",
                    "可以考虑把结余的一部分配置到【资产管理】中，记录存款或投资的变化。"));
        }
    }

    /** A4 成员预算执行，逐个成员判断是否超支。 */
    private void analyzeBudget(List<AnalysisItem> items, DateRange range) {
        YearMonth target = YearMonth.from(range.getEnd());
        for (BudgetVO b : statService.budgetStat(target)) {
            if ("已超支".equals(b.getStatus())) {
                items.add(new AnalysisItem("A4", "danger",
                        b.getMemberName() + " 在 " + target + " 已超出月度预算",
                        "预算 " + b.getBudget() + " 元，实际支出 " + b.getExpense() + " 元，使用率 "
                                + b.getUsedRate() + "%，超出 " + b.getExpense().subtract(b.getBudget()) + " 元。",
                        "建议与该成员核对超支明细；若预算本身设置偏低，可在【成员管理】中调整月度预算。"));
            } else if ("接近上限".equals(b.getStatus())) {
                items.add(new AnalysisItem("A4", "warning",
                        b.getMemberName() + " 在 " + target + " 的预算即将用完",
                        "预算 " + b.getBudget() + " 元，实际支出 " + b.getExpense() + " 元，使用率 "
                                + b.getUsedRate() + "%。",
                        "本月剩余额度不多，建议暂缓非必要消费。"));
            }
        }
    }

    /** A5 支出结构集中度：占比最高的一级分类。 */
    private void analyzeCategoryConcentration(List<AnalysisItem> items, DateRange range, BigDecimal totalExpense) {
        if (totalExpense.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        List<AmountItem> cats = statService.categoryStat(2, range);
        if (cats.isEmpty()) {
            return;
        }
        AmountItem top = cats.get(0);
        String level = top.getRatio().compareTo(new BigDecimal("50")) >= 0 ? "warning" : "info";
        StringBuilder basis = new StringBuilder();
        basis.append("支出占比前").append(Math.min(3, cats.size())).append("的分类为：");
        for (int i = 0; i < Math.min(3, cats.size()); i++) {
            AmountItem c = cats.get(i);
            basis.append(i > 0 ? "、" : "").append(c.getName()).append(" ").append(c.getAmount())
                    .append(" 元（").append(c.getRatio()).append("%，").append(c.getCount()).append(" 笔）");
        }
        basis.append("。");
        items.add(new AnalysisItem("A5", level,
                "支出主要集中在【" + top.getName() + "】，占总支出 " + top.getRatio() + "%",
                basis.toString(),
                "50% 以上说明支出结构单一，压缩这一类的效果最直接；可点击饼图钻取到二级分类，定位到具体的花销项。"));
    }

    /** A6 商家消费集中度，回答"钱主要花在哪些商家"。 */
    private void analyzeMerchant(List<AnalysisItem> items, DateRange range) {
        List<AmountItem> ranks = statService.merchantRank(range, 5);
        if (ranks.isEmpty()) {
            items.add(new AnalysisItem("A6", "info", "流水中很少填写商家信息，无法分析消费流向",
                    "区间内没有可用于统计的商家数据。",
                    "录入时补填商家名称，系统才能算出常去的商家和可议价空间。"));
            return;
        }
        AmountItem top = ranks.get(0);
        StringBuilder basis = new StringBuilder("消费金额前 ").append(ranks.size()).append(" 的商家：");
        for (int i = 0; i < ranks.size(); i++) {
            AmountItem r = ranks.get(i);
            basis.append(i > 0 ? "、" : "").append(r.getName()).append(" ").append(r.getAmount())
                    .append(" 元/").append(r.getCount()).append(" 笔");
        }
        basis.append("。其中 ").append(top.getName()).append(" 占已填写商家消费的 ").append(top.getRatio()).append("%。");

        boolean concentrated = top.getRatio().compareTo(MERCHANT_CONCENTRATION) >= 0;
        items.add(new AnalysisItem("A6", concentrated ? "warning" : "info",
                concentrated
                        ? "消费集中在【" + top.getName() + "】，占比 " + top.getRatio() + "%"
                        : "商家消费较为分散，最高的是【" + top.getName() + "】（" + top.getRatio() + "%）",
                basis.toString(),
                concentrated
                        ? "高频高额商家值得关注：可以办会员卡或找替代选择，单点优化就能带来可见的节省。"
                        : "消费分散说明没有明显的资金黑洞，保持记录习惯即可。"));
    }

    /** A7 消费片区分布，回答"消费主要集中在哪个片区"。 */
    private void analyzeArea(List<AnalysisItem> items, DateRange range) {
        List<AmountItem> areas = statService.areaStat(range);
        if (areas.isEmpty()) {
            return;
        }
        AmountItem top = areas.get(0);
        StringBuilder basis = new StringBuilder("各片区消费：");
        for (int i = 0; i < Math.min(4, areas.size()); i++) {
            AmountItem a = areas.get(i);
            basis.append(i > 0 ? "、" : "").append(a.getName()).append(" ").append(a.getAmount())
                    .append(" 元（").append(a.getRatio()).append("%）");
        }
        basis.append("。");
        boolean concentrated = top.getRatio().compareTo(AREA_CONCENTRATION) >= 0;
        items.add(new AnalysisItem("A7", "info",
                concentrated
                        ? "消费明显集中在【" + top.getName() + "】片区，占比 " + top.getRatio() + "%"
                        : "消费分布在 " + areas.size() + " 个片区，最高的是【" + top.getName() + "】",
                basis.toString(),
                "片区分布可以反映生活半径。若某片区占比过高且并非居住地附近，建议核对是否有通勤或应酬造成的额外支出。"));
    }

    /** A8 人情往来专项分析，对应课程要求"朋友间礼尚往来的消费有多少"。 */
    private void analyzeGift(List<AnalysisItem> items, DateRange range, BigDecimal totalExpense) {
        BigDecimal gift = statMapper.sumGiftExpense(UserContext.getFamilyId(), range.getStart(), range.getEnd());
        if (gift.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal ratio = divide(gift.multiply(BigDecimal.valueOf(100)), totalExpense);
        boolean high = ratio.compareTo(GIFT_RATIO_WARN) >= 0;
        items.add(new AnalysisItem("A8", high ? "warning" : "info",
                "人情往来支出 " + gift + " 元，占总支出 " + ratio + "%",
                "区间 " + range + " 内标记为人情往来的支出合计 " + gift + " 元，总支出 " + totalExpense + " 元。",
                high
                        ? "人情往来占比偏高，建议按年度设定礼金上限，并记录往来对象，避免重复或超额支出。"
                        : "人情往来支出在合理范围内，继续按笔标记即可，年末可以据此做一次完整复盘。"));
    }

    private Map<String, BigDecimal> toMap(List<AmountItem> list) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (AmountItem item : list) {
            map.put(item.getName(), item.getAmount());
        }
        return map;
    }

    private BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b == null || b.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return a.divide(b, 2, RoundingMode.HALF_UP);
    }
}
