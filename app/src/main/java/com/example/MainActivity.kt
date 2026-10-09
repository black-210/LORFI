package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AcousticScreen
import com.example.ui.screens.InspectorScreen
import com.example.ui.screens.Nexus6GScreen
import com.example.ui.screens.OpticalScreen
import com.example.ui.theme.CyberNavyDark
import com.example.ui.theme.CyberSurfaceDark
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SignalAmber
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.SignalGreen
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val selectedTab by viewModel.selectedTab.collectAsState()
                val userFeedback by viewModel.userFeedback.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(userFeedback) {
                    userFeedback?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearFeedback()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "WAVENET 6G PROTOCOL",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            color = SignalCyan
                                        )
                                        Text(
                                            text = "اتصال ونقل ملفات بدون إنترنت • P2P Zero-Cloud",
                                            fontSize = 10.sp,
                                            color = TextSecondaryDark
                                        )
                                    }

                                    // Hardware Security & Air-Gap Badge
                                    Surface(
                                        color = Color(0xFF1E293B),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.border(0.5.dp, SignalGreen, RoundedCornerShape(6.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(SignalGreen)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "AIR-GAP",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SignalGreen,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = CyberNavyDark,
                                titleContentColor = TextPrimaryDark
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            containerColor = CyberSurfaceDark,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { viewModel.setTab(0) },
                                icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Acoustic") },
                                label = { Text("أصوات الموجات", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberNavyDark,
                                    selectedTextColor = SignalCyan,
                                    indicatorColor = SignalCyan,
                                    unselectedIconColor = TextSecondaryDark,
                                    unselectedTextColor = TextSecondaryDark
                                ),
                                modifier = Modifier.testTag("nav_tab_acoustic")
                            )

                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { viewModel.setTab(1) },
                                icon = { Icon(Icons.Default.QrCode, contentDescription = "Optical") },
                                label = { Text("باركود متدفق", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberNavyDark,
                                    selectedTextColor = SignalAmber,
                                    indicatorColor = SignalAmber,
                                    unselectedIconColor = TextSecondaryDark,
                                    unselectedTextColor = TextSecondaryDark
                                ),
                                modifier = Modifier.testTag("nav_tab_optical")
                            )

                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { viewModel.setTab(2) },
                                icon = { Icon(Icons.Default.CellTower, contentDescription = "Nexus 6G") },
                                label = { Text("بروتوكول 6G", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberNavyDark,
                                    selectedTextColor = SignalGreen,
                                    indicatorColor = SignalGreen,
                                    unselectedIconColor = TextSecondaryDark,
                                    unselectedTextColor = TextSecondaryDark
                                ),
                                modifier = Modifier.testTag("nav_tab_nexus_6g")
                            )

                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { viewModel.setTab(3) },
                                icon = { Icon(Icons.Default.Security, contentDescription = "Inspector") },
                                label = { Text("فاحص الحزم", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberNavyDark,
                                    selectedTextColor = SignalCyan,
                                    indicatorColor = SignalCyan,
                                    unselectedIconColor = TextSecondaryDark,
                                    unselectedTextColor = TextSecondaryDark
                                ),
                                modifier = Modifier.testTag("nav_tab_inspector")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            0 -> AcousticScreen(viewModel)
                            1 -> OpticalScreen(viewModel)
                            2 -> Nexus6GScreen(viewModel)
                            3 -> InspectorScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
