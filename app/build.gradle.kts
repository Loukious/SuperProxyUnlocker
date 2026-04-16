plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "tn.loukious.superproxyunlocker"
    compileSdk = 35

    defaultConfig {
        applicationId = "tn.loukious.superproxyunlocker"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
    }
}

base.archivesName.set("SuperProxyUnlocker-v${android.defaultConfig.versionName}")

dependencies {
    compileOnly("com.github.deltazefiro:XposedBridge:main-SNAPSHOT")
    implementation(libs.androidx.core.ktx)
}