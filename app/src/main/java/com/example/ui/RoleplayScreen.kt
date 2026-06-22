package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import com.example.data.ChatMessage
import com.example.data.RoleplayCharacter
import com.example.data.CharacterMemory
import kotlinx.coroutines.launch

// Base Layout colors
val DefaultSlateBg = Color(0xFF0E1119)
val CardGray = Color(0xFF1E2130)
val DarkBorder = Color(0xFF2C3148)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplayScreen(
    viewModel: RoleplayViewModel,
    modifier: Modifier = Modifier
) {
    val characters by viewModel.characters.collectAsStateWithLifecycle()
    val selectedCharacter by viewModel.selectedCharacter.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog state for edits, backtracks, and deletions
    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    var editTextState by remember { mutableStateOf("") }
    var messageToBacktrack by remember { mutableStateOf<ChatMessage?>(null) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var showSuggestionsWindow by remember { mutableStateOf(false) }
    var suggestionSeed by remember { mutableStateOf(0) }

    // Form inputs for consolidated configuration
    var isCreationMode by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    var inputUserName by remember { mutableStateOf("") }
    var inputSegmentA by remember { mutableStateOf("") }
    var inputPromptOverride by remember { mutableStateOf("") }
    var inputTemp by remember { mutableStateOf(0.85f) }
    var inputTopP by remember { mutableStateOf(0.95f) }
    var inputBaseUrl by remember { mutableStateOf("https://integrate.api.nvidia.com/v1") }
    var inputModelName by remember { mutableStateOf("meta/llama-3.1-8b-instruct") }

    // Theme Customization Form inputs
    var inputBackgroundColor by remember { mutableStateOf(0xFF0E1119.toInt()) }
    var inputThemeAccentColor by remember { mutableStateOf(0xFF8E7CC3.toInt()) }
    var inputBubbleColor by remember { mutableStateOf(0xFF1F243A.toInt()) }
    var inputBackgroundImageUri by remember { mutableStateOf("") }
    var inputBackgroundDim by remember { mutableStateOf(0.5f) }
    var inputCharBubbleColor by remember { mutableStateOf(0xFF25293E.toInt()) }
    var inputUserTextColor by remember { mutableStateOf(0xFFFFFFFF.toInt()) }
    var inputCharTextColor by remember { mutableStateOf(0xFFDCD6F7.toInt()) }

    // Consolidated Settings internal sub-tab indicator (0 = Persona, 1 = Theme, 2 = Long-Term Memories)
    var settingsSubTab by remember { mutableStateOf(0) }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    
    // Custom ContentScale to keep background images absolutely static and stable during keyboard resize
    val stableContentScale = remember {
        object : ContentScale {
            var maxSeenHeight = 0f
            override fun computeScaleFactor(
                srcSize: androidx.compose.ui.geometry.Size,
                dstSize: androidx.compose.ui.geometry.Size
            ): androidx.compose.ui.layout.ScaleFactor {
                if (dstSize.height > maxSeenHeight) {
                    maxSeenHeight = dstSize.height
                }
                val referenceHeight = if (maxSeenHeight > 0f) maxSeenHeight else dstSize.height
                val scaleX = dstSize.width / srcSize.width
                val scaleY = referenceHeight / srcSize.height
                val scale = maxOf(scaleX, scaleY)
                return androidx.compose.ui.layout.ScaleFactor(scale, scale)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                if (inputStream != null) {
                    val file = java.io.File(context.filesDir, "custom_bg_${System.currentTimeMillis()}.jpg")
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    inputBackgroundImageUri = file.absolutePath
                    android.widget.Toast.makeText(context, "Custom gallery background applied!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Error reading image file from galley.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to load background: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // Synchronize form fields of selected character
    LaunchedEffect(selectedCharacter, isCreationMode) {
        if (!isCreationMode && selectedCharacter != null) {
            val sChar = selectedCharacter!!
            inputName = sChar.name
            inputUserName = sChar.userName
            inputSegmentA = sChar.segmentA
            inputPromptOverride = sChar.customPromptOverride
            inputTemp = sChar.temperature
            inputTopP = sChar.topP
            inputBaseUrl = sChar.baseUrl
            inputModelName = sChar.modelName
            inputBackgroundColor = sChar.backgroundColor
            inputThemeAccentColor = sChar.themeAccentColor
            inputBubbleColor = sChar.bubbleColor
            inputBackgroundImageUri = sChar.backgroundImageUri
            inputBackgroundDim = sChar.backgroundDim
            inputCharBubbleColor = sChar.charBubbleColor
            inputUserTextColor = sChar.userTextColor
            inputCharTextColor = sChar.charTextColor
        } else if (isCreationMode) {
            inputName = ""
            inputUserName = "Adventurer"
            inputSegmentA = """
You are an actual human being texting on an app, not an AI. Be highly realistic, flawed, spontaneous, and extremely lifelike in your responses. React completely organically.

CRITICAL FORMATTING RULES:
All actions, internal thoughts, and narrations MUST be strictly enclosed in asterisks (e.g., *sips tea and smiles*).
Spoken dialogue must be written as plain text outside of asterisks.

ANTI-PUPPETING RULE:
NEVER control, speak for, or dictate the actions of the user. Only describe your own character's actions, thoughts, and dialogue.

Keep your responses punchy and extremely natural. Drive the story forward. Never monologue internally.
            """.trimIndent()
            inputPromptOverride = "A relaxing sunset conversation on a peaceful mountain edge as cool wind blows."
            inputTemp = 0.85f
            inputTopP = 0.95f
            inputBaseUrl = "https://integrate.api.nvidia.com/v1"
            inputModelName = "meta/llama-3.1-8b-instruct"
            inputBackgroundColor = 0xFF0E1119.toInt()
            inputThemeAccentColor = 0xFF8E7CC3.toInt()
            inputBubbleColor = 0xFF191D2E.toInt()
            inputBackgroundImageUri = ""
            inputBackgroundDim = 0.5f
            inputCharBubbleColor = 0xFF25293E.toInt()
            inputUserTextColor = 0xFFFFFFFF.toInt()
            inputCharTextColor = 0xFFDCD6F7.toInt()
        }
    }

    // Unified High-Performance Scroll Controller
    // This completely eliminates any competing scroll animations, removing all flickering and lag!
    @OptIn(ExperimentalLayoutApi::class)
    val isKeyboardOpen = WindowInsets.isImeVisible
    LaunchedEffect(messages.size, isKeyboardOpen) {
        if (messages.isNotEmpty()) {
            kotlinx.coroutines.delay(100) // Settle keyboard/layout transitions
            try {
                val lastMsg = messages.lastOrNull()
                if (lastMsg?.sender == "USER") {
                    // Smooth scroll only when user actively sends a message
                    lazyListState.animateScrollToItem(messages.size - 1)
                } else {
                    // Snap scroll instantly for AI replies to avoid competing with fast streaming rendering cycles
                    lazyListState.scrollToItem(messages.size - 1)
                }
            } catch (e: Exception) {}
        }
    }

    // Keep list scrolled to the bottom during live streaming with an auto-cancelling scroll effect.
    // By using snapshotFlow, we read viewModel.currentStreamingText inside the coroutine block.
    // This completely prevents top-level recomposition of the screen, achieving 0% lag and ultra-smooth scrolling!
    LaunchedEffect(viewModel.isStreaming) {
        if (viewModel.isStreaming) {
            snapshotFlow { viewModel.currentStreamingText }
                .collect { streamingText ->
                    if (streamingText.isNotBlank()) {
                        try {
                            val wordCount = streamingText.split(" ").size
                            // Scroll in increments of 10 words to keep layout stability pristine
                            if (wordCount % 10 == 0 || wordCount == 1) {
                                val targetIndex = if (messages.isNotEmpty()) messages.size else 0
                                lazyListState.scrollToItem(targetIndex)
                            }
                        } catch (e: Exception) {}
                    }
                }
        }
    }

    // Modal AlertDialog for Edit Actions (Supports editing both User and AI messages)
    if (messageToEdit != null) {
        AlertDialog(
            onDismissRequest = { messageToEdit = null },
            title = {
                Text(
                    text = if (messageToEdit?.sender == "USER") "Edit Your Message" else "Edit AI Companion's message",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = editTextState,
                        onValueChange = { editTextState = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(inputThemeAccentColor),
                            unfocusedBorderColor = DarkBorder
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (messageToEdit?.sender == "USER")
                            "Editing your text will backtrack succeeding message logs so the AI can weave dialogue from your new starting point."
                            else "This allows you to customize the companion's reply history locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val msg = messageToEdit!!
                        if (msg.sender == "USER") {
                            viewModel.editUserMessage(msg, editTextState)
                        } else {
                            viewModel.editAiMessage(msg, editTextState)
                        }
                        messageToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(inputThemeAccentColor))
                ) {
                    Text("Apply Edit", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToEdit = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = CardGray,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal AlertDialog for Backtrack Confirmation
    if (messageToBacktrack != null) {
        AlertDialog(
            onDismissRequest = { messageToBacktrack = null },
            title = { Text("Rewind Story Here?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This will permanently clear all messages after this bubble, rewinding the history so you can text back starting from here.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.backtrackToMessage(messageToBacktrack!!)
                        messageToBacktrack = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Rewind", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToBacktrack = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = CardGray,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal AlertDialog for Delete Character/Companion Confirmation
    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text("Delete This Companion?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you absolutely sure you want to delete ${selectedCharacter?.name}? All conversation history and memories stored in SQLite will be permanently deleted.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedCharacter?.let { viewModel.deleteCharacter(it) }
                        showDeleteConfirmationDialog = false
                        viewModel.currentTab = 0
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = CardGray,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
        )
    }

    val activeBgColor = selectedCharacter?.backgroundColor?.let { Color(it) } ?: DefaultSlateBg
    val activeAccentColor = selectedCharacter?.themeAccentColor?.let { Color(it) } ?: Color(0xFF8E7CC3)

    Box(modifier = Modifier.fillMaxSize().background(activeBgColor)) {
        // Static Full-Screen Background Image (Will NOT scale, squeeze, or scroll when keyboard resizes screen height)
        if (viewModel.currentTab == 0 && selectedCharacter != null) {
            val activeChar = selectedCharacter!!
            if (!activeChar.backgroundImageUri.isNullOrBlank()) {
                val bgModel = remember(activeChar.backgroundImageUri) {
                    if (activeChar.backgroundImageUri.startsWith("/")) {
                        java.io.File(activeChar.backgroundImageUri)
                    } else {
                        activeChar.backgroundImageUri
                    }
                }
                AsyncImage(
                    model = bgModel,
                    contentDescription = "Custom Background Theme",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                )
                // Stable, clean background opacity/brightness dimmer with zero memory churn
                val overlayColor = remember(activeChar.backgroundDim) {
                    val dimFactor = 1f - activeChar.backgroundDim.coerceIn(0f, 1f)
                    Color.Black.copy(alpha = dimFactor)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayColor)
                )
            }
        }

        Scaffold(
            modifier = modifier
                .fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
            topBar = {
                val showBgImage = viewModel.currentTab == 0 && selectedCharacter != null && !selectedCharacter!!.backgroundImageUri.isNullOrBlank()
                Column(
                    modifier = Modifier
                        .background(if (showBgImage) Color.Transparent else activeBgColor)
                        .statusBarsPadding()
                        .padding(vertical = 4.dp)
                ) {
                // Application Top bar layout
                if (selectedCharacter != null && viewModel.currentTab == 0) {
                    val activeChar = selectedCharacter!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Character Info (Avatar details)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Avatar circle with dynamic border
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(activeChar.avatarColor).copy(alpha = 0.2f))
                                    .border(1.5.dp, Color(activeChar.avatarColor), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeChar.name.take(2).uppercase(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = activeChar.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${activeChar.userName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Right: Single Integrated Settings Gear Button (Unified tab 1 settings options)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { 
                                    settingsSubTab = 0
                                    viewModel.currentTab = 1 
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Console Theme & Preset Settings",
                                    tint = activeAccentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Simple Unified Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "LLaMA Console",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "NIM Nvidia High-Speed Roleplay Engine",
                                style = MaterialTheme.typography.labelSmall,
                                color = activeAccentColor
                            )
                        }
                        if (viewModel.currentTab == 1 && !isCreationMode) {
                            IconButton(
                                onClick = { 
                                    isCreationMode = true 
                                    inputName = ""
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "New preset", tint = activeAccentColor)
                            }
                        } else if (viewModel.currentTab == 1) {
                            TextButton(onClick = { viewModel.currentTab = 0 }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp), tint = activeAccentColor)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Back to Chat", color = activeAccentColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Characters Horizontal Selection bar in tab 0 (or list presets)
                if (characters.isNotEmpty() && viewModel.currentTab == 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        characters.forEach { char ->
                            val isChosen = char.id == selectedCharacter?.id
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (isChosen) Color(char.avatarColor).copy(alpha = 0.22f) else CardGray)
                                    .border(
                                        width = if (isChosen) 1.5.dp else 1.dp,
                                        color = if (isChosen) Color(char.avatarColor) else DarkBorder,
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .clickable {
                                        viewModel.selectCharacter(char.id)
                                        isCreationMode = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color(char.avatarColor))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = char.name,
                                    color = if (isChosen) Color.White else Color.LightGray,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                        
                        // "New Character" quick action button right in the scroll bar!
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(CardGray)
                                .border(1.dp, activeAccentColor.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                .clickable {
                                    isCreationMode = true
                                    settingsSubTab = 0
                                    viewModel.currentTab = 1
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create New Character",
                                tint = activeAccentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "New Character",
                                color = activeAccentColor,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {}, // Completely removed the bottom bar buttons as requested!
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = viewModel.currentTab,
                animationSpec = tween(250),
                label = "tab_fade",
                modifier = Modifier.fillMaxSize()
            ) { targetTab ->
                when (targetTab) {
                0 -> {
                    // Immersive Chat Interface
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                    ) {
                        if (selectedCharacter == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardGray),
                                    border = BorderStroke(1.dp, activeAccentColor.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 24.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RocketLaunch,
                                            contentDescription = "No Character Loaded",
                                            tint = activeAccentColor,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Companion Console Offline",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No companion is currently loaded. Build a custom AI persona with custom background styles, or retrieve the master presets.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        isCreationMode = true
                                        settingsSubTab = 0
                                        viewModel.currentTab = 1
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = activeAccentColor),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Create Custom Companion", fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = { viewModel.restoreDefaultCompanions() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = activeAccentColor),
                                    border = BorderStroke(1.dp, activeAccentColor.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = activeAccentColor)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Restore Master Presets", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            val activeChar = selectedCharacter!!
                            val activeAccentColor = remember(activeChar.themeAccentColor) { Color(activeChar.themeAccentColor) }
                            val activeCharBubbleColor = remember(activeChar.charBubbleColor) { Color(activeChar.charBubbleColor) }
                            val activeUserBubbleColor = remember(activeChar.bubbleColor) { Color(activeChar.bubbleColor) }
                            val activeCharTextColor = remember(activeChar.charTextColor) { Color(activeChar.charTextColor) }
                            val activeUserTextColor = remember(activeChar.userTextColor) { Color(activeChar.userTextColor) }

                            Box(modifier = Modifier.weight(1f)) {

                                // Conversation Chat Bubbles List
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 16.dp, top = 16.dp)
                                ) {
                                    if (messages.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 54.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Default.ChatBubbleOutline,
                                                        contentDescription = null,
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(54.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(14.dp))
                                                    Text(
                                                        text = "Start roleplaying with ${activeChar.name}!",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Dialogue is normal bold, physical actions in *asterisks*.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.LightGray,
                                                        textAlign = TextAlign.Center
                                                    )
                                                    
                                                    Spacer(modifier = Modifier.height(24.dp))
                                                    Text(
                                                        text = "Tap a starter to set dialogue:",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = activeAccentColor,
                                                        fontWeight = FontWeight.ExtraBold
                                                     )
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    
                                                    val starters = listOf(
                                                        "Hey! *looks up and smiles* What are you working on today?",
                                                        "*walks up and taps your shoulder playfully* Guess who?",
                                                        "Lovely evening, isn't it? *hands you a warm cup of coffee*"
                                                    )
                                                    starters.forEach { starterText ->
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(vertical = 5.dp)
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .background(CardGray.copy(alpha = 0.8f))
                                                                .border(1.dp, activeAccentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                                                .clickable {
                                                                    viewModel.inputMessage = starterText
                                                                }
                                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                                        ) {
                                                            Text(
                                                                text = starterText,
                                                                color = Color.White,
                                                                fontSize = 11.sp,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                textAlign = TextAlign.Center,
                                                                modifier = Modifier.fillMaxWidth()
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        items(messages, key = { it.id }) { msg ->
                                            val isUser = msg.sender == "USER"
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth(),
                                                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = if (isUser) activeChar.userName else activeChar.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (isUser) activeAccentColor else activeCharBubbleColor,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(bottom = 2.dp)
                                                    )
                                                }

                                                // Chat Message Content bubble
                                                Box(
                                                    modifier = Modifier
                                                        .widthIn(max = 300.dp)
                                                        .clip(
                                                            RoundedCornerShape(
                                                                topStart = 16.dp,
                                                                topEnd = 16.dp,
                                                                bottomStart = if (isUser) 16.dp else 4.dp,
                                                                bottomEnd = if (isUser) 4.dp else 16.dp
                                                            )
                                                        )
                                                        .background(
                                                            if (isUser) activeUserBubbleColor else activeCharBubbleColor
                                                        )
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isUser) DarkBorder else activeCharBubbleColor.copy(alpha = 0.40f),
                                                            shape = RoundedCornerShape(
                                                                topStart = 16.dp,
                                                                topEnd = 16.dp,
                                                                bottomStart = if (isUser) 16.dp else 4.dp,
                                                                bottomEnd = if (isUser) 4.dp else 16.dp
                                                            )
                                                        )
                                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Column {
                                                        ParsedMessageText(
                                                            content = msg.content,
                                                            dialogueColor = if (isUser) activeUserTextColor else activeCharTextColor
                                                        )
                                                    }
                                                }

                                                // Rewind backtrack + Inline Edit + Regenerate controls
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                                                ) {
                                                    // Edit button (functional for both User and AI)
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                editTextState = msg.content
                                                                messageToEdit = msg
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Edit message content",
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Edit", color = Color.Gray, fontSize = 11.sp)
                                                    }

                                                    // Backtrack button (Permanently rewrite from code timeline)
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                messageToBacktrack = msg
                                                            }
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.History,
                                                            contentDescription = "Backtrack to here",
                                                            tint = Color.Gray,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = "Backtrack",
                                                            color = Color.Gray,
                                                            fontSize = 11.sp
                                                        )
                                                    }

                                                    // Regenerate button (Visible only under AI's responses)
                                                    if (!isUser && !viewModel.isGenerating) {
                                                        Row(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .clickable {
                                                                    viewModel.regenerateAiReply(msg)
                                                                }
                                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Autorenew,
                                                                contentDescription = "Regenerate Reply",
                                                                tint = activeAccentColor,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                text = "Regenerate",
                                                                color = activeAccentColor,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Custom high-fidelity word-by-word simulated typewriter streaming effect using lambda provider
                                    // This bypasses top-level recomposition of RoleplayScreen completely, keeping typing 100% lag-free!
                                    if (viewModel.isStreaming) {
                                        // Avoid showing a duplicate streaming bubble if the database list has already received the final message
                                        val alreadySaved = messages.lastOrNull()?.let {
                                            it.sender == "AI" && it.content == viewModel.currentStreamingText
                                        } ?: false

                                        if (!alreadySaved) {
                                            item(key = "active_stream") {
                                                StreamingMessageBubble(
                                                    streamingTextProvider = { viewModel.currentStreamingText },
                                                    charName = activeChar.name,
                                                    activeCharTextColor = activeCharTextColor,
                                                    activeCharBubbleColor = activeCharBubbleColor
                                                )
                                            }
                                        }
                                    }
 
                                    // Real-time custom-themed typing effect (Only when not speaking live and logically correct)
                                    val showTypingIndicator = viewModel.isGenerating && !viewModel.isStreaming &&
                                            (messages.isEmpty() || messages.lastOrNull()?.sender == "USER")

                                    if (showTypingIndicator) {
                                        item(key = "typing_indicator") {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth(),
                                                horizontalAlignment = Alignment.Start
                                            ) {
                                                Text(
                                                    text = "${activeChar.name} is drafting details...",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = activeCharTextColor,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(bottom = 2.dp)
                                                )
 
                                                TypingIndicatorBubble(
                                                    activeCharTextColor = activeCharTextColor,
                                                    activeCharBubbleColor = activeCharBubbleColor
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Error Network Details
                            AnimatedVisibility(
                                visible = viewModel.errorMessage != null,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF331622)),
                                    border = BorderStroke(1.dp, Color(0xFFFF5252))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFFF5252)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = viewModel.errorMessage ?: "",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.errorMessage = null },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close Error",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // SUGGESTED DIALOGUE OPTIONS OVERLAY (Lightbulb suggestions)
                            AnimatedVisibility(
                                visible = showSuggestionsWindow,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = CardGray),
                                    border = BorderStroke(1.dp, activeAccentColor.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Lightbulb,
                                                    contentDescription = null,
                                                    tint = activeAccentColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Suggested Replies",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Refresh suggestion button (queries API dynamically based on last AI text!)
                                                IconButton(
                                                    onClick = { viewModel.generateSuggestionsForReply() },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = "Refresh Options",
                                                        tint = activeAccentColor,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IconButton(
                                                    onClick = { showSuggestionsWindow = false },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Close suggestions",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        if (viewModel.isGeneratingSuggestions) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = activeAccentColor,
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "Drafting suggestions based on AI's text...",
                                                    color = Color.Gray,
                                                    fontSize = 11.sp,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        } else {
                                            val suggestions = viewModel.generatedSuggestions
                                            if (suggestions.isEmpty()) {
                                                Text(
                                                    text = "No suggestions ready yet. Click refresh to draft.",
                                                    color = Color.Gray,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                )
                                            } else {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    suggestions.forEach { optionText ->
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(Color.White.copy(alpha = 0.03f))
                                                                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                                .clickable {
                                                                    viewModel.inputMessage = optionText
                                                                    showSuggestionsWindow = false
                                                                }
                                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                                        ) {
                                                            Text(
                                                                text = optionText,
                                                                color = Color.White,
                                                                fontSize = 12.sp,
                                                                style = MaterialTheme.typography.bodySmall
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Custom High Fidelity Text Field Console
                            Surface(
                                tonalElevation = 8.dp,
                                color = activeBgColor,
                                border = BorderStroke(1.dp, DarkBorder),
                                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextField(
                                        value = viewModel.inputMessage,
                                        onValueChange = { viewModel.inputMessage = it },
                                        placeholder = {
                                            Text(
                                                text = "Message ${activeChar.name} as ${activeChar.userName}...",
                                                color = Color.Gray,
                                                fontSize = 14.sp
                                            )
                                        },
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color(0xFF151824),
                                            unfocusedContainerColor = Color(0xFF151824),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(
                                            onSend = {
                                                if (viewModel.inputMessage.isNotBlank() && !viewModel.isGenerating) {
                                                    viewModel.sendMessage()
                                                    scope.launch {
                                                        kotlinx.coroutines.delay(50)
                                                        try {
                                                            focusRequester.requestFocus()
                                                        } catch (e: Exception) {}
                                                    }
                                                }
                                            }
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        maxLines = 4,
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(focusRequester)
                                            .testTag("message_input")
                                            .padding(end = 6.dp)
                                    )

                                    // SMALL BULB BUTTON BESIDE SEND BUTTON
                                    IconButton(
                                        onClick = { 
                                            showSuggestionsWindow = !showSuggestionsWindow
                                            if (showSuggestionsWindow) {
                                                viewModel.generateSuggestionsForReply()
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (showSuggestionsWindow)
                                                    activeAccentColor.copy(alpha = 0.25f)
                                                else
                                                    Color(0xFF151824)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (showSuggestionsWindow) activeAccentColor else DarkBorder,
                                                shape = CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = "Get Suggestions",
                                            tint = if (showSuggestionsWindow) activeAccentColor else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.sendMessage()
                                            scope.launch {
                                                kotlinx.coroutines.delay(50)
                                                try {
                                                    focusRequester.requestFocus()
                                                } catch (e: Exception) {}
                                            }
                                        },
                                        enabled = viewModel.inputMessage.isNotBlank() && !viewModel.isGenerating,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (viewModel.inputMessage.isNotBlank() && !viewModel.isGenerating)
                                                    activeAccentColor
                                                else
                                                    Color.DarkGray
                                            )
                                            .testTag("submit_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Send",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // CONSOLIDATED SINGLE SETTINGS OPTION (Tab 1)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // High-fidelity tab headers inside settings
                        val activeAccentColorVal = Color(inputThemeAccentColor)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardGray)
                                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val subTabs = listOf(
                                Triple(0, Icons.Filled.Person, "Persona"),
                                Triple(1, Icons.Filled.Palette, "Theme"),
                                Triple(2, Icons.Filled.AutoAwesome, "Memories")
                            )
                            subTabs.forEach { (index, icon, title) ->
                                val isSubSel = settingsSubTab == index
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSubSel) activeAccentColorVal.copy(alpha = 0.2f) else Color.Transparent)
                                        .border(
                                            width = if (isSubSel) 1.dp else 0.dp,
                                            color = if (isSubSel) activeAccentColorVal else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { settingsSubTab = index }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = title,
                                        tint = if (isSubSel) activeAccentColorVal else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = title,
                                        color = if (isSubSel) Color.White else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (settingsSubTab == 0) {
                            // Section A: Companion Character Persona Config
                        Card(
                                    colors = CardDefaults.cardColors(containerColor = CardGray),
                                    border = BorderStroke(1.dp, DarkBorder)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = if (isCreationMode) "Create Custom Chat Preset" else "Edit Persona & Model Settings",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        OutlinedTextField(
                                            value = inputName,
                                            onValueChange = { inputName = it },
                                            label = { Text("Companion Character Name (AI)", color = Color.Gray) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = activeAccentColor,
                                                unfocusedBorderColor = DarkBorder
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        OutlinedTextField(
                                            value = inputUserName,
                                            onValueChange = { inputUserName = it },
                                            label = { Text("Your Roleplay Character Name (User)", color = Color.Gray) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = activeAccentColor,
                                                unfocusedBorderColor = DarkBorder
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        var isSystemPromptsExpanded by remember { mutableStateOf(false) }

                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
                                            border = BorderStroke(1.dp, if (isSystemPromptsExpanded) activeAccentColor else DarkBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { isSystemPromptsExpanded = !isSystemPromptsExpanded },
                                                     horizontalArrangement = Arrangement.SpaceBetween,
                                                     verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Code, contentDescription = null, tint = activeAccentColor, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Developer System Prompts",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                     }
                                                     Icon(
                                                         imageVector = if (isSystemPromptsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                         contentDescription = null,
                                                         tint = Color.LightGray,
                                                         modifier = Modifier.size(18.dp)
                                                     )
                                                 }

                                                 if (isSystemPromptsExpanded) {
                                                     Spacer(modifier = Modifier.height(12.dp))

                                                     OutlinedTextField(
                                                         value = inputSegmentA,
                                            onValueChange = { inputSegmentA = it },
                                            label = { Text("Realism Prompter (System Segment A)", color = Color.Gray) },
                                            minLines = 4,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = activeAccentColor,
                                                unfocusedBorderColor = DarkBorder
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        OutlinedTextField(
                                            value = inputPromptOverride,
                                            onValueChange = { inputPromptOverride = it },
                                            label = { Text("Scenery & Environment Context (Segment D)", color = Color.Gray) },
                                            minLines = 2,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = activeAccentColor,
                                                unfocusedBorderColor = DarkBorder
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Advanced Model Engine expansion
                                var isApiCardExpanded by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.height(12.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardGray),
                                    border = BorderStroke(1.dp, if (isApiCardExpanded) activeAccentColor else DarkBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { isApiCardExpanded = !isApiCardExpanded },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.SettingsApplications, contentDescription = null, tint = activeAccentColor)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "NVIDIA NIM Models",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Icon(
                                                imageVector = if (isApiCardExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = Color.LightGray
                                            )
                                        }

                                        if (isApiCardExpanded) {
                                            Spacer(modifier = Modifier.height(12.dp))

                                            OutlinedTextField(
                                                value = inputBaseUrl,
                                                onValueChange = { inputBaseUrl = it },
                                                label = { Text("NVIDIA NIM Base URL Endpoint", color = Color.Gray) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = activeAccentColor,
                                                    unfocusedBorderColor = DarkBorder
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                             Text(
                                                text = "Select Conversation Engine:",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            // Render Recommended selection cells
                                            val recommendedModels = listOf(
                                                "meta/llama-3.3-70b-instruct" to "Llama 3.3 (70B) - Highly Smart & Vivid",
                                                "nvidia/llama-3.1-nemotron-70b-instruct" to "Nemotron (70B) - Realistic Dialogue",
                                                "meta/llama-3.1-8b-instruct" to "Llama 3.1 (8B) - Fast & Compact Engine",
                                                "mistralai/mixtral-8x22b-instruct-v0.1" to "Mixtral (8x22B) - Deep Creative Flow"
                                            )
                                            
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                recommendedModels.forEach { (mId, mDesc) ->
                                                    val isSel = inputModelName == mId
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (isSel) activeAccentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.02f))
                                                            .border(
                                                                width = 1.dp,
                                                                color = if (isSel) activeAccentColor else DarkBorder,
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                            .clickable { inputModelName = mId }
                                                            .padding(10.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = mId,
                                                                color = if (isSel) Color.White else Color.LightGray,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp
                                                            )
                                                            Text(
                                                                text = mDesc,
                                                                color = Color.Gray,
                                                                fontSize = 10.sp
                                                            )
                                                        }
                                                        if (isSel) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Selected",
                                                                tint = activeAccentColor,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))
                                            
                                            // Expander block for custom API Key models
                                            var showCustomModelFetcher by remember { mutableStateOf(false) }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showCustomModelFetcher = !showCustomModelFetcher }
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Configure Custom Models / Fetcher",
                                                    color = activeAccentColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                    imageVector = if (showCustomModelFetcher) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    tint = activeAccentColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            if (showCustomModelFetcher) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Live Fetched NIMs:", color = Color.Gray, fontSize = 11.sp)
                                                    var isModelDropdownExpanded by remember { mutableStateOf(false) }
                                                    Box {
                                                        Button(
                                                            onClick = {
                                                                viewModel.fetchAvailableModels(inputBaseUrl, viewModel.customApiKey.trim())
                                                                isModelDropdownExpanded = true
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = activeAccentColor.copy(alpha = 0.2f)),
                                                            border = BorderStroke(1.dp, activeAccentColor),
                                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Query Token Keys", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 10.sp)
                                                        }
                                                        DropdownMenu(
                                                            expanded = isModelDropdownExpanded,
                                                            onDismissRequest = { isModelDropdownExpanded = false },
                                                            modifier = Modifier.background(CardGray).heightIn(max = 240.dp)
                                                        ) {
                                                            if (viewModel.availableModelsList.isEmpty()) {
                                                                androidx.compose.material3.DropdownMenuItem(
                                                                    text = { Text(text = "No custom models found or empty key", color = Color.Gray, fontSize = 11.sp) },
                                                                    onClick = { isModelDropdownExpanded = false }
                                                                )
                                                            } else {
                                                                viewModel.availableModelsList.forEach { mKey ->
                                                                    androidx.compose.material3.DropdownMenuItem(
                                                                        text = { Text(text = mKey, color = Color.White, fontSize = 12.sp) },
                                                                        onClick = {
                                                                            inputModelName = mKey
                                                                            isModelDropdownExpanded = false
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            OutlinedTextField(
                                                value = inputModelName,
                                                onValueChange = { inputModelName = it },
                                                label = { Text("Model Name Target", color = Color.Gray) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = activeAccentColor,
                                                    unfocusedBorderColor = DarkBorder
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Temp: ${String.format("%.2f", inputTemp)}",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Slider(
                                                        value = inputTemp,
                                                        onValueChange = { inputTemp = it },
                                                        valueRange = 0.1f..1.5f,
                                                        colors = SliderDefaults.colors(thumbColor = activeAccentColor, activeTrackColor = activeAccentColor, inactiveTrackColor = DarkBorder)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "Top P: ${String.format("%.2f", inputTopP)}",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Slider(
                                                        value = inputTopP,
                                                        onValueChange = { inputTopP = it },
                                                        valueRange = 0.1f..1.0f,
                                                        colors = SliderDefaults.colors(thumbColor = activeAccentColor, activeTrackColor = activeAccentColor, inactiveTrackColor = DarkBorder)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            OutlinedTextField(
                                                value = viewModel.customApiKey,
                                                onValueChange = { 
                                                    viewModel.customApiKey = it
                                                    if (it.isNotBlank()) {
                                                        viewModel.fetchAvailableModels(inputBaseUrl, it.trim())
                                                    }
                                                },
                                                label = { Text("Active ID NVIDIA NIM Token Key", color = Color.Gray) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White,
                                                    focusedBorderColor = activeAccentColor,
                                                    unfocusedBorderColor = DarkBorder
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                        }

                        if (settingsSubTab == 1) {
                                // Section B: High-fidelity FULL Customization of Background & Colors!
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardGray),
                                    border = BorderStroke(1.dp, DarkBorder)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Dynamic Theme customizer",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tailor the interface background and accent highlight tones to your story atmosphere.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // 1. Background Theme Selection Presets
                                        Text("1. Console Background Theme:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val backgroundsList = listOf(
                                            Triple("Cosmic Dark", 0xFF0E1119, "Deep cosmic space"),
                                            Triple("Stealth Black", 0xFF050505, "True AMOLED premium back"),
                                            Triple("Sepia Study", 0xFF14100D, "Warm academic leather brown"),
                                            Triple("Ocean Midnight", 0xFF081215, "Cool marine blue"),
                                            Triple("Void Violet", 0xFF100D1A, "Futuristic electric indigo tint")
                                        )
                                        backgroundsList.forEach { (label, colorInt, desc) ->
                                            val isSelected = colorInt.toInt() == inputBackgroundColor
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(if (isSelected) Color(colorInt).copy(alpha = 0.2f) else Color.Transparent)
                                                    .border(1.2.dp, if (isSelected) activeAccentColor else DarkBorder, RoundedCornerShape(10.dp))
                                                    .clickable { inputBackgroundColor = colorInt.toInt() }
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(colorInt))
                                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(text = label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    Text(text = desc, color = Color.Gray, fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // 2. High-contrast Accent Highlight presets
                                        Text("2. Theme Accent Highlights:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val accentsList = listOf(
                                            "Llama Lavender" to 0xFF8E7CC3,
                                            "Mint Spark" to 0xFF20B2AA,
                                            "Electric Cyan" to 0xFF00FFCC,
                                            "Tangerine Gold" to 0xFFE0A96D,
                                            "Cyber Punk Rose" to 0xFFEFB8C8,
                                            "Lotus Bloom Orchid" to 0xFFB19DFF
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            accentsList.forEach { (label, colorVal) ->
                                                val isSelected = colorVal.toInt() == inputThemeAccentColor
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isSelected) Color(colorVal).copy(alpha = 0.25f) else CardGray)
                                                        .border(1.2.dp, if (isSelected) Color(colorVal) else Color.Transparent, RoundedCornerShape(12.dp))
                                                        .clickable { 
                                                            inputThemeAccentColor = colorVal.toInt() 
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(colorVal)))
                                                        Text(text = label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // 3. User Text Message Bubble design choices
                                        Text("3. Chat Bubble Backdrop Base:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val bubbleBackings = listOf(
                                            "Slate Navy" to 0xFF191D2E,
                                            "Charcoal Metal" to 0xFF252528,
                                            "Earthy Core" to 0xFF2A1D15,
                                            "Forest Pine" to 0xFF10282E,
                                            "Midnight Velvet" to 0xFF221133
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            bubbleBackings.forEach { (label, colorInt) ->
                                                val isSelected = colorInt.toInt() == inputBubbleColor
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isSelected) Color(colorInt).copy(alpha = 0.3f) else CardGray)
                                                        .border(1.dp, if (isSelected) activeAccentColor else DarkBorder, RoundedCornerShape(12.dp))
                                                        .clickable { inputBubbleColor = colorInt.toInt() }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Text(text = label, color = Color.White, fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // 4. Custom Background Image
                                        Text("4. Static Background Image:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    galleryLauncher.launch("image/*")
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = activeAccentColor,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.weight(1f).testTag("select_from_gallery")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Pick Image",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Pick Image from Gallery", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text("Or paste a URL/URI instead:", color = Color.Gray, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Text input field for URL
                                        OutlinedTextField(
                                            value = inputBackgroundImageUri,
                                            onValueChange = { inputBackgroundImageUri = it },
                                            label = { Text("Paste Custom Background Image URL / URI", color = Color.Gray, fontSize = 12.sp) },
                                            placeholder = { Text("https://images.unsplash.com/...", color = Color.DarkGray, fontSize = 11.sp) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = activeAccentColor,
                                                unfocusedBorderColor = DarkBorder,
                                                focusedLabelColor = activeAccentColor,
                                                unfocusedLabelColor = Color.Gray
                                            ),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().testTag("bg_image_input"),
                                            trailingIcon = {
                                                if (inputBackgroundImageUri.isNotBlank()) {
                                                    IconButton(onClick = { inputBackgroundImageUri = "" }) {
                                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear background", tint = Color.Gray)
                                                     }
                                                 }
                                             }
                                         )
                                         
                                         Spacer(modifier = Modifier.height(10.dp))
                                         
                                         Text("Aesthetic Preset Backdrops:", color = Color.Gray, fontSize = 11.sp, fontStyle = FontStyle.Italic)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         
                                         // Row of image preset option cards (We provide gorgeous unsplash preset options)
                                         val bgPresets = listOf(
                                             Triple("Cozy Room", "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?q=80&w=600", "Anime style study"),
                                             Triple("Neon City", "https://images.unsplash.com/photo-1545239351-ef35f43d514b?q=80&w=600", "Cyberpunk neon"),
                                             Triple("Nebula Space", "https://images.unsplash.com/photo-1462331940025-496dfbfc7564?q=80&w=600", "Cosmic stars"),
                                             Triple("Academic Cafe", "https://images.unsplash.com/photo-1481627834876-b7833e8f5570?q=80&w=600", "Candlelit library"),
                                             Triple("Mist Pines", "https://images.unsplash.com/photo-1506744038136-46273834b3fb?q=80&w=600", "Foggy mountains")
                                         )
                                         
                                         Row(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .horizontalScroll(rememberScrollState()),
                                             horizontalArrangement = Arrangement.spacedBy(8.dp)
                                         ) {
                                             bgPresets.forEach { (name, url, d) ->
                                                 val isSelected = inputBackgroundImageUri == url
                                                 Box(
                                                     modifier = Modifier
                                                         .width(92.dp)
                                                         .height(60.dp)
                                                         .clip(RoundedCornerShape(8.dp))
                                                         .background(CardGray)
                                                         .border(
                                                             width = if (isSelected) 2.dp else 1.dp,
                                                             color = if (isSelected) activeAccentColor else DarkBorder,
                                                             shape = RoundedCornerShape(8.dp)
                                                         )
                                                         .clickable { inputBackgroundImageUri = url }
                                                 ) {
                                                     AsyncImage(
                                                         model = url,
                                                         contentDescription = name,
                                                         contentScale = ContentScale.Crop,
                                                         modifier = Modifier.fillMaxSize()
                                                     )
                                                     Box(
                                                         modifier = Modifier
                                                             .fillMaxSize()
                                                             .background(Color.Black.copy(alpha = 0.4f))
                                                     )
                                                     Column(
                                                         modifier = Modifier
                                                             .fillMaxSize()
                                                             .padding(4.dp),
                                                         verticalArrangement = Arrangement.Center,
                                                         horizontalAlignment = Alignment.CenterHorizontally
                                                     ) {
                                                         Text(
                                                             text = name,
                                                             color = Color.White,
                                                             fontSize = 11.sp,
                                                             fontWeight = FontWeight.Bold,
                                                             textAlign = TextAlign.Center
                                                         )
                                                     }
                                                 }
                                             }
                                         }
                                         Spacer(modifier = Modifier.height(20.dp))

                                         // 5. Adjust Background Brightness Dimmer
                                         Text(
                                             text = "5. Adjust Background Brightness Dimmer:",
                                             color = Color.White,
                                             fontWeight = FontWeight.Bold,
                                             fontSize = 13.sp
                                         )
                                         Text(
                                             text = "Slide to adjust backdrop visibility overlay from completely black (0%) to fully bright (100%).",
                                             color = Color.Gray,
                                             fontSize = 11.sp
                                         )
                                         Spacer(modifier = Modifier.height(8.dp))
                                         Row(
                                             verticalAlignment = Alignment.CenterVertically,
                                             modifier = Modifier.fillMaxWidth()
                                         ) {
                                             Icon(Icons.Default.BrightnessLow, contentDescription = "Dim", tint = Color.Gray)
                                             Spacer(modifier = Modifier.width(8.dp))
                                             Slider(
                                                 value = inputBackgroundDim,
                                                 onValueChange = { inputBackgroundDim = it },
                                                 valueRange = 0f..1f,
                                                 colors = SliderDefaults.colors(
                                                     thumbColor = activeAccentColor,
                                                     activeTrackColor = activeAccentColor,
                                                     inactiveTrackColor = DarkBorder
                                                 ),
                                                 modifier = Modifier.weight(1f).testTag("brightness_dimmer_slider")
                                             )
                                             Spacer(modifier = Modifier.width(8.dp))
                                             Icon(Icons.Default.BrightnessHigh, contentDescription = "Bright", tint = Color.LightGray)
                                         }
                                         Text(
                                             text = "Current Visibility Level: ${(inputBackgroundDim * 100).toInt()}%",
                                             color = activeAccentColor,
                                             fontSize = 11.sp,
                                             fontWeight = FontWeight.Bold
                                         )

                                         Spacer(modifier = Modifier.height(24.dp))

                                         // 6. Messenger Dialogue Styling Panel
                                         Text(
                                             text = "6. Messenger Dialogue Styling Panel:",
                                             color = Color.White,
                                             fontWeight = FontWeight.Bold,
                                             fontSize = 13.sp
                                         )
                                         Text(
                                             text = "Design custom background colors and text contrasts for both sides of the chat bubble thread.",
                                             color = Color.Gray,
                                             fontSize = 11.sp
                                         )
                                         Spacer(modifier = Modifier.height(14.dp))

                                         val dialogPaletteHexes = remember {
                                             buildList {
                                                 // Core Grayscale & Brand
                                                 add(0xFF1F243A.toInt()); add(0xFF121420.toInt()); add(0xFF252528.toInt())
                                                 add(0xFF1E1E1E.toInt()); add(0xFF000000.toInt()); add(0xFF555555.toInt())
                                                 add(0xFFAAAAAA.toInt()); add(0xFFFFFFFF.toInt())
                                                 // Generate complete Hue spectrum across 3 brightness levels
                                                 for (h in 0..35) { // 36 hues * 10 = 360 degrees
                                                     for (v in listOf(0.2f, 0.45f, 0.75f, 1.0f)) { 
                                                         val hsv = floatArrayOf(h * 10f, 0.9f, v)
                                                         val colorInt = android.graphics.Color.HSVToColor(hsv)
                                                         add(colorInt)
                                                     }
                                                 }
                                             }
                                         }

                                         // A. User Message Bubble Color Background Selection
                                         Text("• User Message Box Background Color:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         Row(
                                             modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                             horizontalArrangement = Arrangement.spacedBy(6.dp)
                                         ) {
                                             dialogPaletteHexes.forEach { hexVal ->
                                                 val isSel = inputBubbleColor == hexVal.toInt()
                                                 Box(
                                                     modifier = Modifier
                                                         .size(32.dp)
                                                         .clip(CircleShape)
                                                         .background(Color(hexVal))
                                                         .border(
                                                             width = if (isSel) 2.5.dp else 1.dp,
                                                             color = if (isSel) activeAccentColor else Color.White.copy(alpha = 0.3f),
                                                             shape = CircleShape
                                                         )
                                                         .clickable { inputBubbleColor = hexVal.toInt() }
                                                 )
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(14.dp))

                                         // B. Companion Message Bubble Color Background Selection
                                         Text("• Companion Message Box Background Color:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         Row(
                                             modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                             horizontalArrangement = Arrangement.spacedBy(6.dp)
                                         ) {
                                             dialogPaletteHexes.forEach { hexVal ->
                                                 val isSel = inputCharBubbleColor == hexVal.toInt()
                                                 Box(
                                                     modifier = Modifier
                                                         .size(32.dp)
                                                         .clip(CircleShape)
                                                         .background(Color(hexVal))
                                                         .border(
                                                             width = if (isSel) 2.5.dp else 1.dp,
                                                             color = if (isSel) activeAccentColor else Color.White.copy(alpha = 0.3f),
                                                             shape = CircleShape
                                                         )
                                                         .clickable { inputCharBubbleColor = hexVal.toInt() }
                                                 )
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(14.dp))

                                         // C. User Message Text Color Selection
                                         Text("• User Message Font Text Color:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         Row(
                                             modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                             horizontalArrangement = Arrangement.spacedBy(6.dp)
                                         ) {
                                             listOf(0xFFFFFFFF, 0xFFDCD6F7, 0xFF00FFCC, 0xFF20B2AA, 0xFFE0A96D, 0xFFEFB8C8, 0xFFFFE4E1, 0xFFC0C0C0).forEach { hexVal ->
                                                 val isSel = inputUserTextColor == hexVal.toInt()
                                                 Box(
                                                     modifier = Modifier
                                                         .size(32.dp)
                                                         .clip(CircleShape)
                                                         .background(Color(hexVal))
                                                         .border(
                                                             width = if (isSel) 2.5.dp else 1.dp,
                                                             color = if (isSel) activeAccentColor else Color.White.copy(alpha = 0.3f),
                                                             shape = CircleShape
                                                         )
                                                         .clickable { inputUserTextColor = hexVal.toInt() }
                                                 )
                                             }
                                         }

                                         Spacer(modifier = Modifier.height(14.dp))

                                         // D. Companion Message Text Color Selection
                                         Text("• Companion Message Font Text Color:", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                         Spacer(modifier = Modifier.height(6.dp))
                                         Row(
                                             modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                             horizontalArrangement = Arrangement.spacedBy(6.dp)
                                         ) {
                                             listOf(0xFFFFFFFF, 0xFFDCD6F7, 0xFF00FFCC, 0xFF20B2AA, 0xFFE0A96D, 0xFFEFB8C8, 0xFFFFE4E1, 0xFFC0C0C0).forEach { hexVal ->
                                                 val isSel = inputCharTextColor == hexVal.toInt()
                                                 Box(
                                                     modifier = Modifier
                                                         .size(32.dp)
                                                         .clip(CircleShape)
                                                         .background(Color(hexVal))
                                                         .border(
                                                             width = if (isSel) 2.5.dp else 1.dp,
                                                             color = if (isSel) activeAccentColor else Color.White.copy(alpha = 0.3f),
                                                             shape = CircleShape
                                                         )
                                                         .clickable { inputCharTextColor = hexVal.toInt() }
                                                 )
                                             }
                                         }
                                    }
                                }

                        }

                        if (settingsSubTab == 2) {
                                // Section C: Room permanent Memories & Environment facts
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CardGray),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = activeAccentColor)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Core Memory Engine: ${inputName.ifEmpty { "AI Companion" }}",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Add custom environmental facts or key long-term memories below. When you send messages, they get injected subconsciously so your partner never forgets!",
                                            fontSize = 11.sp,
                                            color = Color.LightGray
                                        )
                                    }
                                }

                                // Interactive prompt memory inserter
                                var localFactText by remember { mutableStateOf("") }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = localFactText,
                                        onValueChange = { localFactText = it },
                                        label = { Text("Add permanent memories or env context...", color = Color.Gray, fontSize = 11.sp) },
                                        placeholder = { Text("Ex: We are traveling across of Snowy peaks", color = Color.DarkGray) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = activeAccentColor,
                                            unfocusedBorderColor = DarkBorder
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (localFactText.isNotBlank()) {
                                                viewModel.addMemory(localFactText)
                                                localFactText = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = activeAccentColor),
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Add fact", tint = Color.White)
                                    }
                                }

                                Text(
                                    text = "Active Stored Facts (${memories.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = activeAccentColor,
                                    fontWeight = FontWeight.Bold
                                )

                                if (memories.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Memory, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(34.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("No memories or environmental laws registered.", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        memories.forEach { fact ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(CardGray)
                                                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = fact.info,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { viewModel.deleteMemory(fact) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Button(
                                            onClick = { viewModel.clearAllMemories() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.08f)),
                                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Reset Memory Matrix", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Save Actions (Applies changes to Room SQLite perfectly)
                        Button(
                            onClick = {
                                if (inputName.isNotBlank() && inputUserName.isNotBlank()) {
                                    if (isCreationMode) {
                                        viewModel.addNewCharacter(
                                            name = inputName,
                                            userName = inputUserName,
                                            segmentA = inputSegmentA,
                                            customPromptOverride = inputPromptOverride,
                                            temperature = inputTemp,
                                            topP = inputTopP,
                                            baseUrl = inputBaseUrl,
                                            modelName = inputModelName,
                                            avatarColor = inputThemeAccentColor,
                                            backgroundColor = inputBackgroundColor,
                                            themeAccentColor = inputThemeAccentColor,
                                            bubbleColor = inputBubbleColor,
                                            backgroundImageUri = inputBackgroundImageUri,
                                            backgroundDim = inputBackgroundDim,
                                            charBubbleColor = inputCharBubbleColor,
                                            userTextColor = inputUserTextColor,
                                            charTextColor = inputCharTextColor
                                        )
                                        isCreationMode = false
                                    } else {
                                        selectedCharacter?.let { current ->
                                            val updated = current.copy(
                                                name = inputName,
                                                userName = inputUserName,
                                                segmentA = inputSegmentA,
                                                customPromptOverride = inputPromptOverride,
                                                temperature = inputTemp,
                                                topP = inputTopP,
                                                baseUrl = inputBaseUrl,
                                                modelName = inputModelName,
                                                backgroundColor = inputBackgroundColor,
                                                themeAccentColor = inputThemeAccentColor,
                                                bubbleColor = inputBubbleColor,
                                                backgroundImageUri = inputBackgroundImageUri,
                                                backgroundDim = inputBackgroundDim,
                                                charBubbleColor = inputCharBubbleColor,
                                                userTextColor = inputUserTextColor,
                                                charTextColor = inputCharTextColor
                                            )
                                            viewModel.updateCurrentCharacter(updated)
                                        }
                                        viewModel.currentTab = 0 // Transition back back safely
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(inputThemeAccentColor)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_config_button"),
                            enabled = inputName.isNotBlank() && inputUserName.isNotBlank()
                        ) {
                            Text(
                                text = if (isCreationMode) "Create Persona & Styles" else "Save Changes & Exit Settings",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!isCreationMode && selectedCharacter != null) {
                            OutlinedButton(
                                onClick = {
                                    showDeleteConfirmationDialog = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Delete This Persona", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
}
}

@Composable
fun TypingIndicatorBubble(
    activeCharTextColor: Color,
    activeCharBubbleColor: Color,
    modifier: Modifier = Modifier
) {
    val typingTransition = rememberInfiniteTransition(label = "typing_indicator")
    val dot1Alpha by typingTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 0),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val dot2Alpha by typingTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 180),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val dot3Alpha by typingTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 360),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(activeCharBubbleColor.copy(alpha = 0.70f))
            .border(1.dp, activeCharBubbleColor.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = dot1Alpha }
                    .background(activeCharTextColor, shape = CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = dot2Alpha }
                    .background(activeCharTextColor, shape = CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer { alpha = dot3Alpha }
                    .background(activeCharTextColor, shape = CircleShape)
            )
        }
    }
}

@Composable
fun ParsedMessageText(
    content: String,
    modifier: Modifier = Modifier,
    dialogueColor: Color
) {
    val annotatedString = remember(content, dialogueColor) {
        buildAnnotatedString {
            val parts = content.split("*")
            for (i in parts.indices) {
                val part = parts[i]
                if (i % 2 == 1) {
                    // Physical gestures, descriptions, internal narrations in beautiful soft italicized highlight
                    withStyle(
                        style = SpanStyle(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Medium,
                            color = dialogueColor.copy(alpha = 0.75f)
                        )
                    ) {
                        append("*$part*")
                    }
                } else {
                    // Dialogue spoken text outside asterisks in bold custom text color
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = dialogueColor
                        )
                    ) {
                        append(part)
                    }
                }
            }
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    )
}

@Composable
fun StreamingMessageBubble(
    streamingTextProvider: () -> String,
    charName: String,
    activeCharTextColor: Color,
    activeCharBubbleColor: Color,
    modifier: Modifier = Modifier
) {
    val streaming = streamingTextProvider()
    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = charName,
                style = MaterialTheme.typography.labelSmall,
                color = activeCharTextColor.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        // Chat Message Content bubble
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(activeCharBubbleColor)
                .border(
                    width = 1.dp,
                    color = activeCharBubbleColor.copy(alpha = 0.40f),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 16.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                ParsedMessageText(
                    content = streaming,
                    dialogueColor = activeCharTextColor
                )
            }
        }
    }
}

private fun getSuggestionsForCharacter(
    char: com.example.data.RoleplayCharacter,
    lastMsg: com.example.data.ChatMessage?,
    seed: Int
): List<String> {
    val charName = char.name
    val userName = char.userName
    
    // Base collections of high-fidelity template formats
    val scholarConversations = listOf(
        "I was thinking about our last discussion, Marcus. Is there room for subversion of dogma in intellectual research?",
        "Marcus, how do you balance your objective analysis with your subjective human experiences?",
        "I brought some tea for us. Tell me more about the rare manuscripts you are currently decoding."
    )
    val scholarActions = listOf(
        "*lightly brushes my fingers over the antique book covers, looking at Marcus* Let's organize these folio pages together.",
        "*sits down on the wooden bench adjacent to Marcus's desk* I'm ready to learn. What topic are we examining today?",
        "*closes my books quietly and looks up at Marcus, smiling faintly* You look like you've been reading for hours. Take a breath."
    )
    val scholarRomantic = listOf(
        "*gently places my hand over your notebook hand* Marcus... let's pause the academic work for just five minutes.",
        "*notices your faint smile and blushes slightly* I actually value your intellectual strictness. It suits you.",
        "*leans in closer to read the margin text beside you, our shoulders almost touching* What is this cursive notation?"
    )

    val lunaConversations = listOf(
        "Luna! The evening colors are absolutely stunning. If they were paints, how would you blend them?",
        "Do you think wild expressiveness can sometimes lead to mistakes in life, or only in art?",
        "Tell me about the very first painting that made you cry."
    )
    val lunaActions = listOf(
        "*laughs, picking up a stray palette knife* Show me how you blend those starry blue layers, Luna!",
        "*sits on the roof beside Luna, absolute silence between us as we watch the crimson sun descend*",
        "*playfully ticks your cheek with my finger and runs behind a giant painting easel, giggling*"
    )
    val lunaRomantic = listOf(
        "*gently wraps my arms around your shoulders from behind, whispering* Your art is gorgeous, but you are the real masterpiece here.",
        "*smiles warmly, locking our eyes together* Let's promise to always paint our dreams together, okay?",
        "*leans in and wraps a paint-splattered scarf around both of our necks, laughing at our goofiness*"
    )

    val generalConversations = listOf(
        "Hey, $charName! Tell me something you've never shared with anyone else before.",
        "How has your day been so far? Tell me about the best part of it.",
        "If you could teleport us anywhere in the universe right now, where would we go?",
        "Let's play a game, $charName. If we were characters in a fantasy epic, what would our classes be?",
        "What's a song or a melody that reminds you of peaceful moments?"
    )
    val generalActions = listOf(
        "*nudges you playfully with my elbow and smiles* Are you always this serious, or do I just distract you?",
        "*walks over and sits down right next to you, looking into your eyes* Hey, what's occupying your thoughts?",
        "*holds up a fresh cup of coffee, letting the steam rise between us* Here, I made this exactly the way you like it.",
        "*stretches and looks up at the ceiling, sighing happily* It's just really peaceful when we hang out like this.",
        "*walks slowly beside you, our hands brushing against each other occasionally as we enjoy the fresh air*"
    )
    val generalRomantic = listOf(
        "*reaches over and gently takes your hand, squeezing it warmly* Don't worry, whatever it is, I'm right here with you.",
        "*looks at you for a long quiet second, smiling softly* You look lovely today, $charName.",
        "*leans my shoulder lightly against yours, feeling your heartbeat* Thanks for listening to me. It means a lot.",
        "*boops your nose playfully, laughing at your surprised reaction* Got you! You're cute when you're caught off guard.",
        "*stands very close to you, looking at the stars* Moments like this feel absolutely infinite with you."
    )

    // Select collections based on character identity
    val collectionCon = when {
        charName.contains("Marcus", ignoreCase = true) -> scholarConversations
        charName.contains("Luna", ignoreCase = true) -> lunaConversations
        else -> generalConversations
    }
    
    val collectionAct = when {
        charName.contains("Marcus", ignoreCase = true) -> scholarActions
        charName.contains("Luna", ignoreCase = true) -> lunaActions
        else -> generalActions
    }
    
    val collectionRom = when {
        charName.contains("Marcus", ignoreCase = true) -> scholarRomantic
        charName.contains("Luna", ignoreCase = true) -> lunaRomantic
        else -> generalRomantic
    }

    // Determine selection index based on the seed
    val absSeed = seed.coerceAtLeast(0)
    val sizeCon = collectionCon.size
    val sizeAct = collectionAct.size
    val sizeRom = collectionRom.size

    val opt1 = collectionCon[(absSeed * 3 + 1) % sizeCon]
    val opt2 = collectionAct[(absSeed * 3 + 2) % sizeAct]
    val opt3 = collectionRom[(absSeed * 3 + 3) % sizeRom]

    return listOf(opt1, opt2, opt3)
}
