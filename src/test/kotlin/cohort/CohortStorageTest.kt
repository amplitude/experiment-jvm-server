@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.cohort

import com.amplitude.experiment.EvaluationProxyConfiguration
import com.amplitude.experiment.ExperimentalApi
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlin.test.Test

class CohortStorageTest {

    @Test
    fun `test in memory`() {
        val storage = InMemoryCohortStorage()
        val cohortA = Cohort("a", "User", 1, 100, setOf("u1"))
        val cohortB = Cohort("b", "group", 1, 100, setOf("g1"))
        // test get, null
        var cohort = storage.getCohort(cohortA.id)
        assertNull(cohort)
        // test get all, empty
        var cohorts = storage.getCohorts()
        assertEquals(0, cohorts.size)
        // test get memberships for user, empty
        var memberships = storage.getCohortsForUser("u1", setOf(cohortA.id, cohortB.id))
        assertEquals(0, memberships.size)
        // test get memberships for group, empty
        memberships = storage.getCohortsForUser("g1", setOf(cohortA.id, cohortB.id))
        assertEquals(0, memberships.size)
        // test put, get, cohort
        storage.putCohort(cohortA)
        storage.putCohort(cohortB)
        cohort = storage.getCohort(cohortA.id)
        assertEquals(cohortA, cohort)
        // test put, get all, cohorts
        cohorts = storage.getCohorts()
        assertEquals(mapOf(cohortA.id to cohortA, cohortB.id to cohortB), cohorts)
        // test get memberships for user, cohort
        memberships = storage.getCohortsForUser("u1", setOf(cohortA.id, cohortB.id))
        assertEquals(setOf(cohortA.id), memberships)
        // test get memberships for group, cohort
        memberships = storage.getCohortsForGroup("group", "g1", setOf(cohortA.id, cohortB.id))
        assertEquals(setOf(cohortB.id), memberships)
        // test delete, get removed, null
        storage.deleteCohort(cohortA.id)
        cohort = storage.getCohort(cohortA.id)
        assertNull(cohort)
        // get existing, cohort
        cohort = storage.getCohort(cohortB.id)
        assertEquals(cohortB, cohort)
        // test get memberships for removed, empty
        memberships = storage.getCohortsForUser("u1", setOf(cohortA.id, cohortB.id))
        assertEquals(0, memberships.size)
        // test get memberships for existing, cohorts
        memberships = storage.getCohortsForGroup("group", "g1", setOf(cohortA.id, cohortB.id))
        assertEquals(setOf(cohortB.id), memberships)
        // test get all, cohort
        cohorts = storage.getCohorts()
        assertEquals(mapOf(cohortB.id to cohortB), cohorts)
    }

    @Test
    fun `test proxy storage`() {
        val cohortMembershipApi = mockk<CohortMembershipApi>()
        every { cohortMembershipApi.getCohortMemberships(eq("User"), eq("u1")) } returns setOf("a")
        every { cohortMembershipApi.getCohortMemberships(eq("group"), eq("g1")) } returns setOf("b")
        val storage = ProxyCohortStorage(
            EvaluationProxyConfiguration(""),
            cohortMembershipApi
        )
        val cohortA = Cohort("a", "User", 1, 100, setOf("u1"))
        val cohortB = Cohort("b", "group", 1, 100, setOf("g1"))

        // test get, null
        var cohort = storage.getCohort(cohortA.id)
        assertNull(cohort)
        // test get all, empty
        var cohorts = storage.getCohorts()
        assertEquals(0, cohorts.size)
        // test get memberships for user, cohort from proxy
        var memberships = storage.getCohortsForUser("u1", setOf(cohortA.id, cohortB.id))
        assertEquals(setOf(cohortA.id), memberships)
        // test get memberships for group, cohort from proxy
        memberships = storage.getCohortsForGroup("group", "g1", setOf(cohortA.id, cohortB.id))
        assertEquals(setOf(cohortB.id), memberships)
        // test put, get, cohort
        storage.putCohort(cohortA)
        storage.putCohort(cohortB)
        cohort = storage.getCohort(cohortA.id)
        assertEquals(cohortA, cohort)
        // test put, get all, cohorts
        cohorts = storage.getCohorts()
        assertEquals(mapOf(cohortA.id to cohortA, cohortB.id to cohortB), cohorts)
        // test get memberships for user, cohort
        memberships = storage.getCohortsForUser("u1", setOf(cohortA.id, cohortB.id))
        assertEquals(setOf(cohortA.id), memberships)
        // test get memberships for group, cohort
        memberships = storage.getCohortsForGroup("group", "g1", setOf(cohortA.id, cohortB.id))
        assertEquals(setOf(cohortB.id), memberships)
        // test delete, get removed, null
        storage.deleteCohort(cohortA.id)
        cohort = storage.getCohort(cohortA.id)
        assertNull(cohort)
        // get existing, cohort
        cohort = storage.getCohort(cohortB.id)
        assertEquals(cohortB, cohort)
        // test get memberships for removed, cohort from proxy
        memberships = storage.getCohortsForUser("u1", setOf(cohortA.id, cohortB.id))
        assertEquals(setOf(cohortA.id), memberships)
        // test get memberships for existing, cohorts
        memberships = storage.getCohortsForGroup("group", "g1", setOf(cohortA.id, cohortB.id))
        assertEquals(setOf(cohortB.id), memberships)
        // test get all, cohort
        cohorts = storage.getCohorts()
        assertEquals(mapOf(cohortB.id to cohortB), cohorts)
    }
}
