package com.gjp.stat;

import com.gjp.common.Result;
import com.gjp.stat.vo.AmountItem;
import com.gjp.stat.vo.BudgetVO;
import com.gjp.stat.vo.MonthAmount;
import com.gjp.stat.vo.OverviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * 统计接口。
 * 区间参数统一支持三种写法：startDate+endDate（优先）、year+month、只传 year；都不传默认当年。
 */
@RestController
@RequestMapping("/api/stat")
public class StatController {

    @Autowired
    private StatService statService;

    @GetMapping("/overview")
    public Result<OverviewVO> overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                       @RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month) {
        return Result.ok(statService.overview(DateRange.of(startDate, endDate, year, month)));
    }

    @GetMapping("/trend")
    public Result<List<MonthAmount>> trend(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                           @RequestParam(required = false) Integer year,
                                           @RequestParam(required = false) Integer month) {
        return Result.ok(statService.trend(DateRange.of(startDate, endDate, year, month)));
    }

    /** 一级分类占比，type：1=收入 2=支出 */
    @GetMapping("/category")
    public Result<List<AmountItem>> category(@RequestParam(defaultValue = "2") Integer type,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                             @RequestParam(required = false) Integer year,
                                             @RequestParam(required = false) Integer month) {
        return Result.ok(statService.categoryStat(type, DateRange.of(startDate, endDate, year, month)));
    }

    /** 二级分类钻取 */
    @GetMapping("/sub-category")
    public Result<List<AmountItem>> subCategory(@RequestParam Long parentId,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                @RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month) {
        return Result.ok(statService.subCategoryStat(parentId, DateRange.of(startDate, endDate, year, month)));
    }

    @GetMapping("/member")
    public Result<List<AmountItem>> member(@RequestParam(defaultValue = "2") Integer type,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                           @RequestParam(required = false) Integer year,
                                           @RequestParam(required = false) Integer month) {
        return Result.ok(statService.memberStat(type, DateRange.of(startDate, endDate, year, month)));
    }

    @GetMapping("/merchant")
    public Result<List<AmountItem>> merchant(@RequestParam(defaultValue = "10") Integer limit,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                             @RequestParam(required = false) Integer year,
                                             @RequestParam(required = false) Integer month) {
        return Result.ok(statService.merchantRank(DateRange.of(startDate, endDate, year, month), limit));
    }

    @GetMapping("/area")
    public Result<List<AmountItem>> area(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                         @RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) Integer month) {
        return Result.ok(statService.areaStat(DateRange.of(startDate, endDate, year, month)));
    }

    @GetMapping("/pay-method")
    public Result<List<AmountItem>> payMethod(@RequestParam(defaultValue = "2") Integer type,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                              @RequestParam(required = false) Integer year,
                                              @RequestParam(required = false) Integer month) {
        return Result.ok(statService.payMethodStat(type, DateRange.of(startDate, endDate, year, month)));
    }

    /** 成员预算执行，ym 格式 yyyy-MM，不传默认当月 */
    @GetMapping("/budget")
    public Result<List<BudgetVO>> budget(@RequestParam(required = false) String ym) {
        YearMonth target = (ym == null || ym.isEmpty()) ? YearMonth.now() : YearMonth.parse(ym);
        return Result.ok(statService.budgetStat(target));
    }

    /** 看板聚合接口，一次返回首页所有图表数据 */
    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                 @RequestParam(required = false) Integer year,
                                                 @RequestParam(required = false) Integer month) {
        return Result.ok(statService.dashboard(DateRange.of(startDate, endDate, year, month)));
    }
}
