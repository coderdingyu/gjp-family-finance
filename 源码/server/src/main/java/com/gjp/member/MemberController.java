package com.gjp.member;

import com.gjp.common.Result;
import com.gjp.entity.Member;
import com.gjp.entity.User;
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
 * 家庭成员管理接口。增删改仅户主可用，普通成员只能查到自己。
 */
@RestController
@RequestMapping("/api/member")
public class MemberController {

    @Autowired
    private MemberService memberService;

    @GetMapping("/list")
    public Result<List<Member>> list() {
        return Result.ok(memberService.list());
    }

    @GetMapping("/{id}")
    public Result<Member> detail(@PathVariable Long id) {
        return Result.ok(memberService.detail(id));
    }

    @PostMapping
    public Result<Member> add(@RequestBody Member member) {
        return Result.ok(memberService.add(member));
    }

    @PutMapping("/{id}")
    public Result<Member> update(@PathVariable Long id, @RequestBody Member member) {
        return Result.ok(memberService.update(id, member));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return Result.ok();
    }

    // ---------------- 成员登录账号 ----------------

    /** 本家庭的账号列表 */
    @GetMapping("/accounts")
    public Result<List<User>> accounts() {
        return Result.ok(memberService.accounts());
    }

    /** 为成员开通登录账号，body: {username, password} */
    @PostMapping("/{id}/account")
    public Result<User> createAccount(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(memberService.createAccount(id, body.get("username"), body.get("password")));
    }

    /** 重置成员账号密码，body: {password} */
    @PutMapping("/account/{userId}/password")
    public Result<Void> resetPassword(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        memberService.resetPassword(userId, body.get("password"));
        return Result.ok();
    }

    /** 启用/禁用成员账号，body: {status} */
    @PutMapping("/account/{userId}/status")
    public Result<Void> toggleStatus(@PathVariable Long userId, @RequestBody Map<String, Integer> body) {
        memberService.toggleStatus(userId, body.get("status"));
        return Result.ok();
    }
}
