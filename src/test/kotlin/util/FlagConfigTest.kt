package com.amplitude.experiment.util

import com.amplitude.experiment.cohort.CohortDescription
import com.amplitude.experiment.evaluation.Allocation
import com.amplitude.experiment.evaluation.FLAG_TYPE_RELEASE
import com.amplitude.experiment.evaluation.FlagConfig
import com.amplitude.experiment.evaluation.Operator
import com.amplitude.experiment.evaluation.ParentDependencies
import com.amplitude.experiment.evaluation.SegmentTargetingConfig
import com.amplitude.experiment.evaluation.UserPropertyFilter
import com.amplitude.experiment.evaluation.Variant
import org.junit.Assert.assertEquals
import org.junit.Test

class FlagConfigTest {

    @Test
    fun `test provider, flag with single cohort dependency in one segment`() {
        val flagConfigs = listOf(
            flagConfig(
                customSegmentTargetingConfigs = listOf(
                    segmentTargetingConfig(
                        conditions = listOf(
                            UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("a"))
                        )
                    )
                )
            )
        )
        val actual = flagConfigs.getCohortIds()
        val expected = setOf("a")
        assertEquals(expected, actual)
    }

    @Test
    fun `test provider, flag with multiple cohort dependencies in one segment`() {
        val flagConfigs = listOf(
            flagConfig(
                customSegmentTargetingConfigs = listOf(
                    segmentTargetingConfig(
                        conditions = listOf(
                            UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("a", "b"))
                        )
                    )
                )
            )
        )
        val actual = flagConfigs.getCohortIds()
        val expected = setOf("a", "b")
        assertEquals(expected, actual)
    }

    @Test
    fun `test provider, flag with single cohort dependency in multiple segments`() {
        val flagConfigs = listOf(
            flagConfig(
                customSegmentTargetingConfigs = listOf(
                    segmentTargetingConfig(
                        conditions = listOf(
                            UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("a"))
                        )
                    ),
                    segmentTargetingConfig(
                        conditions = listOf(
                            UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("b"))
                        )
                    )
                )
            )
        )
        val actual = flagConfigs.getCohortIds()
        val expected = setOf("a", "b")
        assertEquals(expected, actual)
    }

    @Test
    fun `test provider, flag with multiple cohort dependencies in multiple segments`() {
        val flagConfigs = listOf(
            flagConfig(
                customSegmentTargetingConfigs = listOf(
                    segmentTargetingConfig(
                        conditions = listOf(
                            UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("a", "c"))
                        )
                    ),
                    segmentTargetingConfig(
                        conditions = listOf(
                            UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("b", "d"))
                        )
                    )
                )
            )
        )
        val actual = flagConfigs.getCohortIds()
        val expected = setOf("a", "b", "c", "d")
        assertEquals(expected, actual)
    }

    @Test
    fun `test provider, multiple flags with multiple cohort dependencies in multiple segments`() {
        val flagConfigs = listOf(
            flagConfig(
                customSegmentTargetingConfigs = listOf(
                    segmentTargetingConfig(
                        conditions = listOf(
                            UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("a", "c"))
                        )
                    ),
                    segmentTargetingConfig(
                        conditions = listOf(
                            UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("b", "d"))
                        )
                    )
                )
            ),
            flagConfig(
                customSegmentTargetingConfigs = listOf(
                    segmentTargetingConfig(
                        conditions = listOf(
                            UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("e", "g"))
                        )
                    ),
                    segmentTargetingConfig(
                        conditions = listOf(
                            UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("f", "h"))
                        )
                    )
                )
            )
        )
        val actual = flagConfigs.getCohortIds()
        val expected = setOf("a", "b", "c", "d", "e", "f", "g", "h")
        assertEquals(expected, actual)
    }

    @Test
    fun `test provider, no cohort dependencies`() {
        val flagConfigs = listOf(
            flagConfig(
                customSegmentTargetingConfigs = listOf(
                    segmentTargetingConfig(conditions = listOf())
                )
            )
        )
        val actual = flagConfigs.getCohortIds()
        val expected = emptySet<String>()
        assertEquals(expected, actual)
    }
}


internal fun cohortDescription(
    id: String,
    lastComputed: Long = 0,
    size: Int = 0
): CohortDescription = CohortDescription(
    lastComputed = lastComputed,
    id = id,
    size = size,
)

internal fun segmentTargetingConfig(
    name: String = "",
    conditions: List<UserPropertyFilter> = listOf(),
    allocations: List<Allocation> = listOf(),
    bucketingKey: String = "",
): SegmentTargetingConfig = SegmentTargetingConfig(
    name = name,
    conditions = conditions,
    allocations = allocations,
    bucketingKey = bucketingKey,
)

internal fun flagConfig(
    flagKey: String = "",
    experimentKey: String? = null,
    flagVersion: Int = 0,
    enabled: Boolean = false,
    bucketingSalt: String = "salt",
    defaultValue: String? = null,
    variants: List<Variant> = listOf(),
    variantsInclusions: Map<String, Set<String>>? = null,
    allUsersTargetingConfig: SegmentTargetingConfig = segmentTargetingConfig(),
    customSegmentTargetingConfigs: List<SegmentTargetingConfig>? = null,
    parentDependencies: ParentDependencies? = null,
    type: String = FLAG_TYPE_RELEASE,
    deployed: Boolean = true,
): FlagConfig = FlagConfig(
    flagKey = flagKey,
    experimentKey = experimentKey,
    flagVersion = flagVersion,
    enabled = enabled,
    bucketingSalt = bucketingSalt,
    defaultValue = defaultValue,
    variants = variants,
    variantsInclusions = variantsInclusions,
    allUsersTargetingConfig = allUsersTargetingConfig,
    customSegmentTargetingConfigs = customSegmentTargetingConfigs,
    parentDependencies = parentDependencies,
    type = type,
    deployed = deployed,
)

