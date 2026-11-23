# AgroSight — Phase 1 External Service Verification Plan

**Status in Container Test Environment:** `NOT EXECUTED` (Automated CI/container environment operates without live production Firebase credentials or outbound Copernicus/Planetary Computer Sentinel-2 credentials. All automated tests in `backend/test_raster_service.py` and `ExampleRobolectricTest.kt` use explicitly labeled simulated JWT authentication and synthetic GeoTIFF fixtures.)

This document provides the deterministic, step-by-step manual verification procedure for validating **Real Firebase Authentication** and **Real Sentinel-2 / STAC Raster Processing** in a staging or production deployment environment before live field release.

---

## Part A — Real Firebase Authentication & Multi-Tenant Authorization Verification

### Prerequisites
1. A configured Firebase project with **Firebase Authentication** (Google Sign-In / Email-Password enabled) and **Cloud Firestore** provisioned.
2. Two distinct test accounts in Firebase Auth:
   - **User A (Farm Owner):** `uid = <USER_A_UID>`, owner of `farm_id = "farm_verify_alpha"`.
   - **User B (Other Farmer):** `uid = <USER_B_UID>`, owner of `farm_id = "farm_verify_beta"` (no access to `"farm_verify_alpha"`).
   - **Admin User:** `uid = <ADMIN_UID>` with custom claim `{"admin": true}` or `{"role": "admin"}`.
3. Backend server started in `STAGING` or `PRODUCTION` mode with real Firebase Auth token verification enabled:
   ```bash
   export AGROSIGHT_ENV=STAGING
   export AGROSIGHT_STORAGE_ROOT=/var/lib/agrosight/storage
   export AGROSIGHT_SIGNED_URL_SECRET="<32+ byte production HMAC secret>"
   export GOOGLE_CLOUD_PROJECT="<firebase-project-id>"
   ```

### Scenario A1 — Valid Authenticated User (Owner Access)
1. Sign in as **User A** on the Android app (or via Firebase Auth REST API) and obtain a fresh Firebase ID Token (`<TOKEN_USER_A>`).
2. Submit an analysis or signed-URL request for `farm_verify_alpha`:
   ```bash
   curl -i -X POST "https://<backend-host>/api/v1/satellite/files/<FILE_ID_FARM_ALPHA>/signed-url" \
     -H "Authorization: Bearer <TOKEN_USER_A>" \
     -H "Content-Type: application/json" \
     -d '{"ttl_seconds": 300}'
   ```
3. **Expected Result:**
   - HTTP `200 OK`.
   - JSON payload contains `file_id`, `download_url` with `expires=<unix_epoch>&sig=<hmac_sha256>`, `sha256_checksum`, `crs_epsg`, and `bbox`.
4. Perform a `GET` request to the returned `download_url`:
   ```bash
   curl -i "https://<backend-host><download_url_path_and_query>" -o downloaded_ndvi.tif
   ```
5. **Expected Result:**
   - HTTP `200 OK`, `Content-Type: image/tiff`.
   - `sha256sum downloaded_ndvi.tif` matches `sha256_checksum` from Step 3.

### Scenario A2 — Expired Firebase ID Token Rejection
1. Obtain a Firebase ID Token (`<EXPIRED_TOKEN_USER_A>`) and wait until its `exp` claim has passed (or use a captured token >1 hour old).
2. Request a signed URL using the expired token:
   ```bash
   curl -i -X POST "https://<backend-host>/api/v1/satellite/files/<FILE_ID_FARM_ALPHA>/signed-url" \
     -H "Authorization: Bearer <EXPIRED_TOKEN_USER_A>" \
     -H "Content-Type: application/json" \
     -d '{"ttl_seconds": 300}'
   ```
3. **Expected Result:**
   - HTTP `401 Unauthorized` (or `403 Forbidden` with structured error code `AUTH_TOKEN_EXPIRED` / `INVALID_ID_TOKEN`).
   - No signed URL is issued.

### Scenario A3 — Unauthorized Cross-Farm Access Rejection (Tenant Isolation)
1. Sign in as **User B** (`<TOKEN_USER_B>`), who does **not** own `farm_verify_alpha`.
2. Attempt to request a signed URL for `farm_verify_alpha`'s raster file (`<FILE_ID_FARM_ALPHA>`):
   ```bash
   curl -i -X POST "https://<backend-host>/api/v1/satellite/files/<FILE_ID_FARM_ALPHA>/signed-url" \
     -H "Authorization: Bearer <TOKEN_USER_B>" \
     -H "Content-Type: application/json" \
     -d '{"ttl_seconds": 300}'
   ```
3. **Expected Result:**
   - HTTP `403 Forbidden`.
   - Error response indicates the authenticated user does not have access to `farm_verify_alpha`.

### Scenario A4 — Admin Role Access Verification
1. Sign in as **Admin User** (`<TOKEN_ADMIN>`) possessing the Firebase custom claim `admin: true` (or `role: "admin"`).
2. Request a signed URL for `farm_verify_alpha`'s raster file (`<FILE_ID_FARM_ALPHA>`):
   ```bash
   curl -i -X POST "https://<backend-host>/api/v1/satellite/files/<FILE_ID_FARM_ALPHA>/signed-url" \
     -H "Authorization: Bearer <TOKEN_ADMIN>" \
     -H "Content-Type: application/json" \
     -d '{"ttl_seconds": 300}'
   ```
