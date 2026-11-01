package com.example.snapledger

import android.app.Application
import android.content.Context
import com.example.snapledger.data.firebase.AuthRepository
import com.example.snapledger.data.firebase.FakeAuthRepository
import com.example.snapledger.data.firebase.FirebaseAuthRepository
import com.google.firebase.FirebaseApp
import com.example.snapledger.data.local.AppDatabase
import com.example.snapledger.data.local.SettingsStore
import com.example.snapledger.data.remote.KakaoApiClient
import com.example.snapledger.data.repository.CategoryClassifier
import com.example.snapledger.data.repository.ExpenseRepository
import com.example.snapledger.data.repository.RoomExpenseRepository
import com.example.snapledger.ocr.ParsedReceipt
import com.example.snapledger.ocr.ReceiptParser
import com.example.snapledger.ocr.ReceiptTextRecognizer

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

    val expenseRepository: ExpenseRepository = RoomExpenseRepository(database.expenseDao())
    // google-services.json이 들어가 Firebase가 켜져 있으면 진짜 로그인, 아니면 테스트용 가짜 로그인
    val authRepository: AuthRepository =
        if (FirebaseApp.getApps(appContext).isNotEmpty()) FirebaseAuthRepository() else FakeAuthRepository()
    val settingsStore = SettingsStore(appContext)

    val categoryClassifier = CategoryClassifier(
        ruleDao = database.categoryRuleDao(),
        kakaoApi = KakaoApiClient.create(BuildConfig.KAKAO_REST_API_KEY),
        kakaoEnabled = BuildConfig.KAKAO_REST_API_KEY.isNotBlank(),
    )

    val receiptRecognizer = ReceiptTextRecognizer(appContext)
    val receiptParser = ReceiptParser()

    /** 촬영 화면 → 인식 결과 확인 화면으로 넘기는 임시 데이터 */
    var pendingDraft: ReceiptDraft? = null
}

/** 인식 결과 확인 화면에 미리 채울 값 */
data class ReceiptDraft(
    val imagePath: String?,
    val parsed: ParsedReceipt,
    val recognizedLines: List<String> = emptyList(),
)
