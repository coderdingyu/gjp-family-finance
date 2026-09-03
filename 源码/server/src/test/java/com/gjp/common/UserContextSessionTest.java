package com.gjp.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 改密码后的会话版本同步。
 *
 * 背景：改密码会把 t_user.session_version 加一，拦截器发现当前会话的版本号
 * 对不上就把这个账号踢下线 —— 重置密码的常见原因就是账号疑似被别人拿到，
 * 只换密码不断开旧会话等于没换。但「改自己的密码」不能把操作人自己也踢掉，
 * 所以那几个入口会把新版本号同步回当前会话。
 */
class UserContextSessionTest {

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    private UserContext.LoginUser login(long userId, int sessionVersion) {
        UserContext.LoginUser u = new UserContext.LoginUser();
        u.setUserId(userId);
        u.setUsername("u" + userId);
        u.setFamilyId(1L);
        u.setMemberId(userId);
        u.setRole(Role.MEMBER);
        u.setSessionVersion(sessionVersion);
        UserContext.set(u);
        return u;
    }

    @Test
    void 操作对象是自己时才认作self() {
        login(7L, 0);
        assertTrue(UserContext.isSelf(7L));
        assertFalse(UserContext.isSelf(8L));
        assertFalse(UserContext.isSelf(null));
    }

    @Test
    void 未登录时isSelf一律为假() {
        UserContext.clear();
        assertFalse(UserContext.isSelf(7L));
    }

    @Test
    void 同步版本号后当前会话与库中一致() {
        UserContext.LoginUser me = login(7L, 3);
        UserContext.syncSessionVersion(4);
        assertEquals(4, me.getSessionVersion());
        assertEquals(4, UserContext.get().getSessionVersion());
    }

    /** 未登录时同步不应抛异常，否则登录接口里改密码的分支会炸 */
    @Test
    void 未登录时同步版本号是空操作() {
        UserContext.clear();
        UserContext.syncSessionVersion(9);
        assertNull(UserContext.get());
    }
}
