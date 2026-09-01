package com.gjp.auth;

import com.gjp.auth.dto.LoginDTO;
import com.gjp.auth.dto.RegisterDTO;
import com.gjp.common.LoginInterceptor;
import com.gjp.common.Result;
import com.gjp.common.UserContext;
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

    @PostMapping("/register")
    public Result<UserContext.LoginUser> register(@Valid @RequestBody RegisterDTO dto, HttpSession session) {
        UserContext.LoginUser user = authService.register(dto);
        session.setAttribute(LoginInterceptor.SESSION_KEY, user);
        return Result.ok(user);
    }

    @PostMapping("/login")
    public Result<UserContext.LoginUser> login(@Valid @RequestBody LoginDTO dto, HttpSession session) {
        UserContext.LoginUser user = authService.login(dto);
        session.setAttribute(LoginInterceptor.SESSION_KEY, user);
        return Result.ok(user);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpSession session) {
        session.invalidate();
        return Result.ok();
    }

    /** 前端刷新页面后用它恢复登录态，未登录时由拦截器直接返回 401 */
    @GetMapping("/current")
    public Result<UserContext.LoginUser> current() {
        return Result.ok(UserContext.get());
    }
}
