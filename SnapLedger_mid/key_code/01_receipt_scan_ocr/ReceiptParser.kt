package com.example.snapledger.ocr

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 영수증에서 뽑아낸 값. 못 읽은 값은 null */
data class ParsedReceipt(
    val storeName: String? = null,
    val businessNumber: String? = null,
    val paidAt: LocalDateTime? = null,
    val total: Long? = null,
)

/**
 * ML Kit이 읽은 한 행. 같은 높이의 글자를 왼쪽→오른쪽으로 합친 것.
 * height = 그 행의 글자 높이(px). 상호명은 보통 크게 인쇄되므로 점수에 쓴다. (0이면 정보 없음)
 */
data class OcrRow(val text: String, val top: Int = 0, val height: Int = 0)

/**
 * 규칙 기반 영수증 파서. ML Kit이 읽은 줄 목록(위→아래, 같은 높이의 글자는 한 줄로 합친 것)을 받아
 * 필요한 값만 고른다. 안드로이드에 의존하지 않는 순수 Kotlin이라 PC에서 단위 테스트로 바로 고칠 수 있다.
 */
class ReceiptParser {

    /** 글자만 있을 때 (단위 테스트용) */
    fun parse(lines: List<String>): ParsedReceipt = parseRows(lines.map { OcrRow(it) })

    /** 앱에서 쓰는 입구: 행의 위치·글자 크기까지 활용 */
    fun parseRows(rows: List<OcrRow>): ParsedReceipt {
        val kept = rows.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
        val cleaned = kept.map { it.text }
        return ParsedReceipt(
            storeName = findStoreName(cleaned, kept.map { it.height }),
            businessNumber = findBusinessNumber(cleaned),
            paidAt = findDateTime(cleaned),
            total = findTotal(cleaned),
        )
    }

    // =====================================================================
    // 공통 패턴
    // =====================================================================
    private val bizRegex = Regex("""(?<!\d)(\d{3})\s?-\s?(\d{2})\s?-\s?(\d{5})(?!\d)""")
    private val phoneRegex = Regex("""(?<!\d)0\d{1,3}\s?[-)]\s?\d{3,4}\s?-\s?\d{4}(?!\d)""")
    private val cardRegex = Regex("""[\d*]{4}(?:\s?-\s?[\d*]{2,4}){2,3}""")
    private val date4Regex = Regex("""(?<!\d)(20\d{2})\s?[.\-/년]\s?(\d{1,2})\s?[.\-/월]\s?(\d{1,2})""")
    private val date2Regex = Regex("""(?<!\d)(\d{2})[.\-/](\d{1,2})[.\-/](\d{1,2})(?!\d)""")
    private val timeRegex = Regex("""(?<!\d)([01]?\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?(?!\d)""")

    /** 띄어쓰기를 지우고 대문자로 (OCR이 '합 계', '승인 번호'처럼 띄어 읽는 경우 대비) */
    private fun compact(line: String) = line.replace(" ", "").uppercase()

    private fun containsAny(line: String, words: List<String>): Boolean {
        val c = compact(line)
        return words.any { c.contains(it.uppercase()) }
    }

    // =====================================================================
    // 사업자등록번호
    // =====================================================================
    private val tenDigitsRegex = Regex("""(?<!\d)(\d{10})(?!\d)""")

    fun findBusinessNumber(lines: List<String>): String? {
        // 1) 123-45-67890 형태
        for (line in lines) {
            val m = bizRegex.find(line) ?: continue
            return "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}"
        }
        // 2) 하이픈 없이 '1234567890'으로 찍힌 경우: 사업자번호 검증 공식을 통과하는 10자리만 인정
        for (line in lines) {
            for (m in tenDigitsRegex.findAll(line)) {
                val d = m.groupValues[1]
                if (isValidBusinessNumber(d)) return "${d.substring(0, 3)}-${d.substring(3, 5)}-${d.substring(5)}"
            }
        }
        return null
    }