3. **Expected Result:**
   - HTTP `200 OK` with a valid signed download URL, confirming administrative read/audit access works as designed.

---

## Part B — Real Sentinel-2 / STAC Discovery, Band Download, Cloud Masking & Index GeoTIFF Verification

### Prerequisites
1. Network access from the backend host to the Element84 Earth Search STAC API (`https://earth-search.aws.element84.com/v1/search`) or Microsoft Planetary Computer STAC API (`https://planetarycomputer.microsoft.com/api/stac/v1/search`).
2. A known agricultural field polygon with low-to-moderate cloud cover, e.g., Nile Delta / Beheira Governorate (`bbox = [30.4500, 30.8200, 30.4650, 30.8350]`).

### Step B1 — Discover a Real Sentinel-2 L2A Scene via STAC
1. Query the STAC endpoint for `sentinel-2-l2a` scenes intersecting the target farm polygon over the last 30 days:
   ```bash
   curl -s -X POST "https://earth-search.aws.element84.com/v1/search" \
     -H "Content-Type: application/json" \
     -d '{
       "collections": ["sentinel-2-l2a"],
       "bbox": [30.4500, 30.8200, 30.4650, 30.8350],
       "datetime": "2025-01-01T00:00:00Z/2025-02-01T23:59:59Z",
       "limit": 5
     }' | jq '.features[0] | {id, datetime: .properties.datetime, cloud_cover: .properties["eo:cloud_cover"], epsg: .properties["proj:epsg"]}'
   ```

### Step B2 — Verify Scene Metadata
1. Confirm the returned STAC item includes:
   - Non-empty scene ID (e.g., `S2A_36RUV_202501..._L2A` or `S2B_...`).
   - Acquisition timestamp (`properties.datetime`).
   - Scene cloud cover percentage (`properties["eo:cloud_cover"]`, ideally `< 30.0`).
   - Native UTM EPSG code (`properties["proj:epsg"]`, e.g., `32636` for UTM Zone 36N).
   - Asset HREF links for bands `B02` (Blue), `B04` (Red), `B05` (Red-Edge 1), `B08` (NIR), `B11` (SWIR 1), and `SCL` (Scene Classification Layer).

### Step B3 — Download Required Spectral Bands & SCL Band
1. Trigger the backend analysis pipeline (`POST /api/v1/satellite/analyze`) for the target farm polygon so the STAC provider fetches the windowed COG subsets for `B02`, `B04`, `B05`, `B08`, `B11`, and `SCL`.
2. Confirm that 20m bands (`B05`, `B11`, `SCL`) are co-registered and resampled to the 10m target grid (`B02`, `B04`, `B08`) in the scene's UTM projection (`EPSG:32636`).

### Step B4 — Verify SCL Cloud Masking Behavior
1. Inspect the returned `cloud_stats` object in the analysis result JSON:
   - `total_pixels`, `valid_clear_pixels`, `cloud_masked_pixels`, `shadow_masked_pixels`, `nodata_pixels`.
   - Verify `valid_pixel_ratio = valid_clear_pixels / total_pixels`.
   - Confirm that pixels with SCL classes `0` (No Data), `1` (Saturated/Defective), `3` (Cloud Shadow), `8` (Cloud Medium Probability), `9` (Cloud High Probability), `10` (Thin Cirrus), and `11` (Snow/Ice) are masked out (`NoData = -9999.0`) before computing spectral indices.

### Step B5 — Generate NDVI, NDRE, NDMI, and PSRI Rasters
1. Confirm the analysis job completes with `status = "COMPLETED"` and produces `output_files` descriptors for all four spectral indices:
   - **NDVI:** `(B08 - B04) / (B08 + B04)` — valid range `[-1.0, 1.0]`.
   - **NDRE:** `(B08 - B05) / (B08 + B05)` — valid range `[-1.0, 1.0]`.
   - **NDMI:** `(B08 - B11) / (B08 + B11)` — valid range `[-1.0, 1.0]`.
   - **PSRI:** `(B04 - B02) / B05` — valid range `[-2.0, 2.0]`.

### Step B6 — Verify Resulting GeoTIFF Metadata & Android Round-Trip
1. For each index (`NDVI`, `NDRE`, `NDMI`, `PSRI`), request a signed URL (`POST /api/v1/satellite/files/{file_id}/signed-url`) and download the binary GeoTIFF (`GET /api/v1/satellite/files/{file_id}?expires=...&sig=...`), or tap **"تحميل وفحص ملف GeoTIFF"** in the Android app's Satellite Disease Radar screen.
2. Verify each downloaded GeoTIFF file:
   - SHA-256 of the downloaded bytes matches `sha256_checksum` in the database and signed URL response.
   - TIFF header is valid little-endian (`II`, magic `42`), single-band `float32` (`SampleFormat = 3`, `BitsPerSample = 32`).
   - GeoTIFF tags match the scene's UTM CRS (`GeoKeyDirectoryTag` -> `EPSG:32636`), `ModelPixelScaleTag` (`10.0m × 10.0m`), `ModelTiepointTag` (upper-left UTM origin), and `GDAL_NODATA` (`"-9999.0"`).
   - Unmasked agricultural pixels fall within expected biophysical ranges and match the summary statistics (`mean`, `min`, `max`, `p10`, `p50`, `p90`) returned by the analysis endpoint.
