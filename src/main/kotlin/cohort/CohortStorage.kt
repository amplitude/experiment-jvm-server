package com.amplitude.experiment.cohort

internal interface CohortStorage {
    fun getCohortsForUser(userId: String): Set<String>
    fun getCohortDescription(cohortId: String): CohortDescription?
    fun putCohort(cohortDescription: CohortDescription, userIds: List<String?>)
}

internal class InMemoryCohortStorage: CohortStorage {
    private val userStore = mutableMapOf<String, MutableSet<String>>()
    private val descriptionStore = mutableMapOf<String, CohortDescription>()

    override fun getCohortsForUser(userId: String): Set<String> {
        return synchronized(this) {
            userStore[userId] ?: setOf()
        }
    }

    override fun getCohortDescription(cohortId: String): CohortDescription? {
        return synchronized(this) {
            descriptionStore[cohortId]
        }
    }

    override fun putCohort(cohortDescription: CohortDescription, userIds: List<String?>) {
        synchronized(this) {
            userIds.forEach { userId ->
                if (userId != null) {
                    userStore.compute(userId) { _, v ->
                        v?.apply { add(cohortDescription.id) } ?:
                        mutableSetOf(cohortDescription.id)
                    }
                }
            }
            descriptionStore[cohortDescription.id] = cohortDescription
        }
    }
}
