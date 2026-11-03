package com.example.snapledger.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import kotlin.math.max

/**
 * 글자 인식 전에 영수증 사진을 다듬는다.
 * 1) EXIF 회전 정보대로 똑바로 세운다
 * 2) 크기를 맞춘다: 좁은 변이 1000px보다 작으면 키우고(글자가 너무 작으면 인식률이 떨어짐),
 *    너무 크면 줄인다(처리 시간·메모리)
 * 3) 회색조로 바꾸고 ReceiptImageFilter로 그림자 제거 + 대비 늘리기
 */
class ReceiptImagePreprocessor {

    fun preprocess(source: File): Bitmap {
        val upright = decodeUpright(source)
        val scaled = scaleForOcr(upright)
        if (scaled !== upright) upright.recycle()

        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        scaled.recycle()

        for (i in pixels.indices) pixels[i] = ReceiptImageFilter.luminance(pixels[i])
        val normalized = ReceiptImageFilter.normalize(pixels, width, height)
        for (i in normalized.indices) {
            val v = normalized[i]
            normalized[i] = Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(normalized, width, height, Bitmap.Config.ARGB_8888)
    }

    /** 큰 사진은 디코딩할 때부터 줄여서(inSampleSize) 메모리를 아끼고, EXIF 방향대로 회전한다 */
    private fun decodeUpright(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("사진을 열 수 없어요")

        var sample = 1
        // 디코딩 결과가 목표 픽셀 수의 2배를 넘지 않을 때까지 1/2씩 줄여 읽는다 (예: 1,200만 화소 → 300만)
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > 2L * ReceiptImageFilter.MAX_PIXELS) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalStateException("사진을 열 수 없어요")

        val matrix = Matrix()
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleForOcr(bitmap: Bitmap): Bitmap {
        val scale = ReceiptImageFilter.ocrScale(bitmap.width, bitmap.height)
        if (scale == 1.0) return bitmap
        val w = max(1, (bitmap.width * scale).toInt())
        val h = max(1, (bitmap.height * scale).toInt())
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
