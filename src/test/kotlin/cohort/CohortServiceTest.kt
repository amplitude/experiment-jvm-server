package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.SystemLogger
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.CompletableFuture
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CohortServiceTest {

    private val config = CohortServiceConfig()

    init {
        Logger.implementation = SystemLogger(debug = true)
    }

    @Test
    fun `test refresh, success`() {
        val provider = { setOf("a", "b") }
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
        val service = CohortServiceImpl(config, api, storage, provider)
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
        val provider = { setOf("a", "b") }
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = CohortServiceImpl(config, api, storage, provider)
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
        val provider = { setOf("a", "b") }
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = CohortServiceImpl(config, api, storage, provider)
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
        val provider = { setOf("a", "b") }
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        storage.putCohort(cohortDescription("a", lastComputed = 0L), listOf())
        storage.putCohort(cohortDescription("b", lastComputed = 0L), listOf())
        val service = CohortServiceImpl(config, api, storage, provider)
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
    fun `test filter cohorts, only explicit cohorts are included`() {
        val provider = { setOf("a", "b") }
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = CohortServiceImpl(config, api, storage, provider)
        val actual = service.filterCohorts(
            listOf(
                cohortDescription("a"),
                cohortDescription("b"),
                cohortDescription("c"),
                cohortDescription("d"),
                cohortDescription("e"),
            ),
            setOf("d", "e")
        )
        val expected = listOf(
            cohortDescription("d"),
            cohortDescription("e"),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `test download cohorts, happens async`() {
        val provider = { setOf("a", "b", "c") }
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
        val service = CohortServiceImpl(config, api, storage, provider)
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
        val provider = { setOf("a", "b", "c") }
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
        val service = CohortServiceImpl(config, api, storage, provider)
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
        val provider = { setOf("a", "b") }
        val api = mock(CohortApi::class.java)
        val storage = InMemoryCohortStorage()
        val service = CohortServiceImpl(config, api, storage, provider)
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
}
