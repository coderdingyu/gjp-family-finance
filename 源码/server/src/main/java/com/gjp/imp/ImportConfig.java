package com.gjp.imp;

import com.gjp.dify.DifyProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 导入任务用小线程池并行，不同家庭/用户互不排队。
 * 同时最多 3 个任务，避免把本地 Dify 打满。
 * 工作线程不使用 UserContext，只认任务里记下的家庭/成员。
 */
@Configuration
@EnableConfigurationProperties({DifyProperties.class, ImportProperties.class, com.gjp.dify.QwenProperties.class})
public class ImportConfig {

    public static final int POOL_SIZE = 3;
    public static final int LLM_POOL_SIZE = 4;

    @Bean(destroyMethod = "shutdown")
    public ExecutorService importExecutor() {
        AtomicInteger n = new AtomicInteger();
        return Executors.newFixedThreadPool(POOL_SIZE, r -> {
            Thread t = new Thread(r, "gjp-import-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** 智能体调用单独线程池，避免和导入任务互相占满死锁。 */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService llmExecutor() {
        AtomicInteger n = new AtomicInteger();
        return Executors.newFixedThreadPool(LLM_POOL_SIZE, r -> {
            Thread t = new Thread(r, "gjp-llm-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }
}
