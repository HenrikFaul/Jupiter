package com.jupiter.filemanager.feature.browser.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Dialog used to rename an existing file or folder.
 *
 * Pre-fills the text field with [initialName] (selecting the whole value so the
 * user can immediately overwrite it) and confirms only when the trimmed name is
 * non-blank and differs from the original. Pure UI: all side effects are
 * delegated to [onConfirm] / [onDismiss].
 *
 * @param initialName the current name of the item being renamed.
 * @param onConfirm invoked with the trimmed, validated new name.
 * @param onDismiss invoked when the dialog is dismissed without confirming.
 */
@Composable
fun RenameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    NameInputDialog(
        title = "Rename",
        label = "Name",
        initialValue = initialName,
        confirmLabel = "Rename",
        validate = { candidate -> candidate.isNotBlank() && candidate != initialName },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * Dialog used to create a new folder inside the current directory.
 *
 * Starts with an empty field and enables confirmation only once a non-blank name
 * has been entered. Pure UI: the actual folder creation is delegated to
 * [onConfirm].
 *
 * @param onConfirm invoked with the trimmed, validated folder name.
 * @param onDismiss invoked when the dialog is dismissed without confirming.
 */
@Composable
fun CreateFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    NameInputDialog(
        title = "New folder",
        label = "Folder name",
        initialValue = "",
        confirmLabel = "Create",
        validate = { candidate -> candidate.isNotBlank() },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * Shared scaffold for the single-text-field "enter a name" dialogs used for
 * renaming and folder creation.
 *
 * Holds the editable text in local state, requests focus when shown, and routes
 * both the IME "Done" action and the confirm button through the same validated
 * [submit] path. The confirm button is disabled until [validate] accepts the
 * trimmed input.
 *
 * @param validate predicate over the trimmed candidate name; controls whether
 *        confirmation is allowed.
 * @param onConfirm invoked with the trimmed name when the user confirms.
 */
@Composable
private fun NameInputDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmLabel: String,
    validate: (String) -> Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val trimmed = text.trim()
    val isValid = validate(trimmed)

    val submit: () -> Unit = {
        if (validate(text.trim())) {
            keyboardController?.hide()
            onConfirm(text.trim())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = JupiterDesign.CardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(text = label) },
                    singleLine = true,
                    isError = text.isNotEmpty() && !isValid,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = JupiterDesign.CompactCardShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = isValid,
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )

    // Focus the field as soon as the dialog enters composition.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        scope.launch {
            focusRequester.requestFocus()
        }
    }
}
