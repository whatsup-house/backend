# Jira 컨벤션

이 문서는 Jira 이슈 생성, 업데이트, 상태 전환 시 따라야 할 규칙을 정의한다.

---

## 1. 이슈 생성 규칙

- projectKey: `KAN`, issueTypeName: `Feature`
- assignee: `atlassianUserInfo`로 조회한 현재 사용자 accountId
- Start date(`customfield_10015`): 오늘 날짜 (YYYY-MM-DD)
- **`contentFormat: adf`** 필수 — markdown은 한글 인코딩 깨짐

---

## 2. ADF 템플릿

```json
{
  "version": 1,
  "type": "doc",
  "content": [
    {
      "type": "heading", "attrs": { "level": 2 },
      "content": [{ "type": "text", "text": "설명" }]
    },
    {
      "type": "paragraph",
      "content": [{ "type": "text", "text": "{한 줄 설명}" }]
    },
    {
      "type": "heading", "attrs": { "level": 2 },
      "content": [{ "type": "text", "text": "요구사항" }]
    },
    {
      "type": "bulletList",
      "content": [
        { "type": "listItem", "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "{요구사항 항목}" }] }] }
      ]
    },
    {
      "type": "heading", "attrs": { "level": 2 },
      "content": [{ "type": "text", "text": "API" }]
    },
    {
      "type": "paragraph",
      "content": [{ "type": "text", "text": "{METHOD /api/path}", "marks": [{ "type": "code" }] }]
    },
    {
      "type": "heading", "attrs": { "level": 2 },
      "content": [{ "type": "text", "text": "Request" }]
    },
    {
      "type": "paragraph",
      "content": [{ "type": "text", "text": "{요청 파라미터 설명}" }]
    },
    {
      "type": "heading", "attrs": { "level": 2 },
      "content": [{ "type": "text", "text": "Response" }]
    },
    {
      "type": "codeBlock", "attrs": { "language": "json" },
      "content": [{ "type": "text", "text": "{응답 JSON 예시}" }]
    },
    {
      "type": "heading", "attrs": { "level": 2 },
      "content": [{ "type": "text", "text": "DB" }]
    },
    {
      "type": "paragraph",
      "content": [{ "type": "text", "text": "{관련 테이블 및 조건}" }]
    },
    {
      "type": "heading", "attrs": { "level": 2 },
      "content": [{ "type": "text", "text": "예외" }]
    },
    {
      "type": "table",
      "attrs": { "isNumberColumnEnabled": false, "layout": "default" },
      "content": [
        {
          "type": "tableRow",
          "content": [
            { "type": "tableHeader", "attrs": {}, "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "코드" }] }] },
            { "type": "tableHeader", "attrs": {}, "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "설명" }] }] }
          ]
        },
        {
          "type": "tableRow",
          "content": [
            { "type": "tableCell", "attrs": {}, "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "{HTTP 상태코드}" }] }] },
            { "type": "tableCell", "attrs": {}, "content": [{ "type": "paragraph", "content": [{ "type": "text", "text": "{에러 설명}" }] }] }
          ]
        }
      ]
    }
  ]
}
```

---

## 3. 이슈 요약 형식

Jira 이슈 작성 전 아래 형식으로 정리:

```
[설명] [요구사항] [API] [Request] [Response] [DB] [예외]
```

---

## 4. Jira 링크

```
[{이슈키}](https://whatsuphouse.atlassian.net/browse/{이슈키})
```

---

## 5. 금지

- markdown contentFormat 사용 금지
- 불명확한 내용 추측 금지 — "문서에 명시 없음"으로 표기
