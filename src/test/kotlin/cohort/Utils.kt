package com.amplitude.experiment.cohort

import com.amplitude.experiment.evaluation.Allocation
import com.amplitude.experiment.evaluation.EvaluationMode
import com.amplitude.experiment.evaluation.FlagConfig
import com.amplitude.experiment.evaluation.SegmentTargetingConfig
import com.amplitude.experiment.evaluation.UserPropertyFilter
import com.amplitude.experiment.evaluation.Variant

internal fun cohortDescription(
    id: String,
    lastComputed: Long = 0,
    size: Int = 0
): CohortDescription = CohortDescription(
    lastComputed = lastComputed,
    published = true,
    archived = false,
    appId = 0,
    lastMod = 0,
    type = "",
    id = id,
    size = size,
)

internal fun segmentTargetingConfig(
    name: String = "",
    conditions: List<UserPropertyFilter> = listOf(),
    allocations: List<Allocation> = listOf(),
    bucketingKey: String? = null,
): SegmentTargetingConfig = SegmentTargetingConfig(
    name = name,
    conditions = conditions,
    allocations = allocations,
    bucketingKey = bucketingKey,
)

internal fun flagConfig(
    flagKey: String = "",
    enabled: Boolean = false,
    bucketingKey: String = "",
    bucketingSalt: String? = null,
    defaultValue: String? = null,
    variants: List<Variant> = listOf(),
    variantsExclusions: Map<String, Set<String>>? = null,
    variantsInclusions: Map<String, Set<String>>? = null,
    allUsersTargetingConfig: SegmentTargetingConfig = segmentTargetingConfig(),
    customSegmentTargetingConfigs: List<SegmentTargetingConfig>? = null,
    evalMode: EvaluationMode = EvaluationMode.LOCAL,
): FlagConfig = FlagConfig(
    flagKey = flagKey,
    enabled = enabled,
    bucketingKey = bucketingKey,
    bucketingSalt = bucketingSalt,
    defaultValue = defaultValue,
    variants = variants,
    variantsExclusions = variantsExclusions,
    variantsInclusions = variantsInclusions,
    allUsersTargetingConfig = allUsersTargetingConfig,
    customSegmentTargetingConfigs = customSegmentTargetingConfigs,
    evalMode = evalMode,
)
