# TODO

### KAN-141 — 네이밍/컨벤션 정리 및 Swagger 개선
작업 순서: 도메인별 순차 진행

**1단계 — 내가 작업한 도메인 (맥락 파악 완료)**
- [ ] auth
- [ ] user
- [ ] gathering
- [ ] application
- [ ] notification

**2단계 — 협업 도메인 (파악하면서 같이 결정)**
- [ ] home
- [ ] review
- [ ] mileage
- [ ] location

**각 도메인 체크 항목**
- 변수명/메서드명 컨벤션 불일치
- API 경로 일관성 (REST 규칙)
- @Tag, @Operation summary/description 누락 또는 불일치
- Swagger 태그 그룹명 통일 (기능별 정렬 config로 지정)
- DTO 필드명 일관성
