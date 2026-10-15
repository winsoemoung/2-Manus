package com.example.offlinepos.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.asImageBitmap
import com.example.offlinepos.hardware.QrCodeHelper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.offlinepos.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PosViewModel,
) {
    val context = LocalContext.current
    val actor by viewModel.actor.collectAsState()
    val storeProfile by viewModel.storeProfile.collectAsState()
    val auditEvents by viewModel.auditEvents.collectAsState()

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }

    val googleAccount by viewModel.googleAccount.collectAsState()
    val lastGoogleBackupTime by viewModel.lastGoogleBackupTime.collectAsState()
    var googleEmailInput by remember(googleAccount) { mutableStateOf(googleAccount ?: "WINSOEMOUNG@gmail.com") }

    var showChangeOwnerPinDialog by remember { mutableStateOf(false) }
    var showChangeManagerPinDialog by remember { mutableStateOf(false) }

    var currentOwnerPinInput by remember { mutableStateOf("") }
    var newOwnerPinInput by remember { mutableStateOf("") }
    var confirmOwnerPinInput by remember { mutableStateOf("") }
    var ownerPinError by remember { mutableStateOf<String?>(null) }

    var currentManagerPinInput by remember { mutableStateOf("") }
    var newManagerPinInput by remember { mutableStateOf("") }
    var confirmManagerPinInput by remember { mutableStateOf("") }
    var managerPinError by remember { mutableStateOf<String?>(null) }

    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val receivedCount by viewModel.serverReceivedCount.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }

    val serverQrBitmap = remember(serverUrl, isServerRunning) {
        if (isServerRunning && serverUrl.isNotBlank()) {
            QrCodeHelper.generateQrBitmap(serverUrl, 260)
        } else null
    }

    // Store profile editing states
    var storeName by remember(storeProfile) { mutableStateOf(storeProfile.storeName) }
    var address by remember(storeProfile) { mutableStateOf(storeProfile.address) }
    var phone by remember(storeProfile) { mutableStateOf(storeProfile.phone) }
    var headerNote by remember(storeProfile) { mutableStateOf(storeProfile.headerNote) }
    var footerMessage by remember(storeProfile) { mutableStateOf(storeProfile.footerMessage) }
    var backupSchedule by remember(storeProfile) { mutableStateOf(storeProfile.autoBackupSchedule) }

    // Logo image picker
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.saveStoreLogo(uri) { success, msg ->
                viewModel.setBanner(msg)
            }
        }
    }

    // Database file import picker
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importDatabaseFile(uri) { success, msg ->
                backupStatusMessage = msg
                viewModel.setBanner(msg)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("စနစ် သတ်မှတ်ချက်များ (Settings)", fontWeight = FontWeight.Bold) },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Store Owner Header Profile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        StoreLogoAvatar(
                            customLogoPath = storeProfile.customLogoPath,
                            contentDescription = "Lady Store Owner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            onClick = {
                                try {
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (e: Exception) {
                                    viewModel.setBanner("ပုံရွေးချယ်မှု မအောင်မြင်ပါ: ${e.localizedMessage}")
                                }
                            }
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                            shadowElevation = 2.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("📷", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = storeProfile.storeName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "ဆိုင်ရှင် / မန်နေဂျာ (Store Manager)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "ဖုန်း: ${storeProfile.phone}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        logoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    } catch (e: Exception) {
                                        viewModel.setBanner("ပုံရွေးချယ်မှု မအောင်မြင်ပါ: ${e.localizedMessage}")
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp).testTag("change_store_logo_button"),
                            ) {
                                Text("🖼️ ပုံပြောင်းမည်", fontSize = 11.5.sp)
                            }

                            if (!storeProfile.customLogoPath.isNullOrBlank()) {
                                TextButton(
                                    onClick = { viewModel.resetStoreLogo() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp).testTag("reset_store_logo_button"),
                                ) {
                                    Text("မူလပုံ ပြန်ထားမည်", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // Store & Receipt Customization Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ဆိုင်အချက်အလက်နှင့် ပြေစာ (Receipt) ပြင်ဆင်ရန်",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ဤအချက်အလက်များသည် 80mm Bluetooth Thermal ပြေစာနှင့် အက်ပ်တွင် အလိုအလျောက် ပေါ်မည်ဖြစ်ပါသည်",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = storeName,
                        onValueChange = { storeName = it },
                        label = { Text("ဆိုင်အမည် (Store Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("store_name_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = headerNote,
                        onValueChange = { headerNote = it },
                        label = { Text("ခေါင်းစီးစာသား (Header Note / Slogan)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("store_header_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("ဆိုင်လိပ်စာ (Address)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("store_address_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("ဆက်သွယ်ရန် ဖုန်းနံပါတ် (Phone)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("store_phone_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = footerMessage,
                        onValueChange = { footerMessage = it },
                        label = { Text("အောက်ခြေ နှုတ်ခွန်းဆက်စာသား (Footer Message)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("store_footer_input"),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.updateStoreProfile(
                                storeProfile.copy(
                                    storeName = storeName.trim(),
                                    address = address.trim(),
                                    phone = phone.trim(),
                                    headerNote = headerNote.trim(),
                                    footerMessage = footerMessage.trim(),
                                    autoBackupSchedule = backupSchedule,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_store_profile_button"),
                    ) {
                        Text("ဆိုင်အချက်အလက်များ သိမ်းဆည်းမည်")
                    }
                }
            }

            // Tax Configuration Card (0% to 100%)
            var taxRateState by remember(storeProfile.taxRatePercent) {
                mutableStateOf(if (storeProfile.taxRatePercent % 1.0 == 0.0) storeProfile.taxRatePercent.toInt().toString() else storeProfile.taxRatePercent.toString())
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "အခွန် သတ်မှတ်ချက် (Tax Settings)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (storeProfile.taxRatePercent > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "လက်ရှိ: ${if (storeProfile.taxRatePercent % 1.0 == 0.0) storeProfile.taxRatePercent.toInt() else storeProfile.taxRatePercent}%",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "အရောင်းပြေစာတွင် ထည့်သွင်းတွက်ချက်မည့် အခွန် ရာခိုင်နှုန်း (၀% မှ ၁၀၀% အထိ စိတ်ကြိုက် သတ်မှတ်နိုင်ပါသည်)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("အသုံးများသော အခွန်နှုန်းထားများ:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val presets = listOf(
                            Pair(0.0, "၀% (အခွန်မပါ)"),
                            Pair(5.0, "၅% (5%)"),
                            Pair(7.0, "၇% (7%)"),
                            Pair(10.0, "၁၀% (10%)"),
                        )
                        items(presets) { (rate, label) ->
                            FilterChip(
                                selected = storeProfile.taxRatePercent == rate,
                                onClick = {
                                    taxRateState = if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()
                                    viewModel.setTaxRate(rate)
                                },
                                label = { Text(label, fontSize = 11.5.sp) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = taxRateState,
                        onValueChange = { input ->
                            taxRateState = input
                            val num = input.toDoubleOrNull()
                            if (num != null && num in 0.0..100.0) {
                                viewModel.setTaxRate(num)
                            }
                        },
                        label = { Text("စိတ်ကြိုက် အခွန် ရာခိုင်နှုန်း (၀ - ၁၀၀ %)") },
                        trailingIcon = { Text("%", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("custom_tax_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val currentRateVal = taxRateState.toDoubleOrNull() ?: storeProfile.taxRatePercent
                    val sampleTax = (10000.0 * currentRateVal / 100.0).toLong()
                    val sampleTotal = 10000L + sampleTax

                    val formattedTax = "%,d".format(sampleTax)
                    val formattedTotal = "%,d".format(sampleTotal)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "💡 ဥပမာ - ၁၀,၀၀၀ ကျပ်ဖိုး ရောင်းချပါက:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "• အခွန် (${currentRateVal}%): $formattedTax Ks\n• စုစုပေါင်း ကျသင့်ငွေ: $formattedTotal Ks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Database Backup, Export & Import, Google Drive
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ဒေတာဘေ့စ် Backup၊ Export / Import နှင့် Google Drive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "အရောင်းမှတ်တမ်းများ၊ ပစ္စည်းစာရင်းများကို သိမ်းဆည်းရန် သို့မဟုတ် Google Drive သို့ တင်ရန်",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("အလိုအလျောက် Backup အချိန်ဇယား:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("DAILY" to "နေ့စဉ် (Daily)", "WEEKLY" to "အပတ်စဉ် (Weekly)", "MONTHLY" to "လစဉ် (Monthly)").forEach { (key, label) ->
                            FilterChip(
                                selected = backupSchedule == key,
                                onClick = {
                                    backupSchedule = key
                                    viewModel.updateStoreProfile(storeProfile.copy(autoBackupSchedule = key))
                                },
                                label = { Text(label, fontSize = 11.sp) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Export & Share to Google Drive
                        Button(
                            onClick = {
                                try {
                                    val exported = viewModel.exportDatabaseFile()
                                    if (exported != null) {
                                        val uri: Uri = try {
                                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exported)
                                        } catch (e: Exception) {
                                            Uri.fromFile(exported)
                                        }
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/octet-stream"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, "san thit POS ဒေတာ Backup")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Google Drive သို့ Upload ရန် သို့မဟုတ် ဖိုင်သိမ်းရန်"))
                                        backupStatusMessage = "Backup ဖိုင် (${exported.name}) ကို အောင်မြင်စွာ ထုတ်ယူပြီးပါပြီ"
                                    } else {
                                        backupStatusMessage = "Backup ထုတ်ယူခြင်း မအောင်မြင်ပါ"
                                    }
                                } catch (e: Exception) {
                                    backupStatusMessage = "Backup လုပ်ဆောင်ရာတွင် ချို့ယွင်းချက်ဖြစ်ပေါ်ပါသည်: ${e.localizedMessage}"
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_backup_button"),
                        ) {
                            Text("☁️ Google Drive သို့ တင်ရန် / Export", fontSize = 12.sp)
                        }

                        // Import & Restore
                        OutlinedButton(
                            onClick = {
                                try {
                                    importFileLauncher.launch(arrayOf("*/*"))
                                } catch (e: Exception) {
                                    backupStatusMessage = "ဖိုင်ရွေးချယ်မှုကို ဖွင့်မရပါ: ${e.localizedMessage}"
                                }
                            },
                            modifier = Modifier.weight(0.7f).testTag("import_backup_button"),
                        ) {
                            Text("📥 Restore ပြန်တင်မည်", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("👤 Google Account ဖြင့် အရန်သိမ်းဆည်းရန် Log In / Sync:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (googleAccount != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("✅ ချိတ်ဆက်ထားသော Google Account:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(googleAccount!!, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                if (lastGoogleBackupTime > 0) {
                                    val formattedTime = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(lastGoogleBackupTime))
                                    Text("နောက်ဆုံး Backup လုပ်ခဲ့သည့်အချိန်: $formattedTime", fontSize = 11.sp, color = Color(0xFF15803D))
                                } else {
                                    Text("Google Drive သို့ Backup မပြုလုပ်ရသေးပါ", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.disconnectGoogleAccount() },
                                modifier = Modifier.weight(1f).testTag("disconnect_google_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("အကောင့်ဖြုတ်မည်", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                            Button(
                                onClick = {
                                    try {
                                        val exported = viewModel.exportDatabaseFile()
                                        if (exported != null) {
                                            viewModel.recordGoogleBackupSuccess()
                                            val uri: Uri = try {
                                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exported)
                                            } catch (e: Exception) {
                                                Uri.fromFile(exported)
                                            }
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/octet-stream"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, "787 POS ဒေတာ Backup (${googleAccount})")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Google Drive သို့ တင်ရန်"))
                                            backupStatusMessage = "Google Drive သို့ Backup တင်ပို့ပြီးပါပြီ"
                                        }
                                    } catch (e: Exception) {
                                        backupStatusMessage = "ချို့ယွင်းချက်ဖြစ်ပေါ်ပါသည်: ${e.localizedMessage}"
                                    }
                                },
                                modifier = Modifier.weight(1.3f).testTag("backup_to_drive_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("☁️ Google Drive သို့ Backup", fontSize = 11.sp)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = googleEmailInput,
                            onValueChange = { googleEmailInput = it },
                            label = { Text("Google အကောင့် (Gmail)") },
                            placeholder = { Text("WINSOEMOUNG@gmail.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("google_account_input"),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                viewModel.connectGoogleAccount(googleEmailInput)
                            },
                            modifier = Modifier.fillMaxWidth().testTag("connect_google_account_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🔗 Google Account ဖြင့် Log in / ချိတ်ဆက်မည်")
                        }
                    }

                    backupStatusMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Quick Business Management Hub
            var showCustomerDialog by remember { mutableStateOf(false) }
            var showStockInDialog by remember { mutableStateOf(false) }
            var showShiftReportDialog by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🏢 ဆိုင်စီမံခန့်ခွဲမှု အမြန်လမ်းကြောင်း (Quick Hub)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showCustomerDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("👥 ဝယ်သူ & အကြွေး", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { showStockInDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("📦 ပစ္စည်းအဝင်", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showShiftReportDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("📑 နေ့ချုပ်ပိတ်သိမ်းခြင်း (Z-Report စစ်ဆေးမည်)")
                    }
                }
            }

            if (showCustomerDialog) {
                CustomerManagementDialog(viewModel = viewModel, onDismiss = { showCustomerDialog = false })
            }
            if (showStockInDialog) {
                StockInDialog(viewModel = viewModel, onDismiss = { showStockInDialog = false })
            }
            if (showShiftReportDialog) {
                ShiftReportDialog(viewModel = viewModel, onDismiss = { showShiftReportDialog = false })
            }

            // Cashier / Role Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("အသုံးပြုသူ အဆင့်ခွဲခြားမှု (Role-Based Access)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "လက်ရှိ: ${actor.userId} (လျှော့စျေးခွင့်ပြုချက်: ${if (actor.canApplyDiscount) "ရရှိထားသည်" else "မရရှိပါ"})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.switchRole("CASHIER", "") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ငွေကိုင်", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                pinInput = ""
                                pinError = null
                                showPinDialog = true
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("PIN ဖြင့် ဖွင့်မည်", fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("🔐 လုံခြုံရေး PIN နံပါတ်များ ပြောင်းလဲခြင်း (Change PINs):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                currentOwnerPinInput = ""
                                newOwnerPinInput = ""
                                confirmOwnerPinInput = ""
                                ownerPinError = null
                                showChangeOwnerPinDialog = true
                            },
                            modifier = Modifier.weight(1f).testTag("change_owner_pin_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🔑 ဆိုင်ရှင် PIN ပြောင်း", fontSize = 11.5.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                currentManagerPinInput = ""
                                newManagerPinInput = ""
                                confirmManagerPinInput = ""
                                managerPinError = null
                                showChangeManagerPinDialog = true
                            },
                            modifier = Modifier.weight(1f).testTag("change_manager_pin_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("👔 မန်နေဂျာ PIN ပြောင်း", fontSize = 11.5.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• ဆိုင်ရှင် မူလ PIN: 7870 (Full Access) | • မန်နေဂျာ မူလ PIN: 1234", fontSize = 11.sp, color = Color.Gray)
                }
            }

            // AI Superuser Privileges & Promo Code Card
            var aiSuperuserEnabled by remember { mutableStateOf(viewModel.isAiSuperuserGranted()) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🛡️ AI Superuser အခွင့်အရေးနှင့် Promo Code များ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "AI ထံ အမိန့်ပေးလိုက်သောအခါ 787 စနစ်ထဲမှ Code အသစ်ထည့်ခြင်း၊ Code အစားထိုးခြင်း၊ ဈေးနှုန်းနှင့် အခွန်နှုန်း ပြုပြင်ခြင်း အခွင့်အရေး အပြည့်အဝ ပေးထားပါသည်။",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Code Privileges",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (aiSuperuserEnabled) "အခွင့်အရေး ရရှိထားသည် (Authorized)" else "ပိတ်ထားသည်",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (aiSuperuserEnabled) Color(0xFF15803D) else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Switch(
                            checked = aiSuperuserEnabled,
                            onCheckedChange = {
                                aiSuperuserEnabled = it
                                viewModel.setAiSuperuserGranted(it)
                                viewModel.setBanner(if (it) "AI Superuser အခွင့်အရေး ဖွင့်လှစ်ပြီးပါပြီ" else "AI အခွင့်အရေး ပိတ်ထားပါသည်")
                            },
                            modifier = Modifier.testTag("settings_ai_superuser_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("🎟️ လက်ရှိ အသုံးပြုနိုင်သော Promo Code များ:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    val promos = viewModel.getPromoCodes()
                    promos.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• ${p.code}: ${p.description}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                val discLabel = if (p.discountPercent > 0) "${p.discountPercent}% Off" else "${"%,d".format(p.discountFlatMinor)} Ks Off"
                                Text(
                                    text = discLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 15A Main POS Server & Direct HTML5 Web Portal
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Wifi,
                                        contentDescription = "Main POS Server Logo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("🌐 Main POS Server (15A)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Direct HTML5 Web Portal စနစ်", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isServerRunning) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        ) {
                            Text(
                                text = if (isServerRunning) "📡 Server ON" else "📡 Server OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isServerRunning) Color(0xFF15803D) else Color(0xFFB91C1C),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("ဖုန်းထဲတွင် App မသွင်းလိုသော ဝန်ထမ်းများအတွက် Chrome/Browser မှတစ်ဆင့် Server IP သို့ ဝင်ရောက်၍ ကုန်ပစ္စည်းလှမ်းထည့်သည့် စနစ်", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isServerRunning) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isServerRunning) "🟢 Server Online (လည်ပတ်နေပါသည်)" else "🔴 Server ရပ်တန့်ထားပါသည်",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isServerRunning) Color(0xFF15803D) else Color(0xFFB91C1C)
                                )
                                if (isServerRunning) {
                                    Text(
                                        text = "Web Portal: $serverUrl",
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF166534),
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "ဝန်ထမ်းများထံမှ လက်ခံရရှိမှု: $receivedCount ခု",
                                        fontSize = 11.sp,
                                        color = Color(0xFF166534)
                                    )
                                }
                            }

                            Switch(
                                checked = isServerRunning,
                                onCheckedChange = { start ->
                                    if (start) viewModel.startLocalServer() else viewModel.stopLocalServer()
                                }
                            )
                        }
                    }

                    if (isServerRunning) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // URL Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Server Web Portal URL", serverUrl))
                                    Toast.makeText(context, "Web Portal လိပ်စာ ကူးယူပြီးပါပြီ:\n$serverUrl", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("📋 Link ကူးယူမည်", fontSize = 11.5.sp)
                            }

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Browser ဖွင့်မရပါ: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("🌐 Web Portal ဖွင့်မည်", fontSize = 11.5.sp)
                            }
                        }

                        // Direct QR Code display inside Settings
                        serverQrBitmap?.let { qr ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("📱 ဖုန်း Camera ဖြင့် Scan ဖတ်၍ Portal တိုက်ရိုက်ဖွင့်ရန် QR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Image(
                                        bitmap = qr.asImageBitmap(),
                                        contentDescription = "Server QR Code",
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White)
                                            .padding(6.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Wi-Fi ကွန်ရက်တစ်ခုတည်းတွင် ဝန်ထမ်းဖုန်းဖြင့် Scan ဖတ်ပါက Chrome/Browser ဖြင့် Direct Web Portal သို့ စက္ကန့်ပိုင်းအတွင်း တိုက်ရိုက်ရောက်ရှိပါမည်",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Features summary
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("✨ Remote Product Addition အင်္ဂါရပ်များ:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("• ဝန်ထမ်းအမည် (Staff Name) ထည့်သွင်းခြင်း", fontSize = 10.5.sp)
                                Text("• ကုန်ပစ္စည်းအမည်၊ အမျိုးအစား (Categories) ရွေးချယ်ခြင်း၊ ယူနစ်", fontSize = 10.5.sp)
                                Text("• ကင်မရာဖြင့် Barcode Scan ဖတ်ခြင်း သို့မဟုတ် SKU ထည့်သွင်းခြင်း", fontSize = 10.5.sp)
                                Text("• ရောင်းဈေး၊ ဝယ်ရင်းဈေး၊ လက်ကျန်အရေအတွက် နှင့် သတိပေးလက်ကျန် သတ်မှတ်ခြင်း", fontSize = 10.5.sp)
                                Text("• ပေါင်းထည့်လိုက်သော ပစ္စည်းများ Database ထဲသို့ တိုက်ရိုက်ရောက်ရှိခြင်း", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF15803D))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showServerDialog = true },
                            modifier = Modifier.weight(1f).testTag("open_server_status_btn"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("📡 Fullscreen QR & Status", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.setStaffClientMode(true) },
                            modifier = Modifier.weight(1f).testTag("switch_to_client_mode_btn"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("📱 ဝန်ထမ်း Client App သို့", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Terminal Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("စနစ် အချက်အလက်များ", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• ဆိုင်: ${storeProfile.storeName}", style = MaterialTheme.typography.bodySmall)
                    Text("• ဗားရှင်း: 15A (15A_787_AI_update.apk)", style = MaterialTheme.typography.bodySmall)
                    Text("• Direct HTML5 Web Portal: Chrome/Browser မှတစ်ဆင့် ကုန်ပစ္စည်းထည့်သွင်းစနစ် ပါဝင်ပြီး", style = MaterialTheme.typography.bodySmall)
                    Text("• ဆာဗာ အဆင့်: Local Embedded HTTP REST & WebSocket (Port 8787)", style = MaterialTheme.typography.bodySmall)
                    Text("• ဆိုင်ဝန်ထမ်း Client စနစ်: client.apk နှင့် HTML5 Web App အပြည့်အစုံ", style = MaterialTheme.typography.bodySmall)
                    Text("• AI အဆင့်: Gemini 3.5 Flash Online + Superuser Local NLP Engine", style = MaterialTheme.typography.bodySmall)
                    Text("• Bluetooth Printer ပုံစံ: 80x80mm ESC/POS Thermal (576 dots)", style = MaterialTheme.typography.bodySmall)
                    Text("• မြန်မာစာ ပရင့်စနစ်: Native High-Resolution Canvas Rendering", style = MaterialTheme.typography.bodySmall)
                    Text("• သိမ်းဆည်းမှု စနစ်: SQLite Room Offline Database", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // Manager PIN Dialog
    if (showPinDialog) {
        Dialog(onDismissRequest = { showPinDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("manager_pin_dialog"),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("မန်နေဂျာ PIN ရိုက်ထည့်ပါ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("နမူနာ သတ်မှတ်ထားသော PIN မှာ 1234 ဖြစ်ပါသည်", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        label = { Text("၄ လုံး PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("manager_pin_input"),
                    )

                    pinError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPinDialog = false }) { Text("မလုပ်တော့ပါ") }
                        Button(
                            onClick = {
                                if (viewModel.authenticateManager(pinInput)) {
                                    showPinDialog = false
                                } else {
                                    pinError = "PIN မှားယွင်းနေပါသည်။ 1234 ကို သုံးပါ"
                                }
                            },
                            modifier = Modifier.testTag("submit_manager_pin_button"),
                        ) {
                            Text("အတည်ပြုမည်")
                        }
                    }
                }
            }
        }
    }

    // Change Owner PIN Dialog
    if (showChangeOwnerPinDialog) {
        Dialog(onDismissRequest = { showChangeOwnerPinDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("change_owner_pin_dialog"),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔑 ဆိုင်ရှင် PIN နံပါတ် ပြောင်းလဲရန်", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("လက်ရှိ ဆိုင်ရှင် PIN နှင့် အသစ်ထားရှိမည့် PIN ကို ရိုက်ထည့်ပါ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = currentOwnerPinInput,
                        onValueChange = { currentOwnerPinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("လက်ရှိ ဆိုင်ရှင် PIN (မူလ: 7870)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("current_owner_pin_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newOwnerPinInput,
                        onValueChange = { newOwnerPinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("PIN အသစ် (အနည်းဆုံး ၄ လုံး)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_owner_pin_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmOwnerPinInput,
                        onValueChange = { confirmOwnerPinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("PIN အသစ်ကို ထပ်မံအတည်ပြုပါ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("confirm_owner_pin_input"),
                    )

                    ownerPinError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showChangeOwnerPinDialog = false }) { Text("မလုပ်တော့ပါ") }
                        Button(
                            onClick = {
                                if (newOwnerPinInput.length < 3) {
                                    ownerPinError = "PIN အသစ်သည် အနည်းဆုံး ၃ လုံး ရှိရပါမည်"
                                    return@Button
                                }
                                if (newOwnerPinInput != confirmOwnerPinInput) {
                                    ownerPinError = "PIN အသစ်နှစ်ခု ကိုက်ညီမှုမရှိပါ"
                                    return@Button
                                }
                                if (viewModel.changeOwnerPin(currentOwnerPinInput, newOwnerPinInput)) {
                                    showChangeOwnerPinDialog = false
                                } else {
                                    ownerPinError = "လက်ရှိ ဆိုင်ရှင် PIN မှားယွင်းနေပါသည်"
                                }
                            },
                            modifier = Modifier.testTag("submit_change_owner_pin_button"),
                        ) {
                            Text("ပြောင်းလဲသိမ်းဆည်းမည်")
                        }
                    }
                }
            }
        }
    }

    // Change Manager PIN Dialog
    if (showChangeManagerPinDialog) {
        Dialog(onDismissRequest = { showChangeManagerPinDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("change_manager_pin_dialog"),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("👔 မန်နေဂျာ PIN နံပါတ် ပြောင်းလဲရန်", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("လက်ရှိ မန်နေဂျာ/ဆိုင်ရှင် PIN နှင့် PIN အသစ်ကို ရိုက်ထည့်ပါ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = currentManagerPinInput,
                        onValueChange = { currentManagerPinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("လက်ရှိ မန်နေဂျာ သို့မဟုတ် ဆိုင်ရှင် PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("current_manager_pin_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newManagerPinInput,
                        onValueChange = { newManagerPinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("PIN အသစ် (အနည်းဆုံး ၄ လုံး)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_manager_pin_input"),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmManagerPinInput,
                        onValueChange = { confirmManagerPinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("PIN အသစ်ကို ထပ်မံအတည်ပြုပါ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("confirm_manager_pin_input"),
                    )

                    managerPinError?.let {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showChangeManagerPinDialog = false }) { Text("မလုပ်တော့ပါ") }
                        Button(
                            onClick = {
                                if (newManagerPinInput.length < 3) {
                                    managerPinError = "PIN အသစ်သည် အနည်းဆုံး ၃ လုံး ရှိရပါမည်"
                                    return@Button
                                }
                                if (newManagerPinInput != confirmManagerPinInput) {
                                    managerPinError = "PIN အသစ်နှစ်ခု ကိုက်ညီမှုမရှိပါ"
                                    return@Button
                                }
                                if (viewModel.changeManagerPin(currentManagerPinInput, newManagerPinInput)) {
                                    showChangeManagerPinDialog = false
                                } else {
                                    managerPinError = "လက်ရှိ PIN မှားယွင်းနေပါသည်"
                                }
                            },
                            modifier = Modifier.testTag("submit_change_manager_pin_button"),
                        ) {
                            Text("ပြောင်းလဲသိမ်းဆည်းမည်")
                        }
                    }
                }
            }
        }
    }

    if (showServerDialog) {
        ServerStatusDialog(
            viewModel = viewModel,
            onDismiss = { showServerDialog = false },
            onOpenStaffClient = {
                showServerDialog = false
                viewModel.setStaffClientMode(true)
            }
        )
    }
}
