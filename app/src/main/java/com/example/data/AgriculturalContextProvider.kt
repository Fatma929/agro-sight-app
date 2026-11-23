package com.example.data

import com.example.model.AgroClimaticAlert
import com.example.model.AppScreen
import com.example.model.formatCoord4
import com.example.model.formatDecimal1
import com.example.model.formatDecimal2
import com.example.viewmodel.AgroSightUiState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Deterministic, read-only snapshot of the farmer's actual agricultural context across
 * Farm, Field, Satellite (Sentinel-2 & GeoTIFF), FAO-56 Irrigation, Open-Meteo Environment,
 * Farm Activity, and Multi-Turn Follow-Up Resolution.
 *
 * Security & Privacy Invariant:
 * - Never includes sensitive PII (`nationalId`, `whatsappPhone`, `gmailAddress`) or auth secrets.
 * - Never invents or recalculates official AgroSight numerical results; all values are copied
 *   directly from the authoritative calculation engines and stored records.
 */
data class AgriculturalContextSnapshot(
    val generatedAtDateIso: String,
    val farm: FarmContextSummary,
    val satellite: SatelliteContextSummary,
    val irrigation: IrrigationContextSummary,
    val environmental: EnvironmentalContextSummary,
    val activity: FarmActivityContextSummary,
    val followUp: FollowUpResolutionContext
) {
    /**
     * Serializes the deterministic agricultural context into a structured prompt block for Gemini.
     * Excludes all private PII (nationalId, phone, email, auth tokens) and enforces strict
     * non-invention and agricultural safety rules.
     */
    fun toStructuredGeminiPromptBlock(): String = buildString {
        appendLine("=== السياق الزراعي الموثق من محرك AgroSight (Deterministic Agricultural Context) ===")
        appendLine("تاريخ توليد السياق المرجعي: $generatedAtDateIso")
        appendLine()
        appendLine("[A] بيانات المزرعة والحقل النشط (Farm & Field Context):")
        appendLine("- معرف المزرعة: ${farm.farmId} | اسم المزارع: ${farm.farmerDisplayName} | المنطقة: ${farm.regionName}")
        appendLine("- معرف الحقل النشط: ${farm.fieldId} | اسم الحقل: ${farm.fieldName}")
        appendLine("- مساحة الحقل: ${formatDecimal1(farm.areaFeddans)} فدان | الإحداثيات: (${formatCoord4(farm.latitude)}, ${formatCoord4(farm.longitude)})")
        appendLine("- المحصول: ${farm.cropNameAr} (${farm.cropId})")
        appendLine("- تاريخ الزراعة: ${farm.plantingDateIso} | عمر المحصول: ${farm.daysAfterSowing} يوم بعد الزراعة")
        appendLine("- مرحلة النمو الحالية: ${farm.growthStageNameAr} (المرحلة #${farm.growthStageIndex}) | معامل المحصول الأساسي Kc=${formatDecimal2(farm.baseKc)}")
        appendLine("- قوام التربة: ${farm.soilTextureNameAr} | حموضة التربة pH=${formatDecimal1(farm.soilPh)} | ملوحة التربة ECe=${formatDecimal1(farm.soilEceDsM)} dS/m | ملوحة المياه ECw=${formatDecimal1(farm.waterEcwDsM)} dS/m")
        appendLine("- نظام الري: ${farm.irrigationSystemNameAr} (كفاءة التوزيع ${farm.irrigationEfficiencyPct}% | تصرف المحبس ${formatDecimal1(farm.valveFlowRateM3Hr)} م³/ساعة)")
        appendLine()

        appendLine("[B] سياق القمر الصناعي Sentinel-2 وملفات GeoTIFF (Satellite & Spatial Context):")
        appendLine("- حالة توفر بيانات القمر الصناعي: ${if (satellite.isAvailable) "متوفرة (AVAILABLE)" else "غير متوفرة (MISSING)"}")
        appendLine("- مصدر البيانات ووضع التشغيل: ${satellite.dataSourceLabel} [${satellite.dataMode.name}]")
        appendLine("- تاريخ المسح الطيفي: ${satellite.analysisDateIso ?: "غير متوفر"} | عمر البيانات بالأيام: ${satellite.ageDays?.toString() ?: "غير معروف"} | حالة الحداثة: ${if (satellite.isStale) "قديمة وتحتاج تحديث (STALE)" else "حديثة (FRESH)"}")
        if (satellite.isAvailable) {
            appendLine("- المؤشرات الطيفية المحسوبة: NDVI=${formatDecimal2(satellite.ndviMean)} | NDRE=${formatDecimal2(satellite.ndreMean)} | NDMI=${formatDecimal2(satellite.ndmiMean)} | PSRI=${formatDecimal2(satellite.psriMean)}")
            appendLine("- الرصد المكاني والبؤرة: ${satellite.detectedRiskNameAr} [النمط: ${satellite.anomalyPatternCode}] في (${satellite.hotspotLocationAr}) عند محبس #${satellite.affectedValveNumber} (مساحة متأثرة: ${formatDecimal1(satellite.infectedAreaFeddans)} فدان | توافق النمط الإرشادي: ${satellite.heuristicConsistencyPct}%)")
            if (satellite.valveSectors.isNotEmpty()) {
                appendLine("- قراءات قطاعات المحابس المسجلة:")
                satellite.valveSectors.forEach { sector ->
                    appendLine("  * ${sector.sectorName} (محبس #${sector.valveId}): NDVI=${formatDecimal2(sector.ndvi)} | NDWI=${formatDecimal2(sector.ndwi)} | ECe=${formatDecimal1(sector.soilEceDsM)} dS/m | المقنن=${formatDecimal1(sector.grossWaterM3PerFeddan)} م³/فدان | التشغيل=${formatDecimal1(sector.operatingHours)} ساعة | التشخيص=${sector.diagnosisAr}")
                }
            }
            val hist = satellite.historicalComparison
            if (hist != null) {
                appendLine("- المقارنة التاريخية الموثقة (مقارنةً بمسح ${hist.previousScanDateIso} من إجمالي ${hist.scanCountForField} مسوحات للحقل):")
                appendLine("  * NDVI السابق=${formatDecimal2(hist.previousNdvi)} (التغير ΔNDVI=${formatSignedDelta(hist.ndviDelta)})")
                appendLine("  * NDRE السابق=${formatDecimal2(hist.previousNdre)} (التغير ΔNDRE=${formatSignedDelta(hist.ndreDelta)})")
                appendLine("  * NDMI السابق=${formatDecimal2(hist.previousNdmi)} (التغير ΔNDMI=${formatSignedDelta(hist.ndmiDelta)})")
                appendLine("  * PSRI السابق=${formatDecimal2(hist.previousPsri)} (التغير ΔPSRI=${formatSignedDelta(hist.psriDelta)})")
                appendLine("  * اتجاه التغير: ${hist.trendSummaryAr}")
            } else {
                appendLine("- المقارنة التاريخية: لا تتوفر قراءة تاريخية سابقة للمقارنة في هذا الحقل.")
            }
            if (satellite.hasGeoTiffMetadata) {
                appendLine("- بيانات GeoTIFF المكانية: نظام الإحداثيات ${satellite.crsEpsg ?: "EPSG:4326"} | الدقة المكانية ${satellite.spatialResolutionMeters ?: 10.0} متر | نسبة البكسلات الصالحة ${formatDecimal1(satellite.validPixelRatioPct ?: 100f)}% | المؤشرات المتاحة: ${satellite.availableGeoTiffIndices.joinToString(", ")}")
                if (satellite.downloadedGeoTiffSummaries.isNotEmpty()) {
                    appendLine("- ملفات GeoTIFF المحملة محلياً: ${satellite.downloadedGeoTiffSummaries.joinToString(" | ")}")
                }
            }
        } else {
            appendLine("- تنبيه نقص بيانات: لا يوجد مسح طيفي مسجل لهذا الحقل حالياً. وجّه المزارع لفتح شاشة (${followUp.recommendedScreenLabelAr ?: "رادار Sentinel-2 الطيفي"}).")
        }
        appendLine()

        appendLine("[C] سياق الري ومعادلات FAO-56 Penman-Monteith (Irrigation Context):")
        appendLine("- حالة توفر حسابات الري: ${if (irrigation.isAvailable) "متوفرة (AVAILABLE)" else "غير متوفرة (MISSING)"}")
        appendLine("- تاريخ الحساب: ${irrigation.calculationDateIso} | المصدر: ${irrigation.dataSourceLabel}")
        appendLine("- البخر-نتح المرجعي ET0=${formatDecimal2(irrigation.et0MmDay)} مم/يوم | معامل المحصول الأساسي Kc=${formatDecimal2(irrigation.baseKc)} | معامل المحصول المدمج الفعلي Kc_fused=${formatDecimal2(irrigation.fusedKc)}")
        appendLine("- صافي استهلاك المحصول ETc=${formatDecimal2(irrigation.netEtcMmDay)} مم/يوم | الأمطار الفعالة=${formatDecimal2(irrigation.effectivePrecipitationMm)} مم")
        appendLine("- احتياج الغسيل الملحي=${irrigation.leachingRequirementPct}% | كفاءة نظام الري=${irrigation.applicationEfficiencyPct}%")
        appendLine("- صافي المياه=${formatDecimal1(irrigation.netWaterM3PerFeddanDay)} م³/فدان/يوم | المقنن المائي الإجمالي=${formatDecimal1(irrigation.grossWaterM3PerFeddanDay)} م³/فدان/يوم | إجمالي مياه الحقل اليومي=${formatDecimal1(irrigation.totalFarmDailyWaterM3)} م³/يوم")
        appendLine("- زمن تشغيل المحبس=${formatDecimal1(irrigation.valveOperatingHours)} ساعة/يوم | جرعة الحامض الإرشادية=${formatDecimal1(irrigation.acidDoseLitersPerFeddan)} لتر/فدان")
        appendLine("- توصية الري الفورية: ${irrigation.immediateIrrigationActionAr}")
        appendLine("- سجلات الري والمعاملات السابقة للحقل: ${irrigation.recentIrrigationLogs.size} سجل")
        appendLine()

        appendLine("[D] السياق البيئي والأرصاد الجوية Open-Meteo (Environmental Context):")
        appendLine("- حالة توفر بيانات الطقس: ${if (environmental.isAvailable) "متوفرة (AVAILABLE)" else "غير متوفرة (MISSING)"}")
        appendLine("- تاريخ الرصد الجوي: ${environmental.observationDateIso ?: "غير متوفر"} | عمر البيانات بالأيام: ${environmental.ageDays?.toString() ?: "غير معروف"} | حالة الحداثة: ${if (environmental.isStale) "قديمة/مخزنة وتحتاج تحديث (STALE)" else "حديثة (FRESH)"}")
        appendLine("- المصدر ووضع البيانات: ${environmental.dataSourceLabel} [${environmental.dataMode.name}]")
        if (environmental.isAvailable) {
            appendLine("- الحرارة العظمى=${formatDecimal1(environmental.tempMaxC)}°C | الصغرى=${formatDecimal1(environmental.tempMinC)}°C | الأمطار=${formatDecimal1(environmental.precipitationMm)} مم")
            appendLine("- سرعة الرياح=${formatDecimal1(environmental.windSpeedKmh)} كم/ساعة | الرطوبة النسبية=${environmental.relativeHumidityPct}% | الإشعاع الشمسي=${formatDecimal1(environmental.solarRadiationMjM2)} MJ/m²")
            if (environmental.climaticAlerts.isNotEmpty()) {
                appendLine("- التنبيهات المناخية النشطة (${environmental.climaticAlerts.size}): ${environmental.climaticAlerts.joinToString(" | ") { "${it.titleAr}: ${it.detailAr}" }}")
            }
        }
        appendLine()

        appendLine("[E] نشاط المزرعة والسجلات المحفوظة (Farm Activity Context):")
        appendLine("- عدد المسوحات الطيفية للحقل: ${activity.recentSatelliteScansCount} | عدد سجلات الري والمعاملات: ${activity.recentIrrigationLogsCount}")
        appendLine("- عدد التقارير المحفوظة في قاعدة بيانات Room SQL: ${activity.savedRoomReportsCount} (آخر حفظ: ${activity.latestSavedReportTimestamp ?: "لا يوجد"})")
        if (activity.recentNotifications.isNotEmpty()) {
            appendLine("- أحدث التنبيهات المسجلة للحقل: ${activity.recentNotifications.joinToString(" || ")}")
        }
        appendLine()

        appendLine("[F] قواعد الدقة العددية والسلامة الزراعية الإلزامية (Mandatory Rules):")
        appendLine("1. يُمنع منعاً باتاً اختراع أو إعادة حساب أي قيمة رقمية خارج القيم الموثقة أعلاه.")
        appendLine("2. ميّز بوضوح في إجابتك بين: (أ) رصد القمر الصناعي، (ب) المؤشر المحسوب، (ج) التفسير الذكي، (د) التوصية الزراعية الإرشادية.")
        appendLine("3. لا تشخص أي مرض نباتي بشكل قاطع من البيانات الطيفية وحدها، ولا توصِ بأي مبيد كيميائي محدد بدون معاينة ميدانية مؤكدة.")
        appendLine("4. لا تقم بتعديل جدول الري تلقائياً بدون تأكيد صريح من المزارع.")
    }

    companion object {
        fun formatSignedDelta(delta: Float): String {
            val formatted = formatDecimal2(delta)
            return if (delta > 0f) "+$formatted" else formatted
        }
    }
}

