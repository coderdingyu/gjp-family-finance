package com.gjp.stat;

import com.gjp.common.BizException;
import com.gjp.common.Result;
import com.gjp.stat.vo.AmountItem;
import com.gjp.stat.vo.BudgetVO;
import com.gjp.stat.vo.MonthAmount;
import com.gjp.stat.vo.OverviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * 统计接口。参数统一用 {@link StatQuery} 绑定：
 *   · 区间支持三种写法（优先级从高到低）：startDate+endDate > year+month > 只传 year，都不传默认当年
 *   · memberId 指定查看某个成员；普通成员传了也会被后端强制成自己
 */
@RestController
@RequestMapping("/api/stat")
public class StatController {

    @Autowired
    private StatService statService;

    @GetMapping("/overview")
    public Result<OverviewVO> overview(@ModelAttribute StatQuery q) {
        return Result.ok(statService.overview(q.range(), q.getMemberId()));
    }

    @GetMapping("/trend")
    public Result<List<MonthAmount>> trend(@ModelAttribute StatQuery q) {
        return Result.ok(statService.trend(q.range(), q.getMemberId()));
    }

    /** 一级分类占比 */
    @GetMapping("/category")
    public Result<List<AmountItem>> category(@ModelAttribute StatQuery q) {
        return Result.ok(statService.categoryStat(q.typeOrExpense(), q.range(), q.getMemberId()));
    }

    /** 子分类钻取，支持一级→二级→三级逐层下钻 */
    @GetMapping("/sub-category")
    public Result<List<AmountItem>> subCategory(@ModelAttribute StatQuery q) {
        if (q.getParentId() == null) {
            throw new BizException("请指定要钻取的分类");
        }
        return Result.ok(statService.subCategoryStat(q.getParentId(), q.range(), q.getMemberId()));
    }

    @GetMapping("/member")
    public Result<List<AmountItem>> member(@ModelAttribute StatQuery q) {
        return Result.ok(statService.memberStat(q.typeOrExpense(), q.range(), q.getMemberId()));
    }

    @GetMapping("/merchant")
    public Result<List<AmountItem>> merchant(@ModelAttribute StatQuery q) {
        return Result.ok(statService.merchantRank(q.range(), q.limitOrDefault(10), q.getMemberId()));
    }

    @GetMapping("/area")
    public Result<List<AmountItem>> area(@ModelAttribute StatQuery q) {
        return Result.ok(statService.areaStat(q.range(), q.getMemberId()));
    }

    @GetMapping("/pay-method")
    public Result<List<AmountItem>> payMethod(@ModelAttribute StatQuery q) {
        return Result.ok(statService.payMethodStat(q.typeOrExpense(), q.range(), q.getMemberId()));
    }

    /** 成员预算执行，ym 格式 yyyy-MM，不传默认当月 */
    @GetMapping("/budget")
    public Result<List<BudgetVO>> budget(@ModelAttribute StatQuery q) {
        YearMonth target;
        try {
            target = (q.getYm() == null || q.getYm().isEmpty()) ? YearMonth.now() : YearMonth.parse(q.getYm());
        } catch (Exception e) {
            throw new BizException("月份格式应为 yyyy-MM，如 2026-08");
        }
        return Result.ok(statService.budgetStat(target, q.getMemberId()));
    }

    /** 看板聚合接口，一次返回首页所有图表数据 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(@ModelAttribute StatQuery q) {
        return Result.ok(statService.dashboard(q.range(), q.getMemberId()));
    }
}
