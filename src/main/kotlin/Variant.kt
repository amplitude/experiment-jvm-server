package com.amplitude.experiment

data class Variant @JvmOverloads constructor(
    @JvmField val value: String? = null,
    @JvmField val payload: Any? = null,
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
