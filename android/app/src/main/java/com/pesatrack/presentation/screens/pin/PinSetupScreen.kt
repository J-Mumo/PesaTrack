package com.pesatrack.presentation.screens.pin

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * PIN setup screen — navigated to from Settings for initial setup or PIN change.
 *
 * Modes:
 * - SETUP: Enter → Confirm → Save (when enabling PIN for the first time)
 * - CHANGE: Verify Current → Enter New → Confirm New → Save (when changing PIN)
 * - DISABLE: Verify Current → Clear PIN (when disabling PIN lock)
 *
 * @param isChangeMode True for change PIN flow (verify current first), false for new setup
 * @param isDisableMode True for disabling PIN (verify current then clear)
 * @param onNavigateBack Navigate back to Settings
 * @param onSetupComplete Called when PIN is successfully set/changed/disabled
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    isChangeMode: Boolean = false,
    isDisableMode: Boolean = false,
    onNavigateBack: () -> Unit,
    onSetupComplete: () -> Unit,
    viewModel: PinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initialize the mode
    LaunchedEffect(isChangeMode, isDisableMode) {
        val mode = when {
            isDisableMode -> PinMode.VERIFY_DISABLE
            isChangeMode -> PinMode.VERIFY_CURRENT
            else -> PinMode.SETUP_ENTER
        }
        viewModel.initialize(mode)
    }

    // Handle success
    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onSetupComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isDisableMode -> "Disable PIN"
                            isChangeMode -> "Change PIN"
                            else -> "Set Up PIN"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            PinLockScreen(
                uiState = uiState,
                onDigitEntered = viewModel::onDigitEntered,
                onBackspace = viewModel::onBackspace,
                onBiometricRequest = { } // No biometric during setup/change
            )
        }
    }
}
