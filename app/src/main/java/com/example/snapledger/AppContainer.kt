package com.example.snapledger

import android.app.Application
import android.content.Context
import com.example.snapledger.data.firebase.AuthRepository
import com.example.snapledger.data.firebase.FakeAuthRepository
import com.example.snapledger.data.firebase.FirebaseAuthRepository
import com.google.firebase.FirebaseApp
import com.example.snapledger.data.local.AppDatabase
import com.example.snapledger.data.local.SessionStore
import com.example.snapledger.data.local.SettingsStore
import com.example.snapledger.data.firebase.FirestoreExpenseBackup
import com.example.snapledger.data.remote.GeminiApiClient
import com.example.snapledger.data.remote.KakaoApiClient
import com.example.snapledger.data.repository.CategoryClassifier
import com.example.snapledger.data.repository.ExpenseRepository
import com.example.snapledger.data.repository.ExpenseSync
import com.example.snapledger.data.repository.RoomExpenseRepository
import com.example.snapledger.model.Category
import com.example.snapledger.ocr.GeminiReceiptReader
import com.example.snapledger.ocr.ParsedReceipt
import com.example.snapledger.ocr.ReceiptImagePreprocessor
import com.example.snapledger.ocr.ReceiptParser
import com.example.snapledger.ocr.ReceiptTextRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth

class SnapLedgerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * 앱 전체에서 하나씩만 쓰는 객체들을 모아 두는 곳 (간단한 수동 의존성 주입).
 * 가짜 구현을 진짜로 바꿀 때는 여기 한 줄만 고치면 된다.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    private val database = AppDatabase.build(appContext)

    /** 화면과 상관없이 앱이 켜져 있는 동안 도는 작업용 (백업 업로드 등) */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 로그인 상태 유지(자동 로그인) 설정 */
    val sessionStore = SessionStore(appContext)
    // google-services.json이 들어가 Firebase가 켜져 있으면 진짜 로그인, 아니면 테스트용 가짜 로그인
    val authRepository: AuthRepository =
        if (FirebaseApp.getApps(appContext).isNotEmpty()) FirebaseAuthRepository() else FakeAuthRepository(sessionStore)

    /** Firestore 백업 (Firebase 로그인일 때만 실제로 동작, 테스트 모드면 아무것도 안 함) */
    val expenseSync = ExpenseSync(
        dao = database.expenseDao(),
        backup = FirestoreExpenseBackup(authRepository),
        auth = authRepository,
        scope = appScope,
    )
    val expenseRepository: ExpenseRepository = RoomExpenseRepository(database.expenseDao(), expenseSync)
    val settingsStore = SettingsStore(appContext)

    val categoryClassifier = CategoryClassifier(
        ruleDao = database.categoryRuleDao(),
        kakaoApi = KakaoApiClient.create(BuildConfig.KAKAO_REST_API_KEY),
        kakaoEnabled = BuildConfig.KAKAO_REST_API_KEY.isNotBlank(),
    )

    val receiptRecognizer = ReceiptTextRecognizer(appContext)
    val receiptPreprocessor = ReceiptImagePreprocessor()
    val receiptParser = ReceiptParser()
    /** Gemini AI 영수증 인식 (local.properties에 GEMINI_API_KEY가 없으면 꺼짐 → ML Kit만 사용) */
    val geminiReader = GeminiReceiptReader(
        api = BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }?.let { GeminiApiClient.create(it) },
        model = BuildConfig.GEMINI_MODEL,
        parser = receiptParser,
    )

    /** 촬영 화면 → 인식 결과 확인 화면으로 넘기는 임시 데이터 */
    var pendingDraft: ReceiptDraft? = null

    /** 홈 화면에서 보고 있는 달. 다른 달 영수증을 저장하면 그 달로 바꿔서 방금 저장한 내역이 바로 보이게 한다 */
    val homeMonth = MutableStateFlow(YearMonth.now())
}

/** 인식 결과 확인 화면에 미리 채울 값 */
data class ReceiptDraft(
    val imagePath: String?,
    val parsed: ParsedReceipt,
    val recognizedLines: List<String> = emptyList(),
    /** 글자 인식용으로 보정한 이미지 (보정에 실패했으면 null) */
    val processedImagePath: String? = null,
    /** 원본·보정 중 보정 이미지 쪽 인식 결과를 썼는지 */
    val usedProcessedImage: Boolean = false,
    /** Gemini AI가 읽은 값을 썼는지 */
    val usedAi: Boolean = false,
    /** Gemini가 추천한 카테고리 (분류 규칙으로 못 정했을 때 사용) */
    val aiCategory: Category? = null,
)
