package com.whatsuphouse.backend.global.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * @Scheduled 전용 풀(spring.task.scheduling.pool.size, 기본 4). (KAN-406)
 * STOMP 브로커가 messageBrokerTaskScheduler 빈을 만들면 Boot 기본 taskScheduler가 생기지 않아(속성도 무시됨)
 * 스케줄러들이 브로커 풀을 나눠 썼다. 빈으로 두면 Boot가 STOMP 채널 실행기로 골라 가므로 빈이 아니라 여기서만 쓴다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer, DisposableBean {

    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

    @Value("${spring.task.scheduling.pool.size:4}")
    private int poolSize;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
    }
}
