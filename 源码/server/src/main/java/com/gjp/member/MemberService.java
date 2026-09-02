package com.gjp.member;

import com.gjp.common.BizException;
import com.gjp.common.Md5Util;
import com.gjp.common.Role;
import com.gjp.common.UserContext;
import com.gjp.entity.Member;
import com.gjp.entity.User;
import com.gjp.log.OperationLogService;
import com.gjp.mapper.MemberMapper;
import com.gjp.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 家庭成员业务。成员是收支数据的最小归属单位，所以这里的校验偏严：
 * 同一家庭不允许重名，名下还有流水的成员不允许删除。
 *
 * 权限（需求第 9 条）：
 *   · 成员的增删改仅户主可用 —— 成员是全家共用的口径，谁都能改会让统计口径混乱
 *   · 普通成员调 list() 只能看到自己（记账时下拉框里也就只有自己）
 *   · 成员登录账号由户主开通，默认角色是普通成员
 */
@Service
public class MemberService {

    /** 允许的家庭关系，前端下拉框与这里保持一致 */
    private static final List<String> RELATIONS = List.of("本人", "配偶", "子女", "父母", "其他");

    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OperationLogService logService;

    /** 成员列表。普通成员只返回自己 */
    public List<Member> list() {
        Long familyId = UserContext.getFamilyId();
        List<Member> all = memberMapper.selectByFamily(familyId);
        Long scope = UserContext.scopeMemberId();
        if (scope == null) {
            return all;
        }
        List<Member> mine = new ArrayList<>();
        for (Member m : all) {
            if (scope.equals(m.getId())) {
                mine.add(m);
            }
        }
        return mine;
    }

    public Member detail(Long id) {
        Member member = memberMapper.selectById(id, UserContext.getFamilyId());
        if (member == null) {
            throw new BizException("成员不存在");
        }
        Long scope = UserContext.scopeMemberId();
        if (scope != null && !scope.equals(id)) {
            throw new BizException("成员不存在");
        }
        return member;
    }

    public Member add(Member member) {
        UserContext.requireOwner();
        Long familyId = UserContext.getFamilyId();
        validate(member);
        if (memberMapper.countByName(familyId, member.getMemberName(), null) > 0) {
            throw new BizException("家庭中已有同名成员：" + member.getMemberName());
        }
        member.setFamilyId(familyId);
        memberMapper.insert(member);
        logService.record(OperationLogService.M_MEMBER, OperationLogService.A_ADD, member.getId(),
                "新增成员【" + member.getMemberName() + "】，月度预算 " + member.getMonthlyBudget() + " 元");
        return member;
    }

    public Member update(Long id, Member member) {
        UserContext.requireOwner();
        Long familyId = UserContext.getFamilyId();
        Member before = memberMapper.selectById(id, familyId);
        if (before == null) {
            throw new BizException("成员不存在");
        }
        validate(member);
        if (memberMapper.countByName(familyId, member.getMemberName(), id) > 0) {
            throw new BizException("家庭中已有同名成员：" + member.getMemberName());
        }
        member.setId(id);
        member.setFamilyId(familyId);
        memberMapper.update(member);
        logService.record(OperationLogService.M_MEMBER, OperationLogService.A_UPDATE, id,
                "修改成员【" + member.getMemberName() + "】，预算 "
                        + before.getMonthlyBudget() + " → " + member.getMonthlyBudget() + " 元");
        return member;
    }

    public void delete(Long id) {
        UserContext.requireOwner();
        Long familyId = UserContext.getFamilyId();
        Member before = memberMapper.selectById(id, familyId);
        if (before == null) {
            throw new BizException("成员不存在");
        }
        int records = memberMapper.countRecords(id);
        if (records > 0) {
            // 直接删会让流水失去归属，统计口径也会对不上，因此挡在这里
            throw new BizException("该成员名下还有 " + records + " 笔流水，请先删除或转移这些流水");
        }
        if (userMapper.countByMemberId(id, null) > 0) {
            throw new BizException("该成员已绑定登录账号，请先删除或解绑账号");
        }
        memberMapper.deleteById(id, familyId);
        logService.record(OperationLogService.M_MEMBER, OperationLogService.A_DELETE, id,
                "删除成员【" + before.getMemberName() + "】");
    }

