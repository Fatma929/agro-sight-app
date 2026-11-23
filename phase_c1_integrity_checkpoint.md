# AgroSight — Phase C.1 Integrity Checkpoint & Read-Only Audit

## 1. Executive Summary & Audit Scope

Before modifying any source files, build scripts, or configurations, a complete read-only audit of the existing AgroSight project (Android Kotlin + Jetpack Compose + Room + Firebase + Python FastAPI raster service) was performed.

All completed work from **Phase 1**, **Phase A**, **Phase B**, and **Phase C** is preserved intact. Consistent with the non-negotiable visual restriction, **`Color.kt`**, **`Theme.kt`**, **`Type.kt`**, **`FarmerFirstDesignSystem.kt`**, and all color resources are frozen and will not be modified.

---

## 2. Read-Only Audit Findings

### 2.1 Build System, Gradle Version, AGP Version, and Missing Gradle Wrapper (Part 1 & Part 2)
- **`gradle/wrapper/gradle-wrapper.properties`**:
  - Configured for **`gradle-9.3.1-bin.zip`** (`distributionUrl=https\://services.gradle.org/distributions/gradle-9.3.1-bin.zip`).
- **`gradle/libs.versions.toml`**:
  - Android Gradle Plugin (`agp`): **`9.1.1`**
  - Kotlin (`kotlin`): **`2.2.10`**
  - KSP (`googleDevtoolsKsp`): **`2.3.5`**
  - Robolectric (`robolectric`): **`4.16.1`**
  - Firebase BoM (`firebaseBom`): **`34.17.0`**
- **`settings.gradle.kts` / `build.gradle.kts` / `app/build.gradle.kts`**:
  - Single-module Android application (`:app`) with `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`, Java 17 toolchain, Jetpack Compose, Room + KSP, and Firebase dependencies.
- **Defect Identified**:
  - `gradlew` (POSIX wrapper script), `gradlew.bat` (Windows wrapper script), and `gradle/wrapper/gradle-wrapper.jar` (wrapper bootstrap JAR) are missing from the repository root and `gradle/wrapper/`.
  - `.github/workflows/android-ci.yml` invokes raw `gradle` instead of `./gradlew` with `chmod +x ./gradlew`.

### 2.2 Demo Data Leakage into Real Farmer Accounts (Part 3)
- **`DatabaseService.kt`**:
  1. `loginOrRegisterByWhatsApp()` (lines 679–714) registers a new real farmer (`isDemoAccount = false`) and immediately injects:
     - A fabricated `SatelliteSpectralScanEntity` (`ndviMean = 0.68f, ndreMean = 0.34f, psriMean = 0.19f, ndmiMean = 0.36f`) with fake disease detection `"بؤرة مبكرة لمرض اللفحة المتأخرة (Phytophthora infestans)"`.
     - A fabricated `WhatsAppMessageEntity` marked `status = "DELIVERED"` containing an automatic chemical pesticide prescription (`"رش بؤرة المحبس 3 فقط بمبيد مانكوزيب + ميتالاكسيل بمعدل وقائي..."`).
  2. `buildEgyptianWhatsAppReportText()` (line 1049) defaults its `preventiveRecommendation` parameter to an automatic chemical pesticide prescription (`"رش بؤرة المحبس 3 فقط (0.4 فدان) بمبيد فطري متخصص (ميتالاكسيل + مانكوزيب)..."`).
  3. `scan_admin_1` (lines 274 & 833) on `farmer_admin_owner` uses a disease-specific label `"رصد مبكر لبؤرة الندوة المتأخرة (Phytophthora infestans)"` rather than a neutral spectral anomaly observation.
  4. `registerOrSyncCloudFarmerWorkspace()` (lines 773–783) unconditionally creates a `SatelliteSpectralScanEntity` even when incoming cloud workspace spectral indices are all `0f` (no scan performed yet).
