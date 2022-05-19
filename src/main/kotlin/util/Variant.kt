package com.amplitude.experiment.util

import com.amplitude.experiment.Variant
import com.amplitude.experiment.evaluation.serialization.SerialVariant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun SerialVariant.toVariant() = Variant(
    value = key,
    payload = payload?.toAny(),
)

internal fun JsonElement.toAny(): Any? {
    return when (this) {
        is JsonNull -> null
        is JsonObject -> this.toMap().mapValues { it.value.toAny() }
        is JsonArray -> this.toList().map { it.toAny() }
        is JsonPrimitive -> {
            if (this.isString) {
                return this.contentOrNull
            }
            this.intOrNull ?: this.longOrNull ?: this.floatOrNull ?: this.doubleOrNull ?: this.booleanOrNull
        }
    }
}
