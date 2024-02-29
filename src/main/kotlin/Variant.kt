package com.amplitude.experiment

import com.amplitude.experiment.evaluation.EvaluationVariant

data class Variant @JvmOverloads constructor(
    @JvmField val value: String? = null,
    @JvmField val payload: Any? = null,
    @JvmField val key: String? = null,
    @JvmField val metadata: Map<String, Any?>? = null,
) {
    companion object {
        /**
         * Utility function for comparing a possibly null variant's value to a string in java.
         *
         * ```
         * Variant.valueEquals(variant, "on");
         * ```
         *
         * is equivalent to
         *
         * ```
         * variant != null && "on".equals(variant.value)
         * ```
         *
         * @param variant The nullable variant whose value should be compared.
         * @param value The string to compare with the value of the variant.
         */
        @JvmStatic
        fun valueEquals(variant: Variant?, value: String?) = variant?.value == value
    }
}

internal fun Variant.isNullOrEmpty(): Boolean =
    this.key == null && this.value == null && this.payload == null && this.metadata == null

internal fun EvaluationVariant.isDefaultVariant(): Boolean {
    return metadata?.get("default") as? Boolean ?: false
}

internal fun Variant.isDefaultVariant(): Boolean {
    return metadata?.get("default") as? Boolean ?: false
}
