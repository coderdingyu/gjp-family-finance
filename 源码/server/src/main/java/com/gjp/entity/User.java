package com.gjp.entity;

import java.time.LocalDateTime;

/**
 * 用户表 t_user 实体（登录账号）
 */
public class User {

    /** 用户ID */
    private Long id;

    /** 登录账号 */
    private String username;

    /** 密码，MD5 摘要后存储，返回给前端前必须置空 */
    private String password;

    /** 真实姓名 */
    private String realName;

    /** 所属家庭ID */
    private Long familyId;

    /** 创建时间 */
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public Long getFamilyId() {
        return familyId;
    }

    public void setFamilyId(Long familyId) {
        this.familyId = familyId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}