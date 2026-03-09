package com.example.gymqrdisplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.gymqrdisplayer.ui.theme.GYMQRDisplayerTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataStoreManager = DataStoreManager(this)

        enableEdgeToEdge()
        setContent {
            GYMQRDisplayerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LoginScreen(
                        dataStoreManager = dataStoreManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(dataStoreManager: DataStoreManager, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var uid by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        uid = dataStoreManager.uidFlow.first() ?: ""
        pwd = dataStoreManager.getPassword() ?: ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "GYM QR Displayer",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Enter your credentials below",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = uid,
            onValueChange = { uid = it },
            label = { Text("Account (UID)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = loginError != null && uid.isBlank()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = pwd,
            onValueChange = { pwd = it },
            label = { Text("Password (PWD)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = loginError != null && pwd.isBlank()
        )

        // Error message
        if (loginError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = loginError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                // Input validation
                if (uid.isBlank() || pwd.isBlank()) {
                    loginError = "Please enter both account and password"
                    return@Button
                }

                isLoggingIn = true
                loginError = null

                scope.launch {
                    try {
                        // Validate credentials by attempting login
                        val repository = GymRepository.instance
                        val hashCode = repository.getHashCode()

                        if (hashCode != null) {
                            val uuid = repository.login(uid, pwd, hashCode)
                            if (uuid != null) {
                                // Login successful, save credentials
                                dataStoreManager.saveCredentials(uid, pwd)
                                loginError = null
                            } else {
                                loginError = "Invalid account or password"
                            }
                        } else {
                            loginError = "Connection error, please try again"
                        }
                    } catch (e: Exception) {
                        loginError = "Error: ${e.message}"
                    } finally {
                        isLoggingIn = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            enabled = !isLoggingIn
        ) {
            Text(if (isLoggingIn) "Logging in..." else "Login")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 新增：顯示 QR Code 的按鈕 (觸發 Bottom Sheet)
        Button(
            onClick = {
                val intent = Intent(context, QrBottomSheetActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Show My QR Code")
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Instructions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "1. Enter your credentials and click Login.\n" +
                   "2. Click 'Show My QR Code' to test.\n" +
                   "3. For faster access, add the 'GYM QR' widget to your home screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
        )
    }
}
