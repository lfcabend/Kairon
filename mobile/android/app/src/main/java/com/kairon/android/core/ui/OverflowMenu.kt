package com.kairon.android.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * D3 — Account/About moved out of the bottom nav (which only has room for
 * four primary destinations: Today/Todo/Journal/Projects) into this overflow
 * menu, reachable from every top-level screen via [KaironBanner].
 */
@Composable
fun OverflowMenu(onAccount: () -> Unit, onAbout: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("Account") }, onClick = { expanded = false; onAccount() })
        DropdownMenuItem(text = { Text("About") }, onClick = { expanded = false; onAbout() })
    }
}
