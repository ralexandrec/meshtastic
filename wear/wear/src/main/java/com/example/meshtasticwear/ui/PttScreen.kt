package com.example.meshtasticwear.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.example.meshtasticwear.R

@Composable
fun PttScreen(
    viewModel: PttViewModel,
    onTriggerSpeech: () -> Unit,
    onTriggerTextInput: (String) -> Unit
) {
    val connectionStatus by viewModel.connectionStatus
    val isConnected by viewModel.isConnected
    val isVoiceMode by viewModel.isVoiceMode
    val isRecording by viewModel.isRecording
    val messages = viewModel.messages
    var showSayboardWarning by viewModel.showSayboardWarning
    var showSettingsDialog by viewModel.showSettingsDialog

    val listState = rememberScalingLazyListState()
    val innerScrollState = rememberScrollState()

    // Automatically scrolls to the last received/sent message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            innerScrollState.animateScrollTo(innerScrollState.maxValue)
        }
    }

    // 1. Circular Layout Scaffold (Curved Clock and Scrollbar)
    Scaffold(
        timeText = {
            TimeText()
        },
        positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(
                    top = 28.dp, // Space for curved clock
                    start = 10.dp,
                    end = 10.dp,
                    bottom = 28.dp
                )
            ) {
                // 2. Connection Status: Minimalist Compact Chip at the Top
                item {
                    CompactChip(
                        onClick = { showSettingsDialog = true },
                        label = { 
                            Text(
                                text = connectionStatus.uppercase(), 
                                fontSize = 9.sp, 
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ) 
                        },
                        colors = ChipDefaults.chipColors(
                            backgroundColor = if (isConnected) {
                                MaterialTheme.colors.primary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colors.error.copy(alpha = 0.2f)
                            },
                            contentColor = if (isConnected) MaterialTheme.colors.primary else MaterialTheme.colors.error
                        ),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // 3. LoRa History: Semantic AppCard with Internal Scrolling
                item {
                    TitleCard(
                        onClick = { // No action or expanded history
                        },
                        title = {
                            Text(
                                text = stringResource(id = R.string.radio_mesh_brasil),
                                fontSize = 8.sp,
                                color = MaterialTheme.colors.secondary
                            )
                        },
                        modifier = Modifier.fillMaxWidth(0.78f)
                    ) {
                        if (messages.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.waiting_for_traffic),
                                fontSize = 9.sp,
                                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Internal message scrolling (no line limits to allow complete reading)
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp) // Fixed height to allow internal scrolling
                                    .verticalScroll(innerScrollState)
                            ) {
                                messages.forEach { msg ->
                                    if (msg.isGps && msg.latitude != null && msg.longitude != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = msg.fullDisplay,
                                                fontSize = 9.sp,
                                                modifier = Modifier.weight(1f),
                                                textAlign = TextAlign.Left
                                            )
                                            val context = LocalContext.current
                                            CompactButton(
                                                onClick = {
                                                    viewModel.openMap(msg.latitude, msg.longitude, context)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.2f),
                                                    contentColor = MaterialTheme.colors.primary
                                                ),
                                                modifier = Modifier.size(24.dp).semantics { contentDescription = "Mapa" }
                                            ) {
                                                Text(text = "🗺️", fontSize = 10.sp)
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = msg.fullDisplay,
                                            fontSize = 9.sp,
                                            textAlign = TextAlign.Left
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Central Control (PTT / TXT) and Secondary Buttons Grouped in the Same Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mode Switch Button (Left Side, aligned to the center)
                        CompactButton(
                            onClick = { viewModel.toggleMode() },
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.surface,
                                contentColor = MaterialTheme.colors.onSurface
                            ),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text(
                                text = if (isVoiceMode) "🎙️" else "💬", 
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Main PTT / TXT Button (Center)
                        if (isVoiceMode) {
                            Button(
                                onClick = { // Event handled in pointerInput
                                },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = if (isRecording) MaterialTheme.colors.secondary else MaterialTheme.colors.primary,
                                    contentColor = MaterialTheme.colors.onPrimary
                                ),
                                modifier = Modifier
                                    .size(72.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                viewModel.startVoiceRecording()
                                                try {
                                                    awaitRelease()
                                                } finally {
                                                    viewModel.stopVoiceRecordingAndTrigger(onTriggerSpeech)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Text(
                                    text = if (isRecording) stringResource(id = R.string.ptt_talk) else stringResource(id = R.string.ptt_button),
                                    fontSize = 14.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                                )
                            }
                        } else {
                            // Text Mode: The PTT becomes a button that triggers direct typing
                            Button(
                                onClick = { onTriggerTextInput("Digite:") },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = MaterialTheme.colors.primary,
                                    contentColor = MaterialTheme.colors.onPrimary
                                ),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.txt_button),
                                    fontSize = 14.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Right Side (GPS and optionally Battery)
                        if (viewModel.showBattery.value) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // GPS Button (PTT Upper Line)
                                CompactButton(
                                    onClick = { viewModel.sendLocation(-23.5615, -46.6560, 760.0) },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = MaterialTheme.colors.surface,
                                        contentColor = MaterialTheme.colors.onSurface
                                    ),
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Text(text = "📍", fontSize = 11.sp)
                                }

                                // Battery Button (PTT Bottom Line)
                                CompactButton(
                                    onClick = { viewModel.sendBatteryStatus(85) },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = MaterialTheme.colors.surface,
                                        contentColor = MaterialTheme.colors.onSurface
                                    ),
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Text(text = "🔋", fontSize = 11.sp)
                                }
                            }
                        } else {
                            // GPS Only (Aligned in the same row to the center of the PTT)
                            CompactButton(
                                onClick = { viewModel.sendLocation(-23.5615, -46.6560, 760.0) },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = MaterialTheme.colors.surface,
                                    contentColor = MaterialTheme.colors.onSurface
                                ),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text(text = "📍", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Top vignette overlay for time text readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.9f),
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
            )
        }
    }

    // 6. Wear OS Native Alert if Sayboard is not installed
    Dialog(
        showDialog = showSayboardWarning,
        onDismissRequest = { showSayboardWarning = false }
    ) {
        Alert(
            title = {
                Text(
                    text = stringResource(id = R.string.sayboard_missing_title),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.error,
                    fontSize = 12.sp
                )
            },
            positiveButton = {
                Button(
                    onClick = { showSayboardWarning = false },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text(stringResource(id = R.string.ok), fontSize = 11.sp)
                }
            },
            negativeButton = {}
        ) {
            Text(
                text = stringResource(id = R.string.sayboard_missing_message),
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
        }
    }

    // 7. Connection Settings Dialog (TCP / BLE / Disconnect)
    Dialog(
        showDialog = showSettingsDialog,
        onDismissRequest = { showSettingsDialog = false }
    ) {
        Alert(
            title = {
                Text(
                    text = stringResource(id = R.string.configure_connection),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp
                )
            },
            positiveButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text(stringResource(id = R.string.back), fontSize = 11.sp)
                }
            },
            negativeButton = {}
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.status_label, connectionStatus),
                    fontSize = 8.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
                )
                
                Chip(
                    onClick = {
                        viewModel.switchConnection(com.example.meshtasticwear.data.TcpMeshClient("10.0.2.2", 4403))
                        showSettingsDialog = false
                    },
                    label = { Text(stringResource(id = R.string.connect_tcp), fontSize = 10.sp) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Chip(
                    onClick = {
                        viewModel.switchConnection(com.example.meshtasticwear.data.BleMeshClient(simulateSuccess = true))
                        showSettingsDialog = false
                    },
                    label = { Text(stringResource(id = R.string.connect_ble), fontSize = 10.sp) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                ToggleChip(
                    checked = viewModel.showBattery.value,
                    onCheckedChange = { viewModel.showBattery.value = it },
                    label = { Text(stringResource(id = R.string.show_battery), fontSize = 10.sp) },
                    toggleControl = {
                        Switch(
                            checked = viewModel.showBattery.value,
                            onCheckedChange = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Chip(
                    onClick = {
                        viewModel.disconnectFromMesh()
                        showSettingsDialog = false
                    },
                    label = { Text(stringResource(id = R.string.disconnect), fontSize = 10.sp) },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = MaterialTheme.colors.error.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colors.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
