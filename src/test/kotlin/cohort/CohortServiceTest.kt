package com.amplitude.experiment.cohort

import org.junit.Assert
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CohortServiceTest {

    private val config = CohortServiceConfig()

    @Test
    fun `test refresh, success`() {
        val managedCohorts = setOf("a", "b")
        val api = mock(CohortApi::class.java)
        `when`(api.getCohorts(GetCohortsRequest))
            .thenReturn(
                CompletableFuture.completedFuture(
                    GetCohortsResponse(
                        listOf(
                            cohortDescription("a"),
                            cohortDescription("b"),
                        )
                    )
                )
            )
        `when`(api.getCohort(GetCohortRequest("a", 0)))
            .thenReturn(
                CompletableFuture.completedFuture(
                    GetCohortResponse(
                        cohortDescription("a"),
                        listOf("1"),
                    )
                )
            )
        `when`(api.getCohort(GetCohortRequest("b", 0)))
            .thenReturn(
                CompletableFuture.completedFuture(
                    GetCohortResponse(
                        cohortDescription("b"),
                        listOf("1", "2"),
                    )
                )
            )
        val storage = InMemoryCohortStorage()
        val service = PollingCohortService(config, api, storage).apply {
            manage(managedCohorts)
        }
        service.refresh()

        // Check storage description
        val storageDescriptionA = storage.getCohortDescription("a")
        val storageDescriptionB = storage.getCohortDescription("b")
        assertEquals(cohortDescription("a"), storageDescriptionA)
        assertEquals(cohortDescription("b"), storageDescriptionB)

        // Check storage users
        val storageUser1Cohorts = storage.getCohortsForUser("1")
        val storageUser2Cohorts = storage.getCohortsForUser("2")
        assertEquals(setOf("a", "b"), storageUser1Cohorts)
        assertEquals(setOf("b"), storageUser2Cohorts)
    }

    @Test
    fun `test filter cohorts, cohorts not matching provider are filtered`() {
        val managedCohorts = setOf("a", "b")
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = PollingCohortService(config, api, storage).apply {
            manage(managedCohorts)
        }
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
        val managedCohorts = setOf("a", "b")
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = PollingCohortService(config, api, storage).apply {
            manage(managedCohorts)
        }
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
        val managedCohorts = setOf("a", "b")
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        storage.putCohort(cohortDescription("a", lastComputed = 0L), listOf())
        storage.putCohort(cohortDescription("b", lastComputed = 0L), listOf())
        val service = PollingCohortService(config, api, storage).apply {
            manage(managedCohorts)
        }
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
        val managedCohorts = setOf("a", "b")
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = PollingCohortService(config, api, storage).apply {
            manage(managedCohorts)
        }
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
    fun `test download cohorts, happens async`() {
        val managedCohorts = setOf("a", "b", "c")
        val api = mock(CohortApi::class.java)
        `when`(api.getCohort(GetCohortRequest("a", 0)))
            .thenReturn(
                CompletableFuture.supplyAsync {
                    Thread.sleep(100)
                    GetCohortResponse(
                        cohortDescription("a"),
                        listOf("1"),
                    )
                }
            )
        `when`(api.getCohort(GetCohortRequest("b", 0)))
            .thenReturn(
                CompletableFuture.supplyAsync {
                    Thread.sleep(100)
                    GetCohortResponse(
                        cohortDescription("b"),
                        listOf("1"),
                    )
                }
            )
        `when`(api.getCohort(GetCohortRequest("c", 0)))
            .thenReturn(
                CompletableFuture.supplyAsync {
                    Thread.sleep(100)
                    GetCohortResponse(
                        cohortDescription("c"),
                        listOf("1"),
                    )
                }
            )
        `when`(api.getCohort(GetCohortRequest("d", 0)))
            .thenReturn(
                CompletableFuture.supplyAsync {
                    Thread.sleep(100)
                    GetCohortResponse(
                        cohortDescription("d"),
                        listOf("1"),
                    )
                }
            )
        val storage = InMemoryCohortStorage()
        val service = PollingCohortService(config, api, storage).apply {
            manage(managedCohorts)
        }
        val duration = measureTimeMillis {
            service.downloadCohorts(
                listOf(
                    cohortDescription("a"),
                    cohortDescription("b"),
                    cohortDescription("c"),
                    cohortDescription("d"),
                )
            )
        }
        assertTrue(duration < 200)
    }

    @Test
    fun `test download cohorts, single failure`() {
        val managedCohorts = setOf("a", "b", "c")
        val api = mock(CohortApi::class.java)
        `when`(api.getCohort(GetCohortRequest("a", 0)))
            .thenReturn(
                CompletableFuture.supplyAsync {
                    GetCohortResponse(
                        cohortDescription("a"),
                        listOf("1"),
                    )
                }
            )
        `when`(api.getCohort(GetCohortRequest("b", 0)))
            .thenReturn(
                CompletableFuture.supplyAsync {
                    GetCohortResponse(
                        cohortDescription("b"),
                        listOf("1"),
                    )
                }
            )
        `when`(api.getCohort(GetCohortRequest("c", 0)))
            .thenReturn(
                CompletableFuture.supplyAsync {
                    throw RuntimeException("Failure")
                }
            )
        `when`(api.getCohort(GetCohortRequest("d", 0)))
            .thenReturn(
                CompletableFuture.supplyAsync {
                    GetCohortResponse(
                        cohortDescription("d"),
                        listOf("1"),
                    )
                }
            )
        val storage = InMemoryCohortStorage()
        val service = PollingCohortService(config, api, storage).apply {
            manage(managedCohorts)
        }
        val actual = service.downloadCohorts(
            listOf(
                cohortDescription("a"),
                cohortDescription("b"),
                cohortDescription("c"),
                cohortDescription("d"),
            )
        )
        val expected = listOf(
            GetCohortResponse(
                cohortDescription("a"),
                listOf("1"),
            ),
            GetCohortResponse(
                cohortDescription("b"),
                listOf("1"),
            ),
            GetCohortResponse(
                cohortDescription("d"),
                listOf("1"),
            ),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `test store cohorts, success`() {
        val managedCohorts = setOf("a", "b")
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = PollingCohortService(config, api, storage).apply {
            manage(managedCohorts)
        }
        service.storeCohorts(
            listOf(
                GetCohortResponse(
                    cohortDescription("a"),
                    listOf("1")
                ),
                GetCohortResponse(
                    cohortDescription("b"),
                    listOf("1", "2")
                ),
            )
        )
        // Check storage description
        val storageDescriptionA = storage.getCohortDescription("a")
        val storageDescriptionB = storage.getCohortDescription("b")
        assertEquals(cohortDescription("a"), storageDescriptionA)
        assertEquals(cohortDescription("b"), storageDescriptionB)

        // Check storage users
        val storageUser1Cohorts = storage.getCohortsForUser("1")
        val storageUser2Cohorts = storage.getCohortsForUser("2")
        assertEquals(setOf("a", "b"), storageUser1Cohorts)
        assertEquals(setOf("b"), storageUser2Cohorts)
    }

    @Test
    fun `test refresh download failure throws`() {
        val managedCohorts = setOf("a", "b")
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = PollingCohortService(config, api, storage).apply {
            manage(managedCohorts)
        }

        // Setup mocks
        `when`(api.getCohorts(GetCohortsRequest))
            .thenReturn(
                CompletableFuture.completedFuture(
                    GetCohortsResponse(
                        listOf(
                            cohortDescription("a"),
                            cohortDescription("b"),
                        )
                    )
                )
            )
        `when`(api.getCohort(GetCohortRequest("a", 0)))
            .thenReturn(
                CompletableFuture.completedFuture(
                    GetCohortResponse(
                        cohortDescription("a"),
                        listOf("1"),
                    )
                )
            )
        `when`(api.getCohort(GetCohortRequest("b", 0)))
            .thenReturn(
                CompletableFuture.failedFuture(IOException("Connection timed out"))
            )

        // Refresh should throw. No cohorts should be stored.
        Assert.assertThrows(IOException::class.java) { service.refresh() }
        Assert.assertEquals(emptySet<String>(), storage.getCohortsForUser("1"))
    }
}
