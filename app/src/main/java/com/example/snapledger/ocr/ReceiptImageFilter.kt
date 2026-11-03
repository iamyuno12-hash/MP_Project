package com.example.snapledger.ocr

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 영수증 사진 보정 필터. 회색조 픽셀 배열(0=검정, 255=흰색)에 적용하는 순수 Kotlin 코드라
 * PC에서 단위 테스트로 결과를 확인할 수 있다 (ReceiptImageFilterTest).
 *
 * 1) 조명 보정: 주변에서 가장 밝은 값(= 종이 색)을 배경으로 추정하고, 픽셀 값을 배경으로 나눈다.
 *    손 그림자나 한쪽만 어두운 사진에서도 종이는 고르게 하얘지고 글자만 어둡게 남는다.
 *    배경 추정에는 닫힘 연산(팽창 후 침식)을 써서 글자는 지우되 그림자 경계는 제자리에 둔다.
 * 2) 대비 늘리기: 가장 어두운 쪽(글자)을 0, 종이 밝기를 255로 펼친다. 흐린 감열지 글자가 진해진다.
 */
object ReceiptImageFilter {

    /** 배경을 추정할 때 쓰는 블록 크기(px). 블록 최댓값 + 닫힘 연산이라 약 16px보다 가는 획은 배경에서 지워진다 */
    private const val BLOCK = 8

    /** 좁은 변 목표(px): 영수증 글자 높이가 ML Kit 권장(16px 이상)보다 넉넉해지는 크기 */
    const val MIN_SHORT_SIDE = 1000
    /** 넓은 변 상한(px) */
    const val MAX_LONG_SIDE = 3000
    /** 전체 픽셀 수 상한 (메모리·처리 시간) */
    const val MAX_PIXELS = 4_500_000

    /** 사진 크기에 곱할 배율. 작으면 키우되 최대 2배, 크면 줄인다 */
    fun ocrScale(width: Int, height: Int): Double {
        val shortSide = min(width, height).toDouble()
        val longSide = max(width, height).toDouble()
        var scale = 1.0
        if (shortSide < MIN_SHORT_SIDE) scale = min(2.0, MIN_SHORT_SIDE / shortSide)
        if (longSide * scale > MAX_LONG_SIDE) scale = MAX_LONG_SIDE / longSide
        if (width.toDouble() * height * scale * scale > MAX_PIXELS) scale = sqrt(MAX_PIXELS / (width.toDouble() * height))
        return scale
    }

    /** 사람 눈의 밝기 감각에 맞춘 회색조 값 (BT.601). argb는 안드로이드 색 정수 */
    fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    fun normalize(gray: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0 && gray.size == width * height) { "이미지 크기가 맞지 않아요" }

        val background = BackgroundGrid.estimate(gray, width, height, BLOCK)
        // 검은 띠 안의 흰 글자(반전 인쇄)처럼 넓게 어두운 곳은 배경으로 나누면 통째로 하얘지므로
        // 배경 값이 전체 종이 밝기의 절반보다 어두워지지 않게 막는다
        val paper = percentile(histogram(gray), 0.90)
        val floor = max(1, paper / 2)

        val out = IntArray(gray.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val bg = max(background.sample(x, y), floor)
                out[i] = min(255, gray[i] * 255 / bg)
            }
        }
        stretchContrast(out)
        return out
    }

    /** 어두운 쪽 1%를 0, 가운데 값(대부분 종이)을 255로 펼친다. 거의 한 가지 색이면 그대로 둔다 */
    internal fun stretchContrast(pixels: IntArray) {
        val hist = histogram(pixels)
        val low = percentile(hist, 0.01)
        val high = percentile(hist, 0.50)
        if (high - low < 32) return
        for (i in pixels.indices) {
            pixels[i] = ((pixels[i] - low) * 255 / (high - low)).coerceIn(0, 255)
        }
    }

    internal fun histogram(pixels: IntArray): IntArray {
        val hist = IntArray(256)
        pixels.forEach { hist[it.coerceIn(0, 255)]++ }
        return hist
    }

    /** 히스토그램에서 아래쪽부터 비율 q(0~1)에 해당하는 밝기 */
    internal fun percentile(hist: IntArray, q: Double): Int {
        val total = hist.sum()
        if (total == 0) return 0
        val target = (total * q).toLong()
        var seen = 0L
        for (value in hist.indices) {
            seen += hist[value]
            if (seen > target) return value
        }
        return 255
    }
}

