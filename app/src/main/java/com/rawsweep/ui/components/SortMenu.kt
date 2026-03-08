package com.rawsweep.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rawsweep.data.SortOption

@Composable
fun SortMenu(
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = "Sort",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        SortOption.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = {
                    onSortSelected(option)
                    expanded = false
                },
                leadingIcon = {
                    RadioButton(
                        selected = currentSort == option,
                        onClick = null,
                    )
                },
            )
        }
    }
}
