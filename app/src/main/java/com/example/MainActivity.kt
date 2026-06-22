package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.CharacterRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.RoleplayScreen
import com.example.ui.RoleplayViewModel
import com.example.ui.RoleplayViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize local Room persistence
        val database = AppDatabase.getDatabase(this)
        val repository = CharacterRepository(
            characterDao = database.characterDao(),
            messageDao = database.messageDao(),
            memoryDao = database.memoryDao()
        )

        setContent {
            MyApplicationTheme {
                // Instantiate the viewModel with constructor injection using ViewModelFactory
                val viewModel: RoleplayViewModel by viewModels {
                    RoleplayViewModelFactory(repository)
                }

                RoleplayScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
