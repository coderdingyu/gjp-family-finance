package com.gjp.category;

import com.gjp.common.Result;
import com.gjp.entity.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收支分类管理接口。
 */
@RestController
@RequestMapping("/api/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /** 平铺列表，type 可选：1=收入 2=支出，不传表示全部 */
    @GetMapping("/list")
    public Result<List<Category>> list(@RequestParam(required = false) Integer type) {
        return Result.ok(categoryService.list(type));
    }

    /** 树形列表，给录入页的级联选择器用 */
    @GetMapping("/tree")
    public Result<List<Category>> tree(@RequestParam(required = false) Integer type) {
        return Result.ok(categoryService.tree(type));
    }

    @PostMapping
    public Result<Category> add(@RequestBody Category category) {
        return Result.ok(categoryService.add(category));
    }

    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @RequestBody Category category) {
        return Result.ok(categoryService.update(id, category));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
