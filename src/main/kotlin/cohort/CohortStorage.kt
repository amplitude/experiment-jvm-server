package com.amplitude.experiment.cohort

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal interface CohortStorage {
    fun getCohortsForUser(userId: String): Set<String>
    fun getCohortDescription(cohortId: String): CohortDescription?
    fun getAllCohortDescriptions(): Map<String, CohortDescription>
    fun putCohort(cohortDescription: CohortDescription, userIds: List<String?>)
    fun deleteCohort(cohortId: String)
}

internal class InMemoryCohortStorage : CohortStorage {
    private val lock = ReentrantReadWriteLock()
    private val cohortStore = mutableMapOf<String, Set<String>>()
    private val descriptionStore = mutableMapOf<String, CohortDescription>()

    override fun getCohortsForUser(userId: String): Set<String> {
        val result = mutableSetOf<String>()
        lock.read {
            for (entry in cohortStore.entries) {
                if (entry.value.contains(userId)) {
                    result.add(entry.key)
                }
            }
        }
        return result
    }

    override fun getCohortDescription(cohortId: String): CohortDescription? {
        return lock.read {
            descriptionStore[cohortId]
        }
    }

    override fun getAllCohortDescriptions(): Map<String, CohortDescription> {
        return lock.read {
            descriptionStore.toMap()
        }
    }

    override fun putCohort(cohortDescription: CohortDescription, userIds: List<String?>) {
        lock.write {
            cohortStore[cohortDescription.id] = userIds.filterNotNullTo(mutableSetOf())
            descriptionStore[cohortDescription.id] = cohortDescription
        }
    }

    override fun deleteCohort(cohortId: String) {
        lock.write {
            cohortStore.remove(cohortId)
            descriptionStore.remove(cohortId)
        }
    }
}
