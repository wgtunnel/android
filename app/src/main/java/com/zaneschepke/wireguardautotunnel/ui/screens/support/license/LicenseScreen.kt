package com.zaneschepke.wireguardautotunnel.ui.screens.support.license

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.variant.LibraryDetailMode

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LicenseScreen() {
    val context = LocalContext.current
    val libs = remember { buildLibsWithAdditionalLibraries(context) }

    LibrariesContainer(
        libraries = libs,
        modifier = Modifier.fillMaxSize(),
        detailMode = LibraryDetailMode.Sheet,
    )
}
