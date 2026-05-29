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

    // SMS permission uses the primer pattern: page 3 shows context and a
    // prominent "Grant SMS Permission" button. The user initiates the system
    // dialog by tapping that button, which produces a materially higher grant
    // rate than auto-launching the dialog on page entry (especially in Kenya,
    // where users have been conditioned to reflexively deny SMS asks). If the
    // user prefers not to grant, the Next button is relabeled
    // "Skip — I'll add manually" so the alternative path is explicit.
    //
    // Notification permission is intentionally NOT requested here either.
    // MainActivity.requestNotificationPermission() runs after onComplete(), so
    // the user sees at most one dialog during onboarding (SMS) and the
    // notification ask follows once they reach Home — avoids dialog fatigue.

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
                    // On page 3 (SMS permission), if not granted, label the
                    // Next button as an explicit skip so users see the
                    // alternative path instead of feeling stuck.
                    val isSmsPageWithoutPermission =
                        pagerState.currentPage == 2 && !smsPermissionGranted
                    val buttonLabel = if (isSmsPageWithoutPermission) {
                        "Skip \u2014 I'll add manually"
                    } else {
                        "Next"
                    }
                    val buttonColors = if (isSmsPageWithoutPermission) {
                        ButtonDefaults.outlinedButtonColors()
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                    if (isSmsPageWithoutPermission) {
                        OutlinedButton(
                            onClick = {
                                onSmsPermissionSkipped()
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        ) {
                            Text(buttonLabel)
                        }
                    } else {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            colors = buttonColors
                        ) {
                            Text(buttonLabel)
                        }
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
            text = "PesaTrack reads only M-PESA and bank SMS to track your " +
                    "expenses. We ignore all other messages.\n\n" +
                    "Nothing leaves your phone \u2014 PesaTrack has no internet " +
                    "permission, so it cannot send your data anywhere.\n\n" +
                    "Prefer not to grant SMS access? You can add expenses " +
                    "manually \u2014 just tap Skip below.",
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
                "No problem \u2014 you can add expenses manually as you spend.\n\n" +
                        "To import past M-PESA SMS later, grant SMS access from " +
                        "the Home screen or Settings anytime."
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
