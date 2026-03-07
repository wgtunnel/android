package com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import com.zaneschepke.wireguardautotunnel.R
import com.zaneschepke.wireguardautotunnel.ui.screens.tunnels.splittunnel.state.SplitOption

@Composable
fun SplitOptionSelector(selectedOption: SplitOption, onOptionChange: (SplitOption) -> Unit) {
    val context = LocalContext.current
    val locale = Locale.current.platformLocale
    val inputHeight = 45.dp

    MultiChoiceSegmentedButtonRow(
        modifier =
            Modifier.background(color = MaterialTheme.colorScheme.surface)
                .fillMaxWidth()
                .height(inputHeight)
                .padding(horizontal = 16.dp)
    ) {
        SplitOption.entries.forEachIndexed { index, entry ->
            val active = selectedOption == entry
            SegmentedButton(
                shape =
                    SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SplitOption.entries.size,
                        baseShape = RoundedCornerShape(8.dp),
                    ),
                icon = {
                    SegmentedButtonDefaults.Icon(
                        active = active,
                        activeContent = {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = stringResource(R.string.select),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                            )
                        },
                    ) {
                        Icon(
                            imageVector = entry.icon(),
                            contentDescription = entry.icon().name,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    }
                },
                colors =
                    SegmentedButtonDefaults.colors()
                        .copy(
                            activeContainerColor = MaterialTheme.colorScheme.surface,
                            inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                onCheckedChange = { onOptionChange(entry) },
                checked = active,
            ) {
                Text(
                    entry.text().asString(context).replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
