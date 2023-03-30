package com.amplitude.experiment.cohort

class CohortIdProviderTest {

    // @Test
    // fun `test provider, flag with single cohort dependency in one segment`() {
    //     val flagConfigs = listOf(
    //         flagConfig(
    //             customSegmentTargetingConfigs = listOf(
    //                 segmentTargetingConfig(
    //                     conditions = listOf(
    //                         UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("a"))
    //                     )
    //                 )
    //             )
    //         )
    //     )
    //     val actual = flagConfigs.getCohortIds()
    //     val expected = setOf("a")
    //     assertEquals(expected, actual)
    // }
    //
    // @Test
    // fun `test provider, flag with multiple cohort dependencies in one segment`() {
    //     val flagConfigs = listOf(
    //         flagConfig(
    //             customSegmentTargetingConfigs = listOf(
    //                 segmentTargetingConfig(
    //                     conditions = listOf(
    //                         UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("a", "b"))
    //                     )
    //                 )
    //             )
    //         )
    //     )
    //     val actual = flagConfigs.getCohortIds()
    //     val expected = setOf("a", "b")
    //     assertEquals(expected, actual)
    // }
    //
    // @Test
    // fun `test provider, flag with single cohort dependency in multiple segments`() {
    //     val flagConfigs = listOf(
    //         flagConfig(
    //             customSegmentTargetingConfigs = listOf(
    //                 segmentTargetingConfig(
    //                     conditions = listOf(
    //                         UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("a"))
    //                     )
    //                 ),
    //                 segmentTargetingConfig(
    //                     conditions = listOf(
    //                         UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("b"))
    //                     )
    //                 )
    //             )
    //         )
    //     )
    //     val actual = flagConfigs.getCohortIds()
    //     val expected = setOf("a", "b")
    //     assertEquals(expected, actual)
    // }
    //
    // @Test
    // fun `test provider, flag with multiple cohort dependencies in multiple segments`() {
    //     val flagConfigs = listOf(
    //         flagConfig(
    //             customSegmentTargetingConfigs = listOf(
    //                 segmentTargetingConfig(
    //                     conditions = listOf(
    //                         UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("a", "c"))
    //                     )
    //                 ),
    //                 segmentTargetingConfig(
    //                     conditions = listOf(
    //                         UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("b", "d"))
    //                     )
    //                 )
    //             )
    //         )
    //     )
    //     val actual = flagConfigs.getCohortIds()
    //     val expected = setOf("a", "b", "c", "d")
    //     assertEquals(expected, actual)
    // }
    //
    // @Test
    // fun `test provider, multiple flags with multiple cohort dependencies in multiple segments`() {
    //     val flagConfigs = listOf(
    //         flagConfig(
    //             customSegmentTargetingConfigs = listOf(
    //                 segmentTargetingConfig(
    //                     conditions = listOf(
    //                         UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("a", "c"))
    //                     )
    //                 ),
    //                 segmentTargetingConfig(
    //                     conditions = listOf(
    //                         UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("b", "d"))
    //                     )
    //                 )
    //             )
    //         ),
    //         flagConfig(
    //             customSegmentTargetingConfigs = listOf(
    //                 segmentTargetingConfig(
    //                     conditions = listOf(
    //                         UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("e", "g"))
    //                     )
    //                 ),
    //                 segmentTargetingConfig(
    //                     conditions = listOf(
    //                         UserPropertyFilter(COHORT_PROP_KEY, Operator.IS, setOf("f", "h"))
    //                     )
    //                 )
    //             )
    //         )
    //     )
    //     val actual = flagConfigs.getCohortIds()
    //     val expected = setOf("a", "b", "c", "d", "e", "f", "g", "h")
    //     assertEquals(expected, actual)
    // }
    //
    // @Test
    // fun `test provider, no cohort dependencies`() {
    //     val flagConfigs = listOf(
    //         flagConfig(
    //             customSegmentTargetingConfigs = listOf(
    //                 segmentTargetingConfig(conditions = listOf())
    //             )
    //         )
    //     )
    //     val actual = flagConfigs.getCohortIds()
    //     val expected = emptySet<String>()
    //     assertEquals(expected, actual)
    // }
}
