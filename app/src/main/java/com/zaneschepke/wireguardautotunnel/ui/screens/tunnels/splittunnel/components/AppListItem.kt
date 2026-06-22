package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components

import android.content.pm.PackageManager
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.domain.model.InstalledPackage
import com.zaneschepke.wireguardautotunnel.ui.common.button.SurfaceRow
import com.zaneschepke.wireguardautotunnel.ui.common.text.DescriptionText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListItem(
    installedPackage: InstalledPackage,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val icon by remember {
        derivedStateOf {
            try {
                context.packageManager.getApplicationIcon(installedPackage.packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                AppCompatResources.getDrawable(context, R.drawable.resource_package)!!
            }
        }
    }

    SurfaceRow(
        leading = {
            Image(
                painter = rememberDrawablePainter(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        title = installedPackage.name,
        description = { DescriptionText(installedPackage.packageName) },
        onClick = { onToggle(!isSelected) },
        trailing = {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggle(it) })
                }
            }
        },
    )
}
