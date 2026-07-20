import java.util.Properties

val useLocalN8n = providers.gradleProperty("noti.useLocalN8n")
    .orElse("false")
    .map(String::toBooleanStrict)
    .get()
val localN8nBaseUrl = providers.gradleProperty("noti.localN8nBaseUrl")
    .orElse("http://192.168.1.165:5678/")
    .get()
val publicN8nBaseUrl = "https://n8n.udchen.tw/"

require(localN8nBaseUrl.endsWith('/')) {
    "noti.localN8nBaseUrl must end with '/' so Retrofit can resolve webhook paths"
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "org.muilab.notigpt"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.muilab.notigpt"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // One switch controls every part of LAN testing: URL selection, cleartext HTTP, and the
        // Android 17 local-network runtime prompt. Set noti.useLocalN8n=false for the public server.
        buildConfigField("boolean", "USE_LOCAL_N8N", useLocalN8n.toString())
        buildConfigField(
            "String",
            "N8N_BASE_URL",
            (if (useLocalN8n) localN8nBaseUrl else publicN8nBaseUrl).asBuildConfigString(),
        )
        manifestPlaceholders["usesCleartextTraffic"] = useLocalN8n.toString()
        manifestPlaceholders["networkSecurityConfig"] = if (useLocalN8n) {
            "@xml/network_security_config_local"
        } else {
            "@xml/network_security_config_public"
        }

        // Per-notiKey extraction pipeline (contract v3). Each stage is its own n8n workflow/webhook.
        buildConfigField("String", "N8N_EXTRACT_A_SCAN_PATH", "\"webhook/extract-a-scan\"")
        buildConfigField("String", "N8N_EXTRACT_B_ITEMS_PATH", "\"webhook/extract-b-items\"")
        buildConfigField("String", "N8N_EXTRACT_C_SUMMARY_PATH", "\"webhook/extract-c-summary\"")
        buildConfigField("String", "N8N_EXTRACT_D1_SHORTLIST_PATH", "\"webhook/extract-d1-shortlist\"")
        buildConfigField("String", "N8N_EXTRACT_E1_MERGE_PATH", "\"webhook/extract-e1-merge\"")
        buildConfigField("String", "N8N_EXTRACT_D2_GROUPING_PATH", "\"webhook/extract-d2-grouping\"")
        buildConfigField("String", "N8N_EXTRACT_E2_MERGE_PATH", "\"webhook/extract-e2-merge\"")
        buildConfigField("String", "N8N_REGENERATE_ONE_PATH", "\"webhook/reminder-regenerate-one\"")
        buildConfigField("String", "N8N_PREFERENCE_QUICK_SYNC_PATH", "\"webhook/preference-quick-sync\"")
        buildConfigField("String", "N8N_PREFERENCE_CHAT_INTERACT_PATH", "\"webhook/preference-chat-interact\"")
        buildConfigField("String", "N8N_CONTEXT_DISCOVER_PATH", "\"webhook/preference-context-discover\"")
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

// Room's JSON schema is part of the migration contract. Commit generated versions so future
// migrations can be tested against the exact database shape shipped to users.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))

    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.tooling.preview)
    implementation(libs.material3)
    implementation(libs.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Room
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // Reorderable LazyColumn
    implementation(libs.reorderable)

    implementation(libs.gson)
    implementation(libs.constraintlayout.compose)

    // For HTTP Requests
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.work.runtime.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    // Credential Manager for Google Sign-In (mandatory login)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Google Tasks API integration
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.tasks)
    // Firestore and the Google Tasks client otherwise resolve incompatible gRPC families.
    implementation(platform(libs.grpc.bom))

    // Hilt owns app-scoped construction and Android entry-point injection.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

}
