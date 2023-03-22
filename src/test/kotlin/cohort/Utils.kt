package com.amplitude.experiment.cohort

import com.amplitude.experiment.evaluation.Allocation
import com.amplitude.experiment.evaluation.FLAG_TYPE_RELEASE
import com.amplitude.experiment.evaluation.FlagConfig
import com.amplitude.experiment.evaluation.ParentDependencies
import com.amplitude.experiment.evaluation.SegmentTargetingConfig
import com.amplitude.experiment.evaluation.UserPropertyFilter
import com.amplitude.experiment.evaluation.Variant

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
