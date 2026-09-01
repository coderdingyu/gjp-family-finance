package com.gjp.entity;

import java.time.LocalDateTime;

/**
 * 家庭表 t_family 实体
 */
public class Family {

    /** 家庭ID */
    private Long id;

    /** 家庭名称 */
    private String familyName;

    /** 创建时间 */
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}