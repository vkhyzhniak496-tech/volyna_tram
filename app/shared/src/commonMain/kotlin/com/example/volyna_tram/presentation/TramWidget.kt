package com.example.volyna_tram.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.volyna_tram.domain.model.Tram
@Composable

fun TramWidget(
    tram: Tram,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (tram.state) {
        "Early" -> Color(0xFF1E88E5)
        "On Time" -> Color(0xFF4CAF50)
        "Delayed" -> Color(0xFFE53935)
        else -> Color(0xFF757575)
    }

    Box(
        modifier = modifier
            .width(120.dp)
            .height(60.dp)
            .background(color = backgroundColor, shape = RoundedCornerShape(8.dp))
            .clickable {
                val nextState = when (tram.state) {
                    "On Time" -> "Delayed"
                    "Delayed" -> "Early"
                    else -> "On Time"
                }
                TramStore.updateTramState(tram.id, nextState)
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Linia ${tram.number}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tram.state,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
    }
}