plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
}

// ── Deployment hosts ────────────────────────────────────────────────────────
// Override with -PNAME=value. Nothing secret is ever stored here; the Android
// client only talks to the public Worker API and the public Accounts endpoints.
fun httpsProperty(name: String, fallback: String): String =
  providers.gradleProperty(name).orElse(fallback).get().also {
    require(it.startsWith("https://") && it.endsWith("/") && !it.contains('"') && !it.contains('\\')) {
      "$name must be an HTTPS URL ending in /"
    }
  }

android {
  namespace = "com.gothwad.store"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.gothwad.store"
    minSdk = 24
    targetSdk = 36
    val suppliedCode = providers.gradleProperty("versionCode").orElse("1").get()
    versionCode = suppliedCode.toIntOrNull()?.also {
      require(it in 1..2100000000) { "versionCode must be between 1 and 2100000000" }
    } ?: error("versionCode must be an integer")
    versionName = providers.gradleProperty("versionName").orElse("1.0").get().also {
      require(it.matches(Regex("[0-9][0-9A-Za-z.+_-]{0,63}"))) { "Invalid versionName" }
    }

    buildConfigField("String", "STORE_API_BASE_URL", "\"${httpsProperty("STORE_API_BASE_URL", "https://store.gothwadtech.com/api/")}\"")
    buildConfigField("String", "CONSOLE_URL", "\"${httpsProperty("CONSOLE_URL", "https://console.gothwadtech.com/")}\"")
    buildConfigField("String", "STORE_WEB_URL", "\"${httpsProperty("STORE_WEB_URL", "https://store.gothwadtech.com/")}\"")
    buildConfigField("String", "ACCOUNTS_API_BASE_URL", "\"${httpsProperty("ACCOUNTS_API_BASE_URL", "https://accounts.gothwadtech.com/api/")}\"")
    buildConfigField("String", "ACCOUNTS_SIGNIN_URL", "\"${httpsProperty("ACCOUNTS_SIGNIN_URL", "https://accounts.gothwadtech.com/")}\"")
    buildConfigField("String", "SHIZUKU_DOWNLOAD_URL", "\"${httpsProperty("SHIZUKU_DOWNLOAD_URL", "https://shizuku.rikka.app/")}\"")
    buildConfigField("String", "OAUTH_CLIENT_ID", "\"${providers.gradleProperty("OAUTH_CLIENT_ID").orElse("gothwad-store").get()}\"")
    buildConfigField("String", "OAUTH_REDIRECT_URI", "\"gothwadstore://oauth/callback\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      // CI signs the unsigned outputs afterward, like GothwadTech/browser.
      // Optional local signing still supports the original environment variables.
      System.getenv("KEYSTORE_PATH")?.let { path ->
        storeFile = file(path)
        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: System.getenv("STORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (System.getenv("KEYSTORE_PATH") != null)
        signingConfigs.getByName("release") else null
    }
    // Use Android's generated debug keystore; no repo-local key required.
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  lint {
    // Shizuku/root/ADB install helpers are intentional; the app is distributed
    // through GitHub releases, not Play, and every privileged path is opt-in.
    disable += setOf("QueryAllPackagesPermission", "GoogleAppIndexingWarning")
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  // Privileged install back-ends (opt-in): Shizuku / Sui.
  implementation(libs.shizuku.api)
  implementation(libs.shizuku.provider)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation("com.squareup.okhttp3:mockwebserver:4.10.0")
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
