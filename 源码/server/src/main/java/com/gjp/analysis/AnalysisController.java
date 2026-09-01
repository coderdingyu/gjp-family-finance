package com.gjp.analysis;

import com.gjp.common.Result;
import com.gjp.stat.DateRange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 智能分析接口。区间参数与统计接口保持一致。
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Autowired
    private AnalysisService analysisService;

    /** 分析报告：返回按严重程度排序的结论列表 */
    @GetMapping("/report")
    public Result<List<AnalysisItem>> report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                             @RequestParam(required = false) Integer year,
                                             @RequestParam(required = false) Integer month) {
        return Result.ok(analysisService.report(DateRange.of(startDate, endDate, year, month)));
    }
}
