package com.example.snapledger.model

/** 앱에서 쓰는 지출 카테고리 8종. Room에는 이름(FOOD 등)으로 저장된다. */
enum class Category(val label: String) {
    FOOD("식비"),
    CAFE("카페 및 디저트"),
    CONVENIENCE("편의점 및 마트"),
    TRANSPORT("교통"),
    SHOPPING("쇼핑"),
    LIVING("생활 및 의료"),
    CULTURE("문화 및 여가 생활"),
    ETC("기타"),
}
