package com.rabbeat.app.desktop.sys

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import java.io.File

suspend fun pickDirectory(title: String): File? =
    FileKit.openDirectoryPicker(
        dialogSettings = FileKitDialogSettings(title = title),
    )?.file

suspend fun pickFile(desc: String, vararg ext: String): File? =
    FileKit.openFilePicker(
        type = if (ext.isEmpty()) FileKitType.File() else FileKitType.File(ext.toSet()),
        dialogSettings = FileKitDialogSettings(title = desc),
    )?.file

suspend fun pickSave(defaultName: String): File? =
    FileKit.openFileSaver(
        suggestedName = defaultName.substringBeforeLast('.', defaultName),
        defaultExtension = defaultName.substringAfterLast('.', "").ifBlank { null },
    )?.file
