package com.gjp.common;

/**
 * 当前登录用户上下文。登录拦截器在请求进来时写入，请求结束时清理。
 * 各 Service 通过 UserContext.getFamilyId() 拿到家庭ID，从而做到数据按家庭隔离，
 * 不用每个接口都从前端传 familyId（前端传的不可信）。
 *
 * 权限判断也统一走这里，核心是 {@link #scopeMemberId()}：
 * 它返回 null 表示"能看全家"，返回成员ID表示"只能看这个成员"。
 * 所有查询类 Service 都用它来收敛数据范围，这样权限逻辑只有一处，
 * 不会出现某个接口忘了加限制导致越权。
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

    public static int getRole() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BizException(401, "未登录");
        }
        return u.getRole() == null ? Role.MEMBER : u.getRole();
    }

    /** 是否系统管理员 */
    public static boolean isAdmin() {
        return getRole() == Role.ADMIN;
    }

    /**
     * 是否家庭户主。
     *
     * 注意这里是**严格相等**而不是 role >= OWNER：系统管理员不是任何家庭的户主，
     * 它 family_id = 0、不绑定成员，让它通过户主校验会导致它能往"0 号家庭"里写业务数据，
     * 是一个真实的越权口子。管理员的权限由 {@link #requireAdmin()} 单独把关。
     */
    public static boolean isOwner() {
        return getRole() == Role.OWNER;
    }

    /**
     * 数据可见范围。
     *
     * @return null 表示可以看全家所有成员；非 null 表示只能看这一个成员的数据。
     */
    public static Long scopeMemberId() {
        LoginUser u = HOLDER.get();
        if (u == null) {
            throw new BizException(401, "未登录");
        }
        int role = u.getRole() == null ? Role.MEMBER : u.getRole();
        if (role == Role.ADMIN) {
            // 管理员不属于任何家庭，调业务接口是没有意义的，直接挡住比返回空列表更清晰
            throw new BizException(403, "系统管理员不参与记账，请使用管理员界面");
        }
        if (role == Role.OWNER) {
            return null;
        }
        if (u.getMemberId() == null) {
            // 普通成员没有绑定家庭成员时，宁可什么都看不到，也不能放开成全家可见
            throw new BizException(403, "当前账号未绑定家庭成员，请联系户主处理");
        }
        return u.getMemberId();
    }

    /**
     * 要求是家庭内的角色（普通成员或户主）。
     * 所有业务接口都应该先过这一关，把系统管理员挡在业务数据之外。
     */
    public static void requireFamilyMember() {
        if (isAdmin()) {
            throw new BizException(403, "系统管理员不参与记账，请使用管理员界面");
        }
    }

    /**
     * 把前端请求的成员ID收敛到允许的范围内。
     *
     * 普通成员：无论前端传什么，一律强制成自己，防止改请求参数越权看别人的账。
     * 户主/管理员：按前端传的走，传空表示看全家汇总。
     */
    public static Long resolveMemberId(Long requested) {
        Long scope = scopeMemberId();
        return scope != null ? scope : requested;
    }

    /**
     * 当前登录人绑定的成员ID。户主也强制成自己，不看全家。
     * 个人看板等「只看我」的入口用这个，而不是 {@link #resolveMemberId(Long)}。
     */
    public static Long requireOwnMemberId() {
        requireFamilyMember();
        LoginUser u = get();
        if (u.getMemberId() == null) {
            throw new BizException(403, "当前账号未绑定家庭成员，请联系户主处理");
        }
        return u.getMemberId();
    }

    /** 被操作的账号是不是当前登录人自己 */
    public static boolean isSelf(Long userId) {
        LoginUser u = HOLDER.get();
        return u != null && userId != null && userId.equals(u.getUserId());
    }

    /**
     * 把当前会话的 sessionVersion 同步成库里的最新值。
     * 改自己的密码之后必须调一次，否则下一个请求会被拦截器当成「密码已被别处重置」踢下线。
     */
    public static void syncSessionVersion(Integer version) {
        LoginUser u = HOLDER.get();
        if (u != null) {
            u.setSessionVersion(version);
        }
    }

    /** 要求户主及以上权限，否则抛 403 */
    public static void requireOwner() {
        if (!isOwner()) {
            throw new BizException(403, "该操作仅户主可用");
        }
    }

    /** 要求系统管理员权限，否则抛 403 */
    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new BizException(403, "该操作仅系统管理员可用");
        }
    }

    /** 存在 session 中的登录态，字段刻意保持精简 */
    public static class LoginUser {
        private Long userId;
        private String username;
        private String realName;
        private Long familyId;
        private String familyName;
        /** 绑定的家庭成员ID，普通成员的数据隔离依据 */
        private Long memberId;
        /** 绑定成员姓名，前端顶栏显示用 */
        private String memberName;
        /** 角色：0=普通成员 1=户主 2=系统管理员 */
        private Integer role;
        /** 角色中文名，前端直接显示 */
        private String roleName;
        /** 登录时的 sessionVersion，拦截器与库中当前值比较 */
        private Integer sessionVersion;

        public LoginUser() {
        }

        public LoginUser(Long userId, String username, String realName, Long familyId, String familyName) {
            this.userId = userId;
            this.username = username;
            this.realName = realName;
            this.familyId = familyId;
            this.familyName = familyName;
        }

        public Long getMemberId() {
            return memberId;
        }

        public void setMemberId(Long memberId) {
            this.memberId = memberId;
        }

        public String getMemberName() {
            return memberName;
        }

        public void setMemberName(String memberName) {
            this.memberName = memberName;
        }

        public Integer getRole() {
            return role;
        }

        public void setRole(Integer role) {
            this.role = role;
            this.roleName = Role.name(role);
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
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

        public Integer getSessionVersion() {
            return sessionVersion;
        }

        public void setSessionVersion(Integer sessionVersion) {
            this.sessionVersion = sessionVersion;
        }
    }
}
