package com.gjp.common;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：统一接口前缀交由各 Controller 的 @RequestMapping 声明，
 * 这里只负责注册拦截器和放开开发期跨域。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/logout"
                );
    }

    /**
     * 前端 Vite 开发服务器已配置 /api 代理到 8080，正常情况下同源不涉及跨域；
     * 这里放开是为了方便成员用 Postman 或直接开 5173 以外端口调试。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // 身份靠 X-Auth-Token 头传递，跨源调试时必须放行它，否则请求发不出去
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
