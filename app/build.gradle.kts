plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}                                           
val rssFeeds: String = System.getenv("WYRIX_RSS_FEEDS") ?: (project.findProperty("wyrix.rssFeeds") as String?) ?: ""
val currentsApiKey: String = System.getenv("CURRENTS_API_KEY")?.trim().takeUnless { it.isNullOrBlank() } ?: (project.findProperty("wyrix.currentsApiKey") as String?)?.trim() ?: ""
val apitubeApiKey: String = System.getenv("APITUBE_API_KEY")?.trim().takeUnless { it.isNullOrBlank() } ?: (project.findProperty("wyrix.apitubeApiKey") as String?)?.trim() ?: ""
val fnioApiKey: String = System.getenv("FNIO_API_KEY")?.trim().takeUnless { it.isNullOrBlank() } ?: (project.findProperty("wyrix.fnioApiKey") as String?)?.trim() ?: ""
// Wyrix News AdMob ids. Release builds use these (override with ADMOB_APP_ID / ADMOB_BANNER_ID env vars).
val admobAppIdRelease: String = System.getenv("ADMOB_APP_ID")?.trim().takeUnless { it.isNullOrBlank() } ?: "ca-app-pub-5602764660213350~4827514676"
val admobBannerRelease: String = System.getenv("ADMOB_BANNER_ID")?.trim().takeUnless { it.isNullOrBlank() } ?: "ca-app-pub-5602764660213350/9682866477"
// Google's public TEST ids: debug builds always use these so you never see/click live ads while developing.
val admobAppIdTest = "ca-app-pub-3940256099942544~3347511713"
val admobBannerTest = "ca-app-pub-3940256099942544/6300978111"
val fnaApiKey: String = System.getenv("FNA_API_KEY")?.trim().takeUnless { it.isNullOrBlank() } ?: (project.findProperty("wyrix.fnaApiKey") as String?)?.trim() ?: ""

android {
    namespace = "com.wyrix.news"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wyrix.news"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 3
        versionName = "1.0.3"
        buildConfigField("String", "CURRENTS_API_KEY", "\"$currentsApiKey\"")
        buildConfigField("String", "APITUBE_API_KEY", "\"$apitubeApiKey\"")
        buildConfigField("String", "FNIO_API_KEY", "\"$fnioApiKey\"")
        buildConfigField("String", "FNA_API_KEY", "\"$fnaApiKey\"")
        buildConfigField(
            "String",
            "RSS_FEEDS",
            "\"" + rssFeeds.replace("\\", "\\\\").replace("\"", "\\\"") + "\"",
        )
    }

    val keystorePath: String? = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = admobAppIdTest
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerTest\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = admobAppIdRelease
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerRelease\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.browser:browser:1.8.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
