package com.example.offlinepos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.offlinepos.ai.AiAction
import com.example.offlinepos.ai.AiActionType
import com.example.offlinepos.ai.AiChatMessage
import com.example.offlinepos.ai.MessageSender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCopilotScreen(
    viewModel: PosViewModel,
) {
    val messages by viewModel.aiMessages.collectAsState()
    val isThinking by viewModel.isAiThinking.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Scroll to latest message
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "787 AI ကွပ်ကဲရေးစင်တာ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val hasKey = viewModel.getGeminiApiKey().isNotBlank()
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (hasKey) Color(0xFFDCFCE7) else Color(0xFFE0F2FE),
                                ) {
                                    Text(
                                        text = if (hasKey) "✨ Gemini 3.5 Flash" else "⚡ Local AI Rule Engine",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasKey) Color(0xFF15803D) else Color(0xFF0369A1),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFFEF3C7),
                                ) {
                                    Text(
                                        text = "🛡️ AI Privilege: FULL ACCESS",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.clearAiChat() },
                        modifier = Modifier.testTag("clear_ai_chat_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Chat")
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("ai_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "AI Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Quick preset suggestions for code, promo, price, and actions
            val promptSuggestions = listOf(
                "📉 Dead Stock စစ်ဆေးပါ",
                "⚠️ ပစ္စည်းမှာယူရန် Reorder စာရင်း",
                "📊 အရောင်း & အမြတ် သုံးသပ်ချက်",
                "➕ Code အသစ် 787-CEM-01 ထည့်ပါ",
                "🔄 Code အစားထိုး ပြုပြင်ပါ",
                "🎟️ Promo Code '787VIP' ၁၀% ထည့်ပါ",
                "💰 အခွန်နှုန်းကို ၀% သို့ ပြင်ပါ",
                "🏷️ သံချောင်း ဈေးနှုန်း ပြင်ပါ",
                "⚡ AI Superuser အခွင့်အရေး စစ်ဆေးပါ",
                "🛠️ သွပ်ပြား လက်ကျန် +၂၀ ထပ်ဖြည့်ပါ",
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(promptSuggestions) { prompt ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            viewModel.sendAiCommand(prompt)
                        },
                        label = { Text(prompt, fontSize = 11.5.sp) },
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        onExecuteAction = { action ->
                            viewModel.executeAiAction(action)
                        }
                    )
                }

                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI စဉ်းစားတွက်ချက်နေပါသည်...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text("AI ထံ အမိန့်ရိုက်ထည့်ပါ (ဥပမာ - Code အသစ် 787-CEM ထည့်ပါ)...", fontSize = 12.sp)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendAiCommand(inputText)
                                inputText = ""
                            }
                        }),
                        trailingIcon = {
                            if (inputText.isNotEmpty()) {
                                IconButton(onClick = { inputText = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendAiCommand(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isThinking,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (inputText.isNotBlank() && !isThinking) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("ai_send_button")
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank() && !isThinking) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AiSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun ChatMessageItem(
    message: AiChatMessage,
    onExecuteAction: (AiAction) -> Unit,
) {
    val isUser = message.sender == MessageSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(top = 2.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp,
                ),
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // Executable Action Card
        message.action?.let { action ->
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(start = if (isUser) 0.dp else 36.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (action.isExecuted) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚙️ AI Superuser Command",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        if (action.isExecuted) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFDCFCE7),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Executed",
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "လုပ်ဆောင်ပြီးပါပြီ",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = action.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Detail Specs for All Action Types
                    when (val type = action.type) {
                        is AiActionType.AddProduct -> {
                            Text(
                                text = "• ပစ္စည်း: ${type.name} (${type.category})\n• Code (SKU): ${type.sku}\n• Barcode: ${type.barcode ?: "-"}\n• ရောင်းဈေး: %,d Ks | သွင်းဈေး: %,d Ks\n• လက်ကျန်: ${type.stockQuantity} ခု".format(
                                    type.sellPriceMinor,
                                    type.costPriceMinor
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.ReplaceProductCode -> {
                            Text(
                                text = "• ပစ္စည်း: ${type.productName}\n• Code အဟောင်း: ${type.oldCode} ➔ Code အသစ်: ${type.newCode}\n• အမျိုးအစား: ${if (type.isBarcode) "Barcode" else "SKU Code"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.UpdateSku -> {
                            Text(
                                text = "• ပစ္စည်း: ${type.productName}\n• Code အဟောင်း: ${type.oldSku} ➔ Code အသစ်: ${type.newSku}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.UpdateBarcode -> {
                            Text(
                                text = "• ပစ္စည်း: ${type.productName}\n• Barcode အဟောင်း: ${type.oldBarcode ?: "-"} ➔ Barcode အသစ်: ${type.newBarcode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.BatchReplaceCodePrefix -> {
                            Text(
                                text = "• အသုတ်လိုက် Code ပြုပြင်ခြင်း: ပစ္စည်းအားလုံးရှေ့တွင် '${type.newPrefix}' Prefix တပ်ဆင်မည်",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.AddPromoCode -> {
                            Text(
                                text = "• Promo / Discount Code: ${type.code}\n• လျှော့နှုန်း: ${if (type.discountPercent > 0) "${type.discountPercent}%" else "%,d Ks".format(type.discountFlatMinor)}\n• ဖော်ပြချက်: ${type.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.ReplacePromoCode -> {
                            Text(
                                text = "• Code အဟောင်း: ${type.oldCode} ➔ Code အသစ်: ${type.newCode}\n• လျှော့နှုန်းသစ်: ${type.discountPercent}%\n• ဖော်ပြချက်: ${type.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.ExecuteDynamicCode -> {
                            Text(
                                text = "• Language: ${type.codeLanguage}\n• Script: ${type.codeScript}\n• ဖော်ပြချက်: ${type.explanation}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.ApplyCartDiscountByCode -> {
                            Text(
                                text = "• Promo Code '${type.promoCode}' ဖြင့် ${type.discountPercent}% လျှော့ပေးခြင်း",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.UpdatePrice -> {
                            Text(
                                text = "• ပစ္စည်း: ${type.productName}\n• ပြင်ဆင်မည့် ရောင်းဈေး: %,d Ks".format(type.newSellPriceMinor),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.AdjustStock -> {
                            Text(
                                text = "• ပစ္စည်း: ${type.productName}\n• လက်ကျန် ထပ်ဖြည့်အရေအတွက်: +${type.adjustment}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.SetTaxRate -> {
                            Text(
                                text = "• သတ်မှတ်မည့် အခွန်နှုန်းထား: ${type.newTaxPercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.UpdateStoreProfile -> {
                            Text(
                                text = "• ဆိုင်အမည်: ${type.storeName ?: '-'}\n• ဖုန်း: ${type.phone ?: '-'}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AiActionType.BatchReplaceCategory -> {
                            Text(
                                text = "• ကုန်ပစ္စည်းအမျိုးအစား အစားထိုးခြင်း: '${type.oldCategory}' ➔ '${type.newCategory}'",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!action.isExecuted) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onExecuteAction(action) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("execute_ai_action_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("အတည်ပြု လုပ်ဆောင်မည် (Execute Code / Action)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiSettingsDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
) {
    var apiKeyInput by remember { mutableStateOf(viewModel.getGeminiApiKey()) }
    var autoExecute by remember { mutableStateOf(viewModel.isAiAutoExecute()) }
    var superuserPrivileges by remember { mutableStateOf(viewModel.isAiSuperuserGranted()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .testTag("ai_settings_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI စနစ် သတ်မှတ်ချက်များ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Clear, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Google Gemini API Key (Optional):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    placeholder = { Text("AIzaSy... (Gemini 3.5 Flash)", fontSize = 12.sp) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
                    shape = RoundedCornerShape(10.dp),
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "💡 API Key ထည့်ထားပါက Cloud Gemini 3.5 Flash Model ဖြင့် အလွန်စမတ်ကျစွာ အလုပ်လုပ်မည်ဖြစ်ပြီး၊ API Key မထည့်ထားပါကလည်း Offline Rule Engine ဖြင့် သုံးနိုင်ပါသည်။",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Superuser Privileges",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "AI အား Code အသစ်ထည့်ခြင်း၊ အစားထိုးခြင်း နှင့် ပြုပြင်ခြင်း အခွင့်အရေး အပြည့်အဝ ပေးရန်",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = superuserPrivileges,
                        onCheckedChange = { superuserPrivileges = it },
                        modifier = Modifier.testTag("ai_superuser_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Execute Actions",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "ခလုတ်ထပ်မံနှိပ်စရာမလိုဘဲ AI အမိန့်ကို ချက်ချင်း အလိုအလျောက် အတည်ပြုလုပ်ဆောင်မည်",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = autoExecute,
                        onCheckedChange = { autoExecute = it },
                        modifier = Modifier.testTag("ai_auto_execute_switch")
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        viewModel.setGeminiApiKey(apiKeyInput)
                        viewModel.setAiAutoExecute(autoExecute)
                        viewModel.setAiSuperuserGranted(superuserPrivileges)
                        viewModel.setBanner("AI သတ်မှတ်ချက်များကို သိမ်းဆည်းပြီးပါပြီ")
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("save_ai_settings_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("သိမ်းဆည်းမည် (Save Settings)")
                }
            }
        }
    }
}
