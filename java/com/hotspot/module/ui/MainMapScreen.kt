package com.hotspot.module.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hotspot.module.data.PrefsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMapScreen() {
    val context = LocalContext.current
    val sharedPref = remember { PrefsManager.getPreferences(context) }
    
    var isSpoofingActive by remember { 
        mutableStateOf(sharedPref.getBoolean("is_active", false)) 
    }

    // Koordinat simulasi (Contoh: Semanggi Jakarta)
    val mockLat = -6.2199
    val mockLng = 106.8162

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HOTSPOT Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Area Peta utama diletakkan di sini
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSpoofingActive) "STATUS: ACTIVE SPOOFING" else "STATUS: STANDBY",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSpoofingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Koordinat Aktif: $mockLat, $mockLng", style = MaterialTheme.typography.bodyMedium)
            }

            // Panel Interaktif Bawah (Material 3 Card)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "Target Hooks: Gojek & Grab Driver", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            isSpoofingActive = !isSpoofingActive
                            PrefsManager.saveData(context, isSpoofingActive, mockLat, mockLng)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSpoofingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isSpoofingActive) "Stop Hotspot Injector" else "Start Hotspot Injector")
                    }
                }
            }
        }
    }
}
