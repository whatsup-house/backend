# CLAUDE.md

## 서비스
관리자가 소규모 게더링(최대 20명)을 주최, 유저는 신청만 함. 유저 간 개인 매칭 없음.
(matching 도메인은 우연한 식탁의 관리자용 테이블 배정 기능이다.)
우연한 식탁(RANDOM_TABLE)과 이용권 구매는 회원 전용이다. 일반 게더링은 비회원 신청 가능.

## 프로젝트
Spring Boot REST API 서버. 도메인 중심 구조.

## 도메인 규칙
- 타 도메인의 Repository를 직접 주입하지 않는다. 타 도메인 접근은 해당 도메인의 Service 또는 도메인 이벤트를 통한다. (기존 위반 코드는 점진 정리 중)
- 이용권 잔여 수(remainingCount)를 변경하는 코드는 반드시 TicketTransaction 기록을 함께 남긴다.
- 시스템 예약 질문 키(name/phone/email)는 `SystemQuestionKey` enum만 사용한다. 문자열 리터럴 금지.
- 정원 검사(count→confirm)는 `GatheringRepository.findByIdAndDeletedAtIsNullForUpdate`로 게더링 행을 잠근 뒤 수행한다.

## 명령어
```bash
./gradlew bootRun | build | clean build | test
./gradlew test --tests "com.whatsuphouse.backend.SomeTest"
```
Swagger: `/swagger-ui/index.html`

## 구조
```
domain/{name}/controller, service, repository, entity, dto
global/config, auth, exception, common
```

## Git
커밋 메시지와 PR에 Claude, AI, 자동화 도구 관련 내용 일체 언급 금지. `Co-Authored-By` 포함 금지.
