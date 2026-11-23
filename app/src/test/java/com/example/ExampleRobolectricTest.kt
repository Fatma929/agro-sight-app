package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AgroAiCopilotDataSource
import com.example.data.AgroSightCloudWorker
import com.example.data.AgroSightDependencies
import com.example.data.CloudChatLog
import com.example.data.CloudWorkspaceRepository
import com.example.data.DatabaseService
import com.example.data.EpidemiologyDiagnosisReport
import com.example.data.FarmerCloudWorkspace
import com.example.data.GeminiAgronomyChatMessage
import com.example.data.GeminiGroundedResponse
import com.example.data.MeteoForecastDataSource
import com.example.data.OpenMeteoClient
import com.example.data.OpenMeteoResult
import com.example.data.OpenSourceSatelliteClient
import com.example.data.RealSatelliteAndNasaTelemetry
import com.example.data.SatelliteTelemetryDataSource
import com.example.data.SqlFieldAuditDao
import com.example.data.SqlFieldAuditEntity
import com.example.data.StacAcquisitionStatus
import com.example.model.AccountStatus
import com.example.model.AppScreen
import com.example.model.IrrigationDayStatus
import com.example.model.UserRole
import com.example.viewmodel.AgroSightViewModel
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = androidx.compose.ui.test.junit4.createComposeRule()

    private class FakeMeteoDataSource : MeteoForecastDataSource {
        override suspend fun fetch7DayAgroForecast(
            latitude: Double,
            longitude: Double,
            baseKc: Float
        ): OpenMeteoResult {
            return OpenMeteoClient().buildFallbackForecast(
                latitude = latitude,
                longitude = longitude,
                baseKc = baseKc,
                isLive = true
            )
        }
    }

    private class FakeSatelliteDataSource : SatelliteTelemetryDataSource {
        override suspend fun fetchLiveOpenSourceSatelliteAndNasaData(
            latitude: Double,
            longitude: Double
        ): RealSatelliteAndNasaTelemetry {
            return RealSatelliteAndNasaTelemetry(
                sceneId = "S2B_TEST_SCENE",
                acquisitionDateIso = "2026-05-01",
                constellationAr = "Sentinel-2 Test",
                mgrsTileId = "35RQQ",
                cloudCoverPct = 0.5f,
                platformInstrument = "MSI L2A",
                stacCatalogSource = "Element84 Test",
                opticalTileUrl = "https://example.com/tile.png",
                retrievedSatelliteBitmap = null,
                nasaSolarRadiationMjM2Day = 24.5f,
                nasaClearSkyInsolationMjM2Day = 26.2f,
                nasaRootZoneSoilMoisturePct = 44.0f,
                nasaTemp2mMaxC = 32.0f,
                nasaTemp2mMinC = 19.0f,
                nasaRelativeHumidityPct = 52.0f,
                nasaWindSpeed2mMs = 3.5f,
                nasaVaporPressureDeficitKpa = 1.8f,
                channels = emptyList()
            )
        }
    }

    private class FakeAiCopilotDataSource(
        private val throwCancellation: Boolean = false
    ) : AgroAiCopilotDataSource {
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
            responseMode: com.example.data.AssistantResponseMode
        ): GeminiGroundedResponse {
            if (throwCancellation) throw CancellationException("Test coroutine cancellation")
            return GeminiGroundedResponse(
                replyText = "إجابة استرشادية للسؤال: $userMessage",
                citations = emptyList(),
                isLiveAiGenerated = true
            )
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
        ): String {
            if (throwCancellation) throw CancellationException("Test coroutine cancellation")
            return "توصية استرشادية اختبارية لمحصول $cropNameAr"
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
        ): String {
            if (throwCancellation) throw CancellationException("Test coroutine cancellation")
            return "رد واتساب استرشادي: $farmerQuestion"
        }

        override suspend fun generateCustomWorkerMessage(
            prompt: String,
            fallbackText: String
        ): String {
            if (throwCancellation) throw CancellationException("Test coroutine cancellation")
            return fallbackText
        }
    }

    private class FakeCloudWorkspaceRepository(
        private val authenticatedUid: String? = null
    ) : CloudWorkspaceRepository {
        override val currentUser: FirebaseUser? = null

        override fun observeAuthState(): Flow<FirebaseUser?> = flowOf(null)

        override suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> =
            Result.failure(IllegalStateException("Fake Auth"))

        override fun signOut() {}

        override fun observeWorkspace(userId: String): Flow<FarmerCloudWorkspace?> = flowOf(null)

        override fun observeCloudChatLogs(userId: String): Flow<List<CloudChatLog>> = flowOf(emptyList())

        override suspend fun upsertFarmerWorkspace(
            userId: String,
            farmerName: String,
            whatsappPhone: String,
            gmailAddress: String,
            nationalId: String,
            fieldName: String,
            region: String,
            cropType: String,
            areaFeddans: Float,
            latitude: Double,
            longitude: Double,
            soilPh: Float,
            soilEce: Float,
            waterEcw: Float,
            ndviMean: Float,
            ndreMean: Float,
            psriMean: Float,
            ndmiMean: Float,
            detectedRiskName: String
        ): Result<Unit> = Result.success(Unit)

        override suspend fun adminUpdateCloudUserRoleAndStatus(
            targetUserId: String,
            newRole: String,
            newStatus: String
        ): Result<Unit> = Result.success(Unit)

        override suspend fun appendCloudChatLog(
            userId: String,
            role: String,
            channel: String,
            messageText: String,
            groundingSources: String
        ): Result<Unit> = Result.success(Unit)

        override suspend fun deleteCloudWorkspace(userId: String): Result<Unit> {
            if (userId.isBlank()) {
                return Result.failure(IllegalArgumentException("Blank userId"))
            }
            val uid = authenticatedUid ?: return Result.failure(IllegalStateException("Not signed in"))
            if (uid != userId) {
                return Result.failure(
                    SecurityException("Unauthorized workspace deletion: authenticated UID does not match requested userId")
                )
            }
            return Result.success(Unit)
        }
    }

    private class FakeSqlFieldAuditDao : SqlFieldAuditDao {
        override fun observeAllAudits(): Flow<List<SqlFieldAuditEntity>> = flowOf(emptyList())
        override suspend fun getAuditCount(): Int = 0
        override suspend fun insertAudit(entity: SqlFieldAuditEntity) {}
        override suspend fun deleteAuditById(id: String) {}
    }

    private fun buildTestViewModel(
        app: Application,
        throwCancellation: Boolean = false,
        authenticatedUid: String? = null
    ): AgroSightViewModel {
        app.getSharedPreferences("agrosight_multi_tenant_db", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val dbService = DatabaseService(app)
        val fakeGemini = FakeAiCopilotDataSource(throwCancellation = throwCancellation)
        val deps = AgroSightDependencies(
            databaseService = dbService,
            openMeteoClient = FakeMeteoDataSource(),
            openSourceSatelliteClient = FakeSatelliteDataSource(),
            satelliteRasterBackendRepository = com.example.data.RetrofitSatelliteRasterBackendRepository(
                baseUrl = "http://127.0.0.1:1/",
                stacClient = OpenSourceSatelliteClient()
            ),
            geminiAgroService = fakeGemini,
            cloudWorker = AgroSightCloudWorker(dbService, fakeGemini),
            firestoreRepository = FakeCloudWorkspaceRepository(authenticatedUid = authenticatedUid),
            sqlAuditDao = FakeSqlFieldAuditDao()
        )
        return AgroSightViewModel(app, deps)
    }

    @Test
    fun `read app name and parse OpenMeteo JSON with Robolectric`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AgroSight", appName)

        val sampleJson = """
            {
              "daily": {
                "time": ["2026-05-01", "2026-05-02", "2026-05-03"],
                "et0_fao_evapotranspiration": [5.4, 5.8, 6.1],
                "temperature_2m_max": [31.5, 33.0, 35.2],
                "temperature_2m_min": [18.0, 19.2, 20.1],
                "wind_speed_10m_max": [14.0, 18.5, 27.0],
                "precipitation_sum": [0.0, 0.0, 0.2],
                "shortwave_radiation_sum": [24.1, 25.0, 25.8]
              }
            }
        """.trimIndent()
        val parsed = OpenMeteoClient().parseOpenMeteoJson(sampleJson, 1.15f)
        assertTrue(parsed.isLiveConnected)
        assertEquals(3, parsed.records.size)
        assertEquals(5.4f, parsed.records[0].et0MmDay, 0.01f)
    }

    @Test
    fun `parseStacFeatureCollectionJson distinguishes valid scene with band assets vs empty vs missing assets`() {
        val satClient = OpenSourceSatelliteClient()

        // 1. Valid scene with required spectral band assets (B02, B04, B05, B06, B08, B8A, B11, SCL)
        val validStacJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "id": "S2B_35RQQ_20260501_0_L2A",
                  "properties": {
                    "datetime": "2026-05-01T08:42:11Z",
                    "eo:cloud_cover": 2.4,
                    "platform": "sentinel-2b",
                    "mgrs:utm_zone": 35,
                    "mgrs:latitude_band": "R",
                    "mgrs:grid_square": "QQ"
                  },
                  "assets": {
                    "blue": { "href": "https://sentinel-cogs.s3.us-west-2.amazonaws.com/B02.tif" },
                    "red": { "href": "https://sentinel-cogs.s3.us-west-2.amazonaws.com/B04.tif" },
                    "rededge1": { "href": "https://sentinel-cogs.s3.us-west-2.amazonaws.com/B05.tif" },
                    "rededge2": { "href": "https://sentinel-cogs.s3.us-west-2.amazonaws.com/B06.tif" },
                    "nir": { "href": "https://sentinel-cogs.s3.us-west-2.amazonaws.com/B08.tif" },
                    "nir08": { "href": "https://sentinel-cogs.s3.us-west-2.amazonaws.com/B8A.tif" },
                    "swir16": { "href": "https://sentinel-cogs.s3.us-west-2.amazonaws.com/B11.tif" },
                    "scl": { "href": "https://sentinel-cogs.s3.us-west-2.amazonaws.com/SCL.tif" }
                  }
                }
              ]
            }
        """.trimIndent()
        val validParsed = satClient.parseStacFeatureCollectionJson(
            body = validStacJson,
            httpCode = 200,
            elapsedMs = 115L,
            defaultMgrs = "35RQQ",
            demoSceneId = "DEMO_SCENE"
        )
        assertEquals(StacAcquisitionStatus.VALID_SCENE_WITH_BAND_ASSETS, validParsed.status)
        assertEquals("S2B_35RQQ_20260501_0_L2A", validParsed.sceneId)
        assertTrue(validParsed.bandAssets.hasAllRequiredSpectralBands)
        assertTrue(validParsed.bandAssets.b08Nir10mHref.isNotBlank())

        // 2. Empty FeatureCollection (HTTP 200 but no matching scenes)
        val emptyStacJson = """{"type": "FeatureCollection", "features": []}"""
        val emptyParsed = satClient.parseStacFeatureCollectionJson(
            body = emptyStacJson,
            httpCode = 200,
            elapsedMs = 90L,
            defaultMgrs = "35RQQ",
            demoSceneId = "DEMO_SCENE"
        )
        assertEquals(StacAcquisitionStatus.NO_MATCHING_SCENES, emptyParsed.status)

        // 3. Scene present but missing spectral band assets
        val missingAssetsJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "id": "S2A_INCOMPLETE_ASSETS",
                  "properties": { "datetime": "2026-05-01T08:42:11Z", "eo:cloud_cover": 1.0 },
                  "assets": {
                    "visual": { "href": "https://example.com/TCI.tif" }
                  }
                }
              ]
            }
        """.trimIndent()
        val missingParsed = satClient.parseStacFeatureCollectionJson(
            body = missingAssetsJson,
            httpCode = 200,
            elapsedMs = 88L,
            defaultMgrs = "35RQQ",
            demoSceneId = "DEMO_SCENE"
        )
        assertEquals(StacAcquisitionStatus.MISSING_OR_INACCESSIBLE_ASSETS, missingParsed.status)
        assertFalse(missingParsed.bandAssets.hasAllRequiredSpectralBands)
    }

    @Test
    fun `AgroSightViewModel initializes with 7-day irrigation schedule and enforces RBAC on admin actions`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Verify standard ViewModelProvider.AndroidViewModelFactory reflection instantiation succeeds
        val reflectedVm = androidx.lifecycle.ViewModelProvider(
            androidx.lifecycle.ViewModelStore(),
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[AgroSightViewModel::class.java]
        assertNotNull("AndroidViewModelFactory must instantiate AgroSightViewModel via reflection", reflectedVm)

        val viewModel = buildTestViewModel(app)

        val initialState = viewModel.uiState.value
        assertEquals(AppScreen.HOME, initialState.currentScreen)
        assertEquals(7, initialState.weeklySchedule.size)
        assertEquals(7, initialState.forecastDays.size)
        assertEquals(UserRole.SUPER_ADMIN, initialState.activeFarmer.role)

        // Super Admin can access Admin Portal
        viewModel.selectScreen(AppScreen.ADMIN_PORTAL)
        assertEquals(AppScreen.ADMIN_PORTAL, viewModel.uiState.value.currentScreen)

        // Switch to a regular FARMER account ("farmer_1")
        viewModel.switchFarmerAccount("farmer_1")
        assertEquals(UserRole.FARMER, viewModel.uiState.value.activeFarmer.role)
        // Regular farmer is automatically redirected away from Admin Portal to HOME
        assertEquals(AppScreen.HOME, viewModel.uiState.value.currentScreen)

        // Attempt by regular FARMER to navigate to Admin Portal is rejected
        viewModel.selectScreen(AppScreen.ADMIN_PORTAL)
        assertEquals(AppScreen.HOME, viewModel.uiState.value.currentScreen)

        // Attempt by regular FARMER to promote themselves to SUPER_ADMIN is blocked
        viewModel.adminChangeUserRole("farmer_1", UserRole.SUPER_ADMIN)
        assertEquals(UserRole.FARMER, viewModel.uiState.value.activeFarmer.role)
        assertTrue(viewModel.uiState.value.lastWhatsAppBannerNotificationAr?.contains("مرفوضة") == true)

        // Attempt by regular FARMER to change status is blocked
        viewModel.adminChangeUserStatus("farmer_2", AccountStatus.SUSPENDED)
        val farmer2 = viewModel.uiState.value.dbSnapshot.farmers.first { it.id == "farmer_2" }
        assertEquals(AccountStatus.ACTIVE, farmer2.accountStatus)
    }

    @Test
    fun `AgroSightViewModel updates 7-day timetable status and What-If simulator parameters`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = buildTestViewModel(app)

        viewModel.updateDayIrrigationStatus(1, IrrigationDayStatus.POSTPONED_WIND)
        assertEquals(
            IrrigationDayStatus.POSTPONED_WIND,
            viewModel.uiState.value.weeklySchedule[1].status
        )

        viewModel.applyWhatIfPreset(efficiencyPct = 95, targetPh = 6.5f, tempDeltaC = 4.0f)
        val whatIf = viewModel.uiState.value.whatIfInput
        assertEquals(95, whatIf.simEfficiencyPercent)
        assertEquals(6.5f, whatIf.simSoilPh, 0.01f)
        assertEquals(4.0f, whatIf.simTempDeltaC, 0.01f)
    }

    @Test
    fun `AgroSightViewModel resets loading flags cleanly when coroutine is cancelled`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = buildTestViewModel(app, throwCancellation = true)

        viewModel.runGeminiEpidemiologyAnalysis()
        assertFalse(
            "isGeneratingGeminiDiagnosis must not remain stuck after cancellation",
            viewModel.uiState.value.isGeneratingGeminiDiagnosis
        )
    }

    private class FakeSatelliteRasterBackendRepository : com.example.data.SatelliteRasterBackendRepository {
        override suspend fun checkBackendHealth(): Result<Boolean> = Result.success(true)

        override suspend fun runFullGeoreferencedSatelliteAnalysis(
            farmId: String,
            ownerUid: String,
            cropId: String,
            growthStageIndex: Int,
            centerLat: Double,
            centerLon: Double,
            areaFeddans: Float,
            customPolygonGeoJson: String?,
            explicitAuthToken: String?
        ): Result<com.example.data.GeoreferencedRasterBackendResult> {
            val cells = (0 until 96).map { idx ->
                val r = idx / 12
                val c = idx % 12
                com.example.data.SpectralPixelCell(
                    row = r,
                    col = c,
                    valveNumber = (((r / 4) * 3) + (c / 4) + 1).coerceIn(1, 6),
                    ndvi = 0.72f,
                    ndre = 0.39f,
                    psri = 0.08f,
                    ndmi = 0.36f,
                    isHotspot = (r == 2 && c == 8),
                    isCloudMasked = false
                )
            }
            return Result.success(
                com.example.data.GeoreferencedRasterBackendResult(
                    resultId = "res_test_001",
                    jobId = "job_test_001",
                    farmId = farmId,
                    ownerUid = ownerUid,
                    sceneId = "S2B_35RQQ_20260501_0_L2A",
                    cropId = cropId,
                    executionState = com.example.data.RasterPipelineExecutionState.COMPLETED_LIVE_GEOREFERENCED,
                    isLiveGeoreferencedRaster = true,
                    crsEpsg = 32636,
                    spatialResolutionMeters = 10.0f,
                    totalPixels = 96,
                    polygonPixels = 96,
                    validPixels = 94,
                    maskedCloudPixels = 2,
                    validPixelRatio = 94f / 96f,
                    ndviMean = 0.72f,
                    ndviStdDev = 0.04f,
                    ndreMean = 0.39f,
                    ndmiMean = 0.36f,
                    psriMean = 0.08f,
                    anomalyPixelCount = 3,
                    anomalyAreaFeddans = 0.07f,
                    anomalyZonesGeoJson = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Polygon","coordinates":[[[30.34,30.38],[30.35,30.38],[30.35,30.39],[30.34,30.39],[30.34,30.38]]]},"properties":{"cluster_id":1}}]}""",
                    pixelGrid = cells,
                    vegetationStressDetectionAr = "رصد شذوذ طيفي موضعي في 3 بكسلات مرجعية داخل حدود المزرعة",
                    agriculturalRiskAssessmentAr = "احتمال إجهاد فسيولوجي مبكر يتطلب فحص المحبس رقم 3",
                    confirmedDiseaseDiagnosisStatusAr = "غير مؤكد مختبرياً — الشذوذ الطيفي من القمر الصناعي لا يغني عن المعاينة الحقلية",
                    statusSummaryAr = "تم تحليل 94 بكسل مرجعي (EPSG:32636 • دقة 10.0م)"
                )
            )
        }
    }

    @Test
    fun `AgroSightViewModel runGeoreferencedRasterAnalysis and RetrofitSatelliteRasterBackendRepository update 3-tier scientific state`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dbService = DatabaseService(app)
        val fakeGemini = FakeAiCopilotDataSource()
        val viewModel = AgroSightViewModel(
            app,
            AgroSightDependencies(
                databaseService = dbService,
                openMeteoClient = FakeMeteoDataSource(),
                openSourceSatelliteClient = FakeSatelliteDataSource(),
                satelliteRasterBackendRepository = FakeSatelliteRasterBackendRepository(),
                geminiAgroService = fakeGemini,
                cloudWorker = AgroSightCloudWorker(dbService, fakeGemini),
                firestoreRepository = FakeCloudWorkspaceRepository(authenticatedUid = "farmer_admin_owner"),
                sqlAuditDao = FakeSqlFieldAuditDao()
            )
        )

        viewModel.runGeoreferencedRasterAnalysis()
        val rasterRes = viewModel.uiState.value.rasterBackendResult
        assertNotNull("Georeferenced raster backend result must be populated", rasterRes)
        assertTrue(rasterRes!!.isLiveGeoreferencedRaster)
        assertEquals(32636, rasterRes.crsEpsg)
        assertEquals(10.0f, rasterRes.spatialResolutionMeters, 0.01f)
        assertEquals(96, rasterRes.pixelGrid.size)
        assertTrue(rasterRes.anomalyZonesGeoJson.contains("\"FeatureCollection\""))
        assertTrue(viewModel.uiState.value.epidemiologyReport.dataModeDesignation.contains("LIVE_GEOREFERENCED_RASTER"))
        assertTrue(viewModel.uiState.value.epidemiologyReport.confirmedDiseaseDiagnosisStatusAr.contains("غير مؤكد"))
    }

    @Test
    fun `deleteCloudWorkspace enforces UID ownership verification and blocks cross-user deletion`() {
        val repo = FakeCloudWorkspaceRepository(authenticatedUid = "farmer_alice_123")

        // 1. Blank userId is rejected
        val blankRes = kotlinx.coroutines.runBlocking { repo.deleteCloudWorkspace("") }
        assertTrue(blankRes.isFailure)
        assertTrue(blankRes.exceptionOrNull() is IllegalArgumentException)

        // 2. Unauthorized cross-user deletion (Alice trying to delete Bob's workspace) is rejected with SecurityException
        val crossUserRes = kotlinx.coroutines.runBlocking { repo.deleteCloudWorkspace("farmer_bob_456") }
        assertTrue(crossUserRes.isFailure)
        assertTrue(crossUserRes.exceptionOrNull() is SecurityException)

        // 3. Owner deleting their own workspace succeeds
        val ownerRes = kotlinx.coroutines.runBlocking { repo.deleteCloudWorkspace("farmer_alice_123") }
        assertTrue(ownerRes.isSuccess)
    }

    @Test
    fun `RetrofitSatelliteRasterBackendRepository executes token refresh on 401, submits job, polls 9-stage lifecycle, and retrieves georeferenced result`() {
        val jsonMediaType = with(okhttp3.MediaType) { "application/json".toMediaType() }
        var tokenRefreshRequested = false
        val tokenProvider = com.example.data.FirebaseIdTokenProvider { forceRefresh ->
            if (forceRefresh) {
                tokenRefreshRequested = true
                "fresh_firebase_id_token_v2"
            } else {
                "expired_firebase_id_token_v1"
            }
        }

        var jobPollCount = 0
        val pollStates = listOf(
            "QUEUED" to 10,
            "DISCOVERING_SCENES" to 20,
            "DOWNLOADING_ASSETS" to 35,
            "PROCESSING_RASTERS" to 55,
            "CALCULATING_INDICES" to 75,
            "GENERATING_OUTPUTS" to 90,
            "COMPLETED" to 100
        )

        val mockInterceptor = okhttp3.Interceptor { chain ->
            val req = chain.request()
            val path = req.url.encodedPath
            val authHeader = req.header("Authorization").orEmpty()

            if (authHeader == "Bearer expired_firebase_id_token_v1") {
                return@Interceptor okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body(
                        with(okhttp3.ResponseBody) {
                            """{"detail":{"error_code":"TOKEN_EXPIRED","message":"Firebase ID token has expired"}}"""
                                .toResponseBody(jsonMediaType)
                        }
                    )
                    .build()
            }

            assertEquals("Bearer fresh_firebase_id_token_v2", authHeader)

            val responseJson = when {
                path.endsWith("/api/v1/satellite/scenes") -> {
                    """
                    {
                      "farm_id": "farm_wadi_1",
                      "scene_count": 1,
                      "scenes": [
                        {
                          "scene_id": "S2A_36RTU_20261118_1_L2A",
                          "scale_factor": 0.0001,
                          "boa_add_offset": -1000.0,
                          "band_assets": {"B04": "https://sentinel-cogs.s3.us-west-2.amazonaws.com/B04.tif"}
                        }
                      ]
                    }
                    """.trimIndent()
                }
                path.endsWith("/api/v1/satellite/process") -> {
                    """
                    {
                      "job_id": "job_live_001",
                      "result_id": "res_live_001",
                      "farm_id": "farm_wadi_1",
                      "owner_uid": "farmer_uid_1",
                      "status": "QUEUED",
                      "progress_pct": 5
                    }
                    """.trimIndent()
                }
                path.contains("/api/v1/satellite/jobs/") -> {
                    val idx = jobPollCount.coerceAtMost(pollStates.lastIndex)
                    jobPollCount++
                    val (st, pct) = pollStates[idx]
                    """
                    {
                      "job_id": "job_live_001",
                      "result_id": "res_live_001",
                      "status": "$st",
                      "progress_pct": $pct
                    }
                    """.trimIndent()
                }
                path.contains("/api/v1/satellite/results/") -> {
                    """
                    {
                      "result_id": "res_live_001",
                      "job_id": "job_live_001",
                      "farm_id": "farm_wadi_1",
                      "owner_uid": "farmer_uid_1",
                      "scene_id": "S2A_36RTU_20261118_1_L2A",
                      "crs_epsg": 32636,
                      "spatial_resolution_m": 10.0,
                      "statistics": {
                        "total_pixel_count": 64,
                        "polygon_pixel_count": 48,
                        "valid_pixel_count": 44,
                        "masked_cloud_or_nodata_count": 4,
                        "valid_pixel_ratio": 0.9167,
                        "ndvi_mean": 0.68,
                        "ndvi_std_dev": 0.05,
                        "ndre_mean": 0.41,
                        "ndmi_mean": 0.31,
                        "psri_mean": 0.04,
                        "anomaly_pixel_count": 6,
                        "anomaly_area_feddans": 0.14
                      },
                      "scientific_assessment": {
                        "vegetation_stress_detection_ar": "رصد إجهاد نباتي طيفي في 6 بكسل",
                        "agricultural_risk_assessment_ar": "مؤشر خطر زراعي متوسط",
                        "confirmed_disease_diagnosis_status_ar": "غير مؤكد من القمر الصناعي وحده"
                      },
                      "anomaly_zones_geojson": {"type":"FeatureCollection","features":[]},
                      "pixel_evaluations": [
                        {"row": 0, "col": 0, "ndvi": 0.68, "ndre": 0.41, "ndmi": 0.31, "psri": 0.04, "is_anomalous": false, "is_cloud_masked": false}
                      ]
                    }
                    """.trimIndent()
                }
                else -> """{"status":"HEALTHY"}"""
            }

            okhttp3.Response.Builder()
                .request(req)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(with(okhttp3.ResponseBody) { responseJson.toResponseBody(jsonMediaType) })
                .build()
        }

        val okHttp = okhttp3.OkHttpClient.Builder()
            .addInterceptor(mockInterceptor)
            .build()

        val repo = com.example.data.RetrofitSatelliteRasterBackendRepository(
            environment = com.example.data.BackendEnvironment.STAGING,
            baseUrl = "https://staging-api.agrosight.eg/",
            okHttpClient = okHttp,
            tokenProvider = tokenProvider,
            pollIntervalMs = 1L
        )

        val progressTransitions = mutableListOf<com.example.data.RasterPipelineExecutionState>()
        val result = kotlinx.coroutines.runBlocking {
            repo.runFullGeoreferencedSatelliteAnalysis(
                farmId = "farm_wadi_1",
                ownerUid = "farmer_uid_1",
                cropId = "tomato",
                growthStageIndex = 2,
                centerLat = 30.3833,
                centerLon = 30.3500,
                areaFeddans = 15f,
                dateStartIso = "2026-10-01",
                dateEndIso = "2026-11-20",
                onProgressUpdate = { state, _, _ ->
                    progressTransitions.add(state)
                }
            )
        }

        assertTrue("Repository call must succeed after automatic token refresh", result.isSuccess)
        assertTrue("Token force-refresh must have been triggered on HTTP 401", tokenRefreshRequested)
        val data = result.getOrThrow()
        assertEquals("res_live_001", data.resultId)
        assertEquals("S2A_36RTU_20261118_1_L2A", data.sceneId)
        assertEquals(32636, data.crsEpsg)
        assertEquals(44, data.validPixels)
        assertEquals(0.68f, data.ndviMean, 0.001f)
        assertTrue(
            "All 7 polled lifecycle states must be recorded",
            data.jobStatusHistory.containsAll(
                listOf(
                    "QUEUED",
                    "DISCOVERING_SCENES",
                    "DOWNLOADING_ASSETS",
                    "PROCESSING_RASTERS",
                    "CALCULATING_INDICES",
                    "GENERATING_OUTPUTS",
                    "COMPLETED"
                )
            )
        )
        assertTrue(progressTransitions.contains(com.example.data.RasterPipelineExecutionState.COMPLETED_LIVE_GEOREFERENCED))
    }

    @Test
    fun `RetrofitSatelliteRasterBackendRepository handles 401 expiration, 403 unauthorized farm, network interruption, 503 unavailable, and FAILED job`() {
        val jsonMediaType = with(okhttp3.MediaType) { "application/json".toMediaType() }
        fun buildRepoForMock(handler: (okhttp3.Request) -> okhttp3.Response): com.example.data.RetrofitSatelliteRasterBackendRepository {
            val client = okhttp3.OkHttpClient.Builder()
                .addInterceptor { chain -> handler(chain.request()) }
                .build()
            return com.example.data.RetrofitSatelliteRasterBackendRepository(
                environment = com.example.data.BackendEnvironment.PRODUCTION,
                baseUrl = "https://api.agrosight.eg/",
                okHttpClient = client,
                tokenProvider = com.example.data.FirebaseIdTokenProvider { "still_invalid_token" },
                pollIntervalMs = 1L
            )
        }

        // 1. Persistent HTTP 401 Authentication Expiration
        val repo401 = buildRepoForMock { req ->
            okhttp3.Response.Builder()
                .request(req)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body(
                    with(okhttp3.ResponseBody) {
                        """{"detail":{"error_code":"TOKEN_EXPIRED","message":"Expired token"}}"""
                            .toResponseBody(jsonMediaType)
                    }
                )
                .build()
        }
        val res401 = kotlinx.coroutines.runBlocking {
            repo401.runFullGeoreferencedSatelliteAnalysis("f1", "u1", "tomato", 2, 30.38, 30.35, 10f)
        }
        assertTrue(res401.isFailure)
        val err401 = res401.exceptionOrNull() as com.example.data.SatelliteBackendApiException
        assertEquals(401, err401.httpStatusCode)
        assertEquals("TOKEN_EXPIRED", err401.errorCode)

        // 2. HTTP 403 Unauthorized Farm Access
        val repo403 = buildRepoForMock { req ->
            okhttp3.Response.Builder()
                .request(req)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body(
                    with(okhttp3.ResponseBody) {
                        """{"detail":{"error_code":"FORBIDDEN_FARM_ACCESS","message":"Caller does not own farm"}}"""
                            .toResponseBody(jsonMediaType)
                    }
                )
                .build()
        }
        val res403 = kotlinx.coroutines.runBlocking {
            repo403.runFullGeoreferencedSatelliteAnalysis("farm_other_user", "u1", "tomato", 2, 30.38, 30.35, 10f)
        }
        assertTrue(res403.isFailure)
        val err403 = res403.exceptionOrNull() as com.example.data.SatelliteBackendApiException
        assertEquals(403, err403.httpStatusCode)
        assertEquals("FORBIDDEN_FARM_ACCESS", err403.errorCode)

        // 3. Network Interruption (IOException)
        val repoNet = buildRepoForMock {
            throw java.io.IOException("Socket closed mid-transfer")
        }
        val resNet = kotlinx.coroutines.runBlocking {
            repoNet.runFullGeoreferencedSatelliteAnalysis("f1", "u1", "tomato", 2, 30.38, 30.35, 10f)
        }
        assertTrue(resNet.isFailure)
        val errNet = resNet.exceptionOrNull() as com.example.data.SatelliteBackendApiException
        assertEquals("NETWORK_INTERRUPTED", errNet.errorCode)

        // 4. Backend Unavailable (HTTP 503 FIREBASE_AUTH_NOT_CONFIGURED)
        val repo503 = buildRepoForMock { req ->
            okhttp3.Response.Builder()
                .request(req)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(503)
                .message("Service Unavailable")
                .body(
                    with(okhttp3.ResponseBody) {
                        """{"detail":{"error_code":"FIREBASE_AUTH_NOT_CONFIGURED","message":"Missing Firebase Admin credentials"}}"""
                            .toResponseBody(jsonMediaType)
                    }
                )
                .build()
        }
        val res503 = kotlinx.coroutines.runBlocking {
            repo503.runFullGeoreferencedSatelliteAnalysis("f1", "u1", "tomato", 2, 30.38, 30.35, 10f)
        }
        assertTrue(res503.isFailure)
        val err503 = res503.exceptionOrNull() as com.example.data.SatelliteBackendApiException
        assertEquals(503, err503.httpStatusCode)
        assertEquals("FIREBASE_AUTH_NOT_CONFIGURED", err503.errorCode)

        // 5. Processing Failure during Job Execution (status = FAILED)
        val repoFailedJob = buildRepoForMock { req ->
            val path = req.url.encodedPath
            val bodyStr = when {
                path.endsWith("/api/v1/satellite/scenes") ->
                    """{"scenes":[{"scene_id":"S2A_TEST","band_assets":{"B04":"https://example.com/B04.tif"}}]}"""
                path.endsWith("/api/v1/satellite/process") ->
                    """{"job_id":"job_fail_99","result_id":"res_fail_99","status":"PROCESSING_RASTERS","progress_pct":45}"""
                else ->
                    """{"job_id":"job_fail_99","result_id":"res_fail_99","status":"FAILED","error_code":"CORRUPTED_RASTER_TIFF","error_message":"Truncated GeoTIFF tile strip"}"""
            }
            okhttp3.Response.Builder()
                .request(req)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(with(okhttp3.ResponseBody) { bodyStr.toResponseBody(jsonMediaType) })
                .build()
        }
        val resFailedJob = kotlinx.coroutines.runBlocking {
            repoFailedJob.runFullGeoreferencedSatelliteAnalysis("f1", "u1", "tomato", 2, 30.38, 30.35, 10f)
        }
        assertTrue(resFailedJob.isFailure)
        val errFailedJob = resFailedJob.exceptionOrNull() as com.example.data.SatelliteBackendApiException
        assertEquals("CORRUPTED_RASTER_TIFF", errFailedJob.errorCode)
    }

    @Test
    fun `ChatbotScreen route, grounded Gemini conversation flow, and AgroSight logo Material3 palette are consistent`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = buildTestViewModel(app)

        // 1. Verify AgroSight Logo Material3 Theme Palette
        assertEquals(
            com.example.ui.theme.AgroSightBotanicalGreen,
            com.example.ui.theme.AgroSightLogoLightColorScheme.primary
        )
        assertEquals(
            com.example.ui.theme.AgroSightPixelBlue,
            com.example.ui.theme.AgroSightLogoLightColorScheme.secondary
        )
        assertEquals(
            com.example.ui.theme.AgroSightIvory,
            com.example.ui.theme.AgroSightLogoLightColorScheme.background
        )
        assertEquals(
            com.example.ui.theme.AgroSightBotanicalSage,
            com.example.ui.theme.AgroSightLogoLightColorScheme.primaryContainer
        )
        assertEquals(
            androidx.compose.ui.text.style.TextAlign.Right,
            com.example.ui.theme.Typography.bodyLarge.textAlign
        )
        assertEquals(
            androidx.compose.ui.text.style.TextDirection.Rtl,
            com.example.ui.theme.Typography.bodyLarge.textDirection
        )

        // 2. Navigate to AppScreen.CHATBOT and send a farm-grounded query
        viewModel.selectScreen(AppScreen.CHATBOT)
        assertEquals(AppScreen.CHATBOT, viewModel.uiState.value.currentScreen)

        val initialCount = viewModel.uiState.value.geminiChatMessages.size
        viewModel.sendGeminiAgronomyChatMessage("ما المقنن المائي وحالة Sentinel-2 لحقلي اليوم؟", false)
        val afterSend = viewModel.uiState.value.geminiChatMessages
        assertTrue("Chatbot history should append user query and grounded assistant reply", afterSend.size >= initialCount + 2)
        val lastReply = afterSend.last()
        assertEquals("ASSISTANT", lastReply.role)
        assertTrue(lastReply.text.contains("إجابة استرشادية للسؤال"))
        assertTrue(lastReply.isLiveAiGenerated)

        // 3. Clear chat history resets to grounded welcome message
        viewModel.clearGeminiChatHistory()
        assertEquals(1, viewModel.uiState.value.geminiChatMessages.size)
        assertEquals("ASSISTANT", viewModel.uiState.value.geminiChatMessages.first().role)
    }

    @Test
    fun `RoomOfflineTelemetryCacheRepository persists weather data and satellite analysis results and serves them when connectivity fails`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val roomDb = androidx.room.Room.inMemoryDatabaseBuilder(
            app,
            com.example.data.AgroSightRoomDatabase::class.java
        )
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        try {
            val cacheRepo = com.example.data.RoomOfflineTelemetryCacheRepository(
                roomDb.offlineTelemetryCacheDao()
            )
            val dbService = DatabaseService(app)
            val fakeGemini = FakeAiCopilotDataSource()
            var isNetworkOnline = true

            val toggleableMeteoSource = object : MeteoForecastDataSource {
                override suspend fun fetch7DayAgroForecast(
                    latitude: Double,
                    longitude: Double,
                    baseKc: Float
                ): OpenMeteoResult {
                    return OpenMeteoClient().buildFallbackForecast(
                        latitude = latitude,
                        longitude = longitude,
                        baseKc = baseKc,
                        isLive = isNetworkOnline,
                        httpStatusCode = if (isNetworkOnline) 200 else 0,
                        errorReasonAr = if (isNetworkOnline) null else "Offline Test Disconnect"
                    )
                }
            }

            val toggleableSatelliteSource = object : SatelliteTelemetryDataSource {
                override suspend fun fetchLiveOpenSourceSatelliteAndNasaData(
                    latitude: Double,
                    longitude: Double
                ): RealSatelliteAndNasaTelemetry {
                    return RealSatelliteAndNasaTelemetry(
                        sceneId = if (isNetworkOnline) "S2A_LIVE_CACHED_SCENE_36RTU" else "DEMO_OFFLINE_FALLBACK",
                        acquisitionDateIso = "2026-11-18",
                        constellationAr = "Sentinel-2 L2A",
                        mgrsTileId = "36RTU",
                        cloudCoverPct = 1.2f,
                        platformInstrument = "MSI L2A",
                        stacCatalogSource = "Element84 STAC",
                        opticalTileUrl = "https://example.com/tile.png",
                        retrievedSatelliteBitmap = null,
                        nasaSolarRadiationMjM2Day = 24.8f,
                        nasaClearSkyInsolationMjM2Day = 26.5f,
                        nasaRootZoneSoilMoisturePct = 46.0f,
                        nasaTemp2mMaxC = 31.5f,
                        nasaTemp2mMinC = 18.0f,
                        nasaRelativeHumidityPct = 50.0f,
                        nasaWindSpeed2mMs = 3.2f,
                        nasaVaporPressureDeficitKpa = 1.7f,
                        channels = emptyList(),
                        stacAcquisitionStatus = if (isNetworkOnline) {
                            StacAcquisitionStatus.VALID_SCENE_WITH_BAND_ASSETS
                        } else {
                            StacAcquisitionStatus.NETWORK_OR_HTTP_FAILURE
                        },
                        dataMode = if (isNetworkOnline) {
                            com.example.data.TelemetryDataMode.LIVE
                        } else {
                            com.example.data.TelemetryDataMode.DEMO_ESTIMATED
                        }
                    )
                }
            }

            val viewModel = AgroSightViewModel(
                app,
                AgroSightDependencies(
                    databaseService = dbService,
                    openMeteoClient = toggleableMeteoSource,
                    openSourceSatelliteClient = toggleableSatelliteSource,
                    satelliteRasterBackendRepository = FakeSatelliteRasterBackendRepository(),
                    geminiAgroService = fakeGemini,
                    cloudWorker = AgroSightCloudWorker(dbService, fakeGemini),
                    firestoreRepository = FakeCloudWorkspaceRepository(),
                    sqlAuditDao = roomDb.sqlFieldAuditDao(),
                    offlineTelemetryCacheRepository = cacheRepo
                )
            )

            // 1. Online telemetry refresh caches both weather forecast and satellite analysis in Room SQLite
            val activeFieldId = viewModel.uiState.value.fieldId
            val activeLat = viewModel.uiState.value.latitude
            val activeLon = viewModel.uiState.value.longitude
            val cachedWeather = kotlinx.coroutines.runBlocking {
                cacheRepo.getCachedWeatherForecast(activeFieldId, activeLat, activeLon)
            }
            val cachedSatellite = kotlinx.coroutines.runBlocking {
                cacheRepo.getCachedSatelliteAnalysis(activeFieldId, activeLat, activeLon)
            }
            assertNotNull("Weather forecast must be persisted in Room SQLite", cachedWeather)
            assertNotNull("Satellite analysis must be persisted in Room SQLite", cachedSatellite)
            assertEquals("S2B_35RQQ_20260501_0_L2A", cachedSatellite!!.sceneId)
            assertTrue(cachedWeather!!.wasOriginallyLive)
            assertTrue(cachedSatellite.wasOriginallyLive)

            // 2. Simulate loss of internet connectivity and refresh telemetry -> falls back to Room SQLite cache
            isNetworkOnline = false
            viewModel.restoreTelemetryFromRoomOfflineCache()

            val offlineState = viewModel.uiState.value
            assertTrue("App should serve telemetry from Room offline cache when offline", offlineState.isServingFromRoomOfflineCache)
            assertTrue(offlineState.meteoStatusAr.contains("CACHED • Room SQLite"))
            assertNotNull(offlineState.satelliteAndNasaTelemetry)
            assertEquals(
                com.example.data.TelemetryDataMode.CACHED,
                offlineState.satelliteAndNasaTelemetry?.dataMode
            )
            assertNotNull("Georeferenced raster analysis must remain available from Room cache while offline", offlineState.rasterBackendResult)
            assertEquals("S2B_35RQQ_20260501_0_L2A", offlineState.rasterBackendResult?.sceneId)

            // 3. Clearing offline cache empties Room tables
            viewModel.clearOfflineTelemetryCache()
            val afterClearWeather = kotlinx.coroutines.runBlocking {
                cacheRepo.getCachedWeatherForecast(activeFieldId, activeLat, activeLon)
            }
            val afterClearSat = kotlinx.coroutines.runBlocking {
                cacheRepo.getCachedSatelliteAnalysis(activeFieldId, activeLat, activeLon)
            }
            assertEquals(null, afterClearWeather)
            assertEquals(null, afterClearSat)
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun `RetrofitSatelliteRasterBackendRepository enforces AUD-02 signed URL POST and binary GeoTIFF GET contract with integrity checks`() {
        val jsonMediaType = with(okhttp3.MediaType) { "application/json".toMediaType() }
        val tiffMediaType = with(okhttp3.MediaType) { "image/tiff".toMediaType() }

        val sampleGrid = arrayOf(
            floatArrayOf(0.75f, 0.72f, 0.69f, 0.68f),
            floatArrayOf(0.71f, 0.25f, 0.23f, 0.67f),
            floatArrayOf(0.70f, 0.24f, 0.22f, 0.66f),
            floatArrayOf(0.73f, 0.70f, 0.68f, 0.65f)
        )
        val validTiffBytes = com.example.data.KotlinGeoTiffCogProcessor.writeGeoTiffBytes(
            width = 4,
            height = 4,
            crsEpsg = 32636,
            originX = 245000.0,
            originY = 3365000.0,
            pixelWidth = 10.0,
            pixelHeight = 10.0,
            grid = sampleGrid,
            isFloat32 = true
        )
        val validSha256 = java.security.MessageDigest.getInstance("SHA-256")
            .digest(validTiffBytes)
            .joinToString("") { "%02x".format(it) }

        val validSignature = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
        val validExpires = 1893456000L

        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                val authHeader = req.header("Authorization").orEmpty()

                when {
                    // 1. POST /api/v1/satellite/files/{file_id}/signed-url
                    path.endsWith("/signed-url") -> {
                        if (req.method != "POST") {
                            return@addInterceptor okhttp3.Response.Builder()
                                .request(req)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(405)
                                .message("Method Not Allowed")
                                .body(
                                    with(okhttp3.ResponseBody) {
                                        """{"error_code":"METHOD_NOT_ALLOWED","message":"Use POST"}"""
                                            .toResponseBody(jsonMediaType)
                                    }
                                )
                                .build()
                        }
                        if (authHeader != "Bearer valid_owner_token") {
                            val status = if (authHeader.isBlank()) 401 else 403
                            val errCode = if (status == 401) "MISSING_BEARER_TOKEN" else "FORBIDDEN_FARM_ACCESS"
                            return@addInterceptor okhttp3.Response.Builder()
                                .request(req)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(status)
                                .message("Auth Error")
                                .body(
                                    with(okhttp3.ResponseBody) {
                                        """{"error_code":"$errCode","message":"Unauthorized access"}"""
                                            .toResponseBody(jsonMediaType)
                                    }
                                )
                                .build()
                        }
                        if (path.contains("file_missing_404")) {
                            return@addInterceptor okhttp3.Response.Builder()
                                .request(req)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(404)
                                .message("Not Found")
                                .body(
                                    with(okhttp3.ResponseBody) {
                                        """{"error_code":"FILE_NOT_FOUND","message":"File does not exist"}"""
                                            .toResponseBody(jsonMediaType)
                                    }
                                )
                                .build()
                        }
                        val reqBuffer = okio.Buffer()
                        req.body?.writeTo(reqBuffer)
                        val bodyJson = org.json.JSONObject(reqBuffer.readUtf8())
                        val ttl = bodyJson.optInt("ttl_seconds", 300)
                        val respJson = """
                            {
                              "file_id": "file_ndvi_01",
                              "index_name": "NDVI",
                              "expires_epoch": $validExpires,
                              "ttl_seconds": $ttl,
                              "signed_download_url": "/api/v1/satellite/files/file_ndvi_01?expires=$validExpires&signature=$validSignature"
                            }
                        """.trimIndent()
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(with(okhttp3.ResponseBody) { respJson.toResponseBody(jsonMediaType) })
                            .build()
                    }

                    // 2. GET /api/v1/satellite/files/{file_id}
                    path.startsWith("/api/v1/satellite/files/") -> {
                        if (path.endsWith("file_missing_404")) {
                            return@addInterceptor okhttp3.Response.Builder()
                                .request(req)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(404)
                                .message("Not Found")
                                .body(
                                    with(okhttp3.ResponseBody) {
                                        """{"error_code":"FILE_NOT_FOUND","message":"Missing raster file"}"""
                                            .toResponseBody(jsonMediaType)
                                    }
                                )
                                .build()
                        }
                        val qExpires = req.url.queryParameter("expires")
                        val qSig = req.url.queryParameter("signature")
                        if (!qExpires.isNullOrBlank() || !qSig.isNullOrBlank()) {
                            val expLong = qExpires?.toLongOrNull() ?: 0L
                            if (expLong < 1700000000L) {
                                return@addInterceptor okhttp3.Response.Builder()
                                    .request(req)
                                    .protocol(okhttp3.Protocol.HTTP_1_1)
                                    .code(401)
                                    .message("Expired")
                                    .body(
                                        with(okhttp3.ResponseBody) {
                                            """{"error_code":"SIGNED_URL_EXPIRED","message":"Signed download URL has expired"}"""
                                                .toResponseBody(jsonMediaType)
                                        }
                                    )
                                    .build()
                            }
                            if (qSig != validSignature) {
                                return@addInterceptor okhttp3.Response.Builder()
                                    .request(req)
                                    .protocol(okhttp3.Protocol.HTTP_1_1)
                                    .code(403)
                                    .message("Forbidden")
                                    .body(
                                        with(okhttp3.ResponseBody) {
                                            """{"error_code":"INVALID_SIGNED_URL_SIGNATURE","message":"Signature mismatch"}"""
                                                .toResponseBody(jsonMediaType)
                                        }
                                    )
                                    .build()
                            }
                        } else if (authHeader != "Bearer valid_owner_token") {
                            return@addInterceptor okhttp3.Response.Builder()
                                .request(req)
                                .protocol(okhttp3.Protocol.HTTP_1_1)
                                .code(403)
                                .message("Forbidden")
                                .body(
                                    with(okhttp3.ResponseBody) {
                                        """{"error_code":"FORBIDDEN_FARM_ACCESS","message":"Not authorized"}"""
                                            .toResponseBody(jsonMediaType)
                                    }
                                )
                                .build()
                        }

                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .addHeader("Content-Type", "image/tiff")
                            .addHeader("Content-Disposition", """attachment; filename="res_01_NDVI.tif"""")
                            .body(with(okhttp3.ResponseBody) { validTiffBytes.toResponseBody(tiffMediaType) })
                            .build()
                    }

                    else -> error("Unexpected path: $path")
                }
            }
            .build()

        val repo = com.example.data.RetrofitSatelliteRasterBackendRepository(
            environment = com.example.data.BackendEnvironment.PRODUCTION,
            baseUrl = "https://api.agrosight.eg/",
            okHttpClient = client,
            tokenProvider = com.example.data.FirebaseIdTokenProvider { "valid_owner_token" }
        )

        // A. Authorized signed URL creation (POST /api/v1/satellite/files/{fileId}/signed-url) -> 200
        val signedRes = kotlinx.coroutines.runBlocking {
            repo.createSignedFileDownloadUrl(fileId = "file_ndvi_01", ttlSeconds = 180)
        }
        assertTrue(signedRes.isSuccess)
        val signedUrlObj = signedRes.getOrThrow()
        assertEquals("file_ndvi_01", signedUrlObj.fileId)
        assertEquals("NDVI", signedUrlObj.indexName)
        assertEquals(180, signedUrlObj.ttlSeconds)
        assertEquals(validSignature, signedUrlObj.signature)

        // B. Unauthorized signed URL creation -> 403 FORBIDDEN_FARM_ACCESS
        val unauthSignedRes = kotlinx.coroutines.runBlocking {
            repo.createSignedFileDownloadUrl(
                fileId = "file_ndvi_01",
                ttlSeconds = 180,
                explicitAuthToken = "other_user_token"
            )
        }
        assertTrue(unauthSignedRes.isFailure)
        val unauthErr = unauthSignedRes.exceptionOrNull() as com.example.data.SatelliteBackendApiException
        assertEquals(403, unauthErr.httpStatusCode)
        assertEquals("FORBIDDEN_FARM_ACCESS", unauthErr.errorCode)

        // C. Binary GeoTIFF download using valid signed URL -> 200 + verified SHA-256 & parsed GeoTIFF
        val dlRes = kotlinx.coroutines.runBlocking {
            repo.downloadOutputGeoTiffRaster(
                fileId = "file_ndvi_01",
                expectedIndexName = "NDVI",
                expectedSha256 = validSha256,
                signedUrl = signedUrlObj
            )
        }
        assertTrue(dlRes.isSuccess)
        val downloaded = dlRes.getOrThrow()
        assertEquals("image/tiff", downloaded.contentType)
        assertEquals("res_01_NDVI.tif", downloaded.fileName)
        assertEquals(validSha256, downloaded.sha256Checksum)
        assertEquals(4, downloaded.parsedBand.width)
        assertEquals(4, downloaded.parsedBand.height)
        assertEquals(32636, downloaded.parsedBand.crsEpsg)
        assertEquals(0.75f, downloaded.parsedBand.grid[0][0], 0.001f)

        // D. Invalid signature -> 403 INVALID_SIGNED_URL_SIGNATURE
        val tamperedSigned = signedUrlObj.copy(signature = "0000invalid")
        val badSigRes = kotlinx.coroutines.runBlocking {
            repo.downloadOutputGeoTiffRaster(
                fileId = "file_ndvi_01",
                expectedIndexName = "NDVI",
                signedUrl = tamperedSigned
            )
        }
        assertTrue(badSigRes.isFailure)
        val badSigErr = badSigRes.exceptionOrNull() as com.example.data.SatelliteBackendApiException
        assertEquals(403, badSigErr.httpStatusCode)
        assertEquals("INVALID_SIGNED_URL_SIGNATURE", badSigErr.errorCode)

        // E. Expired signed URL -> 401 SIGNED_URL_EXPIRED
        val expiredSigned = signedUrlObj.copy(expiresEpoch = 1600000000L)
        val expiredRes = kotlinx.coroutines.runBlocking {
            repo.downloadOutputGeoTiffRaster(
                fileId = "file_ndvi_01",
                expectedIndexName = "NDVI",
                signedUrl = expiredSigned
            )
        }
        assertTrue(expiredRes.isFailure)
        val expErr = expiredRes.exceptionOrNull() as com.example.data.SatelliteBackendApiException
        assertEquals(401, expErr.httpStatusCode)
        assertEquals("SIGNED_URL_EXPIRED", expErr.errorCode)

        // F. Missing file -> 404 FILE_NOT_FOUND
        val missingRes = kotlinx.coroutines.runBlocking {
            repo.downloadOutputGeoTiffRaster(
                fileId = "file_missing_404",
                expectedIndexName = "NDVI"
            )
        }
        assertTrue(missingRes.isFailure)
        val missingErr = missingRes.exceptionOrNull() as com.example.data.SatelliteBackendApiException
        assertEquals(404, missingErr.httpStatusCode)
        assertEquals("FILE_NOT_FOUND", missingErr.errorCode)

        // G. Corrupted / checksum mismatch detection -> GEOTIFF_CHECKSUM_MISMATCH
        val checksumMismatchRes = kotlinx.coroutines.runBlocking {
            repo.downloadOutputGeoTiffRaster(
                fileId = "file_ndvi_01",
                expectedIndexName = "NDVI",
                expectedSha256 = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                signedUrl = signedUrlObj
            )
        }
        assertTrue(checksumMismatchRes.isFailure)
        val mismatchErr = checksumMismatchRes.exceptionOrNull() as com.example.data.SatelliteBackendApiException
        assertEquals("GEOTIFF_CHECKSUM_MISMATCH", mismatchErr.errorCode)
    }

    @Test
    fun `FIX 4 cross-language Python GeoTiffCogProcessor float32 GeoTIFF binary is verified and parsed by Android repository`() {
        // Exact 352-byte float32 GeoTIFF (4x3, EPSG:32636, origin=(305000.0, 3365000.0), pixel=(10.0, -10.0), GDAL_NODATA=-9999.0)
        // generated directly by Python backend's GeoTiffCogProcessor.write_geotiff(..., dtype="float32")
        val pythonGeneratedGeoTiffBase64 =
            "SUkqALIAAAB4nGNgcLB/E2hhXy2iZ891Xdm+/5CpvbHxZrugHfPsDn/VsL++2MBevlXbPi1NzT5oh5w9AJMhESMAAAAAAAAA" +
                "JEAAAAAAAAAkQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKCdEkEAAAAARKxJQQAAAAAAAAAAAQABAAAA" +
                "AwAABAAAAQABAAEEAAABAAEAAAwAAAEAfH8tOTk5OS4wAA4AAAEEAAEAAAAEAAAAAQEEAAEAAAADAAAAAgEDAAEAAAAgAAAA" +
                "AwEDAAEAAAAIAAAABgEDAAEAAAABAAAAEQEEAAEAAAAIAAAAFQEDAAEAAAABAAAAFgEEAAEAAAADAAAAFwEEAAEAAAA5AAAA" +
                "UwEDAAEAAAADAAAADoMMAAMAAABCAAAAgoQMAAYAAABaAAAAr4cDABAAAACKAAAAgaQCAAgAAACqAAAAAAAAAA=="
        val pythonTiffBytes = java.util.Base64.getDecoder().decode(pythonGeneratedGeoTiffBase64)
        val expectedPythonSha256 = "3767c9e896aaa553a5923686c658de4851aae6420bc756cf25c4ad1dca7911a3"
        assertEquals(352, pythonTiffBytes.size)

        val jsonMediaType = with(okhttp3.MediaType) { "application/json; charset=utf-8".toMediaType() }
        val tiffMediaType = with(okhttp3.MediaType) { "image/tiff".toMediaType() }

        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val path = req.url.encodedPath
                when {
                    req.method == "POST" && path == "/api/v1/satellite/files/file_py_ndvi_4x3/signed-url" -> {
                        val respJson = """
                            {
                              "file_id": "file_py_ndvi_4x3",
                              "index_name": "NDVI",
                              "expires_epoch": 1999999999,
                              "ttl_seconds": 300,
                              "signed_download_url": "/api/v1/satellite/files/file_py_ndvi_4x3?expires=1999999999&signature=py_sig_valid_123"
                            }
                        """.trimIndent()
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(with(okhttp3.ResponseBody) { respJson.toResponseBody(jsonMediaType) })
                            .build()
                    }
                    req.method == "GET" && path == "/api/v1/satellite/files/file_py_ndvi_4x3" -> {
                        okhttp3.Response.Builder()
                            .request(req)
                            .protocol(okhttp3.Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .addHeader("Content-Type", "image/tiff")
                            .addHeader("Content-Disposition", """attachment; filename="py_backend_NDVI.tif"""")
                            .body(with(okhttp3.ResponseBody) { pythonTiffBytes.toResponseBody(tiffMediaType) })
                            .build()
                    }
                    else -> error("Unexpected request: ${req.method} $path")
                }
            }
            .build()

        val repo = com.example.data.RetrofitSatelliteRasterBackendRepository(
            environment = com.example.data.BackendEnvironment.PRODUCTION,
            baseUrl = "https://api.agrosight.eg/",
            okHttpClient = client,
            tokenProvider = com.example.data.FirebaseIdTokenProvider { "valid_owner_token" }
        )

        val outcome = kotlinx.coroutines.runBlocking {
            repo.downloadOutputGeoTiffWithSignedUrlRefresh(
                fileId = "file_py_ndvi_4x3",
                expectedIndexName = "NDVI",
                expectedSha256 = expectedPythonSha256
            )
        }
        assertTrue("Expected Python GeoTIFF download & parse to succeed", outcome.isSuccess)
        val (downloaded, signedUrl) = outcome.getOrThrow()
        assertEquals("file_py_ndvi_4x3", signedUrl.fileId)
        assertEquals(expectedPythonSha256, downloaded.sha256Checksum)
        assertEquals("py_backend_NDVI.tif", downloaded.fileName)

        val band = downloaded.parsedBand
        assertEquals("NDVI", band.bandName)
        assertEquals(4, band.width)
        assertEquals(3, band.height)
        assertEquals(32636, band.crsEpsg)
        assertEquals(305000.0, band.originX, 0.01)
        assertEquals(3365000.0, band.originY, 0.01)
        assertEquals(10.0, band.pixelWidth, 0.001)
        assertEquals(-10.0, band.pixelHeight, 0.001)

        // Verify representative float32 pixel values written by Python struct.pack("<f", ...)
        assertEquals(0.75f, band.grid[0][0], 0.0001f)
        assertEquals(0.72f, band.grid[0][1], 0.0001f)
        assertEquals(0.68f, band.grid[0][2], 0.0001f)
        assertEquals(0.64f, band.grid[0][3], 0.0001f)
        assertEquals(0.35f, band.grid[1][1], 0.0001f)
        assertEquals(0.31f, band.grid[1][2], 0.0001f)
        assertEquals(0.62f, band.grid[2][3], 0.0001f)
    }

    @Test
    fun `FIX 1 and FIX 3 ViewModel GeoTIFF download workflow handles expired signed URL refresh duplicate guard and farm association`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        // FIX 3 verification: placeholder and numeric keys are rejected; valid AIza key format is recognized
        assertFalse(com.example.ui.components.isConfiguredGoogleMapsKey(""))
        assertFalse(com.example.ui.components.isConfiguredGoogleMapsKey("MY_GOOGLE_MAPS_API_KEY"))
        assertFalse(com.example.ui.components.isConfiguredGoogleMapsKey("2317154"))
        assertTrue(com.example.ui.components.isConfiguredGoogleMapsKey("AIzaSyD-ValidFormatSyntheticKeyForUnitTest1234"))
        assertFalse(com.example.ui.components.hasManifestGoogleMapsApiKey(app))

        val pythonGeneratedGeoTiffBase64 =
            "SUkqALIAAAB4nGNgcLB/E2hhXy2iZ891Xdm+/5CpvbHxZrugHfPsDn/VsL++2MBevlXbPi1NzT5oh5w9AJMhESMAAAAAAAAA" +
                "JEAAAAAAAAAkQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKCdEkEAAAAARKxJQQAAAAAAAAAAAQABAAAA" +
                "AwAABAAAAQABAAEEAAABAAEAAAwAAAEAfH8tOTk5OS4wAA4AAAEEAAEAAAAEAAAAAQEEAAEAAAADAAAAAgEDAAEAAAAgAAAA" +
                "AwEDAAEAAAAIAAAABgEDAAEAAAABAAAAEQEEAAEAAAAIAAAAFQEDAAEAAAABAAAAFgEEAAEAAAADAAAAFwEEAAEAAAA5AAAA" +
                "UwEDAAEAAAADAAAADoMMAAMAAABCAAAAgoQMAAYAAABaAAAAr4cDABAAAACKAAAAgaQCAAgAAACqAAAAAAAAAA=="
        val pythonTiffBytes = java.util.Base64.getDecoder().decode(pythonGeneratedGeoTiffBase64)
        val expectedPythonSha256 = "3767c9e896aaa553a5923686c658de4851aae6420bc756cf25c4ad1dca7911a3"

        var signedUrlCalls = 0
        var downloadCalls = 0
        var expiredFailuresObserved = 0
        var simulateDownloadDelayMs = 0L

        val customRepo = object : com.example.data.SatelliteRasterBackendRepository {
            override suspend fun checkBackendHealth(): Result<Boolean> = Result.success(true)

            override suspend fun runFullGeoreferencedSatelliteAnalysis(
                farmId: String,
                ownerUid: String,
                cropId: String,
                growthStageIndex: Int,
                centerLat: Double,
                centerLon: Double,
                areaFeddans: Float,
                customPolygonGeoJson: String?,
                explicitAuthToken: String?
            ): Result<com.example.data.GeoreferencedRasterBackendResult> {
                return Result.success(
                    com.example.data.GeoreferencedRasterBackendResult(
                        resultId = "res_vm_geotiff_01",
                        jobId = "job_vm_geotiff_01",
                        farmId = farmId,
                        ownerUid = ownerUid,
                        sceneId = "S2A_36RUV_20260501_L2A",
                        cropId = cropId,
                        executionState = com.example.data.RasterPipelineExecutionState.COMPLETED_LIVE_GEOREFERENCED,
                        isLiveGeoreferencedRaster = true,
                        crsEpsg = 32636,
                        spatialResolutionMeters = 10f,
                        totalPixels = 12,
                        polygonPixels = 12,
                        validPixels = 12,
                        maskedCloudPixels = 0,
                        validPixelRatio = 1.0f,
                        ndviMean = 0.65f,
                        ndviStdDev = 0.08f,
                        ndreMean = 0.36f,
                        ndmiMean = 0.28f,
                        psriMean = 0.07f,
                        anomalyPixelCount = 2,
                        anomalyAreaFeddans = 0.05f,
                        anomalyZonesGeoJson = """{"type":"FeatureCollection","features":[]}""",
                        pixelGrid = emptyList(),
                        vegetationStressDetectionAr = "رصد طيفي طبيعي",
                        agriculturalRiskAssessmentAr = "مخاطر منخفضة",
                        confirmedDiseaseDiagnosisStatusAr = "يتطلب فحصاً حقلياً",
                        statusSummaryAr = "تحليل مكتمل",
                        jobStatusHistory = listOf("QUEUED", "COMPLETED"),
                        outputFiles = mapOf(
                            "NDVI" to com.example.data.RasterOutputFileDescriptor(
                                fileId = "file_vm_ndvi_01",
                                indexName = "NDVI",
                                downloadEndpoint = "/api/v1/satellite/files/file_vm_ndvi_01",
                                signedUrlEndpoint = "/api/v1/satellite/files/file_vm_ndvi_01/signed-url",
                                sha256Checksum = expectedPythonSha256,
                                sizeBytes = pythonTiffBytes.size.toLong(),
                                contentType = "image/tiff",
                                crsEpsg = 32636,
                                spatialResolutionMeters = 10f
                            )
                        )
                    )
                )
            }

            override suspend fun createSignedFileDownloadUrl(
                fileId: String,
                ttlSeconds: Int,
                explicitAuthToken: String?
            ): Result<com.example.data.SignedRasterDownloadUrl> {
                signedUrlCalls++
                return Result.success(
                    com.example.data.SignedRasterDownloadUrl(
                        fileId = fileId,
                        indexName = "NDVI",
                        expiresEpoch = 1999999999L,
                        ttlSeconds = ttlSeconds,
                        signedDownloadUrl = "/api/v1/satellite/files/$fileId?expires=1999999999&signature=fresh_sig_$signedUrlCalls",
                        signature = "fresh_sig_$signedUrlCalls"
                    )
                )
            }

            override suspend fun downloadOutputGeoTiffRaster(
                fileId: String,
                expectedIndexName: String,
                expectedSha256: String?,
                signedUrl: com.example.data.SignedRasterDownloadUrl?,
                explicitAuthToken: String?
            ): Result<com.example.data.DownloadedGeoTiffRaster> {
                downloadCalls++
                if (simulateDownloadDelayMs > 0L) {
                    kotlinx.coroutines.delay(simulateDownloadDelayMs)
                }
                if (signedUrl != null && signedUrl.expiresEpoch < 1700000000L) {
                    expiredFailuresObserved++
                    return Result.failure(
                        com.example.data.SatelliteBackendApiException(
                            httpStatusCode = 401,
                            errorCode = "SIGNED_URL_EXPIRED",
                            userMessageAr = "انتهت صلاحية رابط التحميل الموقّع"
                        )
                    )
                }
                val parsed = com.example.data.KotlinGeoTiffCogProcessor.readGeoTiffBytes(
                    pythonTiffBytes,
                    bandName = expectedIndexName
                )
                return Result.success(
                    com.example.data.DownloadedGeoTiffRaster(
                        fileId = fileId,
                        indexName = expectedIndexName,
                        contentType = "image/tiff",
                        fileName = "${fileId}_${expectedIndexName}.tif",
                        rawBytes = pythonTiffBytes,
                        sha256Checksum = expectedPythonSha256,
                        parsedBand = parsed
                    )
                )
            }
        }

        app.getSharedPreferences("agrosight_multi_tenant_db", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val dbService = DatabaseService(app)
        val fakeGemini = FakeAiCopilotDataSource()
        val viewModel = AgroSightViewModel(
            app,
            AgroSightDependencies(
                databaseService = dbService,
                openMeteoClient = FakeMeteoDataSource(),
                openSourceSatelliteClient = FakeSatelliteDataSource(),
                satelliteRasterBackendRepository = customRepo,
                geminiAgroService = fakeGemini,
                cloudWorker = AgroSightCloudWorker(dbService, fakeGemini),
                firestoreRepository = FakeCloudWorkspaceRepository(authenticatedUid = "farmer_1"),
                sqlAuditDao = FakeSqlFieldAuditDao()
            )
        )

        // Wait for initial analysis job to populate rasterBackendResult (note: analysis does NOT auto-download GeoTIFFs)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        var waitTicks = 0
        while (viewModel.uiState.value.rasterBackendResult == null && waitTicks < 40) {
            Thread.sleep(25)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            waitTicks++
        }
        val rasterRes = viewModel.uiState.value.rasterBackendResult
        assertNotNull("Expected rasterBackendResult to be populated", rasterRes)
        assertTrue(
            "Analysis must not automatically download GeoTIFF files without user action",
            viewModel.uiState.value.downloadedGeoTiffRasters.isEmpty()
        )
        assertEquals(0, downloadCalls)

        // 1. Seed an EXPIRED signed URL for file_vm_ndvi_01 and trigger user download action
        viewModel.cacheSignedRasterUrl(
            com.example.data.SignedRasterDownloadUrl(
                fileId = "file_vm_ndvi_01",
                indexName = "NDVI",
                expiresEpoch = 1600000000L, // expired
                ttlSeconds = 60,
                signedDownloadUrl = "/api/v1/satellite/files/file_vm_ndvi_01?expires=1600000000&signature=old_sig",
                signature = "old_sig"
            )
        )

        viewModel.downloadAndInspectOutputGeoTiff("NDVI")
        var dlTicks = 0
        while (viewModel.uiState.value.isDownloadingGeoTiffRaster && dlTicks < 40) {
            Thread.sleep(25)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            dlTicks++
        }

        // Verify expired signed URL caused 1 expired failure, 1 fresh signed URL creation, and 2 total download attempts
        assertEquals(1, expiredFailuresObserved)
        assertEquals(1, signedUrlCalls)
        assertEquals(2, downloadCalls)

        val downloadedNdvi = viewModel.uiState.value.downloadedGeoTiffRasters["NDVI"]
        assertNotNull("Expected NDVI GeoTIFF to be downloaded and stored in state", downloadedNdvi)
        assertEquals("file_vm_ndvi_01", downloadedNdvi!!.fileId)
        assertEquals("NDVI", downloadedNdvi.indexName)
        assertEquals(expectedPythonSha256, downloadedNdvi.sha256Checksum)
        assertEquals(4, downloadedNdvi.parsedBand.width)
        assertEquals(3, downloadedNdvi.parsedBand.height)
        assertNotNull(viewModel.uiState.value.lastDownloadedGeoTiffSummaryAr)
        assertTrue(viewModel.uiState.value.lastDownloadedGeoTiffSummaryAr!!.contains(rasterRes!!.farmId))
        assertTrue(viewModel.uiState.value.lastDownloadedGeoTiffSummaryAr!!.contains("res_vm_geotiff_01"))

        // 2. Verify requesting an unavailable index ("PSRI") reports a clear error and preserves association
        viewModel.downloadAndInspectOutputGeoTiff("PSRI")
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertNotNull(viewModel.uiState.value.rasterDownloadErrorAr)
        assertTrue(viewModel.uiState.value.rasterDownloadErrorAr!!.contains("PSRI"))

        // 3. Verify duplicate-download prevention when a request is already running
        val callsBeforeDuplicateTest = downloadCalls
        simulateDownloadDelayMs = 150L
        viewModel.downloadAndInspectOutputGeoTiff("NDVI")
        // Call again immediately while first request is still running
        viewModel.downloadAndInspectOutputGeoTiff("NDVI")
        var dupTicks = 0
        while (viewModel.uiState.value.isDownloadingGeoTiffRaster && dupTicks < 40) {
            Thread.sleep(25)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            dupTicks++
        }
        assertEquals(
            "Concurrent duplicate download call must be ignored while a download is already running",
            callsBeforeDuplicateTest + 1,
            downloadCalls
        )
    }

    @Test
    fun `TASK 1 real cross-process Python HTTP server to Android Retrofit GeoTIFF download and parse`() {
        // NOTE ON ENVIRONMENT & AUTHENTICATION:
        // 1. Third-party fastapi/uvicorn wheels are not installed in this container's Python environment.
        //    backend/app.py provides create_http_server(service, host="127.0.0.1", port=0), which runs the
        //    actual SatelliteBackendService route handlers (POST /api/v1/satellite/files/{file_id}/signed-url
        //    and GET /api/v1/satellite/files/{file_id}) over a real OS TCP socket via ThreadingHTTPServer.
        // 2. Authentication uses make_test_authenticator() + create_test_firebase_jwt(), explicitly
        //    identified as SIMULATED AUTHENTICATION for local automated testing.
        val backendDir = java.io.File("backend").takeIf { it.isDirectory }
            ?: java.io.File("../backend").takeIf { it.isDirectory }
            ?: error("Could not locate backend directory from working directory ${java.io.File(".").absolutePath}")

        val pythonServerScript = """
import hashlib
import os
import shutil
import sys
import tempfile
import threading
import time

from app import SatelliteBackendService, create_http_server
from raster_service import GeoTiffCogProcessor, GeoTransform, RasterBandData
from test_raster_service import create_test_firebase_jwt, make_test_authenticator

storage_dir = tempfile.mkdtemp(prefix="agrosight_real_http_task1_")
try:
    os.environ["AGROSIGHT_ENV"] = "TEST"
    svc = SatelliteBackendService(
        storage_root=storage_dir,
        authenticator=make_test_authenticator(),
        signed_url_secret="task1-real-http-socket-signed-url-secret-32b",
        environment="TEST",
    )
    grid = [
        [0.75, 0.72, 0.68, 0.64],
        [0.71, 0.35, 0.31, 0.66],
        [0.69, 0.67, 0.65, 0.62],
    ]
    gt = GeoTransform(305000.0, 10.0, 0.0, 3365000.0, 0.0, -10.0)
    band_data = RasterBandData("NDVI", 4, 3, 32636, gt, grid, nodata_value=-9999.0)
    out_dir = os.path.join(storage_dir, "outputs")
    os.makedirs(out_dir, exist_ok=True)
    tiff_path = os.path.join(out_dir, "real_http_NDVI.tif")
    GeoTiffCogProcessor.write_geotiff(tiff_path, band_data, dtype="float32")
    with open(tiff_path, "rb") as f:
        tiff_bytes = f.read()
    sha256_hex = hashlib.sha256(tiff_bytes).hexdigest()

    svc.db.register_or_get_farm(
        farm_id="farm_real_http_01",
        owner_uid="farmer_real_http_uid",
        farm_name="Real HTTP Test Farm",
        crop_id="wheat",
    )
    svc.db.register_output_file(
        file_id="file_real_http_ndvi",
        result_id="res_real_http_01",
        job_id="job_real_http_01",
        farm_id="farm_real_http_01",
        owner_uid="farmer_real_http_uid",
        index_name="NDVI",
        private_file_path=tiff_path,
        file_size_bytes=len(tiff_bytes),
        sha256_hex=sha256_hex,
        crs_epsg=32636,
    )

    httpd, _ = create_http_server(service=svc, host="127.0.0.1", port=0)
    actual_port = httpd.server_address[1]
    server_thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    server_thread.start()

    # Simulated Firebase JWT for local automated verification
    sim_token = create_test_firebase_jwt(uid="farmer_real_http_uid", email="farmer@agrosight.eg")
    print(f"READY|{actual_port}|file_real_http_ndvi|{sha256_hex}|{sim_token}", flush=True)
    sys.stdin.readline()
    httpd.shutdown()
    httpd.server_close()
finally:
    shutil.rmtree(storage_dir, ignore_errors=True)
        """.trimIndent()

        val pb = ProcessBuilder("python3", "-u", "-c", pythonServerScript)
        pb.environment()["PYTHONPATH"] = backendDir.absolutePath
        pb.redirectErrorStream(true)
        val process = pb.start()

        try {
            val reader = process.inputStream.bufferedReader()
            val readyLine = reader.readLine()
                ?: error("Python HTTP server subprocess exited without printing READY line")
            assertTrue(
                "Expected READY line from Python HTTP server but got: $readyLine",
                readyLine.startsWith("READY|")
            )
            val parts = readyLine.split("|")
            val port = parts[1].toInt()
            val fileId = parts[2]
            val expectedSha256 = parts[3]
            val simulatedJwt = parts[4]

            // Real OkHttpClient with ZERO mock interceptors talking over localhost TCP socket
            val realHttpClient = okhttp3.OkHttpClient.Builder().build()
            assertTrue("OkHttpClient must have no interceptors", realHttpClient.interceptors.isEmpty())

            val repo = com.example.data.RetrofitSatelliteRasterBackendRepository(
                environment = com.example.data.BackendEnvironment.LOCAL_EMULATOR,
                baseUrl = "http://127.0.0.1:$port/",
                okHttpClient = realHttpClient,
                tokenProvider = com.example.data.FirebaseIdTokenProvider { simulatedJwt }
            )

            val outcome = kotlinx.coroutines.runBlocking {
                repo.downloadOutputGeoTiffWithSignedUrlRefresh(
                    fileId = fileId,
                    expectedIndexName = "NDVI",
                    expectedSha256 = expectedSha256
                )
            }
            assertTrue(
                "Expected real HTTP Python->Android GeoTIFF download to succeed: ${outcome.exceptionOrNull()}",
                outcome.isSuccess
            )
            val (downloaded, signedUrl) = outcome.getOrThrow()
            assertEquals(fileId, signedUrl.fileId)
            assertEquals("NDVI", signedUrl.indexName)
            assertTrue(signedUrl.signedDownloadUrl.contains("signature="))
            assertEquals(expectedSha256, downloaded.sha256Checksum)

            val band = downloaded.parsedBand
            assertEquals("NDVI", band.bandName)
            assertEquals(4, band.width)
            assertEquals(3, band.height)
            assertEquals(32636, band.crsEpsg)
            assertEquals(305000.0, band.originX, 0.01)
            assertEquals(3365000.0, band.originY, 0.01)
            assertEquals(10.0, band.pixelWidth, 0.001)
            assertEquals(-10.0, band.pixelHeight, 0.001)
            assertNotNull("Expected GDAL_NODATA tag (42113) to be decoded", band.nodataValue)
            assertEquals(-9999.0f, band.nodataValue!!, 0.01f)

            // Verify pixel grid values
            assertEquals(0.75f, band.grid[0][0], 0.0001f)
            assertEquals(0.72f, band.grid[0][1], 0.0001f)
            assertEquals(0.68f, band.grid[0][2], 0.0001f)
            assertEquals(0.64f, band.grid[0][3], 0.0001f)
            assertEquals(0.71f, band.grid[1][0], 0.0001f)
            assertEquals(0.35f, band.grid[1][1], 0.0001f)
            assertEquals(0.31f, band.grid[1][2], 0.0001f)
            assertEquals(0.66f, band.grid[1][3], 0.0001f)
            assertEquals(0.69f, band.grid[2][0], 0.0001f)
            assertEquals(0.67f, band.grid[2][1], 0.0001f)
            assertEquals(0.65f, band.grid[2][2], 0.0001f)
            assertEquals(0.62f, band.grid[2][3], 0.0001f)
        } finally {
            runCatching {
                process.outputStream.write("\n".toByteArray())
                process.outputStream.flush()
                process.outputStream.close()
            }
            if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h1400dp")
    fun `TASK 4 Compose UI level verification of GeoTIFF download panel visibility click callback disabled loading success and error states`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val baseVm = buildTestViewModel(app)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val sampleOutputDescriptor = com.example.data.RasterOutputFileDescriptor(
            fileId = "file_ui_test_ndre",
            indexName = "NDRE",
            downloadEndpoint = "/api/v1/satellite/files/file_ui_test_ndre",
            signedUrlEndpoint = "/api/v1/satellite/files/file_ui_test_ndre/signed-url",
            sha256Checksum = "3767c9e896aaa553a5923686c658de4851aae6420bc756cf25c4ad1dca7911a3",
            sizeBytes = 352L,
            contentType = "image/tiff",
            crsEpsg = 32636,
            spatialResolutionMeters = 10f
        )

        val baseRasterResult = com.example.data.GeoreferencedRasterBackendResult(
            resultId = "res_ui_test_01",
            jobId = "job_ui_test_01",
            farmId = "farm_1",
            ownerUid = "farmer_1",
            sceneId = "S2A_36RUV_20260501_L2A",
            cropId = "wheat",
            executionState = com.example.data.RasterPipelineExecutionState.COMPLETED_LIVE_GEOREFERENCED,
            isLiveGeoreferencedRaster = true,
            crsEpsg = 32636,
            spatialResolutionMeters = 10f,
            totalPixels = 12,
            polygonPixels = 12,
            validPixels = 12,
            maskedCloudPixels = 0,
            validPixelRatio = 1.0f,
            ndviMean = 0.65f,
            ndviStdDev = 0.08f,
            ndreMean = 0.36f,
            ndmiMean = 0.28f,
            psriMean = 0.07f,
            anomalyPixelCount = 2,
            anomalyAreaFeddans = 0.05f,
            anomalyZonesGeoJson = """{"type":"FeatureCollection","features":[]}""",
            pixelGrid = emptyList(),
            vegetationStressDetectionAr = "رصد طيفي طبيعي",
            agriculturalRiskAssessmentAr = "مخاطر منخفضة",
            confirmedDiseaseDiagnosisStatusAr = "يتطلب فحصاً حقلياً",
            statusSummaryAr = "تحليل مكتمل",
            jobStatusHistory = listOf("QUEUED", "COMPLETED"),
            outputFiles = mapOf("NDRE" to sampleOutputDescriptor)
        )

        val clickedIndices = mutableListOf<String>()
        val uiStateHolder = androidx.compose.runtime.mutableStateOf(
            baseVm.uiState.value.copy(
                activeSpectralRadarLayer = com.example.data.SpectralRadarLayer.NDRE,
                rasterBackendResult = baseRasterResult,
                isDownloadingGeoTiffRaster = false,
                downloadingRasterIndexName = null,
                lastDownloadedGeoTiffSummaryAr = null,
                rasterDownloadErrorAr = null
            )
        )

        composeTestRule.setContent {
            com.example.ui.theme.AgroSightTheme {
                com.example.ui.screens.SatelliteDiseaseRadarScreen(
                    uiState = uiStateHolder.value,
                    onSelectSpectralLayer = {},
                    onSelectAnomalyPattern = {},
                    onSendProactiveWhatsAppAlert = {},
                    onRunGeminiDiagnosis = {},
                    onDownloadGeoTiffRaster = { indexCode ->
                        clickedIndices.add(indexCode)
                    }
                )
            }
        }

        // Scroll the LazyColumn to bring geotiff_raster_download_panel into view
        composeTestRule.onNodeWithTag("disease_radar_screen_list")
            .performScrollToNode(hasTestTag("geotiff_raster_download_panel"))

        // 1. Panel visibility when outputFiles are available
        composeTestRule.onNodeWithTag("geotiff_raster_download_panel")
            .assertIsDisplayed()

        // 2. Download button visibility and enabled state
        composeTestRule.onNodeWithTag("download_geotiff_raster_button")
            .assertIsDisplayed()
            .assertIsEnabled()

        // 3. Clicking the button invokes the expected callback
        composeTestRule.onNodeWithTag("download_geotiff_raster_button")
            .performClick()
        assertEquals(listOf("NDRE"), clickedIndices)

        // 4. Button disabled during download & loading state rendered
        composeTestRule.runOnIdle {
            uiStateHolder.value = uiStateHolder.value.copy(
                isDownloadingGeoTiffRaster = true,
                downloadingRasterIndexName = "NDRE"
            )
        }
        composeTestRule.onNodeWithTag("disease_radar_screen_list")
            .performScrollToNode(hasTestTag("geotiff_download_loading_status"))
        composeTestRule.onNodeWithTag("download_geotiff_raster_button")
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag("geotiff_download_loading_status")
            .assertIsDisplayed()

        // 5a. Success state renders correctly
        val successSummary = "تم تحميل وفحص ملف GeoTIFF (NDRE • 4×3 px • EPSG:32636 • الحقل: farm_1 • النتيجة: res_ui_test_01)"
        composeTestRule.runOnIdle {
            uiStateHolder.value = uiStateHolder.value.copy(
                isDownloadingGeoTiffRaster = false,
                downloadingRasterIndexName = null,
                lastDownloadedGeoTiffSummaryAr = successSummary,
                rasterDownloadErrorAr = null
            )
        }
        composeTestRule.onNodeWithTag("disease_radar_screen_list")
            .performScrollToNode(hasTestTag("geotiff_download_success_summary"))
        composeTestRule.onNodeWithTag("download_geotiff_raster_button")
            .assertIsEnabled()
        composeTestRule.onNodeWithTag("geotiff_download_success_summary")
            .assertIsDisplayed()
            .assertTextContains("EPSG:32636", substring = true)

        // 5b. Error state renders correctly
        val errorMsg = "تعذر تحميل ملف GeoTIFF: انتهت صلاحية الرابط الموقّع"
        composeTestRule.runOnIdle {
            uiStateHolder.value = uiStateHolder.value.copy(
                isDownloadingGeoTiffRaster = false,
                lastDownloadedGeoTiffSummaryAr = null,
                rasterDownloadErrorAr = errorMsg
            )
        }
        composeTestRule.onNodeWithTag("disease_radar_screen_list")
            .performScrollToNode(hasTestTag("geotiff_download_error_status"))
        composeTestRule.onNodeWithTag("geotiff_download_error_status")
            .assertIsDisplayed()
            .assertTextContains("تعذر تحميل ملف GeoTIFF", substring = true)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h2200dp")
    fun phaseA_farmerFirstDashboard_rendersWelcomeHealthQuickActionsSimplifiedInsightsAndActivity() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val baseVm = buildTestViewModel(app)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val navigatedScreens = mutableListOf<com.example.model.AppScreen>()
        val selectedFields = mutableListOf<String>()
        var refreshTelemetryCount = 0
        var printReportCount = 0

        val sampleFields = listOf(
            com.example.data.FieldEntity(
                id = "field_toshka_01",
                farmerId = "farmer_01",
                fieldName = "مزرعة توشكى النموذجية - قطاع أ",
                lat = 22.85,
                lon = 31.52,
                areaFeddans = 120f,
                cropType = "قمح",
                plantingDate = "2025-11-15",
                irrigationType = "رش محوري",
                flowRateM3Hr = 18f
            ),
            com.example.data.FieldEntity(
                id = "field_sadat_02",
                farmerId = "farmer_01",
                fieldName = "مزرعة السادات الذكية - حقل البطاطس",
                lat = 30.38,
                lon = 30.52,
                areaFeddans = 65f,
                cropType = "بطاطس",
                plantingDate = "2025-12-01",
                irrigationType = "تنقيط",
                flowRateM3Hr = 16f
            )
        )

        val initialState = baseVm.uiState.value.copy(
            fieldId = "field_toshka_01",
            fieldName = "مزرعة توشكى النموذجية - قطاع أ",
            areaFeddans = 120f,
            dbSnapshot = baseVm.uiState.value.dbSnapshot.copy(
                fields = sampleFields
            )
        )

        composeTestRule.setContent {
            com.example.ui.theme.AgroSightTheme {
                com.example.ui.screens.HomeDashboardScreen(
                    uiState = initialState,
                    onNavigateToScreen = { navigatedScreens.add(it) },
                    onSelectField = { selectedFields.add(it) },
                    onOpenAuthModal = {},
                    onSendMorningWhatsAppReport = {},
                    onRefreshTelemetry = { refreshTelemetryCount++ },
                    onOpenPrintReport = { printReportCount++ }
                )
            }
        }

        // Section A — Welcome Section & Farm/Field Switcher
        composeTestRule.onNodeWithTag("farmer_welcome_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("farmer_farm_switcher_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_field_chip_field_sadat_02")
            .assertIsDisplayed()
            .performClick()
        assertEquals(listOf("field_sadat_02"), selectedFields)

        // Section B — Farm Health Overview (4 simple status cards)
        composeTestRule.onNodeWithTag("farm_health_overview_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("farmer_health_status_crop").assertIsDisplayed()
        composeTestRule.onNodeWithTag("farmer_health_status_irrigation").assertIsDisplayed()
        composeTestRule.onNodeWithTag("farmer_health_status_satellite").assertIsDisplayed()
        composeTestRule.onNodeWithTag("farmer_health_status_alerts").assertIsDisplayed()

        // Section C — 5 Quick Action Cards
        composeTestRule.onNodeWithTag("farmer_quick_actions_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_action_analyze_farm").assertIsDisplayed().performClick()
        assertEquals(1, refreshTelemetryCount)
        assertTrue(navigatedScreens.contains(com.example.model.AppScreen.DISEASE_RADAR))

        composeTestRule.onNodeWithTag("quick_action_satellite_map").assertIsDisplayed().performClick()
        assertTrue(navigatedScreens.contains(com.example.model.AppScreen.SATELLITE_NDVI_MAP))

        composeTestRule.onNodeWithTag("quick_action_irrigation_recommendations").assertIsDisplayed().performClick()
        assertTrue(navigatedScreens.contains(com.example.model.AppScreen.IRRIGATION_SCHEDULE))

        composeTestRule.onNodeWithTag("quick_action_crop_health_report").assertIsDisplayed().performClick()
        assertEquals(1, printReportCount)

        composeTestRule.onNodeWithTag("quick_action_weather_info").assertIsDisplayed().performClick()
        assertTrue(navigatedScreens.contains(com.example.model.AppScreen.METEO_ANALYTICS))

        // Section D — Simplified Satellite Insights (NDVI, NDRE, NDMI, PSRI) + Technical View Toggle
        composeTestRule.onNodeWithTag("home_overview_lazy_column")
            .performScrollToNode(hasTestTag("farmer_satellite_insights_section"))
        composeTestRule.onNodeWithTag("farmer_satellite_insights_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("satellite_insight_ndvi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("satellite_insight_ndre").assertIsDisplayed()
        composeTestRule.onNodeWithTag("satellite_insight_ndmi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("satellite_insight_psri").assertIsDisplayed()
        composeTestRule.onNodeWithTag("satellite_insights_technical_toggle")
            .assertIsDisplayed()
            .performClick()

        // Section E — Real Farm Activity Timeline
        composeTestRule.onNodeWithTag("home_overview_lazy_column")
            .performScrollToNode(hasTestTag("farmer_activity_timeline_section"))
        composeTestRule.onNodeWithTag("farmer_activity_timeline_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_item_recent_analysis").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_item_irrigation_recommendation").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_item_farm_alert").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_item_saved_report").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h2200dp")
    fun phaseB_persistentFloatingAssistant_modeSwitching_rtlLtr_errorRetry_and_noOverlap() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        // 0. Strict Color Preservation Verification: FarmerFirstTokens reuses exact AgroSight Color.kt tokens
        assertEquals(com.example.ui.theme.ForestBgDark, com.example.ui.components.FarmerFirstTokens.SurfacePrimary)
        assertEquals(com.example.ui.theme.ForestCardDark, com.example.ui.components.FarmerFirstTokens.SurfaceElevated)
        assertEquals(com.example.ui.theme.ForestCardElevated, com.example.ui.components.FarmerFirstTokens.SurfaceHighlight)
        assertEquals(com.example.ui.theme.ForestCardBorder, com.example.ui.components.FarmerFirstTokens.CardBorder)
        assertEquals(com.example.ui.theme.EmeraldPrimary, com.example.ui.components.FarmerFirstTokens.CardBorderStrong)
        assertEquals(com.example.ui.theme.EmeraldPrimary, com.example.ui.components.FarmerFirstTokens.HealthyGreen)
        assertEquals(com.example.ui.theme.EmeraldSoft, com.example.ui.components.FarmerFirstTokens.HealthyBg)
        assertEquals(com.example.ui.theme.SkyAccent, com.example.ui.components.FarmerFirstTokens.IrrigationBlue)
        assertEquals(com.example.ui.theme.SkySoft, com.example.ui.components.FarmerFirstTokens.IrrigationBg)
        assertEquals(com.example.ui.theme.AmberWarning, com.example.ui.components.FarmerFirstTokens.ModerateAmber)
        assertEquals(com.example.ui.theme.AmberSoft, com.example.ui.components.FarmerFirstTokens.ModerateBg)
        assertEquals(com.example.ui.theme.RoseDanger, com.example.ui.components.FarmerFirstTokens.CriticalRed)
        assertEquals(com.example.ui.theme.RoseSoft, com.example.ui.components.FarmerFirstTokens.CriticalBg)
        assertEquals(com.example.ui.theme.MintTextPrimary, com.example.ui.components.FarmerFirstTokens.TextPrimary)
        assertEquals(com.example.ui.theme.MintTextSoft, com.example.ui.components.FarmerFirstTokens.TextSecondary)
        assertEquals(com.example.ui.theme.MintTextMuted, com.example.ui.components.FarmerFirstTokens.TextMuted)

        // Configurable fake Gemini service to test Farmer Mode, Technical Mode, Error, and Retry
        var shouldThrowNext = false
        val capturedModes = mutableListOf<com.example.data.AssistantResponseMode>()
        val realServiceForFallback = com.example.data.GeminiAgroService()
        val controllableGemini = object : AgroAiCopilotDataSource {
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
            ): String = "توصية استرشادية"

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
            ): String = "رسالة واتساب"

            override suspend fun generateCustomWorkerMessage(
                prompt: String,
                fallbackText: String
            ): String = fallbackText

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
                responseMode: com.example.data.AssistantResponseMode
            ): GeminiGroundedResponse {
                capturedModes.add(responseMode)
                if (shouldThrowNext) {
                    throw IllegalStateException("Simulated Gemini temporary network interruption")
                }
                val modePrefix = if (responseMode == com.example.data.AssistantResponseMode.FARMER) {
                    "🌾 *[وضع المزارع المبسط — ملخص عملي مباشر]*\n"
                } else {
                    "🔬 *[الوضع الفني المتقدم — تحليل المؤشرات ومنهجية FAO-56]*\n"
                }
                val body = realServiceForFallback.buildSmartEgyptianArabicFallbackReply(
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
                    responseMode = responseMode
                )
                return GeminiGroundedResponse(
                    replyText = modePrefix + body,
                    citations = emptyList(),
                    isLiveAiGenerated = false
                )
            }
        }

        val dbService = DatabaseService()
        val viewModel = AgroSightViewModel(
            app,
            AgroSightDependencies(
                databaseService = dbService,
                openMeteoClient = FakeMeteoDataSource(),
                openSourceSatelliteClient = FakeSatelliteDataSource(),
                satelliteRasterBackendRepository = FakeSatelliteRasterBackendRepository(),
                geminiAgroService = controllableGemini,
                cloudWorker = AgroSightCloudWorker(dbService, controllableGemini),
                firestoreRepository = FakeCloudWorkspaceRepository(),
                sqlAuditDao = FakeSqlFieldAuditDao()
            )
        )
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        composeTestRule.setContent {
            com.example.ui.theme.AgroSightTheme {
                AgroSightApp(viewModel = viewModel)
            }
        }

        // 1. Floating assistant visibility on HOME screen & non-overlap with bottom navigation and content
        assertEquals(com.example.model.AppScreen.HOME, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("floating_ask_agrosight_button")
            .assertIsDisplayed()
            .assertTextContains("اسأل AgroSight", substring = true)

        val fabBoundsHome = composeTestRule.onNodeWithTag("floating_ask_agrosight_button")
            .fetchSemanticsNode().boundsInRoot
        val bottomNavBoundsHome = composeTestRule.onNodeWithTag("bottom_nav_bar")
            .fetchSemanticsNode().boundsInRoot
        val mainContentBoundsHome = composeTestRule.onNodeWithTag("main_screen_content_box")
            .fetchSemanticsNode().boundsInRoot

        assertFalse(
            "Floating assistant button must not overlap bottom navigation bar on HOME",
            fabBoundsHome.overlaps(bottomNavBoundsHome)
        )
        assertFalse(
            "Floating assistant dock must not overlap main screen content box on HOME",
            fabBoundsHome.overlaps(mainContentBoundsHome)
        )

        // 2. Navigate to SATELLITE_NDVI_MAP and verify floating assistant remains visible and does not overlap map controls
        composeTestRule.runOnIdle {
            viewModel.selectScreen(com.example.model.AppScreen.SATELLITE_NDVI_MAP)
        }
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.AppScreen.SATELLITE_NDVI_MAP, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("floating_ask_agrosight_button")
            .assertIsDisplayed()

        val fabBoundsMap = composeTestRule.onNodeWithTag("floating_ask_agrosight_button")
            .fetchSemanticsNode().boundsInRoot
        val bottomNavBoundsMap = composeTestRule.onNodeWithTag("bottom_nav_bar")
            .fetchSemanticsNode().boundsInRoot
        val mapContentBounds = composeTestRule.onNodeWithTag("main_screen_content_box")
            .fetchSemanticsNode().boundsInRoot

        assertFalse(
            "Floating assistant button must not overlap bottom navigation bar on SATELLITE_NDVI_MAP",
            fabBoundsMap.overlaps(bottomNavBoundsMap)
        )
        assertFalse(
            "Floating assistant button must not overlap satellite map controls container",
            fabBoundsMap.overlaps(mapContentBounds)
        )

        // 3. Opening and closing the chat interface via the floating assistant button
        composeTestRule.onNodeWithTag("floating_ask_agrosight_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.AppScreen.CHATBOT, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("chatbot_screen").assertIsDisplayed()
        assertEquals(
            "Floating assistant button should hide while already inside CHATBOT screen",
            0,
            composeTestRule.onAllNodesWithTag("floating_ask_agrosight_button").fetchSemanticsNodes().size
        )

        // Close chat and return to SATELLITE_NDVI_MAP
        composeTestRule.onNodeWithTag("chatbot_close_return_button")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.AppScreen.SATELLITE_NDVI_MAP, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("floating_ask_agrosight_button").assertIsDisplayed()

        // Re-open chat from floating button
        composeTestRule.onNodeWithTag("floating_ask_agrosight_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.AppScreen.CHATBOT, viewModel.uiState.value.currentScreen)

        // 4. Verify all 4 mandatory suggested questions are present and scrollable in the quick prompts strip
        composeTestRule.onNodeWithTag("chatbot_messages_list")
            .performScrollToNode(hasTestTag("chatbot_quick_prompt_0"))
        composeTestRule.onNodeWithTag("chatbot_quick_prompt_0")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("هل المحصول عندي بحالة جيدة؟", substring = true)
        composeTestRule.onNodeWithTag("chatbot_quick_prompt_1")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("هل الأرض محتاجة ري؟", substring = true)
        composeTestRule.onNodeWithTag("chatbot_quick_prompt_2")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("اشرحلي تحليل الأقمار الصناعية.", substring = true)
        composeTestRule.onNodeWithTag("chatbot_quick_prompt_3")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("ما أهم التنبيهات في مزرعتي؟", substring = true)

        // 5. Farmer Mode vs Technical Mode switching
        composeTestRule.onNodeWithTag("chatbot_messages_list")
            .performScrollToNode(hasTestTag("chatbot_farm_grounding_card"))
        assertEquals(com.example.data.AssistantResponseMode.FARMER, viewModel.uiState.value.assistantResponseMode)
        composeTestRule.onNodeWithTag("chatbot_active_mode_banner")
            .assertIsDisplayed()
            .assertTextContains("وضع المزارع المبسط نشط", substring = true)

        // Send a question in Farmer Mode by clicking suggested question 0
        val countBeforeFarmerMsg = viewModel.uiState.value.geminiChatMessages.size
        composeTestRule.onNodeWithTag("chatbot_messages_list")
            .performScrollToNode(hasTestTag("chatbot_quick_prompt_0"))
        composeTestRule.onNodeWithTag("chatbot_quick_prompt_0")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.uiState.value.isGeminiChatLoading &&
                viewModel.uiState.value.geminiChatMessages.size >= countBeforeFarmerMsg + 2
        }
        composeTestRule.waitForIdle()
        assertTrue(capturedModes.contains(com.example.data.AssistantResponseMode.FARMER))
        val farmerReply = viewModel.uiState.value.geminiChatMessages.last()
        assertEquals(com.example.data.AssistantResponseMode.FARMER, farmerReply.responseMode)
        assertTrue(farmerReply.text.contains("وضع المزارع المبسط"))

        // Switch to Technical Mode (scroll up to grounding card first)
        composeTestRule.onNodeWithTag("chatbot_messages_list")
            .performScrollToNode(hasTestTag("chatbot_farm_grounding_card"))
        composeTestRule.onNodeWithTag("chatbot_mode_technical_button")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.data.AssistantResponseMode.TECHNICAL, viewModel.uiState.value.assistantResponseMode)
        composeTestRule.onNodeWithTag("chatbot_active_mode_banner")
            .assertIsDisplayed()
            .assertTextContains("الوضع الفني المتقدم نشط", substring = true)
            .assertTextContains("FAO-56", substring = true)

        // Send an English LTR question in Technical Mode to verify Technical reply + English LTR message bubble direction
        val countBeforeTechMsg = viewModel.uiState.value.geminiChatMessages.size
        composeTestRule.onNodeWithTag("chatbot_input_field")
            .performTextInput("Explain the NDVI, NDRE, NDMI, PSRI values and FAO-56 irrigation calculation.")
        composeTestRule.onNodeWithTag("chatbot_send_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.uiState.value.isGeminiChatLoading &&
                viewModel.uiState.value.geminiChatMessages.size >= countBeforeTechMsg + 2
        }
        composeTestRule.waitForIdle()

        assertTrue(capturedModes.contains(com.example.data.AssistantResponseMode.TECHNICAL))
        val messagesAfterTech = viewModel.uiState.value.geminiChatMessages
        val englishUserMsg = messagesAfterTech[messagesAfterTech.size - 2]
        val technicalAssistantMsg = messagesAfterTech.last()
        assertEquals(com.example.data.AssistantResponseMode.TECHNICAL, technicalAssistantMsg.responseMode)
        assertTrue(technicalAssistantMsg.text.contains("NDVI"))
        assertTrue(technicalAssistantMsg.text.contains("NDRE"))
        assertTrue(technicalAssistantMsg.text.contains("NDMI"))
        assertTrue(technicalAssistantMsg.text.contains("PSRI"))
        assertTrue(technicalAssistantMsg.text.contains("FAO-56"))
        assertTrue(technicalAssistantMsg.text.contains("حدود البيانات وعدم اليقين"))

        // 6. Verify Arabic RTL and English LTR per-message layout tags and screen layout toggle
        assertEquals(
            androidx.compose.ui.unit.LayoutDirection.Rtl,
            com.example.ui.screens.detectMessageLayoutDirection("هل المحصول عندي بحالة جيدة؟")
        )
        assertEquals(
            androidx.compose.ui.unit.LayoutDirection.Ltr,
            com.example.ui.screens.detectMessageLayoutDirection(englishUserMsg.text)
        )
        composeTestRule.onNodeWithTag("chatbot_messages_list")
            .performScrollToNode(hasTestTag("chatbot_message_${englishUserMsg.id}"))
        composeTestRule.onNodeWithTag("chatbot_message_dir_ltr_${englishUserMsg.id}", useUnmergedTree = true)
            .assertExists()
        composeTestRule.onNodeWithTag("chatbot_messages_list")
            .performScrollToNode(hasTestTag("chatbot_message_${technicalAssistantMsg.id}"))
        composeTestRule.onNodeWithTag("chatbot_message_dir_rtl_${technicalAssistantMsg.id}", useUnmergedTree = true)
            .assertExists()

        // Toggle screen layout direction RTL <-> LTR
        composeTestRule.onNodeWithTag("chatbot_messages_list")
            .performScrollToNode(hasTestTag("chatbot_farm_grounding_card"))
        composeTestRule.onNodeWithTag("chatbot_layout_direction_toggle")
            .assertIsDisplayed()
            .assertTextContains("AR/RTL", substring = true)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chatbot_layout_direction_toggle")
            .assertTextContains("EN/LTR", substring = true)
            .performClick()
        composeTestRule.waitForIdle()

        // 7. Error and Retry states
        shouldThrowNext = true
        composeTestRule.onNodeWithTag("chatbot_messages_list")
            .performScrollToNode(hasTestTag("chatbot_quick_prompt_1"))
        composeTestRule.onNodeWithTag("chatbot_quick_prompt_1")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.uiState.value.isGeminiChatLoading &&
                viewModel.uiState.value.geminiChatErrorAr != null
        }
        composeTestRule.waitForIdle()

        assertNotNull(viewModel.uiState.value.geminiChatErrorAr)
        composeTestRule.onNodeWithTag("chatbot_messages_list")
            .performScrollToNode(hasTestTag("chatbot_error_banner"))
        composeTestRule.onNodeWithTag("chatbot_error_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chatbot_retry_button").assertIsDisplayed()

        // Retry succeeds when network recovers
        shouldThrowNext = false
        val countBeforeRetry = viewModel.uiState.value.geminiChatMessages.size
        composeTestRule.onNodeWithTag("chatbot_retry_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.uiState.value.isGeminiChatLoading &&
                viewModel.uiState.value.geminiChatErrorAr == null &&
                viewModel.uiState.value.geminiChatMessages.size == countBeforeRetry + 1
        }
        composeTestRule.waitForIdle()

        assertEquals(null, viewModel.uiState.value.geminiChatErrorAr)
        assertEquals(countBeforeRetry + 1, viewModel.uiState.value.geminiChatMessages.size)
    }

    @Test
    fun `Phase C AgriculturalContextProvider deterministically retrieves farm, field, satellite, historical, FAO-56, weather, and activity context without PII leakage`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = buildTestViewModel(app)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val geminiService = com.example.data.GeminiAgroService()
        val refDate = java.time.LocalDate.parse("2026-05-03")

        // 1. Selected Farm & Field Context Retrieval + PII Exclusion Check
        val initialFieldId = viewModel.uiState.value.fieldId
        val secondFieldId = viewModel.uiState.value.dbSnapshot.fields
            .first { it.id != initialFieldId }.id

        val ctxField1 = viewModel.buildCurrentAgriculturalContext(
            currentQuery = "هل المحصول عندي بحالة جيدة؟",
            referenceDate = refDate
        )
        assertEquals(viewModel.uiState.value.activeFarmer.id, ctxField1.farm.farmId)
        assertEquals(viewModel.uiState.value.activeFarmer.fullName, ctxField1.farm.farmerDisplayName)
        assertEquals(viewModel.uiState.value.activeFarmer.region, ctxField1.farm.regionName)
        assertEquals(initialFieldId, ctxField1.farm.fieldId)
        assertEquals(viewModel.uiState.value.fieldName, ctxField1.farm.fieldName)
        assertEquals(viewModel.uiState.value.areaFeddans, ctxField1.farm.areaFeddans, 0.01f)
        assertEquals(viewModel.uiState.value.selectedCrop.nameAr, ctxField1.farm.cropNameAr)
        assertTrue(ctxField1.farm.plantingDateIso.isNotBlank())
        assertTrue(ctxField1.farm.growthStageNameAr.isNotBlank())

        val piiState = viewModel.uiState.value.copy(
            activeFarmer = viewModel.uiState.value.activeFarmer.copy(
                nationalId = "28501011234567",
                whatsappPhone = "+201098765432",
                gmailAddress = "private.farmer.pii@example.com"
            )
        )
        val piiCtx = com.example.data.AgriculturalContextProvider.buildContext(
            uiState = piiState,
            referenceDate = refDate,
            currentQuery = "هل المحصول عندي بحالة جيدة؟"
        )
        val promptBlock = piiCtx.toStructuredGeminiPromptBlock()
        assertFalse(
            "Gemini prompt block must NOT leak farmer nationalId",
            promptBlock.contains("28501011234567")
        )
        assertFalse(
            "Gemini prompt block must NOT leak farmer whatsappPhone",
            promptBlock.contains("+201098765432")
        )
        assertFalse(
            "Gemini prompt block must NOT leak farmer gmailAddress",
            promptBlock.contains("private.farmer.pii@example.com")
        )

        // 2. Switching Selected Field updates Field, Crop, Area, Coordinates, and Field-Scoped Records
        viewModel.selectFieldById(secondFieldId)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val ctxField2 = viewModel.buildCurrentAgriculturalContext(
            currentQuery = "اشرحلي تحليل الأقمار الصناعية.",
            referenceDate = refDate
        )
        assertEquals(secondFieldId, ctxField2.farm.fieldId)
        assertTrue(ctxField1.farm.fieldName != ctxField2.farm.fieldName)
        assertEquals(viewModel.uiState.value.fieldName, ctxField2.farm.fieldName)
        assertEquals(viewModel.uiState.value.areaFeddans, ctxField2.farm.areaFeddans, 0.01f)

        // Switch back to initialFieldId for detailed satellite, historical, irrigation, and weather verification
        viewModel.selectFieldById(initialFieldId)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // 3. Satellite Index Retrieval (NDVI, NDRE, NDMI, PSRI, scan date, anomaly, valve sectors)
        val sat = ctxField1.satellite
        assertTrue("Satellite data should be available for initial field", sat.isAvailable)
        assertEquals(viewModel.uiState.value.epidemiologyReport.ndviMean, sat.ndviMean, 0.001f)
        assertEquals(viewModel.uiState.value.epidemiologyReport.ndreMean, sat.ndreMean, 0.001f)
        assertEquals(viewModel.uiState.value.epidemiologyReport.ndmiMean, sat.ndmiMean, 0.001f)
        assertEquals(viewModel.uiState.value.epidemiologyReport.psriMean, sat.psriMean, 0.001f)
        assertNotNull("Satellite analysis date must be present", sat.analysisDateIso)
        assertEquals(viewModel.uiState.value.epidemiologyReport.detectedRiskNameAr, sat.detectedRiskNameAr)
        assertEquals(viewModel.uiState.value.epidemiologyReport.hotspotLocationAr, sat.hotspotLocationAr)
        assertEquals(viewModel.uiState.value.epidemiologyReport.affectedValveNumber, sat.affectedValveNumber)
        assertTrue("Valve sectors must be populated", sat.valveSectors.isNotEmpty())

        // 4. Historical Comparison Calculation from Actual Dated Scan Records
        val stateWithTwoScans = viewModel.uiState.value.let { st ->
            val existingScans = st.dbSnapshot.satelliteSpectralScans.filter { it.fieldId == initialFieldId }
            val latestScan = existingScans.first()
            val olderScan = latestScan.copy(
                id = "scan_hist_prev",
                scanDate = "2026-04-20",
                ndviMean = (latestScan.ndviMean - 0.06f).coerceAtLeast(0.10f),
                ndreMean = (latestScan.ndreMean - 0.04f).coerceAtLeast(0.08f),
                ndmiMean = (latestScan.ndmiMean - 0.05f).coerceAtLeast(0.05f),
                psriMean = (latestScan.psriMean + 0.03f)
            )
            val currentDatedScan = latestScan.copy(scanDate = "2026-05-01")
            st.copy(
                dbSnapshot = st.dbSnapshot.copy(
                    satelliteSpectralScans = listOf(currentDatedScan, olderScan)
                )
            )
        }
        val ctxWithHistory = com.example.data.AgriculturalContextProvider.buildContext(
            uiState = stateWithTwoScans,
            referenceDate = refDate,
            currentQuery = "قارن القراءة الحالية بالمسح التاريخي السابق"
        )
        val hist = ctxWithHistory.satellite.historicalComparison
        assertNotNull("Historical comparison must be computed when >= 2 dated scans exist", hist)
        assertEquals("2026-04-20", hist!!.previousScanDateIso)
        assertEquals(
            ctxWithHistory.satellite.ndviMean - hist.previousNdvi,
            hist.ndviDelta,
            0.001f
        )
        assertEquals(
            ctxWithHistory.satellite.ndreMean - hist.previousNdre,
            hist.ndreDelta,
            0.001f
        )
        val histReply = geminiService.buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = "قارن القراءة الحالية بالمسح التاريخي السابق",
            farmerName = ctxWithHistory.farm.farmerDisplayName,
            fieldName = ctxWithHistory.farm.fieldName,
            cropNameAr = ctxWithHistory.farm.cropNameAr,
            soilPh = ctxWithHistory.farm.soilPh,
            soilEc = ctxWithHistory.farm.soilEceDsM,
            et0MmDay = ctxWithHistory.irrigation.et0MmDay,
            kcValue = ctxWithHistory.irrigation.fusedKc,
            dailyM3PerFeddan = ctxWithHistory.irrigation.grossWaterM3PerFeddanDay,
            valveRunHours = ctxWithHistory.irrigation.valveOperatingHours,
            report = stateWithTwoScans.epidemiologyReport,
            responseMode = com.example.data.AssistantResponseMode.TECHNICAL,
            agriculturalContext = ctxWithHistory
        )
        assertTrue("Historical reply must cite previous scan date 2026-04-20", histReply.contains("2026-04-20"))
        assertTrue(
            "Historical reply must cite exact previous NDVI ${com.example.model.formatDecimal2(hist.previousNdvi)}",
            histReply.contains(com.example.model.formatDecimal2(hist.previousNdvi))
        )

        // 5. FAO-56 Irrigation Result Consistency & No Invented Numerical Values
        val irr = ctxWithHistory.irrigation
        assertTrue(irr.isAvailable)
        assertTrue(irr.et0MmDay > 0f)
        assertTrue(irr.fusedKc > 0f)
        assertTrue(irr.netEtcMmDay > 0f)
        assertTrue(irr.grossWaterM3PerFeddanDay > 0f)
        val irrReply = geminiService.buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = "هل الأرض محتاجة ري؟ وكم المقنن المائي وفق FAO-56؟",
            farmerName = ctxWithHistory.farm.farmerDisplayName,
            fieldName = ctxWithHistory.farm.fieldName,
            cropNameAr = ctxWithHistory.farm.cropNameAr,
            soilPh = ctxWithHistory.farm.soilPh,
            soilEc = ctxWithHistory.farm.soilEceDsM,
            et0MmDay = irr.et0MmDay,
            kcValue = irr.fusedKc,
            dailyM3PerFeddan = irr.grossWaterM3PerFeddanDay,
            valveRunHours = irr.valveOperatingHours,
            report = stateWithTwoScans.epidemiologyReport,
            responseMode = com.example.data.AssistantResponseMode.TECHNICAL,
            agriculturalContext = ctxWithHistory
        )
        assertTrue("Irrigation reply must match exact ET0", irrReply.contains(com.example.model.formatDecimal2(irr.et0MmDay)))
        assertTrue("Irrigation reply must match exact Kc", irrReply.contains(com.example.model.formatDecimal2(irr.fusedKc)))
        assertTrue("Irrigation reply must match exact ETc", irrReply.contains(com.example.model.formatDecimal2(irr.netEtcMmDay)))
        assertTrue("Irrigation reply must match exact gross water m3/feddan", irrReply.contains(com.example.model.formatDecimal1(irr.grossWaterM3PerFeddanDay)))
        assertTrue("Irrigation reply must match exact valve operating hours", irrReply.contains(com.example.model.formatDecimal1(irr.valveOperatingHours)))
        assertTrue("Irrigation reply must confirm schedule is not modified without user confirmation", irrReply.contains("تلقائياً"))

        // 6. Weather Context Retrieval
        val env = ctxWithHistory.environmental
        assertTrue("Environmental weather context should be available", env.isAvailable)
        val weatherReply = geminiService.buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = "ما حالة الطقس والحرارة والرياح والرطوبة اليوم؟",
            farmerName = ctxWithHistory.farm.farmerDisplayName,
            fieldName = ctxWithHistory.farm.fieldName,
            cropNameAr = ctxWithHistory.farm.cropNameAr,
            soilPh = ctxWithHistory.farm.soilPh,
            soilEc = ctxWithHistory.farm.soilEceDsM,
            et0MmDay = irr.et0MmDay,
            kcValue = irr.fusedKc,
            dailyM3PerFeddan = irr.grossWaterM3PerFeddanDay,
            valveRunHours = irr.valveOperatingHours,
            report = stateWithTwoScans.epidemiologyReport,
            responseMode = com.example.data.AssistantResponseMode.FARMER,
            agriculturalContext = ctxWithHistory
        )
        assertTrue("Weather reply must include exact max temperature", weatherReply.contains(com.example.model.formatDecimal1(env.tempMaxC)))
        assertTrue("Weather reply must include exact wind speed", weatherReply.contains(com.example.model.formatDecimal1(env.windSpeedKmh)))
        assertTrue("Weather reply must include exact relative humidity", weatherReply.contains("${env.relativeHumidityPct}%"))

        // 7. Farmer Mode vs Technical Mode & 4-Part Agricultural Safety Distinction
        val farmerHealthReply = geminiService.buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = "هل المحصول عندي بحالة جيدة؟",
            farmerName = ctxWithHistory.farm.farmerDisplayName,
            fieldName = ctxWithHistory.farm.fieldName,
            cropNameAr = ctxWithHistory.farm.cropNameAr,
            soilPh = ctxWithHistory.farm.soilPh,
            soilEc = ctxWithHistory.farm.soilEceDsM,
            et0MmDay = irr.et0MmDay,
            kcValue = irr.fusedKc,
            dailyM3PerFeddan = irr.grossWaterM3PerFeddanDay,
            valveRunHours = irr.valveOperatingHours,
            report = stateWithTwoScans.epidemiologyReport,
            responseMode = com.example.data.AssistantResponseMode.FARMER,
            agriculturalContext = ctxWithHistory
        )
        val technicalHealthReply = geminiService.buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = "هل المحصول عندي بحالة جيدة؟",
            farmerName = ctxWithHistory.farm.farmerDisplayName,
            fieldName = ctxWithHistory.farm.fieldName,
            cropNameAr = ctxWithHistory.farm.cropNameAr,
            soilPh = ctxWithHistory.farm.soilPh,
            soilEc = ctxWithHistory.farm.soilEceDsM,
            et0MmDay = irr.et0MmDay,
            kcValue = irr.fusedKc,
            dailyM3PerFeddan = irr.grossWaterM3PerFeddanDay,
            valveRunHours = irr.valveOperatingHours,
            report = stateWithTwoScans.epidemiologyReport,
            responseMode = com.example.data.AssistantResponseMode.TECHNICAL,
            agriculturalContext = ctxWithHistory
        )
        listOf(farmerHealthReply, technicalHealthReply).forEach { reply ->
            assertTrue("Reply must distinguish Satellite Observation", reply.contains("رصد القمر الصناعي"))
            assertTrue("Reply must distinguish Calculated Indicator", reply.contains("المؤشر المحسوب"))
            assertTrue("Reply must distinguish AI Interpretation", reply.contains("التفسير الذكي"))
            assertTrue("Reply must distinguish Agricultural Recommendation", reply.contains("التوصية الزراعية الإرشادية"))
            assertTrue("Reply must state spectral reading is not a certain disease diagnosis", reply.contains("تشخيص"))
            assertTrue("Reply must prohibit specific chemical pesticide without field inspection", reply.contains("مبيد كيميائي"))
        }
        assertTrue("Technical mode reply must include FAO-56 and GeoTIFF/uncertainty details", technicalHealthReply.contains("FAO-56"))
    }

    @Test
    fun `Phase C missing and stale data handling, multi-turn spatial follow-up resolution, and recommended screen navigation work end-to-end`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val geminiService = com.example.data.GeminiAgroService()
        val viewModel = buildTestViewModel(app)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // 1. Missing Satellite Data Handling
        val missingSatState = viewModel.uiState.value.let { st ->
            st.copy(
                dbSnapshot = st.dbSnapshot.copy(satelliteSpectralScans = emptyList()),
                satelliteAndNasaTelemetry = null,
                rasterBackendResult = null,
                epidemiologyReport = st.epidemiologyReport.copy(
                    ndviMean = 0f,
                    ndreMean = 0f,
                    ndmiMean = 0f,
                    psriMean = 0f
                )
            )
        }
        val missingSatCtx = com.example.data.AgriculturalContextProvider.buildContext(
            uiState = missingSatState,
            referenceDate = java.time.LocalDate.parse("2026-05-10"),
            currentQuery = "اشرحلي تحليل الأقمار الصناعية لحقلي"
        )
        assertFalse("Satellite context must report isAvailable = false when no scans exist", missingSatCtx.satellite.isAvailable)
        assertEquals(com.example.model.AppScreen.DISEASE_RADAR, missingSatCtx.followUp.recommendedScreen)

        val missingSatReply = geminiService.buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = "اشرحلي تحليل الأقمار الصناعية لحقلي",
            farmerName = missingSatCtx.farm.farmerDisplayName,
            fieldName = missingSatCtx.farm.fieldName,
            cropNameAr = missingSatCtx.farm.cropNameAr,
            soilPh = missingSatCtx.farm.soilPh,
            soilEc = missingSatCtx.farm.soilEceDsM,
            et0MmDay = missingSatCtx.irrigation.et0MmDay,
            kcValue = missingSatCtx.irrigation.fusedKc,
            dailyM3PerFeddan = missingSatCtx.irrigation.grossWaterM3PerFeddanDay,
            valveRunHours = missingSatCtx.irrigation.valveOperatingHours,
            report = missingSatState.epidemiologyReport,
            responseMode = com.example.data.AssistantResponseMode.FARMER,
            agriculturalContext = missingSatCtx
        )
        assertTrue("Missing satellite reply must explicitly inform farmer data is unavailable", missingSatReply.contains("غير متوفر"))
        assertTrue("Missing satellite reply must state platform does not invent unrecorded numbers", missingSatReply.contains("لا تخترع أرقاماً"))
        assertTrue("Missing satellite reply must direct user to Disease Radar screen", missingSatReply.contains("رادار Sentinel-2 الطيفي"))

        // 2. Stale Satellite & Weather Data Handling
        val staleDateState = viewModel.uiState.value.let { st ->
            val oldScans = st.dbSnapshot.satelliteSpectralScans.map {
                it.copy(scanDate = "2026-03-01")
            }
            st.copy(
                dbSnapshot = st.dbSnapshot.copy(satelliteSpectralScans = oldScans),
                satelliteAndNasaTelemetry = null,
                isServingFromRoomOfflineCache = true
            )
        }
        val staleCtx = com.example.data.AgriculturalContextProvider.buildContext(
            uiState = staleDateState,
            referenceDate = java.time.LocalDate.parse("2026-05-15"),
            currentQuery = "هل المحصول عندي بحالة جيدة؟"
        )
        assertTrue("Satellite data older than 10 days must be flagged as stale", staleCtx.satellite.isStale)
        assertTrue("Satellite ageDays must be calculated accurately", (staleCtx.satellite.ageDays ?: 0L) > 10L)
        assertTrue("Weather served from offline cache must be flagged as stale", staleCtx.environmental.isStale)
        assertEquals(com.example.model.AppScreen.DISEASE_RADAR, staleCtx.followUp.recommendedScreen)

        val staleReply = geminiService.buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = "هل المحصول عندي بحالة جيدة؟",
            farmerName = staleCtx.farm.farmerDisplayName,
            fieldName = staleCtx.farm.fieldName,
            cropNameAr = staleCtx.farm.cropNameAr,
            soilPh = staleCtx.farm.soilPh,
            soilEc = staleCtx.farm.soilEceDsM,
            et0MmDay = staleCtx.irrigation.et0MmDay,
            kcValue = staleCtx.irrigation.fusedKc,
            dailyM3PerFeddan = staleCtx.irrigation.grossWaterM3PerFeddanDay,
            valveRunHours = staleCtx.irrigation.valveOperatingHours,
            report = staleDateState.epidemiologyReport,
            responseMode = com.example.data.AssistantResponseMode.FARMER,
            agriculturalContext = staleCtx
        )
        assertTrue("Stale reply must warn farmer about data age", staleReply.contains("تنبيه حداثة بيانات القمر الصناعي"))
        assertTrue("Stale reply must include exact old date 2026-03-01", staleReply.contains("2026-03-01"))

        // 3. Multi-Turn Follow-Up Context Resolution ("هل المحصول عندي كويس؟" -> "طيب والمنطقة اللي في الشمال؟" -> "طيب ومحبس 2؟")
        val initialHistory = listOf(
            com.example.data.GeminiAgronomyChatMessage(
                id = "u1",
                role = "USER",
                channel = "GEMINI_CHAT",
                text = "هل المحصول عندي كويس؟",
                timestamp = "10:00"
            ),
            com.example.data.GeminiAgronomyChatMessage(
                id = "a1",
                role = "ASSISTANT",
                channel = "GEMINI_CHAT",
                text = "محصولك بحالة جيدة مع ملاحظة في البؤرة المسجلة.",
                timestamp = "10:00"
            )
        )

        // Case 3A: Follow-up about "الشمال" when recorded hotspot is in "جنوب الحقل - محبس 2" (must NOT invent a northern anomaly!)
        val southHotspotState = viewModel.uiState.value.copy(
            epidemiologyReport = viewModel.uiState.value.epidemiologyReport.copy(
                hotspotLocationAr = "جنوب الحقل - قطاع محبس 2",
                affectedValveNumber = 2
            )
        )
        val unrecordedNorthFollowUpCtx = com.example.data.AgriculturalContextProvider.buildContext(
            uiState = southHotspotState,
            chatHistory = initialHistory,
            currentQuery = "طيب والمنطقة اللي في الشمال؟"
        )
        assertTrue(unrecordedNorthFollowUpCtx.followUp.isFollowUpQuestion)
        assertEquals("هل المحصول عندي كويس؟", unrecordedNorthFollowUpCtx.followUp.previousUserQuestion)
        assertEquals("الشمال", unrecordedNorthFollowUpCtx.followUp.referencedDirectionOrSubregionAr)
        assertFalse(
            "Must NOT claim verified spatial data for North when hotspot is in South",
            unrecordedNorthFollowUpCtx.followUp.hasVerifiedSpatialDataForSubregion
        )

        val unrecordedNorthReply = geminiService.buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = "طيب والمنطقة اللي في الشمال؟",
            farmerName = unrecordedNorthFollowUpCtx.farm.farmerDisplayName,
            fieldName = unrecordedNorthFollowUpCtx.farm.fieldName,
            cropNameAr = unrecordedNorthFollowUpCtx.farm.cropNameAr,
            soilPh = unrecordedNorthFollowUpCtx.farm.soilPh,
            soilEc = unrecordedNorthFollowUpCtx.farm.soilEceDsM,
            et0MmDay = unrecordedNorthFollowUpCtx.irrigation.et0MmDay,
            kcValue = unrecordedNorthFollowUpCtx.irrigation.fusedKc,
            dailyM3PerFeddan = unrecordedNorthFollowUpCtx.irrigation.grossWaterM3PerFeddanDay,
            valveRunHours = unrecordedNorthFollowUpCtx.irrigation.valveOperatingHours,
            report = southHotspotState.epidemiologyReport,
            responseMode = com.example.data.AssistantResponseMode.FARMER,
            agriculturalContext = unrecordedNorthFollowUpCtx
        )
        assertTrue(
            "Reply must explicitly state no separate directional record exists for North",
            unrecordedNorthReply.contains("لا يتوفر تقسيم مكاني منفصل مسجل لمنطقة (الشمال)")
        )
        assertTrue(
            "Reply must report actual recorded South hotspot without inventing a North anomaly",
            unrecordedNorthReply.contains("جنوب الحقل - قطاع محبس 2")
        )

        // Case 3B: Follow-up about "الشمال" when recorded hotspot IS in "شمال شرق الحقل - محبس 2"
        val northHotspotState = viewModel.uiState.value.copy(
            epidemiologyReport = viewModel.uiState.value.epidemiologyReport.copy(
                hotspotLocationAr = "شمال شرق الحقل - قطاع محبس 2",
                affectedValveNumber = 2
            )
        )
        val verifiedNorthFollowUpCtx = com.example.data.AgriculturalContextProvider.buildContext(
            uiState = northHotspotState,
            chatHistory = initialHistory,
            currentQuery = "طيب والمنطقة اللي في الشمال؟"
        )
        assertTrue(verifiedNorthFollowUpCtx.followUp.isFollowUpQuestion)
        assertTrue(verifiedNorthFollowUpCtx.followUp.hasVerifiedSpatialDataForSubregion)
        val verifiedNorthReply = geminiService.buildSmartEgyptianArabicFallbackReply(
            farmerQuestion = "طيب والمنطقة اللي في الشمال؟",
            farmerName = verifiedNorthFollowUpCtx.farm.farmerDisplayName,
            fieldName = verifiedNorthFollowUpCtx.farm.fieldName,
            cropNameAr = verifiedNorthFollowUpCtx.farm.cropNameAr,
            soilPh = verifiedNorthFollowUpCtx.farm.soilPh,
            soilEc = verifiedNorthFollowUpCtx.farm.soilEceDsM,
            et0MmDay = verifiedNorthFollowUpCtx.irrigation.et0MmDay,
            kcValue = verifiedNorthFollowUpCtx.irrigation.fusedKc,
            dailyM3PerFeddan = verifiedNorthFollowUpCtx.irrigation.grossWaterM3PerFeddanDay,
            valveRunHours = verifiedNorthFollowUpCtx.irrigation.valveOperatingHours,
            report = northHotspotState.epidemiologyReport,
            responseMode = com.example.data.AssistantResponseMode.FARMER,
            agriculturalContext = verifiedNorthFollowUpCtx
        )
        assertTrue(verifiedNorthReply.contains("شمال شرق الحقل - قطاع محبس 2"))

        // Case 3C: Follow-up about specific valve sector ("طيب ومحبس 2؟")
        val valveFollowUpCtx = com.example.data.AgriculturalContextProvider.buildContext(
            uiState = viewModel.uiState.value,
            chatHistory = initialHistory,
            currentQuery = "طيب ومحبس 2؟"
        )
        assertEquals(2, valveFollowUpCtx.followUp.referencedValveNumber)
        assertTrue(valveFollowUpCtx.followUp.hasVerifiedSpatialDataForSubregion)
        val sector2 = viewModel.uiState.value.valveSectors.first { it.valveNumber == 2 }
        assertTrue(
            valveFollowUpCtx.followUp.verifiedSubregionFindingAr!!.contains(
                com.example.model.formatDecimal2(sector2.ndvi)
            )
        )

        // 4. Compose UI Verification of Recommended Screen Navigation Button on Assistant Reply
        val dbService = DatabaseService(app)
        val realFallbackCopilot = object : AgroAiCopilotDataSource {
            override suspend fun generateMultiTurnGroundedAgronomyReply(
                chatHistory: List<com.example.data.GeminiAgronomyChatMessage>,
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
                report: com.example.data.EpidemiologyDiagnosisReport,
                responseMode: com.example.data.AssistantResponseMode
            ): com.example.data.GeminiGroundedResponse = generateMultiTurnGroundedAgronomyReply(
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
                chatHistory: List<com.example.data.GeminiAgronomyChatMessage>,
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
                report: com.example.data.EpidemiologyDiagnosisReport,
                responseMode: com.example.data.AssistantResponseMode,
                agriculturalContext: com.example.data.AgriculturalContextSnapshot?
            ): com.example.data.GeminiGroundedResponse {
                val text = geminiService.buildSmartEgyptianArabicFallbackReply(
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
                )
                return com.example.data.GeminiGroundedResponse(
                    replyText = text,
                    citations = emptyList(),
                    isLiveAiGenerated = false,
                    recommendedScreen = agriculturalContext?.followUp?.recommendedScreen,
                    recommendedScreenLabelAr = agriculturalContext?.followUp?.recommendedScreenLabelAr,
                    dataTimestampIso = agriculturalContext?.satellite?.analysisDateIso
                        ?: agriculturalContext?.generatedAtDateIso,
                    dataSourceModeLabelAr = agriculturalContext?.satellite?.dataMode?.badgeAr
                )
            }

            override suspend fun generateEpidemiologicalInsight(
                farmerName: String,
                fieldName: String,
                cropNameAr: String,
                report: com.example.data.EpidemiologyDiagnosisReport,
                soilPh: Float,
                soilEc: Float,
                tempMaxC: Float,
                humidityPct: Int,
                et0MmDay: Float
            ): String = "تحليل"

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
                report: com.example.data.EpidemiologyDiagnosisReport
            ): String = "رد"

            override suspend fun generateCustomWorkerMessage(prompt: String, fallbackText: String): String = fallbackText
        }

        val uiViewModel = AgroSightViewModel(
            app,
            AgroSightDependencies(
                databaseService = dbService,
                openMeteoClient = FakeMeteoDataSource(),
                openSourceSatelliteClient = FakeSatelliteDataSource(),
                satelliteRasterBackendRepository = FakeSatelliteRasterBackendRepository(),
                geminiAgroService = realFallbackCopilot,
                cloudWorker = AgroSightCloudWorker(dbService, realFallbackCopilot),
                firestoreRepository = FakeCloudWorkspaceRepository(),
                sqlAuditDao = FakeSqlFieldAuditDao()
            )
        )
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        composeTestRule.setContent {
            com.example.ui.theme.AgroSightTheme {
                AgroSightApp(viewModel = uiViewModel)
            }
        }

        // Open assistant and ask a spatial follow-up question that recommends SATELLITE_NDVI_MAP
        composeTestRule.onNodeWithTag("floating_ask_agrosight_button").performClick()
        composeTestRule.waitForIdle()
        val beforeCount = uiViewModel.uiState.value.geminiChatMessages.size
        composeTestRule.onNodeWithTag("chatbot_input_field")
            .performTextInput("طيب والمنطقة اللي في الشمال؟")
        composeTestRule.onNodeWithTag("chatbot_send_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            !uiViewModel.uiState.value.isGeminiChatLoading &&
                uiViewModel.uiState.value.geminiChatMessages.size >= beforeCount + 2
        }
        composeTestRule.waitForIdle()

        val lastAssistantMsg = uiViewModel.uiState.value.geminiChatMessages.last()
        assertEquals(com.example.model.AppScreen.SATELLITE_NDVI_MAP, lastAssistantMsg.recommendedScreen)

        composeTestRule.onNodeWithTag("chatbot_messages_list")
            .performScrollToNode(hasTestTag("chatbot_navigate_recommended_${lastAssistantMsg.id}"))
        composeTestRule.onNodeWithTag("chatbot_navigate_recommended_${lastAssistantMsg.id}")
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "Clicking recommended screen chip in chatbot reply must navigate to SATELLITE_NDVI_MAP",
            com.example.model.AppScreen.SATELLITE_NDVI_MAP,
            uiViewModel.uiState.value.currentScreen
        )
    }
}




