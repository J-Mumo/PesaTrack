package com.pesatrack.presentation.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * First-launch onboarding screen — shown once when the app is first installed.
 *
 * 4 pages:
 * 1. Welcome — what the app does
 * 2. How it works — SMS parsing explained
 * 3. SMS Permission — grant permission with context
 * 4. Import History — offer to import past SMS
 *
 * Uses Compose HorizontalPager with dot indicators.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onImportHistory: () -> Unit,
    onSmsPermissionGranted: () -> Unit = {},
    onSmsPermissionSkipped: () -> Unit = {}
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()

    // Track SMS permission state
    var smsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                    PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher for SMS
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        smsPermissionGranted = allGranted
        if (allGranted) {
            onSmsPermissionGranted()
        }
    }

    // Notification permission launcher (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* No action needed — just request it */ }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Skip button at top right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    if (!smsPermissionGranted) {
                        onSmsPermissionSkipped()
                    }
                    onComplete()
                }) {
                    Text("Skip")
                }
            }

            // Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> HowItWorksPage()
                    2 -> SmsPermissionPage(
                        smsPermissionGranted = smsPermissionGranted,
                        onGrantPermission = {
                            smsPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.RECEIVE_SMS
                                )
                            )
                        }
                    )
                    3 -> ImportHistoryPage(
                        smsPermissionGranted = smsPermissionGranted,
                        onImportNow = {
                            onImportHistory()
                            onComplete()
                        }
                    )
                }
            }

            // Dot indicators + navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button (hidden on first page)
                AnimatedVisibility(visible = pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    ) {
                        Text("Back")
                    }
                }
                if (pagerState.currentPage == 0) {
                    Spacer(modifier = Modifier.width(64.dp))
                }

                // Dot indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                // Next / Get Started button
                if (pagerState.currentPage < 3) {
                    Button(
                        onClick = {
                            // Record SMS skipped if leaving page 2 without granting
                            if (pagerState.currentPage == 2 && !smsPermissionGranted) {
                                onSmsPermissionSkipped()
                            }
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                            // Request notification permission when leaving page 2
                            if (pagerState.currentPage == 2) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    ) {
                        Text("Next")
                    }
                } else {
                    Button(onClick = {
                        if (!smsPermissionGranted) {
                            onSmsPermissionSkipped()
                        }
                        onComplete()
                    }) {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}

// ==================== Individual Pages ====================

@Composable
private fun WelcomePage() {
    PageContent(
        emoji = "💰",
        title = "Welcome to PesaTrack",
        description = "Track your M-PESA and bank expenses automatically.\n\n" +
                "All data stays on your device — no internet, no cloud, no tracking."
    )
}

@Composable
private fun HowItWorksPage() {
    PageContent(
        emoji = "📱",
        title = "How It Works",
        description = "PesaTrack reads your M-PESA confirmation SMS and extracts " +
                "transaction details — amount, recipient, date, and type.\n\n" +
                "You categorize and budget. PesaTrack does the rest."
    )
}

@Composable
private fun SmsPermissionPage(
    smsPermissionGranted: Boolean,
    onGrantPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔐",
            fontSize = 64.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SMS Access Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PesaTrack needs SMS permission to read your M-PESA " +
                    "and bank messages.\n\n" +
                    "We only read SMS from MPESA and supported banks — " +
                    "never your personal messages.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (smsPermissionGranted) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("✅", fontSize = 20.sp)
                    Text(
                        text = "Permission granted",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Button(
                onClick = onGrantPermission,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Grant SMS Permission")
            }
        }
    }
}

@Composable
private fun ImportHistoryPage(
    smsPermissionGranted: Boolean,
    onImportNow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📥",
            fontSize = 64.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Import Past Transactions?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (smsPermissionGranted) {
                "Want to import your existing M-PESA SMS?\n\n" +
                        "This scans your message history for M-PESA transactions " +
                        "and adds them to PesaTrack."
            } else {
                "SMS permission is needed to import past transactions.\n\n" +
                        "You can grant permission on the previous page, " +
                        "or import later from the Import screen."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (smsPermissionGranted) {
            Button(
                onClick = onImportNow,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Import Now")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "You can also import later from the Import screen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Reusable page layout for simple text-only pages.
 */
@Composable
private fun PageContent(
    emoji: String,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = emoji,
            fontSize = 64.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
