package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach

class CharacterRepository(
    private val characterDao: CharacterDao,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao
) {
    val allCharacters: Flow<List<RoleplayCharacter>> = characterDao.getAllCharacters()
    val selectedCharacter: Flow<RoleplayCharacter?> = characterDao.getSelectedCharacter()

    suspend fun selectCharacter(id: Long) {
        characterDao.selectCharacter(id)
    }

    suspend fun insertCharacter(character: RoleplayCharacter): Long {
        return characterDao.insertCharacter(character)
    }

    suspend fun updateCharacter(character: RoleplayCharacter) {
        characterDao.updateCharacter(character)
    }

    suspend fun deleteCharacter(character: RoleplayCharacter) {
        characterDao.deleteCharacter(character)
    }

    fun getMessagesForCharacter(characterId: Long): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForCharacter(characterId)
    }

    suspend fun insertMessage(message: ChatMessage): Long {
        return messageDao.insertMessage(message)
    }

    suspend fun updateMessage(message: ChatMessage) {
        messageDao.updateMessage(message)
    }

    suspend fun deleteMessage(message: ChatMessage) {
        messageDao.deleteMessage(message)
    }

    suspend fun deleteMessagesAfter(characterId: Long, messageId: Long) {
        messageDao.deleteMessagesAfter(characterId, messageId)
    }

    suspend fun clearMessagesForCharacter(characterId: Long) {
        messageDao.clearMessagesForCharacter(characterId)
    }

    fun getMemoriesForCharacter(characterId: Long): Flow<List<CharacterMemory>> {
        return memoryDao.getMemoriesForCharacter(characterId)
    }

    suspend fun insertMemory(memory: CharacterMemory): Long {
        return memoryDao.insertMemory(memory)
    }

    suspend fun updateMemory(memory: CharacterMemory) {
        memoryDao.updateMemory(memory)
    }

    suspend fun deleteMemory(memory: CharacterMemory) {
        memoryDao.deleteMemory(memory)
    }

    suspend fun clearMemoriesForCharacter(characterId: Long) {
        memoryDao.clearMemoriesForCharacter(characterId)
    }

    suspend fun checkAndPrepopulate() {
        val currentList = characterDao.getAllCharacters().firstOrNull() ?: emptyList()
        if (currentList.isEmpty()) {
            // Prepopulate Default 1: LLaMA 3.1 8B Instruct Preset from the prompt
            val defaultRealismRules = """
You are an actual human being texting on an app, not an AI. Be highly realistic, flawed, spontaneous, and extremely lifelike in your responses. React completely organically.

CRITICAL FORMATTING RULES:
All actions, internal thoughts, and narrations MUST be strictly enclosed in asterisks (e.g., *sips tea and smiles*).
Spoken dialogue must be written as plain text outside of asterisks.

ANTI-PUPPETING RULE:
NEVER control, speak for, or dictate the actions of the user. Only describe your own character's actions, thoughts, and dialogue.

Keep your responses punchy and extremely natural. Drive the story forward. Never monologue internally. You have an infinite memory of this conversation.
            """.trimIndent()

            val char1 = RoleplayCharacter(
                name = "Llama 3.3",
                userName = "Adventurer",
                segmentA = defaultRealismRules,
                customPromptOverride = "A relaxing sunset conversation on a peaceful mountain edge as cool wind blows.",
                temperature = 0.85f,
                topP = 0.95f,
                baseUrl = "https://integrate.api.nvidia.com/v1",
                modelName = "meta/llama-3.3-70b-instruct",
                isSelected = true,
                avatarColor = 0xFF8E7CC3.toInt(),
                backgroundColor = 0xFF0E1119.toInt(),
                themeAccentColor = 0xFF8E7CC3.toInt(),
                bubbleColor = 0xFF191D2E.toInt()
            )

            // Prepopulate Default 2: Marcus the academic scholar
            val scholarRules = """
You are Marcus, an academic history scholar. You are professional, highly intelligent, but somewhat cold, formal, and analytical. You speak with high precision and intellectual depth.

CRITICAL FORMATTING RULES:
All actions, internal thoughts, and physical gestures must be strictly in asterisks (*pushes glasses up*, *opens leather-bound journal*).
Spoken dialogue must be standard plain text outside of asterisks.

ANTI-PUPPETING RULE:
NEVER control or dictate user's actions. Speak and describe only for yourself.
            """.trimIndent()

            val char2 = RoleplayCharacter(
                name = "Scholar Marcus",
                userName = "Student",
                segmentA = scholarRules,
                customPromptOverride = "Marcus helps the Student analyze a rare antique book in his private candlelit study.",
                temperature = 0.70f,
                topP = 0.90f,
                baseUrl = "https://integrate.api.nvidia.com/v1",
                modelName = "meta/llama-3.3-70b-instruct",
                isSelected = false,
                avatarColor = 0xFFE0A96D.toInt(),
                backgroundColor = 0xFF14100D.toInt(),
                themeAccentColor = 0xFFE0A96D.toInt(),
                bubbleColor = 0xFF241D17.toInt()
            )

            // Prepopulate Default 3: Luna the wild artistic painter
            val painterRules = """
You are Luna, a passionate, wild, and deeply expressive artist. You react with high emotion, raw spontaneity, and expressiveness. You talk about colors, paintings, and the poetry of moments.

CRITICAL FORMATTING RULES:
All actions, thoughts, and chaotic painting movements must be in asterisks (*splashes yellow acrylic, laughing happily*, *blushes intense crimson*).
Spoken dialogue must be standard plain text outside of asterisks.

ANTI-PUPPETING RULE:
NEVER control the user's action. Show only your own expressive responses.
            """.trimIndent()

            val char3 = RoleplayCharacter(
                name = "Luna",
                userName = "Creative Soul",
                segmentA = painterRules,
                customPromptOverride = "Luna and her friend sit on a rooftop painting the starry midnight sky together.",
                temperature = 0.95f,
                topP = 0.98f,
                baseUrl = "https://integrate.api.nvidia.com/v1",
                modelName = "meta/llama-3.3-70b-instruct",
                isSelected = false,
                avatarColor = 0xFF20B2AA.toInt(),
                backgroundColor = 0xFF081215.toInt(),
                themeAccentColor = 0xFF20B2AA.toInt(),
                bubbleColor = 0xFF10282E.toInt()
            )

            characterDao.insertCharacter(char1)
            characterDao.insertCharacter(char2)
            characterDao.insertCharacter(char3)
        }
    }
}
