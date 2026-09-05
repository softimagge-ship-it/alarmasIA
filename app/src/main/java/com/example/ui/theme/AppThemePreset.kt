package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class AppThemePreset(
    val id: String,
    val title: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val containerColor: Color
) {
    INDIGO(
        id = "indigo",
        title = "Índigo Moderno",
        description = "Elegante violeta y lavanda minimalista",
        primaryColor = Color(0xFF6750A4),
        secondaryColor = Color(0xFF625B71),
        containerColor = Color(0xFFEADDFF)
    ),
    EMERALD(
        id = "emerald",
        title = "Esmeralda & Menta",
        description = "Verde esmeralda y menta fresca relajante",
        primaryColor = Color(0xFF006C51),
        secondaryColor = Color(0xFF4C6358),
        containerColor = Color(0xFF8CF6CD)
    ),
    AMBER(
        id = "amber",
        title = "Ámbar & Sunset",
        description = "Cálido terracota y ámbar atardecer",
        primaryColor = Color(0xFF944B00),
        secondaryColor = Color(0xFF755846),
        containerColor = Color(0xFFFFDCC7)
    );

    companion object {
        fun fromId(id: String?): AppThemePreset {
            return values().firstOrNull { it.id == id } ?: INDIGO
        }
    }
}
