package com.example.ui.screens

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AgroAiCopilotDataSource
import com.example.data.AssistantResponseMode
import com.example.data.DatabaseService
import com.example.data.GeminiAgronomyChatMessage
import com.example.data.GroundingCitation
import com.example.model.AppScreen
import com.example.model.formatCoord4
import com.example.model.formatDecimal1
import com.example.model.formatDecimal2
import com.example.model.formatHoursMinutesCompact
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.ForestBgDark
import com.example.ui.theme.ForestBgDeep
import com.example.ui.theme.ForestCardBorder
import com.example.ui.theme.ForestCardDark
import com.example.ui.theme.ForestCardElevated
import com.example.ui.theme.LimeAccent
import com.example.ui.theme.LimeBright
import com.example.ui.theme.LimeSoftBg
import com.example.ui.theme.MintTextMuted
import com.example.ui.theme.MintTextPrimary
import com.example.ui.theme.MintTextSoft
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.RoseSoft
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft
import com.example.viewmodel.AgroSightUiState
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Mandatory Phase B farmer-first suggested questions.
 */
val PHASE_B_MANDATORY_SUGGESTED_QUESTIONS = listOf(
    "هل المحصول عندي بحالة جيدة؟",
    "هل الأرض محتاجة ري؟",
    "اشرحلي تحليل الأقمار الصناعية.",
    "ما أهم التنبيهات في مزرعتي؟"
)

/**
 * Detects whether a message is primarily LTR English or RTL Arabic based on its first strong letter.
 */
internal fun detectMessageLayoutDirection(text: String): LayoutDirection {
    for (ch in text) {
        if (!Character.isLetter(ch)) continue
        val dir = Character.getDirectionality(ch)
        if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
        ) {
            return LayoutDirection.Rtl
        }
        if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
            return LayoutDirection.Ltr
        }
    }
    return LayoutDirection.Rtl
}

/**
 * Standalone overload of [ChatbotScreen] that directly accepts an [AgroAiCopilotDataSource]
 * (e.g., [com.example.data.GeminiAgroService]) and grounds every agricultural query in the
 * user's live farm telemetry from [uiState].
 */
