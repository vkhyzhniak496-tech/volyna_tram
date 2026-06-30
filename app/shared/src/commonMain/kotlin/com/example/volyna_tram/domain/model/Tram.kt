package com.example.volyna_tram.domain.model

data class Tram(
    val id: String,          // Unikalny identyfikator konkretnego składu
    val number: String,  // np. "16", "19", "26"
    val start: String, // Skąd ruszył (kierunek A)
    val terminus: String,    // Dokąd jedzie (kierunek B)
    val state: String        // Żeby prostokącik wiedział, jaki mieć stan (np. "W ruchu", "Zatrzymany")
)