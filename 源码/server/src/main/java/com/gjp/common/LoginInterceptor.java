package com.gjp.common;

import com.gjp.entity.User;
import com.gjp.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器。除登录/注册接口外，所有接口都必须先登录。
 * 校验通过后把登录用户放进 UserContext，供 Service 层取 familyId 用。
 *
 * 每次请求都会回库核对账号是否还存在、是否已被禁用：
 * 只认 session 的话，户主禁用成员后，对方已打开的页面还能继续记账。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY = "LOGIN_USER";

    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute(SESSION_KEY);
        if (user == null) {
            writeUnauthorized(response, "未登录或登录已过期");
            return false;
        }
        UserContext.LoginUser loginUser = (UserContext.LoginUser) user;
        User db = userMapper.selectById(loginUser.getUserId());
        if (db == null || (db.getStatus() != null && db.getStatus() == 0)) {
            session.invalidate();
            writeUnauthorized(response, "账号已被禁用或已失效");
            return false;
        }
        UserContext.set(loginUser);
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // ThreadLocal 必须清理，否则线程复用时会串号
        UserContext.clear();
    }
}