    private void validate(Member member) {
        if (!StringUtils.hasText(member.getMemberName())) {
            throw new BizException("请输入成员姓名");
        }
        if (member.getMemberName().length() > 20) {
            throw new BizException("成员姓名不能超过 20 个字");
        }
        if (StringUtils.hasText(member.getRelation()) && !RELATIONS.contains(member.getRelation())) {
            throw new BizException("家庭关系只能是：" + String.join("/", RELATIONS));
        }
        if (member.getMonthlyBudget() == null) {
            member.setMonthlyBudget(BigDecimal.ZERO);
        }
        if (member.getMonthlyBudget().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("月度预算不能为负数");
        }
    }

    // ---------------- 成员登录账号（户主管理） ----------------

    /** 本家庭的账号列表，密码字段一律置空后再返回 */
    public List<User> accounts() {
        UserContext.requireOwner();
        List<User> list = userMapper.selectByFamily(UserContext.getFamilyId());
        list.forEach(u -> u.setPassword(null));
        return list;
    }

    /**
     * 为某个家庭成员开通登录账号，角色固定为普通成员。
     * 户主不能通过这个接口再造一个户主 —— 一个家庭只应有一个财务负责人，
     * 否则"谁能看全家数据"就失控了。
     */
    public User createAccount(Long memberId, String username, String password) {
        UserContext.requireOwner();
        Long familyId = UserContext.getFamilyId();

        Member member = memberMapper.selectById(memberId, familyId);
        if (member == null) {
            throw new BizException("成员不存在");
        }
        if (!StringUtils.hasText(username) || username.length() < 3 || username.length() > 20) {
            throw new BizException("账号长度需在 3-20 个字符之间");
        }
        if (!StringUtils.hasText(password) || password.length() < 6 || password.length() > 20) {
            throw new BizException("密码长度需在 6-20 个字符之间");
        }
        if (userMapper.countByUsername(username) > 0) {
            throw new BizException("该账号已被占用，请换一个");
        }
        if (userMapper.countByMemberId(memberId, null) > 0) {
            throw new BizException("成员【" + member.getMemberName() + "】已有登录账号");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(Md5Util.md5(password));
        user.setRealName(member.getMemberName());
        user.setFamilyId(familyId);
        user.setMemberId(memberId);
        user.setRole(Role.MEMBER);
        user.setStatus(1);
        userMapper.insert(user);

        logService.record(OperationLogService.M_MEMBER, OperationLogService.A_ADD, user.getId(),
                "为成员【" + member.getMemberName() + "】开通账号 " + username + "（普通成员）");
        user.setPassword(null);
        return user;
    }

    /** 重置成员账号密码 */
    public void resetPassword(Long userId, String newPassword) {
        UserContext.requireOwner();
        User target = mustBeSameFamily(userId);
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BizException("密码长度需在 6-20 个字符之间");
        }
        userMapper.updatePassword(userId, Md5Util.md5(newPassword));
        logService.record(OperationLogService.M_MEMBER, OperationLogService.A_RESET_PWD, userId,
                "重置账号 " + target.getUsername() + " 的密码");
    }

    /** 启用/禁用成员账号 */
    public void toggleStatus(Long userId, Integer status) {
        UserContext.requireOwner();
        User target = mustBeSameFamily(userId);
        if (target.getId().equals(UserContext.getUserId())) {
            throw new BizException("不能禁用自己的账号");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态只能是 0=禁用 1=启用");
        }
        userMapper.updateStatus(userId, status);
        logService.record(OperationLogService.M_MEMBER,
                status == 1 ? OperationLogService.A_ENABLE : OperationLogService.A_DISABLE, userId,
                (status == 1 ? "启用" : "禁用") + "账号 " + target.getUsername());
    }

    private User mustBeSameFamily(Long userId) {
        User target = userMapper.selectById(userId);
        if (target == null || !UserContext.getFamilyId().equals(target.getFamilyId())) {
            throw new BizException("账号不存在");
        }
        if (target.getRole() != null && target.getRole() >= Role.OWNER) {
            throw new BizException("不能操作户主或管理员账号");
        }
        return target;
    }
}
