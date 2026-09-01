package com.gjp.entity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 收支分类表 t_category 实体，支持两级分类
 */
public class Category {

    /** 分类ID */
    private Long id;

    /** 所属家庭ID */
    private Long familyId;

    /** 父分类ID，0 表示一级分类 */
    private Long parentId;

    /** 分类名称 */
    private String categoryName;

    /** 类型：1=收入 2=支出 */
    private Integer type;

    /** 是否系统预置：1=预置不可删 0=用户自定义 */
    private Integer isDefault;

    /** 排序号 */
    private Integer sortNo;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 父分类名称，联表查询用，非数据库字段 */
    private String parentName;

    /** 子分类列表，构建树形结构用，非数据库字段 */
    private List<Category> children;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public List<Category> getChildren() {
        return children;
    }

    public void setChildren(List<Category> children) {
        this.children = children;
    }

}