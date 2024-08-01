package com.amplitude.experiment.cohort

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import org.junit.Assert.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.ExecutionException
import kotlin.test.Test

class CohortLoaderTest {

    @Test
    fun `test load, success`() {
        val cohortA = cohort("a", setOf("1"))
        val cohortB = cohort("b", setOf("1", "2"))
        val api = mock(CohortDownloadApi::class.java)
        `when`(api.getCohort("a", null)).thenReturn(cohortA)
        `when`(api.getCohort("b", null)).thenReturn(cohortB)
        val storage = InMemoryCohortStorage()
        val loader = CohortLoader(api, storage)
        loader.loadCohort("a").get()
        loader.loadCohort("b").get()
        val storageCohortA = storage.getCohort("a")
        val storageCohortB = storage.getCohort("b")
        assertEquals(cohortA, storageCohortA)
        assertEquals(cohortB, storageCohortB)
    }

    @Test
    fun `test load, cohorts greater than max cohort size are not downloaded`() {
        val cohortB = cohort("b", setOf("1", "2"))
        val api = mock(CohortDownloadApi::class.java)
        `when`(api.getCohort("a", null)).thenThrow(CohortTooLargeException("a", 15000))
        `when`(api.getCohort("b", null)).thenReturn(cohortB)
        val storage = InMemoryCohortStorage()
        val loader = CohortLoader(api, storage)
        loader.loadCohort("a").get()
        loader.loadCohort("b").get()
        val storageDescriptionA = storage.getCohort("a")
        val storageDescriptionB = storage.getCohort("b")
        assertNull(storageDescriptionA)
        assertEquals(cohortB, storageDescriptionB)
    }

    @Test
    fun `test load, unchanged cohorts dont change`() {
        val storageCohortA = cohort("a", setOf("1"))
        val storageCohortB = cohort("b", setOf("1", "2"))
        val storage = InMemoryCohortStorage()
        storage.putCohort(storageCohortA)
        storage.putCohort(storageCohortB)
        val networkCohortB = cohort("b", setOf("1", "2"))
        val api = mock(CohortDownloadApi::class.java)
        `when`(api.getCohort("a", storageCohortA)).thenThrow(CohortNotModifiedException("a"))
        `when`(api.getCohort("b", storageCohortB)).thenReturn(networkCohortB)
        val loader = CohortLoader(api, storage)
        loader.loadCohort("a").get()
        loader.loadCohort("b").get()
        val newStorageCohortA = storage.getCohort("a")
        val newStorageCohortB = storage.getCohort("b")
        assertEquals(storageCohortA, newStorageCohortA)
        assertEquals(networkCohortB, newStorageCohortB)
    }

    @Test
    fun `test load, download failure throws`() {
        val cohortA = cohort("a", setOf("1"))
        val cohortC = cohort("c", setOf("1", "2", "3"))
        val api = mock(CohortDownloadApi::class.java)
        val storage = InMemoryCohortStorage()
        val loader = CohortLoader(api, storage)
        `when`(api.getCohort("a", null)).thenReturn(cohortA)
        `when`(api.getCohort("b", null)).thenThrow(RuntimeException("Connection timed out"))
        `when`(api.getCohort("c", null)).thenReturn(cohortC)
        loader.loadCohort("a").get()
        assertThrows(ExecutionException::class.java) { loader.loadCohort("b").get() }
        loader.loadCohort("c").get()
        assertEquals(setOf("a", "c"), storage.getCohortsForUser("1", setOf("a", "b", "c")))
    }
}

private fun cohort(id: String, members: Set<String>, lastModified: Long = 1234) =
    Cohort(
        id = id,
        members = members,
        lastModified = lastModified,
        size = members.size,
        groupType = "User",
    )
