import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: keystore.properties (gitignored) or DELAYD_* env vars. See docs/RELEASE.md.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingValue(prop: String, env: String): String? = keystoreProps.getProperty(prop) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "DELAYD_KEYSTORE_FILE")

android {
    namespace = "app.delayd"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.delayd"
        minSdk = 26
        targetSdk = 36
        // single source of truth in gradle.properties; the version code is a plain monotonic integer
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()
    }

    signingConfigs {
        if (releaseStoreFile != null) create("release") {
            storeFile = rootProject.file(releaseStoreFile)
            storePassword = signingValue("storePassword", "DELAYD_KEYSTORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "DELAYD_KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "DELAYD_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false // no git info in the APK, keeps builds reproducible
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStoreFile != null) signingConfigs.getByName("release") else {
                // Local smoke builds only. A tag build without a real key must never produce an artifact.
                if (System.getenv("GITHUB_REF_TYPE") == "tag" || System.getenv("DELAYD_REQUIRE_RELEASE_KEY") == "true")
                    throw GradleException("No release keystore configured (keystore.properties / DELAYD_KEYSTORE_FILE)")
                logger.warn("WARNING: no release keystore configured, signing release with the DEBUG key. Do not publish this build.")
                signingConfigs.getByName("debug")
            }
        }
    }

    // F-Droid and Play: no Google-signed dependency metadata blob in the APK/AAB (also keeps builds reproducible)
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true // VERSION_NAME for the API User-Agent
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui-tooling-preview") // @Preview annotations only (no runtime code)
    debugImplementation("androidx.compose.ui:ui-tooling") // lets Android Studio render the @Previews

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in local unit tests
    testImplementation("org.json:json:20240303")
}
