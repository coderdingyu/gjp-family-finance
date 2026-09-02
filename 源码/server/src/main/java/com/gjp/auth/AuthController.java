package com.gjp.auth;

import com.gjp.auth.dto.LoginDTO;
import com.gjp.auth.dto.RegisterDTO;
import com.gjp.common.LoginInterceptor;
import com.gjp.common.Result;
import com.gjp.common.UserContext;
import com.gjp.log.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录注册接口。这几个接口在 WebConfig 中被排除在登录拦截之外。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;
    @Autowired
    private OperationLogService logService;

    @PostMapping("/register")
    public Result<UserContext.LoginUser> register(@Valid @RequestBody RegisterDTO dto,
                                                  HttpServletRequest request) {
        UserContext.LoginUser user = authService.register(dto);
        HttpSession session = request.getSession();
        request.changeSessionId();
        session.setAttribute(LoginInterceptor.SESSION_KEY, user);
        return Result.ok(user);
    }

    @PostMapping("/login")
    public Result<UserContext.LoginUser> login(@Valid @RequestBody LoginDTO dto,
                                               HttpServletRequest request) {
        // 必须先完成账号密码与禁用状态校验，再轮换 Session ID。
        // 这样登录失败不会破坏当前身份，登录成功也不会沿用可能被固定的旧 ID。
        UserContext.LoginUser user = authService.login(dto);
        HttpSession session = request.getSession();
        request.changeSessionId();
        session.setAttribute(LoginInterceptor.SESSION_KEY, user);
        return Result.ok(user);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        // 退出接口没有经过登录拦截器，所以 UserContext 是空的，
        // 操作人信息只能从 session 里取，取完再销毁 session
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Result.ok();
        }
        Object attr = session.getAttribute(LoginInterceptor.SESSION_KEY);
        if (attr instanceof UserContext.LoginUser u) {
            logService.recordAuth(OperationLogService.A_LOGOUT, u.getUserId(), u.getUsername(),
                    u.getRealName(), u.getFamilyId(), "账号 " + u.getUsername() + " 退出登录", true, null);
        }
        session.invalidate();
        return Result.ok();
    }

    /** 前端刷新页面后用它恢复登录态，未登录时由拦截器直接返回 401 */
    @GetMapping("/current")
    public Result<UserContext.LoginUser> current() {
        return Result.ok(UserContext.get());
    }
}
