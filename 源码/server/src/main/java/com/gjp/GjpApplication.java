package com.gjp;

import com.gjp.common.AppTime;
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
        // 必须在 Spring 起来之前：数据源、日志、各处 now() 都会读 JVM 默认时区，
        // 云主机默认 UTC 时不钉死，业务口径的「今天」会比北京时间少一天。
        AppTime.pinDefaultZone();
        SpringApplication.run(GjpApplication.class, args);
    }
}
