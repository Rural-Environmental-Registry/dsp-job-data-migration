package br.car.dsp_batch.reconciliation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(ReconciliationProperties.class)
public class ReconciliationConfiguration {

    @Bean(name = "reconciliationTaskExecutor")
    public TaskExecutor reconciliationTaskExecutor(ReconciliationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getThreadPoolSize());
        executor.setMaxPoolSize(properties.getThreadPoolSize());
        executor.setQueueCapacity(properties.getThreadPoolSize() * 2);
        executor.setThreadNamePrefix("reconciliation-");
        executor.initialize();
        return executor;
    }
}
