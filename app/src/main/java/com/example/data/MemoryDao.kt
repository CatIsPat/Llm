package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM character_memories WHERE characterId = :characterId ORDER BY timestamp DESC")
    fun getMemoriesForCharacter(characterId: Long): Flow<List<CharacterMemory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: CharacterMemory): Long

    @Update
    suspend fun updateMemory(memory: CharacterMemory)

    @Delete
    suspend fun deleteMemory(memory: CharacterMemory)

    @Query("DELETE FROM character_memories WHERE characterId = :characterId")
    suspend fun clearMemoriesForCharacter(characterId: Long)
}
