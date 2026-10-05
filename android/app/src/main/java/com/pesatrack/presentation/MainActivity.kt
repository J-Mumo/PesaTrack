package com.pesatrack.presentation

import android.Manifest
import android.app.NotificationManager
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pesatrack.data.local.preferences.AppPreferences
import com.pesatrack.presentation.navigation.BottomNavItem
import com.pesatrack.presentation.navigation.NavGraph
import com.pesatrack.presentation.navigation.NotificationNavigation
import com.pesatrack.presentation.navigation.Screen
import com.pesatrack.presentation.screens.onboarding.OnboardingScreen
import com.pesatrack.presentation.screens.pin.PinLockScreen
import com.pesatrack.presentation.screens.pin.PinMode
import com.pesatrack.presentation.screens.pin.PinViewModel
import com.pesatrack.presentation.components.TelemetryConsentSheet
import com.pesatrack.presentation.theme.PesaTrackTheme
import com.pesatrack.services.AppLockLifecycleObserver
import com.pesatrack.services.NotificationHelper
import com.pesatrack.services.ExpenseNotificationContract
import com.pesatrack.services.telemetry.ActivationTelemetry
import com.pesatrack.services.telemetry.TelemetryClient
import com.pesatrack.services.telemetry.TelemetryEvents

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appLockLifecycleObserver: AppLockLifecycleObserver

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var telemetryClient: TelemetryClient

    @Inject
    lateinit var activationTelemetry: ActivationTelemetry

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
            // Telemetry — emit granted/denied with source=app for post-onboarding
            // permission asks (returning-user SMS re-grant, notifications on
            // Android 13+). The onboarding SMS flow reports separately with
            // source=onboarding via OnboardingScreen callbacks.
            val kind = permissionKind(permission) ?: return@forEach
            val event = if (granted) TelemetryEvents.PERMISSION_GRANTED
                        else TelemetryEvents.PERMISSION_DENIED
            telemetryClient.logEvent(
                event,
                mapOf(
                    TelemetryEvents.PARAM_KIND to kind,
                    TelemetryEvents.PARAM_SOURCE to TelemetryEvents.SOURCE_APP
                )
            )
        }
    }

    /**
     * Map an Android [Manifest.permission] string to the small telemetry enum
     * we allow ourselves to send. Returns `null` for permissions we don't
     * report, so unknown permissions never leak as novel enum values.
     */
    private fun permissionKind(permission: String): String? = when (permission) {
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS -> TelemetryEvents.PERMISSION_SMS
        Manifest.permission.POST_NOTIFICATIONS -> TelemetryEvents.PERMISSION_NOTIFICATION
        else -> null
    }

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private lateinit var executor: Executor

    /** Whether this device supports biometric authentication. */
    private var biometricAvailable = false

    /** Callback to invoke when biometric succeeds — set by the Compose layer. */
    private var onBiometricSuccess: (() -> Unit)? = null

    /** Deep-link navigation target from notification intent extras. */
    private val pendingNavigation = mutableStateOf<NotificationNavigation?>(null)
    private var navigationToken = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Record app open milestone (fire-and-forget)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            appPreferences.recordAppOpen()
        }

        // Phase 1 telemetry — emit app_opened once per cold start. The
        // TelemetryClient itself short-circuits when the user has not opted
        // in, so this is a no-op until consent is granted.
        telemetryClient.logEvent(TelemetryEvents.APP_OPENED)
        // Phase 4 — daily permission_state + user properties + pending flushes.
        // No-op until the user has opted in.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { activationTelemetry.onAppOpened() }
        }

        // Create notification channels (safe to call multiple times)
        NotificationHelper.createNotificationChannel(this)
        

        // Set up biometric prompt
        setupBiometric()

        // Handle deep-link from notification
        if (savedInstanceState == null) {
            handleDeepLinkIntent(intent)
        } else {
            navigationToken = savedInstanceState.getLong("notification_navigation_token")
            // Restore only unconsumed delivery, never replay the original activity intent.
            val extras = savedInstanceState.getBundle("pending_notification_navigation")
            pendingNavigation.value = extras?.let { readNavigation(Intent().putExtras(it), navigationToken) }
        }

        setContent {
            PesaTrackTheme {
                AppEntryPoint()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("notification_navigation_token", navigationToken)
        pendingNavigation.value?.let { request ->
            outState.putBundle("pending_notification_navigation", Bundle().apply {
                putString("navigate_to", request.target)
                request.expenseId?.let { putLong("expense_id", it) }
                request.incomeId?.let { putLong("income_id", it) }
                request.snapshotId?.let { putLong("report_snapshot_id", it) }
                request.year?.let { putInt("year", it) }
            })
        }
        super.onSaveInstanceState(outState)
    }

    private fun readNavigation(intent: Intent?, token: Long): NotificationNavigation? {
        val target = intent?.getStringExtra("navigate_to") ?: return null
        return NotificationNavigation(
            token, target,
            intent.getLongExtra("expense_id", -1L).takeIf { it > 0 },
            intent.getLongExtra("income_id", -1L).takeIf { it > 0 },
            intent.getLongExtra("report_snapshot_id", -1L).takeIf { it > 0 },
            intent.getIntExtra("year", -1).takeIf { it > 0 }
        ).takeIf { it.route() != null }
    }

    /**
     * Extract deep-link navigation extras from the notification intent.
     */
    private fun handleDeepLinkIntent(intent: Intent?) {
        val request = readNavigation(intent, ++navigationToken) ?: return
        pendingNavigation.value = request
        val navigateTo = request.target
        // Action buttons do not auto-cancel. Only cancel the validated originating expense
        // notification, never an untagged ID belonging to a budget/review/income notification.
        val kind = intent?.getStringExtra(ExpenseNotificationContract.EXTRA_KIND)
        if (navigateTo == "categorize" && ExpenseNotificationContract.isOpen(
                intent?.action, intent?.dataString, request.expenseId ?: -1L, kind)) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(ExpenseNotificationContract.tag(request.expenseId!!, kind!!),
                ExpenseNotificationContract.NOTIFICATION_ID)
        }

        // Telemetry — bucket the deep-link target into one of the four
        // notification kinds. Anything unrecognised is dropped (silence is
        // safer than sending a novel enum value).
        val telemetryKind = when (navigateTo) {
            "categorize", "categorize_income" -> TelemetryEvents.NOTIFICATION_CATEGORIZE
            "weekly_review", "monthly_review", "quarterly_review", "year_in_review" ->
                TelemetryEvents.NOTIFICATION_REVIEW
            "budget" -> TelemetryEvents.NOTIFICATION_BUDGET
            else -> null
        }
        if (telemetryKind != null) {
            telemetryClient.logEvent(
                TelemetryEvents.NOTIFICATION_OPENED,
                mapOf(TelemetryEvents.PARAM_KIND to telemetryKind)
            )
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
        val onboardingCompleted by appPreferences.onboardingCompleted.collectAsState(initial = null)
        val coroutineScope = rememberCoroutineScope()

        // Route to open after onboarding: SMS import ("Import Now") or the
        // statement-import alternative for users without SMS access.
        var pendingImportRoute by remember { mutableStateOf<String?>(null) }
        val pendingImportNavigation = pendingImportRoute != null

        // Do not consume a notification while persisted onboarding state is still loading.
        if (onboardingCompleted == null) return
        if (onboardingCompleted == false) {
            // Record onboarding started milestone (fire-and-forget)
            LaunchedEffect(Unit) {
                // (F) If onboarding was started on an earlier launch and never
                // finished, store the furthest step so it can be reported after
                // consent. Must run before recordOnboardingStarted().
                appPreferences.markOnboardingAbandonedIfRestarted()
                appPreferences.recordOnboardingStarted()
                telemetryClient.logEvent(TelemetryEvents.ONBOARDING_STARTED)
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
                    // Telemetry — completion path is derived from whether the
                    // user tapped "Import Now" during the flow. This is the
                    // last signal before the flow tears down, so it fires here.
                    val completionKind = if (pendingImportNavigation) {
                        TelemetryEvents.ONBOARDING_COMPLETION_IMPORT
                    } else {
                        TelemetryEvents.ONBOARDING_COMPLETION_SKIPPED
                    }
                    telemetryClient.logEvent(
                        TelemetryEvents.ONBOARDING_COMPLETED,
                        mapOf(TelemetryEvents.PARAM_KIND to completionKind)
                    )
                    // Request remaining permissions (notifications) after onboarding
                    requestNotificationPermission()
                },
                onRequestSmsPermission = {
                    // SMS permission is handled inside OnboardingScreen via ActivityResult
                },
                onImportHistory = {
                    // Flag that we should navigate to import screen after onboarding completes
                    pendingImportRoute = Screen.ImportHistory.route
                    coroutineScope.launch {
                        appPreferences.recordOnboardingImportChosen()
                    }
                },
                onImportStatement = {
                    // Path B — no SMS access; import an M-PESA PDF statement instead.
                    pendingImportRoute = Screen.StatementImport.route
                    coroutineScope.launch {
                        appPreferences.recordOnboardingImportChosen()
                    }
                    telemetryClient.logEvent(
                        TelemetryEvents.STATEMENT_ALTERNATIVE_CHOSEN,
                        mapOf(TelemetryEvents.PARAM_SOURCE to TelemetryEvents.SOURCE_ONBOARDING)
                    )
                },
                onPageReached = { page ->
                    coroutineScope.launch { appPreferences.recordOnboardingStep(page) }
                },
                onSmsPermissionRequested = {
                    telemetryClient.logEvent(
                        TelemetryEvents.PERMISSION_REQUESTED,
                        mapOf(
                            TelemetryEvents.PARAM_KIND to TelemetryEvents.PERMISSION_SMS,
                            TelemetryEvents.PARAM_SOURCE to TelemetryEvents.SOURCE_ONBOARDING
                        )
                    )
                },
                onSmsPermissionGranted = {
                    coroutineScope.launch { appPreferences.recordOnboardingSmsGranted() }
                    telemetryClient.logEvent(
                        TelemetryEvents.PERMISSION_GRANTED,
                        mapOf(
                            TelemetryEvents.PARAM_KIND to TelemetryEvents.PERMISSION_SMS,
                            TelemetryEvents.PARAM_SOURCE to TelemetryEvents.SOURCE_ONBOARDING
                        )
                    )
                },
                onSmsPermissionDenied = {
                    coroutineScope.launch { appPreferences.recordOnboardingSmsDenied() }
                    telemetryClient.logEvent(
                        TelemetryEvents.PERMISSION_DENIED,
                        mapOf(
                            TelemetryEvents.PARAM_KIND to TelemetryEvents.PERMISSION_SMS,
                            TelemetryEvents.PARAM_SOURCE to TelemetryEvents.SOURCE_ONBOARDING
                        )
                    )
                },
                onSmsPermissionSkipped = {
                    coroutineScope.launch { appPreferences.recordOnboardingSmsSkipped() }
                }
            )
            return
        }

        AppWithLockOverlay(
            importRoute = pendingImportRoute,
            onImportNavigated = { pendingImportRoute = null }
        )
    }

    /**
     * Root composable that shows either the PIN lock screen or the main app.
     */
    @Composable
    private fun AppWithLockOverlay(
        importRoute: String? = null,
        onImportNavigated: () -> Unit = {}
    ) {
        val isLocked by appLockLifecycleObserver.isLocked.collectAsState()
        val lockReady by appLockLifecycleObserver.isLockStateReady.collectAsState()
        if (!lockReady) return

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
            val request by pendingNavigation
            MainScreen(
                importRoute = importRoute,
                onImportNavigated = onImportNavigated,
                notificationNavigation = request,
                onDeepLinkHandled = { handled ->
                    // An older effect cannot acknowledge a newer notification delivery.
                    pendingNavigation.value = pendingNavigation.value?.acknowledge(handled)
                },
                onScreenViewed = { screen ->
                    telemetryClient.logEvent(
                        TelemetryEvents.SCREEN_VIEWED,
                        mapOf(TelemetryEvents.PARAM_SCREEN to screen)
                    )
                }
            )

            // Phase 1 telemetry consent — shown once, only after onboarding
            // is complete and the app is unlocked. Both actions dismiss
            // permanently; the Settings toggle is the ongoing control.
            TelemetryConsentGate()
        }
    }

    /**
     * Shows the one-time telemetry consent sheet on top of the main app.
     *
     * Gating rules:
     *  - Only after onboarding (handled by caller — we already passed the
     *    onboarding branch).
     *  - Only after the PIN unlock overlay is dismissed (again, caller-gated).
     *  - Only if the prompt has never been shown before.
     *
     * Emits [TelemetryEvents.TELEMETRY_PROMPT_SHOWN] once the sheet is
     * visible, and either `TELEMETRY_ENABLED` or `TELEMETRY_PROMPT_DISMISSED`
     * depending on which button the user taps. The "enabled" event goes out
     * *after* [TelemetryClient.setEnabled] flips the switch on, so it is the
     * first event of the newly-consented session.
     */
    @Composable
    private fun TelemetryConsentGate() {
        val promptShown by appPreferences.telemetryPromptShown
            .collectAsState(initial = true)
        val scope = rememberCoroutineScope()
        var visible by remember { mutableStateOf(false) }
        var eventLogged by remember { mutableStateOf(false) }

        LaunchedEffect(promptShown) {
            if (!promptShown) {
                visible = true
                if (!eventLogged) {
                    telemetryClient.logEvent(TelemetryEvents.TELEMETRY_PROMPT_SHOWN)
                    eventLogged = true
                }
            }
        }

        if (!visible) return

        TelemetryConsentSheet(
            onAccept = {
                scope.launch {
                    appPreferences.setTelemetryEnabled(true)
                    appPreferences.markTelemetryPromptShown()
                    telemetryClient.setEnabled(true)
                    telemetryClient.logEvent(TelemetryEvents.TELEMETRY_ENABLED)
                    // Phase 4 — replay onboarding choices (A), permission state
                    // (B/C) and any pending first-scan / abandoned-onboarding (D/F).
                    runCatching { activationTelemetry.onTelemetryEnabled() }
                    visible = false
                }
            },
            onDismiss = {
                scope.launch {
                    appPreferences.markTelemetryPromptShown()
                    telemetryClient.logEvent(TelemetryEvents.TELEMETRY_PROMPT_DISMISSED)
                    visible = false
                }
            },
        )
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
     * Request notification permission (Android 13+).
     * SMS permissions are now handled in the onboarding flow.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                telemetryClient.logEvent(
                    TelemetryEvents.PERMISSION_REQUESTED,
                    mapOf(
                        TelemetryEvents.PARAM_KIND to TelemetryEvents.PERMISSION_NOTIFICATION,
                        TelemetryEvents.PARAM_SOURCE to TelemetryEvents.SOURCE_APP
                    )
                )
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
            telemetryClient.logEvent(
                TelemetryEvents.PERMISSION_REQUESTED,
                mapOf(
                    TelemetryEvents.PARAM_KIND to TelemetryEvents.PERMISSION_SMS,
                    TelemetryEvents.PARAM_SOURCE to TelemetryEvents.SOURCE_APP
                )
            )
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    importRoute: String? = null,
    onImportNavigated: () -> Unit = {},
    notificationNavigation: NotificationNavigation? = null,
    onDeepLinkHandled: (NotificationNavigation) -> Unit = {},
    onScreenViewed: (String) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val navReady = navBackStackEntry != null
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    // Phase 2 telemetry — emit `screen_viewed` whenever the active
    // destination changes. We strip both `?` (optional query args like
    // `analytics?section={section}`) and `/` (path args like
    // `categorize/{expenseId}`) so we only ever send the route base and
    // never leak an id or user-controlled argument.
    LaunchedEffect(currentDestination?.route) {
        val route = currentDestination?.route ?: return@LaunchedEffect
        val screen = route.substringBefore('?').substringBefore('/')
        if (screen.isNotEmpty()) {
            onScreenViewed(screen)
        }
    }

    // Navigate to the import screen chosen during onboarding (SMS or statement)
    LaunchedEffect(importRoute, navReady, resumed) {
        if (importRoute != null && navReady && resumed) {
            navController.navigate(importRoute)
            onImportNavigated()
        }
    }

    // Handle deep-link navigation from notification tap
    LaunchedEffect(notificationNavigation, navReady, resumed) {
        val request = notificationNavigation ?: return@LaunchedEffect
        // This composable exists only after the onboarding and lock gates have resolved.
        val route = request.routeWhenReady(true, true, false, resumed, navReady) ?: return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        onDeepLinkHandled(request)
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
                        selected = currentDestination?.hierarchy?.any { dest ->
                            // Compare ignoring optional query args ("route?arg={arg}") so
                            // destinations like Analytics still highlight the tab.
                            dest.route?.substringBefore("?") == item.route
                        } == true,
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
