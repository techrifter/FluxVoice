package com.techrifter.fluxvoice.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation routes for Navigation Compose 2.9+ */

@Serializable
object Splash

@Serializable
object Voice

@Serializable
object Settings

@Serializable
data class Studio(
    val initialModelId: String? = null,
    val initialProvider: String? = null,
    val initialCategory: String? = null,
    val pipelineId: String? = null,
    val isNew: Boolean = false
)

@Serializable
object KeyVaultRoute

@Serializable
object BenchmarksRoute

@Serializable
object CatalogRoute

@Serializable
object AnonymousAuthRoute

