package com.example.data

import com.example.BuildConfig
import com.example.model.AppScreen
import com.example.model.formatDecimal1
import com.example.model.formatDecimal2
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class AssistantResponseMode(
    val code: String,
    val titleAr: String,
    val badgeAr: String,
    val descriptionAr: String
) {
    FARMER(
        code = "FARMER",
        titleAr = "وضع المزارع المبسط",
        badgeAr = "وضع المزارع",
        descriptionAr = "شرح عربي بسيط ومختصر وخطوات عملية مباشرة بدون مصطلحات فنية معقدة"
    ),
    TECHNICAL(
        code = "TECHNICAL",
        titleAr = "الوضع الفني المتقدم",
        badgeAr = "الوضع الفني",
        descriptionAr = "قيم المؤشرات الطيفية الدقيقة، مقارنات تاريخية، منهجية FAO-56، وحدود عدم اليقين"
    )
}

data class GroundingCitation(
    val title: String,
    val url: String,
    val sourceType: String // "GOOGLE_SEARCH" | "GOOGLE_MAPS" | "SENTINEL_2_STAC" | "FAO_56"
)

data class GeminiAgronomyChatMessage(
    val id: String,
    val role: String, // "USER" | "ASSISTANT"
    val channel: String, // "GEMINI_CHAT" | "VOICE_CONVERSATION" | "SEARCH_GROUNDED" | "MAPS_GROUNDED"
    val text: String,
    val timestamp: String,
    val citations: List<GroundingCitation> = emptyList(),
    val isLiveAiGenerated: Boolean = false,
    val responseMode: AssistantResponseMode = AssistantResponseMode.FARMER,
    val recommendedScreen: AppScreen? = null,
    val recommendedScreenLabelAr: String? = null,
    val dataTimestampIso: String? = null,
    val dataSourceModeLabelAr: String? = null
)

data class GeminiGroundedResponse(
    val replyText: String,
    val citations: List<GroundingCitation>,
    val isLiveAiGenerated: Boolean = false,
    val recommendedScreen: AppScreen? = null,
    val recommendedScreenLabelAr: String? = null,
    val dataTimestampIso: String? = null,
    val dataSourceModeLabelAr: String? = null
)

interface AgroAiCopilotDataSource {
    suspend fun generateMultiTurnGroundedAgronomyReply(
        chatHistory: List<GeminiAgronomyChatMessage>,
        userMessage: String,
        useGoogleSearchGrounding: Boolean,
        useGoogleMapsGrounding: Boolean,
        farmerName: String,
        fieldName: String,
        regionName: String,
        latitude: Double,
        longitude: Double,
        cropNameAr: String,
        areaFeddans: Float,
        soilPh: Float,
        soilEc: Float,
        waterEc: Float,
        et0MmDay: Float,
        kcValue: Float,
        dailyM3PerFeddan: Float,
        valveRunHours: Float,
        report: EpidemiologyDiagnosisReport,
        responseMode: AssistantResponseMode = AssistantResponseMode.FARMER
    ): GeminiGroundedResponse

    suspend fun generateMultiTurnGroundedAgronomyReply(
        chatHistory: List<GeminiAgronomyChatMessage>,
        userMessage: String,
        useGoogleSearchGrounding: Boolean,
        useGoogleMapsGrounding: Boolean,
        farmerName: String,
        fieldName: String,
        regionName: String,
        latitude: Double,
        longitude: Double,
        cropNameAr: String,
        areaFeddans: Float,
        soilPh: Float,
        soilEc: Float,
        waterEc: Float,
        et0MmDay: Float,
        kcValue: Float,
        dailyM3PerFeddan: Float,
        valveRunHours: Float,
        report: EpidemiologyDiagnosisReport,
        responseMode: AssistantResponseMode,
        agriculturalContext: AgriculturalContextSnapshot?
    ): GeminiGroundedResponse = generateMultiTurnGroundedAgronomyReply(
        chatHistory = chatHistory,
        userMessage = userMessage,
        useGoogleSearchGrounding = useGoogleSearchGrounding,
        useGoogleMapsGrounding = useGoogleMapsGrounding,
        farmerName = farmerName,
        fieldName = fieldName,
        regionName = regionName,
        latitude = latitude,
        longitude = longitude,
        cropNameAr = cropNameAr,
        areaFeddans = areaFeddans,
        soilPh = soilPh,
        soilEc = soilEc,
        waterEc = waterEc,
        et0MmDay = et0MmDay,
        kcValue = kcValue,
        dailyM3PerFeddan = dailyM3PerFeddan,
        valveRunHours = valveRunHours,
        report = report,
        responseMode = responseMode
    )

    suspend fun generateEpidemiologicalInsight(
        farmerName: String,
        fieldName: String,
        cropNameAr: String,
        report: EpidemiologyDiagnosisReport,
        soilPh: Float,
        soilEc: Float,
        tempMaxC: Float,
        humidityPct: Int,
        et0MmDay: Float
    ): String

    suspend fun replyToFarmerWhatsAppMessage(
        farmerQuestion: String,
        farmerName: String,
        fieldName: String,
        cropNameAr: String,
        areaFeddans: Float,
        soilPh: Float,
        soilEc: Float,
        et0MmDay: Float,
        kcValue: Float,
        dailyM3PerFeddan: Float,
        valveRunHours: Float,
        report: EpidemiologyDiagnosisReport
    ): String

    suspend fun generateCustomWorkerMessage(
        prompt: String,
        fallbackText: String
    ): String
}

