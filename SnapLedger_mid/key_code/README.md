# 찰칵가계부 · 주요 코드 (중간 점검)

프로젝트 전체 코드 중 **기능별 핵심 파일**만 골라 모은 폴더입니다.
원본 위치는 `app/src/main/java/com/example/snapledger/` 이고, 아래 파일들은 2026-10-30 기준 복사본입니다.

```
key_code/
├── 01_receipt_scan_ocr/          핵심 기능 1 · 영수증 스캔 → 글자 인식 → 값 추출
├── 02_category_classification/   핵심 기능 2 · 카테고리 자동 분류
├── 03_data_room_repository/      데이터 저장 (Room, Repository, DataStore)
├── 04_screens_viewmodel/         화면과 ViewModel (홈, 확인, 상세, 통계, 내비게이션)
├── 05_login_auth/                로그인·회원가입 (Firebase Auth, Google 로그인)
└── 06_tests/                     영수증 파서 단위 테스트
```

**전체 구조 (MVVM + Repository)**

```
Compose 화면 ──▶ ViewModel ──▶ Repository ──▶ Room (기기 DB)
                                          ├─▶ Firebase Auth / Firestore
                                          └─▶ Retrofit (카카오 로컬 API)
영수증 인식:  문서 스캐너 ──▶ ML Kit 글자 인식 ──▶ 행 합치기 ──▶ ReceiptParser
```

---

## 01. 영수증 스캔 · 인식 · 값 추출 (핵심 기능 1)

