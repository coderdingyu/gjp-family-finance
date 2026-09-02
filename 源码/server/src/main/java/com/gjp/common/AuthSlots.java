package com.gjp.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多账号并行登录的身份槽位。
 *
 * 为什么不能只靠 session：JSESSIONID 是 Cookie，同一浏览器所有标签页共用一份，
 * 于是"当前登录人"在整个浏览器里只能有一个，切号必然互相顶掉。
 *
 * 这里的做法是把身份从 Cookie 上解耦：一个 session 里挂一张 token -> 登录人 的表，
 * 每个标签页登录时领一个自己的 token（前端存 sessionStorage，天生按标签页隔离），
 * 之后每个请求用 {@code X-Auth-Token} 头指明"我是哪个身份"。
 * 这样同一浏览器的多个标签页可以各自登录不同账号，互不干扰。
 *
 * 仍然沿用 session 承载这张表，是为了直接复用 Tomcat 的会话超时与回收，
 * 不用另建一套 token 存储和过期清理。副作用是整个 session 超时后所有槽位一起失效，
 * 对家用系统来说可以接受。
 */
public final class AuthSlots {

    /** session 里存放 token -> 登录人 的属性名 */
    public static final String SESSION_KEY = "LOGIN_USERS";

    /** 前端指明身份用的请求头 */
    public static final String TOKEN_HEADER = "X-Auth-Token";

    /**
     * 单个浏览器最多同时登录几个账号。
     * 设上限是因为这张表挂在 session 上，不限量的话反复调登录接口就能把它撑大。
     * 超出后按登录先后淘汰最早的那个。
     */
    private static final int MAX_SLOTS = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AuthSlots() {
    }

    /**
     * 给一次成功的登录分配 token 并记住身份。
     * token 由服务端生成，不接受前端指定，避免前端塞一个已知值进来冒用别的槽位。
     */
    public static String issue(HttpServletRequest request, UserContext.LoginUser user) {
        HttpSession session = request.getSession();
        Map<String, UserContext.LoginUser> slots = slots(session, true);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        synchronized (slots) {
            while (slots.size() >= MAX_SLOTS) {
                Iterator<String> it = slots.keySet().iterator();
                it.next();
                it.remove();
            }
            slots.put(token, user);
        }
        // 属性内容变了要重新 set 一次，分布式 session 实现靠这个感知改动
        session.setAttribute(SESSION_KEY, slots);
        return token;
    }

    /** 按 token 找登录人，找不到返回 null */
    public static UserContext.LoginUser find(HttpServletRequest request, String token) {
        if (token == null || token.isBlank()) return null;
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Map<String, UserContext.LoginUser> slots = slots(session, false);
        if (slots == null) return null;
        synchronized (slots) {
            return slots.get(token);
        }
    }

    /** 从请求头里取 token */
    public static String tokenOf(HttpServletRequest request) {
        return request.getHeader(TOKEN_HEADER);
    }

    /** 退掉一个槽位，返回它原来的登录人（用于记退出日志） */
    public static UserContext.LoginUser remove(HttpServletRequest request, String token) {
        if (token == null || token.isBlank()) return null;
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Map<String, UserContext.LoginUser> slots = slots(session, false);
        if (slots == null) return null;
        UserContext.LoginUser removed;
        synchronized (slots) {
            removed = slots.remove(token);
        }
        session.setAttribute(SESSION_KEY, slots);
        return removed;
    }

    /**
     * 踢掉某个账号的所有槽位。
     * 账号被禁用时用：同一个号可能在多个标签页开着，只清当前这一个不够。
     */
    public static void removeByUser(HttpServletRequest request, Long userId) {
        if (userId == null) return;
        HttpSession session = request.getSession(false);
        if (session == null) return;
        Map<String, UserContext.LoginUser> slots = slots(session, false);
        if (slots == null) return;
        synchronized (slots) {
            List<String> dead = new ArrayList<>();
            for (Map.Entry<String, UserContext.LoginUser> e : slots.entrySet()) {
                if (userId.equals(e.getValue().getUserId())) dead.add(e.getKey());
            }
            dead.forEach(slots::remove);
        }
        session.setAttribute(SESSION_KEY, slots);
    }

    /** 当前 session 里还有几个身份在线 */
    public static int count(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return 0;
        Map<String, UserContext.LoginUser> slots = slots(session, false);
        if (slots == null) return 0;
        synchronized (slots) {
            return slots.size();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, UserContext.LoginUser> slots(HttpSession session, boolean create) {
        Object attr = session.getAttribute(SESSION_KEY);
        if (attr instanceof Map) {
            return (Map<String, UserContext.LoginUser>) attr;
        }
        if (!create) return null;
        // LinkedHashMap 保留登录先后顺序，超出上限时才知道该淘汰谁
        Map<String, UserContext.LoginUser> created = new LinkedHashMap<>();
        session.setAttribute(SESSION_KEY, created);
        return created;
    }
}