- **`AgroSightViewModel.kt`**:
  - In `applyTenantSnapshotToUiState()` (line 510) and `selectFieldById()` (line 950), scan lookup performs:
    `snap.satelliteSpectralScans.find { it.fieldId == field.id } ?: snap.satelliteSpectralScans.first()`
    When a newly registered real farmer (`isDemoAccount = false`) has no satellite scan for their field, `.first()` leaks another tenant's/demo scan into the real farmer's active state, and `SatelliteEpidemiologyEngine.evaluateFieldEpidemiology()` generates simulated `0.68 / 0.34 / 0.19 / 0.36` spectral indices and fungal risk alerts.

### 2.3 Weather Data Provenance & Initial Live Connection Flag (Part 4)
- **`AgroSightViewModel.kt`**:
  - `AgroSightUiState.isLiveMeteoConnected` defaults to `true` (line 134), and `AgroSightViewModel.init` constructs `initialState` using `fallbackMeteo = defaultFallbackMeteo.buildFallbackForecast(...)` without copying `isLiveMeteoConnected = fallbackMeteo.isLiveConnected` (`false`) or `meteoStatusAr = fallbackMeteo.statusMessageAr`. As a result, fallback weather data is initially marked as `isLiveMeteoConnected = true` / `LIVE`.
- **`OpenMeteoClient.kt`**:
  - `TelemetryDataMode` currently defines only `LIVE`, `CACHED`, and `DEMO_ESTIMATED`. It needs explicit states for `LIVE`, `CACHED`, `FALLBACK`, `DEMO`, and `UNAVAILABLE` (while retaining `DEMO_ESTIMATED` compatibility), plus structured provenance fields on `OpenMeteoResult`: `source`, `observationTimestampIso`, `retrievalTimestampIso`, `units`, and timestamp validation that rejects malformed dates rather than silently treating them as fresh.

### 2.4 Firebase Restore-Before-Sync Ordering & Cross-User Isolation (Part 5)
- **`AgroSightViewModel.kt`**:
  - In `observeFirebaseAuthAndCloudStreams()` (lines 1910–1947), as soon as `observeAuthState()` emits a non-null `FirebaseUser`, line 1919 immediately invokes `syncCurrentWorkspaceToFirestore()` **before** fetching or restoring the user's cloud workspace from Firestore.
  - This risks overwriting an existing user's cloud workspace in Firestore with uninitialized local state on login or account switch.
  - Furthermore, `syncCurrentWorkspaceToFirestore()` must be gated so that local-to-cloud writes are blocked until initial cloud restoration for `user.uid` has completed (or confirmed empty cloud state on first login), and `ws.userId == user.uid` ownership is strictly validated.

### 2.5 Agricultural Context & Scientific Data Provenance (Part 6)
- **`AgriculturalContextProvider.kt` & `GeminiAgroService.kt`**:
  - Each context section (`satellite`, `irrigation`, `environmental`) must carry explicit provenance metadata: `source`, `timestampIso`, `unitsSummary`, `dataStatus` (`LIVE`, `CACHED`, `FALLBACK`, `DEMO`, `MISSING`, `STALE`, `UNAVAILABLE`), `qualitySummaryAr`, `cloudCoverPct`, and `historicalAvailabilityAr`.
  - When a real farmer account has no satellite scan (`scan == null` and no live STAC scene), `AgriculturalContextProvider` must report `satellite.isAvailable = false` and `dataStatus = MISSING / UNAVAILABLE` rather than falling back to simulated `epidemiologyReport` numbers.

### 2.6 Satellite Index Formulas & Documentation Alignment (Part 7)
- **Code Implementation Verification**:
  - Android `Sentinel2BandMathEngine` (`OpenSourceSatelliteClient.kt` lines 77–108) and Python `backend/raster_service.py` (lines 1923–1958) implement identical scientific formulas:
    - $\text{NDVI} = \frac{B08 - B04}{B08 + B04}$
    - $\text{NDRE} = \frac{B8A - B05}{B8A + B05}$
    - $\text{NDMI} = \frac{B08 - B11}{B08 + B11}$
    - $\text{PSRI} = \frac{B04 - B02}{B06}$
