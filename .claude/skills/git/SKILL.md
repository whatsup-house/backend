---
name: git
description: 커밋 메시지, 브랜치명, 작업 흐름 컨벤션
---

# Git 컨벤션

## 커밋 메시지

```
{prefix}: {한국어 설명} ({이슈키})
```

| prefix | 용도 |
|--------|------|
| `feat` | 새 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 구조 개선 |
| `chore` | 설정, 빌드, 의존성 |
| `docs` | 문서만 변경 |
| `test` | 테스트 추가/수정 |

- Jira 이슈가 있으면 `(KAN-XX)` 포함, 없으면 생략 가능
- `WIP`, `update`, `수정` 같은 의미 없는 메시지 금지
- 한국어로 통일, prefix 필수

## 브랜치

```
{타입}/{이슈키}-{도메인약어}-{짧은-설명}
```

- 타입: `feature` | `fix` | `refactor`
- 도메인약어: `auth` `usr` `gth` `app` `loc` `adm`
- 예시: `feature/KAN-43-usr-check-duplicate`, `fix/KAN-50-gth-status-npe`

## 작업 흐름

1. Jira 이슈 확인 → 이슈키 메모
2. 브랜치 생성
3. 작업 단위별 커밋 (한 커밋 = 한 논리적 변경)
4. PR 생성 → 리뷰 → develop 병합

---

## 4. PR base 브랜치

- **PR의 base(target) 브랜치는 항상 `develop`** — `main`으로 직접 PR 금지
