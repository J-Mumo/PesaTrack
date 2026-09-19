package com.pesatrack.services.pro

import kotlinx.coroutines.flow.Flow

/**
 * Narrow contract that hides [ProState] persistence behind three
 * signatures. [com.pesatrack.data.local.preferences.AppPreferences]
 * implements this in production; a `FakeProStateStore` implements it in
 * unit tests so [ProEntitlementRepository] can be exercised on the JVM
 * without pulling in Robolectric or a real DataStore.
 *
 * The three methods here match the existing `AppPreferences` signatures
 * introduced in Slice A2 — the interface is purely a testability seam,
 * not a redesign.
 */
interface ProStateStore {

    /** Hot flow of the persisted [ProState]. Emits [ProState.DEFAULT] on missing/malformed. */
    val proState: Flow<ProState>

    /** Snapshot read of the current persisted [ProState]. */
    suspend fun getProState(): ProState

    /** Atomic write of a new [ProState]. Never write partial fields — hand the whole snapshot. */
    suspend fun setProState(state: ProState)
}
