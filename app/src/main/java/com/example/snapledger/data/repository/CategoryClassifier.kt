package com.example.snapledger.data.repository

import android.util.Log
import com.example.snapledger.data.local.CategoryRule
import com.example.snapledger.data.local.CategoryRuleDao
import com.example.snapledger.data.remote.KakaoLocalApi
import com.example.snapledger.model.Category

/**
 * 카테고리 자동 분류. 제안서 순서대로 시도하고 앞 단계에서 정해지면 멈춘다.
 * 1) 사용자 규칙 → 2) 내장 키워드 사전 → 3) 카카오 로컬 API → 4) 기타
 */
class CategoryClassifier(
    private val ruleDao: CategoryRuleDao,
    private val kakaoApi: KakaoLocalApi,
    private val kakaoEnabled: Boolean,
) {

    suspend fun classify(storeName: String, businessNumber: String?): Category {
        if (storeName.isBlank()) return Category.ETC

        // 1) 사용자 규칙
        ruleKeys(storeName, businessNumber).forEach { key ->
            ruleDao.find(key)?.let { return it.category }
        }

        // 2) 내장 키워드 사전
        KeywordDictionary.match(storeName)?.let { return it }

        // 3) 카카오 로컬 API (키가 없거나 실패하면 건너뜀)
        if (kakaoEnabled) {
            try {
                val place = kakaoApi.searchKeyword(storeName).documents.firstOrNull()
                if (place != null) {
                    fromKakaoGroupCode(place.categoryGroupCode)?.let { return it }
                }
            } catch (e: Exception) {
                Log.w("CategoryClassifier", "카카오 API 실패: ${e.message}")
            }
        }

        // 4) 미분류
        return Category.ETC
    }

    /** 사용자가 카테고리를 바꿨을 때 호출 → 다음부터 같은 가게는 이 카테고리로 */
    suspend fun remember(storeName: String, businessNumber: String?, category: Category) {
        ruleKeys(storeName, businessNumber).forEach { ruleDao.upsert(CategoryRule(it, category)) }
    }

    private fun ruleKeys(storeName: String, businessNumber: String?): List<String> = buildList {
        if (!businessNumber.isNullOrBlank()) add("biz:$businessNumber")
        if (storeName.isNotBlank()) add("name:${storeName.trim()}")
    }

    companion object {
        /** 카카오 category_group_code → 앱 카테고리 */
        fun fromKakaoGroupCode(code: String): Category? = when (code) {
            "FD6" -> Category.FOOD                          // 음식점
            "CE7" -> Category.CAFE                          // 카페
            "CS2", "MT1" -> Category.CONVENIENCE            // 편의점, 대형마트
            "SW8", "OL7", "PK6" -> Category.TRANSPORT       // 지하철역, 주유소, 주차장
            "HP8", "PM9" -> Category.LIVING                 // 병원, 약국
            "CT1", "AT4" -> Category.CULTURE                // 문화시설, 관광명소
            else -> null
        }
    }
}

/** 자주 나오는 브랜드는 API 없이 바로 분류 */
object KeywordDictionary {
    private val table: List<Pair<String, Category>> = listOf(
        "GS25" to Category.CONVENIENCE, "CU" to Category.CONVENIENCE, "세븐일레븐" to Category.CONVENIENCE,
        "이마트24" to Category.CONVENIENCE, "이마트" to Category.CONVENIENCE, "홈플러스" to Category.CONVENIENCE,
        "롯데마트" to Category.CONVENIENCE, "다이소" to Category.LIVING, "올리브영" to Category.SHOPPING,
        "스타벅스" to Category.CAFE, "투썸" to Category.CAFE, "이디야" to Category.CAFE, "메가커피" to Category.CAFE,
        "빽다방" to Category.CAFE, "컴포즈" to Category.CAFE, "배스킨" to Category.CAFE, "파리바게뜨" to Category.CAFE,
        "맥도날드" to Category.FOOD, "버거킹" to Category.FOOD, "롯데리아" to Category.FOOD, "맘스터치" to Category.FOOD,
        "약국" to Category.LIVING, "의원" to Category.LIVING, "병원" to Category.LIVING,
        "CGV" to Category.CULTURE, "메가박스" to Category.CULTURE, "롯데시네마" to Category.CULTURE,
    )

    fun match(storeName: String): Category? {
        val name = storeName.replace(" ", "").uppercase()
        return table.firstOrNull { (keyword, _) -> name.contains(keyword.uppercase()) }?.second
    }
}
