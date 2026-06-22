package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "roleplay_characters")
data class RoleplayCharacter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val userName: String,
    val segmentA: String, // Core humanoid rules
    val intimacyLevel: Int, // 0 - 100
    val customPromptOverride: String, // Segment D
    val temperature: Float,
    val topP: Float,
    val baseUrl: String,
    val modelName: String,
    val isSelected: Boolean = false,
    val avatarColor: Int = 0xFF5D3EBC.toInt(), // Colorful accent for character avatar
    val backgroundColor: Int = 0xFF0E111A.toInt(), // Custom BG Color
    val themeAccentColor: Int = 0xFF8E7CC3.toInt(), // Theme accent color
    val bubbleColor: Int = 0xFF1F243A.toInt(), // Text bubble user color background
    val backgroundImageUri: String = "", // Static custom background image (URI, URL or Preset name)
    val backgroundDim: Float = 0.5f, // Background dimming value from 0.0f (fully black) to 1.0f (no dimming)
    val charBubbleColor: Int = 0xFF25293E.toInt(), // Chat bubble companion color background
    val userTextColor: Int = 0xFFFFFFFF.toInt(), // User message text color
    val charTextColor: Int = 0xFFFFFFFF.toInt(), // Companion message text color
    val isIntimacyAuto: Boolean = false // Automatic intimacy level progression
)
