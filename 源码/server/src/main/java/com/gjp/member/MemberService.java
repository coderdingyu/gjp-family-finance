package com.gjp.member;

import com.gjp.common.BizException;
import com.gjp.common.UserContext;
import com.gjp.entity.Member;
import com.gjp.mapper.MemberMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 家庭成员业务。成员是收支数据的最小归属单位，所以这里的校验偏严：
 * 同一家庭不允许重名，名下还有流水的成员不允许删除。
 */
@Service
public class MemberService {

    /** 允许的家庭关系，前端下拉框与这里保持一致 */
    private static final List<String> RELATIONS = List.of("本人", "配偶", "子女", "父母", "其他");

    @Autowired
    private MemberMapper memberMapper;

    public List<Member> list() {
        return memberMapper.selectByFamily(UserContext.getFamilyId());
    }

    public Member detail(Long id) {
        Member member = memberMapper.selectById(id, UserContext.getFamilyId());
        if (member == null) {
            throw new BizException("成员不存在");
        }
        return member;
    }

    public Member add(Member member) {
        Long familyId = UserContext.getFamilyId();
        validate(member);
        if (memberMapper.countByName(familyId, member.getMemberName(), null) > 0) {
            throw new BizException("家庭中已有同名成员：" + member.getMemberName());
        }
        member.setFamilyId(familyId);
        memberMapper.insert(member);
        return member;
    }

    public Member update(Long id, Member member) {
        Long familyId = UserContext.getFamilyId();
        detail(id);
        validate(member);
        if (memberMapper.countByName(familyId, member.getMemberName(), id) > 0) {
            throw new BizException("家庭中已有同名成员：" + member.getMemberName());
        }
        member.setId(id);
        member.setFamilyId(familyId);
        memberMapper.update(member);
        return member;
    }

    public void delete(Long id) {
        Long familyId = UserContext.getFamilyId();
        detail(id);
        int records = memberMapper.countRecords(id);
        if (records > 0) {
            // 直接删会让流水失去归属，统计口径也会对不上，因此挡在这里
            throw new BizException("该成员名下还有 " + records + " 笔流水，请先删除或转移这些流水");
        }
        memberMapper.deleteById(id, familyId);
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
}
