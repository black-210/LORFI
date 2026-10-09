package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.MainViewModel
import com.example.optical.QrCameraAnalyzer
import com.example.ui.components.ChunkGridMatrix
import com.example.ui.components.CyberCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CyberNavyDark
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalRed
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import java.util.concurrent.Executors

@Composable
fun OpticalScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = viewModel.opticalEngine

    val txState by engine.txState.collectAsState()
    val rxState by engine.rxState.collectAsState()

    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Transmitter, 1 = Receiver
    var textToStream by remember { mutableStateOf("WAVENET-OPTICAL-BURST: Ultra-dense matrix stream protocol with Fountain FEC framing.") }
    var fpsValue by remember { mutableStateOf(4f) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavyDark)
            .padding(16.dp)
            .testTag("optical_screen_container")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "الناقل الضوئي بالباركود المتدفق",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SignalAmber
                )
                Text(
                    text = "تقطيع وبث الملفات الضخمة كشلال باركود QR عالي السرعة",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )
            }
            StatusBadge(
                label = if (txState.isStreaming) "بث ضوئي" else if (rxState.isReceiving) "التقاط" else "جاهز",
                isActive = txState.isStreaming || rxState.isReceiving,
                activeColor = SignalAmber
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subtabs
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Color(0xFF0F172A),
            contentColor = SignalAmber,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جهاز الإرسال (TX Stream)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الماسح المستقبل (RX Camera)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activeSubTab == 0) {
            // TRANSMITTER VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // QR Display Frame
                CyberCard(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    borderColor = SignalAmber.copy(alpha = 0.5f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (txState.currentBitmap != null) {
                            Image(
                                bitmap = txState.currentBitmap!!.asImageBitmap(),
                                contentDescription = "Stream QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("لا يوجد محتوى", color = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "الحزمة: ${txState.currentFrameIndex} / ${txState.totalFrames}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignalAmber,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "معرف الملف: ${txState.fileId}",
                            fontSize = 12.sp,
                            color = TextSecondaryDark,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    LinearProgressIndicator(
                        progress = {
                            if (txState.totalFrames > 0) txState.currentFrameIndex.toFloat() / txState.totalFrames else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SignalAmber,
                        trackColor = Color(0xFF1E293B)
                    )

                    // Controls: Play / Pause, Prev / Next
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { engine.previousFrame() },
                            enabled = !txState.isStreaming
                        ) {
                            Icon(Icons.Default.FastRewind, contentDescription = "Prev", tint = SignalAmber)
                        }

                        Button(
                            onClick = {
                                if (txState.isStreaming) engine.pauseStreaming()
                                else engine.startStreaming(fpsValue.toInt())
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (txState.isStreaming) SignalRed else SignalAmber,
                                contentColor = CyberNavyDark
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (txState.isStreaming) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (txState.isStreaming) "إيقاف مؤقت" else "بدء التدفق")
                        }

                        IconButton(
                            onClick = { engine.nextFrame() },
                            enabled = !txState.isStreaming
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = "Next", tint = SignalAmber)
                        }
                    }

                    // FPS Slider
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("سرعة التدفق (Frames/Sec):", fontSize = 11.sp, color = TextSecondaryDark)
                            Text("${fpsValue.toInt()} FPS", fontSize = 11.sp, color = SignalAmber, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = fpsValue,
                            onValueChange = {
                                fpsValue = it
                                if (txState.isStreaming) engine.startStreaming(it.toInt())
                            },
                            valueRange = 1f..12f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = SignalAmber,
                                activeTrackColor = SignalAmber
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Input payload configuration
                CyberCard {
                    Text(
                        text = "إعداد حمولة الباركود:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = textToStream,
                        onValueChange = { textToStream = it },
                        label = { Text("النص أو محتوى الملف للتقطيع") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SignalAmber,
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.prepareOpticalText(textToStream, "payload.txt") },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalAmber, contentColor = CyberNavyDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("توليد وتجزئة الحزم", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                val demoFile = viewModel.createSampleTestFile("tactical_doc.dat", 4)
                                viewModel.prepareOpticalFile(demoFile)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ملف تجريبي (4 KB)", fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            // RECEIVER SCANNER VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Camera Viewfinder Card
                CyberCard(borderColor = SignalGreen.copy(alpha = 0.5f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "كاميرا مسح الباركود المتدفق",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignalGreen
                        )
                        IconButton(onClick = { engine.resetReceiver() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = TextSecondaryDark)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!hasCameraPermission) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E293B)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = TextMutedDark, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("يلزم إذن الكاميرا لمسح باركود الجهاز الآخر", color = TextSecondaryDark, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = SignalGreen, contentColor = CyberNavyDark)
                            ) {
                                Text("منح إذن الكاميرا")
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, SignalGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx)
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    val executor = Executors.newSingleThreadExecutor()

                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            .also {
                                                it.setAnalyzer(
                                                    executor,
                                                    QrCameraAnalyzer { qrContent ->
                                                        viewModel.onScannedBarcode(qrContent)
                                                    }
                                                )
                                            }

                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.DEFAULT_BACK_CAMERA,
                                                preview,
                                                imageAnalysis
                                            )
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))

                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Crosshair overlay
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .align(Alignment.Center)
                                    .border(2.dp, SignalGreen, RoundedCornerShape(8.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (rxState.lastScannedInfo.isNotEmpty()) rxState.lastScannedInfo else "وجّه الكاميرا إلى شاشة الجهاز الآخر لالتقاط الشلال",
                        fontSize = 11.sp,
                        color = if (rxState.lastScannedInfo.isNotEmpty()) SignalGreen else TextSecondaryDark,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Chunk Matrix & Status
                CyberCard {
                    ChunkGridMatrix(
                        totalChunks = rxState.totalChunks,
                        receivedIndices = rxState.receivedChunkIndices
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (rxState.missingChunks.isNotEmpty() && !rxState.isComplete) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "الحزم المفقودة: ${rxState.missingChunks.size}",
                                fontSize = 12.sp,
                                color = SignalRed,
                                fontWeight = FontWeight.Bold
                            )
                            val nackBmp = engine.generateMissingChunksNackQr()
                            if (nackBmp != null) {
                                OutlinedButton(
                                    onClick = { /* show dialog or popup */ },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalRed)
                                ) {
                                    Text("عرض باركود طلب الفاقد (NACK)", fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = rxState.isComplete) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SignalGreen.copy(alpha = 0.15f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SignalGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اكتمل استلام وتجميع الملف بنجاح 100%!", fontWeight = FontWeight.Bold, color = SignalGreen)
                            }
                            rxState.completedText?.let {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = it,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextPrimaryDark
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
