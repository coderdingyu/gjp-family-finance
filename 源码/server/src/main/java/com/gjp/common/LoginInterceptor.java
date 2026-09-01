package com.gjp.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器。除登录/注册接口外，所有接口都必须先登录。
 * 校验通过后把登录用户放进 UserContext，供 Service 层取 familyId 用。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    public static final String SESSION_KEY = "LOGIN_USER";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute(SESSION_KEY);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\",\"data\":null}");
            return false;
        }
        UserContext.set((UserContext.LoginUser) user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // ThreadLocal 必须清理，否则线程复用时会串号
        UserContext.clear();
    }
}
