package com.amplitude.experiment.cohort

import com.amplitude.experiment.util.USER_GROUP_TYPE
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal interface CohortStorage {
    fun getCohort(cohortId: String): Cohort?
    fun getCohorts(): Map<String, Cohort>
    fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String>
    fun getCohortsForGroup(groupType: String, groupName: String, cohortIds: Set<String>): Set<String>
    fun putCohort(cohort: Cohort)
    fun deleteCohort(cohortId: String)
}

internal class InMemoryCohortStorage : CohortStorage {
    private val lock = ReentrantReadWriteLock()
    private val cohortStore = mutableMapOf<String, Cohort>()

    override fun getCohort(cohortId: String): Cohort? {
        lock.read {
            return cohortStore[cohortId]
        }
    }

    override fun getCohorts(): Map<String, Cohort> {
        lock.read {
            return cohortStore.toMap()
        }
    }

    override fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String> {
        return getCohortsForGroup(USER_GROUP_TYPE, userId, cohortIds)
    }

    override fun getCohortsForGroup(groupType: String, groupName: String, cohortIds: Set<String>): Set<String> {
        val result = mutableSetOf<String>()
        lock.read {
            for (cohortId in cohortIds) {
                val cohort = cohortStore[cohortId] ?: continue
                if (cohort.groupType != groupType) {
                    continue
                }
                if (cohort.members.contains(groupName)) {
                    result.add(cohortId)
                }
            }
        }
        return result
    }


    override fun putCohort(cohort: Cohort) {
        lock.write {
            cohortStore[cohort.id] = cohort
        }
    }

    override fun deleteCohort(cohortId: String) {
        lock.write {
            cohortStore.remove(cohortId)
        }
    }
}
