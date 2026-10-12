package com.kairon.android.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kairon.android.R

/**
 * The app-wide brand header shown above [KaironApp]'s nav, mirroring the web
 * app's `AppLayout` header: the same logo mark as `web/public/favicon.svg`
 * next to the "Kairon" wordmark. M11.5 adds the [OverflowMenu] (D3) here —
 * the one place Account/About stay reachable from every top-level screen
 * now that the bottom nav is full with Today/Todo/Journal/Projects.
 */
@Composable
fun KaironBanner(onAccount: () -> Unit, onAbout: () -> Unit) {
    Surface {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Image(
                    painter = painterResource(R.drawable.ic_kairon_logo),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Text("Kairon", modifier = Modifier.padding(start = 10.dp))
            }
            OverflowMenu(onAccount = onAccount, onAbout = onAbout)
        }
    }
}
