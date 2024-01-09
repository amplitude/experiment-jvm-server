package com.amplitude.experiment.util

import com.amplitude.experiment.Variant
import com.amplitude.experiment.evaluation.EvaluationVariant

internal fun EvaluationVariant.toVariant(): Variant =
    Variant(value?.toString(), payload, key, metadata)

internal fun Map<String, EvaluationVariant>.toVariants(): Map<String, Variant> =
    mapValues { it.value.toVariant() }

internal fun Map<String, Variant>.filterDefaultVariants(): Map<String, Variant> =
    filter { entry ->
        val default = entry.value.metadata?.get("default") as? Boolean ?: false
        val deployed = entry.value.metadata?.get("deployed") as? Boolean ?: true
        (!default && deployed)
    }
