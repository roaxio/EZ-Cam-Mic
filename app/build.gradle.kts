import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// Release signing: read keystore.properties (project root) if present, else environment
// variables. If neither is set, the release build is produced unsigned.
val keystoreProps = Properties().apply {
  val f = rootProject.file("keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(propKey: String, envKey: String): String? =
  keystoreProps.getProperty(propKey) ?: System.getenv(envKey)

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_PATH")
val hasReleaseSigning = releaseStoreFile != null

android {
  namespace = "io.github.roaxio.ezcammic"
  compileSdk { version = release(37) }

  defaultConfig {
    applicationId = "io.github.roaxio.ezcammic"
    minSdk = 24
    targetSdk = 36
    // Single version source: APP_VERSION_CODE / APP_VERSION_NAME in gradle.properties.
    versionCode = providers.gradleProperty("APP_VERSION_CODE").get().toInt()
    versionName = providers.gradleProperty("APP_VERSION_NAME").get()
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = rootProject.file(releaseStoreFile!!)
        storePassword = signingValue("storePassword", "STORE_PASSWORD")
        keyAlias = signingValue("keyAlias", "KEY_ALIAS") ?: "upload"
        keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
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
      // Robolectric 4.17 needs access to JDK internals (FileDescriptor) on JDK 17+.
      all {
        it.jvmArgs(
          "--add-opens=java.base/java.io=ALL-UNNAMED",
          "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        )
      }
    }
  }
  packaging {
    // BouncyCastle 1.80+ ships the same LICENSE.md in bcprov, bcpkix and bcutil; keep one copy.
    resources { pickFirsts += "META-INF/LICENSE.md" }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.bcpkix)
  implementation(libs.bcprov)

  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.robolectric)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
