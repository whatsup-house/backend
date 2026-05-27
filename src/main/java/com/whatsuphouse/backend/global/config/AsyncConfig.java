package com.whatsuphouse.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * @Async 비동기 실행을 활성화하는 설정 클래스.
 *
 * emailTaskExecutor: 이메일 발송 전용 스레드풀.
 * - corePoolSize=2    : 평시 대기 스레드 수
 * - maxPoolSize=10    : 순간 최대 동시 실행 수 (모임 취소 일괄 발송 등 대비)
 * - queueCapacity=100 : 스레드 포화 시 대기 큐 크기
 * - threadNamePrefix  : 로그에서 이메일 스레드 식별용
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "emailTaskExecutor")
    public TaskExecutor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        executor.initialize();
        return executor;
    }
}
