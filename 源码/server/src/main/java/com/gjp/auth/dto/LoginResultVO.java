package com.gjp.auth.dto;

import com.gjp.common.UserContext;

/**
 * 登录/注册的返回体。
 *
 * 除了登录人本身，还要把 token 交给前端：它标识"这个标签页是哪个身份"，
 * 前端存在 sessionStorage 里，后续请求放到 X-Auth-Token 头上。
 * 见 {@link com.gjp.common.AuthSlots}。
 */
public class LoginResultVO {

    /** 本标签页的身份令牌 */
    private String token;
    /** 登录人信息，字段与 /auth/current 一致 */
    private UserContext.LoginUser user;

    public LoginResultVO() {
    }

    public LoginResultVO(String token, UserContext.LoginUser user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserContext.LoginUser getUser() {
        return user;
    }

    public void setUser(UserContext.LoginUser user) {
        this.user = user;
    }
}
