package com.example.offlinepos.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.offlinepos.hardware.QrCodeHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ServerStatusDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    onOpenStaffClient: () -> Unit,
) {
    val context = LocalContext.current
    val isRunning by viewModel.isServerRunning.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val receivedCount by viewModel.serverReceivedCount.collectAsState()
    val storeProfile by viewModel.storeProfile.collectAsState()

    val qrBitmap: Bitmap? = remember(serverUrl, isRunning) {
        if (isRunning && serverUrl.isNotBlank()) {
            QrCodeHelper.generateQrBitmap(serverUrl, 320)
        } else null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("server_status_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = if (isRunning) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (isRunning) Color(0xFF16A34A) else Color(0xFFDC2626),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("🌐 15C Main POS Server", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = if (isRunning) "🟢 ဆာဗာ အွန်လိုင်း (Online - Direct Web Portal)" else "🔴 ဆာဗာ ရပ်တန့်ထားသည်",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRunning) Color(0xFF16A34A) else Color(0xFFDC2626)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_server_dialog_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // QR Code Card for Staff Scanning
                if (isRunning && qrBitmap != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(14.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📱 ဆိုင်ဝန်ထမ်းများ ဖုန်းဖြင့် Scan ဖတ်ရန် QR Code", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Server Connection QR Code",
                                modifier = Modifier
                                    .size(170.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(8.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Wi-Fi ကွန်ရက်တစ်ခုတည်းတွင် ဝန်ထမ်းဖုန်းဖြင့် Camera သို့မဟုတ် Client App ဖြင့် Scan ဖတ်ပါ",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Server IP and URL info
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ဆာဗာ လိပ်စာ (Server URL):", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(serverUrl, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Server URL", serverUrl))
                                    Toast.makeText(context, "Server URL ကူးယူပြီးပါပြီ: $serverUrl", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Received Products Count & Activity
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ဝန်ထမ်းများမှ ပေါင်းထည့်ပြီးသော ပစ္စည်း:", fontSize = 11.sp, color = Color(0xFF15803D))
                                Text("$receivedCount ခု", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF16A34A))
                            }
                            Text("Database ထဲသို့ ပေါင်းထည့်ပြီး", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ဆာဗာ ရပ်တန့်နေပါသည်", fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("ဆိုင်ဝန်ထမ်းများ ချိတ်ဆက်နိုင်ရန် အောက်ပါ ခလုတ်ကို နှိပ်၍ ဆာဗာကို စတင်ပါ", fontSize = 11.5.sp, textAlign = TextAlign.Center)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRunning) {
                        OutlinedButton(
                            onClick = { viewModel.stopLocalServer() },
                            modifier = Modifier.weight(1f).testTag("stop_server_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFDC2626))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ဆာဗာ ရပ်မည်", fontSize = 11.5.sp, color = Color(0xFFDC2626))
                        }

                        Button(
                            onClick = {
                                viewModel.stopLocalServer()
                                viewModel.startLocalServer()
                                Toast.makeText(context, "ဆာဗာ ပြန်လည်စတင်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).testTag("restart_server_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restart ပြုလုပ်မည်", fontSize = 11.5.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.startLocalServer()
                                Toast.makeText(context, "ဆာဗာကို အောင်မြင်စွာ ဖွင့်လှစ်ပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("start_server_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🚀 ဆာဗာ စတင်ဖွင့်လှစ်မည်")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Button to open Staff Client Screen
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenStaffClient()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("open_staff_client_from_dialog_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("📱 ဆိုင်ဝန်ထမ်း Client စနစ် ဖွင့်မည်", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Test via browser
                if (isRunning && serverUrl.isNotBlank()) {
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Browser ဖွင့်မရပါ: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("🌐 ဖုန်း Web Browser ဖြင့် စမ်းသပ်ကြည့်မည်", fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}
