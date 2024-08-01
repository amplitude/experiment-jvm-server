package com.amplitude.experiment.deployment

import com.amplitude.experiment.LocalEvaluationConfig
import com.amplitude.experiment.cohort.CohortApi
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.evaluation.EvaluationCondition
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.evaluation.EvaluationOperator
import com.amplitude.experiment.evaluation.EvaluationSegment
import com.amplitude.experiment.flag.FlagConfigApi
import com.amplitude.experiment.flag.FlagConfigStorage
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.fail

private const val COHORT_ID = "1234"

class DeploymentRunnerTest {

    val flag = EvaluationFlag(
        key = "flag",
        variants = mapOf(),
        segments = listOf(
            EvaluationSegment(
                conditions = listOf(
                    listOf(
                        EvaluationCondition(
                            selector = listOf("context", "user", "cohort_ids"),
                            op = EvaluationOperator.SET_CONTAINS_ANY,
                            values = setOf(COHORT_ID),
                        )
                    )
                ),
            )
        )
    )

    @Test
    fun `test start throws if first flag config load fails`() {
        val flagApi = Mockito.mock(FlagConfigApi::class.java)
        val cohortApi = Mockito.mock(CohortApi::class.java)
        val flagConfigStorage = Mockito.mock(FlagConfigStorage::class.java)
        val cohortStorage = Mockito.mock(CohortStorage::class.java)
        val runner = DeploymentRunner(
            LocalEvaluationConfig(),
            flagApi,
            flagConfigStorage,
            cohortApi,
            cohortStorage,
        )
        Mockito.`when`(flagApi.getFlagConfigs()).thenThrow(RuntimeException("test"))
        try {
            runner.start()
            fail("expected start() to throw an exception")
        } catch (e: Exception) {
            // pass
        }
        // Should be able to call start again
        try {
            runner.start()
            fail("expected start() to throw an exception")
        } catch (e: Exception) {
            // pass
        }
    }

    @Test
    fun `test start does not throw if first cohort load fails`() {
        val flagApi = Mockito.mock(FlagConfigApi::class.java)
        val cohortApi = Mockito.mock(CohortApi::class.java)
        val flagConfigStorage = Mockito.mock(FlagConfigStorage::class.java)
        val cohortStorage = Mockito.mock(CohortStorage::class.java)
        val runner = DeploymentRunner(
            LocalEvaluationConfig(),
            flagApi, flagConfigStorage,
            cohortApi,
            cohortStorage,
        )
        Mockito.`when`(flagApi.getFlagConfigs()).thenReturn(listOf(flag))
        Mockito.`when`(cohortApi.getCohort(COHORT_ID, null)).thenThrow(RuntimeException("test"))
        runner.start()
    }
}
