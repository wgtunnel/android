package com.zaneschepke.wireguardautotunnel.ui.common.functions

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileImportChooserTest {
    @Test
    fun dropsStubsAndPhotoPicker() {
        val targets =
            selectFileImportTargets(
                listOf(
                    target("com.google.android.tv.frameworkpackagestubs", "Stub", "Files"),
                    target("com.android.providers.media.module", "Photo", "Photos"),
                    target("com.cxinventor.file.explorer", "Open", "CX"),
                ),
            )

        assertEquals(listOf("com.cxinventor.file.explorer"), targets.map { it.packageName })
    }

    @Test
    fun prefersOpenDocumentWhenBothActionsMatch() {
        val activity = "com.example.files.Picker"
        val targets =
            selectFileImportTargets(
                listOf(
                    target(
                        "com.example.files",
                        activity,
                        "Files",
                        action = Intent.ACTION_GET_CONTENT,
                    ),
                    target(
                        "com.example.files",
                        activity,
                        "Files",
                        action = Intent.ACTION_OPEN_DOCUMENT,
                    ),
                ),
            )

        assertEquals(1, targets.size)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, targets.single().action)
    }

    @Test
    fun keepsDistinctActivitiesAndSortsByLabel() {
        val targets =
            selectFileImportTargets(
                listOf(
                    target("com.example.zeta", "Z", "Zeta"),
                    target("com.example.alpha", "A", "Alpha"),
                    target("com.example.alpha", "B", "Alpha documents"),
                ),
            )

        assertEquals(listOf("Alpha", "Alpha documents", "Zeta"), targets.map { it.label })
    }

    @Test
    fun emptyInputStaysEmpty() {
        assertTrue(selectFileImportTargets(emptyList()).isEmpty())
    }

    private fun target(
        packageName: String,
        activityName: String,
        label: String,
        action: String = Intent.ACTION_GET_CONTENT,
    ) =
        FileImportTarget(
            packageName = packageName,
            activityName = activityName,
            label = label,
            action = action,
        )
}