@Composable
fun ChatbotScreen(
    geminiClient: AgroAiCopilotDataSource,
    uiState: AgroSightUiState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var localMessages by remember { mutableStateOf(uiState.geminiChatMessages) }
    var isLocalLoading by remember { mutableStateOf(false) }
    var localMode by rememberSaveable { mutableStateOf(uiState.assistantResponseMode) }
    var localErrorAr by remember { mutableStateOf(uiState.geminiChatErrorAr) }
    var lastFailedPrompt by remember { mutableStateOf(uiState.lastFailedGeminiPrompt) }
    var useSearch by rememberSaveable { mutableStateOf(uiState.useGoogleSearchGrounding) }
    var useMaps by rememberSaveable { mutableStateOf(uiState.useGoogleMapsGrounding) }

    fun sendLocalPrompt(rawQuestion: String, isRetry: Boolean = false) {
        val clean = rawQuestion.trim()
        if (clean.isNotEmpty() && !isLocalLoading) {
            val channel = when {
                useSearch -> "SEARCH_GROUNDED"
                useMaps -> "MAPS_GROUNDED"
                else -> "GEMINI_CHAT"
            }
            if (!isRetry) {
                val userMsg = GeminiAgronomyChatMessage(
                    id = "chat_u_${System.currentTimeMillis()}",
                    role = "USER",
                    channel = channel,
                    text = clean,
                    timestamp = DatabaseService.currentTimeFormatted(),
                    responseMode = localMode
                )
                localMessages = localMessages + userMsg
            }
            isLocalLoading = true
            localErrorAr = null
            scope.launch {
                try {
                    val contextSnapshot = com.example.data.AgriculturalContextProvider.buildContext(
                        uiState = uiState,
                        chatHistory = localMessages,
                        currentQuery = clean
                    )

                    val response = geminiClient.generateMultiTurnGroundedAgronomyReply(
                        chatHistory = localMessages,
                        userMessage = clean,
                        useGoogleSearchGrounding = useSearch,
                        useGoogleMapsGrounding = useMaps,
                        farmerName = contextSnapshot.farm.farmerDisplayName,
                        fieldName = contextSnapshot.farm.fieldName,
                        regionName = contextSnapshot.farm.regionName,
                        latitude = contextSnapshot.farm.latitude,
                        longitude = contextSnapshot.farm.longitude,
                        cropNameAr = contextSnapshot.farm.cropNameAr,
                        areaFeddans = contextSnapshot.farm.areaFeddans,
                        soilPh = contextSnapshot.farm.soilPh,
                        soilEc = contextSnapshot.farm.soilEceDsM,
                        waterEc = contextSnapshot.farm.waterEcwDsM,
                        et0MmDay = contextSnapshot.irrigation.et0MmDay,
                        kcValue = contextSnapshot.irrigation.fusedKc,
                        dailyM3PerFeddan = contextSnapshot.irrigation.grossWaterM3PerFeddanDay,
                        valveRunHours = contextSnapshot.irrigation.valveOperatingHours,
                        report = uiState.epidemiologyReport,
                        responseMode = localMode,
                        agriculturalContext = contextSnapshot
                    )
                    val assistantMsg = GeminiAgronomyChatMessage(
                        id = "chat_a_${System.currentTimeMillis()}",
                        role = "ASSISTANT",
                        channel = channel,
                        text = response.replyText,
                        timestamp = DatabaseService.currentTimeFormatted(),
                        citations = response.citations,
                        isLiveAiGenerated = response.isLiveAiGenerated,
                        responseMode = localMode,
                        recommendedScreen = response.recommendedScreen,
                        recommendedScreenLabelAr = response.recommendedScreenLabelAr,
                        dataTimestampIso = response.dataTimestampIso,
                        dataSourceModeLabelAr = response.dataSourceModeLabelAr
                    )
                    localMessages = localMessages + assistantMsg
                    lastFailedPrompt = null
                } catch (e: Exception) {
                    localErrorAr = "تعذر الاتصال بخدمة المستشار الذكي (${e.message ?: "خطأ غير متوقع"}). يمكنك إعادة المحاولة."
                    lastFailedPrompt = clean
                } finally {
                    isLocalLoading = false
                }
            }
        }
    }

    ChatbotScreen(
        uiState = uiState.copy(
            geminiChatMessages = localMessages,
            isGeminiChatLoading = isLocalLoading,
            assistantResponseMode = localMode,
            geminiChatErrorAr = localErrorAr,
            lastFailedGeminiPrompt = lastFailedPrompt,
            useGoogleSearchGrounding = useSearch,
            useGoogleMapsGrounding = useMaps
        ),
        onSendMessage = { sendLocalPrompt(it, isRetry = false) },
        onSelectResponseMode = { localMode = it },
        onRetryLastMessage = {
            val prompt = lastFailedPrompt ?: localMessages.lastOrNull { it.role == "USER" }?.text
            if (!prompt.isNullOrBlank()) {
                sendLocalPrompt(prompt, isRetry = true)
            }
        },
        onDismissError = { localErrorAr = null },
        onToggleSearchGrounding = { useSearch = !useSearch },
        onToggleMapsGrounding = { useMaps = !useMaps },
        onClearChatHistory = { localMessages = emptyList() },
        modifier = modifier
    )
}

/**
 * Primary [ChatbotScreen] Composable for conversational agricultural queries powered by the
 * Gemini API client ([com.example.data.GeminiAgroService] / Firebase AI Logic `gemini-3.8-flash`),
 * grounded in the farmer's live field telemetry (Sentinel-2 NDVI/NDRE/PSRI/NDMI, FAO-56 ET₀,
 * soil pH/ECe, irrigation valve schedule, and Google Maps coordinates).
 */
