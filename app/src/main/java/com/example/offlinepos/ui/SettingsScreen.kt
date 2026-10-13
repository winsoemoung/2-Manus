package com.example.offlinepos.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.offlinepos.R
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
                                .size(72.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        )
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
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
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
                                text = "• အခွန် (${currentRateVal}%): %,d Ks\n• စုစုပေါင်း ကျသင့်ငွေ: %,d Ks".format(sampleTax, sampleTotal),
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
                                        putExtra(Intent.EXTRA_SUBJECT, "ဆန်းသစ် POS ဒေတာ Backup")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Google Drive သို့ Upload ရန် သို့မဟုတ် ဖိုင်သိမ်းရန်"))
                                    backupStatusMessage = "Backup ဖိုင် (${exported.name}) ကို အောင်မြင်စွာ ထုတ်ယူပြီးပါပြီ"
                                } else {
                                    backupStatusMessage = "Backup ထုတ်ယူခြင်း မအောင်မြင်ပါ"
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_backup_button"),
                        ) {
                            Text("☁️ Google Drive သို့ တင်ရန် / Export", fontSize = 12.sp)
                        }

                        // Import & Restore
                        OutlinedButton(
                            onClick = {
                                importFileLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(0.7f).testTag("import_backup_button"),
                        ) {
                            Text("📥 Restore ပြန်တင်မည်", fontSize = 12.sp)
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• ဆိုင်ရှင် PIN: 7870 (Full Access) | • မန်နေဂျာ PIN: 1234", fontSize = 11.sp, color = Color.Gray)
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
                                Text(
                                    text = if (p.discountPercent > 0) "${p.discountPercent}% Off" else "%,d Ks Off".format(p.discountFlatMinor),
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
                    Text("• ဗားရှင်း: 1.0.0 (787_AI.apk)", style = MaterialTheme.typography.bodySmall)
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
}
