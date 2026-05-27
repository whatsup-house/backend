package com.whatsuphouse.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @Async 비동기 실행을 활성화하는 설정 클래스.
 *
 * EmailNotificationService의 모든 발송 메서드는 @Async로 실행됩니다.
 * 별도 스레드풀을 지정하지 않으면 Spring의 기본 SimpleAsyncTaskExecutor를 사용합니다.
 * 운영 환경에서 발송량이 많아지면 ThreadPoolTaskExecutor 빈을 등록해
 * 스레드 수와 큐 용량을 조정하는 것을 권장합니다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
