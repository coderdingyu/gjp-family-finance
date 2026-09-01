package com.gjp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 《管家婆─家庭收支管理系统》服务端启动类。
 * 启动后接口根路径为 http://localhost:8080/api
 */
@SpringBootApplication
@MapperScan("com.gjp.mapper")
public class GjpApplication {

    public static void main(String[] args) {
        SpringApplication.run(GjpApplication.class, args);
    }
}