class GeminiAgroService(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : AgroAiCopilotDataSource {

    companion object {
        const val AGRONOMIC_SAFETY_DISCLAIMER_AR =
            "⚠️ تنويه إرشادي وعلمي: هذه التقديرات مبنية على نماذج استشعار عن بُعد ومعادلات FAO-56 الإرشادية؛ لا تُعد تشخيصاً مرضياً قاطعاً من البيانات الطيفية وحدها، ويُحظر رش أي مبيد كيميائي محدد قبل المعاينة الميدانية ومراجعة مهندس زراعي معتمد، كما لا يتم تعديل جدول الري تلقائياً بدون تأكيدك."
        private const val GEMINI_MODEL_ID = "gemini-3.8-flash"
    }

    private val firebaseAiModel by lazy {
        Firebase.ai.generativeModel(GEMINI_MODEL_ID)
    }

    override suspend fun generateMultiTurnGroundedAgronomyReply(
        chatHistory: List<GeminiAgronomyChatMessage>,
        userMessage: String,
        useGoogleSearchGrounding: Boolean,
        useGoogleMapsGrounding: Boolean,
        farmerName: String,
        fieldName: String,
        regionName: String,
        latitude: Double,
        longitude: Double,
        cropNameAr: String,
        areaFeddans: Float,
        soilPh: Float,
        soilEc: Float,
        waterEc: Float,
        et0MmDay: Float,
        kcValue: Float,
        dailyM3PerFeddan: Float,
        valveRunHours: Float,
        report: EpidemiologyDiagnosisReport,
        responseMode: AssistantResponseMode
    ): GeminiGroundedResponse = generateMultiTurnGroundedAgronomyReply(
        chatHistory = chatHistory,
        userMessage = userMessage,
        useGoogleSearchGrounding = useGoogleSearchGrounding,
        useGoogleMapsGrounding = useGoogleMapsGrounding,
        farmerName = farmerName,
        fieldName = fieldName,
        regionName = regionName,
        latitude = latitude,
        longitude = longitude,
        cropNameAr = cropNameAr,
        areaFeddans = areaFeddans,
        soilPh = soilPh,
        soilEc = soilEc,
        waterEc = waterEc,
        et0MmDay = et0MmDay,
        kcValue = kcValue,
        dailyM3PerFeddan = dailyM3PerFeddan,
        valveRunHours = valveRunHours,
        report = report,
        responseMode = responseMode,
        agriculturalContext = null
    )

    override suspend fun generateMultiTurnGroundedAgronomyReply(
        chatHistory: List<GeminiAgronomyChatMessage>,
        userMessage: String,
        useGoogleSearchGrounding: Boolean,
        useGoogleMapsGrounding: Boolean,
        farmerName: String,
        fieldName: String,
        regionName: String,
        latitude: Double,
        longitude: Double,
        cropNameAr: String,
        areaFeddans: Float,
        soilPh: Float,
        soilEc: Float,
        waterEc: Float,
        et0MmDay: Float,
        kcValue: Float,
        dailyM3PerFeddan: Float,
        valveRunHours: Float,
        report: EpidemiologyDiagnosisReport,
        responseMode: AssistantResponseMode,
        agriculturalContext: AgriculturalContextSnapshot?
    ): GeminiGroundedResponse = withContext(Dispatchers.IO) {
        val recommendedScreen = agriculturalContext?.followUp?.recommendedScreen
        val recommendedScreenLabelAr = agriculturalContext?.followUp?.recommendedScreenLabelAr
        val dataTimestampIso = agriculturalContext?.satellite?.analysisDateIso
            ?: agriculturalContext?.environmental?.observationDateIso
            ?: agriculturalContext?.generatedAtDateIso
        val dataSourceModeLabelAr = agriculturalContext?.let { ctx ->
            "${ctx.satellite.dataStatus.badgeAr} (${ctx.satellite.dataStatus.name} / ${ctx.satellite.dataMode.name})"
        }

        val modeDirective = when (responseMode) {
            AssistantResponseMode.FARMER -> """
                وضع الإجابة المطلوب: [وضع المزارع المبسط - Farmer Mode]
                - تحدث بلغة عربية بسيطة ومباشرة يفهمها المزارع فوراً.
                - اجعل الشرح قصيراً ومركزاً على الخطوات العملية في الحقل (الري، الفحص الميداني، المتابعة).
                - ميّز بوضوح وبعبارات مبسطة بين: (1) رصد القمر الصناعي، (2) المؤشر المحسوب، (3) التفسير الذكي، (4) التوصية الزراعية الإرشادية.
                - اذكر تاريخ البيانات ووضعها (حي LIVE أو مخزن CACHED أو تقديري DEMO).
            """.trimIndent()
            AssistantResponseMode.TECHNICAL -> """
                وضع الإجابة المطلوب: [الوضع الفني المتقدم - Technical Mode]
                - اذكر القيم الرقمية الدقيقة المتاحة للمؤشرات الطيفية: NDVI=${formatDecimal2(report.ndviMean)}, NDRE=${formatDecimal2(report.ndreMean)}, NDMI=${formatDecimal2(report.ndmiMean)}, PSRI=${formatDecimal2(report.psriMean)}.
                - اعرض المقارنة التاريخية الموثقة من سجلات المسح بالتواريخ والفروق الدقيقة، واشرح منهجية FAO-56 Penman-Monteith (ETc = ET0 × Kc) وطريقة حساب المقنن المائي وساعات التشغيل.
                - ميّز بوضوح في أقسام صريحة بين: (1) رصد القمر الصناعي، (2) المؤشر المحسوب، (3) التفسير الذكي، (4) التوصية الزراعية الإرشادية.
                - وضّح حدود البيانات وعدم اليقين العلمي (دقة Sentinel-2 المكانية 10m/20m، وأن مؤشر توافق النمط ${report.confidenceScorePct}% هو تقدير استشعار عن بُعد وليس تشخيصاً معملياً مؤكداً).
            """.trimIndent()
        }

        val structuredContextBlock = agriculturalContext?.toStructuredGeminiPromptBlock() ?: """
            بيانات الحقل الحالية للمزارع ($farmerName):
            - الحقل: $fieldName في منطقة ($regionName) عند إحداثيات Google Maps (Lat=$latitude, Lon=$longitude) بمساحة ${areaFeddans.toInt()} فدان.
            - المحصول: $cropNameAr | معامل المحصول Kc=${formatDecimal2(kcValue)} | البخر-نتح المرجعي ET0=${formatDecimal2(et0MmDay)} مم/يوم.
            - المقنن المائي اليومي المقدر: ${formatDecimal1(dailyM3PerFeddan)} م³/فدان (تشغيل المحبس ${formatDecimal1(valveRunHours)} ساعة).
            - كيمياء التربة والمياه: pH=${formatDecimal1(soilPh)} | ECe=${formatDecimal1(soilEc)} dS/m | ECw=${formatDecimal1(waterEc)} dS/m | جرعة الحامض الإرشادية=${formatDecimal1(report.acidDoseLitersPerFeddan)} لتر/فدان.
            - مؤشرات القمر الصناعي Sentinel-2: NDVI=${formatDecimal2(report.ndviMean)}, NDRE=${formatDecimal2(report.ndreMean)}, PSRI=${formatDecimal2(report.psriMean)}, NDMI=${formatDecimal2(report.ndmiMean)}.
            - المؤشر المكاني المرصود: ${report.detectedRiskNameAr} (${report.hotspotLocationAr}) بثقة تقديرية ${report.confidenceScorePct}%.
        """.trimIndent()

        val systemInstruction = """
            أنت المستشار الزراعي الذكي (Gemini Agronomy & Remote Sensing Copilot) في منصة "أجرو سايت" (AgroSight) المصرية، مربوط مباشرةً بخرائط Google Maps Satellite وأقمار Sentinel-2.
            $modeDirective
            $structuredContextBlock
            أجب باللغة العربية الواضحة حسب وضع الإجابة المطلوب، مع التمييز الصريح بين (رصد القمر الصناعي، المؤشر المحسوب، التفسير الذكي، التوصية الزراعية الإرشادية)، وعدم اختراع أي أرقام خارج السياق الموثق، واختم بتوصية المعاينة الميدانية ومراجعة مهندس زراعي مختص قبل أي تطبيق كيميائي.
        """.trimIndent()

        val defaultCitations = mutableListOf(
            GroundingCitation(
                title = "Google Maps Satellite View ($fieldName - $regionName: $latitude, $longitude)",
                url = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude",
                sourceType = "GOOGLE_MAPS"
            ),
            GroundingCitation(
                title = "Copernicus Sentinel-2 L2A Multi-Spectral STAC (${formatDecimal2(report.ndviMean)} NDVI)",
                url = "https://earth-search.aws.element84.com/v1/collections/sentinel-2-l2a",
                sourceType = "SENTINEL_2_STAC"
            ),
            GroundingCitation(
                title = "Open-Meteo & FAO-56 Penman-Monteith ET0 (${formatDecimal2(et0MmDay)} mm/day)",
                url = "https://open-meteo.com/en/docs",
                sourceType = "FAO_56"
            )
        )
        if (useGoogleMapsGrounding) {
            defaultCitations.add(
                GroundingCitation(
                    title = "Google Maps Navigation & Field Boundary ($regionName @ $latitude,$longitude)",
                    url = "https://www.google.com/maps/@$latitude,$longitude,16z/data=!3m1!1e3",
                    sourceType = "GOOGLE_MAPS"
                )
            )
        }
        if (useGoogleSearchGrounding) {
            val encodedCropQuery = runCatching {
                URLEncoder.encode("$cropNameAr $regionName FAO-56 Sentinel-2", "UTF-8")
            }.getOrDefault("FAO+56+crop+irrigation")
            defaultCitations.add(
                GroundingCitation(
                    title = "Google Search Agronomy Grounding ($cropNameAr - $regionName)",
                    url = "https://www.google.com/search?q=$encodedCropQuery",
                    sourceType = "GOOGLE_SEARCH"
                )
            )
            defaultCitations.add(
                GroundingCitation(
                    title = "FAO Agronomy & Crop Information Bulletins ($cropNameAr)",
                    url = "https://www.fao.org/land-water/databases-and-software/crop-information/en/",
                    sourceType = "GOOGLE_SEARCH"
                )
            )
        }

        val fallbackReply = buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = userMessage,
            farmerName = farmerName,
            fieldName = fieldName,
            cropNameAr = cropNameAr,
            soilPh = soilPh,
            soilEc = soilEc,
            et0MmDay = et0MmDay,
            kcValue = kcValue,
            dailyM3PerFeddan = dailyM3PerFeddan,
            valveRunHours = valveRunHours,
            report = report,
            responseMode = responseMode,
            agriculturalContext = agriculturalContext
        ) + "\n\n📍 *موقع الحقل على Google Maps:* `$latitude, $longitude` ($regionName)\n" +
            "🔗 https://www.google.com/maps/search/?api=1&query=$latitude,$longitude" +
            if (useGoogleSearchGrounding || useGoogleMapsGrounding) {
                "\n\n🌐 *المراجع الإرشادية (Reference Citations):*\n" +
                    "• Google Maps Satellite: `$latitude, $longitude` ($regionName)\n" +
                    "• Copernicus Sentinel-2 L2A & NASA POWER Agroclimatology\n" +
                    "• FAO Irrigation & Drainage Paper No. 56 / نشرات مركز البحوث الزراعية"
            } else {
                ""
            } + "\n\n$AGRONOMIC_SAFETY_DISCLAIMER_AR"

        // 1. Primary Cloud Path: Firebase AI Logic (Gemini 3.8 Flash via App Check, no manual API key required)
        try {
            val historyTranscript = chatHistory.takeLast(6).joinToString("\n") { msg ->
                val speaker = if (msg.role == "USER") "المزارع ($farmerName)" else "المستشار الذكي (AgroSight Gemini)"
                "$speaker: ${msg.text}"
            }
            val fullPrompt = buildString {
                appendLine(systemInstruction)
                if (historyTranscript.isNotBlank()) {
                    appendLine("\n--- سياق المحادثة السابقة ---")
                    appendLine(historyTranscript)
                }
                appendLine("\n--- سؤال المزارع الحالي ---")
                appendLine(userMessage)
            }
            val aiResponse = withTimeout(60_000L) {
                firebaseAiModel.generateContent(fullPrompt)
            }
            val liveText = aiResponse.text?.trim()
            if (!liveText.isNullOrBlank()) {
                return@withContext GeminiGroundedResponse(
                    replyText = "$liveText\n\n$AGRONOMIC_SAFETY_DISCLAIMER_AR",
                    citations = defaultCitations.distinctBy { it.url },
                    isLiveAiGenerated = true,
                    recommendedScreen = recommendedScreen,
                    recommendedScreenLabelAr = recommendedScreenLabelAr,
                    dataTimestampIso = dataTimestampIso,
                    dataSourceModeLabelAr = dataSourceModeLabelAr
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Fall through to REST API key check or deterministic fallback
        }

        // 2. Secondary REST Path if custom BuildConfig.GEMINI_API_KEY is explicitly configured
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiGroundedResponse(
                replyText = fallbackReply,
                citations = defaultCitations.distinctBy { it.url },
                isLiveAiGenerated = false,
                recommendedScreen = recommendedScreen,
                recommendedScreenLabelAr = recommendedScreenLabelAr,
                dataTimestampIso = dataTimestampIso,
                dataSourceModeLabelAr = dataSourceModeLabelAr
            )
        }

        try {
            val contentsArray = JSONArray()
            chatHistory.takeLast(8).forEach { msg ->
                val geminiRole = if (msg.role == "USER") "user" else "model"
                contentsArray.put(
                    JSONObject().apply {
                        put("role", geminiRole)
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    }
                )
            }
            contentsArray.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                }
            )

            val requestJson = JSONObject().apply {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemInstruction))
                    )
                )
                put("contents", contentsArray)
                if (useGoogleSearchGrounding) {
                    put(
                        "tools",
                        JSONArray().put(JSONObject().put("google_search", JSONObject()))
                    )
                }
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.35)
                    }
                )
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL_ID:generateContent"
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext GeminiGroundedResponse(
                        replyText = fallbackReply,
                        citations = defaultCitations,
                        isLiveAiGenerated = false,
                        recommendedScreen = recommendedScreen,
                        recommendedScreenLabelAr = recommendedScreenLabelAr,
                        dataTimestampIso = dataTimestampIso,
                        dataSourceModeLabelAr = dataSourceModeLabelAr
                    )
                }
                val respStr = response.body?.string().orEmpty()
                if (respStr.isBlank()) {
                    return@withContext GeminiGroundedResponse(
                        replyText = fallbackReply,
                        citations = defaultCitations,
                        isLiveAiGenerated = false,
                        recommendedScreen = recommendedScreen,
                        recommendedScreenLabelAr = recommendedScreenLabelAr,
                        dataTimestampIso = dataTimestampIso,
                        dataSourceModeLabelAr = dataSourceModeLabelAr
                    )
                }
                val root = JSONObject(respStr)
                val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
                val text = candidate
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()

                val parsedCitations = mutableListOf<GroundingCitation>()
                val groundingChunks = candidate
                    ?.optJSONObject("groundingMetadata")
                    ?.optJSONArray("groundingChunks")
                if (groundingChunks != null) {
                    for (i in 0 until groundingChunks.length()) {
                        val chunk = groundingChunks.optJSONObject(i)
                        val web = chunk?.optJSONObject("web")
                        if (web != null) {
                            val title = web.optString("title", "Google Search Grounding Source")
                            val uri = web.optString("uri", "")
                            if (uri.isNotBlank()) {
                                parsedCitations.add(
                                    GroundingCitation(
                                        title = title,
                                        url = uri,
                                        sourceType = "GOOGLE_SEARCH"
                                    )
                                )
                            }
                        }
                    }
                }
                val combinedCitations = (parsedCitations + defaultCitations).distinctBy { it.url }
                if (text.isNullOrBlank()) {
                    GeminiGroundedResponse(
                        replyText = fallbackReply,
                        citations = combinedCitations,
                        isLiveAiGenerated = false,
                        recommendedScreen = recommendedScreen,
                        recommendedScreenLabelAr = recommendedScreenLabelAr,
                        dataTimestampIso = dataTimestampIso,
                        dataSourceModeLabelAr = dataSourceModeLabelAr
                    )
                } else {
                    GeminiGroundedResponse(
                        replyText = "$text\n\n$AGRONOMIC_SAFETY_DISCLAIMER_AR",
                        citations = combinedCitations,
                        isLiveAiGenerated = true,
                        recommendedScreen = recommendedScreen,
                        recommendedScreenLabelAr = recommendedScreenLabelAr,
                        dataTimestampIso = dataTimestampIso,
                        dataSourceModeLabelAr = dataSourceModeLabelAr
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            GeminiGroundedResponse(
                replyText = fallbackReply,
                citations = defaultCitations,
                isLiveAiGenerated = false,
                recommendedScreen = recommendedScreen,
                recommendedScreenLabelAr = recommendedScreenLabelAr,
                dataTimestampIso = dataTimestampIso,
                dataSourceModeLabelAr = dataSourceModeLabelAr
            )
        }
    }

    override suspend fun generateEpidemiologicalInsight(
        farmerName: String,
        fieldName: String,
        cropNameAr: String,
        report: EpidemiologyDiagnosisReport,
        soilPh: Float,
        soilEc: Float,
        tempMaxC: Float,
        humidityPct: Int,
        et0MmDay: Float
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = """
            أنت خبير أمراض نبات واستشعار عن بعد بالأقمار الصناعية (Sentinel-2) في منصة AgroSight المصرية.
            مهمتك تحليل المؤشرات الطيفية (NDRE=${formatDecimal2(report.ndreMean)}, PSRI=${formatDecimal2(report.psriMean)}, NDMI=${formatDecimal2(report.ndmiMean)}, NDVI=${formatDecimal2(report.ndviMean)})
            مع بيانات الأرصاد (الحرارة=${formatDecimal1(tempMaxC)}°C، الرطوبة=$humidityPct%، ET0=${formatDecimal2(et0MmDay)} مم/يوم)
            وكيمياء التربة (pH=${formatDecimal1(soilPh)}, ECe=${formatDecimal1(soilEc)} dS/m) لمحصول ($cropNameAr) في حقل ($fieldName) للمزارع ($farmerName).
            النمط المكاني المرصود: ${report.anomalyPattern.code} (${report.caseTitleAr}).
            اكتب تقريراً فنياً موجزاً (4 نقاط مركزة بالعربية الواضحة) يوضح:
            1. التفسير الطيفي الإرشادي للفرق بين المؤشر المرضي المحتمل والعطل الميكانيكي وقلوية التربة.
            2. توصية تعديل الري المقترحة للمحبس المتأثر.
            3. الإجراء الوقائي الموجه للبؤرة مع التأكيد على المعاينة الميدانية ومراجعة مهندس زراعي مختص.
        """.trimIndent()

        val fallback = "🔬 *تحليل استشاري طيفي (Agro-Epidemiology Advisory):*\n" +
            "• *المؤشر المكاني المقدر:* ${report.detectedRiskNameAr} (مؤشر توافق النمط الطيفي ${report.confidenceScorePct}%) في (${report.hotspotLocationAr}).\n" +
            "• *التفسير الفيزيائي:* قراءة الكلوروفيل المبكر (NDRE=${formatDecimal2(report.ndreMean)}) مع مؤشر شيخوخة الأنسجة (PSRI=${formatDecimal2(report.psriMean)}) ورطوبة المجموع الخضري (NDMI=${formatDecimal2(report.ndmiMean)}) تشير إلى ${if (report.isBiologicalDisease) "احتمال نشاط فطري بؤري يستلزم خفض الرطوبة الحرة وفحص البؤرة ميدانياً" else "رجحان سبب هيدروليكي/كيميائي غير فطري"}.\n" +
            "• *توصية الري والسمادة:* ${report.immediateIrrigationActionAr} (جرعة الحامض الإرشادية لمعادلة pH ${formatDecimal1(soilPh)} هي ${formatDecimal1(report.acidDoseLitersPerFeddan)} لتر/فدان).\n" +
            "• *التدخل الإرشادي الموجه:* ${report.targetedPreventiveActionAr}\n" +
            "$AGRONOMIC_SAFETY_DISCLAIMER_AR"

        callGeminiRestOrFallback(
            prompt = systemPrompt,
            fallbackText = fallback
        )
    }

    override suspend fun replyToFarmerWhatsAppMessage(
        farmerQuestion: String,
        farmerName: String,
        fieldName: String,
        cropNameAr: String,
        areaFeddans: Float,
        soilPh: Float,
        soilEc: Float,
        et0MmDay: Float,
        kcValue: Float,
        dailyM3PerFeddan: Float,
        valveRunHours: Float,
        report: EpidemiologyDiagnosisReport
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            أنت المساعد الاستشاري لمنصة "أجرو سايت" (AgroSight) على واتساب، تتحدث بلهجة زراعية مصرية محترمة وواضحة وعلمية مع المزارع "$farmerName".
            بيانات الحقل الحالية:
            - الحقل: $fieldName (${areaFeddans.toInt()} فدان) - المحصول: $cropNameAr (معامل Kc=${formatDecimal2(kcValue)})
            - البخر-نتح اليوم ET0=${formatDecimal2(et0MmDay)} مم/يوم | المقنن اليومي المقدر=${formatDecimal1(dailyM3PerFeddan)} م³/فدان | تشغيل المحبس=${formatDecimal1(valveRunHours)} ساعة
            - كيمياء التربة: pH=${formatDecimal1(soilPh)} | ملوحة التربة ECe=${formatDecimal1(soilEc)} dS/m | جرعة الحامض الإرشادية=${formatDecimal1(report.acidDoseLitersPerFeddan)} لتر/فدان
            - مؤشرات القمر الصناعي Sentinel-2: NDVI=${formatDecimal2(report.ndviMean)}, NDRE=${formatDecimal2(report.ndreMean)}, PSRI=${formatDecimal2(report.psriMean)}, NDMI=${formatDecimal2(report.ndmiMean)}
            - المؤشر الفضائي الحالي: ${report.detectedRiskNameAr} (${report.hotspotLocationAr}) - توافق النمط ${report.confidenceScorePct}%
            - توصية الري والوقاية: ${report.immediateIrrigationActionAr} | ${report.targetedPreventiveActionAr}

            سؤال المزارع على واتساب: "$farmerQuestion"
            أجب في رسالة واتساب منظمة ومختصرة ومباشرة مستخدماً الأرقام أعلاه مع التنبيه على مراجعة المهندس الزراعي المختص قبل تطبيق الكيماويات.
        """.trimIndent()

        val fallback = buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = farmerQuestion,
            farmerName = farmerName,
            fieldName = fieldName,
            cropNameAr = cropNameAr,
            soilPh = soilPh,
            soilEc = soilEc,
            et0MmDay = et0MmDay,
            kcValue = kcValue,
            dailyM3PerFeddan = dailyM3PerFeddan,
            valveRunHours = valveRunHours,
            report = report
        ) + "\n\n$AGRONOMIC_SAFETY_DISCLAIMER_AR"

        callGeminiRestOrFallback(prompt = prompt, fallbackText = fallback)
    }

    override suspend fun generateCustomWorkerMessage(
        prompt: String,
        fallbackText: String
    ): String = withContext(Dispatchers.IO) {
        callGeminiRestOrFallback(prompt = prompt, fallbackText = fallbackText)
    }

    private suspend fun callGeminiRestOrFallback(prompt: String, fallbackText: String): String {
        // 1. Primary Cloud Path: Firebase AI Logic (Gemini 3.8 Flash via App Check)
        try {
            val aiResponse = withTimeout(60_000L) {
                firebaseAiModel.generateContent(prompt)
            }
            val liveText = aiResponse.text?.trim()
            if (!liveText.isNullOrBlank()) {
                return "$liveText\n\n$AGRONOMIC_SAFETY_DISCLAIMER_AR"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Fall through to REST API key check or deterministic fallback
        }

        // 2. Secondary REST Path if custom BuildConfig.GEMINI_API_KEY is explicitly configured
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return fallbackText
        }

        return try {
            val requestJson = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt))
                        )
                    )
                )
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.35)
                    }
                )
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL_ID:generateContent"
            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .post(body)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return fallbackText
                val respStr = response.body?.string().orEmpty()
                if (respStr.isBlank()) return fallbackText
                val root = JSONObject(respStr)
                val text = root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                if (text.isNullOrBlank()) fallbackText else "$text\n\n$AGRONOMIC_SAFETY_DISCLAIMER_AR"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fallbackText
        }
    }

    internal fun buildSmartEgyptianArabicFallbackReply(
        farmerQuestion: String,
        farmerName: String,
        fieldName: String,
        cropNameAr: String,
        soilPh: Float,
        soilEc: Float,
        et0MmDay: Float,
        kcValue: Float,
        dailyM3PerFeddan: Float,
        valveRunHours: Float,
        report: EpidemiologyDiagnosisReport,
        responseMode: AssistantResponseMode = AssistantResponseMode.FARMER,
        agriculturalContext: AgriculturalContextSnapshot? = null
    ): String {
        if (agriculturalContext != null) {
            return buildContextAwareAgronomyReply(
                farmerQuestion = farmerQuestion,
                context = agriculturalContext,
                responseMode = responseMode
            )
        }

        val q = farmerQuestion.lowercase()
        val dataDateLabel = "تاريخ البيانات: اليوم | الوضع: ${report.dataModeDesignation}"

        if (responseMode == AssistantResponseMode.FARMER) {
            return when {
                q.contains("حالة جيدة") || q.contains("المحصول") || q.contains("كويس") || q.contains("صحة") -> {
                    "أهلاً بك يا $farmerName 🌿\n" +
                        "📅 *$dataDateLabel*\n" +
                        "• *🛰️ رصد القمر الصناعي:* تغطية خضراء جيدة لمحصول *$cropNameAr* في *$fieldName* مع ملاحظة مكانية في (${report.hotspotLocationAr}) عند محبس #${report.affectedValveNumber}.\n" +
                        "• *📐 المؤشر المحسوب:* كثافة الغطاء النباتي ورطوبة الأوراق ضمن النطاق النشط مع مؤشر متابعة لمساحة ${formatDecimal1(report.infectedAreaFeddans)} فدان.\n" +
                        "• *🧠 التفسير الذكي:* ${report.detectedRiskNameAr} (تقدير إرشادي يحتاج معاينة بالعين وليس تشخيصاً مرضياً قاطعاً).\n" +
                        "• *🌱 التوصية الزراعية الإرشادية:* ${report.targetedPreventiveActionAr} (دون رش أي مبيد كيميائي إلا بعد الفحص الميداني)."
                }
                q.contains("محتاجة ري") || q.contains("ري") || q.contains("مياه") -> {
                    "أهلاً بك يا $farmerName 💧\n" +
                        "📅 *$dataDateLabel*\n" +
                        "• *🛰️ رصد القمر الصناعي:* متابعة رطوبة المجموع الخضري في *$fieldName* حول محبس #${report.affectedValveNumber}.\n" +
                        "• *📐 المؤشر المحسوب:* المقنن المائي اليومي *${formatDecimal1(dailyM3PerFeddan)} م³/فدان* وزمن تشغيل المحبس *${formatDecimal1(valveRunHours)} ساعة* (عند ET₀=${formatDecimal2(et0MmDay)} مم/يوم و Kc=${formatDecimal2(kcValue)}).\n" +
                        "• *🧠 التفسير الذكي:* الاحتياج المائي محسوب وفق معادلة FAO-56 لتعويض البخر-نتح اليومي.\n" +
                        "• *🌱 التوصية الزراعية الإرشادية:* ${report.immediateIrrigationActionAr} (لا يتم تعديل جدول الري تلقائياً إلا بتأكيدك)."
                }
                q.contains("الأقمار الصناعية") || q.contains("قمر") || q.contains("تحليل") -> {
                    "أهلاً بك يا $farmerName 🛰️\n" +
                        "📅 *$dataDateLabel*\n" +
                        "• *🛰️ رصد القمر الصناعي:* قمر Sentinel-2 رصد حالة النبات في *$fieldName* وحدد نقطة متابعة في (${report.hotspotLocationAr}) عند محبس #${report.affectedValveNumber}.\n" +
                        "• *📐 المؤشر المحسوب:* خضار النبات (NDVI=${formatDecimal2(report.ndviMean)}) ورطوبة الأوراق (NDMI=${formatDecimal2(report.ndmiMean)}).\n" +
                        "• *🧠 التفسير الذكي:* القراءة تشير إلى (${report.detectedRiskNameAr}) وهي إشارة استشعار عن بُعد لتوجيه الفحص الميداني وليست حكماً نهائياً.\n" +
                        "• *🌱 التوصية الزراعية الإرشادية:* ${report.immediateIrrigationActionAr}"
                }
                q.contains("التنبيهات") || q.contains("تنبيه") -> {
                    "أهلاً بك يا $farmerName 🔔\n" +
                        "📅 *$dataDateLabel*\n" +
                        "• *🛰️ رصد القمر الصناعي:* ملاحظة مكانية في (${report.hotspotLocationAr}) عند محبس #${report.affectedValveNumber}.\n" +
                        "• *📐 المؤشر المحسوب:* المقنن المائي اليومي ${formatDecimal1(dailyM3PerFeddan)} م³/فدان (${formatDecimal1(valveRunHours)} ساعة).\n" +
                        "• *🧠 التفسير الذكي:* ${report.detectedRiskNameAr}.\n" +
                        "• *🌱 التوصية الزراعية الإرشادية:* ${report.immediateIrrigationActionAr} | ${report.targetedPreventiveActionAr}"
                }
                else -> {
                    "أهلاً بك يا $farmerName 👋\n" +
                        "📅 *$dataDateLabel*\n" +
                        "• *🛰️ رصد القمر الصناعي:* متابعة حقل *$fieldName* ($cropNameAr) في (${report.hotspotLocationAr}).\n" +
                        "• *📐 المؤشر المحسوب:* المقنن المائي ${formatDecimal1(dailyM3PerFeddan)} م³/فدان (تشغيل المحبس ${formatDecimal1(valveRunHours)} ساعة).\n" +
                        "• *🧠 التفسير الذكي:* ${report.detectedRiskNameAr}.\n" +
                        "• *🌱 التوصية الزراعية الإرشادية:* ${report.immediateIrrigationActionAr}"
                }
            }
        }

        val etcMmDay = et0MmDay * kcValue
        val technicalAppendix = "\n\n📊 *التفاصيل الفنية الدقيقة (Technical Mode):*\n" +
            "📅 *$dataDateLabel*\n" +
            "• *🛰️ رصد القمر الصناعي (Satellite Observation):* بؤرة مكانية في (${report.hotspotLocationAr}) عند محبس #${report.affectedValveNumber} بمساحة ${formatDecimal1(report.infectedAreaFeddans)} فدان.\n" +
            "• *📐 المؤشر المحسوب (Calculated Indicator):* `NDVI = ${formatDecimal2(report.ndviMean)}` | `NDRE = ${formatDecimal2(report.ndreMean)}` | `NDMI = ${formatDecimal2(report.ndmiMean)}` | `PSRI = ${formatDecimal2(report.psriMean)}` | `ET₀ = ${formatDecimal2(et0MmDay)} mm/day` × `Kc = ${formatDecimal2(kcValue)}` ➔ `ETc = ${formatDecimal2(etcMmDay)} mm/day` (`${formatDecimal1(dailyM3PerFeddan)} م³/فدان` لمدة `${formatDecimal1(valveRunHours)} ساعة`).\n" +
            "• *🧠 التفسير الذكي (AI Interpretation):* ${report.detectedRiskNameAr} (توافق النمط الطيفي ${report.confidenceScorePct}% هو مؤشر استرشادي وليس تشخيصاً مرضياً قاطعاً).\n" +
            "• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* ${report.immediateIrrigationActionAr} | ${report.targetedPreventiveActionAr} (لا يُنصح بأي مبيد كيميائي قبل الفحص الميداني)."

        return when {
            q.contains("مرض") || q.contains("اصفرار") || q.contains("بؤرة") || q.contains("رش") -> {
                "أهلاً بك يا $farmerName 🌿\n" +
                    "بخصوص سؤالك عن حالة محصول *$cropNameAr* في *$fieldName*:" +
                    technicalAppendix
            }
            q.contains("حامض") || q.contains("ph") || q.contains("قلوي") || q.contains("ملوح") || q.contains("سماد") -> {
                "يا $farmerName، بالنسبة لكيمياء التربة في *$fieldName*:\n" +
                    "🧪 *رقم الحموضة المدخل:* `pH = ${formatDecimal1(soilPh)}` | *ملوحة التربة:* `ECe = ${formatDecimal1(soilEc)} dS/m`\n" +
                    "• جرعة الحامض الإرشادية: *${formatDecimal1(report.acidDoseLitersPerFeddan)} لتر/فدان* بعد معايرة قلوية المياه." +
                    technicalAppendix
            }
            else -> {
                "أهلاً بك يا $farmerName 👋\n" +
                    "إليك التقرير الفني لحقل *$fieldName* ($cropNameAr):" +
                    technicalAppendix
            }
        }
    }

    /**
     * Deterministic context-aware response builder that strictly uses values from [AgriculturalContextSnapshot],
     * handles missing/stale data, resolves follow-up spatial/sector questions without hallucinating locations,
     * and enforces the 4-layer agricultural safety distinction in both Farmer and Technical modes.
     */
    internal fun buildContextAwareAgronomyReply(
        farmerQuestion: String,
        context: AgriculturalContextSnapshot,
        responseMode: AssistantResponseMode
    ): String {
        val q = farmerQuestion.trim().lowercase()
        val farm = context.farm
        val sat = context.satellite
        val irr = context.irrigation
        val env = context.environmental
        val act = context.activity
        val followUp = context.followUp

        val satDateText = sat.analysisDateIso ?: "غير متوفر"
        val weatherDateText = env.observationDateIso ?: context.generatedAtDateIso
        val freshnessBanner = buildString {
            append("📅 *تاريخ ومصدر البيانات:* ")
            if (sat.isAvailable) {
                append("مسح القمر الصناعي بتاريخ `$satDateText` [${sat.dataMode.badgeAr} - ${sat.dataMode.name}]")
            } else {
                append("مسح القمر الصناعي `غير متوفر (MISSING)`")
            }
            append(" | الأرصاد بتاريخ `$weatherDateText` [${env.dataMode.badgeAr} - ${env.dataMode.name}]")
        }

        val staleOrMissingNotice = buildString {
            if (!sat.isAvailable) {
                appendLine("⚠️ *تنبيه نقص بيانات القمر الصناعي:* لا يتوفر مسح طيفي (NDVI / NDRE / NDMI / PSRI) مسجل لحقل *${farm.fieldName}* حالياً. يرجى فتح شاشة **رادار Sentinel-2 الطيفي** أو **خريطة القمر الصناعي NDVI** لتشغيل المسح الفضائي قبل الاعتماد على التقييم المكاني.")
            } else if (sat.isStale) {
                appendLine("⏰ *تنبيه حداثة بيانات القمر الصناعي:* آخر مسح طيفي متاح بتاريخ `$satDateText` (منذ ${sat.ageDays} يوم) وهو قديم نسبياً؛ لا تتعامل معه كرصد لحظي لحالة اليوم، ونوصي بفتح شاشة **رادار Sentinel-2 الطيفي** لتحديث الصورة الفضائية.")
            }
            if (!env.isAvailable) {
                appendLine("⚠️ *تنبيه نقص بيانات الطقس:* لا تتوفر قراءات أرصاد جوية حالية لهذا الحقل؛ افتح شاشة **الأرصاد والبخر-نتح FAO-56** لتحديث بيانات الطقس.")
            } else if (env.isStale) {
                appendLine("⏰ *تنبيه حداثة بيانات الطقس:* بيانات الأرصاد الحالية تاريخها `$weatherDateText` أو مخزنة محلياً (Offline Cache)؛ يُفضل تحديثها من شاشة **الأرصاد والبخر-نتح FAO-56**.")
            }
        }.trim()

        // 1. Follow-up question about a specific direction / sub-region or valve sector
        if (followUp.referencedValveNumber != null || followUp.referencedDirectionOrSubregionAr != null) {
            return buildSpatialFollowUpReply(
                context = context,
                responseMode = responseMode,
                freshnessBanner = freshnessBanner,
                staleOrMissingNotice = staleOrMissingNotice
            )
        }

        // 2. Missing satellite data when user asks about satellite / NDVI / crop health / anomalies
        val isAskingAboutSatelliteOrHealth = q.contains("قمر") ||
            q.contains("تحليل") ||
            q.contains("ndvi") ||
            q.contains("ndre") ||
            q.contains("ndmi") ||
            q.contains("psri") ||
            q.contains("طيف") ||
            q.contains("محصول") ||
            q.contains("حالة") ||
            q.contains("كويس") ||
            q.contains("صحة") ||
            q.contains("مرض") ||
            q.contains("بؤرة")

        if (!sat.isAvailable && isAskingAboutSatelliteOrHealth) {
            return buildString {
                appendLine("أهلاً بك يا ${farm.farmerDisplayName} 🛰️")
                appendLine(freshnessBanner)
                if (staleOrMissingNotice.isNotBlank()) {
                    appendLine(staleOrMissingNotice)
                }
                appendLine("• *🛰️ رصد القمر الصناعي (Satellite Observation):* غير متوفر حالياً لحقل *${farm.fieldName}* (${farm.cropNameAr} - ${formatDecimal1(farm.areaFeddans)} فدان).")
                appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):* لا يمكن عرض قيم `NDVI` أو `NDRE` أو `NDMI` أو `PSRI` لأن المنصة لا تخترع أرقاماً غير مسجلة.")
                if (irr.isAvailable) {
                    appendLine("• *🧠 التفسير الذكي (AI Interpretation):* تتوفر فقط حسابات الري الأساسية لحقلك في مرحلة (${farm.growthStageNameAr} - عمر ${farm.daysAfterSowing} يوم): `ET₀ = ${formatDecimal2(irr.et0MmDay)} مم/يوم`، `Kc = ${formatDecimal2(irr.fusedKc)}`، والمقنن المائي `${formatDecimal1(irr.grossWaterM3PerFeddanDay)} م³/فدان`.")
                } else {
                    appendLine("• *🧠 التفسير الذكي (AI Interpretation):* نحتاج إلى إجراء مسح Sentinel-2 للحقل أولاً لقراءة المؤشرات الطيفية وتحديد أي إجهاد نباتي.")
                }
                append("• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* اضغط على زر الانتقال أدناه لفتح شاشة **${followUp.recommendedScreenLabelAr ?: "رادار Sentinel-2 الطيفي"}** وتحميل أحدث مسح فضائي للحقل، مع إجراء معاينة ميدانية مباشرة.")
            }
        }

        // 3. Historical comparison query (placed before irrigation so "التاريخي" does not match "ري")
        if (q.contains("تاريخ") || q.contains("سابق") || q.contains("مقارنة") || q.contains("قارن") || q.contains("تغير") || q.contains("تطور")) {
            return buildHistoricalComparisonReply(
                context = context,
                responseMode = responseMode,
                freshnessBanner = freshnessBanner,
                staleOrMissingNotice = staleOrMissingNotice
            )
        }

        // 4. Alerts / Activity / Saved reports query (placed before irrigation so "تقرير" does not match "ري")
        if (q.contains("تنبيه") || q.contains("التنبيهات") || q.contains("سجل") || q.contains("نشاط") || q.contains("تقرير")) {
            return buildAlertsAndActivityReply(
                context = context,
                responseMode = responseMode,
                freshnessBanner = freshnessBanner,
                staleOrMissingNotice = staleOrMissingNotice
            )
        }

        // 5. Weather / Environmental query (placed before irrigation so "رياح" does not match "ري")
        if (q.contains("طقس") || q.contains("حرارة") || q.contains("رياح") || q.contains("مطر") || q.contains("أمطار") || q.contains("رطوبة") || q.contains("مناخ")) {
            return buildWeatherContextReply(
                context = context,
                responseMode = responseMode,
                freshnessBanner = freshnessBanner,
                staleOrMissingNotice = staleOrMissingNotice
            )
        }

        // 6. Irrigation / FAO-56 / Water requirement query
        if (q.contains("ري") || q.contains("مياه") || q.contains("et0") || q.contains("etc") || q.contains("kc") || q.contains("مقنن") || q.contains("جدول")) {
            return buildIrrigationContextReply(
                context = context,
                responseMode = responseMode,
                freshnessBanner = freshnessBanner,
                staleOrMissingNotice = staleOrMissingNotice
            )
        }

        // 7. Default: Comprehensive Crop Health & Satellite Analysis Reply (Farmer Mode vs Technical Mode)
        return buildCropHealthAndSatelliteReply(
            context = context,
            responseMode = responseMode,
            freshnessBanner = freshnessBanner,
            staleOrMissingNotice = staleOrMissingNotice
        )
    }

    private fun buildSpatialFollowUpReply(
        context: AgriculturalContextSnapshot,
        responseMode: AssistantResponseMode,
        freshnessBanner: String,
        staleOrMissingNotice: String
    ): String {
        val farm = context.farm
        val sat = context.satellite
        val irr = context.irrigation
        val followUp = context.followUp

        val followUpLinkSentence = if (followUp.isFollowUpQuestion && !followUp.previousUserQuestion.isNullOrBlank()) {
            "استكمالاً لسؤالك السابق (${followUp.previousUserQuestion}) حول حقل *${farm.fieldName}* (${farm.cropNameAr}):"
        } else {
            "بخصوص استفسارك المكاني في حقل *${farm.fieldName}* (${farm.cropNameAr}):"
        }

        return buildString {
            appendLine("أهلاً بك يا ${farm.farmerDisplayName} 🧭")
            appendLine(freshnessBanner)
            if (staleOrMissingNotice.isNotBlank()) {
                appendLine(staleOrMissingNotice)
            }
            appendLine(followUpLinkSentence)
            appendLine("• *🛰️ رصد القمر الصناعي (Satellite Observation):* ${followUp.verifiedSubregionFindingAr ?: "البؤرة المسجلة تقع في (${sat.hotspotLocationAr}) عند محبس #${sat.affectedValveNumber}."}")
            if (sat.isAvailable) {
                if (responseMode == AssistantResponseMode.TECHNICAL) {
                    appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):* متوسط الحقل `NDVI=${formatDecimal2(sat.ndviMean)}` | `NDRE=${formatDecimal2(sat.ndreMean)}` | `NDMI=${formatDecimal2(sat.ndmiMean)}` | `PSRI=${formatDecimal2(sat.psriMean)}` | المساحة المتأثرة المسجلة `${formatDecimal1(sat.infectedAreaFeddans)} فدان` من إجمالي `${formatDecimal1(farm.areaFeddans)} فدان`.")
                } else {
                    appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):* خضار النبات العام في الحقل `NDVI=${formatDecimal2(sat.ndviMean)}`، والمقنن المائي اليومي `${formatDecimal1(irr.grossWaterM3PerFeddanDay)} م³/فدان` (تشغيل `${formatDecimal1(irr.valveOperatingHours)} ساعة`).")
                }
            } else {
                appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):* لا توجد مؤشرات طيفية محسوبة حالياً لعدم توفر مسح قمر صناعي لهذا الحقل.")
            }
            if (followUp.hasVerifiedSpatialDataForSubregion) {
                appendLine("• *🧠 التفسير الذكي (AI Interpretation):* البيانات المكانية المسجلة تؤكد وجود ملاحظة (${sat.detectedRiskNameAr}) في هذا النطاق بتوافق نمط استرشادي ${sat.heuristicConsistencyPct}% (وهو مؤشر استشعار عن بُعد لتوجيه الفحص وليس تشخيصاً مرضياً قاطعاً).")
            } else {
                appendLine("• *🧠 التفسير الذكي (AI Interpretation):* التزاماً بالدقة العلمية، لا تخترع المنصة تفسيراً مكانياً لمنطقة غير مرصودة بشكل منفصل؛ البيانات الموثقة تتركز في (${sat.hotspotLocationAr}) عند محبس #${sat.affectedValveNumber}.")
            }
            append("• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* افحص الموقع ميدانياً في الحقل، وراجع شاشة **${followUp.recommendedScreenLabelAr ?: "خريطة القمر الصناعي NDVI"}** لمعاينة توزيع القطاعات، دون رش أي مبيد كيميائي قبل المعاينة الميدانية.")
        }
    }

    private fun buildCropHealthAndSatelliteReply(
        context: AgriculturalContextSnapshot,
        responseMode: AssistantResponseMode,
        freshnessBanner: String,
        staleOrMissingNotice: String
    ): String {
        val farm = context.farm
        val sat = context.satellite
        val irr = context.irrigation
        val hist = sat.historicalComparison

        if (responseMode == AssistantResponseMode.FARMER) {
            return buildString {
                appendLine("أهلاً بك يا ${farm.farmerDisplayName} 🌿")
                appendLine(freshnessBanner)
                if (staleOrMissingNotice.isNotBlank()) {
                    appendLine(staleOrMissingNotice)
                }
                appendLine("• *ملخص الحقل:* محصول *${farm.cropNameAr}* في *${farm.fieldName}* (${formatDecimal1(farm.areaFeddans)} فدان) في مرحلة *${farm.growthStageNameAr}* (عمر ${farm.daysAfterSowing} يوم بعد الزراعة بتاريخ `${farm.plantingDateIso}`).")
                appendLine("• *🛰️ رصد القمر الصناعي (Satellite Observation):* صورة Sentinel-2 بتاريخ `${sat.analysisDateIso ?: "الحالي"}` تُظهر نمواً جيداً في معظم الحقل مع نقطة متابعة في (${sat.hotspotLocationAr}) عند محبس #${sat.affectedValveNumber} على مساحة `${formatDecimal1(sat.infectedAreaFeddans)} فدان`.")
                appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):* مؤشر خضار النبات `NDVI = ${formatDecimal2(sat.ndviMean)}`، ومؤشر الكلوروفيل المبكر `NDRE = ${formatDecimal2(sat.ndreMean)}`، ورطوبة الأوراق `NDMI = ${formatDecimal2(sat.ndmiMean)}`، وشيخوخة الأوراق `PSRI = ${formatDecimal2(sat.psriMean)}`" +
                    (if (hist != null) " (مقارنةً بمسح `${hist.previousScanDateIso}` حيث كان `NDVI = ${formatDecimal2(hist.previousNdvi)}` بتغير `${AgriculturalContextSnapshot.formatSignedDelta(hist.ndviDelta)}`)." else "."))
                appendLine("• *🧠 التفسير الذكي (AI Interpretation):* القراءة تشير إلى احتمالية (${sat.detectedRiskNameAr})، وهي ملاحظة استرشادية من القمر الصناعي وليست تشخيصاً قاطعاً لمرض نباتي بدون معاينة على الأرض.")
                append("• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* ${irr.immediateIrrigationActionAr} | ${irr.targetedPreventiveActionAr} (لا تقم برش أي مبيد كيميائي محدد إلا بعد الفحص الميداني ومراجعة المهندس الزراعي).")
            }
        }

        // Technical Mode
        return buildString {
            appendLine("أهلاً بك يا ${farm.farmerDisplayName} 🔬")
            appendLine("📊 *التقرير الفني المتقدم (Technical Mode) — حقل ${farm.fieldName} (${farm.cropNameAr}):*")
            appendLine(freshnessBanner)
            if (staleOrMissingNotice.isNotBlank()) {
                appendLine(staleOrMissingNotice)
            }
            appendLine("• *بيانات المزرعة والحقل:* المزرعة `${farm.farmId}` | الحقل `${farm.fieldId}` (${formatDecimal1(farm.areaFeddans)} فدان) | تاريخ الزراعة `${farm.plantingDateIso}` (${farm.daysAfterSowing} يوم - ${farm.growthStageNameAr}) | التربة `${farm.soilTextureNameAr}` (`pH=${formatDecimal1(farm.soilPh)}`, `ECe=${formatDecimal1(farm.soilEceDsM)} dS/m`, `ECw=${formatDecimal1(farm.waterEcwDsM)} dS/m`).")
            appendLine("• *🛰️ رصد القمر الصناعي (Satellite Observation):* المشهد `${sat.sceneId ?: "Sentinel-2 L2A"}` بتاريخ `${sat.analysisDateIso ?: "غير محدد"}` يرصد النمط المكاني `${sat.anomalyPatternCode}` في (${sat.hotspotLocationAr}) عند محبس #${sat.affectedValveNumber} بمساحة متأثرة `${formatDecimal1(sat.infectedAreaFeddans)} فدان`" +
                (if (sat.hasGeoTiffMetadata) " | GeoTIFF CRS=`${sat.crsEpsg ?: "EPSG:4326"}` بدقة `${sat.spatialResolutionMeters ?: 10.0}m` (${sat.availableGeoTiffIndices.joinToString(", ")})." else "."))
            appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):* `NDVI = ${formatDecimal2(sat.ndviMean)}` | `NDRE = ${formatDecimal2(sat.ndreMean)}` | `NDMI = ${formatDecimal2(sat.ndmiMean)}` | `PSRI = ${formatDecimal2(sat.psriMean)}`.")
            if (hist != null) {
                appendLine("• *📈 المقارنة التاريخية الموثقة (Historical Comparison vs ${hist.previousScanDateIso}):* `NDVI السابق = ${formatDecimal2(hist.previousNdvi)}` (`ΔNDVI = ${AgriculturalContextSnapshot.formatSignedDelta(hist.ndviDelta)}`) | `NDRE السابق = ${formatDecimal2(hist.previousNdre)}` (`ΔNDRE = ${AgriculturalContextSnapshot.formatSignedDelta(hist.ndreDelta)}`) | `NDMI السابق = ${formatDecimal2(hist.previousNdmi)}` (`ΔNDMI = ${AgriculturalContextSnapshot.formatSignedDelta(hist.ndmiDelta)}`) | `PSRI السابق = ${formatDecimal2(hist.previousPsri)}` (`ΔPSRI = ${AgriculturalContextSnapshot.formatSignedDelta(hist.psriDelta)}`) — ${hist.trendSummaryAr}.")
            } else {
                appendLine("• *📈 المقارنة التاريخية:* لا تتوفر قراءة تاريخية سابقة مسجلة لهذا الحقل للمقارنة.")
            }
            appendLine("• *💧 معادلة FAO-56 Penman-Monteith:* `ET₀ = ${formatDecimal2(irr.et0MmDay)} mm/day` × `Kc_fused = ${formatDecimal2(irr.fusedKc)}` (الأساسي `Kc = ${formatDecimal2(irr.baseKc)}`) ➔ `ETc = ${formatDecimal2(irr.netEtcMmDay)} mm/day` | المقنن الإجمالي `${formatDecimal1(irr.grossWaterM3PerFeddanDay)} م³/فدان/يوم` (إجمالي الحقل `${formatDecimal1(irr.totalFarmDailyWaterM3)} م³/يوم`، تشغيل المحبس `${formatDecimal1(irr.valveOperatingHours)} ساعة`، غسيل ملحي `${irr.leachingRequirementPct}%`).")
            appendLine("• *🧠 التفسير الذكي (AI Interpretation):* النمط المرصود يتوافق استرشادياً بنسبة `${sat.heuristicConsistencyPct}%` مع (${sat.detectedRiskNameAr}). نظراً لدقة Sentinel-2 المكانية (10m/20m)، فإن هذا المؤشر يعكس إجهاداً طيفياً ولا يُعد تشخيصاً مرضياً قاطعاً بدون فحص معملي/ميداني.")
            append("• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* ${irr.immediateIrrigationActionAr} | ${irr.targetedPreventiveActionAr} (جرعة الحامض الإرشادية `${formatDecimal1(irr.acidDoseLitersPerFeddan)} لتر/فدان`؛ يُحظر رش مبيد كيميائي محدد قبل المعاينة الميدانية، ولا يتم تعديل جدول الري تلقائياً بدون تأكيدك).")
        }
    }

    private fun buildIrrigationContextReply(
        context: AgriculturalContextSnapshot,
        responseMode: AssistantResponseMode,
        freshnessBanner: String,
        staleOrMissingNotice: String
    ): String {
        val farm = context.farm
        val sat = context.satellite
        val irr = context.irrigation

        return buildString {
            appendLine("أهلاً بك يا ${farm.farmerDisplayName} 💧")
            appendLine(freshnessBanner)
            if (staleOrMissingNotice.isNotBlank()) {
                appendLine(staleOrMissingNotice)
            }
            appendLine("• *🛰️ رصد القمر الصناعي (Satellite Observation):* " +
                if (sat.isAvailable) {
                    "رطوبة المجموع الخضري في *${farm.fieldName}* `NDMI = ${formatDecimal2(sat.ndmiMean)}` وكثافة الغطاء `NDVI = ${formatDecimal2(sat.ndviMean)}` مع متابعة محبس #${sat.affectedValveNumber} (${sat.hotspotLocationAr})."
                } else {
                    "لا يتوفر مسح قمر صناعي حالي؛ الحسابات أدناه مبنية على مرحلة نمو المحصول (${farm.growthStageNameAr}) والأرصاد الجوية."
                }
            )
            appendLine("• *📐 المؤشر المحسوب (Calculated Indicator - FAO-56):* البخر-نتح المرجعي `ET₀ = ${formatDecimal2(irr.et0MmDay)} مم/يوم` | معامل المحصول `Kc = ${formatDecimal2(irr.fusedKc)}` | صافي استهلاك المحصول `ETc = ${formatDecimal2(irr.netEtcMmDay)} مم/يوم` | المقنن المائي الإجمالي `${formatDecimal1(irr.grossWaterM3PerFeddanDay)} م³/فدان/يوم` (إجمالي الحقل `${formatDecimal1(irr.totalFarmDailyWaterM3)} م³/يوم` لمساحة `${formatDecimal1(farm.areaFeddans)} فدان`) | زمن تشغيل المحبس `${formatDecimal1(irr.valveOperatingHours)} ساعة` (نسبة الغسيل الملحي `${irr.leachingRequirementPct}%` وكفاءة الري `${irr.applicationEfficiencyPct}%`).")
            if (responseMode == AssistantResponseMode.TECHNICAL && sat.valveSectors.isNotEmpty()) {
                appendLine("• *تفصيل قطاعات المحابس المسجلة:*")
                sat.valveSectors.forEach { sector ->
                    appendLine("  - ${sector.sectorName} (محبس #${sector.valveId}): `${formatDecimal1(sector.grossWaterM3PerFeddan)} م³/فدان` لمدة `${formatDecimal1(sector.operatingHours)} ساعة` (ECe=${formatDecimal1(sector.soilEceDsM)} dS/m، NDVI=${formatDecimal2(sector.ndvi)}).")
                }
            }
            appendLine("• *🧠 التفسير الذكي (AI Interpretation):* المقنن محسوب آلياً من محرك FAO-56 Penman-Monteith في AgroSight لتعويض فاقد البخر-نتح مع مراعاة ملوحة التربة (`ECe=${formatDecimal1(farm.soilEceDsM)} dS/m`) وحموضتها (`pH=${formatDecimal1(farm.soilPh)}`).")
            append("• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* ${irr.immediateIrrigationActionAr} (جرعة الحامض الإرشادية `${formatDecimal1(irr.acidDoseLitersPerFeddan)} لتر/فدان`). ملاحظة أمان: لا يقوم المساعد بتعديل جدول الري أو تشغيل المحابس تلقائياً إلا بعد مراجعتك وتأكيدك الصريح في شاشة **جدول الري والمحابس**.")
        }
    }

    private fun buildWeatherContextReply(
        context: AgriculturalContextSnapshot,
        responseMode: AssistantResponseMode,
        freshnessBanner: String,
        staleOrMissingNotice: String
    ): String {
        val farm = context.farm
        val sat = context.satellite
        val irr = context.irrigation
        val env = context.environmental

        if (!env.isAvailable) {
            return buildString {
                appendLine("أهلاً بك يا ${farm.farmerDisplayName} ⛅")
                appendLine(freshnessBanner)
                appendLine(staleOrMissingNotice)
                appendLine("• *🛰️ رصد القمر الصناعي (Satellite Observation):* ${if (sat.isAvailable) "NDVI=${formatDecimal2(sat.ndviMean)} في (${sat.hotspotLocationAr})" else "غير متوفر"}.")
                appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):* لا تتوفر قراءات حرارة أو رياح أو رطوبة مسجلة حالياً.")
                appendLine("• *🧠 التفسير الذكي (AI Interpretation):* يلزم تحديث بيانات Open-Meteo لحساب البخر-نتح اليومي بدقة.")
                append("• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* افتح شاشة **الأرصاد والبخر-نتح FAO-56** لتحديث التوقعات الجوية.")
            }
        }

        val alertsSummary = if (env.climaticAlerts.isNotEmpty()) {
            env.climaticAlerts.joinToString(" | ") { "${it.titleAr}: ${it.detailAr}" }
        } else {
            "لا توجد تحذيرات جوية حرجة مسجلة اليوم"
        }

        return buildString {
            appendLine("أهلاً بك يا ${farm.farmerDisplayName} ☀️")
            appendLine(freshnessBanner)
            if (staleOrMissingNotice.isNotBlank()) {
                appendLine(staleOrMissingNotice)
            }
            appendLine("• *🛰️ رصد القمر الصناعي والأرصاد (Observation):* رصد Open-Meteo لمنطقة (${farm.regionName} - حقل *${farm.fieldName}*) بتاريخ `${env.observationDateIso}`: الحرارة العظمى `${formatDecimal1(env.tempMaxC)}°C` والصغرى `${formatDecimal1(env.tempMinC)}°C`، سرعة الرياح `${formatDecimal1(env.windSpeedKmh)} كم/ساعة`، الرطوبة النسبية `${env.relativeHumidityPct}%`، الأمطار `${formatDecimal1(env.precipitationMm)} مم`، والإشعاع الشمسي `${formatDecimal1(env.solarRadiationMjM2)} MJ/m²`.")
            appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):* البخر-نتح المرجعي `ET₀ = ${formatDecimal2(env.et0MmDay)} مم/يوم` ➔ المقنن المائي المحسوب `${formatDecimal1(irr.grossWaterM3PerFeddanDay)} م³/فدان` (${formatDecimal1(irr.valveOperatingHours)} ساعة)." +
                if (responseMode == AssistantResponseMode.TECHNICAL && sat.isAvailable) {
                    " | المؤشرات الطيفية المرافقة: `NDVI=${formatDecimal2(sat.ndviMean)}`, `NDMI=${formatDecimal2(sat.ndmiMean)}`."
                } else ""
            )
            appendLine("• *🧠 التفسير الذكي (AI Interpretation):* التنبيهات المناخية النشطة (${env.climaticAlerts.size}): $alertsSummary.")
            append("• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* راعِ سرعة الرياح (`${formatDecimal1(env.windSpeedKmh)} كم/ساعة`) ودرجة الحرارة (`${formatDecimal1(env.tempMaxC)}°C`) عند جدولة الري أو أي معاملة حقلية، مع معاينة الحقل ميدانياً.")
        }
    }

    private fun buildHistoricalComparisonReply(
        context: AgriculturalContextSnapshot,
        responseMode: AssistantResponseMode,
        freshnessBanner: String,
        staleOrMissingNotice: String
    ): String {
        val farm = context.farm
        val sat = context.satellite
        val irr = context.irrigation
        val hist = sat.historicalComparison

        return buildString {
            appendLine("أهلاً بك يا ${farm.farmerDisplayName} 📈")
            appendLine(freshnessBanner)
            if (staleOrMissingNotice.isNotBlank()) {
                appendLine(staleOrMissingNotice)
            }
            if (!sat.isAvailable || hist == null) {
                appendLine("• *🛰️ رصد القمر الصناعي (Satellite Observation):* ${if (sat.isAvailable) "يوجد مسح واحد بتاريخ `${sat.analysisDateIso}` (`NDVI=${formatDecimal2(sat.ndviMean)}`)." else "لا يتوفر مسح قمر صناعي مسجل."}")
                appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):* لا تتوفر قراءة تاريخية سابقة ثانية في سجلات حقل *${farm.fieldName}* لحساب فروق التغير الزمني.")
                appendLine("• *🧠 التفسير الذكي (AI Interpretation):* المنصة لا تخترع مقارنات تاريخية غير مسجلة.")
                append("• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* افتح شاشة **رادار Sentinel-2 الطيفي** لتحديث وحفظ المسوحات الدورية للحقل.")
            } else {
                appendLine("• *🛰️ رصد القمر الصناعي (Satellite Observation):* مقارنة المسح الحالي (`${sat.analysisDateIso}`) بالمسح السابق الموثق بتاريخ `${hist.previousScanDateIso}` (إجمالي `${hist.scanCountForField}` مسوحات مسجلة لحقل *${farm.fieldName}*):")
                appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):*")
                appendLine("  - مؤشر الكثافة الخضرية `NDVI`: الحالي `${formatDecimal2(sat.ndviMean)}` مقابل السابق `${formatDecimal2(hist.previousNdvi)}` (الفرق `${AgriculturalContextSnapshot.formatSignedDelta(hist.ndviDelta)}`)")
                appendLine("  - مؤشر الكلوروفيل المبكر `NDRE`: الحالي `${formatDecimal2(sat.ndreMean)}` مقابل السابق `${formatDecimal2(hist.previousNdre)}` (الفرق `${AgriculturalContextSnapshot.formatSignedDelta(hist.ndreDelta)}`)")
                appendLine("  - مؤشر رطوبة المجموع الخضري `NDMI`: الحالي `${formatDecimal2(sat.ndmiMean)}` مقابل السابق `${formatDecimal2(hist.previousNdmi)}` (الفرق `${AgriculturalContextSnapshot.formatSignedDelta(hist.ndmiDelta)}`)")
                appendLine("  - مؤشر شيخوخة الأنسجة `PSRI`: الحالي `${formatDecimal2(sat.psriMean)}` مقابل السابق `${formatDecimal2(hist.previousPsri)}` (الفرق `${AgriculturalContextSnapshot.formatSignedDelta(hist.psriDelta)}`)")
                appendLine("• *🧠 التفسير الذكي (AI Interpretation):* ${hist.trendSummaryAr}، مع تركز الملاحظة المكانية الحالية في (${sat.hotspotLocationAr} - محبس #${sat.affectedValveNumber}: ${sat.detectedRiskNameAr}).")
                append("• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* ${irr.immediateIrrigationActionAr} | افحص محبس #${sat.affectedValveNumber} ميدانياً للتحقق من سبب التغير الطيفي قبل أي إجراء.")
            }
        }
    }

    private fun buildAlertsAndActivityReply(
        context: AgriculturalContextSnapshot,
        responseMode: AssistantResponseMode,
        freshnessBanner: String,
        staleOrMissingNotice: String
    ): String {
        val farm = context.farm
        val sat = context.satellite
        val irr = context.irrigation
        val env = context.environmental
        val act = context.activity

        return buildString {
            appendLine("أهلاً بك يا ${farm.farmerDisplayName} 🔔")
            appendLine(freshnessBanner)
            if (staleOrMissingNotice.isNotBlank()) {
                appendLine(staleOrMissingNotice)
            }
            appendLine("• *🛰️ رصد القمر الصناعي (Satellite Observation):* " +
                if (sat.isAvailable) {
                    "${sat.detectedRiskNameAr} في (${sat.hotspotLocationAr}) عند محبس #${sat.affectedValveNumber} (مساحة متأثرة `${formatDecimal1(sat.infectedAreaFeddans)} فدان`)."
                } else {
                    "لا يوجد مسح طيفي مسجل حالياً."
                }
            )
            appendLine("• *📐 المؤشر المحسوب (Calculated Indicator):* المقنن المائي اليومي `${formatDecimal1(irr.grossWaterM3PerFeddanDay)} م³/فدان` (`${formatDecimal1(irr.valveOperatingHours)} ساعة`) | عدد المسوحات المسجلة للحقل: `${act.recentSatelliteScansCount}` | سجلات الري والمعاملات: `${act.recentIrrigationLogsCount}` | تقارير Room المحفوظة: `${act.savedRoomReportsCount}`.")
            appendLine("• *🧠 التفسير الذكي (AI Interpretation):* " +
                if (env.climaticAlerts.isNotEmpty()) {
                    "توجد `${env.climaticAlerts.size}` تنبيهات مناخية نشطة (${env.climaticAlerts.joinToString("، ") { it.titleAr }}) بالإضافة إلى تنبيه المتابعة المكانية في محبس #${sat.affectedValveNumber}."
                } else {
                    "التنبيه الرئيسي المسجل لحقلك يتركز في متابعة (${sat.detectedRiskNameAr}) وضبط تشغيل محبس #${sat.affectedValveNumber}."
                }
            )
            append("• *🌱 التوصية الزراعية الإرشادية (Agricultural Recommendation):* ${irr.immediateIrrigationActionAr} | ${irr.targetedPreventiveActionAr} (بعد المعاينة الميدانية ومراجعة المهندس الزراعي).")
        }
    }
}
