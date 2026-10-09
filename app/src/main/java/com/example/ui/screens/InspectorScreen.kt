package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.MainViewModel
import com.example.protocol.ProtocolPacketLog
import com.example.protocol.TransferredFile
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
fun InspectorScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val packetLogs by viewModel.packetLogs.collectAsState()
    val receivedFiles by viewModel.receivedFiles.collectAsState()

    var activeSubTab by remember { mutableIntStateOf(0) } // 0 = Packet Log, 1 = File Vault
    var selectedFileForPreview by remember { mutableStateOf<TransferredFile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavyDark)
            .padding(16.dp)
            .testTag("inspector_screen_container")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "فاحص الحزم وخزينة الملفات",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SignalCyan
                )
                Text(
                    text = "تتبع الهيدر والـ CRC والتدفق السداسي عشري (Hex Inspector)",
                    fontSize = 12.sp,
                    color = TextSecondaryDark
                )
            }
            StatusBadge(
                label = "حزم مسجلة: ${packetLogs.size}",
                isActive = true,
                activeColor = SignalCyan
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Subtabs
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Color(0xFF0F172A),
            contentColor = SignalCyan,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("سجل الحزم (Packet Sniffer)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("الملفات المستلمة (${receivedFiles.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activeSubTab == 0) {
            // PACKET LOG VIEW
            if (packetLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لم يتم تسجيل أي حزم حتى الآن. ابدأ ببث صوتي أو باركود متدفق أو اتصال 6G.", color = TextMutedDark, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(packetLogs) { log ->
                        val isOut = log.direction == ProtocolPacketLog.Direction.OUTGOING
                        val mediumColor = Color(log.medium.badgeColorHex)

                        CyberCard(borderColor = Color(0xFF1E293B)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isOut) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = if (isOut) SignalAmber else SignalGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = log.medium.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = mediumColor
                                    )
                                }
                                Text(
                                    text = "${log.payloadLength} B",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondaryDark
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.summary,
                                fontSize = 12.sp,
                                color = TextPrimaryDark,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            // Hex snippet
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF070B14))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = log.hexSnippet,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = SignalCyan
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // FILE VAULT VIEW
            if (receivedFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("لا توجد ملفات مستلمة بعد. قم بالاستقبال عبر الصوت أو الباركود أو 6G.", color = TextMutedDark, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(receivedFiles) { file ->
                        CyberCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = SignalCyan,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = file.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimaryDark
                                        )
                                        Text(
                                            text = "${file.formattedSize} • ${file.medium.displayName}",
                                            fontSize = 11.sp,
                                            color = TextSecondaryDark
                                        )
                                    }
                                }

                                Row {
                                    if (file.textContent != null) {
                                        IconButton(onClick = { selectedFileForPreview = file }) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = "View", tint = SignalCyan)
                                        }
                                        IconButton(onClick = {
                                            clipboardManager.setText(AnnotatedString(file.textContent))
                                            viewModel.showFeedback("تم نسخ المحتوى إلى الحافظة")
                                        }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondaryDark)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Preview Dialog
        selectedFileForPreview?.let { file ->
            AlertDialog(
                onDismissRequest = { selectedFileForPreview = null },
                title = { Text(file.name, color = SignalCyan) },
                text = {
                    Column {
                        Text(
                            text = file.textContent ?: "محتوى ثنائي (Binary Data)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextPrimaryDark
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedFileForPreview = null }) {
                        Text("إغلاق", color = SignalCyan)
                    }
                },
                containerColor = CyberSurfaceDark
            )
        }
    }
}
