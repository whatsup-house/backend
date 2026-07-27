---
name: jira
description: Jira 이슈 생성 규칙, 이슈 요약 형식, Jira 링크 형식
---

# Jira 컨벤션

## 이슈 생성 규칙

- projectKey: `KAN`, issueTypeName: `Feature`
- assignee: `atlassianUserInfo`로 조회한 현재 사용자 accountId
- Start date(`customfield_10015`): 오늘 날짜 (YYYY-MM-DD)
- **`contentFormat: adf`** 필수 — markdown은 한글 인코딩 깨짐

## 이슈 요약 형식

Jira 이슈 작성 전 아래 형식으로 정리:

```
[설명] [요구사항] [API] [Request] [Response] [DB] [예외]
```

## Jira 링크

```
[{이슈키}](https://whatsuphouse.atlassian.net/browse/{이슈키})
```

## 금지

- markdown contentFormat 사용 금지
- 불명확한 내용 추측 금지 — "문서에 명시 없음"으로 표기
