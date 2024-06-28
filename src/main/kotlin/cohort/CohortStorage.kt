package com.amplitude.experiment.cohort

internal interface CohortStorage {
    fun getCohort(cohortId: String): Cohort?
    fun getCohorts(): List<Cohort>
    fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String>
    fun getCohortsForGroup(groupType: String, groupName: String, cohortIds: Set<String>): Set<String>
    fun putCohort(cohort: Cohort)
    fun deleteCohort(groupType: String, cohortId: String)
}

internal class InMemoryCohortStorage : CohortStorage {
    override fun getCohort(cohortId: String): Cohort? {
        TODO("Not yet implemented")
    }

    override fun getCohorts(): List<Cohort> {
        TODO("Not yet implemented")
    }

    override fun getCohortsForUser(userId: String, cohortIds: Set<String>): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getCohortsForGroup(groupType: String, groupName: String, cohortIds: Set<String>): Set<String> {
        TODO("Not yet implemented")
    }

    override fun putCohort(cohort: Cohort) {
        TODO("Not yet implemented")
    }

    override fun deleteCohort(groupType: String, cohortId: String) {
        TODO("Not yet implemented")
    }
}
