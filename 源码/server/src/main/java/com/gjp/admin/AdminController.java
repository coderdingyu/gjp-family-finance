package com.gjp.admin;

import com.gjp.common.Result;
import com.gjp.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统管理员接口（需求第 8 条）。
 * 每个方法内部都会调 UserContext.requireAdmin()，非管理员一律 403。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    /** 运行概览：进程、内存、数据规模、日志健康度 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(adminService.overview());
    }

    /** 各家庭规模概览 */
    @GetMapping("/families")
    public Result<List<Map<String, Object>>> families() {
        return Result.ok(adminService.families());
    }

    /** 全部账号 */
    @GetMapping("/users")
    public Result<List<User>> users(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) Integer role) {
        return Result.ok(adminService.users(keyword, role));
    }

    /** 重置指定账号密码，body: {password} */
    @PutMapping("/users/{userId}/password")
    public Result<Void> resetPassword(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        adminService.resetPassword(userId, body.get("password"));
        return Result.ok();
    }

    /** 启用/禁用指定账号，body: {status} */
    @PutMapping("/users/{userId}/status")
    public Result<Void> toggleStatus(@PathVariable Long userId, @RequestBody Map<String, Integer> body) {
        adminService.toggleStatus(userId, body.get("status"));
        return Result.ok();
    }

    /** 管理员改自己的密码，body: {oldPassword, newPassword} */
    @PutMapping("/password")
    public Result<Void> changeOwnPassword(@RequestBody Map<String, String> body) {
        adminService.changeOwnPassword(body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }
}
