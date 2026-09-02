package com.gjp.category;

import com.gjp.common.BizException;
import com.gjp.common.UserContext;
import com.gjp.entity.Category;
import com.gjp.log.OperationLogService;
import com.gjp.mapper.CategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收支分类业务。
 *
 * 分类为**三级**结构（需求第 2 条）：
 *   文化娱乐(1级) → 影音娱乐(2级) → 游戏充值 / KTV(3级)
 * 之所以停在三级：再往下分，用户录入时要点三四层菜单才能选到，
 * 实际使用成本高于收益；而三级已经能表达「娱乐-影音-游戏」这类需求。
 *
 * 预置分类（is_default = 1）允许改名但不允许删除，避免用户误删导致历史流水没有分类可归。
 * 管理类操作（增删改）仅户主可用，普通成员只读 —— 分类是全家共用的口径，
 * 任由每个成员改会让统计对不上。
 */
@Service
public class CategoryService {

    /** 最大层级 */
    private static final int MAX_LEVEL = 3;

    /**
     * 新家庭的预置分类。数组含义：{一级, 二级, 二级, ...}，
     * 需要三级的用 "二级>三级1|三级2" 这种写法，避免为了少数几个三级分类
     * 把整个结构改成嵌套 Map 而变得难读。
     */
    private static final String[][] DEFAULT_INCOME = {
            {"工资收入", "基本工资", "奖金", "加班费"},
            {"投资收益", "利息", "股票基金", "房租收入"},
            {"其他收入", "红包礼金", "报销", "兼职"}
    };

    private static final String[][] DEFAULT_EXPENSE = {
            {"餐饮支出", "家庭买菜", "外出就餐>正餐|火锅烧烤|快餐", "外卖", "饮品零食>咖啡奶茶|零食水果"},
            {"购物支出", "服饰鞋帽", "日用品", "数码家电>手机数码|家用电器", "美妆护理"},
            {"居住支出", "房租房贷", "水电燃气>电费|水费|燃气费", "物业费", "维修装修"},
            {"交通支出", "公共交通", "打车", "加油", "停车过路费"},
            {"教育支出", "学费", "书籍资料", "培训班"},
            {"医疗健康", "门诊药品", "住院", "体检保险"},
            {"文化娱乐", "旅游度假", "健身运动", "影音娱乐>电影|游戏充值|KTV|流媒体会员"},
            {"人情往来", "礼金红包", "送礼", "请客吃饭"},
            {"其他支出", "手续费", "捐赠", "杂项"}
    };

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private OperationLogService logService;

    // ---------------- 查询 ----------------

    /** 平铺列表，供表格展示 */
    public List<Category> list(Integer type) {
        UserContext.requireFamilyMember();
        return categoryMapper.selectByFamily(UserContext.getFamilyId(), type);
    }

