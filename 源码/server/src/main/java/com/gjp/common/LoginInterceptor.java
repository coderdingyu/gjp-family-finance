package com.gjp.common;

import com.gjp.entity.User;
import com.gjp.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器。除登录/注册接口外，所有接口都必须先登录。
 * 校验通过后把登录用户放进 UserContext，供 Service 层取 familyId 用。
 *
 * 身份不是直接从 session 里取"当前登录人"，而是由请求头 {@code X-Auth-Token}
 * 指明用哪个身份槽位，见 {@link AuthSlots}。这样同一浏览器的多个标签页
 * 可以各自登录不同账号：Cookie 共享，但 token 按标签页隔离。
 *
 * 每次请求都会回库核对账号是否还存在、是否已被禁用：
 * 只认登录时的快照的话，户主禁用成员后，对方已打开的页面还能继续记账。
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = AuthSlots.tokenOf(request);
        UserContext.LoginUser loginUser = AuthSlots.find(request, token);
        if (loginUser == null) {
            writeUnauthorized(response, "未登录或登录已过期");
            return false;
        }
        User db = userMapper.selectById(loginUser.getUserId());
        int liveVer = db == null || db.getSessionVersion() == null ? 0 : db.getSessionVersion();
        int heldVer = loginUser.getSessionVersion() == null ? 0 : loginUser.getSessionVersion();
        if (db == null || (db.getStatus() != null && db.getStatus() == 0) || liveVer != heldVer) {
            // 只踢这个账号的槽位，不能整个 session 作废：
            // 同一浏览器别的标签页可能正登着别的账号（例如管理员），不该被连带踢下线。
            AuthSlots.removeByUser(request, loginUser.getUserId());
            writeUnauthorized(response, "账号已禁用");
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
