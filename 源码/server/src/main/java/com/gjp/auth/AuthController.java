package com.gjp.auth;

import com.gjp.auth.dto.LoginDTO;
import com.gjp.auth.dto.LoginResultVO;
import com.gjp.auth.dto.RegisterDTO;
import com.gjp.common.AuthSlots;
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
 *
 * 登录成功返回一个 token，代表"这个标签页的身份"，多账号并行登录靠它，
 * 具体机制见 {@link AuthSlots}。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;
    @Autowired
    private OperationLogService logService;

    @PostMapping("/register")
    public Result<LoginResultVO> register(@Valid @RequestBody RegisterDTO dto,
                                          HttpServletRequest request) {
        UserContext.LoginUser user = authService.register(dto);
        return Result.ok(new LoginResultVO(openSlot(request, user), user));
    }

    @PostMapping("/login")
    public Result<LoginResultVO> login(@Valid @RequestBody LoginDTO dto,
                                       HttpServletRequest request) {
        // 必须先完成账号密码与禁用状态校验，再动 session。
        // 这样登录失败不会影响本浏览器已经登录的其他标签页。
        UserContext.LoginUser user = authService.login(dto);
        return Result.ok(new LoginResultVO(openSlot(request, user), user));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        // 退出接口没有经过登录拦截器，UserContext 是空的，
        // 操作人信息只能从槽位里取，取完再退掉这一个槽位。
        UserContext.LoginUser u = AuthSlots.remove(request, AuthSlots.tokenOf(request));
        if (u == null) {
            return Result.ok();
        }
        logService.recordAuth(OperationLogService.A_LOGOUT, u.getUserId(), u.getUsername(),
                u.getRealName(), u.getFamilyId(), "账号 " + u.getUsername() + " 退出登录", true, null);
        // 只有本浏览器最后一个身份也退了，才把 session 整个作废。
        // 否则还登着别的账号的标签页会被一起踢下线。
        if (AuthSlots.count(request) == 0) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        return Result.ok();
    }

    /** 前端刷新页面后用它恢复登录态，未登录时由拦截器直接返回 401 */
    @GetMapping("/current")
    public Result<UserContext.LoginUser> current() {
        return Result.ok(UserContext.get());
    }

    /**
     * 记住这次登录并发一个 token 回去。
     *
     * Session ID 只在"本浏览器此前没有任何账号在线"时轮换：
     * 轮换是为了防 Session 固定攻击，但 Cookie 是整个浏览器共用的，
     * 已经登录着的标签页正好赶上轮换会带着旧 ID 请求一次、被误判成掉线。
     * 此前没有身份在线时不存在这个顾虑，防护也正是在这一刻才有意义。
     */
    private String openSlot(HttpServletRequest request, UserContext.LoginUser user) {
        boolean firstInBrowser = AuthSlots.count(request) == 0;
        request.getSession();
        if (firstInBrowser) {
            request.changeSessionId();
        }
        return AuthSlots.issue(request, user);
    }
}