    /**
     * 树形列表，供录入页的级联选择器使用。
     * 只查一次数据库再在内存里拼三级树，避免逐层查子分类造成的 N+1 查询。
     */
    public List<Category> tree(Integer type) {
        UserContext.requireFamilyMember();
        List<Category> all = categoryMapper.selectByFamily(UserContext.getFamilyId(), type);

        Map<Long, Category> byId = new LinkedHashMap<>();
        for (Category c : all) {
            c.setChildren(new ArrayList<>());
            byId.put(c.getId(), c);
        }

        List<Category> roots = new ArrayList<>();
        // all 已按 level 升序排好，所以挂子节点时父节点一定已经在 byId 里
        for (Category c : all) {
            if (c.getParentId() == null || c.getParentId() == 0L) {
                roots.add(c);
            } else {
                Category parent = byId.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(c);
                }
            }
        }
        // 级联选择器遇到 children 为空数组时会显示一个空的下级面板，置成 null 才不会
        for (Category c : all) {
            if (c.getChildren().isEmpty()) {
                c.setChildren(null);
            }
        }
        return roots;
    }

    // ---------------- 维护 ----------------

    public Category add(Category category) {
        UserContext.requireOwner();
        Long familyId = UserContext.getFamilyId();

        if (!StringUtils.hasText(category.getCategoryName())) {
            throw new BizException("请输入分类名称");
        }
        if (category.getCategoryName().length() > 20) {
            throw new BizException("分类名称不能超过 20 个字");
        }
        if (category.getType() == null || (category.getType() != 1 && category.getType() != 2)) {
            throw new BizException("分类类型只能是 1=收入 或 2=支出");
        }

        Long parentId = category.getParentId() == null ? 0L : category.getParentId();
        int level = 1;
        Long rootId = 0L;
        if (parentId != 0L) {
            Category parent = categoryMapper.selectById(parentId, familyId);
            if (parent == null) {
                throw new BizException("父分类不存在");
            }
            if (parent.getLevel() >= MAX_LEVEL) {
                throw new BizException("分类最多 " + MAX_LEVEL + " 级，不能在三级分类下继续添加");
            }
            if (!parent.getType().equals(category.getType())) {
                throw new BizException("子分类的收入/支出类型必须与父分类一致");
            }
            level = parent.getLevel() + 1;
            rootId = parent.getRootId();
        }
        if (categoryMapper.countByName(familyId, parentId, category.getCategoryName(), null) > 0) {
            throw new BizException("同级下已存在同名分类：" + category.getCategoryName());
        }

        category.setFamilyId(familyId);
        category.setParentId(parentId);
        category.setLevel(level);
        category.setRootId(rootId);
        category.setIsDefault(0);
        category.setSortNo(category.getSortNo() == null ? 99 : category.getSortNo());
        categoryMapper.insert(category);
        if (level == 1) {
            // 一级分类的 root_id 就是自己，插入时拿不到自增ID，所以插完回填
            categoryMapper.fixRootIdToSelf(category.getId());
            category.setRootId(category.getId());
        }

        logService.record(OperationLogService.M_CATEGORY, OperationLogService.A_ADD, category.getId(),
                "新增" + level + "级分类【" + category.getCategoryName() + "】");
        return category;
    }

    /** 只允许改名称和排序号；类型和父级一旦确定就不能改，否则历史流水的归属会错乱 */
    public Category update(Long id, Category category) {
        UserContext.requireOwner();
        Long familyId = UserContext.getFamilyId();
        Category old = categoryMapper.selectById(id, familyId);
        if (old == null) {
            throw new BizException("分类不存在");
        }
        if (!StringUtils.hasText(category.getCategoryName())) {
            throw new BizException("请输入分类名称");
        }
        if (categoryMapper.countByName(familyId, old.getParentId(), category.getCategoryName(), id) > 0) {
            throw new BizException("同级下已存在同名分类：" + category.getCategoryName());
        }

        String oldName = old.getCategoryName();
        old.setCategoryName(category.getCategoryName());
        old.setSortNo(category.getSortNo() == null ? old.getSortNo() : category.getSortNo());
        categoryMapper.update(old);

        logService.record(OperationLogService.M_CATEGORY, OperationLogService.A_UPDATE, id,
                "分类改名：【" + oldName + "】→【" + old.getCategoryName() + "】");
        return old;
    }

    public void delete(Long id) {
        UserContext.requireOwner();
        Long familyId = UserContext.getFamilyId();
        Category old = categoryMapper.selectById(id, familyId);
        if (old == null) {
            throw new BizException("分类不存在");
        }
        if (old.getIsDefault() != null && old.getIsDefault() == 1) {
            throw new BizException("系统预置分类不允许删除，可以改名");
        }
        if (categoryMapper.countChildren(id) > 0) {
            throw new BizException("该分类下还有子分类，请先删除子分类");
        }
        int records = categoryMapper.countRecords(id);
        if (records > 0) {
            throw new BizException("该分类已被 " + records + " 笔流水使用，不能删除");
        }
        categoryMapper.deleteById(id, familyId);
        logService.record(OperationLogService.M_CATEGORY, OperationLogService.A_DELETE, id,
                "删除分类【" + old.getCategoryName() + "】");
    }

    // ---------------- 预置分类初始化 ----------------

    /**
     * 为新家庭写入预置分类，is_default = 1 表示不允许删除。
     * 注册流程调用，放在 CategoryService 里而不是 AuthService，
     * 是因为分类结构（三级、root_id 维护）属于分类模块的知识。
     */
    public void initDefaultCategories(Long familyId) {
        insertGroup(familyId, 1, DEFAULT_INCOME);
        insertGroup(familyId, 2, DEFAULT_EXPENSE);
    }

    private void insertGroup(Long familyId, int type, String[][] groups) {
        int sort = 0;
        for (String[] group : groups) {
            Category first = newCategory(familyId, 0L, 0L, 1, type, group[0], sort++);
            categoryMapper.insert(first);
            categoryMapper.fixRootIdToSelf(first.getId());
            Long rootId = first.getId();

            int subSort = 0;
            for (int i = 1; i < group.length; i++) {
                // "影音娱乐>电影|游戏充值|KTV" —— 竖线右边的是三级分类
                String spec = group[i];
                String secondName = spec;
                String[] thirds = new String[0];
                int gt = spec.indexOf('>');
                if (gt > 0) {
                    secondName = spec.substring(0, gt);
                    thirds = spec.substring(gt + 1).split("\\|");
                }

                Category second = newCategory(familyId, first.getId(), rootId, 2, type, secondName, subSort++);
                categoryMapper.insert(second);

                int thirdSort = 0;
                for (String thirdName : thirds) {
                    Category third = newCategory(familyId, second.getId(), rootId, 3, type,
                            thirdName.trim(), thirdSort++);
                    categoryMapper.insert(third);
                }
            }
        }
    }

    private Category newCategory(Long familyId, Long parentId, Long rootId, int level,
                                 int type, String name, int sortNo) {
        Category c = new Category();
        c.setFamilyId(familyId);
        c.setParentId(parentId);
        c.setRootId(rootId);
        c.setLevel(level);
        c.setType(type);
        c.setCategoryName(name);
        c.setIsDefault(1);
        c.setSortNo(sortNo);
        return c;
    }
}
