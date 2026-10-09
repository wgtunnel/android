package com.zaneschepke.wireguardautotunnel.ui.common.scroll

import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.appScrollbar(state: ScrollIndicatorState?, orientation: Orientation): Modifier =
    if (state == null) this else this.nonInteractiveScrollbar(state, orientation)
