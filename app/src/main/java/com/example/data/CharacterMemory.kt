package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_memories")
data class CharacterMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val characterId: Long,
    val info: String,
    val timestamp: Long = System.currentTimeMillis()
)