- **Documentation / Label Discrepancy Found**:
  - In `DatabaseService.kt` lines 64–65, `SpectralRadarLayer.PSRI` displays `fullTitleAr = "PSRI (مؤشر تلف وشيخوخة الأنسجة - B4-B3/B6)"` and `bandFormula = "(B4 - B3) / B6"`, which erroneously references `B3` instead of `B02` (`(B04 - B02) / B06`).

### 2.7 Firestore Test Infrastructure & Configuration/Secret Safety (Part 8 & Part 9)
- **Firestore Rules Test Infrastructure**:
  - `firestore.test.js` exists and uses `@firebase/rules-unit-testing` and Node's built-in test runner (`node:test`), and `firebase.json` configures the Firestore emulator on `127.0.0.1:8085`, but no root `package.json` exists to declare `@firebase/rules-unit-testing`, `firebase-tools`, Node engine requirements, or reproducible npm test scripts.
- **Configuration & Secret Safety**:
  - `backend/app.py` defines `DEFAULT_FIREBASE_PROJECT_ID = "gen-lang-client-0938368516"`. When `FIREBASE_PROJECT_ID` is not explicitly set in the environment or when service-account credentials / signing secrets are absent, `/api/v1/raster/health` and startup diagnostics must explicitly report `using_fallback_firebase_project_id`, `service_account_configured`, `google_services_json_present`, and `production_ready` status so fallback defaults are never mistaken for production readiness.

---

## 3. Controlled Repair Plan

1. **Part 2 (Gradle Wrapper)**: Generate official Gradle 9.3.1 wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) matching `gradle/wrapper/gradle-wrapper.properties`, set executable permissions on `gradlew`, update `.github/workflows/android-ci.yml` to use `./gradlew`, and execute `./gradlew --version`, `./gradlew tasks`, `./gradlew test`, and `./gradlew assembleDebug`.
2. **Part 3 (Demo Data Isolation)**: Remove fabricated satellite scans, fake `Phytophthora infestans` detections, automatic Mancozeb/Metalaxyl chemical recommendations, and fake `DELIVERED` WhatsApp messages from real farmer registration and non-demo flows in `DatabaseService.kt` and `AgroSightViewModel.kt`. Keep demo fixtures strictly isolated to `isDemoAccount = true` demo profiles.
3. **Part 4 (Weather Data Provenance)**: Fix `AgroSightUiState.isLiveMeteoConnected` initialization to `false` when using fallback data, expand `TelemetryDataMode` and `OpenMeteoResult` with explicit provenance (`LIVE`, `CACHED`, `FALLBACK`, `DEMO`, `UNAVAILABLE`, `source`, `observationTimestampIso`, `retrievalTimestampIso`, `units`), and enforce timestamp validation and freshness checks.
4. **Part 5 (Firebase Restore-Before-Sync)**: Add one-shot `fetchFarmerWorkspace(userId)` to `CloudWorkspaceRepository`, refactor `observeFirebaseAuthAndCloudStreams()` in `AgroSightViewModel.kt` to restore and validate ownership (`workspace.userId == user.uid`) **before** allowing any local-to-cloud sync, isolate local state across UID switches, and preserve local state safely on offline/interrupted restore.
5. **Part 6 & Part 7 (Context Provenance & Formula Parity)**: Enrich `AgriculturalContextProvider.kt` and `GeminiAgroService.kt` with full section-level provenance metadata, fix `SpectralRadarLayer.PSRI` formula documentation in `DatabaseService.kt` to `(B04 - B02) / B06`, and add deterministic Android/Python formula parity tests.
6. **Part 8 & Part 9 (Firestore `package.json` & Config Diagnostics)**: Add root `package.json` for Firestore rules testing and enhance backend/Android configuration diagnostics for fallback Firebase project IDs, `google-services.json`, and signing secrets.
7. **Part 10 & Part 11 (Verification & Deliverables)**: Execute all runnable test suites (`./gradlew test`, `./gradlew assembleDebug`, Python backend unittest suite, and Firestore emulator test check) and produce `phase_c1_implementation_report.md` and `phase_c1_test_report.md`.
