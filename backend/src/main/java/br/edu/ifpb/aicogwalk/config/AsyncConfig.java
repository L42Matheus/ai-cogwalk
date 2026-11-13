package br.edu.ifpb.aicogwalk.config;

import org.springframework.core.task.TaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

    /** Uma execução por vez: um navegador, ritmo controlado sobre o site avaliado. */
    @Bean(name = "runExecutor")
    public TaskExecutor runExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("acw-run-");
        ex.initialize();
        return ex;
    }
}
