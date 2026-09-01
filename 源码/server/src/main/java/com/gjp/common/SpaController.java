package com.gjp.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 单页应用路由转发。
 *
 * 打包部署时前端产物（web/dist）会被放进 jar 的 static 目录，由 Spring Boot 直接对外提供，
 * 一个 jar 就能同时跑前后端，验收演示不需要再装 Node、开两个终端。
 *
 * 但前端用的是 history 模式路由：浏览器直接访问 /record 或刷新页面时，
 * 请求会打到服务端，而服务端没有 /record 这个映射，默认会返回 404。
 * 这里把这些前端路由统一转发到 index.html，交给 vue-router 去解析。
 *
 * 注意：路径是显式列出的，不用 /** 通配，避免把 /api/** 也吞掉。
 * 新增前端页面时记得往下面补一条。
 */
@Controller
public class SpaController {

    @RequestMapping({
            "/",
            "/login",
            "/home",
            "/record",
            "/stat",
            "/analysis",
            "/asset",
            "/member",
            "/category"
    })
    public String index() {
        return "forward:/index.html";
    }
}
