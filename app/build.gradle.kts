plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.hotspot.module'
    compileSdk 35

    defaultConfig {
        applicationId "com.hotspot.module"
        minSdk 28
        targetSdk 35
        versionCode 1
        versionName "1.0.0"
    }

    buildFeatures {
        compose true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion '1.5.14' // Versi compiler stabil untuk Kotlin 2.0.0
    }
}

dependencies {
    // LSPosed API 102 Modern Standard
    compileOnly 'org.lsposed.lsposed:api:1.0.2'

    // Jetpack Compose & Core AndroidX
    implementation libs.androidx.core.ktx
    implementation libs.androidx.lifecycle.runtime.ktx
    implementation libs.androidx.activity.compose
    implementation platform(libs.androidx.compose.bom)
    implementation libs.androidx.compose.ui
    implementation libs.androidx.compose.material3
    
    // Google Maps SDK untuk UI Pemilihan Lokasi
    implementation 'com.google.android.gms:play-services-maps:19.0.0'
}
