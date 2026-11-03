package com.example.snapledger.ocr

/**
 * 같은 영수증을 원본·보정 이미지로 두 번 읽었을 때 어느 쪽이 더 잘 읽혔는지 점수로 비교한다.
 * 파서가 찾아낸 값이 많을수록, 한글·숫자를 많이 읽었을수록 점수가 높다 (최대 100점).
 */
object RecognitionScorer {

    fun score(parsed: ParsedReceipt, lines: List<String>): Int {
        var score = 0
        if (parsed.total != null) score += 30
        if (parsed.paidAt != null) score += 20
        // 사업자번호는 국세청 검증 공식을 통과해야 나오는 값이라, 숫자를 정확히 읽었다는 강한 신호
        if (parsed.businessNumber != null) score += 20
        if (parsed.storeName != null) score += 10
        // 읽은 한글·숫자 25자마다 1점 (최대 20점): 값이 같게 나오면 글자를 더 많이 읽은 쪽
        val readable = lines.sumOf { line -> line.count { it.isDigit() || it in '가'..'힣' } }
        score += minOf(20, readable / 25)
        return score
    }

    /** 점수가 같으면 보정 이미지 쪽을 쓴다 */
    fun preferProcessed(originalScore: Int, processedScore: Int): Boolean = processedScore >= originalScore
}
