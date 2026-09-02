package com.gjp.dedup;

import com.gjp.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 账单查重接口（需求第 6 条）。
 */
@RestController
@RequestMapping("/api/dedup")
public class DedupController {

    @Autowired
    private DedupService dedupService;

    /**
     * 扫描疑似重复的流水。
     *
     * @param dayTolerance 日期容差（天），默认 3，0 表示只找同一天
     * @param sameMember   是否要求同一成员，默认 true
     * @param sameCategory 是否要求同一分类，默认 false
     */
    @GetMapping("/scan")
    public Result<Map<String, Object>> scan(@RequestParam(required = false) Integer dayTolerance,
                                            @RequestParam(required = false) Boolean sameMember,
                                            @RequestParam(required = false) Boolean sameCategory,
                                            @RequestParam(required = false) String startDate,
                                            @RequestParam(required = false) String endDate,
                                            @RequestParam(required = false) Long memberId) {
        return Result.ok(dedupService.scan(dayTolerance, sameMember, sameCategory,
                startDate, endDate, memberId));
    }

    /** 用户勾选后删除。删除权在用户手里，系统不做自动清理 */
    @DeleteMapping("/records")
    public Result<Integer> delete(@RequestBody List<Long> ids) {
        return Result.ok(dedupService.deleteSelected(ids));
    }
}
