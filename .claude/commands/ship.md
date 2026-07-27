# /ship

현재 브랜치의 변경사항을 분석해 Jira 이슈를 생성하고, PR을 만든다.
이미 작업이 끝난 코드를 Jira에 역으로 기록하는 용도로 쓴다.

## 사용법

```
/ship
```

인자 없이 실행.

---

## 0단계 — 사전 체크

- Jira MCP: `atlassianUserInfo`로 현재 사용자 조회
- gh CLI: `gh auth status`로 GitHub 인증 확인

하나라도 실패하면 **즉시 중단**하고 보고한다.

---

## 1단계 — 변경사항 파악

```bash
git status
git diff HEAD
git log origin/develop..HEAD --oneline
```

- 변경된 파일 목록, 추가/수정된 기능, API 엔드포인트를 정리한다
- 커밋이 없으면 `git diff --cached`도 확인한다
- 브랜치명에서 이슈키가 있으면 메모해둔다 (예: `feature/KAN-43-...` → `KAN-43`)

---

## 2단계 — Jira 이슈 생성

`.claude/agents/jira-manager.md` 역할을 수행한다.

- 브랜치에 이슈키가 이미 있으면 `getJiraIssue`로 조회해 기존 이슈를 사용
- 이슈키가 없으면 `.claude/rules/jira.md` 규칙에 따라 **새 이슈를 생성**한다
  - 이슈 제목: 변경사항을 한 줄로 요약
  - 이슈 내용: 1단계에서 파악한 변경 내용 기반으로 작성
  - Start date: 오늘 날짜
  - contentFormat: adf 필수
- 이슈 생성 후 상태를 "In Review"(또는 가장 유사한 상태)로 전환

---

## 3단계 — PR 생성

`.claude/agents/pr-creator.md` 역할을 수행한다.

- 스테이징되지 않은 변경사항이 있으면 관련 파일만 명시적으로 `git add`
- 커밋이 없으면 `.claude/rules/git.md` 규칙에 따라 커밋 (이미 커밋된 경우 생략)
- `git push origin {branchName}`
- PR 생성: `--base develop`, 본문에 2단계에서 생성한 Jira 이슈 링크 포함
