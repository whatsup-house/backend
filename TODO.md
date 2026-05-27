# TODO

## 진행 중 / 보류

### KAN-153 — Application EAV 설계 방향 결정
- 현재 `applyInternal()`이 EAV answers에서 name/phone 추출 후 Application 컬럼에도 저장하는 이중 저장 구조
- Application 엔티티에서 EAV 답변 컬럼(name, phone, gender 등) 제거 여부 결정 필요
- 폼 없는 모임 신청 처리 방식 결정 필요
- 결정 전까지 ApplicationServiceTest 5개 케이스 수정 보류

### KAN-60 — 이메일 알림 서비스 PR 마무리
- 구현/테스트 완료, Jira 완료 처리만 남음

### Resend SMTP 운영 테스트 (내일)
- `MAIL_FROM=onboarding@resend.dev` 환경변수 설정
- prod 프로파일로 bootRun 후 Swagger에서 API 호출
- Resend 대시보드(resend.com → Emails)에서 발송 확인
- 도메인 생기면 DNS 인증 후 MAIL_FROM 환경변수만 교체