data class FarmContextSummary(
    val farmId: String,
    val farmerDisplayName: String,
    val regionName: String,
    val fieldId: String,
    val fieldName: String,
    val areaFeddans: Float,
    val latitude: Double,
    val longitude: Double,
    val cropId: String,
    val cropNameAr: String,
    val plantingDateIso: String,
    val daysAfterSowing: Int,
    val growthStageNameAr: String,
    val growthStageIndex: Int,
    val baseKc: Float,
    val soilTextureNameAr: String,
    val soilPh: Float,
    val soilEceDsM: Float,
    val waterEcwDsM: Float,
    val irrigationSystemNameAr: String,
    val irrigationEfficiencyPct: Int,
    val valveFlowRateM3Hr: Float
)

data class ValveSectorContextItem(
    val valveId: Int,
    val sectorName: String,
    val ndvi: Float,
    val ndwi: Float,
    val soilEceDsM: Float,
    val grossWaterM3PerFeddan: Float,
    val operatingHours: Float,
    val diagnosisAr: String
)

data class HistoricalScanRecord(
    val scanId: String,
    val scanDateIso: String,
    val ndviMean: Float,
    val ndreMean: Float,
    val ndmiMean: Float,
    val psriMean: Float,
    val detectedRiskNameAr: String,
    val hotspotCoordinatesAr: String
)