    /** 국세청 사업자등록번호 검증: 가중치 1,3,7,1,3,7,1,3,5 */
    private fun isValidBusinessNumber(d: String): Boolean {
        if (d.length != 10 || !d.all { it.isDigit() }) return false
        val weights = intArrayOf(1, 3, 7, 1, 3, 7, 1, 3, 5)
        var sum = (0 until 9).sumOf { (d[it] - '0') * weights[it] }
        sum += (d[8] - '0') * 5 / 10
        return (10 - sum % 10) % 10 == d[9] - '0'
    }

    // =====================================================================
    // 결제일시
    // =====================================================================
    fun findDateTime(lines: List<String>): LocalDateTime? {
        for ((index, line) in lines.withIndex()) {
            val date = parseDate(line) ?: continue
            // 시간은 같은 줄 → 다음 줄 순서로 찾는다
            val time = parseTime(line) ?: lines.getOrNull(index + 1)?.let { parseTime(it) }
            return LocalDateTime.of(date, time ?: LocalTime.MIDNIGHT)
        }
        return null
    }

    private fun parseDate(line: String): LocalDate? {
        date4Regex.find(line)?.let { m ->
            toDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }
        date2Regex.find(line)?.let { m ->
            toDate(2000 + m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it }
        }
        return null
    }

    private fun toDate(y: Int, m: Int, d: Int): LocalDate? =
        try {
            LocalDate.of(y, m, d)
        } catch (e: Exception) {
            null
        }

