package com.techrifter.fluxvoice

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.compose.rememberNavController
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import com.techrifter.fluxvoice.ui.navigation.AppNavGraph
import com.techrifter.fluxvoice.ui.theme.FluxVoiceTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Hilt injects fields during this call

        // Read startup preferences synchronously — DataStore file read is <10ms at startup.
        val settings = runBlocking { settingsRepository.getSettings().first() }
        val splashEnabled = settings.splashEnabled
        val needsAuth = settings.anonymousUsername.isBlank()

        enableEdgeToEdge()
        setContent {
            FluxVoiceTheme {
                val navController = rememberNavController()
                AppNavGraph(
                    navController  = navController,
                    splashEnabled  = splashEnabled,
                    needsAuth      = needsAuth
                )
            }
        }
    }
}
