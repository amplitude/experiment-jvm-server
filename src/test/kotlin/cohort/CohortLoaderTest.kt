@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.cohort

import com.amplitude.experiment.CohortSyncConfiguration
import com.amplitude.experiment.ExperimentalApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.ExecutionException

class CohortLoaderTest {

    private val config = CohortSyncConfiguration("", "")

    @Test
    fun `test load, success`() {
        val api = mock(CohortDownloadApi::class.java)
        `when`(api.getCohortDescription("a")).thenReturn(cohortDescription("a"))
        `when`(api.getCohortDescription("b")).thenReturn(cohortDescription("b"))
        `when`(api.getCohortMembers(cohortDescription("a")))
            .thenReturn(setOf("1"))
        `when`(api.getCohortMembers(cohortDescription("b")))
            .thenReturn(setOf("1", "2"))
        val storage = InMemoryCohortStorage()
        val loader = CohortLoader(15000, api, storage)
        loader.loadCohort("a").get()
        loader.loadCohort("b").get()

        // Check storage description
        val storageDescriptionA = storage.getCohortDescription("a")
        val storageDescriptionB = storage.getCohortDescription("b")
        assertEquals(cohortDescription("a"), storageDescriptionA)
        assertEquals(cohortDescription("b"), storageDescriptionB)

        // Check storage users
        val storageUser1Cohorts = storage.getCohortsForUser("1", setOf("a", "b"))
        val storageUser2Cohorts = storage.getCohortsForUser("2", setOf("a", "b"))
        assertEquals(setOf("a", "b"), storageUser1Cohorts)
        assertEquals(setOf("b"), storageUser2Cohorts)
    }

    @Test
    fun `test load, cohorts greater than max cohort size are filtered`() {
        val api = mock(CohortDownloadApi::class.java)
        `when`(api.getCohortDescription("a")).thenReturn(cohortDescription("a", size = Int.MAX_VALUE))
        `when`(api.getCohortDescription("b")).thenReturn(cohortDescription("b", size = 1))
        `when`(api.getCohortMembers(cohortDescription("a", size = Int.MAX_VALUE)))
            .thenReturn(setOf("1"))
        `when`(api.getCohortMembers(cohortDescription("b", size = 1)))
            .thenReturn(setOf("1", "2"))

        val storage = InMemoryCohortStorage()
        val loader = CohortLoader(15000, api, storage)
        loader.loadCohort("a").get()
        loader.loadCohort("b").get()

        // Check storage description
        val storageDescriptionA = storage.getCohortDescription("a")
        val storageDescriptionB = storage.getCohortDescription("b")
        assertNull(storageDescriptionA)
        assertEquals(cohortDescription("b", size = 1), storageDescriptionB)

        // Check storage users
        val storageUser1Cohorts = storage.getCohortsForUser("1", setOf("a", "b"))
        val storageUser2Cohorts = storage.getCohortsForUser("2", setOf("a", "b"))
        assertEquals(setOf("b"), storageUser1Cohorts)
        assertEquals(setOf("b"), storageUser2Cohorts)
    }

    @Test
    fun `test filter cohorts, already computed equivalent cohorts are filtered`() {
        val storage = InMemoryCohortStorage()
        storage.putCohort(cohortDescription("a", lastComputed = 0L), setOf())
        storage.putCohort(cohortDescription("b", lastComputed = 0L), setOf())
        val api = mock(CohortDownloadApi::class.java)
        `when`(api.getCohortDescription("a")).thenReturn(cohortDescription("a", lastComputed = 0L))
        `when`(api.getCohortDescription("b")).thenReturn(cohortDescription("b", lastComputed = 1L))
        `when`(api.getCohortMembers(cohortDescription("a", lastComputed = 0L)))
            .thenReturn(setOf("1"))
        `when`(api.getCohortMembers(cohortDescription("b", lastComputed = 1L)))
            .thenReturn(setOf("1", "2"))
        val loader = CohortLoader(15000, api, storage)
        loader.loadCohort("a").get()
        loader.loadCohort("b").get()

        // Check storage description
        val storageDescriptionA = storage.getCohortDescription("a")
        val storageDescriptionB = storage.getCohortDescription("b")
        assertEquals(cohortDescription("a", lastComputed = 0L), storageDescriptionA)
        assertEquals(cohortDescription("b", lastComputed = 1L), storageDescriptionB)

        // Check storage users
        val storageUser1Cohorts = storage.getCohortsForUser("1", setOf("a", "b"))
        val storageUser2Cohorts = storage.getCohortsForUser("2", setOf("a", "b"))
        assertEquals(setOf("b"), storageUser1Cohorts)
        assertEquals(setOf("b"), storageUser2Cohorts)
    }

    @Test
    fun `test load, download failure throws`() {
        val api = mock(CohortDownloadApi::class.java)
        val storage = InMemoryCohortStorage()
        val loader = CohortLoader(15000, api, storage)

        `when`(api.getCohortDescription("a")).thenReturn(cohortDescription("a"))
        `when`(api.getCohortDescription("b")).thenReturn(cohortDescription("b"))
        `when`(api.getCohortDescription("c")).thenReturn(cohortDescription("c"))
        `when`(api.getCohortMembers(cohortDescription("a"))).thenReturn(setOf("1"))
        `when`(api.getCohortMembers(cohortDescription("b"))).thenThrow(RuntimeException("Connection timed out"))
        `when`(api.getCohortMembers(cohortDescription("c"))).thenReturn(setOf("1"))

        loader.loadCohort("a").get()
        assertThrows(ExecutionException::class.java) { loader.loadCohort("b").get() }
        loader.loadCohort("c").get()
        assertEquals(setOf("a", "c"), storage.getCohortsForUser("1", setOf("a", "b", "c")))
    }
}

private fun cohortDescription(id: String, lastComputed: Long = 0, size: Int = 0): CohortDescription {
    return CohortDescription(id = id, lastComputed = lastComputed, size = size)
}