data class HistoricalSatelliteComparison(
    val previousScanDateIso: String,
    val previousNdvi: Float,
    val previousNdre: Float,
    val previousNdmi: Float,
    val previousPsri: Float,
    val ndviDelta: Float,
    val ndreDelta: Float,
    val ndmiDelta: Float,
    val psriDelta: Float,
    val scanCountForField: Int,
    val trendSummaryAr: String
)

data class SatelliteContextSummary(
    val isAvailable: Boolean,
    val dataSourceLabel: String,
    val dataMode: TelemetryDataMode,
    val sceneId: String?,
    val analysisDateIso: String?,
    val ageDays: Long?,
    val isStale: Boolean,
    val ndviMean: Float,
    val ndreMean: Float,
    val ndmiMean: Float,
    val psriMean: Float,
    val cloudCoverPct: Float?,
    val hasGeoTiffMetadata: Boolean,
    val crsEpsg: String?,
    val spatialResolutionMeters: Double?,
    val validPixelRatioPct: Float?,
    val availableGeoTiffIndices: List<String>,
    val downloadedGeoTiffSummaries: List<String>,
    val anomalyPatternCode: String,
    val detectedRiskNameAr: String,
    val hotspotLocationAr: String,
    val affectedValveNumber: Int,
    val infectedAreaFeddans: Float,
    val heuristicConsistencyPct: Int,
    val isBiologicalDisease: Boolean,
    val valveSectors: List<ValveSectorContextItem>,
    val historicalScans: List<HistoricalScanRecord>,
    val historicalComparison: HistoricalSatelliteComparison?
)

data class ContextWeeklyIrrigationDayItem(
    val dayNameAr: String,
    val dateLabel: String,
    val et0Mm: Float,
    val kc: Float,
    val grossWaterM3PerFeddan: Float,
    val valveHours: Float
)

data class IrrigationContextSummary(
    val isAvailable: Boolean,
    val calculationDateIso: String,
    val dataSourceLabel: String,
    val et0MmDay: Float,
    val baseKc: Float,
    val fusedKc: Float,
    val netEtcMmDay: Float,
    val effectivePrecipitationMm: Float,
    val leachingRequirementPct: Int,
    val applicationEfficiencyPct: Int,
    val netWaterM3PerFeddanDay: Float,
    val grossWaterM3PerFeddanDay: Float,
    val totalFarmDailyWaterM3: Float,
    val valveOperatingHours: Float,
    val acidDoseLitersPerFeddan: Float,
    val immediateIrrigationActionAr: String,
    val targetedPreventiveActionAr: String,
    val weeklyScheduleSummary: List<ContextWeeklyIrrigationDayItem>,
    val recentIrrigationLogs: List<IrrigationAndTreatmentLogEntity>
)

data class EnvironmentalContextSummary(
    val isAvailable: Boolean,
    val dataSourceLabel: String,
    val dataMode: TelemetryDataMode,
    val observationDateIso: String?,
    val ageDays: Long?,
    val isStale: Boolean,
    val isServingFromOfflineCache: Boolean,
    val tempMaxC: Float,
    val tempMinC: Float,
    val precipitationMm: Float,
    val windSpeedKmh: Float,
    val relativeHumidityPct: Int,
    val solarRadiationMjM2: Float,
    val et0MmDay: Float,
    val forecastDayCount: Int,
    val climaticAlerts: List<AgroClimaticAlert>
)

data class FarmActivityContextSummary(
    val recentSatelliteScansCount: Int,
    val recentIrrigationLogsCount: Int,
    val recentTreatmentActions: List<String>,
    val savedRoomReportsCount: Int,
    val latestSavedReportTimestamp: String?,
    val recentNotifications: List<String>
)

data class FollowUpResolutionContext(
    val isFollowUpQuestion: Boolean,
    val previousUserQuestion: String?,
    val previousTopicCategory: String?,
    val referencedValveNumber: Int?,
    val referencedDirectionOrSubregionAr: String?,
    val hasVerifiedSpatialDataForSubregion: Boolean,
    val verifiedSubregionFindingAr: String?,
    val recommendedScreen: AppScreen?,
    val recommendedScreenLabelAr: String?
)

object AgriculturalContextProvider {

