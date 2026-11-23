package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FieldEntity
import com.example.model.AppScreen
import com.example.ui.theme.AmberSoft
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSoft
import com.example.ui.theme.ForestBgDark
import com.example.ui.theme.ForestCardBorder
import com.example.ui.theme.ForestCardDark
import com.example.ui.theme.ForestCardElevated
import com.example.ui.theme.MintTextMuted
import com.example.ui.theme.MintTextPrimary
import com.example.ui.theme.MintTextSoft
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.RoseSoft
import com.example.ui.theme.SkyAccent
import com.example.ui.theme.SkySoft
import com.example.viewmodel.AgroSightUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Farmer-First Agricultural SaaS Design Tokens (Phase A & Phase B).
 * Strictly aliases the original AgroSight brand palette from [com.example.ui.theme]
 * without introducing or replacing any color definitions.
 */
object FarmerFirstTokens {
    // Original AgroSight Surfaces & Borders
    val SurfacePrimary = ForestBgDark
    val SurfaceElevated = ForestCardDark
    val SurfaceHighlight = ForestCardElevated
    val CardBorder = ForestCardBorder
    val CardBorderStrong = EmeraldPrimary

    // Original AgroSight Typography Colors
    val TextPrimary = MintTextPrimary
    val TextSecondary = MintTextSoft
    val TextMuted = MintTextMuted

    // Original AgroSight Semantic Agricultural Health & Irrigation Status Colors
    val HealthyGreen = EmeraldPrimary
    val HealthyBg = EmeraldSoft
    val ModerateAmber = AmberWarning
    val ModerateBg = AmberSoft
    val CriticalRed = RoseDanger
    val CriticalBg = RoseSoft
    val IrrigationBlue = SkyAccent
    val IrrigationBg = SkySoft

    // Shape & Touch Target Tokens
    val CardRadius = 20.dp
    val PillRadius = 50.dp
    val MinTouchTarget = 48.dp
}

enum class FarmStatusLevel(
    val badgeLabelAr: String,
    val accentColor: Color,
    val containerColor: Color
) {
    HEALTHY("حالة مطمئنة", FarmerFirstTokens.HealthyGreen, FarmerFirstTokens.HealthyBg),
    MODERATE("تحتاج متابعة", FarmerFirstTokens.ModerateAmber, FarmerFirstTokens.ModerateBg),
    CRITICAL("تنبيه عاجل", FarmerFirstTokens.CriticalRed, FarmerFirstTokens.CriticalBg),
    IRRIGATION("توصية ري", FarmerFirstTokens.IrrigationBlue, FarmerFirstTokens.IrrigationBg)
}

/**
 * SECTION A — WELCOME & FARM/FIELD SWITCHER
 * Personalized greeting, selected farm name, current date, and clear farm/field switching control.
 */
