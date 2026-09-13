    implementation("com.google.android.gms:play-services-maps:19.0.0")
}
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hotspot.module"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hotspot.module"
        minSdk = 28 // Android 9.0 (Pie) ke atas
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // LSPosed API 102 Modern Standard (compileOnly agar tidak konflik di runtime)
    compileOnly("org.lsposed.lsposed:api:1.0.2")

    // Jetpack Compose & Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    
    // Google Maps SDK untuk UI Pemilihan Lokasi
    implementation("com.google.android.gms:play-services-maps:19.0.0")
}
