import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

object GoogleMapsKeyConfigValidator {
  fun readDotEnvPropertyFromContent(content: String?, key: String): String? {
    if (content.isNullOrBlank()) return null
    return content
      .lineSequence()
      .map { it.trim() }
      .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
      .mapNotNull { line ->
        val idx = line.indexOf('=')
        val k = line.substring(0, idx).trim()
        val v = line.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
        if (k == key) v else null
      }
      .firstOrNull()
      ?.takeIf { it.isNotEmpty() }
  }

  fun isValidGoogleMapsApiKeyFormat(candidate: String): Boolean {
    val trimmed = candidate.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.equals("MY_GOOGLE_MAPS_API_KEY", ignoreCase = true)) return false
    if (trimmed.equals("YOUR_API_KEY", ignoreCase = true)) return false
    if (trimmed.all { it.isDigit() }) return false
    return trimmed.startsWith("AIza") && trimmed.length >= 30
  }

  fun resolveAndValidateGoogleMapsApiKey(
    gradleProp: String?,
    envVar: String?,
    dotEnvVal: String?,
    dotEnvExampleVal: String?,
    requireValidKey: Boolean
  ): String {
    val rawCandidate =
      sequenceOf(gradleProp, envVar, dotEnvVal, dotEnvExampleVal)
        .mapNotNull { it?.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: "MY_GOOGLE_MAPS_API_KEY"

    val resolved =
      if (isValidGoogleMapsApiKeyFormat(rawCandidate)) {
        rawCandidate
      } else {
        "MY_GOOGLE_MAPS_API_KEY"
      }

    if (requireValidKey && !isValidGoogleMapsApiKeyFormat(rawCandidate)) {
      throw GradleException(
        "CRITICAL CONFIGURATION ERROR (AUD-01): GOOGLE_MAPS_API_KEY is mandatory when " +
          "AGROSIGHT_REQUIRE_GOOGLE_MAPS_KEY=true, but a valid Google Maps API key was not provided " +
          "via Gradle property (-PGOOGLE_MAPS_API_KEY=...), environment variable (GOOGLE_MAPS_API_KEY), or .env."
      )
    }
    return resolved
  }
}

abstract class VerifyGoogleMapsKeyConfigTask : DefaultTask() {
  @TaskAction
  fun executeVerification() {
    // 1. Missing key when enforcement is enabled -> must throw GradleException
    val missingFailed =
      try {
        GoogleMapsKeyConfigValidator.resolveAndValidateGoogleMapsApiKey(
          null,
          null,
          null,
          null,
          requireValidKey = true
        )
        false
      } catch (_: GradleException) {
        true
      }
    check(missingFailed) { "Expected GradleException when GOOGLE_MAPS_API_KEY is missing and requireValidKey=true" }

    // 2. Placeholder key when enforcement is enabled -> must throw GradleException
    val placeholderFailed =
      try {
        GoogleMapsKeyConfigValidator.resolveAndValidateGoogleMapsApiKey(
          "MY_GOOGLE_MAPS_API_KEY",
          null,
          "2317154",
          "MY_GOOGLE_MAPS_API_KEY",
          requireValidKey = true
        )
        false
      } catch (_: GradleException) {
        true
      }
    check(placeholderFailed) { "Expected GradleException when GOOGLE_MAPS_API_KEY is placeholder and requireValidKey=true" }

    // 3. Valid key format when enforcement is enabled -> must succeed
    val syntheticValid = "AIzaSyD-SyntheticValidationFormatTestKey123456"
    val resolvedValid =
      GoogleMapsKeyConfigValidator.resolveAndValidateGoogleMapsApiKey(
        gradleProp = syntheticValid,
        envVar = null,
        dotEnvVal = null,
        dotEnvExampleVal = "MY_GOOGLE_MAPS_API_KEY",
        requireValidKey = true
      )
    check(resolvedValid == syntheticValid) { "Expected valid key format to pass enforcement" }

    // 4. Development fallback when enforcement is disabled -> must return safe placeholder
    val devFallback =
      GoogleMapsKeyConfigValidator.resolveAndValidateGoogleMapsApiKey(
        gradleProp = null,
        envVar = null,
        dotEnvVal = null,
        dotEnvExampleVal = "MY_GOOGLE_MAPS_API_KEY",
        requireValidKey = false
      )
    check(devFallback == "MY_GOOGLE_MAPS_API_KEY") { "Expected safe development placeholder when requireValidKey=false" }
    println("AUD-01 verifyGoogleMapsKeyConfig PASSED (missing/placeholder rejected, valid accepted, dev fallback verified).")
  }
}

// Track .env and .env.example via providers.fileContents() so Gradle Configuration Cache
// automatically invalidates and re-evaluates when .env is created, modified, or removed.
val dotEnvFileContentProvider =
  providers.fileContents(rootProject.layout.projectDirectory.file(".env")).asText
val dotEnvExampleFileContentProvider =
  providers.fileContents(rootProject.layout.projectDirectory.file(".env.example")).asText

val unifiedGoogleMapsApiKey: String = run {
  val gradleProp = providers.gradleProperty("GOOGLE_MAPS_API_KEY").orNull
  val envVar = providers.environmentVariable("GOOGLE_MAPS_API_KEY").orNull
  val dotEnvContent = dotEnvFileContentProvider.orNull
  val dotEnvExampleContent = dotEnvExampleFileContentProvider.orNull
  val dotEnvVal = GoogleMapsKeyConfigValidator.readDotEnvPropertyFromContent(dotEnvContent, "GOOGLE_MAPS_API_KEY")
  val dotEnvExampleVal = GoogleMapsKeyConfigValidator.readDotEnvPropertyFromContent(dotEnvExampleContent, "GOOGLE_MAPS_API_KEY")

  val requireGoogleMapsKey =
    (providers.gradleProperty("AGROSIGHT_REQUIRE_GOOGLE_MAPS_KEY").orNull
        ?: providers.environmentVariable("AGROSIGHT_REQUIRE_GOOGLE_MAPS_KEY").orNull
        ?: GoogleMapsKeyConfigValidator.readDotEnvPropertyFromContent(dotEnvContent, "AGROSIGHT_REQUIRE_GOOGLE_MAPS_KEY")
        ?: "false")
      .trim()
      .equals("true", ignoreCase = true)

  GoogleMapsKeyConfigValidator.resolveAndValidateGoogleMapsApiKey(
    gradleProp = gradleProp,
    envVar = envVar,
    dotEnvVal = dotEnvVal,
    dotEnvExampleVal = dotEnvExampleVal,
    requireValidKey = requireGoogleMapsKey
  )
}

tasks.register<VerifyGoogleMapsKeyConfigTask>("verifyGoogleMapsKeyConfig") {
  group = "verification"
  description = "Verifies AUD-01 Google Maps API key resolution and enforcement for missing, placeholder, and valid keys."
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.agrosight.vmpqrx"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = unifiedGoogleMapsApiKey
    buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"$unifiedGoogleMapsApiKey\"")
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.systemProperty(
          "robolectric.dependency.repo.url",
          "https://maven-central.storage-download.googleapis.com/maven2"
        )
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
// GOOGLE_MAPS_API_KEY is ignored by Secrets Plugin because it is deterministically
// injected into both manifestPlaceholders and BuildConfig by unifiedGoogleMapsApiKey above.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("GOOGLE_MAPS_API_KEY")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.play.services.maps)
  implementation(libs.maps.compose)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