| 파일 | 핵심 부분 | 하는 일 |
|---|---|---|
| [CaptureScreen.kt](01_receipt_scan_ocr/CaptureScreen.kt) | `CaptureViewModel.recognize()` [L68](01_receipt_scan_ocr/CaptureScreen.kt#L68), `CaptureScreen()` [L116](01_receipt_scan_ocr/CaptureScreen.kt#L116) | ML Kit 문서 스캐너로 촬영(테두리 인식·원근 보정) → 원본 저장 → 인식 → 파싱 → 확인 화면 |
| [ReceiptTextRecognizer.kt](01_receipt_scan_ocr/ReceiptTextRecognizer.kt) | `Text.toRows()` [L39](01_receipt_scan_ocr/ReceiptTextRecognizer.kt#L39) | ML Kit 한국어 인식 결과에서 같은 높이의 글자를 한 행으로 합치고 글자 높이를 기록 |
| [ReceiptParser.kt](01_receipt_scan_ocr/ReceiptParser.kt) | `findTotal()` [L156](01_receipt_scan_ocr/ReceiptParser.kt#L156), `findStoreName()` [L211](01_receipt_scan_ocr/ReceiptParser.kt#L211), `findDateTime()` [L93](01_receipt_scan_ocr/ReceiptParser.kt#L93), `findBusinessNumber()` [L65](01_receipt_scan_ocr/ReceiptParser.kt#L65) | 규칙 기반으로 합계·상호명·결제일시·사업자번호 추출 |

**인식 흐름** — 스캔한 영수증을 저장하고, 읽은 행을 파서에 넘겨 확인 화면용 초안을 만든다.

```kotlin
fun recognize(imageUri: Uri, onDone: () -> Unit) {
    viewModelScope.launch {
        val saved = withContext(Dispatchers.IO) { copyToAppStorage(imageUri) }   // 영수증 보관함용 원본
        val text = container.receiptRecognizer.recognize(Uri.fromFile(saved))   // ML Kit (기기 안에서 처리)
        val rows = text.toRows()                                                // 같은 높이 글자 → 한 행
        container.pendingDraft = ReceiptDraft(
            imagePath = saved.absolutePath,
            parsed = container.receiptParser.parseRows(rows),                  // 값 추출
            recognizedLines = rows.map { it.text },
        )
        onDone()                                                                // 확인 화면으로
    }
}
```

**합계 금액 찾기** — 승인번호(8자리)를 금액으로 읽던 문제와, 휘어진 영수증에서 값이 한 줄 밀리는 문제를 해결한 부분.

```kotlin
fun findTotal(lines: List<String>): Long? {
    // 1) '합계/결제금액' 줄의 금액. 같은 줄에 없으면 바로 아래 → 바로 위 줄 (금액만 있는 줄일 때만)
    val fromKeyword = lines.withIndex().mapNotNull { (index, line) ->
        if (!containsAny(line, totalKeywords) || containsAny(line, notTotalKeywords)) return@mapNotNull null
        amountsIn(line, allowBare = true).lastOrNull()
            ?: listOf(index + 1, index - 1)
                .mapNotNull { lines.getOrNull(it) }
                .firstOrNull { isAmountOnly(it) }
                ?.let { amountsIn(it, allowBare = true).lastOrNull() }
    }
    fromKeyword.maxOrNull()?.let { return it }

    // 2) 키워드가 없으면 '4,800' / '4800원' 형태의 금액 중 최댓값 (승인번호 같은 맨 숫자는 제외)
    return amountCandidates(lines).maxOrNull()
}
```

**사업자등록번호 검증** — 하이픈 없이 찍힌 10자리 숫자도 국세청 검증 공식으로 확인한다.

```kotlin
private fun isValidBusinessNumber(d: String): Boolean {
    if (d.length != 10 || !d.all { it.isDigit() }) return false
    val weights = intArrayOf(1, 3, 7, 1, 3, 7, 1, 3, 5)
    var sum = (0 until 9).sumOf { (d[it] - '0') * weights[it] }
    sum += (d[8] - '0') * 5 / 10
    return (10 - sum % 10) % 10 == d[9] - '0'
}
```

**상호명 찾기** (`findStoreName()`)는 두 단계로 동작한다.
1. '상호' 칸이 있으면 같은 줄과 바로 위·아래 줄을 후보로 비교한다. 영수증에 두 번 나오는 이름은 가산하고, '대표' 칸 근처의 사람 이름은 감점한다.
2. '상호' 칸이 없으면 위쪽 8줄에 점수를 매긴다. 위치, 글자 크기, "…점·편의점·카페" 같은 표현, 사업자번호 바로 위인지를 본다.

---

## 02. 카테고리 자동 분류 (핵심 기능 2)

| 파일 | 핵심 부분 | 하는 일 |
|---|---|---|
| [CategoryClassifier.kt](02_category_classification/CategoryClassifier.kt) | `classify()` [L19](02_category_classification/CategoryClassifier.kt#L19), `remember()` [L47](02_category_classification/CategoryClassifier.kt#L47), `fromKakaoGroupCode()` [L58](02_category_classification/CategoryClassifier.kt#L58), `KeywordDictionary` [L71](02_category_classification/CategoryClassifier.kt#L71) | 사용자 규칙 → 키워드 사전 → 카카오 API → 기타 순으로 분류, 사용자가 바꾼 분류는 규칙으로 기억 |
| [KakaoLocalApi.kt](02_category_classification/KakaoLocalApi.kt) | `KakaoLocalApi` [L12](02_category_classification/KakaoLocalApi.kt#L12) | Retrofit으로 카카오 로컬 '키워드로 장소 검색' 호출 |
| [Category.kt](02_category_classification/Category.kt) | `enum class Category` | 카테고리 8종 |

```kotlin
suspend fun classify(storeName: String, businessNumber: String?): Category {
    // 1) 사용자 규칙: 예전에 사용자가 고친 가게면 그대로
    ruleKeys(storeName, businessNumber).forEach { key -> ruleDao.find(key)?.let { return it.category } }
    // 2) 내장 키워드 사전: GS25 → 편의점 및 마트, 스타벅스 → 카페 및 디저트 …
    KeywordDictionary.match(storeName)?.let { return it }
    // 3) 카카오 로컬 API: 업종 코드(FD6 음식점, CE7 카페 …) → 앱 카테고리
    if (kakaoEnabled) {
        val place = kakaoApi.searchKeyword(storeName).documents.firstOrNull()
        place?.let { fromKakaoGroupCode(it.categoryGroupCode) }?.let { return it }
    }
    // 4) 미분류
    return Category.ETC
}
```
(실제 코드에는 카카오 API 실패 시 건너뛰는 예외 처리가 들어 있다.)

---

## 03. 데이터 저장 (Room · Repository)

| 파일 | 핵심 부분 | 하는 일 |
|---|---|---|
| [Expense.kt](03_data_room_repository/Expense.kt) | `data class Expense` [L12](03_data_room_repository/Expense.kt#L12), `CategoryRule` | 지출 내역 엔티티, 사용자 분류 규칙 엔티티 |
| [Daos.kt](03_data_room_repository/Daos.kt) | `ExpenseDao` [L9](03_data_room_repository/Daos.kt#L9), `countSame()` [L35](03_data_room_repository/Daos.kt#L35) | 조회(Flow), 저장(@Upsert), 삭제, 중복 내역 검사 |
| [AppDatabase.kt](03_data_room_repository/AppDatabase.kt) | `AppDatabase` [L13](03_data_room_repository/AppDatabase.kt#L13) | Room 데이터베이스 |
| [ExpenseRepository.kt](03_data_room_repository/ExpenseRepository.kt) | `ExpenseRepository` [L15](03_data_room_repository/ExpenseRepository.kt#L15), `RoomExpenseRepository` [L26](03_data_room_repository/ExpenseRepository.kt#L26) | 화면이 데이터를 쓰는 유일한 창구 (가짜 구현으로 바꿔 끼울 수 있음) |
| [SettingsStore.kt](03_data_room_repository/SettingsStore.kt) | `SettingsStore` [L13](03_data_room_repository/SettingsStore.kt#L13) | 월 예산 저장 (DataStore) |
| [AppContainer.kt](03_data_room_repository/AppContainer.kt) | `AppContainer` [L33](03_data_room_repository/AppContainer.kt#L33) | 앱 전체 객체를 한곳에서 생성 (간단한 의존성 주입), Firebase 연결 여부로 로그인 구현 자동 선택 |

---

## 04. 화면 · ViewModel

| 파일 | 핵심 부분 | 하는 일 |
|---|---|---|
| [HomeScreen.kt](04_screens_viewmodel/HomeScreen.kt) | `HomeViewModel` [L54](04_screens_viewmodel/HomeScreen.kt#L54) | 이번 달 합계·최근 내역·예산을 `combine`해 하나의 `StateFlow`로 제공 |
| [ConfirmScreen.kt](04_screens_viewmodel/ConfirmScreen.kt) | `ConfirmViewModel` [L57](04_screens_viewmodel/ConfirmScreen.kt#L57), `save()` [L131](04_screens_viewmodel/ConfirmScreen.kt#L131) | 인식 결과 확인·직접 입력·수정을 한 화면에서 처리, 빈 칸 검사, 중복 경고 |
| [DetailScreen.kt](04_screens_viewmodel/DetailScreen.kt) | `DetailViewModel` [L44](04_screens_viewmodel/DetailScreen.kt#L44) | 원본 영수증·내역 표시, 수정·삭제 |
| [StatsScreen.kt](04_screens_viewmodel/StatsScreen.kt) | `StatsViewModel` [L41](04_screens_viewmodel/StatsScreen.kt#L41) | 이번 달 카테고리별 합계·비중 계산 |
| [AppRoot.kt](04_screens_viewmodel/AppRoot.kt) | `Routes` [L34](04_screens_viewmodel/AppRoot.kt#L34), `AppRoot()` [L58](04_screens_viewmodel/AppRoot.kt#L58) | Navigation Compose 화면 이동, 하단 탭 |

```kotlin
class HomeViewModel(repository: ExpenseRepository, settings: SettingsStore) : ViewModel() {
    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.observeBetween(range.first, range.second),   // 이번 달 합계용
            repository.observeAll(),                                // 최근 내역 목록용
            settings.monthlyBudget,                                 // 월 예산
        ) { monthList, all, budget ->
            HomeUiState(
                month = month,
                monthTotal = monthList.sumOf { it.amount },
                budget = budget,
                expenses = all.sortedByDescending { it.createdAt }.take(50),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
```
DB가 바뀌면 Room의 Flow가 새 값을 보내고, 화면이 자동으로 다시 그려진다.

---

## 05. 로그인 · 회원가입

| 파일 | 핵심 부분 | 하는 일 |
|---|---|---|
| [AuthRepository.kt](05_login_auth/AuthRepository.kt) | `AuthRepository` [L27](05_login_auth/AuthRepository.kt#L27), `FirebaseAuthRepository` [L40](05_login_auth/AuthRepository.kt#L40), `signInWithGoogle()` [L58](05_login_auth/AuthRepository.kt#L58), `FakeAuthRepository` [L122](05_login_auth/AuthRepository.kt#L122) | 이메일 가입·로그인, Credential Manager로 Google ID 토큰을 받아 Firebase 로그인, 오류 메시지 한국어 변환 |
| [LoginScreen.kt](05_login_auth/LoginScreen.kt) | `LoginViewModel` [L44](05_login_auth/LoginScreen.kt#L44) | 로그인 화면 (자동 로그인 유지) |
| [SignUpScreen.kt](05_login_auth/SignUpScreen.kt) | `SignUpViewModel` [L40](05_login_auth/SignUpScreen.kt#L40) | 회원가입 화면 (형식 검사, 비밀번호 확인) |

> 현재는 Firebase 프로젝트 연결 전이라 `FakeAuthRepository`(테스트 모드)로 동작한다.
> `google-services.json`을 넣으면 코드 수정 없이 `FirebaseAuthRepository`로 전환된다.

---

## 06. 테스트

| 파일 | 하는 일 |
|---|---|
| [ReceiptParserTest.kt](06_tests/ReceiptParserTest.kt) | 영수증 파서 JUnit 단위 테스트 9개 (폰 없이 PC에서 실행) |

| 테스트 | 확인하는 것 |
|---|---|
| `편의점_영수증` | 상호명·사업자번호·결제일시·합계 기본 추출 |
| `상호_키워드와_두자리_연도` | '상호:' 표기, `26.10.20` 형식 날짜, 다음 줄 금액 |
| `상호_키워드가_없는_카드영수증_승인번호는_금액이_아니다` | 승인번호·카드번호 제외 |
| `휘어진_카드영수증_항목과_값이_한줄씩_밀린_경우` | 실제 영수증 사례: 대표자 이름 대신 상호명, 위 줄의 결제금액, 하이픈 없는 사업자번호 |
| `크게_인쇄된_줄을_상호명으로` | 글자 높이로 상호명 판단 |
| `합계_키워드가_없으면_쉼표나_원이_붙은_금액중_최댓값` | 키워드 없는 영수증 |
| `할인합계와_부가세는_결제금액이_아니다` | 할인·부가세 줄 제외 |
| `전화번호와_카드번호는_금액이_아니다` | 번호류 제외 |
| `날짜가_없으면_null` | 날짜 없는 경우 |

실제 영수증이 잘못 읽히면 Logcat의 `Receipt` 태그로 읽은 행을 복사해 테스트를 추가하고, 통과하도록 파서 규칙을 고치는 방식으로 개선하고 있다.