    private const val SATELLITE_STALE_THRESHOLD_DAYS = 10L
    private const val WEATHER_STALE_THRESHOLD_DAYS = 2L

    private val DIRECTION_SYNONYMS: List<Pair<String, List<String>>> = listOf(
        "الشمال" to listOf("الشمال", "شمال", "الشمالي", "الشمالية", "بحري", "البحري", "north", "northern"),
        "الجنوب" to listOf("الجنوب", "جنوب", "الجنوبي", "الجنوبية", "قبلي", "القبلي", "south", "southern"),
        "الشرق" to listOf("الشرق", "شرق", "الشرقي", "الشرقية", "east", "eastern"),
        "الغرب" to listOf("الغرب", "غرب", "الغربي", "الغربية", "west", "western"),
        "الوسط" to listOf("الوسط", "وسط", "منتصف", "قلب الحقل", "center", "central")
    )

    /**
     * Builds a deterministic [AgriculturalContextSnapshot] from the active [AgroSightUiState].
     */
    fun buildContext(
        uiState: AgroSightUiState,
        referenceDate: LocalDate = LocalDate.now(),
        chatHistory: List<GeminiAgronomyChatMessage> = uiState.geminiChatMessages,
        currentQuery: String = ""
    ): AgriculturalContextSnapshot {
        val activeFieldId = uiState.fieldId
        val activeFieldEntity = uiState.dbSnapshot.fields.firstOrNull { it.id == activeFieldId }

        // A. Farm & Field Context (Strictly excluding PII like nationalId, whatsappPhone, gmailAddress)
        val farmSummary = FarmContextSummary(
            farmId = uiState.activeFarmer.id,
            farmerDisplayName = uiState.activeFarmer.fullName,
            regionName = uiState.activeFarmer.region,
            fieldId = activeFieldId,
            fieldName = activeFieldEntity?.fieldName ?: uiState.fieldName,
            areaFeddans = activeFieldEntity?.areaFeddans ?: uiState.areaFeddans,
            latitude = activeFieldEntity?.lat ?: uiState.latitude,
            longitude = activeFieldEntity?.lon ?: uiState.longitude,
            cropId = uiState.selectedCrop.id,
            cropNameAr = uiState.selectedCrop.nameAr,
            plantingDateIso = activeFieldEntity?.plantingDate ?: uiState.plantingDateIso,
            daysAfterSowing = uiState.daysAfterSowing,
            growthStageNameAr = uiState.growthEvaluation.stageNameAr,
            growthStageIndex = uiState.growthEvaluation.stageIndex,
            baseKc = uiState.growthEvaluation.currentBaseKc,
            soilTextureNameAr = uiState.soilTexture.nameAr,
            soilPh = uiState.soilPh,
            soilEceDsM = uiState.soilEce,
            waterEcwDsM = uiState.waterEcw,
            irrigationSystemNameAr = uiState.irrigationSystem.nameAr,
            irrigationEfficiencyPct = uiState.irrigationSystem.efficiencyPercent,
            valveFlowRateM3Hr = activeFieldEntity?.flowRateM3Hr ?: uiState.valveFlowRateM3PerFeddanHr
        )

        // B. Satellite Context
        val fieldScans = uiState.dbSnapshot.satelliteSpectralScans
            .filter { it.fieldId == activeFieldId }
            .sortedByDescending { it.scanDate }

        val latestScanEntity = fieldScans.firstOrNull()
        val liveTelemetry = uiState.satelliteAndNasaTelemetry
        val rasterResult = uiState.rasterBackendResult
        val report = uiState.epidemiologyReport

        val hasAnySatelliteRecord = (latestScanEntity != null || liveTelemetry != null || rasterResult != null) &&
            report.ndviMean > 0f &&
            !java.lang.Float.isNaN(report.ndviMean)

        val rawSatelliteDate = when {
            !latestScanEntity?.scanDate.isNullOrBlank() -> latestScanEntity?.scanDate
            !liveTelemetry?.acquisitionDateIso.isNullOrBlank() -> liveTelemetry?.acquisitionDateIso
            else -> null
        }
        val parsedSatelliteDate = parseIsoDateOrNull(rawSatelliteDate)
        val satelliteAgeDays = parsedSatelliteDate?.let {
            ChronoUnit.DAYS.between(it, referenceDate).coerceAtLeast(0L)
        }
        val isSatelliteStale = hasAnySatelliteRecord &&
            (satelliteAgeDays != null && satelliteAgeDays > SATELLITE_STALE_THRESHOLD_DAYS)

        val satelliteMode: TelemetryDataMode = when {
            rasterResult?.isLiveGeoreferencedRaster == true ||
                liveTelemetry?.stacAcquisitionStatus?.isUsableLiveScene == true -> TelemetryDataMode.LIVE
            uiState.isServingFromRoomOfflineCache -> TelemetryDataMode.CACHED
            else -> liveTelemetry?.dataMode ?: TelemetryDataMode.DEMO_ESTIMATED
        }

        val satelliteSourceLabel = when (satelliteMode) {
            TelemetryDataMode.LIVE -> "رصد حي Sentinel-2 L2A STAC / GeoTIFF"
            TelemetryDataMode.CACHED -> "بيانات مخزنة محلياً (Room Offline Cache)"
            TelemetryDataMode.DEMO_ESTIMATED -> "بيانات تقديرية لنموذج الحقل (Estimated Telemetry)"
        }

        val ndviMean = when {
            rasterResult != null && !java.lang.Float.isNaN(rasterResult.ndviMean) && rasterResult.ndviMean > 0f ->
                rasterResult.ndviMean
            else -> report.ndviMean
        }
        val ndreMean = when {
            rasterResult != null && !java.lang.Float.isNaN(rasterResult.ndreMean) && rasterResult.ndreMean > 0f ->
                rasterResult.ndreMean
            else -> report.ndreMean
        }
        val ndmiMean = when {
            rasterResult != null && !java.lang.Float.isNaN(rasterResult.ndmiMean) ->
                rasterResult.ndmiMean
            else -> report.ndmiMean
        }
        val psriMean = when {
            rasterResult != null && !java.lang.Float.isNaN(rasterResult.psriMean) ->
                rasterResult.psriMean
            else -> report.psriMean
        }

        val historicalRecords = fieldScans.map { scan ->
            HistoricalScanRecord(
                scanId = scan.id,
                scanDateIso = scan.scanDate,
                ndviMean = scan.ndviMean,
                ndreMean = scan.ndreMean,
                ndmiMean = scan.ndmiMean,
                psriMean = scan.psriMean,
                detectedRiskNameAr = scan.detectedRiskName,
                hotspotCoordinatesAr = scan.hotspotCoordinates
            )
        }

        val historicalComparison = buildHistoricalComparison(
            currentNdvi = ndviMean,
            currentNdre = ndreMean,
            currentNdmi = ndmiMean,
            currentPsri = psriMean,
            fieldScans = fieldScans,
            temporalAnalysis = liveTelemetry?.temporalAnalysis
        )

        val availableGeoTiffIndices = buildList {
            rasterResult?.outputFiles?.keys?.forEach { add(it.uppercase()) }
            uiState.downloadedGeoTiffRasters.keys.forEach { key ->
                val idx = key.uppercase()
                if (idx !in this) add(idx)
            }
        }
        val downloadedGeoTiffSummaries = uiState.downloadedGeoTiffRasters.values.map { dl ->
            "${dl.indexName}: ${dl.parsedBand.width}x${dl.parsedBand.height} (EPSG:${dl.parsedBand.crsEpsg}, SHA256=${dl.sha256Checksum.take(8)})"
        }
        val hasGeoTiffMetadata = rasterResult != null || uiState.downloadedGeoTiffRasters.isNotEmpty()

        val valveSectorItems = uiState.valveSectors.map { v ->
            ValveSectorContextItem(
                valveId = v.valveNumber,
                sectorName = v.nameAr,
                ndvi = v.ndvi,
                ndwi = v.ndwi,
                soilEceDsM = v.sectorEce,
                grossWaterM3PerFeddan = v.grossWaterM3PerFeddan,
                operatingHours = v.operatingHours,
                diagnosisAr = v.recommendationAr
            )
        }

        val crsEpsgString = when {
            rasterResult != null -> "EPSG:${rasterResult.crsEpsg}"
            uiState.downloadedGeoTiffRasters.isNotEmpty() ->
                "EPSG:${uiState.downloadedGeoTiffRasters.values.first().parsedBand.crsEpsg}"
            else -> null
        }

        val satelliteSummary = SatelliteContextSummary(
            isAvailable = hasAnySatelliteRecord,
            dataSourceLabel = satelliteSourceLabel,
            dataMode = satelliteMode,
            sceneId = rasterResult?.sceneId ?: liveTelemetry?.sceneId ?: latestScanEntity?.id,
            analysisDateIso = rawSatelliteDate,
            ageDays = satelliteAgeDays,
            isStale = isSatelliteStale,
            ndviMean = if (hasAnySatelliteRecord) ndviMean else 0f,
            ndreMean = if (hasAnySatelliteRecord) ndreMean else 0f,
            ndmiMean = if (hasAnySatelliteRecord) ndmiMean else 0f,
            psriMean = if (hasAnySatelliteRecord) psriMean else 0f,
            cloudCoverPct = liveTelemetry?.cloudCoverPct,
            hasGeoTiffMetadata = hasGeoTiffMetadata,
            crsEpsg = crsEpsgString,
            spatialResolutionMeters = rasterResult?.spatialResolutionMeters?.toDouble(),
            validPixelRatioPct = rasterResult?.validPixelRatio?.let { it * 100f },
            availableGeoTiffIndices = availableGeoTiffIndices,
            downloadedGeoTiffSummaries = downloadedGeoTiffSummaries,
            anomalyPatternCode = report.anomalyPattern.code,
            detectedRiskNameAr = report.detectedRiskNameAr,
            hotspotLocationAr = report.hotspotLocationAr,
            affectedValveNumber = report.affectedValveNumber,
            infectedAreaFeddans = report.infectedAreaFeddans,
            heuristicConsistencyPct = report.confidenceScorePct,
            isBiologicalDisease = report.isBiologicalDisease,
            valveSectors = valveSectorItems,
            historicalScans = historicalRecords,
            historicalComparison = if (hasAnySatelliteRecord) historicalComparison else null
        )

        // C. Irrigation Context (Strictly from uiState.formulaAudit / forecastDays / weeklySchedule / valveSectors)
        val formulaAudit = uiState.formulaAudit
        val todayMeteo = uiState.forecastDays.firstOrNull()
        val todaySchedule = uiState.weeklySchedule.firstOrNull()
        val fieldIrrigationLogs = uiState.dbSnapshot.irrigationAndTreatmentLogs
            .filter { it.fieldId == activeFieldId }
            .sortedByDescending { it.date }

        val avgSectorGrossM3 = if (uiState.valveSectors.isNotEmpty()) {
            uiState.valveSectors.map { it.grossWaterM3PerFeddan }.average().toFloat()
        } else {
            todaySchedule?.grossM3PerFeddan ?: 0f
        }
        val avgSectorHours = if (uiState.valveSectors.isNotEmpty()) {
            uiState.valveSectors.map { it.operatingHours }.average().toFloat()
        } else {
            todaySchedule?.valveRunHours ?: 0f
        }

        val et0MmDay = if (formulaAudit.openMeteoEt0MmDay > 0f) {
            formulaAudit.openMeteoEt0MmDay
        } else {
            todayMeteo?.et0MmDay
                ?: todaySchedule?.et0MmDay
                ?: fieldIrrigationLogs.firstOrNull()?.et0
                ?: 0f
        }
        val baseKc = if (formulaAudit.tabularFaoKc > 0f) {
            formulaAudit.tabularFaoKc
        } else {
            uiState.growthEvaluation.currentBaseKc
        }
        val fusedKc = if (formulaAudit.fusedAuthoritativeKc > 0f) {
            formulaAudit.fusedAuthoritativeKc
        } else {
            todayMeteo?.adjustedKc ?: baseKc
        }
        val effectiveRainMm = formulaAudit.effectivePrecipitationMm
        val netEtcMmDay = if (formulaAudit.netEtcMmDay > 0f) {
            formulaAudit.netEtcMmDay
        } else {
            todaySchedule?.etcMmDay ?: (et0MmDay * fusedKc - effectiveRainMm).coerceAtLeast(0f)
        }
        val netWaterM3PerFeddan = if (formulaAudit.netWaterM3PerFeddanDay > 0f) {
            formulaAudit.netWaterM3PerFeddanDay
        } else {
            netEtcMmDay * 4.2f
        }
        val grossWaterM3PerFeddan = if (formulaAudit.grossWaterM3PerFeddanDay > 0f) {
            formulaAudit.grossWaterM3PerFeddanDay
        } else {
            avgSectorGrossM3
        }
        val totalFarmDailyM3 = if (formulaAudit.totalFarmDailyWaterM3 > 0f) {
            formulaAudit.totalFarmDailyWaterM3
        } else {
            grossWaterM3PerFeddan * farmSummary.areaFeddans
        }
        val valveOperatingHours = if (formulaAudit.valveOperatingHoursPerSector > 0f) {
            formulaAudit.valveOperatingHoursPerSector
        } else {
            avgSectorHours
        }
        val leachingPct = if (formulaAudit.leachingPercentage > 0) {
            formulaAudit.leachingPercentage
        } else {
            uiState.soilDiagnostics.leachingRequirementPercent
        }
        val efficiencyPct = if (formulaAudit.systemApplicationEfficiencyRatio > 0f) {
            (formulaAudit.systemApplicationEfficiencyRatio * 100f).roundToInt()
        } else {
            farmSummary.irrigationEfficiencyPct
        }

        val isIrrigationAvailable = et0MmDay > 0f && grossWaterM3PerFeddan > 0f
        val calculationDateIso = parseIsoDateOrNull(todayMeteo?.dateIso)?.toString()
            ?: fieldIrrigationLogs.firstOrNull()?.date
            ?: referenceDate.toString()

        val weeklyScheduleItems = uiState.weeklySchedule.map { day ->
            ContextWeeklyIrrigationDayItem(
                dayNameAr = day.dayLabelAr,
                dateLabel = day.dateIso,
                et0Mm = day.et0MmDay,
                kc = fusedKc,
                grossWaterM3PerFeddan = day.grossM3PerFeddan,
                valveHours = day.valveRunHours
            )
        }

        val irrigationSummary = IrrigationContextSummary(
            isAvailable = isIrrigationAvailable,
            calculationDateIso = calculationDateIso,
            dataSourceLabel = "محرك FAO-56 Penman-Monteith المدمج (Sentinel-2 + Open-Meteo)",
            et0MmDay = et0MmDay,
            baseKc = baseKc,
            fusedKc = fusedKc,
            netEtcMmDay = netEtcMmDay,
            effectivePrecipitationMm = effectiveRainMm,
            leachingRequirementPct = leachingPct,
            applicationEfficiencyPct = efficiencyPct,
            netWaterM3PerFeddanDay = netWaterM3PerFeddan,
            grossWaterM3PerFeddanDay = grossWaterM3PerFeddan,
            totalFarmDailyWaterM3 = totalFarmDailyM3,
            valveOperatingHours = valveOperatingHours,
            acidDoseLitersPerFeddan = report.acidDoseLitersPerFeddan,
            immediateIrrigationActionAr = report.immediateIrrigationActionAr,
            targetedPreventiveActionAr = report.targetedPreventiveActionAr,
            weeklyScheduleSummary = weeklyScheduleItems,
            recentIrrigationLogs = fieldIrrigationLogs
        )

        // D. Environmental Context (Open-Meteo observations, forecasts, and alerts)
        val isWeatherAvailable = uiState.forecastDays.isNotEmpty()
        val rawWeatherDate = todayMeteo?.dateIso
        val parsedWeatherDate = parseIsoDateOrNull(rawWeatherDate)
        val weatherAgeDays = parsedWeatherDate?.let {
            ChronoUnit.DAYS.between(it, referenceDate).coerceAtLeast(0L)
        }
        val isWeatherStale = isWeatherAvailable && (
            (weatherAgeDays != null && weatherAgeDays > WEATHER_STALE_THRESHOLD_DAYS) ||
                uiState.isServingFromRoomOfflineCache
            )
        val weatherDataMode = when {
            uiState.isLiveMeteoConnected && !uiState.isServingFromRoomOfflineCache -> TelemetryDataMode.LIVE
            uiState.isServingFromRoomOfflineCache -> TelemetryDataMode.CACHED
            else -> TelemetryDataMode.DEMO_ESTIMATED
        }
        val weatherSourceLabel = when (weatherDataMode) {
            TelemetryDataMode.LIVE -> "أرصاد Open-Meteo الحية (FAO-56 API)"
            TelemetryDataMode.CACHED -> "أرصاد مخزنة محلياً (Room Offline Cache)"
            TelemetryDataMode.DEMO_ESTIMATED -> "نموذج الأرصاد الإقليمي المرجعي"
        }

        val environmentalSummary = EnvironmentalContextSummary(
            isAvailable = isWeatherAvailable,
            dataSourceLabel = weatherSourceLabel,
            dataMode = weatherDataMode,
            observationDateIso = parsedWeatherDate?.toString() ?: rawWeatherDate,
            ageDays = weatherAgeDays,
            isStale = isWeatherStale,
            isServingFromOfflineCache = uiState.isServingFromRoomOfflineCache,
            tempMaxC = todayMeteo?.tempMaxC ?: 0f,
            tempMinC = todayMeteo?.tempMinC ?: 0f,
            precipitationMm = todayMeteo?.precipitationMm ?: 0f,
            windSpeedKmh = todayMeteo?.windSpeedKmh ?: 0f,
            relativeHumidityPct = todayMeteo?.relativeHumidityPct ?: 0,
            solarRadiationMjM2 = todayMeteo?.solarRadiationMjM2 ?: 0f,
            et0MmDay = todayMeteo?.et0MmDay ?: et0MmDay,
            forecastDayCount = uiState.forecastDays.size,
            climaticAlerts = uiState.climaticAlerts
        )

        // E. Farm Activity Context
        val fieldNotifications = uiState.dbSnapshot.whatsappMessages
            .filter { it.fieldId == activeFieldId }
            .take(5)
            .map { "[${it.alertCategory}] ${it.messageText}" }

        val activitySummary = FarmActivityContextSummary(
            recentSatelliteScansCount = fieldScans.size,
            recentIrrigationLogsCount = fieldIrrigationLogs.size,
            recentTreatmentActions = fieldIrrigationLogs.take(3).map {
                "${it.date}: ${it.preventiveSprayAction} (${formatDecimal1(it.waterM3PerFeddan)} م³/فدان - ${it.status})"
            },
            savedRoomReportsCount = uiState.roomSqlAudits.size,
            latestSavedReportTimestamp = uiState.roomSqlAudits.firstOrNull()?.recordedAt,
            recentNotifications = fieldNotifications
        )

        // F. Follow-Up & Spatial Resolution Context
        val followUpContext = resolveFollowUpContext(
            currentQuery = currentQuery,
            chatHistory = chatHistory,
            satellite = satelliteSummary,
            irrigation = irrigationSummary,
            environmental = environmentalSummary
        )

        return AgriculturalContextSnapshot(
            generatedAtDateIso = referenceDate.toString(),
            farm = farmSummary,
            satellite = satelliteSummary,
            irrigation = irrigationSummary,
            environmental = environmentalSummary,
            activity = activitySummary,
            followUp = followUpContext
        )
    }

