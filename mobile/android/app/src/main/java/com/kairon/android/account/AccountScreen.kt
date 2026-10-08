package com.kairon.android.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onLoggedOut: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Account") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            state.error?.let { Text(it) }
            state.me?.let { me ->
                Text("Email: ${me.email ?: ""}")
                Text("Name: ${me.displayName ?: ""}")
                Text("Timezone: ${me.timezone ?: ""}")
                Text("Status: ${me.status ?: ""}")
            }
            Button(onClick = viewModel::logout, modifier = Modifier.padding(top = 24.dp)) {
                Text("Log out")
            }
            Button(onClick = viewModel::logoutAll, modifier = Modifier.padding(top = 8.dp)) {
                Text("Log out everywhere")
            }
        }
    }
}
