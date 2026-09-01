package com.gjp.category;

import com.gjp.common.BizException;
import com.gjp.common.UserContext;
import com.gjp.entity.Category;
import com.gjp.mapper.CategoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 收支分类业务。分类固定两级：一级分类 parent_id = 0，二级分类挂在一级下。
 * 预置分类（is_default = 1）允许改名但不允许删除，避免用户误删导致历史流水没有分类可归。
 */
@Service
public class CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    /** 平铺列表，供表格展示 */
    public List<Category> list(Integer type) {
        return categoryMapper.selectByFamily(UserContext.getFamilyId(), type);
    }

    /**
     * 树形列表，供录入页的级联选择器使用。
     * 只查一次数据库再在内存里拼树，避免按父分类逐个查子分类造成的 N+1 查询。
     */
    public List<Category> tree(Integer type) {
        List<Category> all = categoryMapper.selectByFamily(UserContext.getFamilyId(), type);
        Map<Long, Category> parents = new LinkedHashMap<>();
        List<Category> children = new ArrayList<>();
        for (Category c : all) {
            if (c.getParentId() == null || c.getParentId() == 0L) {
                c.setChildren(new ArrayList<>());
                parents.put(c.getId(), c);
            } else {
                children.add(c);
            }
        }
        for (Category c : children) {
            Category parent = parents.get(c.getParentId());
            if (parent != null) {
                parent.getChildren().add(c);
            }
        }
        return new ArrayList<>(parents.values());
    }

    public Category add(Category category) {
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
        if (parentId != 0L) {
            Category parent = categoryMapper.selectById(parentId, familyId);
            if (parent == null) {
                throw new BizException("父分类不存在");
            }
            if (parent.getParentId() != 0L) {
                throw new BizException("分类最多两级，不能在二级分类下继续添加");
            }
            if (!parent.getType().equals(category.getType())) {
                throw new BizException("子分类的收入/支出类型必须与父分类一致");
            }
        }
        if (categoryMapper.countByName(familyId, parentId, category.getCategoryName(), null) > 0) {
            throw new BizException("同级下已存在同名分类：" + category.getCategoryName());
        }
        category.setFamilyId(familyId);
        category.setParentId(parentId);
        category.setIsDefault(0);
        category.setSortNo(category.getSortNo() == null ? 99 : category.getSortNo());
        categoryMapper.insert(category);
        return category;
    }

    /** 只允许改名称和排序号；类型和父级一旦确定就不能改，否则历史流水的归属会错乱 */
    public Category update(Long id, Category category) {
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
        old.setCategoryName(category.getCategoryName());
        old.setSortNo(category.getSortNo() == null ? old.getSortNo() : category.getSortNo());
        categoryMapper.update(old);
        return old;
    }

    public void delete(Long id) {
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
    }
}
