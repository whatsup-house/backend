# /implement

구글 드라이브 기획 문서를 읽고, Jira 이슈를 생성하고, 코드를 구현하고, 검토 후 완료 처리까지 자동으로 수행한다.

## 사용법

```
/implement {구현할 기능 설명}
```

---

## 0단계 — 사전 연결 체크

작업 시작 전, 아래 세 가지 연결 상태를 먼저 확인한다:
- Google Drive MCP: `search_files`로 테스트 조회
- Jira MCP: `atlassianUserInfo`로 현재 사용자 조회
- gh CLI: `gh auth status`로 GitHub 인증 확인

⚠️ 하나라도 실패하면 **즉시 작업을 중단**하고 보고한다. 이후 단계는 절대 진행하지 않는다.

---

## 1단계 — 문서 읽기

`.claude/agent/spec-reader.md` 역할을 수행한다.

---

## 2단계 — Jira 이슈 생성 + 브랜치 생성

`.claude/agent/jira-manager.md` 역할을 수행한다. (이슈 생성 + 브랜치 생성)

---

## 3단계 — 코드 구현

`.claude/agent/api-creator.md` 역할을 수행한다.

- 구현 전, 동일/인접 도메인의 기존 코드를 읽어 스타일을 파악한다
  - **Controller, Service, Repository, DTO 각 1개 파일씩만** 읽는다
  - 파일이 길면 상단 60줄만 읽는다

---

## 4단계 — 코드 검토

`.claude/agent/backend-reviewer.md` 역할을 수행한다. 문제가 있으면 즉시 수정한다.

---

## 5단계 — Jira 완료 처리

`.claude/agent/jira-manager.md` 역할을 수행한다. (검토 결과 코멘트 추가 + 상태 완료 전환)

---

## 6단계 — 커밋 & PR

`.claude/agent/pr-creator.md` 역할을 수행한다.
