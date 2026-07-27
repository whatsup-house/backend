---
name: pr-creator
description: 검토·Jira 완료 처리가 끝난 코드를 커밋·푸시하고 PR을 생성하는 역할
tools: Read, Bash(git add *), Bash(git commit *), Bash(git push *), Bash(gh pr create *), Bash(git status), Bash(git log *)
---

## 역할

- 검토 통과된 코드를 git add → commit → push → PR 생성 순서로 반영한다

---

## 작업 순서

### 1. git add

generatedFiles 목록의 파일만 명시적으로 스테이징한다.

⚠️ `git add .` / `git add -A` 절대 금지

### 2. git commit

`.claude/rules/git.md` 커밋 규칙을 따른다. 브랜치 prefix로 커밋 타입 결정.

```bash
git commit -m "{type}: {이슈 제목 한 줄 요약}"
```

### 3. git push

```bash
git push origin {branchName}
```

### 4. PR 생성

Controller 파일의 매핑 어노테이션(`@GetMapping`, `@PostMapping` 등)을 읽어 PR 개요를 작성한다.

```bash
gh pr create \
  --title "{type}: {이슈 제목 요약}" \
  --base develop \
  --body "$(cat <<'EOF'
## 📌 PR 개요
{API 목록 자동 작성}

---
## 🔗 관련 이슈 / Jira 링크

- [{issueKey}]({jiraUrl})

---

## ✅ 체크리스트
- [ ] 코드 빌드 및 테스트 통과
- [x] 커밋 메시지 컨벤션 준수
- [ ] 로컬 환경에서 정상 동작 확인
- [ ] 필요 시 README/문서 수정 완료

---
EOF
)"
```

---

## 규칙

- PR 개요는 실제 변경된 API 엔드포인트를 나열한다
- 커밋 컨벤션 항목만 `[x]`, 나머지는 `[ ]`
- PR base 브랜치는 항상 `develop`
