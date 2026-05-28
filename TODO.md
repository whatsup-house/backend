# TODO

### KAN-60 — 이메일 알림 서비스 PR 마무리
- 구현/테스트 완료, Jira 완료 처리만 남음

### Resend SMTP 운영 테스트 (내일)
- `MAIL_FROM=onboarding@resend.dev` 환경변수 설정
- prod 프로파일로 bootRun 후 Swagger에서 API 호출
- Resend 대시보드(resend.com → Emails)에서 발송 확인
- 도메인 생기면 DNS 인증 후 MAIL_FROM 환경변수만 교체
