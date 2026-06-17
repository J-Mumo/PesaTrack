package com.pesatrack.data.local.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pesatrack.data.local.database.PesaTrackDatabase
import com.pesatrack.data.local.database.entities.IncomeTransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomeTransactionDaoTest {

    private lateinit var db: PesaTrackDatabase
    private lateinit var dao: IncomeTransactionDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, PesaTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.incomeTransactionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sample(
        transactionId: String,
        amount: Double = 50000.0,
        source: String = "SALARY",
        timestamp: Long = 1_700_000_000_000L,
        isExcluded: Boolean = false
    ) = IncomeTransactionEntity(
        transactionId = transactionId,
        amount = amount,
        timestamp = timestamp,
        source = source,
        parserSource = "MPESA",
        isExcluded = isExcluded
    )

    @Test
    fun insertNewRow_returnsPositiveId() = runBlocking {
        val id = dao.insertIgnoreOnConflict(sample("TX1"))
        assertTrue("inserted id should be positive, was $id", id > 0L)
        assertNotNull(dao.getByTransactionId("TX1"))
    }

    @Test
    fun insertDuplicateTransactionId_returnsMinusOne() = runBlocking {
        val first = dao.insertIgnoreOnConflict(sample("TX1", amount = 10_000.0))
        val second = dao.insertIgnoreOnConflict(sample("TX1", amount = 99_999.0))
        assertTrue(first > 0L)
        assertEquals(-1L, second)
        // Original amount preserved — IGNORE strategy means second insert is dropped.
        assertEquals(10_000.0, dao.getByTransactionId("TX1")!!.amount, 0.001)
    }

    @Test
    fun sumForRange_sumsOnlyNonExcludedInRange() = runBlocking {
        val start = 1_700_000_000_000L
        val day = 86_400_000L
        dao.insertIgnoreOnConflict(sample("A", amount = 10_000.0, timestamp = start))
        dao.insertIgnoreOnConflict(sample("B", amount = 20_000.0, timestamp = start + day))
        dao.insertIgnoreOnConflict(sample("C", amount = 5_000.0, timestamp = start + 2 * day, isExcluded = true))
        // Out of range
        dao.insertIgnoreOnConflict(sample("D", amount = 99_999.0, timestamp = start + 10 * day))

        val sum = dao.sumForRange(startMs = start, endMs = start + 3 * day)
        assertEquals(30_000.0, sum, 0.001)
    }

    @Test
    fun sumForRangeBySources_filtersToInflowSources() = runBlocking {
        val start = 1_700_000_000_000L
        dao.insertIgnoreOnConflict(sample("S", amount = 100_000.0, source = "SALARY", timestamp = start))
        dao.insertIgnoreOnConflict(sample("T", amount = 50_000.0, source = "TRANSFER_IN", timestamp = start + 1))

        val onlySalary = dao.sumForRangeBySources(
            startMs = start,
            endMs = start + 1_000,
            sources = listOf("SALARY")
        )
        assertEquals(100_000.0, onlySalary, 0.001)

        val both = dao.sumForRangeBySources(
            startMs = start,
            endMs = start + 1_000,
            sources = listOf("SALARY", "TRANSFER_IN")
        )
        assertEquals(150_000.0, both, 0.001)
    }

    @Test
    fun updateSource_setsCategorizedFlag() = runBlocking {
        dao.insertIgnoreOnConflict(sample("TX1", source = "UNCATEGORIZED"))
        val before = dao.getByTransactionId("TX1")!!
        assertEquals("UNCATEGORIZED", before.source)
        assertEquals(false, before.isCategorized)

        dao.updateSource(before.id, "SALARY")

        val after = dao.getByTransactionId("TX1")!!
        assertEquals("SALARY", after.source)
        assertEquals(true, after.isCategorized)
    }

    @Test
    fun setExcluded_togglesExclusion() = runBlocking {
        dao.insertIgnoreOnConflict(sample("TX1"))
        val row = dao.getByTransactionId("TX1")!!
        dao.setExcluded(row.id, true)
        assertEquals(true, dao.getById(row.id)!!.isExcluded)
        dao.setExcluded(row.id, false)
        assertEquals(false, dao.getById(row.id)!!.isExcluded)
    }

    @Test
    fun deleteAll_clearsTable() = runBlocking {
        dao.insertIgnoreOnConflict(sample("TX1"))
        dao.insertIgnoreOnConflict(sample("TX2"))
        dao.deleteAll()
        assertNull(dao.getByTransactionId("TX1"))
        assertNull(dao.getByTransactionId("TX2"))
    }

    @Test
    fun differentTransactionIds_areBothStored() = runBlocking {
        val id1 = dao.insertIgnoreOnConflict(sample("TX1"))
        val id2 = dao.insertIgnoreOnConflict(sample("TX2"))
        assertNotEquals(id1, id2)
        assertNotNull(dao.getByTransactionId("TX1"))
        assertNotNull(dao.getByTransactionId("TX2"))
    }
}
