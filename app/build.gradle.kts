plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "tn.loukious.superproxyunlocker"
    compileSdk = 36
    defaultConfig {
        // Separate identity preserves the recovered legacy APK and its data.
        applicationId = "tn.loukious.superproxyunlocker.modern"
        minSdk = 27
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
    packaging { resources { merges += "META-INF/xposed/*" } }
}
base.archivesName.set("SuperProxyUnlocker-v${android.defaultConfig.versionName}")
dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("org.luckypray:dexkit:2.2.0")
    testImplementation("junit:junit:4.13.2")
}
