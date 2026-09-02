package com.gjp.log;

import com.gjp.entity.OperationLog;

import java.time.LocalDateTime;

/**
 * 系统管理员视角的操作日志 DTO。
 *
 * 管理员只负责跨家庭的运维排查，不需要看到账单金额、消费内容或操作人姓名。
 * 因此这里刻意只暴露家庭、模块、动作、成功状态和时间，避免把数据库里的
 * summary / detail / realName 原样带回前端。
 */
public class AdminLogDTO {

    private Long familyId;
    private String module;
    private String action;
    private Integer success;
    private LocalDateTime createTime;

    public AdminLogDTO(Long familyId, String module, String action, Integer success,
                       LocalDateTime createTime) {
        this.familyId = familyId;
        this.module = module;
        this.action = action;
        this.success = success;
        this.createTime = createTime;
    }

    public static AdminLogDTO from(OperationLog log) {
        return new AdminLogDTO(log.getFamilyId(), log.getModule(), log.getAction(),
                log.getSuccess(), log.getCreateTime());
    }

    public Long getFamilyId() {
        return familyId;
    }

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }

    public Integer getSuccess() {
        return success;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
