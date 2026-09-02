package com.gjp.log;

import com.gjp.common.PageResult;
import com.gjp.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 操作日志接口。任何登录用户都能访问，但能看到的范围由角色决定。
 */
@RestController
@RequestMapping("/api/log")
public class LogController {

    @Autowired
    private OperationLogService logService;

    @PostMapping("/page")
    public Result<PageResult<?>> page(@RequestBody LogQuery query) {
        return Result.ok(logService.page(query));
    }

    @GetMapping("/module-stat")
    public Result<List<Map<String, Object>>> moduleStat() {
        return Result.ok(logService.moduleStat());
    }

    @GetMapping("/options")
    public Result<Map<String, Object>> options() {
        return Result.ok(logService.options());
    }
}