    private fun buildHistoricalComparison(
        currentNdvi: Float,
        currentNdre: Float,
        currentNdmi: Float,
        currentPsri: Float,
        fieldScans: List<SatelliteSpectralScanEntity>,
        temporalAnalysis: TemporalChangeAnalysis?
    ): HistoricalSatelliteComparison? {
        if (fieldScans.size >= 2) {
            val previousScan = fieldScans[1]
            val ndviDelta = currentNdvi - previousScan.ndviMean
            val ndreDelta = currentNdre - previousScan.ndreMean
            val ndmiDelta = currentNdmi - previousScan.ndmiMean
            val psriDelta = currentPsri - previousScan.psriMean
            val trendAr = when {
                ndviDelta >= 0.03f -> "تحسن ملحوظ في الكثافة الخضرية مقارنة بالمسح السابق"
                ndviDelta <= -0.03f -> "انخفاض في الكثافة الخضرية يستدعي متابعة البؤرة والمحبس المتأثر"
                else -> "استقرار نسبي في المؤشرات الطيفية مقارنة بالمسح السابق"
            }
            return HistoricalSatelliteComparison(
                previousScanDateIso = previousScan.scanDate,
                previousNdvi = previousScan.ndviMean,
                previousNdre = previousScan.ndreMean,
                previousNdmi = previousScan.ndmiMean,
                previousPsri = previousScan.psriMean,
                ndviDelta = ndviDelta,
                ndreDelta = ndreDelta,
                ndmiDelta = ndmiDelta,
                psriDelta = psriDelta,
                scanCountForField = fieldScans.size,
                trendSummaryAr = trendAr
            )
        }

        val validObservations = temporalAnalysis?.observations
            ?.filter { !it.isCloudGappedOrUnusable && it.observedNdviMean != null }
            .orEmpty()
        if (validObservations.size >= 2) {
            val previousObs = validObservations[validObservations.size - 2]
            val prevNdvi = previousObs.observedNdviMean ?: currentNdvi
            val prevNdre = previousObs.observedNdreMean ?: currentNdre
            val prevNdmi = previousObs.observedNdmiMean ?: currentNdmi
            val prevPsri = previousObs.observedPsriMean ?: currentPsri
            val ndviDelta = currentNdvi - prevNdvi
            val ndreDelta = currentNdre - prevNdre
            val ndmiDelta = currentNdmi - prevNdmi
            val psriDelta = currentPsri - prevPsri
            return HistoricalSatelliteComparison(
                previousScanDateIso = previousObs.acquisitionDateIso,
                previousNdvi = prevNdvi,
                previousNdre = prevNdre,
                previousNdmi = prevNdmi,
                previousPsri = prevPsri,
                ndviDelta = ndviDelta,
                ndreDelta = ndreDelta,
                ndmiDelta = ndmiDelta,
                psriDelta = psriDelta,
                scanCountForField = validObservations.size,
                trendSummaryAr = temporalAnalysis?.temporalTrendSummaryAr ?: "مقارنة زمنية متعددة المسوحات"
            )
        }

        return null
    }

