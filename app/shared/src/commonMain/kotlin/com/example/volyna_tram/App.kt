package com.example.volyna_tram

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volyna_tram.presentation.TramStore
import com.example.volyna_tram.presentation.TramWidget
import androidx.compose.runtime.LaunchedEffect
@Preview

@Composable
fun App() {
    TramScreen()
}
@Composable

fun TramScreen() {
    MaterialTheme {
        // 🌊 Podłączamy się pod jednokierunkowy strumień danych z RAM-u
        val taborList by TramStore.tabor.collectAsState()

        // ⚡LaunchedEffect odpali się RAZ na starcie aplikacji i asynchronicznie pobierze tabor z sieci
        LaunchedEffect(Unit) {
            TramStore.networkFetchAction?.invoke()
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Praska Centrala Ruchu - Tabor",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            // Renderujemy prostokąciki na ekranie obok siebie
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                taborList.forEach { wagon ->
                    TramWidget(tram = wagon)
                }
            }
        }
    }
}