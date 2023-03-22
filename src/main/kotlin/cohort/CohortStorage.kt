@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.cohort

import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.ProxyConfiguration
import com.amplitude.experiment.util.LRUCache
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal interface CohortStorage {
    fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String>
    fun getCohortDescription(cohortId: String): CohortDescription?
    fun getCohortDescriptions(): Map<String, CohortDescription>
    fun putCohort(cohortDescription: CohortDescription, userIds: Set<String>)
    fun deleteCohort(cohortId: String)
}

internal class ProxyCohortStorage(
    proxyConfig: ProxyConfiguration,
    private val cohortMembershipApi: CohortMembershipApi
) : CohortStorage {

    private val cohortCache = LRUCache<String, Set<String>>(
        proxyConfig.cohortCacheCapacity,
        proxyConfig.cohortCacheTtlMillis
    )
    private val inMemoryStorage = InMemoryCohortStorage()

    override fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String> {
        val localCohortIds = inMemoryStorage.getCohortDescriptions().keys
        return if (localCohortIds.containsAll(cohortIds)) {
            inMemoryStorage.getCohortsForUser(userId, cohortIds)
        } else {
            cohortCache[userId]
                ?: cohortMembershipApi.getCohortsForUser(userId).also { proxyCohortMemberships ->
                    cohortCache[userId] = proxyCohortMemberships
                }
        }
    }

    override fun getCohortDescription(cohortId: String): CohortDescription? {
        return inMemoryStorage.getCohortDescription(cohortId)
    }

    override fun getCohortDescriptions(): Map<String, CohortDescription> {
        return inMemoryStorage.getCohortDescriptions()
    }

    override fun putCohort(cohortDescription: CohortDescription, userIds: Set<String>) {
        inMemoryStorage.putCohort(cohortDescription, userIds)
    }

    override fun deleteCohort(cohortId: String) {
        inMemoryStorage.deleteCohort(cohortId)
    }
}

internal class InMemoryCohortStorage : CohortStorage {
    private val lock = ReentrantReadWriteLock()
    private val cohortStore = mutableMapOf<String, Set<String>>()
    private val descriptionStore = mutableMapOf<String, CohortDescription>()

    override fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String> {
        val result = mutableSetOf<String>()
        lock.read {
            for (entry in cohortStore.entries) {
                if (cohortIds.contains(entry.key) && entry.value.contains(userId)) {
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

    override fun getCohortDescriptions(): Map<String, CohortDescription> {
        return lock.read {
            descriptionStore.toMap()
        }
    }

    override fun putCohort(cohortDescription: CohortDescription, userIds: Set<String>) {
        lock.write {
            cohortStore[cohortDescription.id] = userIds.toMutableSet()
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
