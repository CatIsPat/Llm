package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM roleplay_characters ORDER BY id ASC")
    fun getAllCharacters(): Flow<List<RoleplayCharacter>>

    @Query("SELECT * FROM roleplay_characters WHERE isSelected = 1 LIMIT 1")
    fun getSelectedCharacter(): Flow<RoleplayCharacter?>

    @Query("SELECT * FROM roleplay_characters WHERE id = :id LIMIT 1")
    suspend fun getCharacterById(id: Long): RoleplayCharacter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: RoleplayCharacter): Long

    @Update
    suspend fun updateCharacter(character: RoleplayCharacter)

    @Delete
    suspend fun deleteCharacter(character: RoleplayCharacter)

    @Transaction
    @Query("UPDATE roleplay_characters SET isSelected = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun selectCharacter(id: Long)
}
