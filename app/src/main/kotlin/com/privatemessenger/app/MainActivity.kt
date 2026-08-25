package com.privatemessenger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MessengerApp() }
    }
}

@Composable
private fun MessengerApp() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (!loggedIn) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Private Messenger", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") })
                    OutlinedTextField(password, { password = it }, label = { Text("Пароль") })
                    Button(onClick = { loggedIn = true }, enabled = email.isNotBlank() && password.isNotBlank()) { Text("Войти") }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text("Чат", style = MaterialTheme.typography.headlineSmall)
                    LazyColumn(Modifier.weight(1f)) { items(messages) { Text(it, Modifier.padding(8.dp)) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(message, { message = it }, Modifier.weight(1f), placeholder = { Text("Сообщение") })
                        Button(onClick = { if (message.isNotBlank()) { messages.add(message); message = "" } }) { Text("Отправить") }
                    }
                }
            }
        }
    }
}
