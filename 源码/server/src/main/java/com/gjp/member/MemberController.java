package com.gjp.member;

import com.gjp.common.Result;
import com.gjp.entity.Member;
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

/**
 * 家庭成员管理接口。
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
}
