package com.jupiter.filemanager.feature.browser.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.SortDirection
import com.jupiter.filemanager.domain.model.SortField
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.ui.theme.JupiterDesign

/**
 * Modal bottom sheet that lets the user adjust both the [SortOption] and the
 * non-query parts of a [FilterOption] (hidden-file visibility and a type
 * filter) for the current directory listing.
 *
 * The sheet keeps a local working copy of the sort/filter state so the user can
 * tweak multiple controls before committing. Pressing "Apply" invokes [onApply]
 * with the working copies and then [onDismiss]; pressing "Reset" restores the
 * defaults in-place without applying. Dismissing the sheet (scrim tap / back)
 * discards any uncommitted edits.
 *
 * Pure UI: performs no IO and owns no business logic beyond local state.
 *
 * @param current the sort option currently in effect.
 * @param filter the filter option currently in effect (its [FilterOption.query] is preserved verbatim).
 * @param onApply invoked with the chosen sort and filter when the user confirms.
 * @param onDismiss invoked when the sheet should be hidden without applying.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SortFilterSheet(
    current: SortOption,
    filter: FilterOption,
    onApply: (SortOption, FilterOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local working copies; edits stay here until the user applies.
    var workingSort by remember(current) { mutableStateOf(current) }
    var workingFilter by remember(filter) { mutableStateOf(filter) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = JupiterDesign.HeroCardShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = JupiterDesign.ScreenPadding)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Sort & filter",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- Sort field ---
            SectionLabel(text = "Sort by")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortField.entries.forEach { field ->
                    FilterChip(
                        selected = workingSort.field == field,
                        onClick = { workingSort = workingSort.copy(field = field) },
                        label = { Text(text = sortFieldLabel(field)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Sort direction ---
            SectionLabel(text = "Order")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortDirection.entries.forEach { direction ->
                    FilterChip(
                        selected = workingSort.direction == direction,
                        onClick = { workingSort = workingSort.copy(direction = direction) },
                        label = { Text(text = sortDirectionLabel(direction)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            ToggleRow(
                label = "Folders first",
                checked = workingSort.foldersFirst,
                onCheckedChange = { workingSort = workingSort.copy(foldersFirst = it) },
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // --- Filters ---
            ToggleRow(
                label = "Show hidden files",
                checked = workingFilter.showHidden,
                onCheckedChange = { workingFilter = workingFilter.copy(showHidden = it) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel(text = "File type")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // "All" clears the type filter.
                FilterChip(
                    selected = workingFilter.typeFilter == null,
                    onClick = { workingFilter = workingFilter.copy(typeFilter = null) },
                    label = { Text(text = "All") },
                )
                FileType.entries.forEach { type ->
                    FilterChip(
                        selected = workingFilter.typeFilter == type,
                        onClick = { workingFilter = workingFilter.copy(typeFilter = type) },
                        label = { Text(text = fileTypeLabel(type)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Actions ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        // Reset working copies to defaults, preserving the active query.
                        workingSort = SortOption()
                        workingFilter = FilterOption(query = workingFilter.query)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Reset")
                }
                Button(
                    onClick = {
                        onApply(workingSort, workingFilter)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Apply")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun sortFieldLabel(field: SortField): String = when (field) {
    SortField.NAME -> "Name"
    SortField.SIZE -> "Size"
    SortField.DATE_MODIFIED -> "Date"
    SortField.TYPE -> "Type"
}

private fun sortDirectionLabel(direction: SortDirection): String = when (direction) {
    SortDirection.ASCENDING -> "Ascending"
    SortDirection.DESCENDING -> "Descending"
}

private fun fileTypeLabel(type: FileType): String = when (type) {
    FileType.FOLDER -> "Folders"
    FileType.IMAGE -> "Images"
    FileType.VIDEO -> "Videos"
    FileType.AUDIO -> "Audio"
    FileType.DOCUMENT -> "Documents"
    FileType.PDF -> "PDF"
    FileType.ARCHIVE -> "Archives"
    FileType.APK -> "Apps"
    FileType.CODE -> "Code"
    FileType.OTHER -> "Other"
}
