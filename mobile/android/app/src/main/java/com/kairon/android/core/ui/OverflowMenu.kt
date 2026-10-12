package com.kairon.android.core.ui

import androidx.compose.foundation.layout.Box
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
 * menu, reachable from every top-level screen via [KaironBanner]. M11.6 D1
 * adds "Summaries" here too, for the same "secondary, tucked away" reason.
 */
@Composable
fun OverflowMenu(onAccount: () -> Unit, onAbout: () -> Unit, onSummaries: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    // Wrapped in a Box so the DropdownMenu's anchor (a zero-size node only
    // present while expanded) can't become a third sibling of KaironBanner's
    // own SpaceBetween Row — that was shoving this icon off its edge the
    // instant the menu opened.
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Account") }, onClick = { expanded = false; onAccount() })
            DropdownMenuItem(text = { Text("Summaries") }, onClick = { expanded = false; onSummaries() })
            DropdownMenuItem(text = { Text("About") }, onClick = { expanded = false; onAbout() })
        }
    }
}
