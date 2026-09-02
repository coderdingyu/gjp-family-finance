package com.gjp.auth;

import com.gjp.auth.dto.LoginDTO;
import com.gjp.auth.dto.RegisterDTO;
import com.gjp.category.CategoryService;
import com.gjp.common.BizException;
import com.gjp.common.Md5Util;
import com.gjp.common.Role;
import com.gjp.common.UserContext;
import com.gjp.entity.Family;
import com.gjp.entity.Member;
import com.gjp.entity.User;
import com.gjp.log.OperationLogService;
import com.gjp.mapper.FamilyMapper;
import com.gjp.mapper.MemberMapper;
import com.gjp.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 登录注册业务。
 *
 * 注册即"开一个新家庭"，注册人自动成为**户主**（能看全家数据）。
 * 家庭里其他成员的账号由户主在成员管理里另外开，默认是普通成员（只能看自己）。
 */
@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FamilyMapper familyMapper;
    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private OperationLogService logService;

    /**
     * 注册。整个过程放在一个事务里：家庭、账号、默认成员、预置分类要么全部成功，
     * 要么全部回滚，避免出现"账号建好了但没有任何分类可选"的半成品数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserContext.LoginUser register(RegisterDTO dto) {
        if (userMapper.countByUsername(dto.getUsername()) > 0) {
            throw new BizException("该账号已被注册，请换一个");
        }

        Family family = new Family();
        family.setFamilyName(dto.getFamilyName());
        familyMapper.insert(family);

        // 注册人自动成为家庭的第一个成员，否则记账时无成员可选
        Member self = new Member();
        self.setFamilyId(family.getId());
        self.setMemberName(dto.getRealName());
        self.setRelation("本人");
        self.setMonthlyBudget(BigDecimal.ZERO);
        memberMapper.insert(self);

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(Md5Util.md5(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setFamilyId(family.getId());
        user.setMemberId(self.getId());
        user.setRole(Role.OWNER);
        user.setStatus(1);
        userMapper.insert(user);

        categoryService.initDefaultCategories(family.getId());

        UserContext.LoginUser login = toLoginUser(user, family.getFamilyName(), self.getMemberName());
        logService.recordAuth(OperationLogService.A_LOGIN, user.getId(), user.getUsername(),
                user.getRealName(), family.getId(),
                "注册新家庭【" + family.getFamilyName() + "】并登录", true, null);
        return login;
    }

    /** 登录校验，成功返回登录态对象由 Controller 放进 session */
    public UserContext.LoginUser login(LoginDTO dto) {
        User user = userMapper.selectByUsername(dto.getUsername());
        if (user == null || !user.getPassword().equals(Md5Util.md5(dto.getPassword()))) {
            // 账号不存在与密码错误提示合并，避免被用来枚举已注册账号
            logService.recordAuth(OperationLogService.A_LOGIN, user == null ? null : user.getId(),
                    dto.getUsername(), user == null ? null : user.getRealName(),
                    user == null ? 0L : user.getFamilyId(),
                    "登录失败：" + dto.getUsername(), false, "账号或密码错误");
            throw new BizException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            logService.recordAuth(OperationLogService.A_LOGIN, user.getId(), user.getUsername(),
                    user.getRealName(), user.getFamilyId(),
                    "登录失败：账号已被禁用", false, "账号已被禁用");
            throw new BizException("该账号已被禁用，请联系管理员");
        }

        String familyName = "";
        String memberName = null;
        if (user.getRole() != null && user.getRole() == Role.ADMIN) {
            familyName = "系统管理";
        } else {
            Family family = familyMapper.selectById(user.getFamilyId());
            familyName = family == null ? "" : family.getFamilyName();
            if (user.getMemberId() != null) {
                Member m = memberMapper.selectById(user.getMemberId(), user.getFamilyId());
                memberName = m == null ? null : m.getMemberName();
            }
        }

        userMapper.touchLastLogin(user.getId());
        logService.recordAuth(OperationLogService.A_LOGIN, user.getId(), user.getUsername(),
                user.getRealName(), user.getFamilyId(),
                Role.name(user.getRole()) + "【" + user.getUsername() + "】登录成功", true, null);
        return toLoginUser(user, familyName, memberName);
    }

    private UserContext.LoginUser toLoginUser(User user, String familyName, String memberName) {
        UserContext.LoginUser login = new UserContext.LoginUser(
                user.getId(), user.getUsername(), user.getRealName(), user.getFamilyId(), familyName);
        login.setMemberId(user.getMemberId());
        login.setMemberName(memberName);
        login.setRole(user.getRole() == null ? Role.MEMBER : user.getRole());
        return login;
    }
}