/**
 * 배경(종이) 밝기를 작은 격자로 추정해 두고, 픽셀마다 부드럽게(쌍선형 보간) 꺼내 쓴다.
 * 전체 크기 배열을 하나 더 만들지 않아 메모리를 아낀다.
 */
internal class BackgroundGrid private constructor(
    private val values: IntArray,
    private val gridWidth: Int,
    private val gridHeight: Int,
    private val block: Int,
) {
    fun sample(x: Int, y: Int): Int {
        // 픽셀 중심을 격자 좌표로 (격자 칸의 중심이 정수 좌표)
        val gx = ((x + 0.5f) / block - 0.5f).coerceIn(0f, (gridWidth - 1).toFloat())
        val gy = ((y + 0.5f) / block - 0.5f).coerceIn(0f, (gridHeight - 1).toFloat())
        val x0 = gx.toInt()
        val y0 = gy.toInt()
        val x1 = min(x0 + 1, gridWidth - 1)
        val y1 = min(y0 + 1, gridHeight - 1)
        val fx = gx - x0
        val fy = gy - y0
        val top = at(x0, y0) * (1 - fx) + at(x1, y0) * fx
        val bottom = at(x0, y1) * (1 - fx) + at(x1, y1) * fx
        return (top * (1 - fy) + bottom * fy).toInt()
    }

    private fun at(x: Int, y: Int) = values[y * gridWidth + x]

    companion object {
        fun estimate(gray: IntArray, width: Int, height: Int, block: Int): BackgroundGrid {
            val gw = (width + block - 1) / block
            val gh = (height + block - 1) / block

            // 1) 블록마다 가장 밝은 값
            val blockMax = IntArray(gw * gh)
            for (y in 0 until height) {
                val row = y / block * gw
                for (x in 0 until width) {
                    val cell = row + x / block
                    val v = gray[y * width + x]
                    if (v > blockMax[cell]) blockMax[cell] = v
                }
            }
            // 2) 닫힘 연산 = 이웃 3×3 최댓값(팽창) → 이웃 3×3 최솟값(침식)
            //    팽창으로 블록보다 굵은 글자까지 배경에서 지우고, 침식으로 넓게 번진 밝은 값을 되돌려
            //    손 그림자처럼 경계가 뚜렷한 그림자는 경계 위치를 그대로 지킨다 (경계에 검은 줄이 생기지 않게)
            val dilated = filter3x3(blockMax, gw, gh) { window -> window.max() }
            val closed = filter3x3(dilated, gw, gh) { window -> window.min() }
            // 3) 픽셀마다 꺼낼 때 쌍선형 보간으로 블록 경계를 부드럽게 잇는다 (sample)
            return BackgroundGrid(closed, gw, gh, block)
        }

        private inline fun filter3x3(src: IntArray, w: Int, h: Int, reduce: (IntArray) -> Int): IntArray {
            val out = IntArray(src.size)
            val window = IntArray(9)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var n = 0
                    for (dy in -1..1) {
                        val yy = y + dy
                        if (yy < 0 || yy >= h) continue
                        for (dx in -1..1) {
                            val xx = x + dx
                            if (xx < 0 || xx >= w) continue
                            window[n++] = src[yy * w + xx]
                        }
                    }
                    out[y * w + x] = reduce(window.copyOf(n))
                }
            }
            return out
        }
    }
}
