package com.gjp.log;

import com.gjp.common.BizException;
import com.gjp.common.PageResult;
import com.gjp.common.Role;
import com.gjp.common.UserContext;
import com.gjp.entity.OperationLog;
import com.gjp.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

/**
 * 操作日志业务（对应需求第 7 条）。
 *
 * 写入侧的设计原则：**记日志失败绝不能影响主业务**。
 * 日志表写不进去（比如字段超长）时只打一条 error 日志就算了，
 * 不能因此让用户的记账操作回滚 —— 所以 record() 里把异常全吞掉。
 *
 * 读取侧按角色收敛范围：
 *   普通成员 → 只看自己的操作
 *   户主     → 看本家庭全部成员的操作
 *   管理员   → 看所有家庭
 */
@Service
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);

    /** 模块名常量，避免各处手写字符串写错导致筛选筛不出来 */
    public static final String M_RECORD = "流水";
    public static final String M_MEMBER = "成员";
    public static final String M_CATEGORY = "分类";
    public static final String M_ASSET = "资产";
    public static final String M_LOAN = "贷款";
    public static final String M_IMPORT = "导入";
    public static final String M_AUTH = "登录";
    public static final String M_ADMIN = "管理员";

    public static final String A_ADD = "新增";
    public static final String A_UPDATE = "修改";
    public static final String A_DELETE = "删除";
    public static final String A_IMPORT = "导入";
    public static final String A_LOGIN = "登录";
    public static final String A_LOGOUT = "退出";
    public static final String A_RESET_PWD = "重置密码";
    public static final String A_ENABLE = "启用";
    public static final String A_DISABLE = "禁用";
    public static final String A_BATCH_DELETE = "批量删除";

    @Autowired
    private OperationLogMapper logMapper;

    /** 记一条成功日志 */
    public void record(String module, String action, Long targetId, String summary) {
        write(module, action, targetId, summary, null, 1, null, null);
    }

    /** 记一条成功日志，带 JSON 详情 */
    public void record(String module, String action, Long targetId, String summary, String detail) {
        write(module, action, targetId, summary, detail, 1, null, null);
    }

    /** 记一条失败日志 */
    public void recordFail(String module, String action, String summary, String errorMsg) {
        write(module, action, null, summary, null, 0, errorMsg, null);
    }

    /**
     * 记一条日志。登录场景下 UserContext 还没写入（拦截器放行了登录接口），
     * 所以这里允许显式传入操作人信息。
     */
    public void recordAuth(String action, Long userId, String username, String realName,
                           Long familyId, String summary, boolean ok, String errorMsg) {
        OperationLog entity = new OperationLog();
        entity.setFamilyId(familyId == null ? 0L : familyId);
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setRealName(realName);
        entity.setModule(M_AUTH);
        entity.setAction(action);
        entity.setSummary(cut(summary, 255));
        entity.setIp(clientIp());
        entity.setSuccess(ok ? 1 : 0);
        entity.setErrorMsg(cut(errorMsg, 500));
        save(entity);
    }

    private void write(String module, String action, Long targetId, String summary,
                       String detail, int success, String errorMsg, Integer costMs) {
        OperationLog entity = new OperationLog();
        UserContext.LoginUser u = UserContext.get();
        entity.setFamilyId(u == null || u.getFamilyId() == null ? 0L : u.getFamilyId());
        if (u != null) {
            entity.setUserId(u.getUserId());
            entity.setUsername(u.getUsername());
            entity.setRealName(u.getRealName());
        }
        entity.setModule(module);
        entity.setAction(action);
        entity.setTargetId(targetId);
        entity.setSummary(cut(summary, 255));
        entity.setDetail(detail);
        entity.setIp(clientIp());
        entity.setSuccess(success);
        entity.setErrorMsg(cut(errorMsg, 500));
        entity.setCostMs(costMs);
        save(entity);
    }

    private void save(OperationLog entity) {
        try {
            logMapper.insert(entity);
        } catch (Exception e) {
            // 关键：日志写失败不能影响主业务，这里只记录不抛出
            log.error("写操作日志失败：{} {}", entity.getModule(), entity.getAction(), e);
        }
    }

    /** 截断超长文本，避免因为一条备注太长导致整条日志写不进去 */
    private String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    /**
     * 本机访问时 Servlet 容器给的是 IPv6 回环地址（::1 / 0:0:0:0:0:0:0:1），
     * 日志列表里显示成一串冒号很难读，统一换成大家熟悉的 127.0.0.1。
     */
    private String normalize(String ip) {
        if (ip == null) {
            return null;
        }
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    private String clientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest req = attrs.getRequest();
            // 经过 Nginx 等代理时真实IP在这个头里，本地开发直接取 remoteAddr
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                return normalize(forwarded.split(",")[0].trim());
            }
            return normalize(req.getRemoteAddr());
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 查询 ----------------

    /**
     * 分页查询日志。范围按角色自动收敛，前端传的 userId 只对户主和管理员有效。
     */
    public PageResult<OperationLog> page(LogQuery q) {
        if (q.getPageNum() == null || q.getPageNum() < 1) {
            q.setPageNum(1);
        }
        if (q.getPageSize() == null || q.getPageSize() < 1 || q.getPageSize() > 200) {
            q.setPageSize(20);
        }
        int offset = (q.getPageNum() - 1) * q.getPageSize();

        Long familyId;
        Long userId;
        int role = UserContext.getRole();
        if (role == Role.ADMIN) {
            // 管理员：看全部；前端可以指定家庭
            familyId = q.getFamilyId();
            userId = q.getUserId();
        } else if (role == Role.OWNER) {
            familyId = UserContext.getFamilyId();
            userId = q.getUserId();
        } else {
            // 普通成员：锁死到自己
            familyId = UserContext.getFamilyId();
            userId = UserContext.getUserId();
        }

        long total = logMapper.countByQuery(familyId, userId, q.getModule(), q.getAction(),
                q.getSuccess(), q.getKeyword(), q.getStartTime(), q.getEndTime());
        List<OperationLog> list = total == 0 ? List.of()
                : logMapper.selectByQuery(familyId, userId, q.getModule(), q.getAction(),
                q.getSuccess(), q.getKeyword(), q.getStartTime(), q.getEndTime(),
                offset, q.getPageSize());
        return new PageResult<>(total, list);
    }

    /** 日志按模块的分布，日志页顶部的小统计 */
    public List<Map<String, Object>> moduleStat() {
        Long familyId = UserContext.isAdmin() ? null : UserContext.getFamilyId();
        return logMapper.countByModule(familyId);
    }

    /** 可选的筛选项，前端下拉框用，避免前后端各写一份常量 */
    public Map<String, Object> options() {
        return Map.of(
                "modules", List.of(M_RECORD, M_MEMBER, M_CATEGORY, M_ASSET, M_LOAN, M_IMPORT, M_AUTH, M_ADMIN),
                "actions", List.of(A_ADD, A_UPDATE, A_DELETE, A_BATCH_DELETE, A_IMPORT,
                        A_LOGIN, A_LOGOUT, A_RESET_PWD, A_ENABLE, A_DISABLE)
        );
    }

    /** 供管理员面板复用 */
    public long countAll() {
        return logMapper.countAll();
    }

    public long countFailed() {
        return logMapper.countFailed();
    }

    public List<Map<String, Object>> countByDay(int days) {
        if (days < 1 || days > 90) {
            throw new BizException("天数只能在 1-90 之间");
        }
        return logMapper.countByDay(days);
    }
}
