package com.gjp.auth;

import com.gjp.auth.dto.LoginDTO;
import com.gjp.auth.dto.RegisterDTO;
import com.gjp.common.BizException;
import com.gjp.common.Md5Util;
import com.gjp.common.UserContext;
import com.gjp.entity.Category;
import com.gjp.entity.Family;
import com.gjp.entity.Member;
import com.gjp.entity.User;
import com.gjp.mapper.CategoryMapper;
import com.gjp.mapper.FamilyMapper;
import com.gjp.mapper.MemberMapper;
import com.gjp.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 登录注册业务。
 */
@Service
public class AuthService {

    /** 注册时为新家庭初始化的预置分类：{一级分类, 二级分类...}，第一行是收入，其余是支出 */
    private static final String[][] DEFAULT_INCOME = {
            {"工资收入", "基本工资", "奖金", "加班费"},
            {"投资收益", "利息", "股票基金", "房租收入"},
            {"其他收入", "红包礼金", "报销", "兼职"}
    };

    private static final String[][] DEFAULT_EXPENSE = {
            {"餐饮支出", "家庭买菜", "外出就餐", "外卖", "饮品零食"},
            {"购物支出", "服饰鞋帽", "日用品", "数码家电", "美妆护理"},
            {"居住支出", "房租房贷", "水电燃气", "物业费", "维修装修"},
            {"交通支出", "公共交通", "打车", "加油", "停车过路费"},
            {"教育支出", "学费", "书籍资料", "培训班"},
            {"医疗健康", "门诊药品", "住院", "体检保险"},
            {"文化娱乐", "旅游度假", "健身运动", "影音娱乐"},
            {"人情往来", "礼金红包", "送礼", "请客吃饭"},
            {"其他支出", "手续费", "捐赠", "杂项"}
    };

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FamilyMapper familyMapper;
    @Autowired
    private MemberMapper memberMapper;
    @Autowired
    private CategoryMapper categoryMapper;

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

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(Md5Util.md5(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setFamilyId(family.getId());
        userMapper.insert(user);

        // 注册人自动成为家庭的第一个成员，否则记账时无成员可选
        Member self = new Member();
        self.setFamilyId(family.getId());
        self.setMemberName(dto.getRealName());
        self.setRelation("本人");
        self.setMonthlyBudget(BigDecimal.ZERO);
        memberMapper.insert(self);

        initDefaultCategories(family.getId());

        return new UserContext.LoginUser(user.getId(), user.getUsername(), user.getRealName(),
                family.getId(), family.getFamilyName());
    }

    /** 为新家庭写入两级预置分类，is_default = 1 表示不允许删除 */
    private void initDefaultCategories(Long familyId) {
        insertCategoryGroup(familyId, 1, DEFAULT_INCOME);
        insertCategoryGroup(familyId, 2, DEFAULT_EXPENSE);
    }

    private void insertCategoryGroup(Long familyId, int type, String[][] groups) {
        int sort = 0;
        for (String[] group : groups) {
            Category parent = new Category();
            parent.setFamilyId(familyId);
            parent.setParentId(0L);
            parent.setCategoryName(group[0]);
            parent.setType(type);
            parent.setIsDefault(1);
            parent.setSortNo(sort++);
            categoryMapper.insert(parent);

            int subSort = 0;
            for (int i = 1; i < group.length; i++) {
                Category child = new Category();
                child.setFamilyId(familyId);
                child.setParentId(parent.getId());
                child.setCategoryName(group[i]);
                child.setType(type);
                child.setIsDefault(1);
                child.setSortNo(subSort++);
                categoryMapper.insert(child);
            }
        }
    }

    /** 登录校验，成功返回登录态对象由 Controller 放进 session */
    public UserContext.LoginUser login(LoginDTO dto) {
        User user = userMapper.selectByUsername(dto.getUsername());
        if (user == null || !user.getPassword().equals(Md5Util.md5(dto.getPassword()))) {
            // 账号不存在与密码错误提示合并，避免被用来枚举已注册账号
            throw new BizException("账号或密码错误");
        }
        Family family = familyMapper.selectById(user.getFamilyId());
        return new UserContext.LoginUser(user.getId(), user.getUsername(), user.getRealName(),
                user.getFamilyId(), family == null ? "" : family.getFamilyName());
    }
}
