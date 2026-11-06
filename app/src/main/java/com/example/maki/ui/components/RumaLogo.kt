package com.example.maki.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.maki.R

/** The same vector artwork as pwa/public/ruma-logo.svg. */
@Composable
fun RumaLogo(modifier: Modifier = Modifier, size: Dp = 112.dp) {
    Box(modifier.size(size).makiCard(size * 0.25f).padding(size * 0.12f), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ruma_logo), contentDescription = "Ruma")
    }
}