    private fun resolveFollowUpContext(
        currentQuery: String,
        chatHistory: List<GeminiAgronomyChatMessage>,
        satellite: SatelliteContextSummary,
        irrigation: IrrigationContextSummary,
        environmental: EnvironmentalContextSummary
    ): FollowUpResolutionContext {
        val cleanQuery = currentQuery.trim()
        val qLower = cleanQuery.lowercase()

        val previousUserMsg = chatHistory
            .filter { it.role.equals("USER", ignoreCase = true) }
            .lastOrNull { it.text.trim() != cleanQuery }
            ?.text

        val hasFollowUpMarkers = qLower.startsWith("طيب") ||
            qLower.startsWith("و") ||
            qLower.contains("طيب و") ||
            qLower.contains("ماذا عن") ||
            qLower.contains("وماذا عن") ||
            qLower.contains("والمنطقة") ||
            qLower.contains("ومحبس") ||
            qLower.contains("المسح السابق") ||
            qLower.contains("القراءة السابقة") ||
            qLower.contains("مقارنة")

        val isFollowUp = previousUserMsg != null && (hasFollowUpMarkers || cleanQuery.length <= 48)

        val previousTopicCategory = previousUserMsg?.lowercase()?.let { prev ->
            when {
                prev.contains("محصول") || prev.contains("حالة") || prev.contains("كويس") || prev.contains("صحة") -> "CROP_HEALTH"
                prev.contains("ري") || prev.contains("مياه") || prev.contains("محبس") -> "IRRIGATION"
                prev.contains("قمر") || prev.contains("تحليل") || prev.contains("ndvi") -> "SATELLITE"
                prev.contains("طقس") || prev.contains("حرارة") || prev.contains("رياح") -> "WEATHER"
                else -> "GENERAL_FARM"
            }
        }

        // Detect explicit valve number reference (e.g., "محبس 3", "محبس #2", "valve 1")
        val valveRegex = Regex("""(?:محبس|قطاع|valve)\s*#?\s*([1-9]\d*)""", RegexOption.IGNORE_CASE)
        val referencedValve = valveRegex.find(cleanQuery)?.groupValues?.getOrNull(1)?.toIntOrNull()

        // Detect directional or sub-region reference (e.g., "الشمال", "الجنوب", "الشرق", "الغرب", "الوسط")
        val referencedDirection = DIRECTION_SYNONYMS.firstOrNull { (_, keywords) ->
            keywords.any { kw -> qLower.contains(kw) }
        }?.first

        // Verify whether recorded spatial telemetry actually covers the referenced sub-region or valve
        var hasVerifiedSpatial = false
        var verifiedFindingAr: String? = null

        if (referencedValve != null) {
            val matchingSector = satellite.valveSectors.firstOrNull { it.valveId == referencedValve }
            if (matchingSector != null) {
                hasVerifiedSpatial = true
                verifiedFindingAr =
                    "بيانات ${matchingSector.sectorName} (محبس #${matchingSector.valveId}) المسجلة فعلياً: " +
                        "NDVI=${formatDecimal2(matchingSector.ndvi)}، NDWI=${formatDecimal2(matchingSector.ndwi)}، " +
                        "ملوحة التربة ECe=${formatDecimal1(matchingSector.soilEceDsM)} dS/m، " +
                        "المقنن المائي=${formatDecimal1(matchingSector.grossWaterM3PerFeddan)} م³/فدان " +
                        "(التشغيل ${formatDecimal1(matchingSector.operatingHours)} ساعة)، " +
                        "والتشخيص المسجل: (${matchingSector.diagnosisAr})."
            } else {
                hasVerifiedSpatial = false
                val availableValves = satellite.valveSectors.joinToString("، ") { "محبس #${it.valveId}" }
                verifiedFindingAr =
                    "لا توجد بيانات مسجلة لمحبس رقم #$referencedValve في هذا الحقل. القطاعات المتوفرة فعلياً هي: ($availableValves)."
            }
        } else if (referencedDirection != null) {
            val directionKeywords = DIRECTION_SYNONYMS.first { it.first == referencedDirection }.second
            val hotspotMatchesDirection = satellite.isAvailable && directionKeywords.any { kw ->
                satellite.hotspotLocationAr.lowercase().contains(kw)
            }
            val matchingSector = satellite.valveSectors.firstOrNull { sector ->
                directionKeywords.any { kw ->
                    sector.sectorName.lowercase().contains(kw) || sector.diagnosisAr.lowercase().contains(kw)
                }
            }
            val matchingScanHotspot = satellite.historicalScans.firstOrNull()?.let { scan ->
                directionKeywords.any { kw -> scan.hotspotCoordinatesAr.lowercase().contains(kw) }
            } ?: false

            if (hotspotMatchesDirection || matchingSector != null || matchingScanHotspot) {
                hasVerifiedSpatial = true
                verifiedFindingAr = buildString {
                    append("الرصد المكاني الموثق لجهة ($referencedDirection) في حقل (${satellite.hotspotLocationAr}): ")
                    append("${satellite.detectedRiskNameAr} عند محبس #${satellite.affectedValveNumber} ")
                    append("بمساحة متأثرة ${formatDecimal1(satellite.infectedAreaFeddans)} فدان ")
                    append("(NDVI=${formatDecimal2(satellite.ndviMean)}، NDRE=${formatDecimal2(satellite.ndreMean)}، NDMI=${formatDecimal2(satellite.ndmiMean)}، PSRI=${formatDecimal2(satellite.psriMean)}).")
                    if (matchingSector != null) {
                        append(" قراءة القطاع (${matchingSector.sectorName}): NDVI=${formatDecimal2(matchingSector.ndvi)}، المقنن=${formatDecimal1(matchingSector.grossWaterM3PerFeddan)} م³/فدان.")
                    }
                }
            } else {
                hasVerifiedSpatial = false
                verifiedFindingAr = if (satellite.isAvailable) {
                    "لا يتوفر تقسيم مكاني منفصل مسجل لمنطقة ($referencedDirection) تحديداً في المسح الحالي؛ " +
                        "البؤرة المكانية المرصودة فعلياً في بيانات الحقل تقع في (${satellite.hotspotLocationAr}) عند محبس #${satellite.affectedValveNumber} (${satellite.detectedRiskNameAr}). " +
                        "يرجى عدم افتراض وجود إصابة في ($referencedDirection) بدون فتح خريطة القمر الصناعي أو المعاينة الميدانية."
                } else {
                    "لا تتوفر بيانات مسح مكاني بالأقمار الصناعية لمنطقة ($referencedDirection) في هذا الحقل حالياً."
                }
            }
        }

        // Determine recommended screen when data is missing, stale, or relevant to the query
        val (recommendedScreen, recommendedLabelAr) = when {
            !satellite.isAvailable || satellite.isStale ->
                AppScreen.DISEASE_RADAR to "رادار Sentinel-2 الطيفي (تحديث المسح الفضائي)"
            !environmental.isAvailable || environmental.isStale ->
                AppScreen.METEO_ANALYTICS to "الأرصاد والبخر-نتح FAO-56 (تحديث الطقس)"
            referencedDirection != null || qLower.contains("خريطة") || qLower.contains("geotiff") || qLower.contains("شمال") || qLower.contains("جنوب") ->
                AppScreen.SATELLITE_NDVI_MAP to "خريطة القمر الصناعي NDVI"
            qLower.contains("قمر") || qLower.contains("ndvi") || qLower.contains("ndre") || qLower.contains("مرض") || qLower.contains("بؤرة") ->
                AppScreen.DISEASE_RADAR to "رادار Sentinel-2 الطيفي"
            qLower.contains("ري") || qLower.contains("مياه") || qLower.contains("محبس") || qLower.contains("et0") || !irrigation.isAvailable ->
                AppScreen.IRRIGATION_SCHEDULE to "جدول الري والمحابس"
            qLower.contains("طقس") || qLower.contains("حرارة") || qLower.contains("رياح") || qLower.contains("مطر") ->
                AppScreen.METEO_ANALYTICS to "الأرصاد والبخر-نتح FAO-56"
            qLower.contains("تربة") || qLower.contains("ملوح") || qLower.contains("ph") || qLower.contains("حامض") ->
                AppScreen.FARM_SOIL_STUDIO to "معمل التربة والمزرعة"
            else -> null to null
        }

        return FollowUpResolutionContext(
            isFollowUpQuestion = isFollowUp,
            previousUserQuestion = previousUserMsg,
            previousTopicCategory = previousTopicCategory,
            referencedValveNumber = referencedValve,
            referencedDirectionOrSubregionAr = referencedDirection,
            hasVerifiedSpatialDataForSubregion = hasVerifiedSpatial,
            verifiedSubregionFindingAr = verifiedFindingAr,
            recommendedScreen = recommendedScreen,
            recommendedScreenLabelAr = recommendedLabelAr
        )
    }

    internal fun parseIsoDateOrNull(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val dateToken = Regex("""(\d{4}-\d{2}-\d{2})""").find(trimmed)?.groupValues?.getOrNull(1)
            ?: return null
        return runCatching { LocalDate.parse(dateToken) }.getOrNull()
    }
}
