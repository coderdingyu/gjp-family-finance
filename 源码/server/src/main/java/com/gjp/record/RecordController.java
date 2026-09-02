package com.gjp.record;

import com.gjp.common.Result;
import com.gjp.entity.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 收支流水接口。查询条件较多，统一用 POST + JSON 传参，避免 URL 拼一长串参数。
 */
@RestController
@RequestMapping("/api/record")
public class RecordController {

    @Autowired
    private RecordService recordService;

    /** 多条件分页查询，返回 {page:{total,list}, sumIncome, sumExpense} */
    @PostMapping("/page")
    public Result<Map<String, Object>> page(@RequestBody RecordQuery query) {
        return Result.ok(recordService.page(query));
    }

    @GetMapping("/{id}")
    public Result<Record> detail(@PathVariable Long id) {
        return Result.ok(recordService.detail(id));
    }

    @PostMapping
    public Result<Record> add(@RequestBody Record record) {
        return Result.ok(recordService.add(record));
    }

    @PutMapping("/{id}")
    public Result<Record> update(@PathVariable Long id, @RequestBody Record record) {
        return Result.ok(recordService.update(id, record));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        recordService.delete(id);
        return Result.ok();
    }

    /** 批量删除，查重页勾选多条后一次删掉；返回实际删除条数 */
    @DeleteMapping("/batch")
    public Result<Integer> deleteBatch(@RequestBody List<Long> ids) {
        return Result.ok(recordService.deleteBatch(ids));
    }

    /** 录入页需要的候选项：历史商家、历史片区、支付方式 */
    @GetMapping("/options")
    public Result<Map<String, Object>> options() {
        return Result.ok(recordService.options());
    }
}
