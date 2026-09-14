android {
    namespace = "com.aya.module" // Set namespace here (AGP 8+)
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        // Adjust version to match your project's Kotlin compiler version
        kotlinCompilerExtensionVersion = "1.5.14" 
    }
}

dependencies {
    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Google Play Services Location & Maps
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")

    // Xposed Framework API
    compileOnly("de.robv.android.xposed:api:82")
}
