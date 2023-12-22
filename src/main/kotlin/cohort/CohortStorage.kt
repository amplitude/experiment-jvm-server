@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.cohort

import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.ProxyConfiguration
import com.amplitude.experiment.util.Cache
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.wrapMetrics
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal interface CohortStorage {
    fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String>
    fun getCohortsForGroup(groupType: String, groupName: String, cohortIds: Set<String>): Set<String>
    fun getCohortDescription(cohortId: String): CohortDescription?
    fun getCohortDescriptions(): Map<String, CohortDescription>
    fun putCohort(cohortDescription: CohortDescription, members: Set<String>)
    fun deleteCohort(groupType: String, cohortId: String)
}

internal class ProxyCohortStorage(
    proxyConfig: ProxyConfiguration,
    private val cohortMembershipApi: CohortMembershipApi,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) : CohortStorage {

    private val cohortCache = Cache<String, Set<String>>(
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
                ?: try {
                    wrapMetrics(
                        metric = metrics::onCohortMembership,
                        failure = metrics::onCohortMembershipFailure
                    ) {
                        cohortMembershipApi.getCohortsForUser(userId).also { proxyCohortMemberships ->
                            cohortCache[userId] = proxyCohortMemberships
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("Failed to get cohort membership from proxy.", e)
                    // Fall back on in memory storage in the case of proxy failure.
                    inMemoryStorage.getCohortsForUser(userId, cohortIds)
                }
        }
    }

    override fun getCohortsForGroup(groupType: String, groupName: String, cohortIds: Set<String>): Set<String> {
        // TODO Group cohorts are not yet supported by the proxy.
        return setOf()
    }

    override fun getCohortDescription(cohortId: String): CohortDescription? {
        return inMemoryStorage.getCohortDescription(cohortId)
    }

    override fun getCohortDescriptions(): Map<String, CohortDescription> {
        return inMemoryStorage.getCohortDescriptions()
    }

    override fun putCohort(cohortDescription: CohortDescription, members: Set<String>) {
        inMemoryStorage.putCohort(cohortDescription, members)
    }

    override fun deleteCohort(groupType: String, cohortId: String) {
        inMemoryStorage.deleteCohort(groupType, cohortId)
    }
}

internal class InMemoryCohortStorage : CohortStorage {
    private val lock = ReentrantReadWriteLock()
    private val cohortStore = mutableMapOf<String, MutableMap<String, Set<String>>>()
    private val descriptionStore = mutableMapOf<String, CohortDescription>()

    override fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String> {
        return getCohortsForGroup(USER_GROUP_TYPE, userId, cohortIds)
    }

    override fun getCohortsForGroup(groupType: String, groupName: String, cohortIds: Set<String>): Set<String> {
        val result = mutableSetOf<String>()
        lock.read {
            val groupTypeCohorts = cohortStore[groupType] ?: return result
            for (entry in groupTypeCohorts.entries) {
                if (cohortIds.contains(entry.key) && entry.value.contains(groupName)) {
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

    override fun putCohort(cohortDescription: CohortDescription, members: Set<String>) {
        lock.write {
            cohortStore.getOrPut(
                cohortDescription.groupType
            ) { mutableMapOf() }[cohortDescription.id] = members
            descriptionStore[cohortDescription.id] = cohortDescription
        }
    }

    override fun deleteCohort(groupType: String, cohortId: String) {
        lock.write {
            cohortStore[groupType]?.remove(cohortId)
        }
    }
}
