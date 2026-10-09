package com.example.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainViewModel
import com.example.nexus6g.NexusRole
import com.example.ui.components.CyberCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CyberNavyDark
import com.example.ui.theme.CyberSurfaceDark
import com.example.ui.theme.CyberSurfaceElevated
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.SignalRed
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun Nexus6GScreen(viewModel: MainViewModel) {
    val engine = viewModel.nexus6gEngine
    val telemetry by engine.telemetry.collectAsState()
    val messages by engine.messages.collectAsState()
    val transferProgress by engine.transferProgress.collectAsState()
    val currentRole by engine.currentRole.collectAsState()
    val localIp by engine.localIpAddress.collectAsState()

    var targetIpInput by remember { mutableStateOf("192.168.43.1") }
    var chatInput by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavyDark)
            .padding(16.dp)
            .testTag("nexus_6g_screen_container")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "بروتوكول شبكة 6G المباشرة",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SignalGreen
                )
                Text(
                    text = "اتصال مباشر عالي السرعة بين جهازين دون الحاجة لإنترنت أو بلوتوث",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )
            }
            StatusBadge(
                label = if (telemetry.isConnected) "رابط 6G متصل" else "غير متصل",
                isActive = telemetry.isConnected,
                activeColor = SignalGreen
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Role & Connection Setup Card
        CyberCard(borderColor = if (telemetry.isConnected) SignalGreen else Color(0xFF334155)) {
            Text(
                text = "وضع تشغيل العقدة (Node Operating Mode):",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentRole == NexusRole.GATEWAY_HOST,
                    onClick = {
                        if (!telemetry.isConnected) engine.startGateway()
                    },
                    label = { Text("المحطة المضيفة (Gateway Host)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SignalGreen.copy(alpha = 0.2f),
                        selectedLabelColor = SignalGreen
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = currentRole == NexusRole.PEER_CLIENT,
                    onClick = {
                        if (!telemetry.isConnected) {
                            engine.stop()
                        }
                    },
                    label = { Text("النظير المتصل (Peer Client)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SignalCyan.copy(alpha = 0.2f),
                        selectedLabelColor = SignalCyan
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (currentRole == NexusRole.GATEWAY_HOST) {
                // Gateway Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("عنوان البوابة المضيفة (Local IP):", fontSize = 11.sp, color = TextSecondaryDark)
                        Text("$localIp:${engine.port}", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = SignalGreen)
                    }

                    Row {
                        IconButton(onClick = { engine.refreshIp() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh IP", tint = TextSecondaryDark)
                        }
                        IconButton(onClick = { showQrDialog = !showQrDialog }) {
                            Icon(Icons.Default.QrCode, contentDescription = "Show QR", tint = SignalGreen)
                        }
                    }
                }

                if (showQrDialog) {
                    val qrBmp = viewModel.opticalEngine.generateQrBitmap("N6G://$localIp:${engine.port}", 250)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBmp.asImageBitmap(),
                            contentDescription = "Pairing QR",
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        if (telemetry.isConnected) engine.stop()
                        else engine.startGateway()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (telemetry.isConnected) SignalRed else SignalGreen,
                        contentColor = CyberNavyDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (telemetry.isConnected) Icons.Default.LinkOff else Icons.Default.CellTower, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (telemetry.isConnected) "إيقاف خادم البوابة" else "تشغيل بوابة 6G واستقبال النظير")
                }
            } else {
                // Peer Client Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetIpInput,
                        onValueChange = { targetIpInput = it },
                        label = { Text("IP البوابة المضيفة") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SignalCyan,
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )

                    Button(
                        onClick = {
                            if (telemetry.isConnected) engine.stop()
                            else engine.connectAsPeer(targetIpInput)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (telemetry.isConnected) SignalRed else SignalCyan,
                            contentColor = CyberNavyDark
                        ),
                        modifier = Modifier.height(54.dp)
                    ) {
                        Icon(if (telemetry.isConnected) Icons.Default.LinkOff else Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (telemetry.isConnected) "قطع" else "اتصال")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Link Telemetry HUD
        CyberCard(borderColor = Color(0xFF1E293B)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("السرعة الحالية (Throughput)", fontSize = 10.sp, color = TextSecondaryDark)
                    Text(telemetry.formattedSpeed, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SignalGreen, fontFamily = FontFamily.Monospace)
                }
                Column {
                    Text("زمن الاستجابة (Ping)", fontSize = 10.sp, color = TextSecondaryDark)
                    Text("${telemetry.pingMs} ms", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SignalCyan, fontFamily = FontFamily.Monospace)
                }
                Column {
                    Text("الحزم (Sent / Recv)", fontSize = 10.sp, color = TextSecondaryDark)
                    Text("${telemetry.packetsSent} / ${telemetry.packetsReceived}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SignalAmber, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // File Transfer Pipeline
        CyberCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("نقل الملفات عبر نفق 6G المباشر:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                Text("أقصى سرعة لشبكة Wi-Fi المحلية", fontSize = 10.sp, color = SignalGreen)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick demo file send buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val sample = viewModel.createSampleTestFile("data_chunk_250k.bin", 250)
                        engine.sendFile(sample)
                    },
                    enabled = telemetry.isConnected,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("إرسال ملف 250 KB", fontSize = 11.sp)
                }

                FilledTonalButton(
                    onClick = {
                        val sample = viewModel.createSampleTestFile("highres_pack_1mb.dat", 1024)
                        engine.sendFile(sample)
                    },
                    enabled = telemetry.isConnected,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("إرسال ملف 1 MB", fontSize = 11.sp)
                }
            }

            AnimatedVisibility(visible = transferProgress.isTransferring) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (transferProgress.isOutgoing) "جار الإرسال: ${transferProgress.fileName}" else "جار الاستلام: ${transferProgress.fileName}",
                            fontSize = 11.sp,
                            color = SignalCyan
                        )
                        Text(
                            text = transferProgress.formattedPercent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignalCyan,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { transferProgress.progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = SignalCyan,
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Direct P2P Instant Chat Console
        CyberCard(modifier = Modifier.weight(1f)) {
            Text("قناة المحادثة المباشرة بدون إنترنت (P2P Console):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondaryDark)
            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070B14))
                    .padding(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = "لا توجد رسائل بعد. اكتب رسالة لإرسالها فوراً إلى النظير المتصل.",
                            fontSize = 11.sp,
                            color = TextMutedDark,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                items(messages) { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = if (msg.isFromSelf) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (msg.isFromSelf) SignalGreen.copy(alpha = 0.25f) else Color(0xFF1E293B))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column {
                                Text(
                                    text = msg.sender,
                                    fontSize = 9.sp,
                                    color = if (msg.isFromSelf) SignalGreen else SignalCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = msg.text,
                                    fontSize = 12.sp,
                                    color = TextPrimaryDark
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = { Text("اكتب رسالة مشفرة مباشرة...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SignalGreen,
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        engine.sendMessage(chatInput)
                        chatInput = ""
                    },
                    enabled = telemetry.isConnected && chatInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalGreen, contentColor = CyberNavyDark)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}
