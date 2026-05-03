package com.techrifter.fluxvoice.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.techrifter.fluxvoice.ui.screen.settings.SettingsScreen
import com.techrifter.fluxvoice.ui.screen.splash.SplashScreen
import com.techrifter.fluxvoice.ui.screen.voice.VoiceScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    splashEnabled: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = if (splashEnabled) Splash else Voice
    ) {
        composable<Splash> {
            SplashScreen(
                onComplete = {
                    navController.navigate(Voice) {
                        popUpTo(Splash) { inclusive = true }
                    }
                }
            )
        }

        composable<Voice> {
            VoiceScreen(
                onSettingsClick = { navController.navigate(Settings) }
            )
        }

        composable<Settings> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
