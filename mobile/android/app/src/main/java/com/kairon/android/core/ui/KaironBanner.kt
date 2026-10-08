package com.kairon.android.core.ui

import androidx.compose.foundation.Image
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
 * next to the "Kairon" wordmark.
 */
@Composable
fun KaironBanner() {
    Surface {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_kairon_logo),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text("Kairon", modifier = Modifier.padding(start = 10.dp))
        }
    }
}
