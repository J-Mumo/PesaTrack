package com.pesatrack.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pesatrack.data.local.database.entities.ReportSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [ReportSnapshotEntity].
 *
 * Snapshots are written by the per-cadence workers and read by the matching
 * *ReviewScreen (deep-linked single snapshot + "Previous reports" list).
 */
@Dao
interface ReportSnapshotDao {

    /**
     * Insert a snapshot. The unique index on (cadence, periodStart) guarantees
     * only one snapshot per period per cadence; a re-run for the same period
     * replaces the previous row.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: ReportSnapshotEntity): Long

    /** Fetch a specific snapshot by row id. */
    @Query("SELECT * FROM report_snapshots WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReportSnapshotEntity?

    /** Fetch the most recent snapshot for the given cadence. */
    @Query("SELECT * FROM report_snapshots WHERE cadence = :cadence ORDER BY periodStart DESC LIMIT 1")
    suspend fun getLatestForCadence(cadence: String): ReportSnapshotEntity?

    /**
     * Observe the most recent snapshot for the given cadence so the UI can
     * react when a worker produces a fresh report while the screen is open.
     */
    @Query("SELECT * FROM report_snapshots WHERE cadence = :cadence ORDER BY periodStart DESC LIMIT 1")
    fun observeLatestForCadence(cadence: String): Flow<ReportSnapshotEntity?>

    /** List previous snapshots for the given cadence, newest first. */
    @Query("""
        SELECT * FROM report_snapshots
        WHERE cadence = :cadence
        ORDER BY periodStart DESC
        LIMIT :limit
    """)
    suspend fun getRecentForCadence(cadence: String, limit: Int = 12): List<ReportSnapshotEntity>

    /** Look up a snapshot by exact period bounds (used for "did we already generate this?"). */
    @Query("""
        SELECT * FROM report_snapshots
        WHERE cadence = :cadence AND periodStart = :periodStart
        LIMIT 1
    """)
    suspend fun getByPeriodStart(cadence: String, periodStart: Long): ReportSnapshotEntity?

    /** Mark a snapshot as viewed (sets `viewedAt = now` if currently null). */
    @Query("UPDATE report_snapshots SET viewedAt = :viewedAt WHERE id = :id AND viewedAt IS NULL")
    suspend fun markViewed(id: Long, viewedAt: Long = System.currentTimeMillis())

    /** Delete all snapshots (used by data-reset flow). */
    @Query("DELETE FROM report_snapshots")
    suspend fun deleteAll()
}
