package com.example.snapledger.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/** ML Kit 한국어 글자 인식. 기기 안에서 처리하므로 네트워크가 필요 없다. */
class ReceiptTextRecognizer(private val context: Context) {

    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    /** 이미지에서 글자를 읽어 ML Kit 결과 그대로 돌려준다 (줄별 좌표 포함 → 확인 화면 박스 표시에 사용) */
    suspend fun recognize(imageUri: Uri): Text {
        val image = InputImage.fromFilePath(context, imageUri)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { text -> cont.resume(text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }
}

/**
 * ML Kit 결과를 위→아래 순서의 '행' 목록으로 바꾼다 (파서 입력용).
 *
 * ML Kit은 영수증의 왼쪽 글자('합계')와 오른쪽 숫자('4,800')를 서로 다른 덩어리로 읽는 경우가 많다.
 * 그래서 세로 위치가 거의 같은 줄끼리 모아 왼쪽→오른쪽으로 이어 붙여 '합계 4,800'처럼 한 행으로 만든다.
 */
fun Text.toLinesTopToBottom(): List<String> = toRows().map { it.text }

/** 행 목록 + 각 행의 위치·글자 높이 (파서가 '크게 인쇄된 상호명'을 찾는 데 사용) */
fun Text.toRows(): List<OcrRow> {
    val withBox = textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
    val noBox = textBlocks.flatMap { it.lines }.filter { it.boundingBox == null }.map { OcrRow(it.text) }

    val rows = mutableListOf<MutableList<Text.Line>>()
    for (line in withBox.sortedBy { it.boundingBox!!.centerY() }) {
        val box = line.boundingBox!!
        val row = rows.lastOrNull()
        if (row != null) {
            val rowCenter = row.map { it.boundingBox!!.centerY() }.average()
            val rowHeight = row.map { it.boundingBox!!.height() }.average()
            // 세로 중심 차이가 글자 높이의 절반보다 작으면 같은 행
            if (abs(box.centerY() - rowCenter) < minOf(rowHeight, box.height().toDouble()) * 0.5) {
                row.add(line)
                continue
            }
        }
        rows.add(mutableListOf(line))
    }

    return rows.map { row ->
        val heights = row.map { it.boundingBox!!.height() }.sorted()
        OcrRow(
            text = row.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text },
            top = row.minOf { it.boundingBox!!.top },
            height = heights[heights.size / 2],
        )
    } + noBox
}
