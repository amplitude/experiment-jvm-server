@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.cohort

import com.amplitude.experiment.EvaluationProxyConfiguration
import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationMetrics
import com.amplitude.experiment.util.Cache
import com.amplitude.experiment.util.LocalEvaluationMetricsWrapper
import com.amplitude.experiment.util.Logger
import com.amplitude.experiment.util.USER_GROUP_TYPE
import com.amplitude.experiment.util.wrapMetrics
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

internal interface CohortStorage {
    fun getCohort(cohortId: String): Cohort?
    fun getCohorts(): Map<String, Cohort>
    fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String>
    fun getCohortsForGroup(groupType: String, groupName: String, cohortIds: Set<String>): Set<String>
    fun putCohort(cohort: Cohort)
    fun deleteCohort(cohortId: String)
}

internal class ProxyCohortStorage(
    private val proxyConfig: EvaluationProxyConfiguration,
    private val membershipApi: CohortMembershipApi,
    private val metrics: LocalEvaluationMetrics = LocalEvaluationMetricsWrapper()
) : CohortStorage {

    private val storage: CohortStorage = InMemoryCohortStorage()
    private val membershipCacheLock = ReentrantLock()
    private val membershipCache = mutableMapOf<String, Cache<String, Set<String>>>()

    override fun getCohort(cohortId: String): Cohort? {
        return storage.getCohort(cohortId)
    }

    override fun getCohorts(): Map<String, Cohort> {
        return storage.getCohorts()
    }

    override fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String> {
        return getCohortsForGroup(USER_GROUP_TYPE, userId, cohortIds)
    }

    override fun getCohortsForGroup(groupType: String, groupName: String, cohortIds: Set<String>): Set<String> {
        val localCohortIds = storage.getCohorts().keys
        return if (localCohortIds.containsAll(cohortIds)) {
            storage.getCohortsForGroup(groupType, groupName, cohortIds)
        } else {
            getMembershipCache(groupType)[groupName]
                ?: try {
                    wrapMetrics(
                        metric = metrics::onProxyCohortMembership,
                        failure = metrics::onProxyCohortMembershipFailure
                    ) {
                        membershipApi.getCohortMemberships(groupType, groupName).also { proxyCohortMemberships ->
                            getMembershipCache(groupType)[groupName] = proxyCohortMemberships
                        }
                    }
                } catch (e: Exception) {
                    Logger.e("Failed to get cohort membership from proxy.", e)
                    // Fall back on in memory storage in the case of proxy failure.
                    storage.getCohortsForGroup(groupType, groupName, cohortIds)
                }
        }
    }

    override fun putCohort(cohort: Cohort) {
        storage.putCohort(cohort)
    }

    override fun deleteCohort(cohortId: String) {
        storage.deleteCohort(cohortId)
    }

    private fun getMembershipCache(groupType: String): Cache<String, Set<String>> {
        return membershipCacheLock.withLock {
            membershipCache.getOrPut(groupType) {
                Cache(
                    proxyConfig.cohortCacheCapacity,
                    proxyConfig.cohortCacheTtlMillis
                )
            }
        }
    }
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
