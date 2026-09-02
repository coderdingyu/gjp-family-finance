package com.gjp.admin;

import com.gjp.common.BizException;
import com.gjp.common.Md5Util;
import com.gjp.common.Role;
import com.gjp.common.UserContext;
import com.gjp.entity.User;
import com.gjp.log.OperationLogService;
import com.gjp.mapper.FamilyMapper;
import com.gjp.mapper.OperationLogMapper;
import com.gjp.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统管理员业务（需求第 8 条）。
 *
 * 按需求只设**一个**管理员权限（role = 2），职责限定为"网页维护"：
 * 看系统运行状况、翻操作日志定位问题、处理账号（重置密码、封禁）。
 *
 * 刻意**不给**管理员业务数据入口：管理员看不到任何家庭的流水金额明细，
 * 只能看到条数这类规模指标。理由是记账数据涉及家庭隐私，
 * 运维排查不需要看到具体消费内容 —— 权限应该按"完成工作所需的最小范围"给。
 */
@Service
public class AdminService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private FamilyMapper familyMapper;
    @Autowired
    private OperationLogMapper logMapper;
    @Autowired
    private OperationLogService logService;

    /**
     * 运行概览：进程、内存、数据规模、日志健康度。
     * 这些指标合起来能回答"网站现在是否正常"，是管理员首页的主体。
     */
    public Map<String, Object> overview() {
        UserContext.requireAdmin();

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long maxMb = rt.maxMemory() / 1024 / 1024;
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        long logTotal = logMapper.countAll();
        long logFailed = logMapper.countFailed();

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("javaVersion", System.getProperty("java.version"));
        runtime.put("osName", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        runtime.put("cpuCores", rt.availableProcessors());
        runtime.put("memoryUsedMb", usedMb);
        runtime.put("memoryMaxMb", maxMb);
        runtime.put("memoryUsedRate", maxMb == 0 ? 0
                : Math.round(usedMb * 10000.0 / maxMb) / 100.0);
        runtime.put("uptime", humanDuration(uptimeMs));
        runtime.put("startTime", LocalDateTime.now().minusNanos(uptimeMs * 1_000_000L)
                .withNano(0).toString().replace('T', ' '));

        Map<String, Object> scale = new LinkedHashMap<>();
        scale.put("familyCount", familyMapper.countAll());
        scale.put("userCount", userMapper.countAll());
        scale.put("disabledUserCount", userMapper.countDisabled());
        scale.put("logCount", logTotal);

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("failedCount", logFailed);
        health.put("failRate", logTotal == 0 ? 0
                : Math.round(logFailed * 10000.0 / logTotal) / 100.0);
        // 失败率超过 5% 认为需要关注：正常使用下失败主要是用户输错，占比不会太高
        health.put("status", logTotal == 0 ? "暂无数据"
                : (logFailed * 100.0 / logTotal > 5 ? "需关注" : "正常"));

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("runtime", runtime);
        map.put("scale", scale);
        map.put("health", health);
        map.put("logByModule", logMapper.countByModule(null));
        map.put("logByDay", logMapper.countByDay(14));
        return map;
    }

    /** 各家庭规模概览 */
    public List<Map<String, Object>> families() {
        UserContext.requireAdmin();
        return familyMapper.selectOverview();
    }

    /** 全部账号，密码置空后返回 */
    public List<User> users(String keyword, Integer role) {
        UserContext.requireAdmin();
        List<User> list = userMapper.selectAll(keyword, role);
        list.forEach(u -> u.setPassword(null));
        return list;
    }

    /** 重置任意账号密码 */
    public void resetPassword(Long userId, String newPassword) {
        UserContext.requireAdmin();
        User target = mustExist(userId);
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BizException("密码长度需在 6-20 个字符之间");
        }
        userMapper.updatePassword(userId, Md5Util.md5(newPassword));
        logService.record(OperationLogService.M_ADMIN, OperationLogService.A_RESET_PWD, userId,
                "管理员重置账号 " + target.getUsername() + " 的密码");
    }

    /** 启用/禁用任意账号 */
    public void toggleStatus(Long userId, Integer status) {
        UserContext.requireAdmin();
        User target = mustExist(userId);
        if (target.getRole() != null && target.getRole() == Role.ADMIN) {
            // 只有一个管理员，禁用掉就再也进不来了
            throw new BizException("不能禁用管理员账号");
        }
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态只能是 0=禁用 1=启用");
        }
        userMapper.updateStatus(userId, status);
        logService.record(OperationLogService.M_ADMIN,
                status == 1 ? OperationLogService.A_ENABLE : OperationLogService.A_DISABLE, userId,
                "管理员" + (status == 1 ? "启用" : "禁用") + "账号 " + target.getUsername());
    }

    /** 管理员修改自己的密码 */
    public void changeOwnPassword(String oldPassword, String newPassword) {
        UserContext.requireAdmin();
        User self = userMapper.selectById(UserContext.getUserId());
        if (self == null || !self.getPassword().equals(Md5Util.md5(oldPassword == null ? "" : oldPassword))) {
            throw new BizException("原密码不正确");
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BizException("新密码长度需在 6-20 个字符之间");
        }
        userMapper.updatePassword(self.getId(), Md5Util.md5(newPassword));
        logService.record(OperationLogService.M_ADMIN, OperationLogService.A_RESET_PWD, self.getId(),
                "管理员修改自己的登录密码");
    }

    private User mustExist(Long userId) {
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw new BizException("账号不存在");
        }
        return target;
    }

    /** 毫秒转成"3天2小时5分钟"这种可读形式 */
    private String humanDuration(long ms) {
        Duration d = Duration.ofMillis(ms);
        long days = d.toDays();
        long hours = d.toHours() % 24;
        long minutes = d.toMinutes() % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" 天 ");
        }
        if (days > 0 || hours > 0) {
            sb.append(hours).append(" 小时 ");
        }
        sb.append(minutes).append(" 分钟");
        return sb.toString();
    }
}
