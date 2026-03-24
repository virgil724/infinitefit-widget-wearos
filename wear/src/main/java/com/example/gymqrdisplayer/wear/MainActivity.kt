package com.example.gymqrdisplayer.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    MaterialTheme {
        val navController = rememberSwipeDismissableNavController()
        val viewModel: WearViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsState()

        // 若無憑證則自動導向設定頁，若有憑證則回到 QR 頁
        LaunchedEffect(uiState) {
            when (uiState) {
                is WearViewModel.UiState.NoCredentials -> {
                    if (navController.currentDestination?.route != "credentials") {
                        navController.navigate("credentials")
                    }
                }
                is WearViewModel.UiState.Success -> {
                    if (navController.currentDestination?.route == "credentials") {
                        navController.popBackStack()
                    }
                }
                else -> {}
            }
        }

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "qr"
        ) {
            composable("qr") {
                QrScreen(viewModel = viewModel)
            }
            composable("credentials") {
                CredentialScreen(
                    viewModel = viewModel,
                    onCredentialsSaved = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
