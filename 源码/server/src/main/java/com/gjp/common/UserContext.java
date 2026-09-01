package com.gjp.common;

/**
 * 当前登录用户上下文。登录拦截器在请求进来时写入，请求结束时清理。
 * 各 Service 通过 UserContext.getFamilyId() 拿到家庭ID，从而做到数据按家庭隔离，
 * 不用每个接口都从前端传 familyId（前端传的不可信）。
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long getUserId() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BizException(401, "未登录");
        }
        return u.getUserId();
    }

    public static Long getFamilyId() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BizException(401, "未登录");
        }
        return u.getFamilyId();
    }

    /** 存在 session 中的登录态，字段刻意保持精简 */
    public static class LoginUser {
        private Long userId;
        private String username;
        private String realName;
        private Long familyId;
        private String familyName;

        public LoginUser() {
        }

        public LoginUser(Long userId, String username, String realName, Long familyId, String familyName) {
            this.userId = userId;
            this.username = username;
            this.realName = realName;
            this.familyId = familyId;
            this.familyName = familyName;
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

        public Long getFamilyId() {
            return familyId;
        }

        public void setFamilyId(Long familyId) {
            this.familyId = familyId;
        }

        public String getFamilyName() {
            return familyName;
        }

        public void setFamilyName(String familyName) {
            this.familyName = familyName;
        }
    }
}
