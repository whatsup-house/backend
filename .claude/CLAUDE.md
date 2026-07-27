# CLAUDE.md

## 서비스
관리자가 소규모 게더링(최대 20명)을 주최, 유저는 신청만 함. 유저 간 매칭 없음.

## 프로젝트
Spring Boot REST API 서버. 도메인 중심 구조.

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
