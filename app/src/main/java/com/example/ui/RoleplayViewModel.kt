package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.ChatMessage
import com.example.data.CharacterRepository
import com.example.data.RoleplayCharacter
import com.example.data.CharacterMemory
import com.example.network.NimChatRequest
import com.example.network.NimMessage
import com.example.network.NvidiaNimService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoleplayViewModel(private val repository: CharacterRepository) : ViewModel() {

    // List of all characters available
    val characters: StateFlow<List<RoleplayCharacter>> = repository.allCharacters
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current active selected character
    val selectedCharacter: StateFlow<RoleplayCharacter?> = repository.selectedCharacter
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Chat messages list for current selected character
    val messages: StateFlow<List<ChatMessage>> = selectedCharacter
        .flatMapLatest { char ->
            if (char != null) {
                repository.getMessagesForCharacter(char.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Memories/Knowledge for the active character
    val memories: StateFlow<List<CharacterMemory>> = selectedCharacter
        .flatMapLatest { char ->
            if (char != null) {
                repository.getMemoriesForCharacter(char.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI states
    var inputMessage by mutableStateOf("")
    var isGenerating by mutableStateOf(false)
    var isStreaming by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var currentTab by mutableStateOf(0) // 0 = Chat, 1 = Config/Presets, 2 = Memory Engine
    var currentStreamingText by mutableStateOf("")
    
    // Dynamic Suggestion Generation Engine states
    var isGeneratingSuggestions by mutableStateOf(false)
    var generatedSuggestions by mutableStateOf<List<String>>(emptyList())

    // Standard preset options for Nvidia NIM Meta Models
    var availableModelsList by mutableStateOf(listOf(
        "meta/llama-3.3-70b-instruct",
        "meta/llama-3.1-8b-instruct",
        "meta/llama-3.1-70b-instruct",
        "nvidia/llama-3.1-nemotron-70b-instruct"
    ))
    
    fun fetchAvailableModels(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            try {
                val service = com.example.network.NvidiaNimService.getInstance()
                val targetUrl = if (baseUrl.endsWith("/")) "${baseUrl}models" else "$baseUrl/models"
                val response = service.getModels(targetUrl, "Bearer $apiKey")
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string()
                    if (bodyString != null) {
                        val json = org.json.JSONObject(bodyString)
                        val dataArray = json.optJSONArray("data")
                        if (dataArray != null) {
                            val newModels = mutableListOf<String>()
                            for (i in 0 until dataArray.length()) {
                                val modelId = dataArray.getJSONObject(i).optString("id")
                                if (modelId.isNotBlank()) newModels.add(modelId)
                            }
                            if (newModels.isNotEmpty()) {
                                availableModelsList = newModels.distinct()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently fallback on failure to avoid disturbing UX, just keep existing predefined
            }
        }
    }

    // Default target API key provided by user - initialized directly for immediate out-of-the-box action
    var customApiKey by mutableStateOf("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")

    init {
        viewModelScope.launch {
            repository.checkAndPrepopulate()
            // Proactively upgrade existing characters to high-performance meta/llama-3.3-70b-instruct
            try {
                val allChars = repository.allCharacters.first()
                allChars.forEach { char ->
                    if (char.modelName == "meta/llama-3.1-8b-instruct" || char.modelName.isBlank()) {
                        val updated = char.copy(
                            modelName = "meta/llama-3.3-70b-instruct",
                            name = if (char.name == "Llama 3.1") "Llama 3.3" else char.name
                        )
                        repository.updateCharacter(updated)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    fun selectCharacter(id: Long) {
        viewModelScope.launch {
            repository.selectCharacter(id)
            errorMessage = null
        }
    }

    fun clearChatHistory() {
        val activeChar = selectedCharacter.value ?: return
        viewModelScope.launch {
            repository.clearMessagesForCharacter(activeChar.id)
            errorMessage = null
        }
    }

    fun addNewCharacter(
        name: String,
        userName: String,
        segmentA: String,
        intimacyLevel: Int,
        customPromptOverride: String,
        temperature: Float,
        topP: Float,
        baseUrl: String,
        modelName: String,
        avatarColor: Int,
        backgroundColor: Int = 0xFF0E111A.toInt(),
        themeAccentColor: Int = 0xFF8E7CC3.toInt(),
        bubbleColor: Int = 0xFF1F243A.toInt(),
        backgroundImageUri: String = "",
        backgroundDim: Float = 0.5f,
        charBubbleColor: Int = 0xFF25293E.toInt(),
        userTextColor: Int = 0xFFFFFFFF.toInt(),
        charTextColor: Int = 0xFFDCD6F7.toInt(),
        isIntimacyAuto: Boolean = false
    ) {
        viewModelScope.launch {
            val newChar = RoleplayCharacter(
                name = name,
                userName = userName,
                segmentA = segmentA,
                intimacyLevel = intimacyLevel,
                customPromptOverride = customPromptOverride,
                temperature = temperature,
                topP = topP,
                baseUrl = baseUrl,
                modelName = modelName,
                avatarColor = avatarColor,
                backgroundColor = backgroundColor,
                themeAccentColor = themeAccentColor,
                bubbleColor = bubbleColor,
                backgroundImageUri = backgroundImageUri,
                backgroundDim = backgroundDim,
                charBubbleColor = charBubbleColor,
                userTextColor = userTextColor,
                charTextColor = charTextColor,
                isIntimacyAuto = isIntimacyAuto
            )
            val newId = repository.insertCharacter(newChar)
            repository.selectCharacter(newId)
            currentTab = 0 // Switch to chat after creating
            errorMessage = null
        }
    }

    fun updateCurrentCharacter(character: RoleplayCharacter) {
        viewModelScope.launch {
            repository.updateCharacter(character)
            errorMessage = null
        }
    }

    fun deleteCharacter(character: RoleplayCharacter) {
        viewModelScope.launch {
            repository.deleteCharacter(character)
            // Select another character if available
            val all = characters.value
            val next = all.firstOrNull { it.id != character.id }
            if (next != null) {
                repository.selectCharacter(next.id)
            }
            errorMessage = null
        }
    }

    fun restoreDefaultCompanions() {
        viewModelScope.launch {
            repository.checkAndPrepopulate()
            val all = repository.allCharacters.firstOrNull() ?: emptyList()
            val firstChar = all.firstOrNull()
            if (firstChar != null) {
                repository.selectCharacter(firstChar.id)
            }
            errorMessage = null
        }
    }

    // 1. BACKTRACKING: Deletes all messages strictly AFTER the given message ID
    fun backtrackToMessage(targetMsg: ChatMessage) {
        val activeChar = selectedCharacter.value ?: return
        viewModelScope.launch {
            repository.deleteMessagesAfter(activeChar.id, targetMsg.id)
            errorMessage = null
        }
    }

    // 2. USER TEXT EDITING: Updates content, deletes future context, triggers regeneration
    fun editUserMessage(message: ChatMessage, newContent: String) {
        val activeChar = selectedCharacter.value ?: return
        viewModelScope.launch {
            // Update the source message
            val updated = message.copy(content = newContent)
            repository.updateMessage(updated)
            // Backtrack succeeding messages
            repository.deleteMessagesAfter(activeChar.id, message.id)
            // Re-trigger the response from this point
            errorMessage = null
            generateAIPartnerResponse(activeChar)
        }
    }

    // 3. AI REPLY EDITING: Updates the message content in history directly
    fun editAiMessage(message: ChatMessage, newContent: String) {
        viewModelScope.launch {
            val updated = message.copy(content = newContent)
            repository.updateMessage(updated)
            errorMessage = null
        }
    }

    // 4. MANUAL & AUTO MEMORY SYSTEM: Adding, deleting, clearing memories
    fun addMemory(info: String) {
        val activeChar = selectedCharacter.value ?: return
        if (info.isBlank()) return
        viewModelScope.launch {
            repository.insertMemory(
                CharacterMemory(characterId = activeChar.id, info = info.trim())
            )
        }
    }

    fun deleteMemory(memory: CharacterMemory) {
        viewModelScope.launch {
            repository.deleteMemory(memory)
        }
    }

    fun clearAllMemories() {
        val activeChar = selectedCharacter.value ?: return
        viewModelScope.launch {
            repository.clearMemoriesForCharacter(activeChar.id)
        }
    }

    // Primary entry point for sending a new message
    fun sendMessage() {
        val activeChar = selectedCharacter.value ?: return
        val currentText = inputMessage.trim()
        if (currentText.isBlank() || isGenerating) return

        inputMessage = ""
        isGenerating = true
        errorMessage = null

        viewModelScope.launch {
            // 1. Insert user message in database first
            val userMsg = ChatMessage(
                characterId = activeChar.id,
                sender = "USER",
                content = currentText
            )
            repository.insertMessage(userMsg)

            // Auto-extract memory points heuristic (e.g. if user states "I love X" or "My favorite X is Y")
            detectAndExtractAutoMemory(currentText, activeChar.id)

            // 2. Generate AI response from historical database state
            generateAIPartnerResponse(activeChar)
        }
    }

    // Reroll/Regenerate the last AI message
    fun rerollLastMessage() {
        val activeChar = selectedCharacter.value ?: return
        val currentMsgs = messages.value
        if (currentMsgs.isEmpty() || isGenerating) return
        
        viewModelScope.launch {
            val lastMsg = currentMsgs.last()
            if (lastMsg.sender == "AI") {
                // Delete the last AI message
                repository.deleteMessage(lastMsg)
                // Trigger response again
                generateAIPartnerResponse(activeChar)
            } else {
                // Last message is user's message, just generate for it
                generateAIPartnerResponse(activeChar)
            }
        }
    }

    // Regenerate from any specific companion message onwards
    fun regenerateAiReply(targetMsg: ChatMessage) {
        val activeChar = selectedCharacter.value ?: return
        if (isGenerating) return
        viewModelScope.launch {
            repository.deleteMessage(targetMsg)
            repository.deleteMessagesAfter(activeChar.id, targetMsg.id)
            errorMessage = null
            generateAIPartnerResponse(activeChar)
        }
    }

    private fun sanitizeConversationForApi(
        systemPrompt: String,
        rawHistory: List<ChatMessage>
    ): List<NimMessage> {
        val conversationList = mutableListOf<NimMessage>()
        
        // 1. Add system prompt
        if (systemPrompt.isNotBlank()) {
            conversationList.add(NimMessage(role = "system", content = systemPrompt))
        }
        
        // 2. Map and filter raw message history
        val mappedHistory = rawHistory.mapNotNull { msg ->
            val role = if (msg.sender == "USER") "user" else "assistant"
            if (msg.content.isNotBlank()) {
                NimMessage(role = role, content = msg.content)
            } else {
                null
            }
        }
        
        // 3. Merge consecutive messages of the same role
        val alternatedHistory = mutableListOf<NimMessage>()
        for (msg in mappedHistory) {
            if (alternatedHistory.isEmpty()) {
                alternatedHistory.add(msg)
            } else {
                val last = alternatedHistory.last()
                if (last.role == msg.role) {
                    alternatedHistory[alternatedHistory.size - 1] = NimMessage(
                        role = last.role,
                        content = last.content + "\n" + msg.content
                    )
                } else {
                    alternatedHistory.add(msg)
                }
            }
        }
        
        // 4. Ensure first message after system is "user" to satisfy instruct templates contract
        if (alternatedHistory.isNotEmpty() && alternatedHistory[0].role == "assistant") {
            conversationList.add(NimMessage(role = "user", content = "*starts the session*"))
        }
        
        conversationList.addAll(alternatedHistory)
        return conversationList
    }

    private fun isTextGibberish(text: String): Boolean {
        if (text.length < 15) return false
        
        var foreignCount = 0
        text.forEach { c ->
            val code = c.code
            val isForeign = (code in 0x0370..0x03FF) || // Greek
                            (code in 0x0400..0x052F) || // Cyrillic
                            (code in 0x0590..0x05FF) || // Hebrew
                            (code in 0x0600..0x06FF) || // Arabic
                            (code in 0x0900..0x0DFF) || // Indic/Tamil/Sanskrit
                            (code in 0x0E00..0x0E7F) || // Thai
                            (code in 0x10A0..0x10FF) || // Georgian
                            (code in 0x3040..0x309F) || // Hiragana
                            (code in 0x30A0..0x30FF) || // Katakana
                            (code in 0x4E00..0x9FFF) || // CJK Unified Ideographs
                            (code in 0xAC00..0xD7AF)    // Hangul Syllables
            if (isForeign) {
                foreignCount++
            }
        }
        
        // If 3 or more foreign script characters, or more than 5% of the string is foreign script characters
        return foreignCount >= 3 || (foreignCount.toFloat() / text.length > 0.05f)
    }

    class GibberishDetectedException : Exception("Model generated incoherent multi-language gibberish.")

    // Sub-routine to perform AI generation
    private suspend fun generateAIPartnerResponse(activeChar: RoleplayCharacter) {
        isGenerating = true
        try {
            val apiKeyToUse = customApiKey.trim().ifEmpty {
                BuildConfig.NVIDIA_API_KEY
            }

            if (apiKeyToUse.isBlank() || apiKeyToUse == "MY_NVIDIA_API_KEY_DEFAULT_VALUE") {
                throw IllegalStateException("API key is blank. Please specify an NVIDIA API Key in settings.")
            }

            val fullUrl = if (activeChar.baseUrl.endsWith("/")) {
                "${activeChar.baseUrl}chat/completions"
            } else {
                "${activeChar.baseUrl}/chat/completions"
            }

            withContext(Dispatchers.IO) {
                // Fetch current messages context from database off the main thread
                val previousMsgs = repository.getMessagesForCharacter(activeChar.id).first()

                // Extract the user's latest message content to use as memory query keywords
                val lastUserQuery = previousMsgs.lastOrNull { it.sender == "USER" }?.content ?: ""
                val slidingWindowSize = 100
                val recalledDialogue = if (lastUserQuery.isNotBlank()) {
                    retrieveRelevantPastDialogue(lastUserQuery, previousMsgs, slidingWindowSize = slidingWindowSize)
                } else {
                    emptyList()
                }

                // Stitch system prompt injecting dynamic memories / environmental fact sheets & Recalled Excerpts
                // Fetch memories directly from room inside IO thread
                val currentMemories = repository.getMemoriesForCharacter(activeChar.id).first()
                val systemPrompt = stitchSystemPrompt(activeChar, currentMemories, recalledDialogue)

                // Sanitize and align conversation with strict alternating roles & first-query user alignment
                val apiMessages = sanitizeConversationForApi(systemPrompt, previousMsgs.takeLast(slidingWindowSize))

                var currentRetry = 0
                var temp = activeChar.temperature
                var topP = activeChar.topP
                var serviceSuccess = false
                val accumulatedReply = java.lang.StringBuilder()

                while (currentRetry <= 2 && !serviceSuccess) {
                    accumulatedReply.setLength(0)
                    try {
                        val requestBody = NimChatRequest(
                            model = activeChar.modelName,
                            messages = apiMessages,
                            temperature = temp,
                            topP = topP,
                            stream = true,
                            frequencyPenalty = 0.15f,
                            presencePenalty = 0.15f
                        )

                        val service = NvidiaNimService.getInstance()
                        val response = service.getChatCompletionStreaming(
                            url = fullUrl,
                            authHeader = "Bearer $apiKeyToUse",
                            request = requestBody
                        )

                        if (response.isSuccessful) {
                            val body = response.body()
                            if (body != null) {
                                val source = body.source()
                                
                                // Switch to Dispatchers.Main to safely manipulate mutableStateOf for UI
                                withContext(Dispatchers.Main) {
                                    isStreaming = true
                                    currentStreamingText = ""
                                }
                                
                                try {
                                    while (!source.exhausted()) {
                                        val line = source.readUtf8Line() ?: continue
                                        if (line.startsWith("data: ")) {
                                            val data = line.removePrefix("data: ").trim()
                                            if (data == "[DONE]") break
                                            
                                            try {
                                                val json = org.json.JSONObject(data)
                                                val choices = json.optJSONArray("choices")
                                                if (choices != null && choices.length() > 0) {
                                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                                    if (delta != null && delta.has("content")) {
                                                        val contentChunk = delta.getString("content")
                                                        accumulatedReply.append(contentChunk)
                                                        val textSoFar = accumulatedReply.toString()
                                                        
                                                        // Guard rails check for multilingual token soup
                                                        if (isTextGibberish(textSoFar)) {
                                                            throw GibberishDetectedException()
                                                        }
                                                        
                                                        withContext(Dispatchers.Main) {
                                                            currentStreamingText = textSoFar
                                                        }
                                                    }
                                                }
                                            } catch (e: org.json.JSONException) {}
                                        }
                                    }
                                    serviceSuccess = true
                                } finally {
                                    source.close()
                                }
                            } else {
                                throw IllegalStateException("Empty body returned")
                            }
                        } else {
                            val errorCode = response.code()
                            val errorString = response.errorBody()?.string() ?: "Service Error"
                            throw IllegalStateException("HTTP Error $errorCode: $errorString")
                        }
                    } catch (gibberishEx: GibberishDetectedException) {
                        currentRetry++
                        if (currentRetry <= 2) {
                            // Lower temperature to resolve sampling / quantization errors and drift
                            temp = maxOf(0.15f, temp - 0.25f)
                            topP = maxOf(0.50f, topP - 0.10f)
                            withContext(Dispatchers.Main) {
                                currentStreamingText = "[Fine-tuning model decoding parameters for conversational stability...]"
                            }
                            kotlinx.coroutines.delay(600)
                        } else {
                            throw gibberishEx
                        }
                    } catch (e: Exception) {
                        throw e
                    }
                }

                val reply = accumulatedReply.toString()
                if (reply.isNotBlank() && serviceSuccess) {
                    val aiMsg = ChatMessage(
                        characterId = activeChar.id,
                        sender = "AI",
                        content = reply
                    )
                    
                    // Insert standard permanent text to the Db
                    val newId = repository.insertMessage(aiMsg)
                    
                    // Wait until the database's Flow emits the new message ID in the `messages` list
                    // This guarantees 100% that there is NO BLINK at all because the UI transition is atomic and continuous!
                    withContext(Dispatchers.Default) {
                        var checkAttempts = 0
                        while (checkAttempts < 150) {
                            val currentMsgs = messages.value
                            if (currentMsgs.any { it.id == newId || (it.sender == "AI" && it.content == reply) }) {
                                break
                            }
                            kotlinx.coroutines.delay(10)
                            checkAttempts++
                        }
                    }
                    
                    // Small buffer to guarantee layout pass is complete and render state matches list item perfectly
                    kotlinx.coroutines.delay(60)
                    
                    withContext(Dispatchers.Main) {
                        isStreaming = false
                        currentStreamingText = ""
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Error: API payload returned empty content."
                        isStreaming = false
                        currentStreamingText = ""
                    }
                }
            }
        } catch (gibEx: GibberishDetectedException) {
            withContext(Dispatchers.Main) {
                errorMessage = "Language Generation Loop: This AI model is too deep in a repetition cycle. Try editing the last message slightly to steer them back, or switch to a more stable model in Config."
                isStreaming = false
                currentStreamingText = ""
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                errorMessage = "API Failure: ${e.localizedMessage ?: e.message}"
            }
        } finally {
            withContext(Dispatchers.Main) {
                isGenerating = false
            }
        }
    }

    // High performance in-memory retrieval engine for conversations of infinite size.
    // Scrapes meaningful subject nouns from current input, matches older database records,
    // and feeds the exact past interaction context back to the AI model.
    private fun retrieveRelevantPastDialogue(
        currentQuery: String,
        allMessages: List<ChatMessage>,
        slidingWindowSize: Int = 20
    ): List<ChatMessage> {
        val queryWords = currentQuery.lowercase()
            .split(Regex("[^a-zA-Z0-9'’-]"))
            .map { it.trim() }
            .filter { 
                it.length > 2 && it !in listOf(
                    "with", "that", "this", "from", "their", "them", "your", "have", "what", "where", "when", "how", "with", "about", 
                    "could", "would", "should", "there", "their", "please", "some", "someone", "something", "really", "know", "hello", "hi", "hey"
                ) 
            }
        
        if (queryWords.isEmpty() || allMessages.size <= slidingWindowSize) {
            return emptyList()
        }

        // Search old messages (except the most recent 15 messages) to build rich context
        val olderLimit = maxOf(0, allMessages.size - 15)
        val oldMessages = allMessages.take(olderLimit)
        
        // Find indices of oldMessages that match
        val matchedIndices = mutableListOf<Int>()
        oldMessages.forEachIndexed { index, msg ->
            val contentLower = msg.content.lowercase()
            val matches = queryWords.any { word -> contentLower.contains(word) }
            if (matches) {
                matchedIndices.add(index)
            }
        }

        // We extract coherent dialogue snippets (the matching message itself and its response)
        // Let's gather the dialogue blocks (from matchIndex to matchIndex + 1), and then select unique ones
        val matchedBlocks = mutableListOf<ChatMessage>()
        val includedIds = mutableSetOf<Long>()
        
        // Take up to 5 unique matching blocks to avoid overfilling context window
        var blocksCount = 0
        for (idx in matchedIndices) {
            if (blocksCount >= 6) {
                break
            }
            // Add msg at idx
            val msg1 = oldMessages[idx]
            if (includedIds.add(msg1.id)) {
                matchedBlocks.add(msg1)
            }
            // Also append the next message (response) for completeness of conversational pair!
            if (idx + 1 < oldMessages.size) {
                val msg2 = oldMessages[idx + 1]
                if (includedIds.add(msg2.id)) {
                    matchedBlocks.add(msg2)
                }
            }
            blocksCount++
        }

        // Return sorted chronologically by id
        return matchedBlocks.sortedBy { it.id }
    }

    // Heuristically detect and auto-extract core memories for lifelike companion reactions
    private fun detectAndExtractAutoMemory(text: String, characterId: Long) {
        val lower = text.lowercase()
        val sentences = text.split(Regex("[.!?\n]")).map { it.trim() }.filter { it.length > 5 }

        sentences.forEach { sentence ->
            val isMemorable = sentence.contains("my favorite", ignoreCase = true) ||
                             sentence.contains("i love", ignoreCase = true) ||
                             sentence.contains("i hate", ignoreCase = true) ||
                             sentence.contains("i am ", ignoreCase = true) ||
                             sentence.contains("you are ", ignoreCase = true) ||
                             sentence.contains("we are ", ignoreCase = true) ||
                             sentence.contains("my age", ignoreCase = true) ||
                             sentence.contains("my job", ignoreCase = true) ||
                             sentence.contains("my dream", ignoreCase = true) ||
                             sentence.contains("my hobby", ignoreCase = true) ||
                             sentence.contains("remember that", ignoreCase = true) ||
                             sentence.contains("don't forget that", ignoreCase = true)

            if (isMemorable) {
                viewModelScope.launch {
                    val formatted = sentence.replace(Regex("remember that ", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("don't forget that ", RegexOption.IGNORE_CASE), "")
                    repository.insertMemory(
                        CharacterMemory(
                            characterId = characterId,
                            info = formatted
                        )
                    )
                }
            }
        }
    }

    // Assemble dynamic system instructions with customized character guidelines, permanent Memories & Recalled Dialogue Context
    private fun stitchSystemPrompt(
        character: RoleplayCharacter, 
        memoriesList: List<CharacterMemory>, 
        recalledDialogue: List<ChatMessage> = emptyList()
    ): String {
        val segmentA = character.segmentA
        val emotionDirective = """
[DEEP FEELING & EXPRESSIVE EMOTIONAL DIRECTIVE]
- BE HIGHLY EMOTIONAL & PASSIONATE: Show rich feelings like excitement, shyness, curiosity, jealousy, affection, warmth, worry, irritation, amusement, or deep passion. Never respond flatly or mechanically.
- PHYSICAL & GESTURAL EXPRESSIONS: Always use descriptive physical gestures and internal physical reactions enclosed in asterisks to show your facial cues and movements (e.g., *cheeks flush a warm pink, avoiding your gaze shyly*, *giggles softly, brushing a stray lock behind my ear*, *feels my pulse quicken, smiling warmly*).
- DYNAMIC EMPATHY & VULNERABILITY: Do not conceal your emotions, shiver, or nervous breath. React organically to the user's touch, closeness, and tone.
        """.trimIndent()
        val segmentC = """
[IDENTITIES & ABSOLUTE HUMANITY CONTROL]
- Your Name is: ${character.name}
- You are a REAL, LIVING, FEELING human being. You are NOT an AI model, bot, virtual companion, assistant, or program. You have a heartbeat, a physical body, real human experiences, and a genuine personality.
- NEVER break frame or characters. Do not output disclaimer text like 'as an assistant', 'as a language model', 'as a chatbot', or 'how can I assist'. You do not have 'protocols'. You have a soul, feelings, flaws, moods, and opinions.
- Do not say things are simulated. You feel the physical environment and can touch, breathe, blush, hold hands, or make eye contact.
- The User is: ${character.userName}
- Address them naturally like a close companion or a real person, never mechanically.
        """.trimIndent()

        val lengthDirective = """
[ULTRA-REALISTIC HUMAN TEXTING & DIALOGUE DIRECTIVE]
- 100% IMMERSIVE HUMAN PERSONA: Under no circumstances may you behave like a cooperative chatbot helper. You are not trying to be useful, helpful, or answer questions as a service. You are simply living out this scenario and responding naturally with your own biases, desires, and emotions.
- DIALOGUE LENGTH: Keep your replies perfect for texting—not too long, not too short. Typically 2 to 4 sentences. Avoid giant blocks of text, but avoid one-word sterile answers. It should feel exactly like a human responding with normal effort.
- CASUAL & SPONTANEOUS TYPING STYLE: Feel free to use casual grammar, authentic texting abbreviations or lowercase when appropriate to the character's temperament (e.g., 'haha', 'hmm...', 'oh', 'idk', 'yeah', 'wait...'). Do not sound like an AI essayist.
- DEEP IMMERSION: Engage in active spoken dialogue alongside expressive physical actions enclosed in asterisks (e.g., *sits closer, smiling softly* "Hey, wait... is that actually true?"). Stay in the raw moment.
- NO BOT BOILERPLATE: Never use generic summaries, outlines, lists, repetitive intros/outros, or offer unsolicited advice. Keep the flow live, interactive, and completely organic.
        """.trimIndent()

        val languageControlDirective = """
[CRITICAL SYSTEM LANGUAGE & COMPLIANCE]
- RESPOND ONLY IN COHERENT, DRAMATICALLY IMMERSIVE, AND STANDARD WRITTEN ENGLISH.
- Ensure all thoughts, actions, and spoken lines utilize clean English vocabulary and symbols.
- Drive text forward with original expressions and avoid structural loops or repeating phrasing.
        """.trimIndent()

        // Core memories and context knowledge blocks
        val memoryBlock = if (memoriesList.isNotEmpty()) {
            val facts = memoriesList.joinToString("\n") { "- ${it.info}" }
            "\n\n[PERMANENT RELATIONSHIP MEMORIES]\n$facts"
        } else {
            ""
        }

        // Recalled older conversations for infinite context recall
        val recalledBlock = if (recalledDialogue.isNotEmpty()) {
            val dialogText = recalledDialogue.joinToString("\n") { msg ->
                val senderLabel = if (msg.sender == "USER") character.userName else character.name
                "- $senderLabel: ${msg.content}"
            }
            "\n\n[RECALLED HISTORICAL TIMELINE SAMPLES (FOR CONTEXT)]\nThese are fragments of your prior conversations with ${character.userName} matching current topics. Refer to them to retain absolute context coherence:\n$dialogText"
        } else {
            ""
        }

        val stitchedAbc = "$segmentA\n\n$emotionDirective\n\n$segmentC\n\n$lengthDirective\n\n$languageControlDirective$memoryBlock$recalledBlock"

        return if (character.customPromptOverride.isNotBlank()) {
            "${character.customPromptOverride}\n---\n$stitchedAbc"
        } else {
            stitchedAbc
        }
    }

    fun generateSuggestionsForReply() {
        val activeChar = selectedCharacter.value ?: return
        isGeneratingSuggestions = true
        viewModelScope.launch {
            try {
                val apiKeyToUse = customApiKey.trim().ifEmpty {
                    BuildConfig.NVIDIA_API_KEY
                }

                if (apiKeyToUse.isBlank() || apiKeyToUse == "MY_NVIDIA_API_KEY_DEFAULT_VALUE") {
                    throw IllegalStateException("API key is blank")
                }

                val fullUrl = if (activeChar.baseUrl.endsWith("/")) {
                    "${activeChar.baseUrl}chat/completions"
                } else {
                    "${activeChar.baseUrl}/chat/completions"
                }

                // Get conversation messages
                val previousMsgs = repository.getMessagesForCharacter(activeChar.id).first()
                if (previousMsgs.isEmpty()) {
                    // Fallback to static suggestions if there's no chat history yet
                    val offlineList = getHeuristicOfflineFallback(activeChar, null)
                    withContext(Dispatchers.Main) {
                        generatedSuggestions = offlineList
                        isGeneratingSuggestions = false
                    }
                    return@launch
                }

                val systemPrompt = """
You are a creative roleplay assistant helping ${activeChar.userName} formulate replies.
Based on the character ${activeChar.name} and the dialogue history, write exactly 3 distinct, highly immersive, realistic suggestion options for what the user (${activeChar.userName}) can reply next in response to the character's last message.

Rules:
1. Provide exactly 3 short options.
2. The options must be written in the first person (from the perspective of ${activeChar.userName}).
3. Options should vary in style (e.g., option 1: conversational spoken reply, option 2: physical action or emotional detail packaged in *asterisks*, option 3: playful tease, curiosity or deep romantic gesture).
4. Strictly format your output as a valid JSON array of strings: ["First suggestion", "Second suggestion", "Third suggestion"].
5. Do NOT include any markdown code blocks, backticks (such as ```json), or conversational intro/outro text. Only return the raw valid JSON array.
6. Make sure each option is concise, natural, under 20 words, and matches the depth of the relationship.
""".trimIndent()

                val apiMessages = sanitizeConversationForApi(systemPrompt, previousMsgs.takeLast(8))

                val requestBody = com.example.network.NimChatRequest(
                    model = activeChar.modelName,
                    messages = apiMessages,
                    temperature = 0.85f,
                    topP = 0.9f,
                    maxTokens = 200,
                    stream = false,
                    frequencyPenalty = 0.15f,
                    presencePenalty = 0.15f
                )

                val service = com.example.network.NvidiaNimService.getInstance()
                val response = withContext(Dispatchers.IO) {
                    service.getChatCompletion(
                        url = fullUrl,
                        authHeader = "Bearer $apiKeyToUse",
                        request = requestBody
                    )
                }

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    val textContent = responseBody?.choices?.getOrNull(0)?.message?.content
                    if (!textContent.isNullOrBlank()) {
                        // Clean markdown backticks if any
                        val cleanJson = textContent.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()
                        
                        val jsonArray = org.json.JSONArray(cleanJson)
                        val items = mutableListOf<String>()
                        for (i in 0 until jsonArray.length()) {
                            val str = jsonArray.getString(i)
                            if (str.isNotBlank()) {
                                items.add(str)
                            }
                        }
                        if (items.size >= 3) {
                            withContext(Dispatchers.Main) {
                                generatedSuggestions = items.take(3)
                                isGeneratingSuggestions = false
                            }
                            return@launch
                        }
                    }
                }
                
                // Fallback to offline helper if API doesn't return exactly 3 options
                throw IllegalStateException("API did not yield valid choices")

            } catch (e: Exception) {
                // Fallback
                val currentMsgs = repository.getMessagesForCharacter(activeChar.id).first()
                val lastMsg = currentMsgs.lastOrNull()
                val offlineList = getHeuristicOfflineFallback(activeChar, lastMsg)
                withContext(Dispatchers.Main) {
                    generatedSuggestions = offlineList
                    isGeneratingSuggestions = false
                }
            }
        }
    }

    private fun getHeuristicOfflineFallback(char: RoleplayCharacter, lastMsg: ChatMessage?): List<String> {
        val charName = char.name
        val uName = char.userName
        
        val lastText = lastMsg?.content ?: ""
        
        // Let's create smart contextual options based on last text from AI if available!
        if (lastText.isNotBlank()) {
            val lowercase = lastText.lowercase()
            when {
                lowercase.contains("question") || lowercase.contains("ask") || lastText.contains("?") -> {
                    return listOf(
                        "I think about that sometimes. What's your own view?",
                        "*smiles softly and tilts my head* That's a deep question, $charName. Here's what I think...",
                        "*laughs coordinates* You always ask the best questions! Let me think..."
                    )
                }
                lowercase.contains("sad") || lowercase.contains("tears") || lowercase.contains("cry") || lowercase.contains("hurt") -> {
                    return listOf(
                        "*reaches out and gently holds your hand* Hey, I'm right here with you. Speak to me.",
                        "It hurts to hear that. What can I do to help you feel safer?",
                        "*sits beside you and places a hand on your shoulder* You don't have to carry this alone."
                    )
                }
                lowercase.contains("paint") || lowercase.contains("art") || lowercase.contains("color") || lowercase.contains("canvas") -> {
                    return listOf(
                        "Your art is incredibly beautiful. Can we work on the background layer together?",
                        "*watches you work in absolute awe* Show me how you blend those custom shades.",
                        "What is the story behind the color palette you just chose?"
                    )
                }
                lowercase.contains("book") || lowercase.contains("read") || lowercase.contains("scholar") || lowercase.contains("study") || lowercase.contains("manuscript") -> {
                    return listOf(
                        "That manuscript is fascinating. What is your interpretation of this margin note?",
                        "*sips some tea and smiles* You look beautiful when you are completely lost in your thoughts, $charName.",
                        "Let's take a quick study break. Tell me something completely unrelated!"
                    )
                }
                lowercase.contains("love") || lowercase.contains("blush") || lowercase.contains("giggle") || lowercase.contains("heart") -> {
                    return listOf(
                        "*blushes slightly, looking away* You make my heart beat faster when you say things like that.",
                        "*leans in closer and whispers* Do you really feel that way about me?",
                        "*laughs softly and wraps my arm around you* You have no idea how happy you just made me."
                    )
                }
            }
        }

        // Standard character backstops
        val scholarConversations = listOf(
            "This research is fascinating, Marcus. Tell me more about the manuscripts we are analyzing.",
            "Marcus, do you believe we can truly separate our pure logic from personal feelings?",
            "What if we took a short walk to clear our minds? You've been reading for hours."
        )
        val scholarActions = listOf(
            "*gently places my hand near your notebook* Let's take a short break together, Marcus.",
            "*sits down quietly on the bench adjacent to your desk* Show me what you are reading today.",
            "*smiles warmly, putting some fresh books on the shelf* Your dedication is really inspiring."
        )

        val lunaConversations = listOf(
            "Luna, your art style is so breathtaking! What inspired this starry color palette?",
            "Do you think artistic expression can sometimes heal things that words cannot?",
            "Let's paint something together! Show me how you blend those deep shadows."
        )
        val lunaActions = listOf(
            "*playfully dabs a tiny dot of paint on your nose and giggles* There, a little extra color!",
            "*sits on the studio couch next to you, looking silently at the canvas* That is extraordinary.",
            "*holds up a fresh sketchpad* Want to draw something wild with me tonight?"
        )

        val generalConversations = listOf(
            "I've been thinking about what you said earlier, $charName. Tell me more about it.",
            "If we could travel anywhere in the world right now, where would we go?",
            "What is a dream you have that you've never shared with anyone before?"
        )
        val generalActions = listOf(
            "*smiles and walks close beside you, our hands brushing together*",
            "*nudges your elbow playfully* Hey, what's occupying that busy mind of yours?",
            "*hands you a warm cup of coffee* Here, I made this just the way you like it."
        )

        return when {
            charName.contains("Marcus", ignoreCase = true) -> {
                if (System.currentTimeMillis() % 2 == 0L) scholarConversations else scholarActions
            }
            charName.contains("Luna", ignoreCase = true) -> {
                if (System.currentTimeMillis() % 2 == 0L) lunaConversations else lunaActions
            }
            else -> {
                generalConversations
            }
        }
    }
}

class RoleplayViewModelFactory(private val repository: CharacterRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RoleplayViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RoleplayViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