    private fun parseTime(line: String): LocalTime? {
        val m = timeRegex.find(line) ?: return null
        val sec = m.groupValues[3].ifEmpty { "0" }.toInt()
        return LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), sec)
    }

    // =====================================================================
    // 합계 금액
    // =====================================================================
    private val totalKeywords = listOf("합계", "총액", "결제금액", "받을금액", "총금액", "판매금액", "청구금액", "결제액", "TOTAL")

    // 숫자가 있어도 금액이 아닌 줄 (승인번호 등은 8자리라 금액처럼 보임)
    private val notAmountKeywords = listOf(
        "승인번호", "승인NO", "카드번호", "가맹점번호", "사업자", "전화", "TEL", "FAX", "주문번호", "거래번호",
        "일련번호", "영수증번호", "단말기", "POS", "회원번호", "카드사", "유효기간", "NO.", "번호",
    )

    // 합계 키워드가 들어 있어도 최종 결제 금액이 아닌 줄
    private val notTotalKeywords = listOf("할인", "포인트", "적립", "부가세", "과세", "면세", "거스름", "받은", "잔액", "잔돈", "봉사료")

    /** 금액 후보: '4,800' 처럼 쉼표가 있거나 '4800원'처럼 '원'이 붙은 숫자 (bare=true면 '4800'도 허용) */
    private val amountRegex = Regex("""(?<![\d,.\-])(\d{1,3}(?:,\d{3})+|\d{3,7})(\s?원)?(?![\d,])""")

    private fun amountsIn(line: String, allowBare: Boolean): List<Long> {
        if (containsAny(line, notAmountKeywords)) return emptyList()
        var text = line
        listOf(bizRegex, phoneRegex, cardRegex, date4Regex, date2Regex, timeRegex).forEach {
            text = it.replace(text, " ")
        }
        return amountRegex.findAll(text)
            .filter { m -> allowBare || m.groupValues[1].contains(',') || m.groupValues[2].isNotEmpty() }
            .mapNotNull { it.groupValues[1].replace(",", "").toLongOrNull() }
            .filter { it in 100..10_000_000 }
            .toList()
    }

    fun findTotal(lines: List<String>): Long? {
        // 1) '합계/결제금액' 줄의 금액. 같은 줄에 없으면 바로 아래 → 바로 위 줄 (단, 금액만 있는 줄일 때만)
        //    영수증이 휘어 찍히면 오른쪽 숫자가 왼쪽 글자보다 한 줄 위/아래로 밀려 읽히기 때문
        //    여러 개면 가장 큰 값
        val fromKeyword = lines.withIndex().mapNotNull { (index, line) ->
            if (!containsAny(line, totalKeywords) || containsAny(line, notTotalKeywords)) return@mapNotNull null
            amountsIn(line, allowBare = true).lastOrNull()
                ?: listOf(index + 1, index - 1)
                    .mapNotNull { lines.getOrNull(it) }
                    .firstOrNull { isAmountOnly(it) }
                    ?.let { amountsIn(it, allowBare = true).lastOrNull() }
        }
        fromKeyword.maxOrNull()?.let { return it }

        // 2) 키워드가 없으면 '4,800' / '4800원' 형태의 금액 중 최댓값
        return amountCandidates(lines).maxOrNull()
    }

    /** '130,000원'처럼 금액 말고 다른 글자가 없는 줄 */
    private fun isAmountOnly(line: String): Boolean =
        line.none { it.isLetter() && it != '원' } && amountsIn(line, allowBare = true).isNotEmpty()

    /** 금액처럼 보이는 숫자를 큰 순서로 */
    fun amountCandidates(lines: List<String>): List<Long> =
        lines.flatMap { amountsIn(it, allowBare = false) }.distinct().sortedDescending()

    // =====================================================================
    // 상호명
    // =====================================================================
    private val storeKeywords = listOf("상호명", "상호", "가맹점명", "매장명", "점포명")

    // 상호명이 될 수 없는 줄 (영수증 머리말, 표 머리글, 결제 정보 등)
    private val notStoreKeywords = listOf(
        "영수증", "고객용", "매장용", "카드", "신용", "승인", "매출", "전표", "사업자", "대표", "전화", "TEL", "FAX",
        "주소", "일시", "날짜", "POS", "계산", "판매", "교환", "환불", "감사", "합계", "금액", "품명", "상품명", "단가",
        "수량", "현금", "부가세", "과세", "면세", "주문", "번호", "WELCOME", "RECEIPT", "가맹점", "고객", "포인트",
        "결제", "방법", "매입",
    )

    // '상호 OOO' 칸의 이웃 줄이 '값만 있는 줄'인지 판단할 때 쓰는 항목 이름들
    private val labelWords = listOf(
        "상호", "대표", "사업자", "주소", "전화", "번호", "결제", "승인", "카드", "가맹점", "일시", "매입", "할부",
        "부가세", "공급가", "합계", "금액",
    )
    private val hangulNameRegex = Regex("""^[가-힣]{2,4}$""")
    private val addressRegex = Regex(
        """(^|\s)(서울|경기|인천|부산|대구|대전|광주|울산|세종|강원|충북|충남|전북|전남|경북|경남|제주)""" +
            """|\S+(시|구|군|동|읍|면|로|길)\s?\d|번지"""
    )
    // 가게 이름에 자주 붙는 말 (점수 가산)
    private val storeHints = listOf(
        "점", "편의점", "마트", "카페", "커피", "식당", "분식", "김밥", "약국", "의원", "병원", "베이커리", "제과",
        "치킨", "피자", "버거", "문구", "서점", "마켓", "상회", "상사", "스토어", "푸드", "주점", "포차", "반점", "국밥",
    )

    fun findStoreName(lines: List<String>, heights: List<Int> = emptyList()): String? {
        // '대표' 칸이 있는 줄 (대표번호·대표전화 제외) → 그 근처의 2~4글자 한글은 사람 이름일 가능성이 큼
        val ownerRows = lines.indices.filter {
            val c = compact(lines[it])
            c.contains("대표") && !c.contains("대표번호") && !c.contains("대표전화")
        }
        fun looksLikePersonName(name: String, index: Int) =
            hangulNameRegex.matches(name.replace(" ", "")) && ownerRows.any { kotlin.math.abs(it - index) <= 1 }
        // 상호명은 영수증 맨 위와 '상호' 칸에 두 번 찍히는 경우가 많다
        fun appearsElsewhere(name: String, index: Int) =
            lines.withIndex().any { (j, other) -> j != index && compact(other).contains(compact(name)) }

        // 1) '상호' 칸: 같은 줄의 값 + 바로 위/아래 줄(값만 있는 줄)을 후보로 점수 비교
        //    영수증이 휘어 찍히면 오른쪽 값이 왼쪽 '상호' 글자보다 한 줄 위/아래로 밀려 읽히기 때문
        val labelCandidates = mutableListOf<Pair<String, Double>>()
        for ((i, line) in lines.withIndex()) {
            val c = line.replace(" ", "").trimStart('[', '(')
            val keyword = storeKeywords.firstOrNull { c.startsWith(it) } ?: continue
            val sameRowValue = line.substringAfter(':', missingDelimiterValue = "")
                .ifBlank { c.removePrefix(keyword) }
                .trimStart(']', ')', ':', '：', ' ')
            val options = listOf(i to sameRowValue, (i - 1) to lines.getOrNull(i - 1), (i + 1) to lines.getOrNull(i + 1))
            for ((index, raw) in options) {
                if (raw == null) continue
                if (index != i && containsAny(raw, labelWords)) continue   // 이웃 줄은 '값만 있는 줄'만
                val name = cleanStoreName(raw) ?: continue
                var score = if (index == i) 1.0 else 0.0
                if (appearsElsewhere(name, index)) score += 3.0
                if (looksLikePersonName(name, index)) score -= 3.0
                if (storeHints.any { name.contains(it) }) score += 1.0
                labelCandidates += name to score
            }
        }
        labelCandidates.filter { it.second > 0 }.maxByOrNull { it.second }?.let { return it.first }

        // 2) 위쪽 8줄 안에서 '가게 이름처럼 보이는' 줄에 점수를 매겨 가장 높은 줄
        val bizIndex = lines.indexOfFirst { bizRegex.containsMatchIn(it) }
        val sortedHeights = heights.filter { it > 0 }.sorted()
        val medianHeight = if (sortedHeights.isEmpty()) 0 else sortedHeights[sortedHeights.size / 2]
        return lines.take(8).withIndex()
            .mapNotNull { (index, line) ->
                val name = cleanStoreName(line) ?: return@mapNotNull null
                if (containsAny(name, notStoreKeywords) || addressRegex.containsMatchIn(name)) return@mapNotNull null
                if (parseDate(name) != null || parseTime(name) != null) return@mapNotNull null
                var score = (8 - index) / 4.0                                   // 위에 있을수록 가산
                if (storeHints.any { name.contains(it) }) score += 2.0          // '…점', '…편의점' 등
                if (bizIndex >= 0 && index in (bizIndex - 2) until bizIndex) score += 1.0 // 사업자번호 바로 위
                if (appearsElsewhere(name, index)) score += 1.5                  // 아래 '상호' 칸에도 있음
                if (looksLikePersonName(name, index)) score -= 3.0
                val height = heights.getOrNull(index) ?: 0
                if (medianHeight > 0 && height > 0) {
                    // 다른 줄보다 글씨가 클수록 가산 (보통 상호명이 가장 크게 인쇄됨)
                    score += (height.toDouble() / medianHeight - 1.0).coerceIn(0.0, 1.5) * 3.0
                }
                name to score
            }
            .maxByOrNull { it.second }
            ?.first
    }

    /** 사업자번호·전화번호·'대표 …' 같은 군더더기를 떼고, 이름으로 쓸 수 없으면 null */
    private fun cleanStoreName(raw: String): String? {
        var name = raw
        listOf(bizRegex, phoneRegex).forEach { name = it.replace(name, " ") }
        name = name.substringBefore("대표")
            .replace(Regex("""^[\[(<].*?[\])>]"""), " ")    // 앞의 [고객용] 같은 꼬리표
            .trim(' ', ':', '：', '-', '*', '=', '#', '|')
            .replace(Regex("""\s+"""), " ")
        val letters = name.count { it.isLetter() }
        val digits = name.count { it.isDigit() }
        return if (letters >= 2 && digits <= 4 && name.length <= 30) name else null
    }
}
