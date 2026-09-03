package com.pesatrack.di

import android.content.Context
import com.pesatrack.BuildConfig
import com.pesatrack.services.telemetry.FirebaseTelemetryClient
import com.pesatrack.services.telemetry.NoOpTelemetryClient
import com.pesatrack.services.telemetry.TelemetryClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the app-wide [TelemetryClient].
 *
 * Debug builds get [NoOpTelemetryClient] so local development never emits
 * events. Release builds get the Firebase-backed implementation, which is
 * itself dormant until the user opts in.
 *
 * If Firebase initialization fails (e.g., missing `google-services.json`
 * in a release build), we fall back to the no-op client rather than
 * crashing.
 */
@Module
@InstallIn(SingletonComponent::class)
object TelemetryModule {

    @Provides
    @Singleton
    fun provideTelemetryClient(
        @ApplicationContext context: Context
    ): TelemetryClient {
        if (BuildConfig.DEBUG) return NoOpTelemetryClient()
        return runCatching { FirebaseTelemetryClient(context) }
            .getOrElse { NoOpTelemetryClient() }
    }
}
