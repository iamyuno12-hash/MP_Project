package com.example.snapledger.ui.capture

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.snapledger.AppContainer
import com.example.snapledger.ReceiptDraft
import com.example.snapledger.ocr.toRows
import com.example.snapledger.ui.common.appViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CaptureViewModel(private val container: AppContainer) : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun showError(message: String) {
        error = message
    }

    /** 스캔된 영수증 → 앱 저장소에 원본 복사 → ML Kit 글자 인식 → 행 단위 파싱 → 확인 화면용 초안 */
    fun recognize(imageUri: Uri, onDone: () -> Unit) {
        viewModelScope.launch {
            busy = true
            error = null
            try {
                val saved = withContext(Dispatchers.IO) { copyToAppStorage(imageUri) }
                val text = container.receiptRecognizer.recognize(Uri.fromFile(saved))
                val rows = text.toRows()
                // 테스트셋 만들 때 Logcat에서 'Receipt'로 검색해 복사 → ReceiptParserTest에 추가
                Log.d("Receipt", rows.joinToString("\n") { "[h=${it.height}] ${it.text}" })
                container.pendingDraft = ReceiptDraft(
                    imagePath = saved.absolutePath,
                    parsed = container.receiptParser.parseRows(rows),
                    recognizedLines = rows.map { it.text },
                )
                onDone()
            } catch (e: Exception) {
                error = "글자를 읽지 못했어요: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun startManualInput(onDone: () -> Unit) {
        container.pendingDraft = null
        onDone()
    }

    /** 영수증 보관함용으로 원본을 앱 내부 저장소(files/receipts)에 복사 */
    private fun copyToAppStorage(uri: Uri): File {
        val context = container.appContext
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("사진을 열 수 없어요")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        return file
    }
}

/**
 * 영수증 촬영 = ML Kit 문서 스캐너.
 * 영수증 테두리를 자동으로 찾아 잘라내고, 비스듬히 찍힌 걸 평평하게 펴고, 그림자·밝기를 보정해 준다.
 * 이렇게 전처리한 이미지로 글자를 읽으면 배경·기울어짐 때문에 생기는 오인식이 크게 줄어든다.
 * (스캐너 화면 안에서 갤러리 사진도 불러올 수 있음)
 */
@Composable
fun CaptureScreen(onClose: () -> Unit, onNext: () -> Unit) {
    val vm = appViewModel { CaptureViewModel(it) }
    val activity = LocalContext.current.findActivity()

    val scanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL) // 자르기·보정 필터 포함
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .build()
        )
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pageUri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.firstOrNull()?.imageUri
            if (pageUri != null) vm.recognize(pageUri, onNext) else vm.showError("스캔 결과가 없어요")
        }
    }

    fun startScan() {
        if (activity == null) {
            vm.showError("스캐너를 열 수 없어요")
            return
        }
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e -> vm.showError("스캐너를 열 수 없어요: ${e.message}") }
    }

    // 화면에 들어오면 바로 스캐너를 띄운다 (촬영 한 번으로 끝나도록). 취소하면 이 화면에 남음
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoStarted) {
            autoStarted = true
            startScan()
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "닫기") }
            Text("영수증 촬영", style = MaterialTheme.typography.titleLarge)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (vm.busy) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("영수증을 읽는 중…")
                }
            } else {
                Text(
                    "영수증을 화면에 맞춰 찍으면\n테두리를 자동으로 찾아 반듯하게 펴 줘요",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { startScan() },
                enabled = !vm.busy,
                modifier = Modifier.weight(1f),
            ) { Text("영수증 스캔") }
            OutlinedButton(
                onClick = { vm.startManualInput(onNext) },
                enabled = !vm.busy,
                modifier = Modifier.weight(1f),
            ) { Text("직접 입력") }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
