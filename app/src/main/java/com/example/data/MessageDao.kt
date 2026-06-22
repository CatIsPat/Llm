package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM chat_messages WHERE characterId = :characterId ORDER BY timestamp ASC")
    fun getMessagesForCharacter(characterId: Long): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE characterId = :characterId")
    suspend fun clearMessagesForCharacter(characterId: Long)

    @Query("DELETE FROM chat_messages WHERE characterId = :characterId AND id > :messageId")
    suspend fun deleteMessagesAfter(characterId: Long, messageId: Long)

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Delete
    suspend fun deleteMessage(message: ChatMessage)
}
