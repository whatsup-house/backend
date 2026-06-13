package com.whatsuphouse.backend.domain.mailtemplate.service;

/**
 * 메일 제목/본문 한 쌍. resolve()는 원본(미치환), render()는 변수 치환된 결과를 담는다.
 */
public record MailContent(String subject, String body) {
}
