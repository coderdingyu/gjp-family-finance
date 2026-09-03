package com.gjp.analysis;

import com.gjp.common.Result;
import com.gjp.stat.StatQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能分析接口。区间与成员参数与统计接口保持一致。
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Autowired
    private AnalysisService analysisService;

    /** 是否已接智能体；不回传 API Key */
    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("configured", analysisService.agentConfigured());
        map.put("mode", "workflow");
        return Result.ok(map);
    }

    /** 分析报告：返回按严重程度排序的结论列表 */
    @GetMapping("/report")
    public Result<List<AnalysisItem>> report(@ModelAttribute StatQuery q) {
        return Result.ok(analysisService.report(q.range(), q.getMemberId()));
    }
}