@Composable
fun FarmerWelcomeHeaderCard(
    uiState: AgroSightUiState,
    onSelectField: (String) -> Unit,
    onOpenAccountSwitcher: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFarmSelectorExpanded by rememberSaveable { mutableStateOf(false) }
    val arabicDateText = remember {
        try {
            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale.forLanguageTag("ar-EG"))
            today.format(formatter)
        } catch (_: Exception) {
            "اليوم • الموسم الزراعي الحالي"
        }
    }

    val fieldsList: List<FieldEntity> = remember(uiState.dbSnapshot.fields, uiState.fieldId, uiState.fieldName) {
        uiState.dbSnapshot.fields.ifEmpty {
            listOf(
                FieldEntity(
                    id = uiState.fieldId,
                    farmerId = uiState.activeFarmer.id,
                    fieldName = uiState.fieldName,
                    lat = uiState.latitude,
                    lon = uiState.longitude,
                    areaFeddans = uiState.areaFeddans,
                    cropType = uiState.selectedCrop.nameAr,
                    plantingDate = uiState.plantingDateIso,
                    irrigationType = "تنقيط محوري",
                    flowRateM3Hr = uiState.valveFlowRateM3PerFeddanHr
                )
            )
        }
    }

    Card(
        shape = RoundedCornerShape(FarmerFirstTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = FarmerFirstTokens.SurfaceElevated),
        border = BorderStroke(1.dp, FarmerFirstTokens.CardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("farmer_welcome_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Greeting + Current Date + Account & Alert actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_profile_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(FarmerFirstTokens.HealthyBg)
                            .border(1.5.dp, FarmerFirstTokens.HealthyGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Agriculture,
                            contentDescription = "شعار المزارع",
                            tint = FarmerFirstTokens.HealthyGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "مرحباً بك، ${uiState.activeFarmer.fullName}",
                            color = FarmerFirstTokens.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = FarmerFirstTokens.HealthyGreen,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = arabicDateText,
                                color = FarmerFirstTokens.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onOpenAccountSwitcher,
                        modifier = Modifier
                            .size(FarmerFirstTokens.MinTouchTarget)
                            .clip(CircleShape)
                            .background(FarmerFirstTokens.SurfacePrimary)
                            .border(1.dp, FarmerFirstTokens.CardBorder, CircleShape)
                            .testTag("home_admin_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "تبديل حساب المزرعة",
                            tint = FarmerFirstTokens.HealthyGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier
                            .size(FarmerFirstTokens.MinTouchTarget)
                            .clip(CircleShape)
                            .background(FarmerFirstTokens.SurfacePrimary)
                            .border(1.dp, FarmerFirstTokens.CardBorder, CircleShape)
                            .testTag("home_notifications_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "تنبيهات المزرعة",
                            tint = FarmerFirstTokens.TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Selected Farm & Field Summary Bar with Switcher Toggle
            Surface(
                color = FarmerFirstTokens.SurfacePrimary,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, FarmerFirstTokens.CardBorderStrong),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FarmerFirstTokens.MinTouchTarget)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { isFarmSelectorExpanded = !isFarmSelectorExpanded }
                    .testTag("farmer_farm_switcher_button")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = FarmerFirstTokens.HealthyGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "المزرعة والحقل المختار حالياً",
                                color = FarmerFirstTokens.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${uiState.fieldName} • ${uiState.selectedCrop.nameAr} (${uiState.areaFeddans.roundToInt()} فدان)",
                                color = FarmerFirstTokens.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        color = FarmerFirstTokens.HealthyBg,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, FarmerFirstTokens.HealthyGreen.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "تغيير الحقل (${fieldsList.size})",
                                color = FarmerFirstTokens.HealthyGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (isFarmSelectorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "تبديل الحقل",
                                tint = FarmerFirstTokens.HealthyGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Always-accessible horizontal field switching chips + expandable detail list
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("farmer_field_Quick_chips_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fieldsList.forEach { field ->
                    val isSelected = field.id == uiState.fieldId || field.fieldName == uiState.fieldName
                    Surface(
                        color = if (isSelected) FarmerFirstTokens.HealthyBg else FarmerFirstTokens.SurfacePrimary,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) FarmerFirstTokens.HealthyGreen else FarmerFirstTokens.CardBorder
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectField(field.id) }
                            .testTag("welcome_field_chip_${field.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Eco,
                                contentDescription = null,
                                tint = if (isSelected) FarmerFirstTokens.HealthyGreen else FarmerFirstTokens.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${field.fieldName} (${field.cropType})",
                                color = if (isSelected) FarmerFirstTokens.TextPrimary else FarmerFirstTokens.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isFarmSelectorExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "المنطقة الزراعية: ${uiState.activeFarmer.region} • عمر المحصول: ${uiState.daysAfterSowing} يوم بعد الزراعة",
                        color = FarmerFirstTokens.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * SECTION B — FARM HEALTH OVERVIEW
 * Four simple, understandable status indicators with visual badges and plain-Arabic descriptions:
 * 1. Overall crop health
 * 2. Irrigation status
 * 3. Latest satellite analysis
 * 4. Important farm alerts
 */
@Composable
fun FarmHealthOverviewSection(
    uiState: AgroSightUiState,
    onOpenCropHealth: () -> Unit,
    onOpenIrrigation: () -> Unit,
    onOpenSatelliteAnalysis: () -> Unit,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val report = uiState.epidemiologyReport
    val ndvi = report.ndviMean
    val ndmi = report.ndmiMean
    val todayIrrigation = uiState.weeklySchedule.firstOrNull()
    val waterM3 = todayIrrigation?.grossM3PerFeddan ?: 24.5f
    val durationHours = todayIrrigation?.valveRunHours ?: 2.5f

    // 1. Overall Crop Health derivation
    val cropHealthStatus = when {
        ndvi >= 0.65f && report.psriMean < 0.08f -> FarmStatusLevel.HEALTHY
        ndvi >= 0.48f -> FarmStatusLevel.MODERATE
        else -> FarmStatusLevel.CRITICAL
    }
    val cropHealthHeadline = when (cropHealthStatus) {
        FarmStatusLevel.HEALTHY -> "نمو قوي ومتوازن (${((ndvi * 100f).roundToInt() + 18).coerceIn(75, 96)}%)"
        FarmStatusLevel.MODERATE -> "نمو متوسط مع بوادر إجهاد موضعي"
        else -> "إجهاد نباتي واضح يتطلب فحصاً ميدانياً"
    }
    val cropHealthDescription = "مرحلة ${uiState.growthEvaluation.stageNameAr} • النشاط الخضري جيد في معظم أجزاء ${uiState.fieldName}."

    // 2. Irrigation Status derivation
    val irrigationStatus = when {
        ndmi < 0.25f -> FarmStatusLevel.CRITICAL
        ndmi < 0.36f -> FarmStatusLevel.IRRIGATION
        else -> FarmStatusLevel.HEALTHY
    }
    val irrigationHeadline = "الاحتياج اليومي: ${String.format(Locale.US, "%.1f", waterM3)} م³/فدان (${String.format(Locale.US, "%.1f", durationHours)} ساعة)"
    val irrigationDescription = report.immediateIrrigationActionAr.ifBlank {
        "رطوبة التربة والغطاء النباتي متوازنة وفق معادلة البخر-نتح FAO-56."
    }

    // 3. Latest Satellite Analysis derivation
    val latestScan = uiState.dbSnapshot.satelliteSpectralScans.firstOrNull { it.fieldId == uiState.fieldId }
        ?: uiState.dbSnapshot.satelliteSpectralScans.firstOrNull()
    val satelliteStatus = if (report.confidenceScorePct >= 80) FarmStatusLevel.HEALTHY else FarmStatusLevel.MODERATE
    val satelliteHeadline = latestScan?.let {
        "Sentinel-2 • تاريخ المسح ${it.scanDate}"
    } ?: "Sentinel-2 L2A • دقة 10 أمتار"
    val satelliteDescription = "البؤرة المرصودة: ${report.hotspotLocationAr} (محبس رقم ${report.affectedValveNumber})"

    // 4. Important Farm Alerts derivation
    val primaryAlert = uiState.climaticAlerts.firstOrNull()
    val hasStressAlert = report.detectedRiskNameAr.isNotBlank() && !report.detectedRiskNameAr.contains("سليم")
    val alertStatus = when {
        hasStressAlert -> FarmStatusLevel.MODERATE
        primaryAlert != null -> FarmStatusLevel.MODERATE
        else -> FarmStatusLevel.HEALTHY
    }
    val alertHeadline = when {
        hasStressAlert -> report.detectedRiskNameAr
        primaryAlert != null -> primaryAlert.titleAr
        else -> "لا توجد تنبيهات حرجة حالياً"
    }
    val alertDescription = primaryAlert?.detailAr ?: report.targetedPreventiveActionAr

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("farm_health_overview_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ملخص صحة المزرعة اليوم",
                    color = FarmerFirstTokens.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "مؤشرات مبسطة وواضحة لحالة المحصول والري والتنبيهات",
                    color = FarmerFirstTokens.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Row 1: Overall Crop Health + Irrigation Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FarmHealthStatusCard(
                titleAr = "حالة المحصول العامة",
                headlineAr = cropHealthHeadline,
                descriptionAr = cropHealthDescription,
                statusLevel = cropHealthStatus,
                icon = Icons.Default.Eco,
                onClick = onOpenCropHealth,
                modifier = Modifier
                    .weight(1f)
                    .testTag("farmer_health_status_crop")
            )

            FarmHealthStatusCard(
                titleAr = "حالة الري اليوم",
                headlineAr = irrigationHeadline,
                descriptionAr = irrigationDescription,
                statusLevel = irrigationStatus,
                icon = Icons.Default.WaterDrop,
                onClick = onOpenIrrigation,
                modifier = Modifier
                    .weight(1f)
                    .testTag("farmer_health_status_irrigation")
            )
        }

        // Row 2: Latest Satellite Analysis + Important Farm Alerts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FarmHealthStatusCard(
                titleAr = "آخر تحليل قمر صناعي",
                headlineAr = satelliteHeadline,
                descriptionAr = satelliteDescription,
                statusLevel = satelliteStatus,
                icon = Icons.Default.Radar,
                onClick = onOpenSatelliteAnalysis,
                modifier = Modifier
                    .weight(1f)
                    .testTag("farmer_health_status_satellite")
            )

            FarmHealthStatusCard(
                titleAr = "تنبيهات المزرعة المهمة",
                headlineAr = alertHeadline,
                descriptionAr = alertDescription,
                statusLevel = alertStatus,
                icon = Icons.Default.Warning,
                onClick = onOpenAlerts,
                modifier = Modifier
                    .weight(1f)
                    .testTag("farmer_health_status_alerts")
            )
        }
    }
}

@Composable
private fun FarmHealthStatusCard(
    titleAr: String,
    headlineAr: String,
    descriptionAr: String,
    statusLevel: FarmStatusLevel,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(FarmerFirstTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = FarmerFirstTokens.SurfaceElevated),
        border = BorderStroke(1.dp, statusLevel.accentColor.copy(alpha = 0.45f)),
        modifier = modifier
            .heightIn(min = 148.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(statusLevel.containerColor)
                        .border(1.dp, statusLevel.accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = titleAr,
                        tint = statusLevel.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    color = statusLevel.containerColor,
                    shape = RoundedCornerShape(FarmerFirstTokens.PillRadius),
                    border = BorderStroke(1.dp, statusLevel.accentColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = statusLevel.badgeLabelAr,
                        color = statusLevel.accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = titleAr,
                color = FarmerFirstTokens.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = headlineAr,
                color = FarmerFirstTokens.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = descriptionAr,
                color = FarmerFirstTokens.TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * SECTION C — QUICK ACTIONS
 * Accessible cards for:
 * 1. Analyze my farm
 * 2. View satellite map
 * 3. Irrigation recommendations
 * 4. Crop health report
 * 5. Weather information (backed by real OpenMeteoClient FAO-56 data)
 */
@Composable
fun FarmerQuickActionsGrid(
    uiState: AgroSightUiState,
    onAnalyzeFarm: () -> Unit,
    onOpenSatelliteMap: () -> Unit,
    onOpenIrrigationRecommendations: () -> Unit,
    onOpenCropHealthReport: () -> Unit,
    onOpenWeatherInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todayForecast = uiState.forecastDays.firstOrNull()
    val tempMaxText = "${(todayForecast?.tempMaxC ?: 28f).roundToInt()}°C"
    val et0Text = String.format(Locale.US, "%.1f", todayForecast?.et0MmDay ?: 5.1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("farmer_quick_actions_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "الإجراءات السريعة للمزارع",
                color = FarmerFirstTokens.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "وصول مباشر بضغطة واحدة لأهم خدمات المزرعة اليومية",
                color = FarmerFirstTokens.TextSecondary,
                fontSize = 12.sp
            )
        }

        // Primary Full-Width CTA: 1. Analyze My Farm
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = FarmerFirstTokens.HealthyBg),
            border = BorderStroke(1.5.dp, FarmerFirstTokens.HealthyGreen),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onAnalyzeFarm)
                .testTag("quick_action_analyze_farm")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(FarmerFirstTokens.HealthyGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "افحص مزرعتي الآن",
                            tint = FarmerFirstTokens.SurfacePrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "افحص مزرعتي الآن (تحليل القمر الصناعي)",
                            color = FarmerFirstTokens.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "تحديث مؤشرات صحة النبات والرطوبة وكشف البؤر المجهدة في ${uiState.fieldName}",
                            color = FarmerFirstTokens.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = FarmerFirstTokens.HealthyGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Row 2: 2. View Satellite Map + 3. Irrigation Recommendations
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionTileCard(
                titleAr = "خريطة القمر الصناعي",
                subtitleAr = "عرض خريطة الحقل وتوزيع النمو والرطوبة",
                icon = Icons.Default.Map,
                accentColor = FarmerFirstTokens.HealthyGreen,
                onClick = onOpenSatelliteMap,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_action_satellite_map")
            )

            QuickActionTileCard(
                titleAr = "توصيات وجدول الري",
                subtitleAr = "كميات المياه بالساعة والمتر المكعب لكل محبس",
                icon = Icons.Default.WaterDrop,
                accentColor = FarmerFirstTokens.IrrigationBlue,
                onClick = onOpenIrrigationRecommendations,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_action_irrigation_recommendations")
            )
        }

        // Row 3: 4. Crop Health Report + 5. Weather Information (Real Open-Meteo data)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionTileCard(
                titleAr = "تقرير صحة المحصول",
                subtitleAr = "تقرير شامل قابل للطباعة والمشاركة للمزرعة",
                icon = Icons.Default.Print,
                accentColor = FarmerFirstTokens.ModerateAmber,
                onClick = onOpenCropHealthReport,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_action_crop_health_report")
            )

            QuickActionTileCard(
                titleAr = "حالة الطقس والأرصاد",
                subtitleAr = "الحرارة $tempMaxText • البخر $et0Text مم/يوم (Open-Meteo)",
                icon = Icons.Default.Cloud,
                accentColor = FarmerFirstTokens.IrrigationBlue,
                onClick = onOpenWeatherInfo,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_action_weather_info")
            )
        }
    }
}

@Composable
private fun QuickActionTileCard(
    titleAr: String,
    subtitleAr: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = FarmerFirstTokens.SurfaceElevated),
        border = BorderStroke(1.dp, FarmerFirstTokens.CardBorder),
        modifier = modifier
            .heightIn(min = 104.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = titleAr,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = FarmerFirstTokens.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = titleAr,
                color = FarmerFirstTokens.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitleAr,
                color = FarmerFirstTokens.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * SECTION D — SATELLITE INSIGHTS (SIMPLIFIED BY DEFAULT + EXPANDABLE TECHNICAL VIEW)
 * Summarizes NDVI, NDRE, NDMI, and PSRI in plain farmer-friendly Arabic on the main dashboard,
 * while allowing technical metrics and raw index values to be inspected on demand.
 */
@Composable
fun SimplifiedSatelliteInsightsCard(
    uiState: AgroSightUiState,
    onOpenTechnicalRadar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTechnicalMetrics by rememberSaveable { mutableStateOf(false) }
    val report = uiState.epidemiologyReport

    val ndviStatus = when {
        report.ndviMean >= 0.65f -> FarmStatusLevel.HEALTHY
        report.ndviMean >= 0.48f -> FarmStatusLevel.MODERATE
        else -> FarmStatusLevel.CRITICAL
    }
    val ndviFarmerSummary = when (ndviStatus) {
        FarmStatusLevel.HEALTHY -> "الغطاء النباتي كثيف ونشط في أغلب الحقل"
        FarmStatusLevel.MODERATE -> "كثافة النباتات متوسطة وتحتاج متابعة في الأطراف"
        else -> "ضعف في كثافة النمو الخضري يتطلب معاينة ميدانية"
    }

    val ndreStatus = when {
        report.ndreMean >= 0.40f -> FarmStatusLevel.HEALTHY
        report.ndreMean >= 0.28f -> FarmStatusLevel.MODERATE
        else -> FarmStatusLevel.CRITICAL
    }
    val ndreFarmerSummary = when (ndreStatus) {
        FarmStatusLevel.HEALTHY -> "مستوى الكلوروفيل والتغذية النيتروجينية ممتاز"
        FarmStatusLevel.MODERATE -> "بداية نقص خفيف في التغذية أو الكلوروفيل"
        else -> "إجهاد في حيوية الأوراق يتطلب مراجعة برنامج التسميد"
    }

    val ndmiStatus = when {
        report.ndmiMean >= 0.34f -> FarmStatusLevel.HEALTHY
        report.ndmiMean >= 0.22f -> FarmStatusLevel.IRRIGATION
        else -> FarmStatusLevel.CRITICAL
    }
    val ndmiFarmerSummary = when (ndmiStatus) {
        FarmStatusLevel.HEALTHY -> "رطوبة الأوراق ممتازة ولا يوجد عطش"
        FarmStatusLevel.IRRIGATION -> "بداية حاجة للري في القطاع المتأثر"
        else -> "إجهاد مائي واضح في النباتات يستلزم ضبط الري"
    }

    val psriStatus = when {
        report.psriMean <= 0.06f -> FarmStatusLevel.HEALTHY
        report.psriMean <= 0.11f -> FarmStatusLevel.MODERATE
        else -> FarmStatusLevel.CRITICAL
    }
    val psriFarmerSummary = when (psriStatus) {
        FarmStatusLevel.HEALTHY -> "الأوراق خضراء شابة ولا توجد علامات اصفرار مبكر"
        FarmStatusLevel.MODERATE -> "اصفرار أو شيخوخة محدودة في بعض الأوراق"
        else -> "ارتفاع في إجهاد الأنسجة النباتية أو الاصفرار"
    }

    Card(
        shape = RoundedCornerShape(FarmerFirstTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = FarmerFirstTokens.SurfaceElevated),
        border = BorderStroke(1.dp, FarmerFirstTokens.CardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("farmer_satellite_insights_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "رؤى القمر الصناعي المبسطة",
                        color = FarmerFirstTokens.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "تفسير زراعي واضح لمؤشرات النبات والرطوبة بدون تعقيد",
                        color = FarmerFirstTokens.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    color = FarmerFirstTokens.SurfacePrimary,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, FarmerFirstTokens.HealthyGreen.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { showTechnicalMetrics = !showTechnicalMetrics }
                        .testTag("satellite_insights_technical_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = FarmerFirstTokens.HealthyGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (showTechnicalMetrics) "إخفاء الأرقام الفنية" else "عرض التفاصيل الفنية",
                            color = FarmerFirstTokens.HealthyGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4 Simplified Index Summary Rows
            SimplifiedSpectralIndexRow(
                farmerTitleAr = "قوة وكثافة النمو الخضري",
                indexCode = "NDVI",
                farmerExplanationAr = ndviFarmerSummary,
                technicalValueText = String.format(Locale.US, "%.2f", report.ndviMean),
                technicalNoteAr = "نطاق النمو الصحي: 0.65 إلى 0.90 (Red B04 / NIR B08)",
                statusLevel = ndviStatus,
                showTechnicalMetrics = showTechnicalMetrics,
                testTag = "satellite_insight_ndvi"
            )

            SimplifiedSpectralIndexRow(
                farmerTitleAr = "حيوية الأوراق والتغذية (الكلوروفيل)",
                indexCode = "NDRE",
                farmerExplanationAr = ndreFarmerSummary,
                technicalValueText = String.format(Locale.US, "%.2f", report.ndreMean),
                technicalNoteAr = "الحافة الحمراء المبكرة: 0.40+ ممتاز (Red-Edge B05 / NIR B08)",
                statusLevel = ndreStatus,
                showTechnicalMetrics = showTechnicalMetrics,
                testTag = "satellite_insight_ndre"
            )

            SimplifiedSpectralIndexRow(
                farmerTitleAr = "توازن المياه ورطوبة النبات",
                indexCode = "NDMI",
                farmerExplanationAr = ndmiFarmerSummary,
                technicalValueText = String.format(Locale.US, "%.2f", report.ndmiMean),
                technicalNoteAr = "مؤشر الرطوبة: 0.34+ رطوبة كافية (NIR B08 / SWIR B11)",
                statusLevel = ndmiStatus,
                showTechnicalMetrics = showTechnicalMetrics,
                testTag = "satellite_insight_ndmi"
            )

            SimplifiedSpectralIndexRow(
                farmerTitleAr = "نضج الأوراق أو الإجهاد المبكر",
                indexCode = "PSRI",
                farmerExplanationAr = psriFarmerSummary,
                technicalValueText = String.format(Locale.US, "%.3f", report.psriMean),
                technicalNoteAr = "مؤشر الشيخوخة: أقل من 0.06 مثالي ((B04 - B02) / B06)",
                statusLevel = psriStatus,
                showTechnicalMetrics = showTechnicalMetrics,
                testTag = "satellite_insight_psri"
            )

            // Action Button to open the full technical Satellite Disease Radar & GeoTIFF view
            Button(
                onClick = onOpenTechnicalRadar,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FarmerFirstTokens.SurfacePrimary,
                    contentColor = FarmerFirstTokens.HealthyGreen
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FarmerFirstTokens.MinTouchTarget)
                    .border(1.dp, FarmerFirstTokens.CardBorderStrong, RoundedCornerShape(14.dp))
                    .testTag("satellite_insights_open_technical_screen_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "فتح المختبر الطيفي المتقدم وملفات GeoTIFF",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun SimplifiedSpectralIndexRow(
    farmerTitleAr: String,
    indexCode: String,
    farmerExplanationAr: String,
    technicalValueText: String,
    technicalNoteAr: String,
    statusLevel: FarmStatusLevel,
    showTechnicalMetrics: Boolean,
    testTag: String
) {
    Surface(
        color = FarmerFirstTokens.SurfacePrimary,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, FarmerFirstTokens.CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    Surface(
                        color = statusLevel.containerColor,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, statusLevel.accentColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = indexCode,
                            color = statusLevel.accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = farmerTitleAr,
                        color = FarmerFirstTokens.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = statusLevel.containerColor,
                    shape = RoundedCornerShape(FarmerFirstTokens.PillRadius)
                ) {
                    Text(
                        text = statusLevel.badgeLabelAr,
                        color = statusLevel.accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = farmerExplanationAr,
                color = FarmerFirstTokens.TextSecondary,
                fontSize = 12.sp
            )

            AnimatedVisibility(visible = showTechnicalMetrics) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FarmerFirstTokens.SurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "القيمة الرقمية ($indexCode): $technicalValueText",
                        color = statusLevel.accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = technicalNoteAr,
                        color = FarmerFirstTokens.TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * SECTION E — FARM ACTIVITY
 * Displays recent analyses, irrigation recommendations, alerts, and reports using actual stored application data.
 */
@Composable
fun FarmActivityTimelineSection(
    uiState: AgroSightUiState,
    onNavigateToScreen: (AppScreen) -> Unit,
    onOpenPrintReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestScan = uiState.dbSnapshot.satelliteSpectralScans.firstOrNull { it.fieldId == uiState.fieldId }
        ?: uiState.dbSnapshot.satelliteSpectralScans.firstOrNull()
    val latestTreatment = uiState.dbSnapshot.irrigationAndTreatmentLogs.firstOrNull { it.fieldId == uiState.fieldId }
        ?: uiState.dbSnapshot.irrigationAndTreatmentLogs.firstOrNull()
    val todayIrrigation = uiState.weeklySchedule.firstOrNull()
    val latestAlert = uiState.climaticAlerts.firstOrNull()
    val latestAudit = uiState.roomSqlAudits.firstOrNull()

    Card(
        shape = RoundedCornerShape(FarmerFirstTokens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = FarmerFirstTokens.SurfaceElevated),
        border = BorderStroke(1.dp, FarmerFirstTokens.CardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("farmer_activity_timeline_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = FarmerFirstTokens.HealthyGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "سجل نشاط المزرعة الفعلي",
                            color = FarmerFirstTokens.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "آخر التحليلات، توصيات الري، التنبيهات، والتقارير المسجلة",
                            color = FarmerFirstTokens.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // 1. Recent Satellite Analysis Record
            FarmActivityEntryRow(
                categoryAr = "تحليل قمر صناعي حديث",
                titleAr = latestScan?.let {
                    "فحص Sentinel-2 (${it.scanDate.take(10)}) • ${it.detectedRiskName}"
                } ?: "فحص Sentinel-2 L2A • ${uiState.epidemiologyReport.detectedRiskNameAr}",
                subtitleAr = "الحقل: ${uiState.fieldName} • موقع الرصد: ${uiState.epidemiologyReport.hotspotLocationAr}",
                badgeAr = "مُوثّق",
                accentColor = FarmerFirstTokens.HealthyGreen,
                icon = Icons.Default.Radar,
                onClick = { onNavigateToScreen(AppScreen.DISEASE_RADAR) },
                testTag = "activity_item_recent_analysis"
            )

            HorizontalDivider(color = FarmerFirstTokens.CardBorder.copy(alpha = 0.6f))

            // 2. Irrigation Recommendation & Treatment Log Record
            FarmActivityEntryRow(
                categoryAr = "توصية الري والتسميد",
                titleAr = latestTreatment?.let {
                    "ري ${String.format(Locale.US, "%.1f", it.waterM3PerFeddan)} م³/فدان (${it.valveRunMinutes} دقيقة)"
                } ?: "توصية ري اليوم: ${String.format(Locale.US, "%.1f", todayIrrigation?.grossM3PerFeddan ?: 25.0f)} م³/فدان",
                subtitleAr = latestTreatment?.preventiveSprayAction
                    ?: uiState.epidemiologyReport.immediateIrrigationActionAr,
                badgeAr = "FAO-56",
                accentColor = FarmerFirstTokens.IrrigationBlue,
                icon = Icons.Default.WaterDrop,
                onClick = { onNavigateToScreen(AppScreen.IRRIGATION_SCHEDULE) },
                testTag = "activity_item_irrigation_recommendation"
            )

            HorizontalDivider(color = FarmerFirstTokens.CardBorder.copy(alpha = 0.6f))

            // 3. Weather / Farm Alert Record
            FarmActivityEntryRow(
                categoryAr = "تنبيه جوي وزراعي",
                titleAr = latestAlert?.titleAr ?: "حالة الأرصاد مستقرة • لا توجد موجات حر أو صقيع",
                subtitleAr = latestAlert?.detailAr ?: uiState.epidemiologyReport.targetedPreventiveActionAr,
                badgeAr = if (latestAlert != null) "تنبيه نشط" else "مستقر",
                accentColor = if (latestAlert != null) FarmerFirstTokens.ModerateAmber else FarmerFirstTokens.HealthyGreen,
                icon = Icons.Default.Warning,
                onClick = { onNavigateToScreen(AppScreen.METEO_ANALYTICS) },
                testTag = "activity_item_farm_alert"
            )

            HorizontalDivider(color = FarmerFirstTokens.CardBorder.copy(alpha = 0.6f))

            // 4. Saved Report / Audit Record
            FarmActivityEntryRow(
                categoryAr = "تقرير فحص محفوظ",
                titleAr = latestAudit?.let {
                    "تقرير ${it.fieldName} (${it.cropType}) • ${it.detectedRiskName}"
                } ?: "تقرير حالة ${uiState.fieldName} (${uiState.selectedCrop.nameAr})",
                subtitleAr = "جاهز للعرض أو الطباعة • المساحة ${uiState.areaFeddans.roundToInt()} فدان",
                badgeAr = "تقرير",
                accentColor = FarmerFirstTokens.ModerateAmber,
                icon = Icons.Default.Assessment,
                onClick = onOpenPrintReport,
                testTag = "activity_item_saved_report"
            )
        }
    }
}

@Composable
private fun FarmActivityEntryRow(
    categoryAr: String,
    titleAr: String,
    subtitleAr: String,
    badgeAr: String,
    accentColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f))
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = categoryAr,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = categoryAr,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = titleAr,
                    color = FarmerFirstTokens.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleAr,
                    color = FarmerFirstTokens.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            color = accentColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(FarmerFirstTokens.PillRadius),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
        ) {
            Text(
                text = badgeAr,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * PHASE B — PERSISTENT FLOATING AI AGRICULTURAL ASSISTANT BUTTON ("اسأل AgroSight")
 * Accessible across the main application screens without overlapping bottom navigation,
 * map controls, or primary buttons. Uses the exact original AgroSight color palette.
 */
@Composable
fun AgroSightFloatingAssistantButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = EmeraldPrimary,
        contentColor = ForestCardDark,
        shape = RoundedCornerShape(50),
        shadowElevation = 6.dp,
        border = BorderStroke(1.5.dp, EmeraldSoft),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .testTag("floating_ask_agrosight_button")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(EmeraldDark)
                    .border(1.dp, EmeraldSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Eco,
                    contentDescription = "اسأل AgroSight",
                    tint = EmeraldSoft,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "اسأل AgroSight",
                color = ForestCardDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

