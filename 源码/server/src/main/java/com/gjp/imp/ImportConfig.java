package com.gjp.imp;

import com.gjp.dify.DifyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 导入任务单线程排队，避免多文件同时打满本地 Dify。
 * 工作线程不使用 UserContext（请求结束就会被拦截器清掉），只认任务里记下的家庭/成员。
 */
@Configuration
@EnableConfigurationProperties({DifyProperties.class, ImportProperties.class})
public class ImportConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService importExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "gjp-import");
            t.setDaemon(true);
            return t;
        });
    }
}
