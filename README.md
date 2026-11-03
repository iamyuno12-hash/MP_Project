# 찰칵가계부 (SnapLedger)

영수증을 찍으면 **상호명·결제일시·금액·카테고리가 자동으로 기록**되는 안드로이드 가계부 앱
모바일프로그래밍 팀 프로젝트 · 김윤호, 정일현

| 문서 | 위치 |
|---|---|
| 중간 점검 보고서 | [docs/midterm/README.md](docs/midterm/README.md) |
| 기능별 주요 코드 정리 | [SnapLedger_mid/key_code/README.md](SnapLedger_mid/key_code/README.md) |

---

## 주요 기능

- **영수증 촬영 → 자동 입력**: ML Kit 문서 스캐너(테두리 인식·원근 보정) → 이미지 전처리(그림자 제거·대비 보정) → ML Kit 한국어 글자 인식 → 규칙 기반 파서로 상호명·결제일시·합계·사업자번호 추출 → 확인 후 저장
  - 원본·보정 이미지를 둘 다 읽고, 값을 더 많이 찾은 쪽 결과를 사용
- **카테고리 자동 분류**: 사용자 규칙 → 내장 키워드 사전 → 카카오 로컬 API 업종 조회
- **통계**: 월별·연도별 통계와 이전 기간 이동, 지난달 같은 날까지와 비교하는 누적 지출 그래프, 카테고리 도넛 그래프, 연도별 월 지출 막대 그래프
- **지출 관리**: 이번 달 지출·예산 진행률, 최근 내역, 내역 상세(원본 영수증·수정·삭제), 월 예산
- **로그인**: 이메일 회원가입·로그인, Google 로그인 (Firebase Authentication), 자동 로그인(로그인 상태 유지)

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| UI | Jetpack Compose (Material 3), Navigation Compose |
| 구조 | MVVM + Repository, ViewModel + StateFlow |
| 로컬 저장 | Room, DataStore |
| 영수증 인식 | ML Kit Document Scanner, ML Kit Text Recognition v2 (한국어) |
| 네트워크 | Retrofit2 + OkHttp (카카오 로컬 API) |
| 서버 | Firebase Authentication, Cloud Firestore |
| 그래프 | Compose Canvas (누적 비교 선 그래프, 도넛, 막대) |
| 테스트 | JUnit 단위 테스트 34개 (영수증 파서·이미지 보정·통계 계산·자동 로그인) |

## 프로젝트 구조

```
app/src/main/java/com/example/snapledger
├── AppContainer.kt          앱 전체 객체 생성 (간단한 의존성 주입)
├── MainActivity.kt
├── model/                   카테고리 8종
├── data/
│   ├── local/               Room (Expense, CategoryRule, DAO, AppDatabase), DataStore
│   ├── remote/              카카오 로컬 API (Retrofit)
│   ├── firebase/            로그인 (Firebase Auth, Google 로그인)
│   └── repository/          ExpenseRepository, CategoryClassifier
├── ocr/                     이미지 전처리, ML Kit 인식 결과 정리, 영수증 파서
└── ui/                      화면별 Compose + ViewModel
    ├── login/  home/  capture/  confirm/  detail/  stats/  settings/
    └── navigation/  theme/  common/
app/src/test/.../                       단위 테스트 (ReceiptParser, ReceiptImageFilter, StatsCalculator, MonthComparison, StartRoute)
```

## 실행 방법

1. Android Studio에서 이 폴더를 **Open** → Gradle Sync
2. (선택) 카카오 업종 분류: 맨 위 `local.properties`에 `KAKAO_REST_API_KEY=발급받은키` 추가
3. (선택) Firebase 로그인: Firebase 콘솔에서 받은 `google-services.json`을 `app/`에 넣고 `app/build.gradle.kts`의 `google.services` 플러그인 주석 해제
   - 넣지 않으면 **테스트 모드**(아무 이메일로나 로그인)로 동작
4. ▶ 실행 (Android 8.0 이상, 문서 스캐너는 Google Play 서비스 필요)

> `local.properties`, `google-services.json`은 키가 들어 있어 GitHub에 올리지 않는다. 팀원끼리 따로 전달한다.

---

## 협업 규칙

### 브랜치 전략

```
main      ← 제출·발표용 안정 버전. develop을 merge할 때만 바뀐다 (중간 점검, 최종 제출)
 └ develop   ← 개발 통합 브랜치. 기능 브랜치를 여기로 merge
     ├ feature/기능이름   ← 기능 하나당 브랜치 하나 (develop에서 만들어 develop으로 merge)
     ├ fix/버그이름        ← 버그 수정
     └ docs/문서이름       ← 보고서·README
```

| 브랜치 | 만드는 곳 | 합치는 곳 | 예시 |
|---|---|---|---|
| `feature/*` | develop | develop | `feature/receipt-ocr`, `feature/stats-chart` |
| `fix/*` | develop | develop | `fix/total-amount-parsing` |
| `docs/*` | develop | develop | `docs/final-report` |
| `develop` | - | main | 중간 점검·최종 제출 시점 |

- `main`, `develop`에는 직접 commit 하지 않는다.
- merge는 `--no-ff`(또는 GitHub PR의 "Create a merge commit")로 해서 기능 단위 기록을 남긴다.
- 제출 시점의 main에는 태그를 단다: `v0.1-midterm`, `v1.0-final`

### commit 메시지 규칙

```
<타입>(<범위>): <무엇을 했는지, 한 줄>

<필요하면 왜 바꿨는지 / 무엇이 달라졌는지>
```

| 타입 | 언제 | 예시 |
|---|---|---|
| `feat` | 새 기능 | `feat(ocr): 휘어진 영수증에서 한 줄 밀린 결제금액 인식` |
| `fix` | 버그 수정 | `fix(home): 이번 달이 아닌 내역이 목록에 안 보이던 문제 수정` |
| `refactor` | 동작은 같고 구조만 개선 | `refactor(repository): 분류 로직을 CategoryClassifier로 분리` |
| `test` | 테스트 추가·수정 | `test(ocr): 승인번호를 금액으로 읽지 않는지 확인` |
| `docs` | 문서 | `docs(midterm): 중간 점검 보고서 추가` |
| `style` | 화면 디자인·코드 포맷 (동작 변화 없음) | `style(home): 카드 색상을 와이어프레임 색으로 변경` |
| `chore` | 빌드 설정·라이브러리 버전 등 | `chore(build): Firebase BoM 34.19.0으로 고정` |

- 범위(scope) 예: `ocr`, `capture`, `home`, `confirm`, `detail`, `stats`, `settings`, `auth`, `db`, `classify`, `build`
- 한 commit에는 한 가지 변경만 담는다. 제목은 50자 안팎, 마침표 없이.

### 새 기능 작업 순서

```bash
git checkout develop
git pull origin develop                    # 최신 develop 받기
git checkout -b feature/stats-chart        # 기능 브랜치 만들기

# ... 작업 ...
git add app/src/main/java/com/example/snapledger/ui/stats
git commit -m "feat(stats): 카테고리 도넛 차트 추가"

git push -u origin feature/stats-chart     # GitHub에서 develop으로 Pull Request → 팀원 확인 후 merge
```

제출 시점에는 `develop` → `main` Pull Request를 만들어 merge하고 태그를 단다.
