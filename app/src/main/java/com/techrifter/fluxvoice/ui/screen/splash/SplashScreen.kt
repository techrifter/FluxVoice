package com.techrifter.fluxvoice.ui.screen.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.techrifter.fluxvoice.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SplashBg      = Color(0xFF02040D)

@Composable
fun SplashScreen(onComplete: () -> Unit) {
    val animAlpha = remember { Animatable(0f) }
    val animScale = remember { Animatable(0.88f) }

    LaunchedEffect(Unit) {
        launch { animAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        animScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(1100)
        animAlpha.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(animScale.value)
                .alpha(animAlpha.value)
        ) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(200.dp)
            )
        }
    }
}
