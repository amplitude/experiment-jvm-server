@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.cohort

import com.amplitude.experiment.CohortSyncConfiguration
import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.util.cohortDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class CohortSyncServiceTest {

    private val config = CohortSyncConfiguration("", "")

    @Test
    fun `test refresh, success`() {
        val cohortIds = setOf("a", "b")
        val api = mock(CohortDownloadApi::class.java)
        `when`(api.getCohortDescriptions()).thenReturn(listOf(
            cohortDescription("a"),
            cohortDescription("b"),
        ))
        `when`(api.getCohortMembers(cohortDescription("a")))
            .thenReturn(setOf("1"))
        `when`(api.getCohortMembers(cohortDescription("b")))
            .thenReturn(setOf("1", "2"))
        val storage = InMemoryCohortStorage()
        val service = CohortSyncService(config, api, storage)
        service.refresh(cohortIds)
        service.refresh(cohortIds)

        // Check storage description
        val storageDescriptionA = storage.getCohortDescription("a")
        val storageDescriptionB = storage.getCohortDescription("b")
        assertEquals(cohortDescription("a"), storageDescriptionA)
        assertEquals(cohortDescription("b"), storageDescriptionB)

        // Check storage users
        val storageUser1Cohorts = storage.getCohortsForUser("1", cohortIds)
        val storageUser2Cohorts = storage.getCohortsForUser("2", cohortIds)
        assertEquals(setOf("a", "b"), storageUser1Cohorts)
        assertEquals(setOf("b"), storageUser2Cohorts)
    }

    @Test
    fun `test filter cohorts, cohorts not matching provider are filtered`() {
        val cohortIds = setOf("a", "b")
        val api = mock(CohortDownloadApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = CohortSyncService(config, api, storage)
        service.refresh(cohortIds)
        val actual = service.filterCohorts(
            listOf(
                cohortDescription("a"),
                cohortDescription("b"),
                cohortDescription("c"),
            )
        )
        val expected = listOf(cohortDescription("a"), cohortDescription("b"))
        assertEquals(expected, actual)
    }

    @Test
    fun `test filter cohorts, cohorts greater than max cohort size are filtered`() {
        val cohortIds = setOf("a", "b")
        val api = mock(CohortDownloadApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = CohortSyncService(config, api, storage)
        service.refresh(cohortIds)
        val actual = service.filterCohorts(
            listOf(
                cohortDescription("a", size = Int.MAX_VALUE),
                cohortDescription("b", size = 1),
            )
        )
        val expected = listOf(cohortDescription("b", size = 1))
        assertEquals(expected, actual)
    }

    @Test
    fun `test filter cohorts, already computed equivalent cohorts are filtered`() {
        val cohortIds = setOf("a", "b")
        val api = mock(CohortDownloadApi::class.java)
        val storage = InMemoryCohortStorage()
        storage.putCohort(cohortDescription("a", lastComputed = 0L), setOf())
        storage.putCohort(cohortDescription("b", lastComputed = 0L), setOf())
        val service = CohortSyncService(config, api, storage)
        service.refresh(cohortIds)
        val actual = service.filterCohorts(
            listOf(
                cohortDescription("a", lastComputed = 0L),
                cohortDescription("b", lastComputed = 1L),
            )
        )
        val expected = listOf(cohortDescription("b", lastComputed = 1L))
        assertEquals(expected, actual)
    }

    @Test
    fun `test filter cohorts, only managed cohorts are included`() {
        val cohortIds = setOf("a", "b")
        val api = mock(CohortDownloadApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = CohortSyncService(config, api, storage)
        service.refresh(cohortIds)
        val actual = service.filterCohorts(
            listOf(
                cohortDescription("a"),
                cohortDescription("b"),
                cohortDescription("c"),
                cohortDescription("d"),
                cohortDescription("e"),
            ),
        )
        val expected = listOf(
            cohortDescription("a"),
            cohortDescription("b"),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `test refresh download failure throws`() {
        val cohortIds = setOf("a", "b", "c")
        val api = mock(CohortDownloadApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = CohortSyncService(config, api, storage)
        service.refresh(cohortIds)

        // Setup mocks
        `when`(api.getCohortDescriptions())
            .thenReturn(listOf(
                cohortDescription("a"),
                cohortDescription("b"),
                cohortDescription("c"),
            ))
        `when`(api.getCohortMembers(cohortDescription("a")))
            .thenReturn(setOf("1"))
        `when`(api.getCohortMembers(cohortDescription("b")))
            .thenThrow(RuntimeException("Connection timed out"))
        `when`(api.getCohortMembers(cohortDescription("c")))
            .thenReturn(setOf("1"))

        // Refresh should throw. No cohorts should be stored.
        assertThrows(RuntimeException::class.java) { service.refresh() }
        assertEquals(setOf("a"), storage.getCohortsForUser("1", setOf("a", "b", "c")))
    }
}
