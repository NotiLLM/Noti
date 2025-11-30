import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.secretsGradlePlugin)
}

android {
    namespace = "org.muilab.notigpt"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.muilab.notigpt"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        val localProperties = Properties().apply {
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { load(it) }
            }
        }
        // in your app build.gradle
        buildConfigField("String", "N8N_UPDATE_NOTIFICATION_PATH", "\"webhook-test/update-notification\"")
        buildConfigField("String", "N8N_POST_NOTIFICATION_ACTION_PATH", "\"webhook-test/notification-action\"")
        buildConfigField("String", "N8N_TASK_SCAN_PATH", "\"webhook/task-scan\"")
        buildConfigField("String", "N8N_TASK_EXTRACTION_PATH", "\"webhook/task-extraction\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeCompiler {
        enableStrongSkippingMode = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.constraintlayout)

    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.tooling.preview)
    implementation(libs.material3)
    implementation(libs.runtime.livedata)
    implementation(libs.compose.material.icons.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Room
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.paging)
    implementation(libs.room.ktx)

    // Paging
    implementation(libs.paging.runtime.ktx)
    implementation(libs.paging.compose)

    // OpenAI
    implementation(libs.ktor.client.android)

    // dotenv
    implementation(libs.dotenv)

    // Navigation
    implementation(libs.navigation.compose)

    // LazyColumn Scroll-Bar
    implementation(libs.lazycolumn.scrollbar)

    // Reorderable LazyColumn
    implementation(libs.reorderable)

    implementation(libs.gson)
    implementation(libs.constraintlayout.compose)
    implementation(libs.kotlin.toon)

    // For HTTP Requests
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.work.runtime.ktx)
}