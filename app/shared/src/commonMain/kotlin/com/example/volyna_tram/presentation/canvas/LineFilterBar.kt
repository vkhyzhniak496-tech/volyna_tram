package com.example.volyna_tram.presentation.canvas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LineFilterBar(
    availableLines: List<String>,
    selectedLines: Set<String>,
    visibleTramsCount: Int,
    onLineToggled: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableLines.isEmpty()) return

        Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 12.dp, end = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A), // Ciemne, czytelne tło (Slate 900)
        border = BorderStroke(1.dp, Color(0xFF334155)), // Wyraźna ramka
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "LINIE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFFB300),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•  Widoczne: $visibleTramsCount wozów",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }

                if (selectedLines.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text(
                            text = "Resetuj (${selectedLines.size})",
                            fontSize = 11.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp), // Zabezpieczenie przed ucinaniem ostatnich linii
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Przycisk "Wszystkie"
                item {
                    FilterChip(
                        selected = selectedLines.isEmpty(),
                        onClick = onClearAll,
                        label = { Text("Wszystkie", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF38BDF8),
                            selectedLabelColor = Color(0xFF0F172A),
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (selectedLines.isEmpty()) Color(0xFF38BDF8) else Color(0xFF475569),
                            enabled = true,
                            selected = selectedLines.isEmpty()
                        )
                    )
                }

                // Kafelki linii z ostrym żółtym podświetleniem
                items(availableLines, key = { it }) { line ->
                    val isSelected = line in selectedLines
                    FilterChip(
                        selected = isSelected,
                        onClick = { onLineToggled(line) },
                        label = {
                            Text(
                                text = line,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFB300), // Żółty akcent
                            selectedLabelColor = Color(0xFF0F172A),
                            containerColor = Color(0xFF1E293B), // Nieaktywny czarny/ciemnoszary
                            labelColor = Color(0xFFF1F5F9)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) Color(0xFFFFB300) else Color(0xFF334155),
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }
        }
    }
}