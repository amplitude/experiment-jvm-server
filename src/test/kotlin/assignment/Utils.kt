package com.amplitude.experiment.assignment

import com.amplitude.experiment.evaluation.FlagResult
import com.amplitude.experiment.evaluation.Variant

internal fun flagResult(
    variant: String,
    description: String = "description",
    isDefaultVariant: Boolean = false,
    expKey: String? = null,
    deployed: Boolean = true,
    type: String = "release",
): FlagResult {
    return FlagResult(Variant(variant), description, isDefaultVariant, expKey, deployed, type)
}
