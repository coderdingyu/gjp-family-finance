package com.gjp.log;

/**
 * 日志查询条件。全部可空，为空即不参与过滤。
 */
public class LogQuery {

    /** 家庭ID，只有系统管理员传这个才有效 */
    private Long familyId;
    /** 操作人，普通成员传了也会被强制成自己 */
    private Long userId;
    private String module;
    private String action;
    /** 1=成功 0=失败 */
    private Integer success;
    /** 关键字：模糊匹配摘要、账号、姓名 */
    private String keyword;
    /** 起始时间，格式 yyyy-MM-dd HH:mm:ss */
    private String startTime;
    private String endTime;

    private Integer pageNum = 1;
    private Integer pageSize = 20;

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

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
