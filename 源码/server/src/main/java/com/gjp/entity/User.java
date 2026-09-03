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

    /** 所属家庭ID，系统管理员为 0 */
    private Long familyId;

    /** 绑定的家庭成员ID，普通成员据此做数据隔离 */
    private Long memberId;

    /** 角色：0=普通成员 1=户主 2=系统管理员 */
    private Integer role;

    /** 状态：1=正常 0=已禁用 */
    private Integer status;

    /** 登录会话版本。禁用时加一，拦截器用它踢掉所有已登录槽位 */
    private Integer sessionVersion;

    /** 最后登录时间 */
    private LocalDateTime lastLogin;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 绑定成员的姓名，联表查询用，非数据库字段 */
    private String memberName;

    /** 所属家庭名称，管理员列表用，非数据库字段 */
    private String familyName;

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

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(Integer sessionVersion) {
        this.sessionVersion = sessionVersion;
    }

    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
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