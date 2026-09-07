package com.gjp.analysis;

import com.gjp.common.Result;
import com.gjp.stat.StatQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.LinkedHashMap;
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

    /** 先返回本地快照；AI 增强由独立请求显式触发。 */
    @GetMapping("/report")
    public Result<AnalysisReport> report(@ModelAttribute StatQuery q, @RequestParam(defaultValue = "false") boolean refresh) {
        return Result.ok(analysisService.report(q.range(), q.getMemberId(), refresh));
    }

    public record EnhanceRequest(@NotBlank String reportId) {}
    @PostMapping("/enhance")
    public Result<AnalysisReport> enhance(@Valid @RequestBody EnhanceRequest body) {
        return Result.ok(analysisService.enhance(body.reportId()));
    }
}
