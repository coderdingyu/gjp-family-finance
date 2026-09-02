package com.gjp.entity;

import java.time.LocalDateTime;

/**
 * 操作日志表 t_operation_log 实体。
 */
public class OperationLog {

    /** 日志ID */
    private Long id;

    /** 所属家庭ID，0=系统级操作 */
    private Long familyId;

    /** 操作人用户ID */
    private Long userId;

    /** 操作人账号 */
    private String username;

    /** 操作人姓名 */
    private String realName;

    /** 模块 */
    private String module;

    /** 动作 */
    private String action;

    /** 被操作对象ID */
    private Long targetId;

    /** 一句话摘要 */
    private String summary;

    /** 详细内容，JSON 文本 */
    private String detail;

    /** 操作来源IP */
    private String ip;

    /** 是否成功：1=成功 0=失败 */
    private Integer success;

    /** 失败原因 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Integer costMs;

    /** 操作时间 */
    private LocalDateTime createTime;

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Integer getCostMs() {
        return costMs;
    }

    public void setCostMs(Integer costMs) {
        this.costMs = costMs;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}