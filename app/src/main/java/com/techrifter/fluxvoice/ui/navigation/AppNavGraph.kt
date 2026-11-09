package com.techrifter.fluxvoice.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.techrifter.fluxvoice.ui.screen.auth.AnonymousAuthScreen
import com.techrifter.fluxvoice.ui.screen.benchmarks.BenchmarkHistoryScreen
import com.techrifter.fluxvoice.ui.screen.catalog.ModelCatalogScreen
import com.techrifter.fluxvoice.ui.screen.settings.SettingsScreen
import com.techrifter.fluxvoice.ui.screen.splash.SplashScreen
import com.techrifter.fluxvoice.ui.screen.studio.PipelineStudioScreen
import com.techrifter.fluxvoice.ui.screen.vault.KeyVaultScreen
import com.techrifter.fluxvoice.ui.screen.voice.VoiceScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    splashEnabled: Boolean,
    needsAuth: Boolean = false
) {
    NavHost(
        navController = navController,
        startDestination = when {
            splashEnabled -> Splash
            needsAuth -> AnonymousAuthRoute
            else -> Voice
        },
        enterTransition = {
            fadeIn(tween(280)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(280)
            )
        },
        exitTransition = {
            fadeOut(tween(220)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(220)
            )
        },
        popEnterTransition = {
            fadeIn(tween(280)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(280)
            )
        },
        popExitTransition = {
            fadeOut(tween(220)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(220)
            )
        }
    ) {
        composable<Splash> {
            SplashScreen(
                onComplete = {
                    val destination = if (needsAuth) AnonymousAuthRoute else Voice
                    navController.navigate(destination) {
                        popUpTo(Splash) { inclusive = true }
                    }
                }
            )
        }

        composable<AnonymousAuthRoute> {
            AnonymousAuthScreen(
                onComplete = {
                    navController.navigate(Voice) {
                        popUpTo(AnonymousAuthRoute) { inclusive = true }
                    }
                }
            )
        }

        composable<Voice> {
            VoiceScreen(
                onSettingsClick = { navController.navigate(Settings) },
                onStudioClick = { pipelineId, isNew ->
                    navController.navigate(Studio(pipelineId = pipelineId, isNew = isNew))
                },
                onKeyVaultClick = { navController.navigate(KeyVaultRoute) },
                onBenchmarksClick = { navController.navigate(BenchmarksRoute) },
                onCatalogClick = { navController.navigate(CatalogRoute) }
            )
        }

        composable<Settings> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onKeyVaultClick = { navController.navigate(KeyVaultRoute) },
                onBenchmarksClick = { navController.navigate(BenchmarksRoute) },
                onCatalogClick = { navController.navigate(CatalogRoute) }
            )
        }

        composable<Studio> { backStackEntry ->
            val studioRoute = backStackEntry.toRoute<Studio>()
            PipelineStudioScreen(
                onBack = { navController.popBackStack() },
                initialModelId = studioRoute.initialModelId,
                initialProvider = studioRoute.initialProvider,
                initialCategory = studioRoute.initialCategory,
                pipelineId = studioRoute.pipelineId,
                isNew = studioRoute.isNew
            )
        }

        composable<KeyVaultRoute> {
            KeyVaultScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<BenchmarksRoute> {
            BenchmarkHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<CatalogRoute> {
            ModelCatalogScreen(
                onBack = { navController.popBackStack() },
                onOpenStudio = { modelId, provider, category ->
                    navController.navigate(
                        Studio(
                            initialModelId = modelId,
                            initialProvider = provider,
                            initialCategory = category
                        )
                    )
                }
            )
        }
    }
}
