package com.pesatrack.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.presentation.navigation.BottomNavItem
import com.pesatrack.presentation.navigation.NavGraph
import com.pesatrack.presentation.navigation.Screen
import com.pesatrack.presentation.screens.onboarding.OnboardingScreen
import com.pesatrack.presentation.screens.pin.PinLockScreen
import com.pesatrack.presentation.screens.pin.PinMode
import com.pesatrack.presentation.screens.pin.PinViewModel
import com.pesatrack.presentation.theme.PesaTrackTheme
import com.pesatrack.services.AppLockLifecycleObserver
import com.pesatrack.services.NotificationHelper
import com.pesatrack.services.RecurringReminderWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appLockLifecycleObserver: AppLockLifecycleObserver

    @Inject
    lateinit var appPreferences: AppPreferences

    /**
     * Launcher for requesting multiple permissions at once.
     * Handles SMS and notification permissions.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Log results for debugging
        permissions.forEach { (permission, granted) ->
            android.util.Log.d("MainActivity", "$permission: ${if (granted) "GRANTED" else "DENIED"}")
        }
    }

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var executor: Executor

    /** Whether this device supports biometric authentication. */
    private var biometricAvailable = false

    /** Callback to invoke when biometric succeeds — set by the Compose layer. */
    private var onBiometricSuccess: (() -> Unit)? = null

    /** Deep-link navigation target from notification intent extras. */
    private val pendingNavigateTo = mutableStateOf<String?>(null)
    private val pendingExpenseId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Record app open milestone (fire-and-forget)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            appPreferences.recordAppOpen()
        }

        // Create notification channels (safe to call multiple times)
        NotificationHelper.createNotificationChannel(this)
        NotificationHelper.createRecurringReminderChannel(this)

        // Schedule daily recurring-expense reminder worker
        scheduleRecurringReminderWorker()

        // Set up biometric prompt
        setupBiometric()

        // Handle deep-link from notification
        handleDeepLinkIntent(intent)

        setContent {
            PesaTrackTheme {
                AppEntryPoint()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    /**
     * Extract deep-link navigation extras from the notification intent.
     */
    private fun handleDeepLinkIntent(intent: Intent?) {
        val navigateTo = intent?.getStringExtra("navigate_to") ?: return
        val expenseId = intent.getLongExtra("expense_id", -1L)
        pendingNavigateTo.value = navigateTo
        if (expenseId != -1L) {
            pendingExpenseId.value = expenseId
        }
    }

    /**
     * Root entry point that checks onboarding → PIN lock → main app.
     *
     * Priority:
     * 1. If onboarding not completed → show OnboardingScreen
     * 2. If app is locked → show PinLockScreen
     * 3. Otherwise → show MainScreen
     */
    @Composable
    private fun AppEntryPoint() {
        val onboardingCompleted by appPreferences.onboardingCompleted.collectAsState(initial = true)
        val coroutineScope = rememberCoroutineScope()

        // Track whether user tapped "Import Now" during onboarding
        var pendingImportNavigation by remember { mutableStateOf(false) }

        if (!onboardingCompleted) {
            // Record onboarding started milestone (fire-and-forget)
            LaunchedEffect(Unit) {
                appPreferences.recordOnboardingStarted()
            }

            OnboardingScreen(
                onComplete = {
                    coroutineScope.launch {
                        appPreferences.setOnboardingCompleted()
                        // Record import skipped if user finishes without tapping Import Now
                        if (!pendingImportNavigation) {
                            appPreferences.recordOnboardingImportSkipped()
                        }
                    }
                    // Request remaining permissions (notifications) after onboarding
                    requestNotificationPermission()
                },
                onRequestSmsPermission = {
                    // SMS permission is handled inside OnboardingScreen via ActivityResult
                },
                onImportHistory = {
                    // Flag that we should navigate to import screen after onboarding completes
                    pendingImportNavigation = true
                    coroutineScope.launch {
                        appPreferences.recordOnboardingImportChosen()
                    }
                },
                onSmsPermissionGranted = {
                    coroutineScope.launch { appPreferences.recordOnboardingSmsGranted() }
                },
                onSmsPermissionSkipped = {
                    coroutineScope.launch { appPreferences.recordOnboardingSmsSkipped() }
                }
            )
            return
        }

        AppWithLockOverlay(navigateToImport = pendingImportNavigation, onImportNavigated = { pendingImportNavigation = false })
    }

    /**
     * Root composable that shows either the PIN lock screen or the main app.
     */
    @Composable
    private fun AppWithLockOverlay(
        navigateToImport: Boolean = false,
        onImportNavigated: () -> Unit = {}
    ) {
        val isLocked by appLockLifecycleObserver.isLocked.collectAsState()

        if (isLocked) {
            val pinViewModel: PinViewModel = hiltViewModel()

            // Initialize unlock mode
            LaunchedEffect(Unit) {
                pinViewModel.initialize(PinMode.UNLOCK)
                pinViewModel.setBiometricAvailable(biometricAvailable)
            }

            val uiState by pinViewModel.uiState.collectAsState()

            // Auto-launch biometric on first show
            LaunchedEffect(uiState.showBiometricButton) {
                if (uiState.showBiometricButton) {
                    launchBiometric { pinViewModel.onBiometricSuccess() }
                }
            }

            PinLockScreen(
                uiState = uiState,
                onDigitEntered = pinViewModel::onDigitEntered,
                onBackspace = pinViewModel::onBackspace,
                onBiometricRequest = {
                    launchBiometric { pinViewModel.onBiometricSuccess() }
                }
            )
        } else {
            val deepLinkTarget by pendingNavigateTo
            val deepLinkExpenseId by pendingExpenseId
            MainScreen(
                navigateToImport = navigateToImport,
                onImportNavigated = onImportNavigated,
                deepLinkTarget = deepLinkTarget,
                deepLinkExpenseId = deepLinkExpenseId,
                onDeepLinkHandled = {
                    pendingNavigateTo.value = null
                    pendingExpenseId.value = null
                }
            )
        }
    }

    /**
     * Initialize BiometricPrompt and check device capability.
     */
    private fun setupBiometric() {
        executor = ContextCompat.getMainExecutor(this)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onBiometricSuccess?.invoke()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // User cancelled or hardware error — fall back to PIN (already shown)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Wrong fingerprint — system will show its own message, user can retry
            }
        }

        biometricPrompt = BiometricPrompt(this, executor, callback)

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock PesaTrack")
            .setSubtitle("Use your fingerprint or face to unlock")
            .setNegativeButtonText("Use PIN")
            .build()

        // Check if device supports biometric
        val biometricManager = BiometricManager.from(this)
        biometricAvailable = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Launch the biometric prompt.
     */
    private fun launchBiometric(onSuccess: () -> Unit) {
        if (!biometricAvailable) return
        onBiometricSuccess = onSuccess
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Schedule a daily WorkManager task that checks for upcoming/overdue
     * recurring expenses and sends reminder notifications.
     * Uses KEEP policy so it won't reset the schedule on every app launch.
     */
    private fun scheduleRecurringReminderWorker() {
        val workRequest = PeriodicWorkRequestBuilder<RecurringReminderWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RecurringReminderWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Request notification permission (Android 13+).
     * SMS permissions are now handled in the onboarding flow.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    /**
     * Request SMS permissions for returning users (who already completed onboarding
     * but may have revoked permissions).
     * Called after onboarding is already completed.
     */
    private fun requestSmsPermissionsIfNeeded() {
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECEIVE_SMS)
        }
        if (permissionsNeeded.isNotEmpty()) {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navigateToImport: Boolean = false,
    onImportNavigated: () -> Unit = {},
    deepLinkTarget: String? = null,
    deepLinkExpenseId: Long? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Navigate to import screen if user tapped "Import Now" during onboarding
    LaunchedEffect(navigateToImport) {
        if (navigateToImport) {
            navController.navigate(Screen.ImportHistory.route)
            onImportNavigated()
        }
    }

    // Handle deep-link navigation from notification tap
    LaunchedEffect(deepLinkTarget) {
        when (deepLinkTarget) {
            "categorize" -> {
                deepLinkExpenseId?.let { id ->
                    navController.navigate(Screen.Categorize.createRoute(id))
                }
            }
            "budget" -> navController.navigate(Screen.Budget.route)
        }
        if (deepLinkTarget != null) {
            onDeepLinkHandled()
        }
    }

    // Define bottom nav items with icons
    val items = listOf(
        Triple(BottomNavItem.HOME, Icons.Filled.Home, "Home"),
        Triple(BottomNavItem.ANALYTICS, Icons.Filled.BarChart, "Analytics"),
        Triple(BottomNavItem.EXPENSES, Icons.AutoMirrored.Filled.ReceiptLong, "Expenses")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                items.forEach { (item, icon, label) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            // Special-case the start destination (Home):
                            // restoreState = true silently fails when there is
                            // no previously-saved state, so we skip save/restore
                            // for the Home tab and use inclusive = true to clear
                            // the entire back-stack.
                            val isStartDest =
                                item.route == navController.graph.findStartDestination().route
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = !isStartDest
                                    inclusive = isStartDest
                                }
                                launchSingleTop = true
                                restoreState = !isStartDest
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}
