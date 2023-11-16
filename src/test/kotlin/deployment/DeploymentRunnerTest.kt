@file:OptIn(ExperimentalApi::class)

package com.amplitude.experiment.deployment

import com.amplitude.experiment.ExperimentalApi
import com.amplitude.experiment.LocalEvaluationConfig
import com.amplitude.experiment.cohort.CohortDownloadApi
import com.amplitude.experiment.cohort.CohortLoader
import com.amplitude.experiment.cohort.CohortStorage
import com.amplitude.experiment.cohort.InMemoryCohortStorage
import com.amplitude.experiment.evaluation.EvaluationCondition
import com.amplitude.experiment.evaluation.EvaluationFlag
import com.amplitude.experiment.evaluation.EvaluationOperator
import com.amplitude.experiment.evaluation.EvaluationSegment
import com.amplitude.experiment.flag.FlagConfigApi
import com.amplitude.experiment.flag.FlagConfigStorage
import com.amplitude.experiment.flag.InMemoryFlagConfigStorage
import okio.IOException
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito
import kotlin.test.fail

private const val COHORT_ID = "1234"

class DeploymentRunnerTest {

    val flag = EvaluationFlag(
        key = "flag",
        variants = mapOf(),
        segments = listOf(EvaluationSegment(
            conditions = listOf(listOf(EvaluationCondition(
                selector = listOf("context", "user", "cohort_ids"),
                op = EvaluationOperator.SET_CONTAINS_ANY,
                values = setOf(COHORT_ID),
            ))),
        ))
    )

    @Test
    fun `test start throws if first flag config load fails`() {
        val flagApi = Mockito.mock(FlagConfigApi::class.java)
        val cohortDownloadApi = Mockito.mock(CohortDownloadApi::class.java)
        val flagConfigStorage = Mockito.mock(FlagConfigStorage::class.java)
        val cohortStorage = Mockito.mock(CohortStorage::class.java)
        val cohortLoader = CohortLoader(100, cohortDownloadApi, cohortStorage)
        val runner = DeploymentRunner(
            LocalEvaluationConfig(),
            flagApi, flagConfigStorage,
            cohortStorage,
            cohortLoader
        )
        Mockito.`when`(flagApi.getFlagConfigs()).thenThrow(RuntimeException("test"))
        try {
            runner.start()
            fail("expected start() to throw an exception")
        } catch (e: Exception) {
            // pass
        }
    }

    @Test
    fun `test start throws if first cohort load fails`() {
        val flagApi = Mockito.mock(FlagConfigApi::class.java)
        val cohortDownloadApi = Mockito.mock(CohortDownloadApi::class.java)
        val flagConfigStorage = Mockito.mock(FlagConfigStorage::class.java)
        val cohortStorage = Mockito.mock(CohortStorage::class.java)
        val cohortLoader = CohortLoader(100, cohortDownloadApi, cohortStorage)
        val runner = DeploymentRunner(
            LocalEvaluationConfig(),
            flagApi, flagConfigStorage,
            cohortStorage,
            cohortLoader
        )
        Mockito.`when`(flagApi.getFlagConfigs()).thenReturn(listOf(flag))
        Mockito.`when`(cohortDownloadApi.getCohortDescription(COHORT_ID)).thenThrow(RuntimeException("test"))
        try {
            runner.start()
            fail("expected start() to throw an exception")
        } catch (e: Exception) {
            // pass
        }
    }
}