@Composable
fun ChatbotScreen(
    uiState: AgroSightUiState,
    onSendMessage: (String) -> Unit,
    onSelectResponseMode: (AssistantResponseMode) -> Unit = {},
    onRetryLastMessage: () -> Unit = {},
    onDismissError: () -> Unit = {},
    onCloseChatbot: (() -> Unit)? = null,
    onToggleSearchGrounding: () -> Unit = {},
    onToggleMapsGrounding: () -> Unit = {},
    onSelectField: (String) -> Unit = {},
    onSelectValveSector: (Int) -> Unit = {},
    onClearChatHistory: () -> Unit = {},
    onNavigateToScreen: (AppScreen) -> Unit = {},
    onOpenWhatsAppWithMessage: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var queryInput by rememberSaveable { mutableStateOf("") }
    var showFarmContextDetails by rememberSaveable { mutableStateOf(true) }
    var currentlySpeakingMessageId by remember { mutableStateOf<String?>(null) }
    var screenLayoutDirection by remember { mutableStateOf(LayoutDirection.Rtl) }

    // Optional Arabic TextToSpeech for reading agronomic recommendations aloud
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engineRef: TextToSpeech? = null
        try {
            engineRef = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching {
                        engineRef?.language = Locale.forLanguageTag("ar-EG")
                    }
                }
            }
            ttsEngine = engineRef
        } catch (_: Throwable) {
            // Safe no-op in headless/Robolectric environments
        }
        onDispose {
            runCatching {
                engineRef?.stop()
                engineRef?.shutdown()
            }
        }
    }

    val todayMeteo = uiState.forecastDays.firstOrNull()
    val todayEt0 = todayMeteo?.et0MmDay ?: 5.6f
    val currentKc = todayMeteo?.adjustedKc ?: uiState.growthEvaluation.currentBaseKc
    val avgDailyM3 = remember(uiState.valveSectors) {
        if (uiState.valveSectors.isNotEmpty()) {
            uiState.valveSectors.map { it.grossWaterM3PerFeddan }.average().toFloat()
        } else 25.0f
    }
    val avgValveHours = remember(uiState.valveSectors) {
        if (uiState.valveSectors.isNotEmpty()) {
            uiState.valveSectors.map { it.operatingHours }.average().toFloat()
        } else 2.2f
    }
    val report = uiState.epidemiologyReport

    val listState = rememberLazyListState()
    LaunchedEffect(uiState.geminiChatMessages.size, uiState.isGeminiChatLoading, uiState.geminiChatErrorAr) {
        val errorCount = if (uiState.geminiChatErrorAr != null) 1 else 0
        val loadingCount = if (uiState.isGeminiChatLoading) 1 else 0
        val totalItems = 1 + errorCount + uiState.geminiChatMessages.size + loadingCount
        if (totalItems > 1) {
            runCatching {
                listState.scrollToItem(totalItems - 1)
            }
        }
    }

    // Dynamic suggested prompts combining mandatory Phase B farmer questions + live farm telemetry
    val groundedPrompts = remember(
        uiState.fieldName,
        uiState.selectedCrop.nameAr,
        uiState.soilPh,
        uiState.soilEce,
        todayEt0,
        report.ndviMean,
        report.affectedValveNumber,
        report.detectedRiskNameAr
    ) {
        PHASE_B_MANDATORY_SUGGESTED_QUESTIONS + listOf(
            "💧 كم المقنن المائي وساعات تشغيل المحابس اليوم لمحصول ${uiState.selectedCrop.nameAr} عند ET0=${formatDecimal2(todayEt0)} مم؟",
            "🛰️ فسر لي قراءة Sentinel-2 (NDVI=${formatDecimal2(report.ndviMean)}, NDRE=${formatDecimal2(report.ndreMean)}) في محبس #${report.affectedValveNumber}",
            "🧪 كيف أعادل قلوية التربة (pH=${formatDecimal1(uiState.soilPh)}) وملوحة (ECe=${formatDecimal1(uiState.soilEce)} dS/m) بحامض الفوسفوريك؟",
            "🛡️ ما خطة الوقاية والتدخل الموجه لـ (${report.detectedRiskNameAr}) في ${uiState.fieldName}؟",
            "🗺️ حلل موقع الحقل (${uiState.activeFarmer.region}: ${formatCoord4(uiState.latitude)}°N, ${formatCoord4(uiState.longitude)}°E) على Google Maps"
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides screenLayoutDirection) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ForestBgDark, ForestBgDeep)
                )
            )
            .testTag("chatbot_screen")
    ) {
        // 1. Conversational Feed + Grounded Farm Context Header
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chatbot_messages_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Item: Live Farm Data Grounding Header Card + Mode Switcher
            item(key = "farm_grounding_context_card") {
                FarmGroundingContextHeaderCard(
                    uiState = uiState,
                    todayEt0 = todayEt0,
                    currentKc = currentKc,
                    avgDailyM3 = avgDailyM3,
                    avgValveHours = avgValveHours,
                    isExpanded = showFarmContextDetails,
                    screenLayoutDirection = screenLayoutDirection,
                    onToggleLayoutDirection = {
                        screenLayoutDirection = if (screenLayoutDirection == LayoutDirection.Rtl) {
                            LayoutDirection.Ltr
                        } else {
                            LayoutDirection.Rtl
                        }
                    },
                    onSelectResponseMode = onSelectResponseMode,
                    onCloseChatbot = onCloseChatbot,
                    onToggleExpand = { showFarmContextDetails = !showFarmContextDetails },
                    onToggleSearchGrounding = onToggleSearchGrounding,
                    onToggleMapsGrounding = onToggleMapsGrounding,
                    onSelectField = onSelectField,
                    onSelectValveSector = onSelectValveSector,
                    onOpenSatelliteMap = { onNavigateToScreen(AppScreen.SATELLITE_NDVI_MAP) },
                    onClearChatHistory = onClearChatHistory
                )
            }

            // Second Item: Context-Grounded Quick Agronomic Query Chips (Including Mandatory Phase B Questions)
            item(key = "grounded_quick_prompts_row") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 أسئلة مقترحة واستشارات فورية لحقلك:",
                            color = MintTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${uiState.geminiChatMessages.size} رسالة",
                            color = SkyAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groundedPrompts.forEachIndexed { idx, promptText ->
                            val isMandatoryQuestion = idx < PHASE_B_MANDATORY_SUGGESTED_QUESTIONS.size
                            Surface(
                                color = if (isMandatoryQuestion) EmeraldSoft else ForestCardDark,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isMandatoryQuestion) EmeraldPrimary else ForestCardBorder
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = !uiState.isGeminiChatLoading) {
                                        onSendMessage(promptText)
                                    }
                                    .testTag("chatbot_quick_prompt_$idx")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = EmeraldPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = promptText,
                                        color = MintTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 270.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Error and Retry Banner (Visible when a chat request fails)
            uiState.geminiChatErrorAr?.let { errorText ->
                item(key = "gemini_error_retry_banner") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = RoseSoft),
                        border = BorderStroke(1.2.dp, RoseDanger),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chatbot_error_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = RoseDanger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = errorText,
                                        color = MintTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = onDismissError,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("chatbot_dismiss_error_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إغلاق التنبيه",
                                        tint = RoseDanger,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = onRetryLastMessage,
                                    enabled = !uiState.isGeminiChatLoading,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RoseDanger,
                                        contentColor = ForestCardDark
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("chatbot_retry_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "إعادة المحاولة",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "إعادة المحاولة",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Conversation Message Bubbles
            items(
                items = uiState.geminiChatMessages,
                key = { it.id }
            ) { message ->
                ChatbotMessageBubble(
                    message = message,
                    uiState = uiState,
                    todayEt0 = todayEt0,
                    avgDailyM3 = avgDailyM3,
                    isSpeaking = currentlySpeakingMessageId == message.id,
                    onToggleSpeak = {
                        if (currentlySpeakingMessageId == message.id) {
                            runCatching { ttsEngine?.stop() }
                            currentlySpeakingMessageId = null
                        } else {
                            runCatching {
                                ttsEngine?.speak(
                                    message.text,
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    message.id
                                )
                            }
                            currentlySpeakingMessageId = message.id
                        }
                    },
                    onShareToWhatsApp = {
                        onOpenWhatsAppWithMessage(
                            uiState.activeFarmer.whatsappPhone,
                            message.text
                        )
                    },
                    onNavigateToRecommendedScreen = onNavigateToScreen
                )
            }

            // Live Thinking / Grounding Indicator
            if (uiState.isGeminiChatLoading) {
                item(key = "gemini_loading_indicator") {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = ForestCardElevated),
                        border = BorderStroke(1.dp, SkyAccent.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chatbot_loading_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = EmeraldPrimary
                            )
                            Column {
                                Text(
                                    text = "جاري تحليل بيانات (${uiState.fieldName}) عبر Gemini 3.8 Flash (${uiState.assistantResponseMode.titleAr})...",
                                    color = EmeraldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "دمج مؤشرات Sentinel-2 (NDVI=${formatDecimal2(report.ndviMean)}) + FAO-56 (ET₀=${formatDecimal2(todayEt0)}) + التربة (pH=${formatDecimal1(uiState.soilPh)})",
                                    color = MintTextSoft,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Bottom Sticky Conversational Composer Bar
        Surface(
            color = ForestCardDark,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, ForestCardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val inputDirection = remember(queryInput, screenLayoutDirection) {
                    if (queryInput.isBlank()) screenLayoutDirection else detectMessageLayoutDirection(queryInput)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides inputDirection) {
                        OutlinedTextField(
                            value = queryInput,
                            onValueChange = { queryInput = it },
                            placeholder = {
                                Text(
                                    text = "اسأل AgroSight بالعربية أو الإنجليزية عن ${uiState.selectedCrop.nameAr} في ${uiState.fieldName}...",
                                    color = MintTextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            maxLines = 3,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ForestBgDark,
                                unfocusedContainerColor = ForestBgDark,
                                focusedBorderColor = EmeraldPrimary,
                                unfocusedBorderColor = ForestCardBorder,
                                focusedTextColor = MintTextPrimary,
                                unfocusedTextColor = MintTextPrimary,
                                cursorColor = EmeraldPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chatbot_input_field")
                        )
                    }

                    Button(
                        onClick = {
                            val trimmed = queryInput.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendMessage(trimmed)
                                queryInput = ""
                            }
                        },
                        enabled = queryInput.isNotBlank() && !uiState.isGeminiChatLoading,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = ForestCardDark,
                            disabledContainerColor = ForestCardElevated,
                            disabledContentColor = MintTextMuted
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        modifier = Modifier.testTag("chatbot_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال السؤال إلى Gemini",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إرسال",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun FarmGroundingContextHeaderCard(
    uiState: AgroSightUiState,
    todayEt0: Float,
    currentKc: Float,
    avgDailyM3: Float,
    avgValveHours: Float,
    isExpanded: Boolean,
    screenLayoutDirection: LayoutDirection,
    onToggleLayoutDirection: () -> Unit,
    onSelectResponseMode: (AssistantResponseMode) -> Unit,
    onCloseChatbot: (() -> Unit)?,
    onToggleExpand: () -> Unit,
    onToggleSearchGrounding: () -> Unit,
    onToggleMapsGrounding: () -> Unit,
    onSelectField: (String) -> Unit,
    onSelectValveSector: (Int) -> Unit,
    onOpenSatelliteMap: () -> Unit,
    onClearChatHistory: () -> Unit
) {
    val context = LocalContext.current
    val report = uiState.epidemiologyReport

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ForestCardDark),
        border = BorderStroke(1.2.dp, ForestCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chatbot_farm_grounding_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ForestCardElevated,
                            ForestBgDark,
                            SkySoft
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: AgroSight Logo + Gemini Title + RTL/LTR + Expand/Clear/Return Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (onCloseChatbot != null) {
                        IconButton(
                            onClick = onCloseChatbot,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(ForestCardDark)
                                .border(1.dp, ForestCardBorder, CircleShape)
                                .testTag("chatbot_close_return_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "العودة للشاشة السابقة",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Image(
                        painter = painterResource(id = R.drawable.img_agrosight_logo),
                        contentDescription = "AgroSight Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(listOf(EmeraldPrimary, SkyAccent)),
                                shape = RoundedCornerShape(12.dp)
                            )
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "المستشار الزراعي الذكي (اسأل AgroSight)",
                                color = EmeraldPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "مربوط ببيانات حقل: ${uiState.fieldName} • ${uiState.selectedCrop.nameAr} (${uiState.areaFeddans.roundToInt()} فدان)",
                            color = MintTextSoft,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = ForestCardDark,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, ForestCardBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onToggleLayoutDirection)
                            .testTag("chatbot_layout_direction_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "تبديل اتجاه اللغة RTL/LTR",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (screenLayoutDirection == LayoutDirection.Rtl) "AR/RTL" else "EN/LTR",
                                color = MintTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onClearChatHistory,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ForestCardDark)
                            .border(1.dp, ForestCardBorder, CircleShape)
                            .testTag("chatbot_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "بدء استشارة جديدة",
                            tint = MintTextSoft,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ForestCardDark)
                            .border(1.dp, ForestCardBorder, CircleShape)
                            .testTag("chatbot_toggle_context_button")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "عرض أو إخفاء بيانات الحقل",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Phase B: Two Assistant Modes Switcher (Farmer Mode vs Technical Mode)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chatbot_mode_switcher_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isFarmerMode = uiState.assistantResponseMode == AssistantResponseMode.FARMER
                Surface(
                    color = if (isFarmerMode) EmeraldPrimary else ForestCardDark,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isFarmerMode) EmeraldPrimary else ForestCardBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectResponseMode(AssistantResponseMode.FARMER) }
                        .testTag("chatbot_mode_farmer_button")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "🌾 ${AssistantResponseMode.FARMER.titleAr}",
                            color = if (isFarmerMode) ForestCardDark else MintTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = AssistantResponseMode.FARMER.descriptionAr,
                            color = if (isFarmerMode) EmeraldSoft else MintTextSoft,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val isTechnicalMode = uiState.assistantResponseMode == AssistantResponseMode.TECHNICAL
                Surface(
                    color = if (isTechnicalMode) SkyAccent else ForestCardDark,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isTechnicalMode) SkyAccent else ForestCardBorder
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectResponseMode(AssistantResponseMode.TECHNICAL) }
                        .testTag("chatbot_mode_technical_button")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "🔬 ${AssistantResponseMode.TECHNICAL.titleAr}",
                            color = if (isTechnicalMode) ForestCardDark else MintTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = AssistantResponseMode.TECHNICAL.descriptionAr,
                            color = if (isTechnicalMode) SkySoft else MintTextSoft,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Active Mode Live Summary Banner (Farmer Simple Summary vs Technical Exact Indices + FAO-56 + Limitations)
            Surface(
                color = if (uiState.assistantResponseMode == AssistantResponseMode.FARMER) EmeraldSoft else SkySoft,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (uiState.assistantResponseMode == AssistantResponseMode.FARMER) EmeraldPrimary else SkyAccent
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.assistantResponseMode == AssistantResponseMode.FARMER) {
                    Text(
                        text = "وضع المزارع المبسط نشط: إجابات عربية واضحة ومختصرة بخطوات عملية لري ورعاية محصول ${uiState.selectedCrop.nameAr} بدون مصطلحات معقدة.",
                        color = MintTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("chatbot_active_mode_banner")
                    )
                } else {
                    Text(
                        text = "الوضع الفني المتقدم نشط: NDVI=${formatDecimal2(report.ndviMean)}, NDRE=${formatDecimal2(report.ndreMean)}, NDMI=${formatDecimal2(report.ndmiMean)}, PSRI=${formatDecimal2(report.psriMean)} | منهجية FAO-56 (ETc = ET₀ ${formatDecimal2(todayEt0)} × Kc ${formatDecimal2(currentKc)}) | دقة Sentinel-2: 10م-20م (مع مراعاة حدود السحب وعدم اليقين الإحصائي).",
                        color = MintTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("chatbot_active_mode_banner")
                    )
                }
            }

            // Grounding Toggles Row (Google Search Grounding + Google Maps Grounding + Open Map)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GroundingToggleChip(
                    icon = Icons.Default.Search,
                    label = if (uiState.useGoogleSearchGrounding) "بحث Google العلمي: مفعّل" else "تفعيل بحث Google",
                    isActive = uiState.useGoogleSearchGrounding,
                    activeColor = EmeraldPrimary,
                    activeBg = EmeraldSoft,
                    onClick = onToggleSearchGrounding,
                    testTag = "chatbot_toggle_search_grounding"
                )

                GroundingToggleChip(
                    icon = Icons.Default.Map,
                    label = if (uiState.useGoogleMapsGrounding) "إحداثيات Google Maps: مفعّلة" else "تفعيل Google Maps",
                    isActive = uiState.useGoogleMapsGrounding,
                    activeColor = SkyAccent,
                    activeBg = SkySoft,
                    onClick = onToggleMapsGrounding,
                    testTag = "chatbot_toggle_maps_grounding"
                )

                Surface(
                    color = ForestCardDark,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, SkyAccent),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            try {
                                val uri = "https://www.google.com/maps/search/?api=1&query=${uiState.latitude},${uiState.longitude}".toUri()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (_: Exception) {
                                onOpenSatelliteMap()
                            }
                        }
                        .testTag("chatbot_open_gmaps_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = SkyAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${formatCoord4(uiState.latitude)}°N, ${formatCoord4(uiState.longitude)}°E",
                            color = SkyAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Expandable Farm Telemetry Grounding Matrix (Fields, Soil, Sentinel-2, Irrigation)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = ForestCardBorder.copy(alpha = 0.65f))

                    // Multi-Field Selector Strip (Switch active farm field context in real time)
                    if (uiState.dbSnapshot.fields.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.dbSnapshot.fields.forEach { field ->
                                val isSelected = field.id == uiState.fieldId
                                Surface(
                                    color = if (isSelected) EmeraldPrimary else ForestCardDark,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) EmeraldPrimary else ForestCardBorder
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectField(field.id) }
                                        .testTag("chatbot_field_selector_${field.id}")
                                ) {
                                    Text(
                                        text = "${field.fieldName} • ${field.cropType}",
                                        color = if (isSelected) ForestCardDark else MintTextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 4 Grounding Telemetry Pills (Sentinel-2 | FAO-56 Irrigation | Soil Chemistry | Spatial Risk)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GroundedMetricMiniCard(
                            icon = Icons.Default.SatelliteAlt,
                            titleAr = "Sentinel-2 الطيفي",
                            valueAr = "NDVI ${formatDecimal2(report.ndviMean)} • NDRE ${formatDecimal2(report.ndreMean)}",
                            subtitleAr = "PSRI ${formatDecimal2(report.psriMean)} | NDMI ${formatDecimal2(report.ndmiMean)}",
                            accentColor = EmeraldPrimary,
                            bgColor = ForestCardDark,
                            modifier = Modifier.weight(1f)
                        )
                        GroundedMetricMiniCard(
                            icon = Icons.Default.WaterDrop,
                            titleAr = "مقنن الري FAO-56",
                            valueAr = "${formatDecimal1(avgDailyM3)} م³/فدان",
                            subtitleAr = "ET₀ ${formatDecimal2(todayEt0)} مم • Kc ${formatDecimal2(currentKc)} (${formatHoursMinutesCompact(avgValveHours)})",
                            accentColor = SkyAccent,
                            bgColor = ForestCardDark,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GroundedMetricMiniCard(
                            icon = Icons.Default.Science,
                            titleAr = "كيمياء التربة والمياه",
                            valueAr = "pH ${formatDecimal1(uiState.soilPh)} • ECe ${formatDecimal1(uiState.soilEce)} dS/m",
                            subtitleAr = "جرعة الحامض: ${formatDecimal1(report.acidDoseLitersPerFeddan)} لتر/فدان",
                            accentColor = EmeraldDark,
                            bgColor = ForestCardDark,
                            modifier = Modifier.weight(1f)
                        )
                        GroundedMetricMiniCard(
                            icon = Icons.Default.Eco,
                            titleAr = "الرصد المكاني والمحبس",
                            valueAr = "محبس #${report.affectedValveNumber} (${report.confidenceScorePct}%)",
                            subtitleAr = report.detectedRiskNameAr,
                            accentColor = if (report.isBiologicalDisease) RoseDanger else AmberWarning,
                            bgColor = ForestCardDark,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectValveSector(report.affectedValveNumber) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroundingToggleChip(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    activeBg: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (isActive) activeBg else ForestCardDark,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (isActive) activeColor else ForestCardBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) activeColor else MintTextMuted,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                color = if (isActive) activeColor else MintTextSoft,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun GroundedMetricMiniCard(
    icon: ImageVector,
    titleAr: String,
    valueAr: String,
    subtitleAr: String,
    accentColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ForestCardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = titleAr,
                    color = MintTextSoft,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = valueAr,
                color = MintTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitleAr,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatbotMessageBubble(
    message: GeminiAgronomyChatMessage,
    uiState: AgroSightUiState,
    todayEt0: Float,
    avgDailyM3: Float,
    isSpeaking: Boolean,
    onToggleSpeak: () -> Unit,
    onShareToWhatsApp: () -> Unit,
    onNavigateToRecommendedScreen: (AppScreen) -> Unit = {}
) {
    val context = LocalContext.current
    val isUser = message.role.equals("USER", ignoreCase = true)
    val messageDirection = remember(message.text) { detectMessageLayoutDirection(message.text) }
    val directionTagSuffix = if (messageDirection == LayoutDirection.Rtl) "rtl" else "ltr"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chatbot_message_${message.id}"),
        horizontalAlignment = Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 4.dp else 20.dp,
                bottomEnd = if (isUser) 20.dp else 4.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) EmeraldPrimary else ForestCardDark
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isUser) EmeraldDark else ForestCardBorder
            ),
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header Row: Speaker Identity + Mode Badge + Grounding / Live Gemini Badge + Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isUser) Icons.Default.Eco else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isUser) ForestCardDark else SkyAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isUser) {
                                "المزارع (${uiState.activeFarmer.fullName})"
                            } else {
                                "المستشار الزراعي Gemini • AgroSight"
                            },
                            color = if (isUser) ForestCardDark else EmeraldPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (message.responseMode == AssistantResponseMode.TECHNICAL) SkySoft else EmeraldSoft,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(
                                0.8.dp,
                                if (message.responseMode == AssistantResponseMode.TECHNICAL) SkyAccent else EmeraldPrimary
                            ),
                            modifier = Modifier.testTag("chatbot_message_mode_badge_${message.id}")
                        ) {
                            Text(
                                text = if (message.responseMode == AssistantResponseMode.TECHNICAL) "فني متقدم" else "وضع المزارع",
                                color = if (message.responseMode == AssistantResponseMode.TECHNICAL) SkyAccent else EmeraldPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        if (!isUser) {
                            Surface(
                                color = if (message.isLiveAiGenerated) SkySoft else EmeraldSoft,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (message.isLiveAiGenerated) SkyAccent else EmeraldPrimary
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = if (message.isLiveAiGenerated) SkyAccent else EmeraldPrimary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = if (message.isLiveAiGenerated) "Gemini 3.8 Live" else "Sentinel-2 & FAO-56",
                                        color = if (message.isLiveAiGenerated) SkyAccent else EmeraldPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        Text(
                            text = message.timestamp,
                            color = if (isUser) ForestCardDark.copy(alpha = 0.8f) else MintTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                // Recommended Screen Action Button (immediately visible at top of assistant bubble for missing/stale data or spatial/irrigation inspection)
                if (!isUser && message.recommendedScreen != null) {
                    val targetScreen = message.recommendedScreen
                    val screenLabel = message.recommendedScreenLabelAr ?: targetScreen.titleAr
                    Surface(
                        color = EmeraldSoft,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, EmeraldPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onNavigateToRecommendedScreen(targetScreen) }
                            .testTag("chatbot_navigate_recommended_${message.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SatelliteAlt,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "افتح شاشة التحليل: $screenLabel",
                                    color = MintTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "الانتقال إلى الشاشة المقترحة",
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Message Body Text with automatic per-message RTL Arabic / LTR English layout direction
                CompositionLocalProvider(LocalLayoutDirection provides messageDirection) {
                    Text(
                        text = message.text,
                        color = if (isUser) ForestCardDark else MintTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = if (messageDirection == LayoutDirection.Rtl) TextAlign.Right else TextAlign.Left,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chatbot_message_dir_${directionTagSuffix}_${message.id}")
                    )
                }

                // Assistant Grounding Telemetry Snapshot Strip & Citations
                if (!isUser) {
                    HorizontalDivider(color = ForestCardBorder.copy(alpha = 0.6f))

                    // Grounded Farm Data Pills used in this answer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.dataTimestampIso?.let { dateIso ->
                            val modeTag = message.dataSourceModeLabelAr ?: uiState.epidemiologyReport.dataModeDesignation
                            TelemetryProofBadge(
                                text = "📅 $dateIso • $modeTag",
                                color = SkyAccent,
                                bg = SkySoft
                            )
                        }
                        TelemetryProofBadge(
                            text = "🛰️ NDVI ${formatDecimal2(uiState.epidemiologyReport.ndviMean)}",
                            color = EmeraldPrimary,
                            bg = EmeraldSoft
                        )
                        TelemetryProofBadge(
                            text = "💧 ET₀ ${formatDecimal2(todayEt0)} مم (${formatDecimal1(avgDailyM3)} م³)",
                            color = SkyAccent,
                            bg = SkySoft
                        )
                        TelemetryProofBadge(
                            text = "🧪 pH ${formatDecimal1(uiState.soilPh)} • ECe ${formatDecimal1(uiState.soilEce)}",
                            color = EmeraldDark,
                            bg = ForestCardElevated
                        )
                    }

                    // Clickable Grounding Citations (Google Maps, Google Search, Sentinel-2 STAC, FAO-56)
                    if (message.citations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = "المصادر المرجعية وإحداثيات الحقل (Grounding Citations):",
                                color = MintTextSoft,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            message.citations.forEach { citation ->
                                GroundingCitationRow(
                                    citation = citation,
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, citation.url.toUri())
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Bottom Action Row: Read Aloud (Arabic TTS) + Share to WhatsApp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (isSpeaking) SkySoft else ForestCardElevated,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, if (isSpeaking) SkyAccent else ForestCardBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onToggleSpeak)
                                .testTag("chatbot_tts_btn_${message.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = "قراءة صوتية",
                                    tint = if (isSpeaking) SkyAccent else EmeraldPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isSpeaking) "إيقاف القراءة" else "استماع صوتي",
                                    color = if (isSpeaking) SkyAccent else MintTextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            color = EmeraldSoft,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onShareToWhatsApp)
                                .testTag("chatbot_share_wa_btn_${message.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "مشاركة عبر واتساب",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "إرسال التوصية لواتساب",
                                    color = EmeraldPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryProofBadge(
    text: String,
    color: Color,
    bg: Color
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        border = BorderStroke(0.8.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun GroundingCitationRow(
    citation: GroundingCitation,
    onClick: () -> Unit
) {
    val isMaps = citation.sourceType == "GOOGLE_MAPS"
    Surface(
        color = if (isMaps) SkySoft else ForestBgDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            0.8.dp,
            if (isMaps) SkyAccent.copy(alpha = 0.5f) else ForestCardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isMaps) Icons.Default.LocationOn else Icons.Default.Public,
                    contentDescription = null,
                    tint = if (isMaps) SkyAccent else EmeraldPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = citation.title,
                    color = if (isMaps) SkyAccent else MintTextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "فتح الرابط المرجعي",
                tint = if (isMaps) SkyAccent else MintTextMuted,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
