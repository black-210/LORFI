package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.MainViewModel
import com.example.acoustic.AcousticBand
import com.example.ui.components.CyberCard
import com.example.ui.components.FrequencyEnergyBar
import com.example.ui.components.StatusBadge
import com.example.ui.components.WaveformCanvas
import com.example.ui.theme.CyberNavyDark
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalRed
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun AcousticScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val modem = viewModel.acousticModem

    val txState by modem.txState.collectAsState()
    val rxState by modem.rxState.collectAsState()
    val liveWaveform by modem.liveWaveform.collectAsState()

    var inputMessage by remember { mutableStateOf("NEXUS-AIRGAP-KEY#9941") }
    var selectedBand by remember { mutableStateOf(modem.activeBand) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            modem.startListening()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavyDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("acoustic_screen_container")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "المودم الصوتي التكتيكي",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SignalCyan
                )
                Text(
                    text = "نقل واستقبال البيانات عبر أصوات وموجات التردد FSK",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )
            }
            StatusBadge(
                label = if (txState.isTransmitting) "بث نشط" else if (rxState.isListening) "استماع" else "خامل",
                isActive = txState.isTransmitting || rxState.isListening,
                activeColor = if (txState.isTransmitting) SignalAmber else SignalGreen
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Band Selection
        CyberCard {
            Text(
                text = "نطاق التردد الصوتي (Frequency Band):",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedBand == AcousticBand.AUDIBLE,
                    onClick = {
                        selectedBand = AcousticBand.AUDIBLE
                        modem.activeBand = AcousticBand.AUDIBLE
                    },
                    label = { Text(text = "مسموع (1.8-2.6 kHz)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SignalCyan.copy(alpha = 0.2f),
                        selectedLabelColor = SignalCyan
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedBand == AcousticBand.ULTRASONIC,
                    onClick = {
                        selectedBand = AcousticBand.ULTRASONIC
                        modem.activeBand = AcousticBand.ULTRASONIC
                    },
                    label = { Text(text = "فوق صوتي (17.5-19.5 kHz)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SignalAmber.copy(alpha = 0.2f),
                        selectedLabelColor = SignalAmber
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = selectedBand.subtitle,
                fontSize = 11.sp,
                color = TextSecondaryDark,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Real-Time Oscilloscope / Audio Spectrum Analyzer
        CyberCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Oscilloscope",
                        tint = SignalCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مرسمة الذبذبات الحية (Live Oscilloscope)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalCyan
                    )
                }
                Text(
                    text = "${rxState.signalLevelDb.toInt()} dB",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (rxState.signalLevelDb > -45f) SignalGreen else TextMutedDark
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            WaveformCanvas(waveData = liveWaveform, height = 90.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // Frequency Bins Energy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FrequencyEnergyBar(
                    label = "SYNC",
                    freqHz = "${selectedBand.syncFreq.toInt()}Hz",
                    energy = rxState.syncEnergy,
                    barColor = SignalAmber,
                    modifier = Modifier.weight(1f)
                )
                FrequencyEnergyBar(
                    label = "MARK(1)",
                    freqHz = "${selectedBand.markFreq.toInt()}Hz",
                    energy = rxState.markEnergy,
                    barColor = SignalGreen,
                    modifier = Modifier.weight(1f)
                )
                FrequencyEnergyBar(
                    label = "SPACE(0)",
                    freqHz = "${selectedBand.spaceFreq.toInt()}Hz",
                    energy = rxState.spaceEnergy,
                    barColor = SignalCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Transmitter Section
        CyberCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = SignalAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "جهاز الإرسال الصوتي (Acoustic Transmitter)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SignalAmber
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = inputMessage,
                onValueChange = { inputMessage = it },
                label = { Text("البيانات أو الرسالة المراد بثها صوتياً") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("acoustic_input_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SignalAmber,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedLabelColor = SignalAmber
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick preset pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { inputMessage = "LAT:24.7136,LON:46.6753#SOS" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("إحداثيات SOS", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = { inputMessage = "KEY-RSA2048-A8F0-B9C3" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("مفتاح سري", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = { inputMessage = "FILE:hello.txt:WAVENET-OK" },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ملف نصي", fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (txState.isTransmitting) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "جار توليد النغمات وبث الموجات عبر السماعة...",
                            fontSize = 11.sp,
                            color = SignalAmber
                        )
                        Text(
                            text = "${(txState.progress * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = SignalAmber,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { txState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SignalAmber,
                        trackColor = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { modem.stopTransmission() },
                        colors = ButtonDefaults.buttonColors(containerColor = SignalRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stop_tx_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إيقاف البث الصوتي فوراً")
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.transmitAcousticText(inputMessage) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalAmber,
                        contentColor = CyberNavyDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("broadcast_acoustic_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بدء البث الصوتي عبر السماعة", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Receiver Section
        CyberCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (rxState.isListening) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = if (rxState.isListening) SignalGreen else TextSecondaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "جهاز الاستقبال الصوتي (Acoustic Receiver)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalGreen
                    )
                }
                StatusBadge(
                    label = if (rxState.syncDetected) "نغمة المزامنة ممسوكة!" else if (rxState.isListening) "استماع للميكروفون" else "متوقف",
                    isActive = rxState.isListening,
                    activeColor = if (rxState.syncDetected) SignalAmber else SignalGreen
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!rxState.isListening) {
                Button(
                    onClick = {
                        if (hasMicPermission) {
                            modem.startListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalGreen,
                        contentColor = CyberNavyDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_listening_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تشغيل الميكروفون والاستماع للموجات", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { modem.stopListening() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stop_listening_button")
                ) {
                    Icon(Icons.Default.MicOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إيقاف الاستماع")
                }
            }

            AnimatedVisibility(visible = rxState.lastDecodedText.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberSurfaceElevated)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "آخر حزمة صوتية تم فك تشفيرها بنجاح:",
                        fontSize = 11.sp,
                        color = SignalCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rxState.lastDecodedText,
                        fontSize = 13.sp,
                        color = TextPrimaryDark,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "إجمالي البيانات المستلمة: ${rxState.decodedBytesCount} بايت",
                        fontSize = 10.sp,
                        color = TextSecondaryDark,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
