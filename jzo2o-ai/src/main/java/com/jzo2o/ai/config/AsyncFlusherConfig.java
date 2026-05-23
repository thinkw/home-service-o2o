package com.jzo2o.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * AsyncFlusher 共享线程池配置
 */
@Configuration
public class AsyncFlusherConfig {

    @Bean(name = "asyncFlushScheduler")
    public ScheduledExecutorService asyncFlushScheduler() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "async-flush");
            t.setDaemon(true);
            return t;
        });
    }
}
